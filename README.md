# keyple-java-lab

This repository is used as a lab to develop and to test prototypes. It's structure is the same as keyple-java to facilitate the migration of modules.



## Project : Remote SE plugin

This projects aims at prototyping a Remote SE Plugin and multiple transports layer (websocket, web service REST). A Slave terminal exposes one or multiple Native Reader to a Master Terminal. The Master Terminal interacts with those readers as if they were local, in a sense they are virtualized.

The configuration in both side is rather simple. 

### Slave terminal configuration

Slave terminal should declare the readers to the SeProxyService via the plugin mecanism. Then, it connects one or multple readers using the NativeSeREmoteService. 

```java
    //Get a reference to the ProxyReader (in this example, a stubReader)
    ProxyReader localReader = (StubReader) stubPlugin.getReader("stubReaderTest");
    
    //instanciate the NativeRemoteSeService with a NodeConfiguration (needed to connect to Master)
    NativeSeRemoteService seRemoteService = new NativeSeRemoteService(node);
    
    //Connect the reader with a NodeId (terminalId) and a Map of options
    seRemoteService.connectReader(nodeId, localReader, options);

```

### Master terminal configuration

From the Master terminal, a similar service is used : VirtualSeRemoteService. Instantiate this Service with a NodeConfiguration, and wait for Slaves to connect to your Remote Plugin. PLUGIN EVENTS and READER EVENTS are received as if the remote readers were local.

This module contains the core of the Remote Se Plugin Mecanism in both Master side and Client side.

Master side : 

```java
    //Instanciate the VirtualSeRemoteService with a NodeConfiguration (needed to connect to Slave)
    VirtualSeRemoteService virtualSeRemoteService = new VirtualSeRemoteService(SeProxyService.getInstance(), node);
    
    //Get the instanciate RemoteSe PLugin
    ReaderPlugin rsePlugin = virtualSeRemoteService.getPlugin();
    
    //Observe the plugin for events like READER_CONNECTED or SE_INSERTED 
    ((Observable) rsePlugin).addObserver(this);

``` 


### Processing events from Master


Once the configuration of the VirtualSeRemoteService done, events are sent to observers of the Remote Se Plugin as if connecting readers were local. 

```java

    ReaderPlugin rsePlugin = virtualSeRemoteService.getPlugin();

    //Observe the plugin for events like READER_CONNECTED or SE_INSERTED 
    ((Observable) rsePlugin).addObserver(this);
    
    /**
     * Receives Event from RSE Plugin
     * @param o : can be a ReaderEvent or PluginEvent
     */
    @Override
    public void update(final Object o) throws Exception {
        logger.debug("UPDATE {}", o);
        final DemoMaster masterThread = this;

        // Receive a PluginEvent
        if (o instanceof PluginEvent) {
            PluginEvent event = (PluginEvent) o;
            switch (event.getEventType()) {
                case READER_CONNECTED:
                    //a new virtual reader is connected, let's observe it
                        logger.info("Add ServerTicketingApp as a Observer of RSE reader");
                        rsePlugin.getReader(event.getReaderName()).addObserver(masterThread);
                    break;
                //.. more events
            }
        }
        // ReaderEvent
        else if (o instanceof ReaderEvent) {
            ReaderEvent event = (ReaderEvent) o;
            switch (event.getEventType()) {
                case SE_INSERTED:
                    //do somehting when a SE is inserted in the remote slave reader
                    break;
            }
            //.. more events
        }
    }
    
    
```

### Configuring the nodes

While configuring both services in master and slaves sides, you need to configure two Interfaces so what they can discuss together ! At the most abstract level, each side should send messages to the other side. Those message are encoded into ```KeypleDto```Objects. To be send and receive each Terminal will use a ```Dtosender``` to sends KeypleDto and a ```Dtosender```to receive KeypleDto. 
To make it easier to implement it on simple use cases, you can use the ````TransportNode```` interface that join a ```DtoSender``` and a ```DtoDispatcher``` on a single interface. 

Examples : 

The project org.eclipse.keyple.example.remote.websocket implements a transport layer based on web socket protocol where ```WskClient``` is a client ```TransportNode``` and ```WskServer``` is a server ```TransportNode```. Those two objects are meant to discuss with each other through ```Dtosender``` and ```Dtosender```, then can be used as node for the whole Remote Se plugin configuration.

A similar example can be found in the org.eclipse.keyple.example.remote.wspolling package


## Project : Remote Plugin Examples


