/*
 * Copyright (c) 2018 Calypso Networks Association https://www.calypsonet-asso.org/
 *
 * All rights reserved. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License version 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 */

package org.eclipse.keyple.example.remote;

import java.io.IOException;
import org.eclipse.keyple.example.remote.common.ClientNode;
import org.eclipse.keyple.example.remote.common.ServerNode;
import org.eclipse.keyple.example.remote.common.TransportFactory;
import org.eclipse.keyple.example.remote.sample.CommandSample;
import org.eclipse.keyple.plugin.remote_se.rse.RsePlugin;
import org.eclipse.keyple.plugin.remote_se.rse.RseReader;
import org.eclipse.keyple.plugin.remote_se.rse.VirtualSeRemoteService;
import org.eclipse.keyple.plugin.remote_se.transport.TransportNode;
import org.eclipse.keyple.seproxy.ReaderPlugin;
import org.eclipse.keyple.seproxy.SeProxyService;
import org.eclipse.keyple.seproxy.event.PluginEvent;
import org.eclipse.keyple.seproxy.event.ReaderEvent;
import org.eclipse.keyple.seproxy.exception.KeyplePluginNotFoundException;
import org.eclipse.keyple.seproxy.exception.KeypleReaderNotFoundException;
import org.eclipse.keyple.util.Observable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DemoMaster is the Ticketing Terminal
 * This is where virtual readers {@link RseReader} are created into the Remote Se Plugin {@link RsePlugin}
 */
public class DemoMaster implements org.eclipse.keyple.util.Observable.Observer {

    private static final Logger logger = LoggerFactory.getLogger(DemoMaster.class);

    //TransportNode used as to send and receive KeypleDto to Slaves
    private TransportNode node;

    //For demo testing, use blocking or non blocking transmit API
    private Boolean transmitSync;


    /**
     * Constructor of the DemoMaster thread
     * Starts a transport node, can be server or client
     * 
     * @param transportFactory : type of transport used (websocket, webservice...)
     * @param isServer : is Master the server?
     * @param transmitSync : should we used blocking or non blocking transmit API
     */
    public DemoMaster(TransportFactory transportFactory, Boolean isServer, Boolean transmitSync) {

        this.transmitSync = transmitSync;

        logger.info("*******************");
        logger.info("Create DemoMaster  ");
        logger.info("*******************");

        if (isServer) {
            //Master is server, start Server and wait for Slave Clients
            try {
                node = transportFactory.getServer(true);
                // start server in a new thread
                new Thread() {
                    @Override
                    public void run() {
                        ((ServerNode) node).start();
                        logger.info("Waits for remote connections");
                    }

                }.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            //Master is client, connectAReader to Slave Server
            node = transportFactory.getClient(true);
            ((ClientNode) node).connect();
        }
    }

    public void boot() throws IOException {


        logger.info("Create VirtualSeRemoteService, start plugin");
        //Create virtualSeRemoteService with a DtoSender
        //Dto Sender is required so virtualSeRemoteService can send KeypleDTO to Slave
        //In this case, node is used as the dtosender (can be client or server)
        VirtualSeRemoteService virtualSeRemoteService = new VirtualSeRemoteService(SeProxyService.getInstance(), node);

        //observe remote se plugin for events
        logger.info("Observe SeRemotePLugin for Plugin Events and Reader Events");
        ReaderPlugin rsePlugin = virtualSeRemoteService.getPlugin();
        ((Observable) rsePlugin).addObserver(this);

        //Binds virtualSeRemoteService to a TransportNode so virtualSeRemoteService receives incoming KeypleDto from Slaves
        //in this case we binds it to node (can be client or server)
        virtualSeRemoteService.bindDtoEndpoint(node);


    }



    /**
     * Receives Event from RSE Plugin
     *
     * @param o : can be a ReaderEvent or PluginEvent
     */
    @Override
    public void update(final Object o) {
        logger.debug("UPDATE {}", o);
        final DemoMaster masterThread = this;

        // Receive a PluginEvent
        if (o instanceof PluginEvent) {
            PluginEvent event = (PluginEvent) o;
            switch (event.getEventType()) {
                case READER_CONNECTED:
                    //a new virtual reader is connected, let's observe it readers event
                    logger.info("READER_CONNECTED {} {}", event.getPluginName(),
                            event.getReaderName());
                    try {
                        RsePlugin rsePlugin = (RsePlugin) SeProxyService.getInstance()
                                .getPlugin("RemoteSePlugin");
                        RseReader rseReader =
                                (RseReader) rsePlugin.getReader(event.getReaderName());

                        //observe reader events
                        logger.info("Add ServerTicketingApp as a Observer of RSE reader");
                        rseReader.addObserver(masterThread);

                    } catch (KeypleReaderNotFoundException e) {
                        logger.error(e.getMessage());
                        e.printStackTrace();
                    } catch (KeyplePluginNotFoundException e) {
                        logger.error(e.getMessage());
                        e.printStackTrace();
                    }


                    break;
                case READER_DISCONNECTED:
                    logger.info("READER_DISCONNECTED {} {}", event.getPluginName(),
                            event.getReaderName());
                    break;
            }
        }
        // ReaderEvent
        else if (o instanceof ReaderEvent) {
            ReaderEvent event = (ReaderEvent) o;
            switch (event.getEventType()) {
                case SE_INSERTED:
                    logger.info("SE_INSERTED {} {}", event.getPluginName(),
                            event.getReaderName());

                    if (transmitSync) {
                        //test command with blocking transmit
                        CommandSample.transmit(logger, event.getReaderName());
                    } else {
                        //test command with non-blocking transmit
                        CommandSample.asyncTransmit(logger, event.getReaderName());
                    }
                    break;
                case SE_REMOVAL:
                    logger.info("SE_REMOVAL {} {}", event.getPluginName(),
                            event.getReaderName());
                    break;
                case IO_ERROR:
                    logger.info("IO_ERROR {} {}", event.getPluginName(),
                            event.getReaderName());
                    break;
            }
        }
    }

}
