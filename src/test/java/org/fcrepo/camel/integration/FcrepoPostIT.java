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
import org.apache.camel.language.xpath.XPathBuilder;
import org.apache.camel.support.builder.Namespaces;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.jena.vocabulary.RDF;
import org.fcrepo.camel.FcrepoHeaders;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Test adding a new resource with POST
 * @author Aaron Coburn
 * @since November 7, 2014
 */
@Ignore
public class FcrepoPostIT extends CamelTestSupport {

    private static final String REPOSITORY = "http://fedora.info/definitions/v4/repository#";

    @EndpointInject(uri = "mock:created")
    protected MockEndpoint createdEndpoint;

    @EndpointInject(uri = "mock:result")
    protected MockEndpoint resultEndpoint;

    @EndpointInject(uri = "mock:deleted")
    protected MockEndpoint deletedEndpoint;

    @Produce(uri = "direct:start")
    protected ProducerTemplate template;

    @Test
    public void testPost() throws InterruptedException {
        // Assertions
        resultEndpoint.expectedMessageCount(1);
        resultEndpoint.expectedBodiesReceived("some title &amp; other");

        createdEndpoint.expectedMessageCount(1);
        createdEndpoint.expectedHeaderReceived(Exchange.HTTP_RESPONSE_CODE, 201);

        deletedEndpoint.expectedMessageCount(1);
        deletedEndpoint.expectedHeaderReceived(Exchange.HTTP_RESPONSE_CODE, 204);

        // Setup
        final Map<String, Object> headers = new HashMap<>();
        headers.put(Exchange.HTTP_METHOD, "POST");
        headers.put(Exchange.CONTENT_TYPE, "text/turtle");

        final String fullPath = template.requestBodyAndHeaders(
                "direct:setup", FcrepoTestUtils.getTurtleDocument(), headers, String.class);

        final String identifier = fullPath.replaceAll(FcrepoTestUtils.getFcrepoBaseUrl(), "");

        // Test
        template.sendBodyAndHeader(null, FcrepoHeaders.FCREPO_IDENTIFIER, identifier);

        // Teardown
        final Map<String, Object> teardownHeaders = new HashMap<>();
        teardownHeaders.put(Exchange.HTTP_METHOD, "DELETE");
        teardownHeaders.put(FcrepoHeaders.FCREPO_IDENTIFIER, identifier);
        template.sendBodyAndHeaders("direct:teardown", null, teardownHeaders);

        // Confirm that the assertions passed
        resultEndpoint.assertIsSatisfied();
        deletedEndpoint.assertIsSatisfied();
        createdEndpoint.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                final String fcrepo_uri = FcrepoTestUtils.getFcrepoEndpointUri();

                final Namespaces ns = new Namespaces("rdf", RDF.uri);

                final XPathBuilder titleXpath = new XPathBuilder("/rdf:RDF/rdf:Description/dc:title/text()");
                titleXpath.namespaces(ns);
                titleXpath.namespace("dc", "http://purl.org/dc/elements/1.1/");

                from("direct:setup")
                    .to(fcrepo_uri)
                    .to("mock:created");

                from("direct:start")
                    .to(fcrepo_uri)
                    .convertBodyTo(org.w3c.dom.Document.class)
                    .filter().xpath(
                        "/rdf:RDF/rdf:Description/rdf:type" +
                        "[@rdf:resource='" + REPOSITORY + "Resource']", ns)
                    .split(titleXpath)
                    .to("mock:result");

                from("direct:teardown")
                    .to(fcrepo_uri)
                    .to("mock:deleted");
            }
        };
    }
}
