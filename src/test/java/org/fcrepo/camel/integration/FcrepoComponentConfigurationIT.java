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
package org.fcrepo.camel.integration;

import static org.fcrepo.camel.integration.FcrepoTestUtils.getFcrepoBaseUrl;
import static org.fcrepo.camel.integration.FcrepoTestUtils.getTextDocument;
import static org.fcrepo.camel.integration.FcrepoTestUtils.getTurtleDocument;
import static org.fcrepo.camel.integration.FcrepoTestUtils.FCREPO_USERNAME;
import static org.fcrepo.camel.integration.FcrepoTestUtils.FCREPO_PASSWORD;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.builder.Namespaces;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.jena.vocabulary.RDF;
import org.fcrepo.camel.FcrepoComponent;
import org.fcrepo.camel.FcrepoHeaders;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Test adding an RDF resource
 * @author Aaron Coburn
 * @since Dec 26, 2014
 */
@Ignore
public class FcrepoComponentConfigurationIT extends CamelTestSupport {

    private static final String REPOSITORY = "http://fedora.info/definitions/v4/repository#";

    @EndpointInject("mock:created")
    protected MockEndpoint createdEndpoint;

    @EndpointInject("mock:filter")
    protected MockEndpoint filteredEndpoint;

    @EndpointInject("mock:container")
    protected MockEndpoint containerEndpoint;

    @EndpointInject("mock:verifyGone")
    protected MockEndpoint goneEndpoint;

    @EndpointInject("mock:deleted")
    protected MockEndpoint deletedEndpoint;

    @Produce("direct:filter")
    protected ProducerTemplate template;

    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @Test
    public void testGetContainer() throws InterruptedException {
        // Assertions
        createdEndpoint.expectedMessageCount(2);
        createdEndpoint.expectedHeaderReceived(Exchange.HTTP_RESPONSE_CODE, 201);

        containerEndpoint.expectedMessageCount(1);
        containerEndpoint.expectedHeaderReceived(Exchange.CONTENT_TYPE, "application/rdf+xml");
        containerEndpoint.expectedHeaderReceived(Exchange.HTTP_RESPONSE_CODE, 200);

        filteredEndpoint.expectedMessageCount(1);
        filteredEndpoint.expectedHeaderReceived(Exchange.CONTENT_TYPE, "application/rdf+xml");
        filteredEndpoint.expectedHeaderReceived(Exchange.HTTP_RESPONSE_CODE, 200);

        deletedEndpoint.expectedMessageCount(2);
        deletedEndpoint.expectedBodiesReceived(null, null);
        deletedEndpoint.expectedHeaderReceived(Exchange.HTTP_RESPONSE_CODE, 204);

        goneEndpoint.expectedMessageCount(2);
        goneEndpoint.expectedHeaderReceived(Exchange.HTTP_RESPONSE_CODE, 410);

        final Map<String, Object> headers = new HashMap<>();
        headers.put(Exchange.HTTP_METHOD, "POST");
        headers.put(Exchange.CONTENT_TYPE, "text/turtle");

        final String fullPath = template.requestBodyAndHeaders(
                "direct:create", getTurtleDocument(), headers, String.class);

        // Strip off the baseUrl to get the resource path
        final String identifier = fullPath.replaceAll(getFcrepoBaseUrl(), "");
        final String binary = "/file";

        headers.clear();
        headers.put(Exchange.HTTP_METHOD, "PUT");
        headers.put(Exchange.CONTENT_TYPE, "text/plain");
        headers.put(FcrepoHeaders.FCREPO_IDENTIFIER, identifier + binary);

        template.sendBodyAndHeaders("direct:create", getTextDocument(), headers);

        template.sendBodyAndHeader("direct:get", null, FcrepoHeaders.FCREPO_IDENTIFIER, identifier);
        template.sendBodyAndHeader("direct:get", null, FcrepoHeaders.FCREPO_IDENTIFIER, identifier + binary);

        template.sendBodyAndHeader("direct:delete", null, FcrepoHeaders.FCREPO_IDENTIFIER, identifier + binary);
        template.sendBodyAndHeader("direct:delete", null, FcrepoHeaders.FCREPO_IDENTIFIER, identifier);

        // Confirm that assertions passed
        createdEndpoint.assertIsSatisfied();
        filteredEndpoint.assertIsSatisfied();
        containerEndpoint.assertIsSatisfied();
        goneEndpoint.assertIsSatisfied();
        deletedEndpoint.assertIsSatisfied();

        // skip first message, as we've already extracted the body
        assertEquals(getFcrepoBaseUrl() + identifier + binary,
                createdEndpoint.getExchanges().get(1).getIn().getBody(String.class));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {

        final FcrepoComponent fcrepo = (FcrepoComponent)context.getComponent("fcrepo");
        fcrepo.setBaseUrl(getFcrepoBaseUrl());
        fcrepo.setAuthUsername(FCREPO_USERNAME);
        fcrepo.setAuthPassword(FCREPO_PASSWORD);

        return new RouteBuilder() {
            @Override
            public void configure() {

                final Namespaces ns = new Namespaces("rdf", RDF.uri);

                from("direct:create")
                    .to("fcrepo:foo")
                    .to("mock:created");

                // use an explicit scheme with the fcrepo: endpoint
                from("direct:get")
                    .to("fcrepo:bar")
                    .filter().xpath(
                        "/rdf:RDF/rdf:Description/rdf:type" +
                        "[@rdf:resource='" + REPOSITORY + "Container']", ns)
                    .to("mock:filter")
                    .to("fcrepo:baz")
                    .to("mock:container");

                from("direct:delete")
                    .setHeader(Exchange.HTTP_METHOD, constant("DELETE"))
                    .to("fcrepo:foo")
                    .to("mock:deleted")
                    .setHeader(Exchange.HTTP_METHOD, constant("GET"))
                    .to("fcrepo:bar?throwExceptionOnFailure=false")
                    .to("mock:verifyGone");
            }
        };
    }
}
