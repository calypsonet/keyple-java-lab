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
package org.cna.keyple.demo.integration;

import java.io.IOException;

import org.cna.keyple.demo.httpserver.transaction.SaleTransaction;
import org.eclipse.keyple.example.calypso.common.transaction.CalypsoUtilities;
import org.eclipse.keyple.example.remote.example.calypso.StubCalypsoClassic;
import org.eclipse.keyple.plugin.pcsc.PcscPlugin;
import org.eclipse.keyple.plugin.pcsc.PcscProtocolSetting;
import org.eclipse.keyple.plugin.pcsc.PcscReader;
import org.eclipse.keyple.plugin.remotese.nativese.NativeReaderServiceImpl;
import org.eclipse.keyple.plugin.remotese.transport.*;
import org.eclipse.keyple.plugin.stub.StubPlugin;
import org.eclipse.keyple.plugin.stub.StubProtocolSetting;
import org.eclipse.keyple.plugin.stub.StubReader;
import org.eclipse.keyple.plugin.stub.StubSecureElement;
import org.eclipse.keyple.seproxy.SeProxyService;
import org.eclipse.keyple.seproxy.SeReader;
import org.eclipse.keyple.seproxy.event.ObservablePlugin;
import org.eclipse.keyple.seproxy.event.ObservableReader;
import org.eclipse.keyple.seproxy.event.PluginEvent;
import org.eclipse.keyple.seproxy.exception.KeypleBaseException;
import org.eclipse.keyple.seproxy.exception.KeypleReaderException;
import org.eclipse.keyple.seproxy.protocol.SeProtocolSetting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


class DemoSlave {

    private static final Logger logger = LoggerFactory.getLogger(DemoSlave.class);

    // physical reader, in this case a StubReader
    private PcscReader poReader;

    // TransportNode used as to send and receive KeypleDto to Master
    private TransportNode node;

    // NativeReaderServiceImpl, used to connectAReader and disconnect readers
    private NativeReaderServiceImpl nativeReaderService;

    // Client NodeId used to identify this terminal
    private final String nodeId = "node1";


    /**
     * At startup, create the {@link TransportNode} object, either a {@link ClientNode} or a
     * {@link ServerNode}
     *
     * @param transportFactory : factory to get the type of transport needed (websocket,
     *        webservice...)
     * @param isServer : true if a Server is wanted
     */
    public DemoSlave(TransportFactory transportFactory, Boolean isServer) {
        logger.info("*******************");
        logger.info("Create DemoSlave    ");
        logger.info("*******************");

        if (isServer) {
            // Slave is server, start Server and wait for Master clients
            try {
                node = transportFactory.getServer();
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
            // Slave is client, connectAReader to Master Server
            node = transportFactory.getClient();
            ((ClientNode) node).connect(null);
        }
    }

    /**
     * Creates a {@link StubReader} and connects it to the Master terminal via the
     * {@link org.eclipse.keyple.plugin.remotese.nativese.NativeReaderService}
     *
     * @throws KeypleReaderException
     * @throws InterruptedException
     */
    public void connectAReader()
            throws KeypleBaseException, InterruptedException {


        logger.info("Boot DemoSlave LocalReader ");

        // get seProxyService
        SeProxyService seProxyService = SeProxyService.getInstance();

        logger.info("Create Local StubPlugin");
        PcscPlugin stubPlugin = PcscPlugin.getInstance();

        SeProxyService.getInstance().addPlugin(stubPlugin);

        ObservablePlugin.PluginObserver observer = new ObservablePlugin.PluginObserver() {
            @Override
            public void update(PluginEvent event) {
                logger.info("Update - pluginEvent from inline observer", event);
            }
        };

        // add observer to have the reader management done by the monitoring thread
        stubPlugin.addObserver(observer);

        // get the created proxy reader
        poReader = (PcscReader) initPoReader();

        poReader.addSeProtocolSetting(
                new SeProtocolSetting(StubProtocolSetting.SETTING_PROTOCOL_ISO14443_4)); // should
        // be in
        // master


        // Binds node for outgoing KeypleDto
        nativeReaderService = new NativeReaderServiceImpl(node);

        // Binds node for incoming KeypleDTo
        nativeReaderService.bindDtoEndpoint(node);

        // connect a reader to Remote Plugin
        logger.info("Connect remotely the StubPlugin ");
        nativeReaderService.connectReader(poReader, nodeId);

    }


    SeReader initPoReader() throws KeypleBaseException {

        SeReader poReader = CalypsoUtilities.getDefaultPoReader(SeProxyService.getInstance());

        //((ObservableReader) reader).addObserver(poReaderObserver);
        poReader.setParameter(PcscReader.SETTING_KEY_LOGGING, "true");
        poReader.setParameter(PcscReader.SETTING_KEY_PROTOCOL, PcscReader.SETTING_PROTOCOL_T1);
        /* Set the PO reader protocol flag for ISO14443 */
        poReader.addSeProtocolSetting(
                new SeProtocolSetting(PcscProtocolSetting.SETTING_PROTOCOL_ISO14443_4));
        /* Set the PO reader protocol flag for Mifare Classic */
        poReader.addSeProtocolSetting(
                new SeProtocolSetting(PcscProtocolSetting.SETTING_PROTOCOL_MIFARE_CLASSIC));

        return poReader;
    }


    public void payTicket(Integer ticketNumber){


    }

    public void resetTransaction(){


    }

    public SaleTransaction getTransactionStatus(){

        return new SaleTransaction("");
    }

    public void disconnect() throws KeypleReaderException, KeypleRemoteException {

        logger.info("*************************");
        logger.info("Disconnect native reader ");
        logger.info("*************************");

        nativeReaderService.disconnectReader(poReader, nodeId);
    }



}
