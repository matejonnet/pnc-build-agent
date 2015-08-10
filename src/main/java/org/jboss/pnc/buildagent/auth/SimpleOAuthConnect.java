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
package org.jboss.pnc.buildagent.auth;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.jboss.logging.Logger;
import org.keycloak.RSATokenVerifier;
import org.keycloak.VerificationException;
import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.installed.KeycloakInstalled;
import org.keycloak.representations.AccessToken;

public class SimpleOAuthConnect {
    
    public final static Logger log = Logger.getLogger(SimpleOAuthConnect.class);

    private KeycloakDeployment keycloakDeployment;


    public SimpleOAuthConnect(InputStream keycloakConfiguration) {
        keycloakDeployment = new KeycloakInstalled(keycloakConfiguration).getDeployment();
    }

    public String getAccessToken(String url, Map<String, String> urlParams) 
            throws ClientProtocolException, IOException{
        return connect(url, urlParams)[0];
    }

    public String getRefreshToken(String url, Map<String, String> urlParams) 
            throws ClientProtocolException, IOException{
        return connect(url, urlParams)[1];
    }
    
    public String[] getTokens(String url, Map<String, String> urlParams) 
            throws ClientProtocolException, IOException{
        return connect(url, urlParams);
    }
    
    public String getAccessToken(String url, String clientId, String username, String password) 
            throws ClientProtocolException, IOException{
        Map<String, String> urlParams = new HashMap<String, String>();
        urlParams.put("grant_type", "password");
        urlParams.put("client_id", clientId);
        urlParams.put("username", username);
        urlParams.put("password", password);
        return connect(url, urlParams)[0];
    }
    
    public String getrefreshToken(String url, String clientId, String username, String password) 
            throws ClientProtocolException, IOException{
        Map<String, String> urlParams = new HashMap<String, String>();
        urlParams.put("grant_type", "password");
        urlParams.put("client_id", clientId);
        urlParams.put("username", username);
        urlParams.put("password", password);
        return connect(url, urlParams)[1];
    }
    
    private String[] connect(String url, Map<String, String> urlParams) 
            throws ClientProtocolException, IOException{
        
        CloseableHttpClient httpclient = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost(url);

        // add header
        httpPost.setHeader("Content-Type", "application/x-www-form-urlencoded");

        List <BasicNameValuePair> urlParameters = new ArrayList <BasicNameValuePair>();
        for(String key : urlParams.keySet()) {
            urlParameters.add(new BasicNameValuePair(key, urlParams.get(key)));
        }
        httpPost.setEntity(new UrlEncodedFormEntity(urlParameters));
        CloseableHttpResponse response = httpclient.execute(httpPost);

        String refreshToken = "";
        String accessToken = "";
        try {
            BufferedReader rd = new BufferedReader(
                    new InputStreamReader(response.getEntity().getContent()));

            String line = "";
            while ((line = rd.readLine()) != null) {
                if(line.contains("refresh_token")) {
                    String[] respContent = line.split(",");
                    for (int i = 0; i < respContent.length; i++) {
                        String split = respContent[i];
                        if(split.contains("refresh_token")) {
                            refreshToken = split.split(":")[1].substring(1,split.split(":")[1].length() -1);
                        }
                        if(split.contains("access_token")) {
                            accessToken = split.split(":")[1].substring(1,split.split(":")[1].length() -1);
                        }
                    }
                }
            }

        } finally {
            response.close();
        }
        return new String[] {accessToken,refreshToken};
        
        
    }
    
    public AccessToken authenticateToken(String tokenString) {
        try {
            AccessToken token = RSATokenVerifier.verifyToken(
                    tokenString, 
                    keycloakDeployment.getRealmKey(), 
                    keycloakDeployment.getRealmInfoUrl()
            );
            return token;
        } catch (VerificationException ve) {
            log.error("Failed to verify token", ve);
            return null;
        } catch (Exception e) {
            log.error("Exception during token verification", e);
            return null;
        } 
    }

    public KeycloakDeployment getKeycloakDeployment() {
        return keycloakDeployment;
    }
}
