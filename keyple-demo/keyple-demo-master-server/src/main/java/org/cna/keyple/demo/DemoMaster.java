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
package org.cna.keyple.demo;

import java.io.IOException;
import org.eclipse.keyple.calypso.command.po.parser.ReadDataStructure;
import org.eclipse.keyple.calypso.command.po.parser.ReadRecordsRespPars;
import org.eclipse.keyple.calypso.transaction.CalypsoPo;
import org.eclipse.keyple.calypso.transaction.PoSelector;
import org.eclipse.keyple.calypso.transaction.PoTransaction;
import org.eclipse.keyple.example.calypso.common.postructure.CalypsoClassicInfo;
import org.eclipse.keyple.example.remote.transport.wspolling.server.WsPServer;
import org.eclipse.keyple.plugin.remotese.pluginse.RemoteSePlugin;
import org.eclipse.keyple.plugin.remotese.pluginse.VirtualReader;
import org.eclipse.keyple.plugin.remotese.pluginse.VirtualReaderService;
import org.eclipse.keyple.plugin.remotese.transport.ServerNode;
import org.eclipse.keyple.plugin.remotese.transport.TransportFactory;
import org.eclipse.keyple.plugin.remotese.transport.TransportNode;
import org.eclipse.keyple.seproxy.ChannelState;
import org.eclipse.keyple.seproxy.ReaderPlugin;
import org.eclipse.keyple.seproxy.SeProxyService;
import org.eclipse.keyple.seproxy.event.ObservableReader;
import org.eclipse.keyple.seproxy.event.PluginEvent;
import org.eclipse.keyple.seproxy.event.ReaderEvent;
import org.eclipse.keyple.seproxy.exception.KeyplePluginNotFoundException;
import org.eclipse.keyple.seproxy.exception.KeypleReaderException;
import org.eclipse.keyple.seproxy.exception.KeypleReaderNotFoundException;
import org.eclipse.keyple.seproxy.protocol.ContactlessProtocols;
import org.eclipse.keyple.transaction.MatchingSe;
import org.eclipse.keyple.transaction.SeSelection;
import org.eclipse.keyple.transaction.SeSelector;
import org.eclipse.keyple.util.ByteArrayUtils;
import org.eclipse.keyple.util.Observable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class DemoMaster implements org.eclipse.keyple.util.Observable.Observer {

    private static final Logger logger = LoggerFactory.getLogger(DemoMaster.class);
    private SeSelection seSelection;
    private VirtualReader poReader;
    private ReadRecordsRespPars readEnvironmentParser;


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
    public void boot() {


        logger.info("Create VirtualReaderService, start plugin");
        // Create virtualReaderService with a DtoSender
        // Dto Sender is required so virtualReaderService can send KeypleDTO to Slave
        // In this case, server is used as the dtosender (can be client or server)
        VirtualReaderService virtualReaderService =
                new VirtualReaderService(SeProxyService.getInstance(), server);

        // observe london se plugin for events
        logger.info("Observe SeRemotePlugin for Plugin Events and Reader Events");
        ReaderPlugin rsePlugin = virtualReaderService.getPlugin();
        ((Observable) rsePlugin).addObserver(this);

        // Binds virtualReaderService to a TransportNode so virtualReaderService receives incoming
        // KeypleDto from Slaves
        // in this case we binds it to server (can be client or server)
        virtualReaderService.bindDtoEndpoint(server);


        TicketService ticketService = new TicketServiceMock();
        server.getHttpServer().createContext("/ticket", new TicketEndpoint(ticketService));


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
                                .getInstance().getPlugin("RemoteSePlugin");
                        poReader = (VirtualReader) remoteSEPlugin.getReader(event.getReaderName());

                        /* set default selection request */
                        seSelection = new SeSelection(poReader);

                        /*
                         * Setting of an AID based selection of a Calypso REV3 PO
                         *
                         * Select the first application matching the selection AID whatever the SE
                         * communication protocol keep the logical channel open after the selection
                         */

                        /*
                         * Calypso selection: configures a PoSelector with all the desired
                         * attributes to make the selection and read additional information
                         * afterwards
                         */
                        PoSelector poSelector =
                                new PoSelector(ByteArrayUtils.fromHex(CalypsoClassicInfo.AID),
                                        SeSelector.SelectMode.FIRST, ChannelState.KEEP_OPEN,
                                        ContactlessProtocols.PROTOCOL_ISO14443_4,
                                        "AID: " + CalypsoClassicInfo.AID);

                        /*
                         * Prepare the reading order and keep the associated parser for later use
                         * once the selection has been made.
                         */
                        readEnvironmentParser = poSelector.prepareReadRecordsCmd(
                                CalypsoClassicInfo.SFI_EnvironmentAndHolder,
                                ReadDataStructure.SINGLE_RECORD_DATA,
                                CalypsoClassicInfo.RECORD_NUMBER_1,
                                String.format("EnvironmentAndHolder (SFI=%02X))",
                                        CalypsoClassicInfo.SFI_EnvironmentAndHolder));

                        /*
                         * Add the selection case to the current selection (we could have added
                         * other cases here)
                         */
                        seSelection.prepareSelection(poSelector);

                        /*
                         * Provide the SeReader with the selection operation to be processed when a
                         * PO is inserted.
                         */
                        ((ObservableReader) poReader).setDefaultSelectionRequest(
                                seSelection.getSelectionOperation(),
                                ObservableReader.NotificationMode.MATCHED_ONLY);


                        // observe reader events
                        logger.info("Add ServerTicketingApp as a Observer of RSE reader");
                        poReader.addObserver(masterThread);

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
                case SE_MATCHED:
                    if (seSelection.processDefaultSelection(event.getDefaultSelectionResponse())) {
                        MatchingSe selectedSe = seSelection.getSelectedSe();

                        logger.info(
                                "Observer notification: the selection of the PO has succeeded.");

                        /*
                         * Retrieve the data read from the parser updated during the selection
                         * process
                         */
                        byte environmentAndHolder[] = (readEnvironmentParser.getRecords())
                                .get((int) CalypsoClassicInfo.RECORD_NUMBER_1);

                        /* Log the result */
                        logger.info("Environment file data: {}",
                                ByteArrayUtils.toHex(environmentAndHolder));

                        /* Go on with the reading of the first record of the EventLog file */
                        logger.info(
                                "==================================================================================");
                        logger.info(
                                "= 2nd PO exchange: reading transaction of the EventLog file.                     =");
                        logger.info(
                                "==================================================================================");

                        PoTransaction poTransaction =
                                new PoTransaction(poReader, (CalypsoPo) selectedSe);

                        /*
                         * Prepare the reading order and keep the associated parser for later use
                         * once the transaction has been processed.
                         */
                        ReadRecordsRespPars readEventLogParser =
                                poTransaction.prepareReadRecordsCmd(CalypsoClassicInfo.SFI_EventLog,
                                        ReadDataStructure.SINGLE_RECORD_DATA,
                                        CalypsoClassicInfo.RECORD_NUMBER_1,
                                        String.format("EventLog (SFI=%02X, recnbr=%d))",
                                                CalypsoClassicInfo.SFI_EventLog,
                                                CalypsoClassicInfo.RECORD_NUMBER_1));

                        /*
                         * Actual PO communication: send the prepared read order, then close the
                         * channel with the PO
                         */
                        try {
                            if (poTransaction.processPoCommands(ChannelState.CLOSE_AFTER)) {
                                logger.info("The reading of the EventLog has succeeded.");

                                /*
                                 * Retrieve the data read from the parser updated during the
                                 * transaction process
                                 */
                                byte eventLog[] = (readEventLogParser.getRecords())
                                        .get((int) CalypsoClassicInfo.RECORD_NUMBER_1);

                                /* Log the result */
                                logger.info("EventLog file data: {}",
                                        ByteArrayUtils.toHex(eventLog));
                            }
                        } catch (KeypleReaderException e) {
                            e.printStackTrace();
                        }
                        logger.info(
                                "==================================================================================");
                        logger.info(
                                "= End of the Calypso PO processing.                                              =");
                        logger.info(
                                "==================================================================================");
                    } else {
                        logger.error(
                                "The selection of the PO has failed. Should not have occurred due to the MATCHED_ONLY selection mode.");
                    }
                    break;
                case SE_INSERTED:
                    logger.info("SE_INSERTED {} {}", event.getPluginName(), event.getReaderName());

                    // Transmit a SeRequestSet to native reader
                    // CommandSample.transmit(logger, event.getReaderName());

                    break;
                case SE_REMOVAL:
                    logger.info("SE_REMOVAL {} {}", event.getPluginName(), event.getReaderName());
                    break;
                case IO_ERROR:
                    logger.info("IO_ERROR {} {}", event.getPluginName(), event.getReaderName());
                    break;
            }
        }
    }

}
