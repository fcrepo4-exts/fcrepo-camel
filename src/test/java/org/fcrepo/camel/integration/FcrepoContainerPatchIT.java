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
import org.fcrepo.camel.FcrepoHeaders;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Test adding an RDF resource
 * @author Aaron Coburn
 * @since Dec 26, 2014
 */
@Ignore
public class FcrepoContainerPatchIT extends CamelTestSupport {

    private static final String REPOSITORY = "http://fedora.info/definitions/v4/repository#";

    @EndpointInject("mock:created")
    protected MockEndpoint createdEndpoint;

    @EndpointInject("mock:filter")
    protected MockEndpoint filteredEndpoint;

    @EndpointInject("mock:title")
    protected MockEndpoint titleEndpoint;

    @EndpointInject("mock:verifyGone")
    protected MockEndpoint goneEndpoint;

    @EndpointInject("mock:operation")
    protected MockEndpoint operationEndpoint;

    @Produce("direct:filter")
    protected ProducerTemplate template;

    @Test
    public void testGetContainer() throws InterruptedException {
        // Assertions
        createdEndpoint.expectedMessageCount(1);
        createdEndpoint.expectedHeaderReceived(Exchange.HTTP_RESPONSE_CODE, 201);

        operationEndpoint.expectedMessageCount(2);
        operationEndpoint.expectedBodiesReceived(null, null);
        operationEndpoint.expectedHeaderReceived(Exchange.HTTP_RESPONSE_CODE, 204);

        titleEndpoint.expectedMessageCount(3);
        titleEndpoint.expectedBodiesReceivedInAnyOrder(
                "some title &amp; other", "some title &amp; other", "some other title");
        titleEndpoint.expectedHeaderReceived(Exchange.CONTENT_TYPE, "application/rdf+xml");
        titleEndpoint.expectedHeaderReceived(Exchange.HTTP_RESPONSE_CODE, 200);

        filteredEndpoint.expectedMessageCount(2);
        filteredEndpoint.expectedHeaderReceived(Exchange.CONTENT_TYPE, "application/rdf+xml");
        filteredEndpoint.expectedHeaderReceived(Exchange.HTTP_RESPONSE_CODE, 200);

        goneEndpoint.expectedMessageCount(1);
        goneEndpoint.expectedHeaderReceived(Exchange.HTTP_RESPONSE_CODE, 410);

        final Map<String, Object> headers = new HashMap<>();
        headers.put(Exchange.HTTP_METHOD, "POST");
        headers.put(Exchange.CONTENT_TYPE, "text/turtle");

        final String fullPath = template.requestBodyAndHeaders(
                "direct:create",
                FcrepoTestUtils.getTurtleDocument(),
                headers, String.class);

        // Strip off the baseUrl to get the resource path
        final String identifier = fullPath.replaceAll(FcrepoTestUtils.getFcrepoBaseUrl(), "");

        template.sendBodyAndHeader("direct:title", null, FcrepoHeaders.FCREPO_IDENTIFIER, identifier);

        final String patchDoc = "PREFIX dc: <http://purl.org/dc/elements/1.1/>\n\n" +
                        "INSERT { <> dc:title \"some other title\" } WHERE {}";

        template.sendBodyAndHeader("direct:patch", patchDoc, FcrepoHeaders.FCREPO_IDENTIFIER, identifier);

        template.sendBodyAndHeader("direct:title", null, FcrepoHeaders.FCREPO_IDENTIFIER, identifier);


        template.sendBodyAndHeader("direct:delete", null, FcrepoHeaders.FCREPO_IDENTIFIER, identifier);

        // Confirm that assertions passed
        createdEndpoint.assertIsSatisfied();
        filteredEndpoint.assertIsSatisfied();
        titleEndpoint.assertIsSatisfied();
        goneEndpoint.assertIsSatisfied();
        operationEndpoint.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {

                final String fcrepoUri = FcrepoTestUtils.getFcrepoEndpointUri();

                final Namespaces ns = new Namespaces("rdf", RDF.uri);
                ns.add("dc", "http://purl.org/dc/elements/1.1/");

                from("direct:create")
                    .to(fcrepoUri)
                    .to("mock:created");

                from("direct:patch")
                    .setHeader(Exchange.HTTP_METHOD, constant("PATCH"))
                    .to(fcrepoUri)
                    .to("mock:operation");

                from("direct:title")
                    .to(fcrepoUri)
                    .convertBodyTo(org.w3c.dom.Document.class)
                    .filter().xpath(
                        "/rdf:RDF/rdf:Description/rdf:type" +
                        "[@rdf:resource='" + REPOSITORY + "Container']", ns)
                    .to("mock:filter")
                    .split().xpath(
                        "/rdf:RDF/rdf:Description/dc:title/text()", ns)
                    .to("mock:title");

                from("direct:delete")
                    .setHeader(Exchange.HTTP_METHOD, constant("DELETE"))
                    .to(fcrepoUri)
                    .to("mock:operation")
                    .setHeader(Exchange.HTTP_METHOD, constant("GET"))
                    .to(fcrepoUri + "&throwExceptionOnFailure=false")
                    .to("mock:verifyGone");
            }
        };
    }
}
