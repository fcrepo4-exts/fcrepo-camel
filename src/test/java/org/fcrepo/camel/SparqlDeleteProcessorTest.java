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

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.NoSuchHeaderException;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.fcrepo.camel.processor.SparqlDeleteProcessor;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;

import static java.net.URLEncoder.encode;
import static org.fcrepo.camel.FcrepoHeaders.FCREPO_BASE_URL;
import static org.fcrepo.camel.FcrepoHeaders.FCREPO_IDENTIFIER;
import static org.fcrepo.camel.FcrepoHeaders.FCREPO_URI;

/**
 * Test adding a non-RDF resource
 * @author Aaron Coburn
 * @since November 7, 2014
 */
public class SparqlDeleteProcessorTest extends CamelTestSupport {

    @EndpointInject("mock:result")
    protected MockEndpoint resultEndpoint;

    @Produce("direct:start")
    protected ProducerTemplate template;

    @Test
    public void missingHeaders() throws IOException, InterruptedException {
        final Exchange in = new DefaultExchange(context());
        in.getIn().setHeader(FCREPO_IDENTIFIER, "/foo");
        final Exchange out = template.send(in);
        assertTrue(out.isFailed());
        assertTrue(out.getException() instanceof NoSuchHeaderException);
    }

    @Test
    @Ignore
    public void testDelete() throws IOException, InterruptedException {
        final String base = "http://localhost/rest/";
        final String uri = "path/book3";
        final String incomingDoc =
            "<rdf:RDF" +
            "    xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\"" +
            "    xmlns:vcard=\"http://www.w3.org/2001/vcard-rdf/3.0#\"" +
            "    xmlns:dc=\"http://purl.org/dc/elements/1.1/\"" +
            "    xmlns=\"" + uri + "\">" +
            "  <rdf:Description rdf:about=\"" + base + "\">" +
            "    <dc:title>Silas Marner</dc:title>" +
            "  </rdf:Description>" +
            "  <rdf:Description rdf:about=\"" + uri + "\">" +
            "    <dc:title>Middlemarch</dc:title>" +
            "    <dc:relation rdf:resource=\"" + uri + "/appendix\"/>" +
            "    <dc:relation rdf:resource=\"" + uri + "#appendix2\"/>" +
            "    <dc:relation rdf:resource=\"http://some-other-uri/appendix3\"/>" +
            "    <dc:relation rdf:resource=\"" + uri + "\"/>" +
            "    <dc:creator rdf:parseType=\"Resource\">" +
            "      <vcard:FN>George Elliot</vcard:FN>" +
            "      <vcard:N rdf:parseType=\"Resource\">" +
            "        <vcard:Family>Elliot</vcard:Family>" +
            "        <vcard:Given>George</vcard:Given>" +
            "      </vcard:N>" +
            "    </dc:creator>" +
            "  </rdf:Description>" +
            "</rdf:RDF>";

        // Assertions
        resultEndpoint.expectedBodiesReceived("update=" +
                encode("DELETE WHERE { <" + uri + "> ?p ?o }", "UTF-8"));
        resultEndpoint.expectedHeaderReceived(Exchange.CONTENT_TYPE,
                "application/x-www-form-urlencoded; charset=utf-8");
        resultEndpoint.expectedHeaderReceived(Exchange.HTTP_METHOD, "POST");

        // Test
        template.sendBodyAndHeader(incomingDoc, FCREPO_URI, uri);
        //@FIXME Not sure why this is blowing up with the camel upgrade.
        template.sendBodyAndHeader(incomingDoc, FCREPO_BASE_URL, uri);

        // Confirm that assertions passed
        resultEndpoint.expectedMessageCount(2);
        resultEndpoint.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws IOException {
                onException(IOException.class)
                    .handled(true);

                from("direct:start")
                    .process(new SparqlDeleteProcessor())
                    .to("mock:result");
            }
        };
    }
}
