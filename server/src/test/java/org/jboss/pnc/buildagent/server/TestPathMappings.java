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

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Optional;


/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class TestPathMappings {

    private static final String HOST = "localhost";

    private static Logger log = LoggerFactory.getLogger(TestPathMappings.class);

    @Test
    public void serverShouldListenOnRoot() throws BuildAgentException, InterruptedException, IOException {
        BuildAgentServer buildAgent = new BuildAgentServer(HOST, 0, "", Optional.empty(), null);

        HttpURLConnection connection200 = connectToUrl(buildAgent.getPort(), "");
        Assert.assertEquals("Unexpected http response code.", 200, connection200.getResponseCode());

        HttpURLConnection connection404 = connectToUrl(buildAgent.getPort(), "wrong");
        Assert.assertEquals("Unexpected http response code.", 404, connection404.getResponseCode());

        buildAgent.stop();
    }

    @Test
    public void serverShouldListenOnPath() throws InterruptedException, IOException, BuildAgentException {
        String contextPath = "ctx-path";
        BuildAgentServer buildAgent = new BuildAgentServer(HOST, 0, "/" + contextPath, Optional.empty(), null);

        HttpURLConnection connection200 = connectToUrl(buildAgent.getPort(), contextPath);
        Assert.assertEquals("Unexpected http response code.", 200, connection200.getResponseCode());

        HttpURLConnection connection404 = connectToUrl(buildAgent.getPort(), "");
        Assert.assertEquals("Unexpected http response code.", 404, connection404.getResponseCode());

        HttpURLConnection connection404WithPath = connectToUrl(buildAgent.getPort(), contextPath + "/wrong");
        Assert.assertEquals("Unexpected http response code.", 404, connection404WithPath.getResponseCode());

        buildAgent.stop();
    }


    private HttpURLConnection connectToUrl(int port, String path) throws IOException {
        URL url = new URL("http://" + HOST + ":" + port + "/" + path);
        log.debug("Connecting to {}", url);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(500);
        connection.setRequestMethod("GET");
        connection.setDoOutput(true);
        connection.setDoInput(true);
        connection.connect();
        return connection;
    }

}