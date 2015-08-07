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

package org.jboss.pnc.buildagent;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.jboss.pnc.buildagent.moduleconfig.BuildAgentModuleConfig;
import org.jboss.pnc.common.Configuration;
import org.jboss.pnc.common.json.ConfigurationParseException;
import org.jboss.pnc.common.json.moduleprovider.BAConfigProvider;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class Main {
    private static final String DEFAULT_HOST = "localhost";
    private static final String DEFAULT_PORT = "8080";

    public static void main(String[] args) throws ParseException, BuildAgentException, InterruptedException, 
                                                    ConfigurationParseException, Exception {
        Options options = new Options();
        options.addOption("b", true, "Address to bind. When not specified " + DEFAULT_HOST + " is used as default.");
        options.addOption("p", true, "Port to bind. When not specified " + DEFAULT_PORT + " is used as default.");
        options.addOption("l", true, "Path to folder where process logs are stored. If undefined logs are not written.");
        options.addOption("h", false, "Print this help message.");

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse( options, args);

        if (cmd.hasOption("h")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("...", options);
            return;
        }

        String host = getOption(cmd, "b", DEFAULT_HOST);
        int port = Integer.parseInt(getOption(cmd, "p", DEFAULT_PORT));

        String logPathString = getOption(cmd, "l", null);
        Optional<Path> logPath;
        if (logPathString != null) {
            logPath = Optional.of(Paths.get(logPathString));
        } else {
            logPath = Optional.empty();
        }
        
        // use configuration here
        Configuration configuration = new Configuration();
        BuildAgentModuleConfig config = configuration
                .getModuleConfig(new BAConfigProvider<BuildAgentModuleConfig>(BuildAgentModuleConfig.class));
        String ba_oauth_username = config.getUsername();
        String ba_oauth_password = config.getPassword();
        String baseRestURL = config.getBaseAuthUrl(); 

        if(ba_oauth_password == null || 
            ba_oauth_username == null ||
            ba_oauth_password.equals("") ||
            ba_oauth_username.equals("")) {
                throw new Exception("Wrong configuration");
        }
        
        // TODO OAuth2 releated code here
        System.out.println(">>> BA configuration <<<");
        System.out.println(">>> BA username: " + ba_oauth_username);
        String print_pwd = "not given";
        if(ba_oauth_password.length() > 0 ) {
            print_pwd = "given ****";
        } 
        System.out.println(">>> BA password: " + print_pwd);
        
        new BuildAgent().start(host, port, logPath, null);
    }

    private static String getOption(CommandLine cmd, String opt, String defaultValue) {
        if (cmd.hasOption(opt)) {
            return cmd.getOptionValue(opt);
        } else {
            return defaultValue;
        }
    }
    private static Option longOption(String longOpt, String description) {
        return Option.builder().longOpt(longOpt)
                .desc(description)
                .build();
    }


}
