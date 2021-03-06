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


import com.mpush.api.connection.Connection;
import com.mpush.api.protocol.Command;
import com.mpush.api.protocol.Packet;
import com.mpush.client.ClientConfig;
import com.mpush.message.OkMessage;
import org.apache.log4j.Logger;

/**
 * Created by ohun on 2015/12/30.
 *
 * @author ohun@live.cn (夜色)
 */
public final class OkMessageHandler extends BaseMessageHandler<OkMessage> {
    private static final Logger logger = Logger.getLogger(OkMessageHandler.class);
    private final ClientConfig clientConfig;

    public OkMessageHandler(ClientConfig clientConfig) {
        this.clientConfig = clientConfig;
    }

    @Override
    public OkMessage decode(Packet packet, Connection connection) {
        return new OkMessage(packet, connection);
    }

    @Override
    public void handle(OkMessage message) {
        if (message.cmd == Command.BIND.cmd) {
           clientConfig.getClientListener().onBind(true, message.getConnection().getSessionContext().bindUser);
        } else if (message.cmd == Command.UNBIND.cmd) {
           clientConfig.getClientListener().onUnbind(true, null);
        }

        logger.debug(String.format(">>> receive ok message=%s", message));
    }
}
