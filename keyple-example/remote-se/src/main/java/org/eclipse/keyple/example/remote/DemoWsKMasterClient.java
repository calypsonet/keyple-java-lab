/*
 * Copyright (c) 2018 Calypso Networks Association https://www.calypsonet-asso.org/
 *
 * All rights reserved. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License version 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 */

package org.eclipse.keyple.example.remote;

import org.eclipse.keyple.example.remote.common.TransportFactory;
import org.eclipse.keyple.example.remote.websocket.WskFactory;

public class DemoWsKMasterClient {

    //blocking : works
    //non blocking : todo

    public static void main(String[] args) throws Exception {

        TransportFactory factory = new WskFactory(); // Web socket
        Boolean isMasterServer = false; // DemoMaster is the Client (and DemoSlave the server)

        Boolean isTransmitSync = true; // is Transmit API Blocking or Not Blocking

        /**
         * DemoThreads
         */

        DemoThreads.startServer(isTransmitSync, isMasterServer, factory);
        Thread.sleep(1000);
        DemoThreads.startClient(isTransmitSync, !isMasterServer, factory);
    }
}
