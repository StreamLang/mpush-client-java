package com.mpush.test;

import com.mpush.api.Client;
import com.mpush.api.ClientListener;
//import com.mpush.api.Logger;
//import com.mpush.util.DefaultLogger;

import java.util.concurrent.*;

public class ClientTest {
    private static final String publicKey = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCghPCWCobG8nTD24juwSVataW7iViRxcTkey/B792VZEhuHjQvA3cAJgx2Lv8GnX8NIoShZtoCg3Cx6ecs+VEPD2fBcg2L4JK7xldGpOJ3ONEAyVsLOttXZtNXvyDZRijiErQALMTorcgi79M5uVX9/jMv2Ggb2XAeZhlLD28fHwIDAQAB/B792VZEhuHjQvA3cAJgx2Lv8GnX8NIoShZtoCg3Cx6ecs+VEPD2fBcg2L4JK7xldGpOJ3ONEAyVsLOttXZtNXvyDZRijiErQALMTorcgi79M5uVX9/jMv2Ggb2XAeZhlLD28fHwIDAQAB";//公钥对应服务端的私钥
//    private static final String allocServer = "http://127.0.0.1:9999/";//用于获取MPUSH server的ip:port, 用于负载均衡

    public static void main(String[] args) throws Exception {
        int clientNum = 10000;
        int everyThreadPushAmount = 500;
        String serverHost = "127.0.0.1";
//        boolean enableLog = false;
        String sessionStorage = "/home/streamliu/mpush/client/session/";
        if (args != null && args.length > 0) {
            clientNum = Integer.parseInt(args[0]);
            if (args.length > 1) {
                everyThreadPushAmount = Integer.valueOf(args[1]);
            }
            if (args.length > 2) {
                serverHost = args[2];
            }
//            if (args.length > 3) {
//                enableLog = Boolean.valueOf(args[3]);
//            }
            if (args.length > 3) {
                sessionStorage = args[3];
            }
        }
//
//        Logger logger = new DefaultLogger();
//        logger.enable(enableLog);


        int threadAmount = clientNum / everyThreadPushAmount;
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                clientNum, clientNum, 1,
                TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>()
        );
        ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
        ClientListener listener = new L(scheduledExecutor);

        for (int i = 0; i < threadAmount; i++) {
            ConcurrentClientBuilder clientBuilder = new ConcurrentClientBuilder(
                    publicKey, serverHost,
                    i * everyThreadPushAmount,
                    everyThreadPushAmount, listener/*, logger*/, sessionStorage);
            executor.execute(clientBuilder);
        }
    }

    public static class L implements ClientListener {
        private final ScheduledExecutorService scheduledExecutor;
        boolean flag = true;

        public L(ScheduledExecutorService scheduledExecutor) {
            this.scheduledExecutor = scheduledExecutor;
        }

        @Override
        public void onBind(boolean success, String userId) {

        }

        @Override
        public void onUnbind(boolean success, String userId) {

        }

        @Override
        public void onConnected(Client client) {
            flag = true;
        }

        @Override
        public void onDisConnected(Client client) {
            flag = false;
        }

        @Override
        public void onHandshakeOk(final Client client, final int heartbeat) {
            scheduledExecutor.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    client.healthCheck();
                }
            }, heartbeat, heartbeat, TimeUnit.MILLISECONDS);
        }

        @Override
        public void onReceivePush(Client client, byte[] content, int messageId) {
            if (messageId > 0) client.ack(messageId);
        }

        @Override
        public void onKickUser(String deviceId, String userId) {

        }
    }

}
