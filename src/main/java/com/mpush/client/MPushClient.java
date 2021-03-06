/*
 * (C) Copyright 2015-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     ohun@live.cn (夜色)
 */

package com.mpush.client;


import com.mpush.api.Client;
import com.mpush.api.ack.AckCallback;
import com.mpush.api.ack.AckContext;
import com.mpush.api.connection.SessionContext;
import com.mpush.api.connection.SessionStorage;
import com.mpush.api.http.HttpRequest;
import com.mpush.api.http.HttpResponse;
import com.mpush.api.protocol.Command;
import com.mpush.api.protocol.Packet;
import com.mpush.api.push.PushContext;
import com.mpush.handler.HttpProxyHandler;
import com.mpush.message.*;
import com.mpush.security.AesCipher;
import com.mpush.security.CipherBox;
import com.mpush.session.PersistentSession;
import com.mpush.util.Strings;
import com.mpush.util.thread.ExecutorManager;
import org.apache.log4j.Logger;

import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.mpush.api.Constants.MAX_HB_TIMEOUT_COUNT;

//import com.mpush.api.Logger;

/**
 * Created by ohun on 2016/1/17.
 *
 * @author ohun@live.cn (夜色)
 */
/*package*/final class MPushClient implements Client, AckCallback {

    private enum State {Started, Shutdown, Destroyed}

    private final AtomicReference<State> clientState = new AtomicReference<>(State.Shutdown);

    private final TcpConnection connection;
    private final ClientConfig config;
    //    private final Logger logger;
    private static final Logger logger = Logger.getLogger(MPushClient.class);
    private int hbTimeoutTimes;

    private AckRequestMgr ackRequestMgr;
    private HttpRequestMgr httpRequestMgr;

    @Override
    public ClientConfig getConfig() {
        return config;
    }

    MPushClient(ClientConfig config) {
        this.config = config;

        MessageDispatcher receiver = new MessageDispatcher(config);

        if (config.isEnableHttpProxy()) {
            this.httpRequestMgr = HttpRequestMgr.I();
            receiver.register(Command.HTTP_PROXY, new HttpProxyHandler());
        }

        this.ackRequestMgr = AckRequestMgr.I();
        this.connection = new TcpConnection(this, receiver);
        this.ackRequestMgr.setConnection(this.connection);
    }

    @Override
    public void start() {
//        System.out.println(String.format("===start ==== userId:%s ==== deviceId:%s",
//                config.getUserId(), config.getDeviceId()));
//        if (!config.getUserId().equals(config.getDeviceId())) {
//        }

        if (clientState.compareAndSet(State.Shutdown, State.Started)) {
            connection.setAutoConnect(true);
            connection.connect();
            logger.debug("do start client ...");
        }
    }

    @Override
    public void stop() {
        logger.debug(String.format("client shutdown !!!, state=%s", clientState.get()));
        if (clientState.compareAndSet(State.Started, State.Shutdown)) {
            connection.setAutoConnect(false);
            connection.close();
        }
    }

    @Override
    public void destroy() {
        if (clientState.get() != State.Destroyed) {
            this.stop();
            logger.debug("client destroy !!!");
            ExecutorManager.INSTANCE.shutdown();
//            ClientConfig.I.destroy();
            clientState.set(State.Destroyed);
        }
    }

    @Override
    public boolean isRunning() {
        return clientState.get() == State.Started && connection.isConnected();
    }

    /**
     * 这个方法主要用于解决网络十分不稳定的场景：
     * 正常情况如果网络断开，就应该关闭连接，反之则应去建立连接
     * 但是在网络抖动厉害时就会发生连接频繁的建立／断开。
     * <p>
     * 处理这种场景的其中一个方案是：
     * 当网络断开时不主动关闭连接，而是尝试发送一次心跳检测，
     * 如果能收到响应，说明网络短时间内又恢复了，
     * 否则就断开连接，等待网络恢复并重建连接。
     *
     * @param isConnected true/false
     */
    @Override
    public void onNetStateChange(boolean isConnected) {
        connection.setAutoConnect(isConnected);
        logger.info(String.format("network state change, isConnected=%b, connection=%s", isConnected, connection));
        if (isConnected) { //当有网络时，去尝试重连
            connection.connect();
        } else if (connection.isConnected()) { //无网络，如果连接没有断开，尝试发送一次心跳检测，用于快速校验网络状况
            connection.resetTimeout();//心跳检测前，重置上次读写数据包的时间戳
            hbTimeoutTimes = MAX_HB_TIMEOUT_COUNT - 2;//总共要调用两次healthCheck，第一次用于发送心跳，第二次用于检测是否超时
            final ScheduledExecutorService timer = ExecutorManager.INSTANCE.getTimerThread();

            //隔3s后发送一次心跳检测，看能不能收到服务端的响应
            timer.schedule(new Runnable() {
                int checkCount = 0;

                @Override
                public void run() {
                    logger.debug(String.format("network disconnected, try test tcp connection checkCount=%d, connection=%s", checkCount, connection));
                    //如果期间连接状态发生变化，取消任务
                    if (connection.isAutoConnect() || !connection.isConnected()) return;

                    if (++checkCount <= 2) {
                        if (healthCheck() && checkCount < 2) {
                            timer.schedule(this, 3, TimeUnit.SECONDS);
                        }
                    }
                }

            }, 3, TimeUnit.SECONDS);
        }
    }

    @Override
    public boolean healthCheck() {

        if (connection.isReadTimeout()) {
            hbTimeoutTimes++;
            logger.warn(String.format("heartbeat timeout times=%s", hbTimeoutTimes));
        } else {
            hbTimeoutTimes = 0;
        }

        if (hbTimeoutTimes >= MAX_HB_TIMEOUT_COUNT) {
            logger.warn(String.format("heartbeat timeout times=%d over limit=%d, client restart", hbTimeoutTimes, MAX_HB_TIMEOUT_COUNT));
            hbTimeoutTimes = 0;
            connection.reconnect();
            return false;
        }

        if (connection.isWriteTimeout()) {
            logger.debug("<<< send heartbeat ping...");
            connection.send(Packet.HB_PACKET);
        }

        return true;
    }

    @Override
    public void fastConnect() {
        SessionStorage storage = config.getSessionStorage();
        if (storage == null) {
            handshake();
            return;
        }

        String ss = storage.getSession();
        if (Strings.isBlank(ss)) {
            handshake();
            return;
        }

        PersistentSession session = PersistentSession.decode(ss);
        if (session == null || session.isExpired()) {
            storage.clearSession();
            logger.warn(String.format("fast connect failure session expired, session=%s", session));
            handshake();
            return;
        }

        FastConnectMessage message = new FastConnectMessage(connection);
//        System.out.println(String.format("===fast connect==== userId:%s ==== deviceId:%s === object : %s",
//                config.getUserId(), config.getDeviceId(),this.toString()));
        message.deviceId = config.getDeviceId();
        message.sessionId = session.sessionId;
        message.maxHeartbeat = config.getMaxHeartbeat();
        message.minHeartbeat = config.getMinHeartbeat();
        message.encodeBody();
        ackRequestMgr.add(message.getSessionId(), AckContext
                .build(this)
                .setRequest(message.getPacket())
                .setTimeout(config.getHandshakeTimeoutMills())
                .setRetryCount(config.getHandshakeRetryCount())
        );
        logger.debug(String.format("<<< do fast connect, message=%s", message));
        message.sendRaw();
        connection.getSessionContext().changeCipher(session.cipher);
    }

    @Override
    public void handshake() {
        SessionContext context = connection.getSessionContext();
        context.changeCipher(CipherBox.INSTANCE.getRsaCipher());
        HandshakeMessage message = new HandshakeMessage(connection);
        message.clientKey = CipherBox.INSTANCE.randomAESKey();
        message.iv = CipherBox.INSTANCE.randomAESIV();
        message.deviceId = config.getDeviceId();
        message.osName = config.getOsName();
        message.osVersion = config.getOsVersion();
        message.clientVersion = config.getClientVersion();
        message.maxHeartbeat = config.getMaxHeartbeat();
        message.minHeartbeat = config.getMinHeartbeat();
        message.encodeBody();
        ackRequestMgr.add(message.getSessionId(), AckContext
                .build(this)
                .setRequest(message.getPacket())
                .setTimeout(config.getHandshakeTimeoutMills())
                .setRetryCount(config.getHandshakeRetryCount())
        );
        logger.debug(String.format("<<< do handshake, message=%s", message));
//        System.out.println(String.format("===hand shake==== userId:%s ==== deviceId:%s",
//                config.getUserId(), config.getDeviceId()));
        message.send();
        context.changeCipher(new AesCipher(message.clientKey, message.iv));
    }

    @Override
    public void bindUser(final String userId, final String tags) {
//        System.err.println("=========bind user id:" + userId);
        if (!connection.getSessionContext().handshakeOk()) {
            logger.error("connection handshake is not ok!");
            return;
        }

        if (Strings.isBlank(userId)) {
            logger.error("bind user is null");
            return;
        }
        SessionContext context = connection.getSessionContext();
        if (context.bindUser != null) {
            if (userId.equals(context.bindUser)) {//已经绑定
                if (tags != null && tags.equals(context.tags)) return;
            } else {
                unbindUser();//切换用户，要先解绑老用户
            }
        }
        context.setBindUser(userId).setTags(tags);
        config.setUserId(userId).setTags(tags);
        BindUserMessage message = BindUserMessage
                .buildBind(connection)
                .setUserId(userId)
                .setTags(tags);
        message.encodeBody();
        ackRequestMgr.add(message.getSessionId(), AckContext
                .build(this)
                .setRequest(message.getPacket())
                .setTimeout(config.getBindUserTimeoutMills())
                .setRetryCount(config.getBindUserRetryCount())
        );
        logger.debug(String.format("<<< do bind user, userId=%s", userId));
        message.send();

    }

    @Override
    public void unbindUser() {
        if (!connection.getSessionContext().handshakeOk()) {
            logger.error("connection  handshake is not ok!");
            return;
        }
        String userId = config.getUserId();
        if (Strings.isBlank(userId)) {
            logger.error("unbind user is null");
            return;
        }
        config.setUserId(null).setTags(null);
        connection.getSessionContext().setBindUser(null).setTags(null);
        BindUserMessage
                .buildUnbind(connection)
                .setUserId(userId)
                .send();
        logger.debug(String.format("<<< do unbind user, userId=%s", userId));
    }

    @Override
    public void ack(int messageId) {
        if (messageId > 0) {
            AckMessage ackMessage = new AckMessage(messageId, connection);
            ackMessage.sendRaw();
            logger.debug(String.format("<<< send ack for push messageId=%d", messageId));
        }
    }

    @Override
    public Future<Boolean> push(PushContext context) {
        if (!connection.getSessionContext().handshakeOk()) {
            logger.error("connection handshake is not ok!");
            return null;
        }
        PushMessage message = new PushMessage(context.content, connection);
        message.addFlag(context.ackModel.flag);
        message.send();
        logger.debug(String.format("<<< send push message=%s", message));
        return ackRequestMgr.add(message.getSessionId(), context);
    }

    @Override
    public Future<HttpResponse> sendHttp(HttpRequest request) {
        if (connection.getSessionContext().handshakeOk()) {
            HttpRequestMessage message = new HttpRequestMessage(connection);
            message.method = request.getMethod();
            message.uri = request.getUri();
            message.headers = request.getHeaders();
            message.body = request.getBody();
            message.send();
            logger.debug(String.format("<<< send http proxy, request=%s", request));
            return httpRequestMgr.add(message.getSessionId(), request);
        }
        return null;
    }

    @Override
    public void onSuccess(Packet response) {

    }

    @Override
    public void onTimeout(Packet request) {
        this.connection.reconnect();
    }
}
