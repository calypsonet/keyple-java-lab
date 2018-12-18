package org.cna.keyple.demo;


import org.eclipse.keyple.example.remote.transport.wspolling.client_retrofit.WsPollingRetrofitFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

@SuppressWarnings("PMD.AvoidUsingHardCodedIP")
public class Demo_Start_Server {


    public static Properties readConfig(String filename) throws Exception {
        Properties prop = new Properties();
        InputStream input = null;
        try {

            input = Demo_Start_Server.class.getClassLoader().getResourceAsStream(filename);
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
        String filename = "DemoAndroidMasterServer.properties";

        WsPollingRetrofitFactory transportFactory = new WsPollingRetrofitFactory(readConfig(filename), nodeId);

        DemoMaster master = new DemoMaster(transportFactory, true);

        master.boot();

    }
}
