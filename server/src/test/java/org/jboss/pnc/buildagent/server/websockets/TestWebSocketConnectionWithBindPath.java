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

package org.jboss.pnc.buildagent.server.websockets;

import org.jboss.pnc.buildagent.server.TermdServer;
import org.jboss.pnc.buildagent.api.ResponseMode;
import org.jboss.pnc.buildagent.api.Status;
import org.jboss.pnc.buildagent.api.TaskStatusUpdateEvent;
import org.jboss.pnc.buildagent.client.BuildAgentClient;
import org.jboss.pnc.buildagent.common.ObjectWrapper;
import org.jboss.pnc.buildagent.common.Wait;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Paths;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.function.Consumer;


/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class TestWebSocketConnectionWithBindPath {

    private static final Logger log = LoggerFactory.getLogger(TestWebSocketConnectionWithBindPath.class);

    private static final String HOST = "localhost";
    private static final int PORT = TermdServer.getNextPort();
    private static final String TEST_COMMAND = "java -cp ./server/target/test-classes/:./target/test-classes/ org.jboss.pnc.buildagent.server.MockProcess 100 10";

    private static File logFolder = Paths.get("").toAbsolutePath().toFile();
    private static File logFile = new File(logFolder, "console.log");
    private static String bindPath = "/pnc-ba-test";;

    String terminalBaseUrl = "http://" + HOST + ":" + PORT + bindPath;

    @BeforeClass
    public static void setUP() throws Exception {
        TermdServer.startServer(HOST, PORT, bindPath);
    }

    @AfterClass
    public static void tearDown() {
        TermdServer.stopServer();
        log.debug("Deleting log file {}", logFile);
        logFile.delete();
    }

    @Test
    public void clientShouldBeAbleToReConnect() throws Exception {
        String context = this.getClass().getName() + ".clientShouldBeAbleToConnectToRunningProcessWithBindPath";

        ObjectWrapper<Boolean> completed = new ObjectWrapper<>(false);
        Consumer<TaskStatusUpdateEvent> onStatusUpdate = (statusUpdateEvent) -> {
            if (statusUpdateEvent.getNewStatus().equals(Status.COMPLETED)) {
                completed.set(true);
            }
        };
        BuildAgentClient buildAgentClient = new BuildAgentClient(terminalBaseUrl, Optional.empty(), onStatusUpdate, context, ResponseMode.BINARY, false);
        buildAgentClient.executeCommand(TEST_COMMAND);

        Thread.sleep(1000); //make sure async command execution started
        buildAgentClient.close();

        StringBuilder response = new StringBuilder();
        Consumer<String> onResponse = (message) -> {
            response.append(message);
        };
        BuildAgentClient buildAgentClientReconnected = new BuildAgentClient(
                terminalBaseUrl,
                Optional.of(onResponse),
                onStatusUpdate,
                context,
                ResponseMode.BINARY,
                false);

        Wait.forCondition(() -> completed.get(), 5, ChronoUnit.SECONDS, "Operation did not complete within given timeout.");
        Wait.forCondition(() -> response.toString().contains("I'm done."), 3, ChronoUnit.SECONDS, "Missing or invalid response: " + response.toString());

        buildAgentClientReconnected.close();
    }

    @Test
    public void clientShouldBeAbleToReConnectWithDifferentResponseMode() throws Exception {
        String context = this.getClass().getName() + ".clientShouldBeAbleToConnectToRunningProcessWithBindPath";

        ObjectWrapper<Boolean> completed = new ObjectWrapper<>(false);
        Consumer<TaskStatusUpdateEvent> onStatusUpdate = (statusUpdateEvent) -> {
            if (statusUpdateEvent.getNewStatus().equals(Status.COMPLETED)) {
                completed.set(true);
            }
        };
        BuildAgentClient buildAgentClient = new BuildAgentClient(terminalBaseUrl, Optional.empty(), onStatusUpdate, context, ResponseMode.BINARY, false);
        buildAgentClient.executeCommand(TEST_COMMAND);

        Thread.sleep(1000); //make sure async command execution started
        buildAgentClient.close();

        StringBuilder response = new StringBuilder();
        Consumer<String> onResponse = (message) -> {
            response.append(message);
        };
        BuildAgentClient buildAgentClientReconnected = new BuildAgentClient(
                terminalBaseUrl,
                Optional.of(onResponse),
                onStatusUpdate,
                context,
                ResponseMode.TEXT,
                false);

        Wait.forCondition(() -> completed.get(), 5, ChronoUnit.SECONDS, "Operation did not complete within given timeout.");
        Wait.forCondition(() -> response.toString().contains("I'm done."), 3, ChronoUnit.SECONDS, "Missing or invalid response: " + response.toString());

        buildAgentClientReconnected.close();
    }

}
