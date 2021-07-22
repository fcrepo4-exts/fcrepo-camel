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

import static java.lang.Boolean.FALSE;
import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableMap;
import static java.util.stream.Collectors.toList;
import static org.apache.camel.Exchange.ACCEPT_CONTENT_TYPE;
import static org.apache.camel.Exchange.CONTENT_TYPE;
import static org.apache.camel.Exchange.HTTP_METHOD;
import static org.apache.camel.Exchange.HTTP_RESPONSE_CODE;
import static org.apache.camel.Exchange.DISABLE_HTTP_STREAM_CACHE;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.fcrepo.camel.FcrepoConstants.FIXITY;
import static org.fcrepo.camel.FcrepoHeaders.FCREPO_BASE_URL;
import static org.fcrepo.camel.FcrepoHeaders.FCREPO_IDENTIFIER;
import static org.fcrepo.camel.FcrepoHeaders.FCREPO_PREFER;
import static org.fcrepo.camel.FcrepoHeaders.FCREPO_URI;
import static org.fcrepo.client.HttpMethods.GET;
import static org.fcrepo.client.FcrepoClient.client;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.converter.stream.CachedOutputStream;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.util.IOHelper;
import org.fcrepo.client.FcrepoClient;
import org.fcrepo.client.FcrepoOperationFailedException;
import org.fcrepo.client.FcrepoResponse;
import org.fcrepo.client.GetBuilder;
import org.fcrepo.client.HttpMethods;
import org.slf4j.Logger;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The Fedora producer.
 *
 * @author Aaron Coburn
 * @since October 20, 2014
 */
public class FcrepoProducer extends DefaultProducer {

    public static final String DEFAULT_CONTENT_TYPE = "application/rdf+xml";

    private static final Logger LOGGER = getLogger(FcrepoProducer.class);

    private static final String LDP = "http://www.w3.org/ns/ldp#";

    private static final String REPOSITORY = "http://fedora.info/definitions/v4/repository#";

    private final FcrepoEndpoint endpoint;

    private FcrepoClient fcrepoClient;

    private final TransactionTemplate transactionTemplate;

    public static final Map<String, String> PREFER_PROPERTIES;

    static {
        final Map<String, String> prefer = new HashMap<>();
        prefer.put("PreferContainment", LDP + "PreferContainment");
        prefer.put("PreferMembership", LDP + "PreferMembership");
        prefer.put("PreferMinimalContainer", LDP + "PreferMinimalContainer");
        prefer.put("ServerManaged", REPOSITORY + "ServerManaged");
        prefer.put("EmbedResources", REPOSITORY + "EmbedResources");
        prefer.put("InboundReferences", REPOSITORY + "InboundReferences");

        PREFER_PROPERTIES = unmodifiableMap(prefer);
    }

    /**
     *  Add the appropriate namespace to the prefer header in case the
     *  short form was supplied.
     */
    private static Function<String, String> addPreferNamespace = property -> {
        final String prefer = PREFER_PROPERTIES.get(property);
        if (!isBlank(prefer)) {
            return prefer;
        }
        return property;
    };


    /**
     * Create a FcrepoProducer object
     *
     * @param endpoint the FcrepoEndpoint corresponding to the exchange.
     */
    public FcrepoProducer(final FcrepoEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
        this.transactionTemplate = endpoint.createTransactionTemplate();
        final FcrepoClient.FcrepoClientBuilder builder = client()
                .credentials(endpoint.getAuthUsername(), endpoint.getAuthPassword())
                .authScope(endpoint.getAuthHost());
        if (endpoint.getThrowExceptionOnFailure()) {
            this.fcrepoClient = builder.throwExceptionOnFailure().build();
        } else {
            this.fcrepoClient = builder.build();
        }
    }

    /**
     * Define how message exchanges are processed.
     *
     * @param exchange the InOut message exchange
     * @throws FcrepoOperationFailedException when the underlying HTTP request results in an error
     */
    @Override
    public void process(final Exchange exchange) throws FcrepoOperationFailedException {
        if (exchange.isTransacted()) {
            transactionTemplate.execute(new TransactionCallbackWithoutResult() {
                @Override
                protected void doInTransactionWithoutResult(final TransactionStatus status) {
                    final DefaultTransactionStatus st = (DefaultTransactionStatus)status;
                    final FcrepoTransactionObject tx = (FcrepoTransactionObject)st.getTransaction();
                    try {
                        doRequest(exchange, tx.getSessionId());
                    } catch (final FcrepoOperationFailedException ex) {
                        throw new TransactionSystemException(
                            "Error executing fcrepo request in transaction: ", ex);
                    }
                }
            });
        } else {
            doRequest(exchange, null);
        }
    }

    private void doRequest(final Exchange exchange, final String transaction) throws FcrepoOperationFailedException {
        final Message in = exchange.getIn();
        final HttpMethods method = getMethod(exchange);
        final String contentType = getContentType(exchange);
        final String accept = getAccept(exchange);
        final String url = getUrl(exchange, transaction);

        LOGGER.debug("Fcrepo Request [{}] with method [{}]", url, method);

        final FcrepoResponse response;

        switch (method) {
        case PATCH:
            response = fcrepoClient.patch(getMetadataUri(url)).body(in.getBody(InputStream.class)).perform();
            exchange.getIn().setBody(extractResponseBodyAsStream(response.getBody(), exchange));
            break;
        case PUT:
            response = fcrepoClient.put(URI.create(url)).body(in.getBody(InputStream.class), contentType).perform();
            exchange.getIn().setBody(extractResponseBodyAsStream(response.getBody(), exchange));
            break;
        case POST:
            response = fcrepoClient.post(URI.create(url)).body(in.getBody(InputStream.class), contentType).perform();
            exchange.getIn().setBody(extractResponseBodyAsStream(response.getBody(), exchange));
            break;
        case DELETE:
            response = fcrepoClient.delete(URI.create(url)).perform();
            exchange.getIn().setBody(extractResponseBodyAsStream(response.getBody(), exchange));
            break;
        case HEAD:
            response = fcrepoClient.head(URI.create(url)).perform();
            exchange.getIn().setBody(null);
            break;
        case GET:
        default:
            final GetBuilder get = fcrepoClient.get(getUri(endpoint, url)).accept(accept);
            final String preferHeader = in.getHeader(FCREPO_PREFER, "", String.class);
            if (!preferHeader.isEmpty()) {
                final FcrepoPrefer prefer = new FcrepoPrefer(preferHeader);
                if (prefer.isMinimal()) {
                    response = get.preferRepresentation(
                            asList(URI.create("http://www.w3.org/ns/ldp#PreferMinimalContainer")),
                            asList(URI.create("http://fedora.info/definitions/fcrepo#ServerManaged"))).perform();
                } else if (prefer.isRepresentation()) {
                    response = get.preferRepresentation(prefer.getInclude(), prefer.getOmit()).perform();
                } else {
                    response = get.perform();
                }
            } else {
                final List<URI> include = getPreferInclude(endpoint);
                final List<URI> omit = getPreferOmit(endpoint);
                if (include.isEmpty() && omit.isEmpty()) {
                    response = get.perform();
                } else {
                    response = get.preferRepresentation(include, omit).perform();
                }
            }
            exchange.getIn().setBody(extractResponseBodyAsStream(response.getBody(), exchange));
        }

        exchange.getIn().setHeader(CONTENT_TYPE, response.getContentType());
        exchange.getIn().setHeader(HTTP_RESPONSE_CODE, response.getStatusCode());
    }

    private URI getUri(final FcrepoEndpoint endpoint, final String url) throws FcrepoOperationFailedException {
        if (endpoint.getFixity()) {
            return URI.create(url + FIXITY);
        } else if (endpoint.getMetadata()) {
            return getMetadataUri(url);
        }
        return URI.create(url);
    }

    private List<URI> getPreferOmit(final FcrepoEndpoint endpoint) {
        if (!isBlank(endpoint.getPreferOmit())) {
            return stream(endpoint.getPreferOmit().split("\\s+")).map(addPreferNamespace).map(URI::create)
                .collect(toList());
        }
        return emptyList();
    }

    private List<URI> getPreferInclude(final FcrepoEndpoint endpoint) {
        if (!isBlank(endpoint.getPreferInclude())) {
            return stream(endpoint.getPreferInclude().split("\\s+")).map(addPreferNamespace).map(URI::create)
                .collect(toList());
        }
        return emptyList();
    }

    /**
     * Retrieve the resource location from a HEAD request.
     */
    private URI getMetadataUri(final String url)
            throws FcrepoOperationFailedException {
        final FcrepoResponse headResponse = fcrepoClient.head(URI.create(url)).perform();
        if (headResponse.getLocation() != null) {
            return headResponse.getLocation();
        } else {
            return URI.create(url);
        }
    }


    /**
     * Given an exchange, determine which HTTP method to use. Basically, use GET unless the value of the
     * Exchange.HTTP_METHOD header is defined. Unlike the http4: component, the request does not use POST if there is
     * a message body defined. This is so in order to avoid inadvertant changes to the repository.
     *
     * @param exchange the incoming message exchange
     */
    private HttpMethods getMethod(final Exchange exchange) {
        final HttpMethods method = exchange.getIn().getHeader(HTTP_METHOD, HttpMethods.class);
        if (method == null) {
            return GET;
        } else {
            return method;
        }
    }

    /**
     * Given an exchange, extract the contentType value for use with a Content-Type header. The order of preference is
     * so: 1) a contentType value set on the endpoint 2) a contentType value set on the Exchange.CONTENT_TYPE header
     *
     * @param exchange the incoming message exchange
     */
    private String getContentType(final Exchange exchange) {
        final String contentTypeString = ExchangeHelper.getContentType(exchange);
        if (!isBlank(endpoint.getContentType())) {
            return endpoint.getContentType();
        } else if (!isBlank(contentTypeString)) {
            return contentTypeString;
        } else {
            return null;
        }
    }

    /**
     * Given an exchange, extract the value for use with an Accept header. The order of preference is:
     * 1) whether an accept value is set on the endpoint 2) a value set on
     * the Exchange.ACCEPT_CONTENT_TYPE header 3) a value set on an "Accept" header
     * 4) the endpoint DEFAULT_CONTENT_TYPE (i.e. application/rdf+xml)
     *
     * @param exchange the incoming message exchange
     */
    private String getAccept(final Exchange exchange) {
        final String acceptHeader = getAcceptHeader(exchange);
        if (!isBlank(endpoint.getAccept())) {
            return endpoint.getAccept();
        } else if (!isBlank(acceptHeader)) {
            return acceptHeader;
        } else if (!endpoint.getMetadata()) {
            return "*/*";
        } else {
            return DEFAULT_CONTENT_TYPE;
        }
    }

    /**
     * Given an exchange, extract the value of an incoming Accept header.
     *
     * @param exchange the incoming message exchange
     */
    private String getAcceptHeader(final Exchange exchange) {
        final Message in = exchange.getIn();
        if (!isBlank(in.getHeader(ACCEPT_CONTENT_TYPE, String.class))) {
            return in.getHeader(ACCEPT_CONTENT_TYPE, String.class);
        } else if (!isBlank(in.getHeader("Accept", String.class))) {
            return in.getHeader("Accept", String.class);
        } else {
            return null;
        }
    }

    /**
     * Given an exchange, extract the fully qualified URL for a fedora resource. By default, this will use the entire
     * path set on the endpoint. If either of the following headers are defined, they will be appended to that path in
     * this order of preference: 1) FCREPO_URI 2) FCREPO_BASE_URL + FCREPO_IDENTIFIER
     *
     * @param exchange the incoming message exchange
     */
    private String getUrl(final Exchange exchange, final String transaction) {
        final String uri = exchange.getIn().getHeader(FCREPO_URI, "", String.class);
        if (!uri.isEmpty()) {
            return uri;
        }

        final String baseUrl = exchange.getIn().getHeader(FCREPO_BASE_URL, "", String.class);
        final StringBuilder url = new StringBuilder(baseUrl.isEmpty() ? endpoint.getBaseUrlWithScheme() : baseUrl);
        if (transaction != null) {
            url.append("/");
            url.append(transaction);
        }
        url.append(exchange.getIn().getHeader(FCREPO_IDENTIFIER, "", String.class));

        return url.toString();
    }

    private static Object extractResponseBodyAsStream(final InputStream is, final Exchange exchange) {
        // As httpclient is using a AutoCloseInputStream, it will be closed when the connection is closed
        // we need to cache the stream for it.
        if (is == null) {
            return null;
        }

        // convert the input stream to StreamCache if the stream cache is not disabled
        if (exchange.getProperty(DISABLE_HTTP_STREAM_CACHE, FALSE, Boolean.class)) {
            return is;
        } else {
            try (final CachedOutputStream cos = new CachedOutputStream(exchange)) {
                // This CachedOutputStream will not be closed when the exchange is onCompletion
                IOHelper.copyAndCloseInput(is, cos);
                // When the InputStream is closed, the CachedOutputStream will be closed
                return cos.newStreamCache();
            } catch (final IOException ex) {
                LOGGER.debug("Error extracting body from http request", ex);
                return null;
            }
        }
    }
}
