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

import io.undertow.Undertow;
import io.undertow.server.HttpServerExchange;
import org.jboss.pnc.buildagent.api.ResponseMode;
import org.jboss.pnc.buildagent.server.BuildAgentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;


/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class UndertowBootstrap {

    Logger log = LoggerFactory.getLogger(UndertowBootstrap.class);

    final String host;
    final int port;
    private Undertow server;
    final ConcurrentHashMap<String, Term> terms = new ConcurrentHashMap<>();
    private ScheduledExecutorService executor;
    private Optional<ReadOnlyChannel> appendReadOnlyChannel;

    public UndertowBootstrap(String host, int port, ScheduledExecutorService executor, Optional<ReadOnlyChannel> appendReadOnlyChannel) {
        this.host = host;
        this.port = port;
        this.executor = executor;
        this.appendReadOnlyChannel = appendReadOnlyChannel;
    }

    public void bootstrap(final Consumer<Boolean> completionHandler) throws BuildAgentException {
        server = Undertow.builder()
                .addHttpListener(port, host)
                .setHandler((exchange) -> handleWebSocketRequests(exchange, Configurations.TERM_PATH, Configurations.TERM_PATH_TEXT, Configurations.PROCESS_UPDATES_PATH))
                .build();

        server.start();

        completionHandler.accept(true);
    }

    protected void handleWebSocketRequests(HttpServerExchange exchange, String termPath, String stringTermPath, String processUpdatePath) throws Exception {
        String requestPath = exchange.getRequestPath();

        if (requestPath.startsWith(processUpdatePath)) {
            log.debug("Connecting status listener ...");
            String invokerContext = requestPath.replace(processUpdatePath, "");
            Term term = getTerm(invokerContext, appendReadOnlyChannel);
            term.webSocketStatusUpdateHandler().handleRequest(exchange);
        } else {
            ResponseMode responseMode;
            String invokerContext;
            if (requestPath.startsWith(stringTermPath)) {
                log.debug("Connecting to string term ...");
                responseMode = ResponseMode.TEXT;
                invokerContext = requestPath.replace(stringTermPath, "");
            } else {
                log.debug("Connecting to binary term ...");
                responseMode = ResponseMode.BINARY;
                invokerContext = requestPath.replace(termPath, "");
            }
            //strip /ro from invokerContext
            if (invokerContext.toLowerCase().endsWith("/ro")) {
                invokerContext = invokerContext.substring(0, invokerContext.length() - 3);
            }
            log.debug("Computed invokerContext [{}] from requestPath [{}] and termPath [{}]", invokerContext, requestPath, termPath);

            boolean isReadOnly = requestPath.toLowerCase().endsWith("ro");
            Term term = getTerm(invokerContext, appendReadOnlyChannel);
            term.getWebSocketHandler(responseMode, isReadOnly).handleRequest(exchange);
        }
    }

    private Term getTerm(String invokerContext, Optional<ReadOnlyChannel> appendReadOnlyChannel) {
        return terms.computeIfAbsent(invokerContext, ctx -> createNewTerm(invokerContext, appendReadOnlyChannel));
    }

    protected Term createNewTerm(String invokerContext, Optional<ReadOnlyChannel> appendReadOnlyChannel) {
        log.info("Creating new term for context [{}].", invokerContext);
        Runnable onDestroy = () -> terms.remove(invokerContext);
        Term term = new Term(invokerContext, onDestroy, executor, appendReadOnlyChannel);

        return term;
    }

    public void stop() {
        if (server != null) {
            server.stop();
        }
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public Map<String, Term> getTerms() {
        Map termsClone = new HashMap<>();
        termsClone.putAll(terms);
        return termsClone;
    }
}
