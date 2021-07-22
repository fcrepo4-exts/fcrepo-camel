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

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.net.URI;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.support.DefaultEndpoint;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Represents a Fcrepo endpoint.
 * @author Aaron Coburn
 * @since October 20, 2014
 */
@ManagedResource(description = "Managed FcrepoEndpoint")
@UriEndpoint(scheme = "fcrepo", title = "Fedora Commons Repository", syntax = "fcrepo:host:port/path")
public class FcrepoEndpoint extends DefaultEndpoint {

    private FcrepoConfiguration configuration;

    private PlatformTransactionManager transactionManager;

    public static final int DEFAULT_HTTPS_PORT = 443;

    /**
     * Create a FcrepoEndpoint with a uri, path and component
     * @param uri the endpoint uri (without path values)
     * @param remaining any path values on the endpoint uri
     * @param component an existing component value
     * @param configuration configuration settings for this endpoint
     */
    public FcrepoEndpoint(final String uri, final String remaining, final FcrepoComponent component,
            final FcrepoConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
        this.transactionManager = component.getTransactionManager();
        if (isBlank(configuration.getBaseUrl())) {
            setBaseUrl(remaining);
        }
    }

    /**
     * Create a producer endpoint.
     *
     * @return A new camel producer endpoint
     */
    @Override
    public Producer createProducer() {
        return new FcrepoProducer(this);
    }

    /**
     * This component does not implement a consumer endpoint.
     */
    @Override
    public Consumer createConsumer(final Processor processor) {
        throw new RuntimeCamelException("Cannot consume from a FcrepoEndpoint: " + getEndpointUri());
    }

    /**
     * Define the component as a singleton
     *
     * @return whether the endpoint is implemented as a singleton.
     */
    @Override
    public boolean isSingleton() {
        return true;
    }

    /**
     * Create a template for use in transactions
     *
     * @return a transaction template
     */
    public TransactionTemplate createTransactionTemplate() {
        final TransactionTemplate transactionTemplate;

        if (getTransactionManager() != null) {
            transactionTemplate = new TransactionTemplate(getTransactionManager());
        } else {
            final FcrepoTransactionManager txMgr = new FcrepoTransactionManager();
            txMgr.setBaseUrl(getBaseUrlWithScheme());
            txMgr.setAuthUsername(getAuthUsername());
            txMgr.setAuthPassword(getAuthPassword());
            txMgr.setAuthHost(getAuthHost());
            transactionTemplate = new TransactionTemplate(txMgr);
        }
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        transactionTemplate.afterPropertiesSet();
        return transactionTemplate;
    }

    /**
     * Get the repository baseUrl with a full scheme.
     * The base URL may be any of the following:
     * localhost:8080/rest
     * fedora.institution.org:8983/rest
     * http://localhost:8080/fcrepo/rest
     * https://fedora.institution.org/rest
     * fedora.insitution.org:443/rest
     *
     * This method ensures that the url (fragment) is properly prefixed
     * with either the http or https scheme, suitable for sending to the
     * httpclient.
     *
     * @return String
     */
    public String getBaseUrlWithScheme() {
        final String baseUrl = getBaseUrl();

        if (!baseUrl.startsWith("http:") && !baseUrl.startsWith("https:")) {
            if (URI.create("http://" + baseUrl).getPort() == DEFAULT_HTTPS_PORT) {
                return "https://" + baseUrl;
            } else {
                return "http://" + baseUrl;
            }
        }
        return baseUrl;
    }

    /**
     * configuration getter
     *
     * @return the component configuration
     */
    public FcrepoConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * configuration setter
     *
     * @param config The FcrepoConfiguration
     */
    public void setConfiguration(final FcrepoConfiguration config) {
        this.configuration = config;
    }

    /**
     * baseUrl setter
     *
     * @param url the fcrepo base url
     */
    public void setBaseUrl(final String url) {
        getConfiguration().setBaseUrl(url);
    }

    /**
     * baseUrl getter
     *
     * @return the fcrepo base url
     */
    public String getBaseUrl() {
        return getConfiguration().getBaseUrl();
    }

    /**
     * transactionManager setter
     *
     * @param transactionManager the transaction manager for this endpoint
     */
    @ManagedAttribute(description = "Transaction Manager")
    public void setTransactionManager(final PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    /**
     * transactionManager getter
     *
     * @return the transaction manager for this endpoint
     */
    @ManagedAttribute(description = "Transaction Manager")
    public PlatformTransactionManager getTransactionManager() {
        return transactionManager;
    }

    /**
     * accept setter
     *
     * @param type the mime-type used with Accept headers
     */
    @ManagedAttribute(description = "Accept: Header")
    public void setAccept(final String type) {
        getConfiguration().setAccept(type.replaceAll(" ", "+"));
    }

    /**
     * accept getter
     *
     * @return the mime-type used with Accept headers
     */
    @ManagedAttribute(description = "Accept: Header")
    public String getAccept() {
        return getConfiguration().getAccept();
    }

    /**
     * contentType setter
     *
     * @param type the mime-type used with Content-Type headers
     */
    @ManagedAttribute(description = "Content-Type: Header")
    public void setContentType(final String type) {
        getConfiguration().setContentType(type);
    }

    /**
     * contentType getter
     *
     * @return the mime-type used with Content-Type headers
     */
    @ManagedAttribute(description = "Content-Type: Header")
    public String getContentType() {
        return getConfiguration().getContentType();
    }

    /**
     * authUsername setter
     *
     * @param username used for repository authentication
     */
    @ManagedAttribute(description = "Username for authentication")
    public void setAuthUsername(final String username) {
        getConfiguration().setAuthUsername(username);
    }

    /**
     * authUsername getter
     *
     * @return the username used for repository authentication
     */
    @ManagedAttribute(description = "Username for authentication")
    public String getAuthUsername() {
        return getConfiguration().getAuthUsername();
    }

    /**
     * authPassword setter
     *
     * @param password used for repository authentication
     */
    @ManagedAttribute(description = "Password for authentication")
    public void setAuthPassword(final String password) {
        getConfiguration().setAuthPassword(password);
    }

    /**
     * authPassword getter
     *
     * @return the password used for repository authentication
     */
    @ManagedAttribute(description = "Password for authentication")
    public String getAuthPassword() {
        return getConfiguration().getAuthPassword();
    }

    /**
     * authHost setter
     *
     * @param host realm used for repository authentication
     */
    @ManagedAttribute(description = "Hostname for authentication")
    public void setAuthHost(final String host) {
        getConfiguration().setAuthHost(host);
    }

    /**
     * authHost getter
     *
     * @return the host realm used for repository authentication
     */
    @ManagedAttribute(description = "Hostname for authentication")
    public String getAuthHost() {
        return getConfiguration().getAuthHost();
    }

    /**
     * metadata setter
     *
     * @param metadata whether to retrieve rdf metadata for non-rdf nodes
     */
    @ManagedAttribute(description = "Whether to retrieve the /fcr:metadata endpoint for Binary nodes")
    public void setMetadata(final Boolean metadata) {
        getConfiguration().setMetadata(metadata);
    }

    /**
     * metadata getter
     *
     * @return whether to retrieve rdf metadata for non-rdf nodes
     */
    @ManagedAttribute(description = "Whether to retrieve the /fcr:metadata endpoint for Binary nodes")
    public Boolean getMetadata() {
        return getConfiguration().getMetadata();
    }

    /**
     * throwExceptionOnFailure setter
     *
     * @param throwOnFailure whether HTTP error codes throw exceptions
     */
    @ManagedAttribute(description = "Whether HTTP response errors should throw an exception")
    public void setThrowExceptionOnFailure(final Boolean throwOnFailure) {
        getConfiguration().setThrowExceptionOnFailure(throwOnFailure);
    }

    /**
     * throwExceptionOnFailure getter
     *
     * @return whether HTTP error codes throw exceptions
     */
    @ManagedAttribute(description = "Whether HTTP response errors should throw an exception")
    public Boolean getThrowExceptionOnFailure() {
        return getConfiguration().getThrowExceptionOnFailure();
    }

    /**
     * fixity setter
     *
     * @param fixity whether to access the /fcr:fixity endpoint for a resource
     */
    @ManagedAttribute(description = "Whether to access the /fcr:fixity endpoint for a resource")
    public void setFixity(final Boolean fixity) {
        getConfiguration().setFixity(fixity);
    }

    /**
     * fixity getter
     *
     * @return whether to access the /fcr:fixity endpoint for a resource
     */
    @ManagedAttribute(description = "Whether to access the /fcr:fixity endpoint for a resource")
    public Boolean getFixity() {
        return getConfiguration().getFixity();
    }

    /**
     * preferInclude setter
     *
     * @param include the URI(s) that populate the include section in a Prefer header
     */
    @ManagedAttribute(description = "Whether to include a Prefer: return=representation; include=\"URI\" header")
    public void setPreferInclude(final String include) {
        getConfiguration().setPreferInclude(include);
    }

    /**
     * preferInclude getter
     *
     * @return the URI(s) that populate the include section in a Prefer header
     */
    @ManagedAttribute(description = "Whether to include a Prefer: return=representation; include=\"URI\" header")
    public String getPreferInclude() {
        return getConfiguration().getPreferInclude();
    }

    /**
     * preferOmit setter
     *
     * @param omit the URI(s) that populate the omit section in a Prefer header
     */
    @ManagedAttribute(description = "Whether to include a Prefer: return=representation; omit=\"URI\" header")
    public void setPreferOmit(final String omit) {
        getConfiguration().setPreferOmit(omit);
    }

    /**
     * preferOmit getter
     *
     * @return the URI(s) that populate the omit section in a Prefer header
     */
    @ManagedAttribute(description = "Whether to include a Prefer: return=representation; omit=\"URI\" header")
    public String getPreferOmit() {
        return getConfiguration().getPreferOmit();
    }
}
