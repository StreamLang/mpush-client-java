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


//import com.mpush.api.Logger;
import com.mpush.api.connection.Connection;
import com.mpush.api.protocol.Command;
import com.mpush.api.protocol.Packet;
import com.mpush.client.ClientConfig;
import com.mpush.message.ErrorMessage;
import org.apache.log4j.Logger;

import static com.mpush.api.protocol.ErrorCode.REPEAT_HANDSHAKE;

/**
 * Created by ohun on 2015/12/30.
 *
 * @author ohun@live.cn (夜色)
 */
public final class ErrorMessageHandler extends BaseMessageHandler<ErrorMessage> {
    private static final Logger logger = Logger.getLogger(ErrorMessageHandler.class);
    @Override
    public ErrorMessage decode(Packet packet, Connection connection) {
        return new ErrorMessage(packet, connection);
    }

    @Override
    public void handle(ErrorMessage message) {
       logger.info(String.format(">>> receive an error message=%s", message));
        if (message.cmd == Command.FAST_CONNECT.cmd) {
            ClientConfig.I.getSessionStorage().clearSession();
            message.getConnection().getClient().handshake();
        } else if (message.cmd == Command.HANDSHAKE.cmd) {
            if (message.code != REPEAT_HANDSHAKE.errorCode //重复握手的错误消息直接忽略
                    && !REPEAT_HANDSHAKE.errorMsg.equals(message.reason)) {
                message.getConnection().getClient().stop();
            }
        } else {
            message.getConnection().reconnect();
        }
    }
}
