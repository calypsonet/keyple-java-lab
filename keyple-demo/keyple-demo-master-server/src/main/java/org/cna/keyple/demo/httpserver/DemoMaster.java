/********************************************************************************
 * Copyright (c) 2018 Calypso Networks Association https://www.calypsonet-asso.org/
 *
 * See the NOTICE file(s) distributed with this work for additional information regarding copyright
 * ownership.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.cna.keyple.demo.httpserver;

import java.io.IOException;
import java.util.Arrays;


import org.cna.keyple.demo.httpserver.transaction.SaleTransaction;
import org.cna.keyple.demo.httpserver.transaction.SaleTransactionManager;
import org.cna.keyple.demo.httpserver.transaction.TransactionEndpoint;
import org.cna.keyple.demo.ticketing.CalypsoInfo;
import org.cna.keyple.demo.ticketing.TicketingSession;
import org.cna.keyple.demo.ticketing.TicketingSessionManager;
import org.eclipse.keyple.example.calypso.common.transaction.CalypsoUtilities;
import org.eclipse.keyple.example.remote.transport.wspolling.server.WsPServer;
import org.eclipse.keyple.plugin.pcsc.PcscPlugin;
import org.eclipse.keyple.plugin.pcsc.PcscReader;
import org.eclipse.keyple.plugin.remotese.pluginse.RemoteSePlugin;
import org.eclipse.keyple.plugin.remotese.pluginse.VirtualReader;
import org.eclipse.keyple.plugin.remotese.pluginse.VirtualReaderService;
import org.eclipse.keyple.plugin.remotese.transport.ServerNode;
import org.eclipse.keyple.plugin.remotese.transport.TransportFactory;
import org.eclipse.keyple.plugin.remotese.transport.TransportNode;
import org.eclipse.keyple.seproxy.ChannelState;
import org.eclipse.keyple.seproxy.ReaderPlugin;
import org.eclipse.keyple.seproxy.SeProxyService;
import org.eclipse.keyple.seproxy.SeReader;
import org.eclipse.keyple.seproxy.event.PluginEvent;
import org.eclipse.keyple.seproxy.event.ReaderEvent;
import org.eclipse.keyple.seproxy.exception.KeypleBaseException;
import org.eclipse.keyple.seproxy.exception.KeyplePluginNotFoundException;
import org.eclipse.keyple.seproxy.exception.KeypleReaderException;
import org.eclipse.keyple.seproxy.exception.KeypleReaderNotFoundException;
import org.eclipse.keyple.seproxy.protocol.Protocol;
import org.eclipse.keyple.transaction.SeSelection;
import org.eclipse.keyple.transaction.SeSelector;
import org.eclipse.keyple.util.Observable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class DemoMaster implements  org.eclipse.keyple.util.Observable.Observer {

    private static final Logger logger = LoggerFactory.getLogger(DemoMaster.class);
    //private SeSelection seSelection;
    //private VirtualReader poReader;
    //private ReadRecordsRespPars readEnvironmentParser;
    SaleTransactionManager saleTransactionManager;
    TicketingSessionManager ticketingSessionManager;
    VirtualReaderService virtualReaderService;

    // TransportNode used as to send and receive KeypleDto to Slaves
    private WsPServer server;

    /**
     * Constructor of the DemoMaster thread Starts a common server, can be server or client
     *
     * @param transportFactory : type of transport used (websocket, webservice...)
     * @param isServer : is Master the server?
     */
    public DemoMaster(TransportFactory transportFactory, Boolean isServer) {

        logger.info("*******************");
        logger.info("Create DemoMaster  ");
        logger.info("*******************");

        // Master is server, start Server and wait for Slave Clients
        try {
            server = (WsPServer)transportFactory.getServer();

            // start server in a new thread
            new Thread() {
                @Override
                public void run() {
                    ((ServerNode) server).start();
                    logger.info("Waits for london connections");
                }

            }.start();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * Initiate {@link VirtualReaderService} with both ingoing and outcoming {@link TransportNode}
     */
    public void init() throws KeypleBaseException{

        /* init plugins */
        initPcscPlugin();
        initRemoteSePlugin();

        /* open physical channel to sam */
        SeReader samReader = initializeSamReader();

        /* init ticketing session manager */
        ticketingSessionManager = new TicketingSessionManager(samReader);

        /* init sale transaction manager */
        server.getHttpServer().createContext("/saleTransaction", new TransactionEndpoint(new SaleTransactionManager()));


    }

    private SeReader initializeSamReader() throws KeypleBaseException {

            SeReader samReader = CalypsoUtilities.getDefaultSamReader(SeProxyService.getInstance());

            logger.info("Connected sam reader name {}",samReader.getName());

            samReader.setParameter(PcscReader.SETTING_KEY_LOGGING, "true");
            samReader.setParameter(PcscReader.SETTING_KEY_PROTOCOL, PcscReader.SETTING_PROTOCOL_T0);

            return samReader;


    }





    /**
     * Process Events from {@link RemoteSePlugin} and {@link VirtualReader}
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
                    // a new virtual reader is connected, let's observe it readers event
                    logger.info("READER_CONNECTED {} {}", event.getPluginName(),
                            event.getReaderName());
                    try {
                        RemoteSePlugin remoteSEPlugin = (RemoteSePlugin) SeProxyService
                                .getInstance().getPlugin(RemoteSePlugin.PLUGIN_NAME);

                        SeReader poReader = remoteSEPlugin.getReader(event.getReaderName());

                        /* prepare reader and create ticketing session */
                        ticketingSessionManager.createTicketingSession(poReader);

                        // observe reader events
                        logger.info("Add ServerTicketingApp as a Observer of RSE reader");
                        ((Observable<ReaderEvent>)poReader).addObserver(masterThread);

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
            try {
                ReaderEvent event = (ReaderEvent) o;
                logger.info("{} {} {}",event.getEventType(), event.getPluginName(), event.getReaderName());

                switch (event.getEventType()) {
                    case SE_MATCHED:

                        break;
                    case SE_INSERTED:

                        VirtualReader virtualReader = virtualReaderService.getPlugin().getReaderByRemoteName(event.getReaderName());

                        TicketingSession ticketingSession = ticketingSessionManager.getTicketingSession(virtualReader.getName());

                        if (!ticketingSession.processDefaultSelection(event.getDefaultSelectionResponse())) {
                            logger.error("PO Not selected");
                            return;
                        }
                        if (!ticketingSession.getPoTypeName().equals("CALYPSO")) {
                            logger.info(ticketingSession.getPoTypeName() + " card" +
                                    " not supported in this demo");

                        } else {
                            logger.info("A Calypso PO selection succeeded. SN : {}", ticketingSession.getPoIdentification());

                            byte[] poSn = ticketingSession.getCardContent().getSerialNumber();

                            SaleTransaction saleTransaction = saleTransactionManager.getByPoReaderName(event.getReaderName());

                            if(saleTransaction==null){
                                //if  there is no transaction pending, create one
                                logger.info("A new transaction will be created");
                                saleTransactionManager.create(event.getReaderName());

                            }else{
                                if(!Arrays.equals(saleTransaction.getCardContent().getSerialNumber(), poSn)){
                                    //if a transaction is already pending with another PO in this reader, throw error message
                                    logger.info("A transaction is waiting for another PO, expected PO {}, presented PO {}", saleTransaction.getCardContent().getSerialNumber(), poSn);
                                }else{
                                    if(saleTransaction.getStatus() != SaleTransaction.WAIT_FOR_CARD_TO_LOAD){
                                    //if a transaction is pending with this PO, but there is no action required, do nothing
                                    logger.info("A transaction is pending for this PO, but not action required");

                                    }else{
                                        //if a transaction is waiting for this PO to be loaded, load it
                                        logger.info("A transaction is waiting to load this PO, expected PO {}, ticket number {}", saleTransaction.getTicketNumber());
                                        ticketingSession.loadTickets(saleTransaction.getTicketNumber());
                                    }
                                }
                            }





                        }
                        break;
                    case SE_REMOVAL:

                        break;
                    case IO_ERROR:


                        break;
                }
            } catch (KeypleReaderNotFoundException e) {
                e.printStackTrace();
            } catch (KeypleReaderException e) {
                e.printStackTrace();
            }
        }
    }


    private void initPcscPlugin() {
        logger.info("Instantiate the Keyple SeProxyService.");

        /* Instantiate SeProxyService and add PC/SC plugin */
        SeProxyService seProxyService = SeProxyService.getInstance();

        logger.info("Add the PC/SC plugin.");
        PcscPlugin pcscPlugin = PcscPlugin.getInstance();
        seProxyService.addPlugin(pcscPlugin);


        logger.info("Initialization done.");
    }

    private void initRemoteSePlugin() {
        logger.info("Create VirtualReaderService, start plugin");
        // Create virtualReaderService with a DtoSender
        // Dto Sender is required so virtualReaderService can send KeypleDTO to Slave
        // In this case, server is used as the dtosender (can be client or server)
        virtualReaderService =
                new VirtualReaderService(SeProxyService.getInstance(), server);

        // observe london se plugin for events
        logger.info("Observe SeRemotePlugin for Plugin Events and Reader Events");
        ReaderPlugin rsePlugin = virtualReaderService.getPlugin();

        //add rse plugin to seproxyservice
        SeProxyService.getInstance().addPlugin(rsePlugin);

        ((Observable) rsePlugin).addObserver(this);

        // Binds virtualReaderService to a TransportNode so virtualReaderService receives incoming
        // KeypleDto from Slaves
        // in this case we binds it to server (can be client or server)
        virtualReaderService.bindDtoEndpoint(server);

    }

    /**
     * Initialize the SAM channel
     *
     * @param samReader
     */
    public static void checkSamAndOpenChannel(SeReader samReader) throws KeypleReaderException{
        /*
         * check the availability of the SAM doing a ATR based selection, open its physical and
         * logical channels and keep it open
         */
        SeSelection samSelection = new SeSelection(samReader);

        SeSelector samSelector = new SeSelector(CalypsoInfo.SAM_C1_ATR_REGEX,
                ChannelState.KEEP_OPEN, Protocol.ANY, "Selection SAM C1");

        /* Prepare selector, ignore MatchingSe here */
        samSelection.prepareSelection(samSelector);


        if (!samSelection.processExplicitSelection()) {
            throw new IllegalStateException("Unable to open a logical channel for SAM!");
        }


    }

}
