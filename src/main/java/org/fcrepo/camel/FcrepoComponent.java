/*
 * Licensed to DuraSpace under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.
 *
 * DuraSpace licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fcrepo.camel;

import static org.slf4j.LoggerFactory.getLogger;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.support.DefaultComponent;
import org.slf4j.Logger;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Represents the component that manages {@link FcrepoEndpoint}.
 * @author Aaron Coburn
 * @since October 20, 2014
 */
public class FcrepoComponent extends DefaultComponent {

    private FcrepoConfiguration configuration;

    private PlatformTransactionManager transactionManager;

    private static final Logger LOGGER  = getLogger(FcrepoComponent.class);

    /**
     * Create a FcrepoComponent independent of a CamelContext.
     */
    public FcrepoComponent() {
    }

    /**
     * Given a CamelContext, create a FcrepoComponent instance.
     * @param context the camel context for the component.
     */
    public FcrepoComponent(final CamelContext context) {
        super(context);
    }

    /**
     * Given a FcrepoConfiguration, create a FcrepoComponent instance.
     * @param config the component-wide configuration.
     */
    public FcrepoComponent(final FcrepoConfiguration config) {
        this.configuration = config;
    }

    /**
     * Get the component's configuration.
     * @return the configuration for the component.
     */
    public FcrepoConfiguration getConfiguration() {
        if (configuration == null) {
            configuration = new FcrepoConfiguration();
        }
        return configuration;
    }

    /**
     * Set the component's configuration.
     * @param config the configuration settings for the component.
     */
    public void setConfiguration(final FcrepoConfiguration config) {
        this.configuration = config;
    }

    /**
     * Set the transaction manager for the component
     *
     * @param transactionManager the transaction manager for this component
     */
    public void setTransactionManager(final PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    /**
     * Get the transaction manager for the component
     *
     * @return the transaction manager for this component
     */
    public PlatformTransactionManager getTransactionManager() {
        return transactionManager;
    }

    /**
     * set the authUsername value component-wide.
     * @param username the authentication username.
     */
    public void setAuthUsername(final String username) {
        getConfiguration().setAuthUsername(username);
    }

    /**
     * set the authPassword value component-wide.
     * @param password the authentication password.
     */
    public void setAuthPassword(final String password) {
        getConfiguration().setAuthPassword(password);
    }

    /**
     * set the authHost value component-wide.
     * @param host the authentication host realm.
     */
    public void setAuthHost(final String host) {
        getConfiguration().setAuthHost(host);
    }

    /**
     * set the baseUrl component-wide
     * @param baseUrl the repository root
     */
    public void setBaseUrl(final String baseUrl) {
        getConfiguration().setBaseUrl(baseUrl);
    }

    /**
     *  Create an Endpoint from a fcrepo uri along with an optional path value and attributes.
     *  @param uri the fcrepo uri identifying the repository hostname and port
     *  @param remaining the string identifying the repository path
     *  @param parameters any optional attributes added to the endpoint
     *  @return the camel endpoint
     */
    @Override
    protected Endpoint createEndpoint(final String uri, final String remaining, final Map<String, Object> parameters) {
        final FcrepoConfiguration newConfig;
        if (configuration == null) {
            newConfig = new FcrepoConfiguration();
        } else {
            newConfig = configuration.clone();
        }

        final Endpoint endpoint = new FcrepoEndpoint(uri, remaining, this, newConfig);
        endpoint.configureProperties(parameters);
        LOGGER.debug("Created Fcrepo Endpoint [{}]", endpoint);
        return endpoint;
    }
}
