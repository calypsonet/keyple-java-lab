/*
 * Copyright (c) 2018 Calypso Networks Association https://www.calypsonet-asso.org/
 *
 * All rights reserved. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License version 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 */

package org.eclipse.keyple.example.remote.common;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;

import org.eclipse.keyple.example.pc.calypso.stub.se.StubHoplink;
import org.eclipse.keyple.plugin.remotese.nativese.NativeReaderServiceImpl;
import org.eclipse.keyple.plugin.remotese.transport.TransportNode;
import org.eclipse.keyple.plugin.stub.StubPlugin;
import org.eclipse.keyple.plugin.stub.StubProtocolSetting;
import org.eclipse.keyple.plugin.stub.StubReader;
import org.eclipse.keyple.seproxy.ReaderPlugin;
import org.eclipse.keyple.seproxy.SeProxyService;
import org.eclipse.keyple.seproxy.exception.KeypleReaderException;
import org.eclipse.keyple.seproxy.protocol.SeProtocolSetting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DemoSlave is where slave readers are physically located
 */
public class DemoSlave {

    private static final Logger logger = LoggerFactory.getLogger(DemoSlave.class);

    //physical reader, use a stubreader in our case
    StubReader localReader;

    //TransportNode used as to send and receive KeypleDto to Master
    private TransportNode node;

    //NativeReaderServiceImpl, used to connectAReader and disconnect readers
    private NativeReaderServiceImpl seRemoteService;

    //Client NodeId used to identify this terminal
    private String nodeId = "node1";

    public DemoSlave(TransportFactory transportFactory, Boolean isServer) {
        logger.info("*******************");
        logger.info("Create DemoSlave    ");
        logger.info("*******************");

        if (isServer) {
            //Slave is server, start Server and wait for Master clients
            try {
                node = transportFactory.getServer(false);
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
            //Slave is client, connectAReader to Master Server
            node = transportFactory.getClient(false);
            ((ClientNode) node).connect();
        }
    }

    public void connectAReader() throws KeypleReaderException, InterruptedException, IOException {


        logger.info("Boot DemoSlave LocalReader ");

        // get seProxyService
        SeProxyService seProxyService = SeProxyService.getInstance();

        logger.info("Create Local StubPlugin");
        StubPlugin stubPlugin = StubPlugin.getInstance();
        SortedSet<ReaderPlugin> plugins = SeProxyService.getInstance().getPlugins();
        plugins.add(stubPlugin);
        seProxyService.setPlugins(plugins);
        stubPlugin.plugStubReader("stubClientSlave");

        Thread.sleep(1000);

        // get the created proxy reader
        localReader = (StubReader) stubPlugin.getReader("stubClientSlave");

        localReader.addSeProtocolSetting(
                new SeProtocolSetting(StubProtocolSetting.SETTING_PROTOCOL_ISO14443_4)); //should be in master


        //Binds node for outgoing KeypelDto
        seRemoteService = new NativeReaderServiceImpl(node);

        //Binds node for incoming KeypleDTo
        seRemoteService.bindDtoEndpoint(node);

        //no options used so far
        Map<String, Object> options = new HashMap<String, Object>();

        //connect a reader to Remote Plugin
        logger.info("Connect remotely the StubPlugin ");
        seRemoteService.connectReader(nodeId, localReader, options);

    }

    public void insertSe() {
        logger.info("************************");
        logger.info("Start DEMO - insert SE  ");
        logger.info("************************");

        logger.info("Insert HoplinkStubSE into Local StubReader");
        // insert SE
        localReader.insertSe(new StubHoplink());


    }

    public void removeSe(){

        logger.info("************************");
        logger.info(" remove SE ");
        logger.info("************************");

        localReader.removeSe();

    }

    public void disconnect() throws KeypleReaderException {

        logger.info("*************************");
        logger.info("Disconnect native reader ");
        logger.info("*************************");

        seRemoteService.disconnectReader(nodeId, localReader);
    }




}
