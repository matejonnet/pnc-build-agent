/**
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
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
package org.jboss.pnc.buildagent.moduleconfig;

import org.jboss.pnc.common.json.AbstractModuleConfig;

import com.fasterxml.jackson.annotation.JsonProperty;


public class BuildAgentModuleConfig extends AbstractModuleConfig{
    private String username;
    private String password;
    private String baseAuthUrl;

    public BuildAgentModuleConfig(@JsonProperty("username") String username, 
            @JsonProperty("password")String password, @JsonProperty("baseAuthUrl")String baseAuthUrl) {
        super();
        this.username = username;
        this.password = password;
        this.baseAuthUrl = baseAuthUrl;
    }
    
    public String getUsername() {
        return username;
    }
    public void setUsername(String username) {
        this.username = username;
    }
    public String getPassword() {
        return password;
    }
    public void setPassword(String password) {
        this.password = password;
    }

    public String getBaseAuthUrl() {
        return baseAuthUrl;
    }

    public void setBaseAuthUrl(String baseAuthUrl) {
        this.baseAuthUrl = baseAuthUrl;
    }
    
    @Override
    public String toString() {
        return "AuthenticationModuleConfig [username=HIDDEN, password=" + password + ", baseAuthUrl=" + baseAuthUrl +"]";
    }
    
}
