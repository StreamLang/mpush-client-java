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

package com.mpush.handler;


import com.mpush.api.ClientListener;
import com.mpush.api.connection.Connection;
import com.mpush.api.connection.SessionContext;
import com.mpush.api.connection.SessionStorage;
import com.mpush.api.protocol.Packet;
import com.mpush.client.ClientConfig;
import com.mpush.message.HandshakeOkMessage;
import com.mpush.security.AesCipher;
import com.mpush.security.CipherBox;
import com.mpush.session.PersistentSession;
import org.apache.log4j.Logger;

//import com.mpush.api.Logger;

/**
 * Created by ohun on 2016/1/23.
 *
 * @author ohun@live.cn (夜色)
 */
public final class HandshakeOkHandler extends BaseMessageHandler<HandshakeOkMessage> {
    private static final Logger logger = Logger.getLogger(HandshakeOkHandler.class);
    private final ClientConfig clientConfig;

    public HandshakeOkHandler(ClientConfig clientConfig) {
        this.clientConfig = clientConfig;
    }

    @Override
    public HandshakeOkMessage decode(Packet packet, Connection connection) {
        return new HandshakeOkMessage(packet, connection);
    }

    @Override
    public void handle(HandshakeOkMessage message) {
        logger.debug(String.format(">>> handshake ok message=%s", message));

        Connection connection = message.getConnection();
        SessionContext context = connection.getSessionContext();
        byte[] serverKey = message.serverKey;
        if (serverKey.length != CipherBox.INSTANCE.getAesKeyLength()) {
            logger.debug(String.format("handshake error serverKey invalid message=%s", message));
            connection.reconnect();
            return;
        }
        //设置心跳
        context.setHeartbeat(message.heartbeat);

        //更换密钥
        AesCipher cipher = (AesCipher) context.cipher;
        byte[] sessionKey = CipherBox.INSTANCE.mixKey(cipher.key, serverKey);
        context.changeCipher(new AesCipher(sessionKey, cipher.iv));

        //触发握手成功事件

        ClientListener listener = clientConfig.getClientListener();
        listener.onHandshakeOk(connection.getClient(), message.heartbeat);

        //保存token
        saveToken(message, context);

    }

    private void saveToken(HandshakeOkMessage message, SessionContext context) {
        SessionStorage storage = clientConfig.getSessionStorage();
        if (storage == null || message.sessionId == null) return;
        PersistentSession session = new PersistentSession();
        session.sessionId = message.sessionId;
        session.expireTime = message.expireTime;
        session.cipher = context.cipher;
        storage.saveSession(PersistentSession.encode(session));
    }
}
