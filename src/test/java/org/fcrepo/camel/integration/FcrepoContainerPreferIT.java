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
public class FcrepoContainerPreferIT extends CamelTestSupport {

    private static final String REPOSITORY = "http://fedora.info/definitions/v4/repository#";

    private static final String LDP = "http://www.w3.org/ns/ldp#";

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
        createdEndpoint.expectedHeaderReceived(Exchange.HTTP_RESPONSE_CODE, 201);

        containerEndpoint.expectedMessageCount(1);
        containerEndpoint.expectedHeaderReceived(Exchange.CONTENT_TYPE, "application/rdf+xml");
        containerEndpoint.expectedHeaderReceived(Exchange.HTTP_RESPONSE_CODE, 200);

        filteredEndpoint.expectedMessageCount(4);
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
                "direct:create",
                FcrepoTestUtils.getTurtleDocument(),
                headers, String.class);

        // Strip off the baseUrl to get the resource path
        final String identifier = fullPath.replaceAll(FcrepoTestUtils.getFcrepoBaseUrl(), "");

        final String child = "/child";

        headers.clear();
        headers.put(Exchange.HTTP_METHOD, "PUT");
        headers.put(Exchange.CONTENT_TYPE, "text/turtle");
        headers.put(FcrepoHeaders.FCREPO_IDENTIFIER, identifier + child);

        template.sendBodyAndHeaders("direct:create", FcrepoTestUtils.getTurtleDocument(), headers);

        template.sendBodyAndHeader("direct:includeServerManaged", null, FcrepoHeaders.FCREPO_IDENTIFIER,
                identifier);
        template.sendBodyAndHeader("direct:includeContainment", null, FcrepoHeaders.FCREPO_IDENTIFIER,
                identifier);

        template.sendBodyAndHeader("direct:omitServerManaged", null, FcrepoHeaders.FCREPO_IDENTIFIER,
                identifier);
        template.sendBodyAndHeader("direct:omitContainmentShort", null, FcrepoHeaders.FCREPO_IDENTIFIER,
                identifier);
        template.sendBodyAndHeader("direct:omitContainmentFull", null, FcrepoHeaders.FCREPO_IDENTIFIER,
                identifier);

        template.sendBodyAndHeader("direct:includeContainmentOmitManaged", null, FcrepoHeaders.FCREPO_IDENTIFIER,
                identifier);

        headers.clear();
        headers.put(FcrepoHeaders.FCREPO_IDENTIFIER, identifier);
        headers.put(FcrepoHeaders.FCREPO_PREFER, "return=representation; " +
                    "omit=\"http://fedora.info/definitions/v4/repository#ServerManaged\"; " +
                    "include=\"http://www.w3.org/ns/ldp#PreferContainment\";");
        template.sendBodyAndHeaders("direct:preferHeadersCheckContainment", null, headers);
        template.sendBodyAndHeaders("direct:preferHeadersCheckServerManaged", null, headers);

        headers.put(FcrepoHeaders.FCREPO_PREFER, "return=representation; " +
                    "omit=\"http://fedora.info/definitions/v4/repository#ServerManaged " +
                    "http://www.w3.org/ns/ldp#PreferContainment\"");
        template.sendBodyAndHeaders("direct:preferHeadersCheckContainment", null, headers);
        template.sendBodyAndHeaders("direct:preferHeadersCheckServerManaged", null, headers);

        template.sendBodyAndHeader("direct:delete", null, FcrepoHeaders.FCREPO_IDENTIFIER, identifier + child);
        template.sendBodyAndHeader("direct:delete", null, FcrepoHeaders.FCREPO_IDENTIFIER, identifier);

        // Confirm that assertions passed
        createdEndpoint.assertIsSatisfied();
        filteredEndpoint.assertIsSatisfied();
        containerEndpoint.assertIsSatisfied();
        goneEndpoint.assertIsSatisfied();
        deletedEndpoint.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {

                final String fcrepoUri = FcrepoTestUtils.getFcrepoEndpointUri();

                final Namespaces ns = new Namespaces("rdf", RDF.uri);
                ns.add("fedora", REPOSITORY);
                ns.add("ldp", LDP);

                from("direct:create")
                    .to(fcrepoUri)
                    .to("mock:created");

                from("direct:preferHeadersCheckContainment")
                    .to(fcrepoUri)
                    .filter().xpath(
                        "/rdf:RDF/rdf:Description/ldp:contains", ns)
                    .to("mock:filter");

                from("direct:preferHeadersCheckServerManaged")
                    .to(fcrepoUri)
                    .filter().xpath(
                        "/rdf:RDF/rdf:Description/fedora:created", ns)
                    .to("mock:filter");

                from("direct:includeServerManaged")
                    .to(fcrepoUri + "&preferInclude=ServerManaged")
                    .filter().xpath(
                        "/rdf:RDF/rdf:Description/fedora:created", ns)
                    .to("mock:filter")
                    .to(fcrepoUri)
                    .to("mock:container");

                from("direct:includeContainmentOmitManaged")
                    .to(fcrepoUri + "&preferOmit=ServerManaged&preferInclude=PreferContainment")
                    .filter().xpath(
                        "/rdf:RDF/rdf:Description/ldp:contains", ns)
                    .to("mock:filter");

                from("direct:omitServerManaged")
                    .to(fcrepoUri + "&preferOmit=ServerManaged")
                    .filter().xpath(
                        "/rdf:RDF/rdf:Description/fedora:created", ns)
                    .to("mock:filter")
                    .to(fcrepoUri)
                    .to("mock:container");

                from("direct:includeContainment")
                    .to(fcrepoUri + "&preferInclude=PreferContainment")
                    .filter().xpath(
                            "/rdf:RDF/rdf:Description/ldp:contains", ns)
                    .to("mock:filter");

                from("direct:omitContainmentShort")
                    .to(fcrepoUri + "&preferOmit=PreferContainment")
                    .filter().xpath(
                            "/rdf:RDF/rdf:Description/ldp:contains", ns)
                    .to("mock:filter");

                from("direct:omitContainmentFull")
                    .to(fcrepoUri + "&preferOmit=PreferContainment")
                    .filter().xpath(
                            "/rdf:RDF/rdf:Description/ldp:contains", ns)
                    .to("mock:filter");

                from("direct:delete")
                    .setHeader(Exchange.HTTP_METHOD, constant("DELETE"))
                    .to(fcrepoUri)
                    .to("mock:deleted")
                    .setHeader(Exchange.HTTP_METHOD, constant("GET"))
                    .to(fcrepoUri + "&throwExceptionOnFailure=false")
                    .to("mock:verifyGone");
            }
        };
    }
}
