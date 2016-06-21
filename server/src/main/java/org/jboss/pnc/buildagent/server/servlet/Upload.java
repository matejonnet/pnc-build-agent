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

package org.jboss.pnc.buildagent.server.servlet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class Upload extends HttpServlet {

    Logger log = LoggerFactory.getLogger(Upload.class);

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        log.info("Received new file upload request.");
        int fileSize = request.getContentLength();
        log.debug("File size: {}.", fileSize);
        String fileDestination = request.getPathInfo();
        if (!fileDestination.startsWith("/")) {
            fileDestination = "/" + fileDestination;
        }
        log.debug("File destination: {}.", fileDestination);

        File targetFile = new File(fileDestination);

        File dir = new File(targetFile.getParent());
        if (!dir.exists()) {
            dir.mkdirs();
        }

        int totalBytes = 0;
        try (ServletInputStream inputStream = request.getInputStream()) {
            try (FileOutputStream fileOutputStream = new FileOutputStream(fileDestination)) {
                byte[] bytes = new byte[1024];
                int read;
                while ((read = inputStream.read(bytes)) != -1) {
                    fileOutputStream.write(bytes, 0, read);
                    totalBytes += read;
                }
            }
        }

        log.debug("Upload completed.");

        if (totalBytes != fileSize) {
            log.error("Did not received complete file! Expected {} length received {} length.", fileSize, totalBytes);
            response.sendError(500, "Did not received complete file!");
        } else {
            log.info("Successfully uploaded {}.", fileDestination);
            response.setStatus(200);
        }
    }
}
