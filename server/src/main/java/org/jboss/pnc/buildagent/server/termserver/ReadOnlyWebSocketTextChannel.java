/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.pnc.buildagent.server.termserver;

import io.undertow.websockets.core.WebSocketCallback;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;
import org.jboss.pnc.buildagent.common.StringLiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class ReadOnlyWebSocketTextChannel implements ReadOnlyChannel {

    private static final Logger log = LoggerFactory.getLogger(ReadOnlyWebSocketTextChannel.class);

    private WebSocketChannel webSocketChannel;
    private final StringLiner stringLiner = new StringLiner();

    public ReadOnlyWebSocketTextChannel(WebSocketChannel webSocketChannel) {
        this.webSocketChannel = webSocketChannel;
    }

    @Override
    public void writeOutput(byte[] buffer) {
        String string = new String(buffer, StandardCharsets.UTF_8);
        log.trace("Appending to message [{}], raw [{}]", string, buffer);
        stringLiner.append(string);
        String line;
        while ((line = stringLiner.nextLine()) != null) {
            log.trace("Sending message [{}]", line);
            WebSockets.sendText(line, webSocketChannel, new WebSocketCallbackHandler());
        }
    }

    private static class WebSocketCallbackHandler implements WebSocketCallback  {
        @Override
        public void complete(WebSocketChannel webSocketChannel, Object o) {
        }

        @Override
        public void onError(WebSocketChannel webSocketChannel, Object o, Throwable throwable) {
            log.error("Error sending to WebSocket channel.", throwable);
        }
    }
}
