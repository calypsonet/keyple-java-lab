/*
 * Copyright (c) 2018 Calypso Networks Association https://www.calypsonet-asso.org/
 *
 * All rights reserved. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License version 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 */

package org.eclipse.keyple.example.remote.wspolling.client_retrofit;

import org.eclipse.keyple.example.remote.common.ClientNode;
import org.eclipse.keyple.example.remote.common.ServerNode;
import org.eclipse.keyple.example.remote.common.TransportFactory;
import org.eclipse.keyple.example.remote.wspolling.server.WsPServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Random;

/**
 * Web service factory, get @{@link org.eclipse.keyple.example.remote.wspolling.client_retrofit.WsPRetrofitClient} and {@link WsPServer}
 * Optimized for Android and Java
 */
@SuppressWarnings("PMD.AvoidUsingHardCodedIP")
public class WsPollingRetrofitFactory extends TransportFactory {

    Integer port = 8000 + new Random().nextInt((100 - 0) + 1) + 0;
    String pollingUrl = "/polling";
    String keypleUrl = "/keypleDTO";
    String clientNodeId = "local1";
    //String bindUrl = "192.168.0.12";
    String bindUrl = "0.0.0.0";
    String protocol = "http://";

    private static final Logger logger = LoggerFactory.getLogger(WsPollingRetrofitFactory.class);

    public WsPollingRetrofitFactory(){}

    public WsPollingRetrofitFactory(Integer port, String pollingUrl, String keypleUrl, String clientNodeId, String bindUrl, String protocol) {
        this.port = port;
        this.pollingUrl = pollingUrl;
        this.keypleUrl = keypleUrl;
        this.clientNodeId = clientNodeId;
        this.bindUrl = bindUrl;
        this.protocol = protocol;
    }

    @Override
    public ClientNode getClient(Boolean isMaster) {

        logger.info("*** Create RETROFIT Ws Polling Client ***");
        return new org.eclipse.keyple.example.remote.wspolling.client_retrofit.WsPRetrofitClient(protocol + bindUrl+":" + port , keypleUrl,
                pollingUrl, clientNodeId);
    }



    @Override
    public ServerNode getServer(Boolean isMaster) throws IOException {

        logger.info("*** Create Ws Polling Server ***");
        return new WsPServer(bindUrl, port, keypleUrl, pollingUrl,clientNodeId+"server");

    }
}
