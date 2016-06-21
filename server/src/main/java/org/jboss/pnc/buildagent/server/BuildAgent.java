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

import org.jboss.pnc.buildagent.server.termserver.ReadOnlyChannel;
import org.jboss.pnc.buildagent.server.termserver.UndertowBootstrap;
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
public class BuildAgent {

    Logger log = LoggerFactory.getLogger(BuildAgent.class);
    private UndertowBootstrap undertowBootstrap;
    IoLoggerChannel ioLoggerChannel;
    private final ScheduledExecutorService executor = new ScheduledThreadPoolExecutor(1);

    public void start(String host, final int port, String bindPath, Optional<Path> logPath, Runnable onStart) throws BuildAgentException {
        final int bindPort;
        if (port == 0) {
            bindPort = findFirstFreePort();
        } else {
            bindPort = port;
        }

        Optional<ReadOnlyChannel> ioLoggerChannelWrapper;
        if (logPath.isPresent()) {
            ioLoggerChannel = new IoLoggerChannel(logPath.get());
            ioLoggerChannelWrapper = Optional.of(ioLoggerChannel);
        } else {
            ioLoggerChannelWrapper = Optional.empty();
        }

        undertowBootstrap = new BootstrapUndertowBuildAgentHandlers(host, bindPort, executor, bindPath, ioLoggerChannelWrapper);

        undertowBootstrap.bootstrap(completionHandler -> {
            if (completionHandler) {
                log.info("Server started on " + host + ":" + port);
                if (onStart != null) {
                    onStart.run();
                }
            } else {
                log.info("Could not start server");
            }
        });

    }

    private int findFirstFreePort() {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not obtain default port, try specifying it explicitly");
        }
    }


    public int getPort() {
        return undertowBootstrap.getPort();
    }

    public String getHost() {
        return undertowBootstrap.getHost();
    }

    public void stop() {
        undertowBootstrap.stop();
        if (ioLoggerChannel != null) {
            ioLoggerChannel.close();
        }
    }
}
