/*
 * Copyright (c) 2018 Calypso Networks Association https://www.calypsonet-asso.org/
 *
 * All rights reserved. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License version 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 */

package org.eclipse.keyple.example.remote.websocket;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Random;

import org.eclipse.keyple.example.remote.common.ClientNode;
import org.eclipse.keyple.example.remote.common.ServerNode;
import org.eclipse.keyple.example.remote.common.TransportFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Web socket factory, by default works at localhost
 */
@SuppressWarnings("PMD.AvoidUsingHardCodedIP")
public class WskFactory extends TransportFactory {

    Integer port = 8000 + new Random().nextInt((100 - 0) + 1) + 0;
    String keypleUrl = "/keypleDTO";
    String bindUrl = "0.0.0.0";
    String protocol = "http://";
    String clientNodeId = "local1";

    private static final Logger logger = LoggerFactory.getLogger(WskFactory.class);


    @Override
    public ClientNode getClient(Boolean isMaster) {

        logger.info("*** Create Websocket Client ***");


        ClientNode wskClient = null;
        try {
            wskClient = new WskClient(new URI(protocol + "localhost:" + port + keypleUrl),clientNodeId);
            // wskClient.connect();
            return wskClient;
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return null;
        }

    }

    @Override
    public ServerNode getServer(Boolean isMaster) throws IOException {

        logger.info("*** Create Websocket Server ***");

        InetSocketAddress inet = new InetSocketAddress(Inet4Address.getByName(bindUrl), port);
        WskServer wskServer = new WskServer(inet, !isMaster,clientNodeId+"server");
        return wskServer;
    }
}
