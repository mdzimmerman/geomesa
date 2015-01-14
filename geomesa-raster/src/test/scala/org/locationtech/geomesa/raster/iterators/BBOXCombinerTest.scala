package org.locationtech.geomesa.raster.iterators

import org.apache.accumulo.core.data.Value
import org.junit.runner.RunWith
import org.locationtech.geomesa.utils.geohash.BoundingBox
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class BBOXCombinerTest extends Specification {
  sequential

  "BBOXCombiner" should {

    "covert a BoundingBox to a Value" in {
      val bbox = BoundingBox(0, 10, 0, 10)

      val value = BBOXCombiner.bboxToValue(bbox)

      value must beAnInstanceOf[Value]
    }

    "convert a Value into a BoundingBox" in {
      val value = new Value("POINT (0 0):POINT (10 10)".getBytes())

      val bbox = BBOXCombiner.valueToBbox(value)

      bbox must beAnInstanceOf[BoundingBox]
    }

    "convert a BoundingBox to a Value and then back again into a BoundingBox" in {
      val bbox = BoundingBox(0, 10, 0, 10)

      val value = BBOXCombiner.bboxToValue(bbox)

      val resbbox = BBOXCombiner.valueToBbox(value)

      bbox mustEqual(resbbox)
    }

  }

}
