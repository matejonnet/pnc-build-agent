/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015 Red Hat, Inc., and individual contributors
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

import io.termd.core.http.HttpTtyConnection;
import io.termd.core.tty.TtyConnection;
import io.termd.core.util.Vector;
import io.undertow.websockets.core.AbstractReceiveListener;
import io.undertow.websockets.core.BufferedBinaryMessage;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;
import org.jboss.pnc.buildagent.api.ResponseMode;
import org.jboss.pnc.buildagent.server.BuildAgentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.ChannelListener;
import org.xnio.Pooled;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class WebSocketTtyConnection extends HttpTtyConnection implements TtyConnection {

    private static Logger log = LoggerFactory.getLogger(WebSocketTtyConnection.class);

    private WebSocketChannel webSocketChannel;
    private ResponseMode responseMode;
    private final ScheduledExecutorService executor;

    private Runnable onStdOutCompleted;

    public WebSocketTtyConnection(ScheduledExecutorService executor, Runnable onStdOutCompleted) {
        super(StandardCharsets.UTF_8, new Vector(Integer.MAX_VALUE, Integer.MAX_VALUE));
        this.executor = executor;
        this.onStdOutCompleted = onStdOutCompleted;
    }

    protected void write(byte[] buffer) {
        if (isOpen()) {
            if (ResponseMode.TEXT.equals(responseMode)) {
                WebSockets.sendText(new String(buffer, StandardCharsets.UTF_8), webSocketChannel, null);
            } else if (ResponseMode.BINARY.equals(responseMode)) {
                WebSockets.sendBinary(ByteBuffer.wrap(buffer), webSocketChannel, null);
            } else if (ResponseMode.SILENT.equals(responseMode)) {
                //do not send the response
                log.info("Master connection is in silent mode, no response will be sent over this channel.");
            } else {
                log.error("Invalid response mode.");
            }
        }
        if (new String(buffer).equals("% ")) {
            log.info("Prompt ready.");
            onStdOutCompleted.run();
        }
    }

    @Override
    public void execute(Runnable task) {
        executor.execute(task);
    }

    @Override
    public void schedule(Runnable task, long delay, TimeUnit unit) {
        executor.schedule(task, delay, unit);
    }

    private void registerWebSocketChannelListener(WebSocketChannel webSocketChannel) {
        ChannelListener<WebSocketChannel> listener = new AbstractReceiveListener() {

            @Override
            protected void onFullBinaryMessage(WebSocketChannel channel, BufferedBinaryMessage message) throws IOException {
                log.trace("Server received full binary message");
                Pooled<ByteBuffer[]> pulledData = message.getData();
                try {
                    ByteBuffer[] resource = pulledData.getResource();
                    ByteBuffer byteBuffer = WebSockets.mergeBuffers(resource);
                    writeToDecoder(byteBuffer);
                } catch (BuildAgentException e) {
                    log.error("Invalid request received.", e);
                } finally {
                    pulledData.discard();
                }
            }
        };
        webSocketChannel.getReceiveSetter().set(listener);
    }

    public void writeToDecoder(ByteBuffer byteBuffer) throws BuildAgentException {
        if (byteBuffer.capacity() == 1) { //handle events
            super.writeToDecoder(byteBuffer.array());
        } else {
            String msg = new String(byteBuffer.array());
            super.writeToDecoder(msg);
        }
    }


    public boolean isOpen() {
        return webSocketChannel != null && webSocketChannel.isOpen();
    }

    public void setWebSocketChannel(WebSocketChannel webSocketChannel, ResponseMode responseMode) {
        this.webSocketChannel = webSocketChannel;
        this.responseMode = responseMode;
        registerWebSocketChannelListener(webSocketChannel);
        webSocketChannel.resumeReceives();
    }

    public void removeWebSocketChannel() {
        webSocketChannel = null;
    }

    @Override
    public void close() {
        Consumer<Void> closeHandler = getCloseHandler();
        if (closeHandler != null) {
            closeHandler.accept(null);
        }
    }
}
