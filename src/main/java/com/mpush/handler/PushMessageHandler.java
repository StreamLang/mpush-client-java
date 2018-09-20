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


import com.alibaba.fastjson.JSON;
import com.mpush.api.ClientListener;
import com.mpush.api.Constants;
import com.mpush.api.connection.Connection;
import com.mpush.api.protocol.Packet;
import com.mpush.client.ClientConfig;
import com.mpush.message.AckMessage;
import com.mpush.message.PushMessage;
import org.apache.log4j.Logger;

/**
 * Created by ohun on 2015/12/30.
 *
 * @author ohun@live.cn (夜色)
 */
public final class PushMessageHandler extends BaseMessageHandler<PushMessage> {
    private static final Logger logger = Logger.getLogger(PushMessageHandler.class);
    private final ClientListener listener = ClientConfig.I.getClientListener();

    @Override
    public PushMessage decode(Packet packet, Connection connection) {
        return new PushMessage(packet, connection);
    }

    @Override
    public void handle(PushMessage message) {
        logger.debug(String.format(">>> receive push message=%s", message.content.length));
        long now = System.currentTimeMillis();
        String content = new String(message.content, Constants.UTF_8);
        long push = JSON.parseObject(content).getJSONObject("content").getLongValue("content");
        logger.info(String.format("receive push message,cost{%d}ms ,receiveTime{%d}, content={%s}"
                , now - push, now, content));
        listener.onReceivePush(message.getConnection().getClient(),
                message.content,
                message.bizAck() ? message.getSessionId() : 0);
        if (message.autoAck()) {
            AckMessage.from(message).sendRaw();
            logger.debug(String.format("<<< send ack for push messageId=%d", message.getSessionId()));
        }
    }
}
