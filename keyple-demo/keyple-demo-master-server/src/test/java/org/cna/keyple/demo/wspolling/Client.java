/*
 * Copyright (c) 2018 Calypso Networks Association https://www.calypsonet-asso.org/
 *
 * All rights reserved. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License version 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 */

package org.cna.keyple.demo.wspolling;

import org.cna.keyple.demo.london.wspolling.client.WsPClient;
import org.eclipse.keyple.plugin.remotese.common.DtoHandler;
import org.eclipse.keyple.plugin.remotese.common.KeypleDtoHelper;
import org.eclipse.keyple.plugin.remotese.common.TransportDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Client {

    private static final Logger logger = LoggerFactory.getLogger(Client.class);

    private static String BASE_URL = "http://localhost:8004";
    private static String ENDPOINT_URL = "/keypleDTO";
    private static String POLLING_URL = "/polling";



    void boot() {


        WsPClient client = new WsPClient(BASE_URL,ENDPOINT_URL, POLLING_URL, "test1");
        client.startPollingWorker("node1");
        client.setDtoHandler(new DtoHandler() {
            @Override
            public TransportDto onDTO(TransportDto message) {
                return new WsPTransportDTO(KeypleDtoHelper.NoResponse(), null);
            }
        });

    }

    void demo() {

    }

    public static void main(String[] args) throws Exception {


        Client client = new Client();
        client.boot();
        client.demo();


    }


}
