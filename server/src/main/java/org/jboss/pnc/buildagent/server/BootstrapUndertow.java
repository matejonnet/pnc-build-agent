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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import org.jboss.pnc.buildagent.api.Constants;
import org.jboss.pnc.buildagent.api.ResponseMode;
import org.jboss.pnc.buildagent.server.servlet.Download;
import org.jboss.pnc.buildagent.server.servlet.Terminal;
import org.jboss.pnc.buildagent.server.servlet.Upload;
import org.jboss.pnc.buildagent.server.servlet.Welcome;
import org.jboss.pnc.buildagent.server.termserver.ReadOnlyChannel;
import org.jboss.pnc.buildagent.server.termserver.Term;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.jar.Manifest;

import static io.undertow.servlet.Servlets.defaultContainer;
import static io.undertow.servlet.Servlets.deployment;
import static io.undertow.servlet.Servlets.servlet;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class BootstrapUndertow {

    private final Logger log = LoggerFactory.getLogger(BootstrapUndertow.class);

    private final String host;
    private final int port;
    private final String bindPath;

    private Undertow server;

    private final ConcurrentHashMap<String, Term> terms = new ConcurrentHashMap<>();
    private final ScheduledExecutorService executor;
    private final Optional<ReadOnlyChannel> appendReadOnlyChannel;

    public BootstrapUndertow(
            String host,
            int port,
            ScheduledExecutorService executor,
            String bindPath,
            Optional<ReadOnlyChannel> ioLoggerChannel,
            Consumer<Boolean> completionHandler) throws BuildAgentException {
        this.host = host;
        this.port = port;
        this.bindPath = bindPath;

        this.executor = executor;
        this.appendReadOnlyChannel = ioLoggerChannel;

        bootstrap(completionHandler);
    }

    private void bootstrap(final Consumer<Boolean> completionHandler) throws BuildAgentException {
        String servletPath = bindPath + Constants.SERVLET_PATH;
        String socketPath = bindPath + Constants.SOCKET_PATH;
        String httpPath = bindPath + Constants.HTTP_PATH;

        DeploymentInfo servletBuilder = deployment()
                .setClassLoader(BootstrapUndertow.class.getClassLoader())
                .setContextPath(servletPath)
                .setDeploymentName("ROOT.war")
                .addServlets(
                        servlet("WelcomeServlet", Welcome.class)
                                .addMapping("/"),
                        servlet("TerminalServlet", Terminal.class)
                                .addMapping("/terminal/*"),
                        servlet("UploaderServlet", Upload.class)
                                .addMapping("/upload/*"),
                        servlet("DownloaderServlet", Download.class)
                                .addMapping("/download/*"));

        DeploymentManager manager = defaultContainer().addDeployment(servletBuilder);
        manager.deploy();

        HttpHandler servletHandler = null;
        try {
            servletHandler = manager.start();
        } catch (ServletException e) {
            throw new BuildAgentException("Cannot deploy servlets.", e);
        }

        PathHandler pathHandler = Handlers.path()
                .addPrefixPath(servletPath, servletHandler)
                .addPrefixPath(socketPath, exchange -> handleWebSocketRequests(exchange, socketPath))
                .addPrefixPath(httpPath, exchange -> handleHttpRequests(exchange, httpPath));

        server = Undertow.builder()
                .addHttpListener(port, host)
                .setHandler(pathHandler)
                .build();

        server.start();

        completionHandler.accept(true);
    }

    public void stop() {
        if (server != null) {
            server.stop();
        }
    }

    private void handleWebSocketRequests(HttpServerExchange exchange, String socketPath) throws Exception {
        String processUpdatePath = socketPath + Constants.PROCESS_UPDATES_PATH;
        String requestPath = exchange.getRequestPath();

        if (requestPath.startsWith(processUpdatePath)) {
            handleStatusUpdateRequests(exchange, processUpdatePath);
        } else {
            handleTerminalRequests(exchange, socketPath);
        }
    }

    private void handleTerminalRequests(HttpServerExchange exchange, String socketPath) throws Exception {
        String stringTermPath = socketPath + Constants.TERM_PATH_TEXT;
        String silentTermPath = socketPath + Constants.TERM_PATH_SILENT;
        String termPath = socketPath + Constants.TERM_PATH;
        String requestPath = exchange.getRequestPath();

        ResponseMode responseMode;
        String invokerContext;

        if (requestPath.startsWith(stringTermPath)) {
            log.info("Connecting to string term ...");
            responseMode = ResponseMode.TEXT;
            invokerContext = requestPath.replace(stringTermPath, "");
        } else if (requestPath.startsWith(silentTermPath)) {
            log.info("Connecting to silent term ...");
            responseMode = ResponseMode.SILENT;
            invokerContext = requestPath.replace(silentTermPath, "");
        } else {
            log.info("Connecting to binary term ...");
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

    private void handleStatusUpdateRequests(HttpServerExchange exchange, String processUpdatePath)
            throws Exception {
        log.info("Connecting status listener ...");
        String requestPath = exchange.getRequestPath();
        String invokerContext = requestPath.replace(processUpdatePath, "");
        Term term = getTerm(invokerContext, appendReadOnlyChannel);
        term.webSocketStatusUpdateHandler().handleRequest(exchange);
    }

    private Term getTerm(String invokerContext, Optional<ReadOnlyChannel> appendReadOnlyChannel) {
        return terms.computeIfAbsent(invokerContext, ctx -> createNewTerm(invokerContext, appendReadOnlyChannel));
    }

    private Term createNewTerm(String invokerContext, Optional<ReadOnlyChannel> appendReadOnlyChannel) {
        log.info("Creating new term for context [{}].", invokerContext);
        Runnable onDestroy = () -> terms.remove(invokerContext);
        Term term = new Term(invokerContext, onDestroy, executor, appendReadOnlyChannel);
        return term;
    }

    public Map<String, Term> getTerms() {
        Map termsClone = new HashMap<>();
        termsClone.putAll(terms);
        return termsClone;
    }

    private void handleHttpRequests(HttpServerExchange exchange, String httpPath) throws Exception {
        String requestPath = exchange.getRequestPath();

        if (pathMatches(requestPath, httpPath)) {
            log.debug("Welcome handler requested.");
            String message = "Welcome to PNC Build Agent (" + getManifestInformation() + ")";
            message += "\nVisit /servlet/terminal/ for demo console.";
            exchange.getResponseSender().send(message);
            return;
        }
        if (pathMatches(requestPath, httpPath + "processes")) {
            log.debug("Processes handler requested.");
            getProcessActiveTerms().handleRequest(exchange);
            return;
        }
        ResponseCodeHandler.HANDLE_404.handleRequest(exchange);
    }

    private boolean pathMatches(String requestPath, String path) {
        return requestPath.equals(path) || (requestPath + "/").equals(path);
    }

    private HttpHandler getProcessActiveTerms() {
        return exchange -> {
            ObjectMapper mapper = new ObjectMapper();
            String jsonString = mapper.writeValueAsString(getTerms().keySet());
            exchange.getResponseSender().send(jsonString);
        };
    }

    private String getManifestInformation() {
        String result = "";
        try {
            final Enumeration<URL> resources = Welcome.class.getClassLoader().getResources("META-INF/MANIFEST.MF");

            while (resources.hasMoreElements()) {
                final URL jarUrl = resources.nextElement();

                log.trace("Processing jar resource " + jarUrl);
                if (jarUrl.getFile().contains("build-agent")) {
                    final Manifest manifest = new Manifest(jarUrl.openStream());
                    result = manifest.getMainAttributes().getValue("Implementation-Version");
                    result += " ( SHA: " + manifest.getMainAttributes().getValue("Scm-Revision") + " ) ";
                    break;
                }
            }
        } catch (final IOException e) {
            log.trace( "Error retrieving information from manifest", e);
        }

        return result;
    }
}
