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

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
  */
public class BuildAgentServer {

    private final Logger log = LoggerFactory.getLogger(BuildAgentServer.class);
    private final BootstrapUndertow undertowBootstrap;
    private final IoLoggerChannel ioLoggerChannel;
    private final ScheduledExecutorService executor = new ScheduledThreadPoolExecutor(1);

    private final int port;
    private final String host;

    public BuildAgentServer(String host, final int port, String bindPath, Optional<Path> logPath, Runnable onStart) throws BuildAgentException {
        this.host = host;
        if (port == 0) {
            this.port = findFirstFreePort();
        } else {
            this.port = port;
        }

        if (logPath.isPresent()) {
            ioLoggerChannel = new IoLoggerChannel(logPath.get());
        } else {
            ioLoggerChannel = null;
        }

        undertowBootstrap = new BootstrapUndertow(
                host,
                this.port,
                executor,
                bindPath,
                Optional.ofNullable(ioLoggerChannel),
                completionHandler -> {
                    if (completionHandler) {
                        log.info("Server started on " + this.host + ":" + this.port);
                        if (onStart != null) {
                            onStart.run();
                        }
                    } else {
                        log.info("Could not start server");
                    }
                }
        );
    }

    private int findFirstFreePort() {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not obtain default port, try specifying it explicitly");
        }
    }


    public int getPort() {
        return port;
    }

    public String getHost() {
        return host;
    }

    public void stop() {
        undertowBootstrap.stop();
        if (ioLoggerChannel != null) {
            ioLoggerChannel.close();
        }
    }
}
