package org.locationtech.geomesa.jobs.raster

import java.awt.image.RenderedImage
import javax.media.jai.{Histogram, JAI}

import com.twitter.scalding._
import org.apache.accumulo.core.data.{Key, Mutation, Value}
import org.apache.hadoop.conf.Configuration
import org.geotools.data.DataStoreFinder
import org.locationtech.geomesa.core.data.AccumuloDataStore
import org.locationtech.geomesa.feature.SimpleFeatureDecoder
import org.locationtech.geomesa.jobs.JobUtils
import org.locationtech.geomesa.jobs.scalding._
import org.locationtech.geomesa.raster.data.Raster
import org.locationtech.geomesa.raster.index.RasterIndexSchema
import org.opengis.feature.`type`.AttributeDescriptor
import org.opengis.feature.simple.SimpleFeatureType

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.reflect.ClassTag

trait RasterJobResources {
  def ds: AccumuloDataStore
  def sft: SimpleFeatureType
  def visibilities: String
  def decoder: SimpleFeatureDecoder
  def attributeDescriptors: mutable.Buffer[(Int, AttributeDescriptor)]

  // required by scalding
  def release(): Unit = {}
}

object RasterJobResources {
  import scala.collection.JavaConversions._
  def apply(params:  Map[String, String], feature: String, attributes: List[String]) = new RasterJobResources {
    val ds: AccumuloDataStore = DataStoreFinder.getDataStore(params.asJava).asInstanceOf[AccumuloDataStore]
    val sft: SimpleFeatureType = ds.getSchema(feature)
    val visibilities: String = ds.writeVisibilities
    val decoder: SimpleFeatureDecoder = SimpleFeatureDecoder(sft, ds.getFeatureEncoding(sft))
    // the attributes we want to index
    override val attributeDescriptors =
      sft.getAttributeDescriptors
        .zipWithIndex
        .filter { case (ad, idx) => attributes.contains(ad.getLocalName) }
        .map { case (ad, idx) => (idx, ad) }
  }
}

object RasterJobs {
  val schema = RasterIndexSchema("")
}

import org.locationtech.geomesa.jobs.raster.RasterJobs._

object GrayScaleOperation {
  val d = Array(Array(.21, .71, 0.07, 0.0))

  def colorKVtoGrayScaleMutation(k: Key, v: Value): Mutation = {
    val raster = schema.decode(k, v)

    import javax.media.jai._
    val grayScale: RenderedImage = JAI.create("bandcombine", raster.chunk, d, null)

    val grayRaster = Raster(grayScale, raster.metadata, raster.resolution)

    val (nk, nv) = schema.encode(grayRaster)

    val m = new Mutation(nk.getRow)
    m.put(nk.getColumnFamily, nk.getColumnQualifier, nk.getColumnVisibilityParsed, nv)
    m
  }

}

object HistogramOperation {

  def kvToHistogram(k: Key, v: Value): Array[Int] = {
    val raster = schema.decode(k, v)
    getHist(raster.chunk)
  }

  def getHist(image: java.awt.image.RenderedImage): Array[Int] = {
    val dst = JAI.create("histogram", image, null)
    val h = dst.getProperty("histogram").asInstanceOf[Histogram]
    h.getBins(0)
  }

  def addBins[T : Numeric : ClassTag](a: Array[T], b: Array[T]): Array[T] = {
    require(a.length == b.length)
    val op = implicitly[Numeric[T]]
    val ret = new Array[T](a.length)
    var i = 0
    while(i < a.length) {
      ret(i) = op.plus(a(i), b(i))
      i += 1
    }
    ret
  }

}

class HistogramRasterJob(args: Args) extends Job(args) {

  lazy val zookeepers       = args(RasterConnectionParams.ZOOKEEPERS)
  lazy val instance         = args(RasterConnectionParams.ACCUMULO_INSTANCE)
  lazy val user             = args(RasterConnectionParams.ACCUMULO_USER)
  lazy val password         = args(RasterConnectionParams.ACCUMULO_PASSWORD)
  lazy val hdfs             = args(RasterConnectionParams.HDFS_DEFAULT)
  lazy val inputTable       = args(RasterConnectionParams.INPUT_TABLE)
  lazy val outputTable      = args(RasterConnectionParams.OUTPUT_TABLE)

  lazy val input   = AccumuloInputOptions(inputTable)
  lazy val output  = AccumuloOutputOptions(outputTable)
  lazy val options = AccumuloSourceOptions(instance, zookeepers, user, password, input, output)

  AccumuloSource(options)
    .mapTo[Array[Int]]('hist) {
    (kv: (Key, Value)) => {
      HistogramOperation.kvToHistogram(kv._1, kv._2)
    }
  }.groupAll {
    _.reduce[Array[Int]]('hist -> 'totalHist) {
      (h1: Array[Int], h2: Array[Int]) => HistogramOperation.addBins(h1, h2)
    }
  }.mapTo[Array[Int], String]('totalHist -> 'line) {
    (array: Array[Int]) => s"Histogram = Array(${array.mkString(",")})"
  }.write(TextLine(s"$hdfs/tmp/$outputTable/output/histogram"))

}

object HistogramRasterJob extends RasterJob{
  conf.set("accumulo.monitor.address", "damaster")
  conf.set("mapreduce.framework.name", "yarn")
  conf.set("yarn.resourcemanager.address", "dresman:8040")
  conf.set(RasterConnectionParams.HDFS_DEFAULT, "hdfs://dhead:54310")
  conf.set("yarn.resourcemanager.scheduler.address", "dresman:8030")

  override def runJob(): Unit = {
    JobUtils.setLibJars(conf)

    val args = buildArgs()

    val hdfsMode = Hdfs(strict = true, conf)
    val arguments = Mode.putMode(hdfsMode, args)

    val job = new HistogramRasterJob(arguments)
    val flow = job.buildFlow
    flow.complete() // this blocks until the job is done
  }

  override def buildArgs(): Args = {
    val args = new collection.mutable.ListBuffer[String]()

    args.append("--" + RasterConnectionParams.ZOOKEEPERS, "dzoo1")
    args.append("--" + RasterConnectionParams.ACCUMULO_INSTANCE, "dcloud")
    args.append("--" + RasterConnectionParams.ACCUMULO_USER, "root")
    args.append("--" + RasterConnectionParams.ACCUMULO_PASSWORD, "secret")
    args.append("--" + RasterConnectionParams.INPUT_TABLE, "AANNEX_SRI_ALL_VIS_RASTERS")
    args.append("--" + RasterConnectionParams.OUTPUT_TABLE, "AANNEX_SRI_ALL_VIS_RASTERS")
    args.append("--" + RasterConnectionParams.HDFS_DEFAULT, "hdfs://dhead:54310")

    Args(args)
  }

}

class GrayScaleRasterJob(args: Args) extends Job(args) {

  lazy val zookeepers       = args(RasterConnectionParams.ZOOKEEPERS)
  lazy val instance         = args(RasterConnectionParams.ACCUMULO_INSTANCE)
  lazy val user             = args(RasterConnectionParams.ACCUMULO_USER)
  lazy val password         = args(RasterConnectionParams.ACCUMULO_PASSWORD)
  lazy val inputTable       = args(RasterConnectionParams.INPUT_TABLE)
  lazy val outputTable      = args(RasterConnectionParams.OUTPUT_TABLE)

  lazy val input   = AccumuloInputOptions(inputTable)
  lazy val output  = AccumuloOutputOptions(outputTable)
  lazy val options = AccumuloSourceOptions(instance, zookeepers, user, password, input, output)

  AccumuloSource(options)
      .map(('key, 'value) -> 'mutation) {
        (kv: (Key, Value)) => {
          GrayScaleOperation.colorKVtoGrayScaleMutation(kv._1, kv._2)
        }
    }.write(AccumuloSource(options))
  
}

object GrayScaleRasterJob extends RasterJob {
  conf.set("accumulo.monitor.address", "damaster")
  conf.set("mapreduce.framework.name", "yarn")
  conf.set("yarn.resourcemanager.address", "dresman:8040")
  conf.set("fs.defaultFS", "hdfs://dhead:54310")
  conf.set("yarn.resourcemanager.scheduler.address", "dresman:8030")

  override def runJob(): Unit = {
    JobUtils.setLibJars(conf)

    val args = buildArgs()

    val hdfsMode = Hdfs(strict = true, conf)
    val arguments = Mode.putMode(hdfsMode, args)

    val job = new GrayScaleRasterJob(arguments)
    val flow = job.buildFlow
    flow.complete() // this blocks until the job is done
  }

  override def buildArgs(): Args = {
    val args = new collection.mutable.ListBuffer[String]()

    args.append("--" + RasterConnectionParams.ZOOKEEPERS, "dzoo1")
    args.append("--" + RasterConnectionParams.ACCUMULO_INSTANCE, "dcloud")
    args.append("--" + RasterConnectionParams.ACCUMULO_USER, "root")
    args.append("--" + RasterConnectionParams.ACCUMULO_PASSWORD, "secret")
    args.append("--" + RasterConnectionParams.INPUT_TABLE, "AANNEX_SRI_ALL_VIS_RASTERS")
    args.append("--" + RasterConnectionParams.OUTPUT_TABLE, "AANNEX_SRI_ALL_VIS_RASTERS")

    Args(args)
  }

}


trait RasterJob {
  val conf = new Configuration
  def buildArgs(): Args = ???
  def runJob(): Unit = ???
}