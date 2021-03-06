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
import com.mpush.api.protocol.Packet;
import com.mpush.client.ClientConfig;
import com.mpush.message.FastConnectOkMessage;
import org.apache.log4j.Logger;

//import com.mpush.api.Logger;

/**
 * Created by ohun on 2016/1/23.
 *
 * @author ohun@live.cn (夜色)
 */
public final class FastConnectOkHandler extends BaseMessageHandler<FastConnectOkMessage> {
    private static final Logger logger = Logger.getLogger(FastConnectOkHandler.class);
    private final ClientConfig clientConfig;

    public FastConnectOkHandler(ClientConfig clientConfig) {
        this.clientConfig = clientConfig;
    }

    @Override
    public FastConnectOkMessage decode(Packet packet, Connection connection) {
        return new FastConnectOkMessage(packet, connection);
    }

    @Override
    public void handle(FastConnectOkMessage message) {
       logger.debug(String.format(">>> fast connect ok, message=%s", message));
        message.getConnection().getSessionContext().setHeartbeat(message.heartbeat);
        ClientListener listener = clientConfig.getClientListener();
        listener.onHandshakeOk(message.getConnection().getClient(), message.heartbeat);

    }
}
