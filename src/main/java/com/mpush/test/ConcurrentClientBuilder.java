package com.mpush.test;

import com.mpush.api.Client;
import com.mpush.api.ClientListener;
//import com.mpush.api.Logger;
import com.mpush.client.ClientConfig;
//import com.mpush.util.DefaultLogger;

public class ConcurrentClientBuilder implements Runnable {
    private String publicKey;
    private String serverHost;
    private Integer start;
    private Integer range;
    private ClientListener listener;
//    private Logger logger;
    private String sessionStorage;

    public ConcurrentClientBuilder(String publicKey, String serverHost, Integer start, Integer range, ClientListener listener/*, Logger logger*/, String sessionStorage) {
        this.publicKey = publicKey;
        this.serverHost = serverHost;
        this.start = start;
        this.range = range;
        this.listener = listener;
//        this.logger = logger;
        this.sessionStorage = sessionStorage;
    }

    @Override
    public void run() {
        System.out.println(String.format("================client %d to %d starting========", start, start + range));
        try {
            for (int i = start; i < start + range; i++) {
                String user = new String("user-" + i);
//                String device = new String("deviceId-test-"+user.toString());
//                System.err.println("==========="+user+"==========="+device+"=========");
//                Client client = ClientConfig
//                        .build()
                ClientConfig clientConfig = new ClientConfig();
                Client client = clientConfig.build(clientConfig)
                        .setPublicKey(publicKey)
                        //.setAllotServer(allocServer)
                        .setServerHost(serverHost)
                        .setServerPort(3000)
                        .setDeviceId(user)
                        .setOsName("Android")
                        .setOsVersion("6.0")
                        .setClientVersion("2.0")
                        .setUserId(user)
                        .setSessionStorageDir(sessionStorage + user)
//                        .setLogger(logger)
//                        .setLogEnabled(logger.isEnable())
                        .setEnableHttpProxy(true)
                        .setClientListener(listener)
                        .create();
                client.start();
                Thread.sleep(500);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println(String.format("================client %d to %d started========", start, start + range));
    }
}

