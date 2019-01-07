package org.cna.keyple.demo.integration;


import org.cna.keyple.demo.httpserver.DemoMaster;
import org.eclipse.keyple.example.remote.transport.wspolling.client_retrofit.WsPollingRetrofitFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

@SuppressWarnings("PMD.AvoidUsingHardCodedIP")
public class Demo_Start_Client {

    private static final Logger logger = LoggerFactory.getLogger(Demo_Start_Client.class);


    public static Properties readConfig(String filename) throws Exception {
        Properties prop = new Properties();
        InputStream input = null;
        try {

            input = Demo_Start_Client.class.getClassLoader().getResourceAsStream(filename);
            if(input==null){
                System.out.println("Sorry, unable to find " + filename);
                return null;
            }
            prop.load(input);
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return prop;
    }

    public static void main(String[] args) throws Exception {
        String nodeId = "androidClient1";
        String filename = "SlaveClient.properties";

        WsPollingRetrofitFactory transportFactory = new WsPollingRetrofitFactory(readConfig(filename), nodeId);

        DemoSlave slave = new DemoSlave(transportFactory, false);

        slave.connectAReader();

        logger.info("Please present a card (within 10 seconds)");

        Thread.sleep(10000);

        logger.info("Paying for 3 tickets");

        slave.payTicket(3);
        slave.getTransactionStatus();

        logger.info("Please present the same card to load it");


    }
}
