GeoMesa GeoServer UI
====================

BACKGROUND
----------

When you install the GeoMesa plugin for GeoServer, you get access to the
GeoMesa User Interface. The GeoMesa UI provides status on your GeoMesa
data stores and will eventually provide advanced features, such as
building indices on your data or calculating statistics on your common
queries.

This post outlines some of the features currently available through the
GeoMesa UI.

Instructions for installing the GeoMesa plugin in GeoServer are
available `here </geomesa-deployment/>`__, under 'Deploy GeoMesa
Accumulo Plugin to GeoServer'.

ACCESS TO THE USER INTERFACE
----------------------------

Once you have installed the GeoMesa plugin, there will be a GeoMesa menu
on the sidebar of the GeoServer administration interface:

.. figure:: ../_static/img/tutorials/2014-08-06-geomesa-ui/geoserver-menu.png
   :alt: "GeoMesa Menu"

   "GeoMesa Menu"

DATA STORE SUMMARY
------------------

Any GeoMesa data stores that you have added to GeoServer can be examined
on the Data Stores page. At the top of the page there is a table listing
all of your GeoMesa data stores. Underneath that, there are two charts.
The first chart shows you the number of records in your different
features. The second chart shows a dynamically-updating display of any
current ingestion.

.. note::

    In order for the ingest chart to display, the Accumulo monitor needs to be running and the
    configuration page needs to have the correct address for the monitor (see below).

Further down the page, statistics on each feature are displayed. Here
you can see the different tables used to store the feature in Accumulo,
as well as the number of tablets per table and the total number of
entries. By clicking on the 'Feature Attributes' link, you can see a
list of all the attributes for the feature, and an indication if they
are indexed for querying.

.. figure:: ../_static/img/tutorials/2014-08-06-geomesa-ui/geoserver-datastores.png
   :alt: "Hadoop Status"

   "Hadoop Status"

CONFIGURATION
-------------

To enable some features in the UI, you will need to set various
properties on the configuration page. Most of these properties
correspond to Hadoop properties, and they can be copied from your hadoop
configuration files. You can enter them by hand, or you can upload your
hadoop configuration files directly to the page. To do this, use the
'Load from XML' button.

.. figure:: ../_static/img/tutorials/2014-08-06-geomesa-ui/geoserver-config.png
   :alt: "GeoMesa Configuration"

   "GeoMesa Configuration"

HADOOP STATUS
-------------

Once the configuration is done, you can monitor the Hadoop cluster
status on the Hadoop Status page. Here you can see the load on your
cluster and any currently running jobs.

.. figure:: ../_static/img/tutorials/2014-08-06-geomesa-ui/geoserver-hadoop-status.png
   :alt: "Hadoop Status"

   "Hadoop Status"
