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

package org.jboss.pnc.buildagent.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jboss.pnc.buildagent.api.ResponseMode;
import org.jboss.pnc.buildagent.api.TaskStatusUpdateEvent;
import org.jboss.pnc.buildagent.common.ObjectWrapper;
import org.jboss.pnc.buildagent.common.Wait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.CloseReason;
import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;
import java.io.Closeable;
import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * @see "https://github.com/undertow-io/undertow/blob/5bdddf327209a4abf18792e78148863686c26e9b/websockets-jsr/src/test/java/io/undertow/websockets/jsr/test/BinaryEndpointTest.java"
 *
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class BuildAgentClient implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(BuildAgentClient.class);
    private final ResponseMode responseMode;
    private final boolean readOnly;

    ObjectWrapper<Boolean> isCommandPromptReady = new ObjectWrapper<>(false);

    private Client statusUpdatesClient;
    private Client commandExecutingClient;
    private Optional<Runnable> onCommandExecutionCompleted = Optional.empty();
    private boolean commandSent;

    public BuildAgentClient(String termBaseUrl,
                            Optional<Consumer<String>> responseDataConsumer,
                            Consumer<TaskStatusUpdateEvent> onStatusUpdate,
                            String commandContext
                        ) throws TimeoutException, InterruptedException {
        this(termBaseUrl, responseDataConsumer, onStatusUpdate, commandContext, ResponseMode.BINARY, false);
    }

    public BuildAgentClient(String termBaseUrl,
            Optional<Consumer<String>> responseDataConsumer,
            Consumer<TaskStatusUpdateEvent> onStatusUpdate,
            String commandContext,
            ResponseMode responseMode,
            boolean readOnly) throws TimeoutException, InterruptedException {

        this.responseMode = responseMode;
        this.readOnly = readOnly;

        Consumer<TaskStatusUpdateEvent> onStatusUpdateInternal = (event) -> {
            onStatusUpdate.accept(event);
        };

        statusUpdatesClient = connectStatusListenerClient(termBaseUrl, onStatusUpdateInternal, commandContext);
        commandExecutingClient = connectCommandExecutingClient(termBaseUrl, responseDataConsumer, commandContext);
    }

    public void setCommandCompletionListener(Runnable commandCompletionListener) {
        this.onCommandExecutionCompleted = Optional.of(commandCompletionListener);
    }

    public void executeCommand(String command) throws TimeoutException, BuildAgentClientException {
        log.info("Executing remote command [{}]...", command);
        RemoteEndpoint.Basic remoteEndpoint = commandExecutingClient.getRemoteEndpoint();

        ByteBuffer byteBuffer = prepareRemoteCommand(command);

        try {
            waitCommandPromptReady();
        } catch (InterruptedException e) {
            log.error("Interrupted while waiting for command prompt ready state.", e);
        }

        try {
            log.debug("Sending remote command...");
            remoteEndpoint.sendBinary(byteBuffer);
            isCommandPromptReady.set(false);
            commandSent = true;
        } catch (IOException e) {
            log.error("Cannot execute remote command.", e);
        }
    }

    public void executeNow(Object command) throws BuildAgentClientException { //TODO unify with executeCommand
        log.info("Executing remote command [{}]...", command);
        RemoteEndpoint.Basic remoteEndpoint = commandExecutingClient.getRemoteEndpoint();

        ByteBuffer byteBuffer = prepareRemoteCommand(command);

        try {
            log.debug("Sending remote command...");
            remoteEndpoint.sendBinary(byteBuffer);
            isCommandPromptReady.set(false);
            commandSent = true;
        } catch (IOException e) {
            log.error("Cannot execute remote command.", e);
        }
    }

    private ByteBuffer prepareRemoteCommand(Object command) throws BuildAgentClientException {
        Map<String, Object> cmdJson = new HashMap<>();
        cmdJson.put("action", "read");

        ByteBuffer byteBuffer;
        if (command instanceof String) {
            cmdJson.put("data", command + "\n");
            ObjectMapper mapper = new ObjectMapper();
            try {
                byteBuffer = ByteBuffer.wrap(mapper.writeValueAsBytes(cmdJson));
            } catch (JsonProcessingException e) {
                throw new BuildAgentClientException("Cannot serialize string command.", e);
            }
        } else {
            try {
                byteBuffer = ByteBuffer.allocate(1).put(((Integer)command).byteValue());
            } catch (BufferOverflowException | ClassCastException e) {
                throw new BuildAgentClientException("Invalid signal.", e);
            }
            byteBuffer.flip();
        }
        return byteBuffer;
    }

    private Client connectStatusListenerClient(String webSocketBaseUrl, Consumer<TaskStatusUpdateEvent> onStatusUpdate, String commandContext) {
        Client client = initializeDefault();
        Consumer<String> responseConsumer = (text) -> {
            log.trace("Decoding response: {}", text);

            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonObject = null;
            try {
                jsonObject = mapper.readTree(text);
            } catch (IOException e) {
                log.error( "Cannot read JSON string: " + text, e);
            }
            try {
                TaskStatusUpdateEvent taskStatusUpdateEvent = TaskStatusUpdateEvent.fromJson(jsonObject.get("event").toString());
                onStatusUpdate.accept(taskStatusUpdateEvent);
            } catch (IOException e) {
                log.error("Cannot deserialize TaskStatusUpdateEvent.", e);
            }
        };
        client.onStringMessage(responseConsumer);

        client.onClose(closeReason -> {
        });

        commandContext = formatCommandContext(commandContext);

        try {
            client.connect(stripEndingSlash(webSocketBaseUrl) + Client.WEB_SOCKET_LISTENER_PATH + commandContext);
        } catch (Exception e) {
            throw new AssertionError("Failed to connect to remote client.", e);
        }
        return client;
    }

    private Client connectCommandExecutingClient(String webSocketBaseUrl, Optional<Consumer<String>> responseDataConsumer, String commandContext) throws InterruptedException, TimeoutException {

        Client client = initializeDefault();

        if (ResponseMode.TEXT.equals(responseMode)) {
            registerTextResponseConsumer(responseDataConsumer, client);
        } else {
            registerBinaryResponseConsumer(responseDataConsumer, client);
        }

        client.onClose(closeReason -> {
            log.info("Client received close {}.", closeReason.toString());
        });

        String appendReadOnly = readOnly ? "/ro" : "";

        String webSocketPath;
        if (ResponseMode.TEXT.equals(responseMode)) {
            webSocketPath = stripEndingSlash(webSocketBaseUrl) + Client.WEB_SOCKET_TERMINAL_TEXT_PATH;
        } else {
            webSocketPath = stripEndingSlash(webSocketBaseUrl) + Client.WEB_SOCKET_TERMINAL_PATH;
        }

        commandContext = formatCommandContext(commandContext);

        try {
            client.connect(webSocketPath + commandContext + appendReadOnly);
        } catch (Exception e) {
            throw new AssertionError("Failed to connect to remote client.", e);
        }
        return client;
    }

    private String formatCommandContext(String commandContext) {
        if (commandContext != null && !commandContext.equals("")) {
            commandContext = "/" + commandContext;
        }
        return commandContext;
    }

    private String stripEndingSlash(String path) {
        return path.replaceAll("/$", "");
    }

    private void registerBinaryResponseConsumer(Optional<Consumer<String>> responseDataConsumer, Client client) {
        Consumer<byte[]> responseConsumer = (bytes) -> {
            String responseData = new String(bytes);
            if ("% ".equals(responseData)) {
                log.info("Binary consumer received command line 'ready'(%) marker.");
                onCommandPromptReady();
            } else {
                responseDataConsumer.ifPresent((rdc) -> rdc.accept(responseData));;
            }
        };
        client.onBinaryMessage(responseConsumer);
    }

    private void registerTextResponseConsumer(Optional<Consumer<String>> responseDataConsumer, Client client) {
        Consumer<String> responseConsumer = (string) -> {
            if ("% ".equals(string)) {
                log.info("Text consumer received command line 'ready'(%) marker.");
                onCommandPromptReady();
            } else {
                responseDataConsumer.ifPresent((rdc) -> rdc.accept(string));;
            }
        };
        client.onStringMessage(responseConsumer);
    }

    private void onCommandPromptReady() {
        isCommandPromptReady.set(true);
        if (commandSent) {
            onCommandExecutionCompleted.ifPresent((completed) -> completed.run());
        }
    }

    private void waitCommandPromptReady() throws TimeoutException, InterruptedException {
        log.trace("Waiting for commandPromptReady ... ");
        Wait.forCondition(() -> isCommandPromptReady.get(), 15, ChronoUnit.SECONDS, "Command prompt was not ready.");
        log.debug("CommandPromptReady.");
    }

    private static Client initializeDefault() {
        Client client = new Client();

        Consumer<Session> onOpen = (session) -> {
            log.info("Client connection opened.");
        };

        Consumer<CloseReason> onClose = (closeReason) -> {
            log.info("Client connection closed. " + closeReason);
        };

        client.onOpen(onOpen);
        client.onClose(onClose);

        return client;
    }

    @Override
    public void close() throws IOException {
        try {
            commandExecutingClient.close();
            statusUpdatesClient.close();
        } catch (Exception e) {
            log.error("Cannot close client.", e);
        }
    }
}
