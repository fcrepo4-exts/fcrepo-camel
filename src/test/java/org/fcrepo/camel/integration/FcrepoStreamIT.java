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

import javax.xml.transform.stream.StreamSource;

import org.apache.camel.language.xpath.XPathBuilder;
import org.apache.camel.support.builder.Namespaces;
import org.apache.jena.vocabulary.RDF;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.fcrepo.camel.FcrepoHeaders;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Test adding an RDF resource
 * @author Aaron Coburn
 * @since Dec 26, 2014
 */
@Ignore
public class FcrepoStreamIT extends CamelTestSupport {

    final private int children = 200;

    @EndpointInject(uri = "mock:created")
    protected MockEndpoint createdEndpoint;

    @EndpointInject(uri = "mock:child")
    protected MockEndpoint childEndpoint;

    @EndpointInject(uri = "mock:verifyGone")
    protected MockEndpoint goneEndpoint;

    @EndpointInject(uri = "mock:deleted")
    protected MockEndpoint deletedEndpoint;

    @Produce(uri = "direct:filter")
    protected ProducerTemplate template;

    @Test
    public void testGetStreamedContainer() throws InterruptedException {
        // Assertions
        createdEndpoint.expectedMessageCount(children + 1);
        createdEndpoint.expectedHeaderReceived(Exchange.HTTP_RESPONSE_CODE, 201);

        childEndpoint.expectedMessageCount(children);

        deletedEndpoint.expectedMessageCount(1);
        deletedEndpoint.allMessages().body().equals(null);
        deletedEndpoint.expectedHeaderReceived(Exchange.HTTP_RESPONSE_CODE, 204);

        goneEndpoint.expectedMessageCount(1);
        goneEndpoint.expectedHeaderReceived(Exchange.HTTP_RESPONSE_CODE, 410);

        final Map<String, Object> headers = new HashMap<>();
        headers.put(Exchange.HTTP_METHOD, "PUT");
        headers.put(FcrepoHeaders.FCREPO_IDENTIFIER, "/stream");
        template.sendBodyAndHeaders("direct:create", null, headers);

        headers.clear();
        headers.put(Exchange.HTTP_METHOD, "POST");
        headers.put(Exchange.CONTENT_TYPE, "text/turtle");
        headers.put(FcrepoHeaders.FCREPO_IDENTIFIER, "/stream");

        for (int i = 0; i < children; ++i) {
            template.sendBodyAndHeaders("direct:create", FcrepoTestUtils.getTurtleDocument(), headers);
        }

        template.sendBodyAndHeader("direct:get", null, FcrepoHeaders.FCREPO_IDENTIFIER, "/stream");

        template.sendBodyAndHeader("direct:delete", null, FcrepoHeaders.FCREPO_IDENTIFIER, "/stream");

        // Confirm that assertions passed
        createdEndpoint.assertIsSatisfied();
        childEndpoint.assertIsSatisfied();
        goneEndpoint.assertIsSatisfied();
        deletedEndpoint.assertIsSatisfied();

        for (Exchange exchange : childEndpoint.getExchanges()) {
            Assert.assertTrue(exchange.getIn().getBody(String.class).contains("/stream/"));
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {

                final String fcrepo_uri = FcrepoTestUtils.getFcrepoEndpointUriWithScheme();

                final Namespaces ns = new Namespaces("rdf", RDF.uri);
                ns.add("ldp", "http://www.w3.org/ns/ldp#");

                final XPathBuilder ldpChildren = new XPathBuilder("/rdf:RDF/rdf:Description/ldp:contains");
                ldpChildren.namespaces(ns);

                getContext().getStreamCachingStrategy().setSpoolThreshold(1024);
                getContext().getStreamCachingStrategy().setBufferSize(128);
                getContext().setStreamCaching(true);

                from("direct:create")
                    .to(fcrepo_uri)
                    .to("mock:created");

                from("direct:get")
                    .streamCaching()
                    .to(fcrepo_uri)
                    .convertBodyTo(StreamSource.class)
                    .split(ldpChildren).streaming()
                        .transform()
                        .xpath("/ldp:contains/@rdf:resource", String.class, ns)
                        .to("mock:child");

                from("direct:delete")
                    .setHeader(Exchange.HTTP_METHOD, constant("DELETE"))
                    .to(fcrepo_uri)
                    .to("mock:deleted")
                    .setHeader(Exchange.HTTP_METHOD, constant("GET"))
                    .to(fcrepo_uri + "&throwExceptionOnFailure=false")
                    .to("mock:verifyGone");
            }
        };
    }
}
