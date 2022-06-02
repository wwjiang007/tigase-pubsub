Offline Message Sink
~~~~~~~~~~~~~~~~~~~~~

Messages sent to offline users is published in pubsub node, from where that message is sent to all the node subscribers as a pubsub notification.

.. code:: xml

   <message from='pubsub.coffeebean.local' to='bard@shakespeare.lit' id='foo'>
     <event xmlns='http://jabber.org/protocol/pubsub#event'>
       <items node='message_sink'>
         <item id='ae890ac52d0df67ed7cfdf51b644e901'>
           <message type="chat" xmlns="jabber:client" id="x2ps6u0004"
             to="userB_h6x1bt0002@coffeebean.local"
             from="userA_uyhx8p0001@coffeebean.local/1149352695-tigase-20">
             <body>Hello</body>
           </message>
         </item>
       </items>
     </event>
   </message>


Configuration
''''''''''''''

The pubsub node must be created and configured beforehand:

**Create node**


::

   <iq type='set'
       to='pubsub.coffeebean.local'
       id='create1'>
     <pubsub xmlns='http://jabber.org/protocol/pubsub'>
       <create node='message_sink'/>
     </pubsub>
   </iq>

After that is done, you need to add SessionManager as a publisher:

**Add sess-man as publisher**

.. code:: xml

   <iq type='set'
       to='pubsub.coffeebean.local'
       id='ent2'>
     <pubsub xmlns='http://jabber.org/protocol/pubsub#owner'>
       <affiliations node='message_sink'>
         <affiliation jid='sess-man@coffeebean.local' affiliation='publisher'/>
       </affiliations>
     </pubsub>
   </iq>

Finally, the 'msgoffline' offline messages processor must be configured as well


**config.tdsl configuration**

::

   sess-man {
       msgoffline () {
           msg-pubsub-jid = 'pubsub.coffeebean.local'
           msg-pubsub-node = 'message_sink'
           msg-pubsub-publisher = 'sess-man@coffeebean.local'
       }
   }


**Usage**

Because these sinks use a standard pubsub component, administration of the sink node is identical to any other pubsub node. `XEP-0060 <http://www.xmpp.org/extensions/xep-0060>`__ defines standard pubsub usage and management.


**Managing Subscriptions**

Add new Subscriber

.. code:: xml

   <iq type='set'
       to='pubsub.coffeebean.local'
       id='subman2'>
     <pubsub xmlns='http://jabber.org/protocol/pubsub#owner'>
       <subscriptions node='message_sink'>
         <subscription jid='bard@shakespeare.lit' subscription='subscribed'/>
       </subscriptions>
     </pubsub>
   </iq>


Remove Subscriber

.. code:: xml

   <iq type='set'
       to='pubsub.coffeebean.local'
       id='subman2'>
     <pubsub xmlns='http://jabber.org/protocol/pubsub#owner'>
       <subscriptions node='message_sink'>
         <subscription jid='bard@shakespeare.lit' subscription='none'/>
       </subscriptions>
     </pubsub>
   </iq>
