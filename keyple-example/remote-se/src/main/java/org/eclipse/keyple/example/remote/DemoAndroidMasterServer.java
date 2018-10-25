package org.eclipse.keyple.example.remote;

import org.eclipse.keyple.example.remote.wspolling.client_retrofit.WsPollingRetrofitFactory;

@SuppressWarnings("PMD.AvoidUsingHardCodedIP")
public class DemoAndroidMasterServer {


    public static void main(String[] args) throws Exception {
        String keypleUrl = "/keypleDTO";
        String pollingUrl = "/polling";
        String protocol = "http://";
        String nodeId = "androidClient1";
        Integer port = 8007;
        String hostname = "192.168.0.12";

        WsPollingRetrofitFactory transportFactory = new WsPollingRetrofitFactory(port, pollingUrl, keypleUrl, nodeId, hostname, protocol);

        DemoMaster master = new DemoMaster(transportFactory, true, true);

        master.boot();


    }
}
