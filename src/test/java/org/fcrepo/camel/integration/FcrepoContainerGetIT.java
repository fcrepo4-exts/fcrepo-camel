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

import static org.apache.camel.Exchange.CONTENT_TYPE;
import static org.apache.camel.Exchange.HTTP_METHOD;
import static org.apache.camel.Exchange.HTTP_RESPONSE_CODE;
import static org.fcrepo.camel.FcrepoHeaders.FCREPO_IDENTIFIER;
import static org.fcrepo.camel.FcrepoHeaders.FCREPO_URI;
import static org.fcrepo.camel.integration.FcrepoTestUtils.getFcrepoBaseUrl;
import static org.fcrepo.camel.integration.FcrepoTestUtils.getFcrepoEndpointUriWithScheme;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.builder.Namespaces;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.jena.vocabulary.RDF;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Test adding an RDF resource
 * @author Aaron Coburn
 * @since Dec 26, 2014
 */
@Ignore
public class FcrepoContainerGetIT extends CamelTestSupport {

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

    @Test
    public void testGetContainer() throws InterruptedException {
        // Assertions
        createdEndpoint.expectedMessageCount(2);
        createdEndpoint.expectedHeaderReceived(HTTP_RESPONSE_CODE, 201);

        containerEndpoint.expectedMessageCount(2);
        containerEndpoint.expectedHeaderReceived(CONTENT_TYPE, "application/rdf+xml");
        containerEndpoint.expectedHeaderReceived(HTTP_RESPONSE_CODE, 200);

        filteredEndpoint.expectedMessageCount(2);
        filteredEndpoint.expectedHeaderReceived(CONTENT_TYPE, "application/rdf+xml");
        filteredEndpoint.expectedHeaderReceived(HTTP_RESPONSE_CODE, 200);

        deletedEndpoint.expectedMessageCount(2);
        deletedEndpoint.expectedBodiesReceived(null, null);
        deletedEndpoint.expectedHeaderReceived(HTTP_RESPONSE_CODE, 204);

        goneEndpoint.expectedMessageCount(2);
        goneEndpoint.expectedHeaderReceived(HTTP_RESPONSE_CODE, 410);

        final Map<String, Object> headers = new HashMap<>();
        headers.put(HTTP_METHOD, "POST");
        headers.put(CONTENT_TYPE, "text/turtle");

        final String fullPath = template.requestBodyAndHeaders(
                "direct:create",
                FcrepoTestUtils.getTurtleDocument(),
                headers, String.class);

        // Strip off the baseUrl to get the resource path
        final String identifier = fullPath.replaceAll(getFcrepoBaseUrl(), "");
        final String binary = "/file";

        headers.clear();
        headers.put(HTTP_METHOD, "PUT");
        headers.put(CONTENT_TYPE, "text/plain");
        headers.put(FCREPO_IDENTIFIER, identifier + binary);

        template.sendBodyAndHeaders("direct:create", FcrepoTestUtils.getTextDocument(), headers);

        template.sendBodyAndHeader("direct:get", null, FCREPO_IDENTIFIER, identifier);
        template.sendBodyAndHeader("direct:get", null, FCREPO_IDENTIFIER, identifier + binary);
        template.sendBodyAndHeader("direct:get", null, FCREPO_URI, getFcrepoBaseUrl() + identifier);
        template.sendBodyAndHeader("direct:get", null, FCREPO_URI, getFcrepoBaseUrl() + identifier + binary);

        template.sendBodyAndHeader("direct:delete", null, FCREPO_IDENTIFIER, identifier + binary);
        template.sendBodyAndHeader("direct:delete", null, FCREPO_IDENTIFIER, identifier);

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
        return new RouteBuilder() {
            @Override
            public void configure() {

                final String fcrepoUri = getFcrepoEndpointUriWithScheme();

                final Namespaces ns = new Namespaces("rdf", RDF.uri);

                from("direct:create")
                    .to(fcrepoUri)
                    .to("mock:created");

                // use an explicit scheme with the fcrepo: endpoint
                from("direct:get")
                    .to(fcrepoUri)
                    .filter().xpath(
                        "/rdf:RDF/rdf:Description/rdf:type" +
                        "[@rdf:resource='" + REPOSITORY + "Container']", ns)
                    .to("mock:filter")
                    .to(fcrepoUri)
                    .to("mock:container");

                from("direct:delete")
                    .setHeader(HTTP_METHOD, constant("DELETE"))
                    .to(fcrepoUri)
                    .to("mock:deleted")
                    .setHeader(HTTP_METHOD, constant("GET"))
                    .to(fcrepoUri + "&throwExceptionOnFailure=false")
                    .to("mock:verifyGone");
            }
        };
    }
}
