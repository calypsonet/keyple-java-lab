/*
 * Copyright (c) 2018 Calypso Networks Association https://www.calypsonet-asso.org/
 *
 * All rights reserved. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License version 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 */

package org.eclipse.keyple.example.remote;


import java.io.IOException;
import org.eclipse.keyple.example.remote.common.TransportFactory;
import org.eclipse.keyple.seproxy.exception.KeypleReaderException;
import org.eclipse.keyple.seproxy.exception.KeypleReaderNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DemoThreads {

    private static final Logger logger = LoggerFactory.getLogger(DemoThreads.class);


    static public void startServer(final Boolean isTransmitAsync, final Boolean isMaster,
            final TransportFactory factory) {
        Thread server = new Thread() {
            @Override
            public void run() {
                try {

                    logger.info("**** Starting Server Thread ****");

                    if (isMaster) {
                        DemoMaster master = new DemoMaster(factory, true, isTransmitAsync);
                        master.boot();

                    } else {
                        DemoSlave slave = new DemoSlave(factory, true);
                        logger.info("Wait for 5 seconds, then connectAReader to master");
                        Thread.sleep(5000);
                        slave.connectAReader();
                        logger.info("Wait for 5 seconds, then insert SE");
                        Thread.sleep(5000);
                        slave.insertSe();
                        logger.info("Wait for 5 seconds, then remove SE");
                        Thread.sleep(5000);
                        slave.removeSe();
                        logger.info("Wait for 5 seconds, then disconnect reader");
                        Thread.sleep(5000);
                        slave.disconnect();
                    }

                } catch (KeypleReaderNotFoundException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (KeypleReaderException e) {
                    e.printStackTrace();
                }

            }
        };

        server.start();
    };

    static public void startClient(final Boolean isTransmitAsync, final Boolean isMaster,
            final TransportFactory factory) {
        Thread client = new Thread() {
            @Override
            public void run() {
                logger.info("**** Starting Client Thread ****");

                try {
                    if (isMaster) {
                        DemoMaster master = new DemoMaster(factory, false, isTransmitAsync);
                        master.boot();
                    } else {
                        DemoSlave slave = new DemoSlave(factory, false);
                        slave.connectAReader();
                        logger.info("Wait for 5 seconds, then insert SE");
                        Thread.sleep(5000);
                        slave.insertSe();
                        logger.info("Wait for 5 seconds, then remove SE");
                        Thread.sleep(5000);
                        slave.removeSe();
                        logger.info("Wait for 5 seconds, then disconnect reader");
                        Thread.sleep(5000);
                        slave.disconnect();
                    }

                } catch (KeypleReaderNotFoundException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (KeypleReaderException e) {
                    e.printStackTrace();
                }

            }
        };
        client.start();
    };


}
