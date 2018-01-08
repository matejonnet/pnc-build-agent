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

package org.jboss.pnc.buildagent.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class TermdServer {

    private static final AtomicInteger port_pool = new AtomicInteger(8090);

    private static Thread serverThread;

    private static final Logger log = LoggerFactory.getLogger(TermdServer.class);

    public static int getNextPort() {
        return port_pool.getAndIncrement();
    }

    /**
     * Try to start the build agent and block until it is up and running.
     *
     * @return
     * @throws InterruptedException
     * @param host
     * @param port
     * @param bindPath
     */
    public static void startServer(String host, int port, String bindPath) throws InterruptedException {
        Semaphore mutex = new Semaphore(1);
        Runnable onStart = () ->  {
            log.info("Server started.");
            mutex.release();
        };
        mutex.acquire();
        serverThread = new Thread(() -> {
            Optional<Path> logFolder = Optional.of(Paths.get("").toAbsolutePath());
            try {
                new BuildAgentServer(host, port, bindPath, logFolder, onStart);
            } catch (BuildAgentException e) {
                throw new RuntimeException("Cannot start terminal server.", e);
            }
        }, "termd-serverThread-thread");
        serverThread.start();

        mutex.acquire();
    }

    public static void stopServer() {
        log.info("Stopping server...");
        serverThread.interrupt();
    }

}
