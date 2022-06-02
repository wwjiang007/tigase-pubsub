Tigase’s Publish Subscribe component is an `XEP-0060 <http://www.xmpp.org/extensions/xep-0060.html>`__ compliant plugin handling all publish and subscribe activity within Tigase server. This is enabled as default with the pubsub name, however you may include the following line if you wish to customize it’s configuration.

.. code:: text

   pubsub () {}

You may change the name so long as you specify the pubsub class within parenthesis.