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

import static java.net.URI.create;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.apache.camel.Exchange.ACCEPT_CONTENT_TYPE;
import static org.apache.camel.Exchange.CONTENT_TYPE;
import static org.apache.camel.Exchange.DISABLE_HTTP_STREAM_CACHE;
import static org.apache.camel.Exchange.HTTP_METHOD;
import static org.apache.camel.Exchange.HTTP_RESPONSE_CODE;
import static org.fcrepo.camel.FcrepoHeaders.FCREPO_IDENTIFIER;
import static org.fcrepo.camel.FcrepoHeaders.FCREPO_PREFER;
import static org.fcrepo.camel.FcrepoProducer.PREFER_PROPERTIES;
import static org.fcrepo.camel.TestUtils.N_TRIPLES;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.ExtendedExchange;
import org.apache.camel.converter.stream.InputStreamCache;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.engine.DefaultUnitOfWork;
import org.apache.camel.support.DefaultExchange;
import org.apache.commons.lang3.StringUtils;
import org.fcrepo.client.DeleteBuilder;
import org.fcrepo.client.FcrepoClient;
import org.fcrepo.client.FcrepoOperationFailedException;
import org.fcrepo.client.FcrepoResponse;
import org.fcrepo.client.GetBuilder;
import org.fcrepo.client.HeadBuilder;
import org.fcrepo.client.HttpMethods;
import org.fcrepo.client.PatchBuilder;
import org.fcrepo.client.PostBuilder;
import org.fcrepo.client.PutBuilder;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * @author acoburn
 */
@RunWith(MockitoJUnitRunner.class)
public class FcrepoProducerTest {

    private static final String REPOSITORY = "http://fedora.info/definitions/v4/repository#";

    private static final String LDP = "http://www.w3.org/ns/ldp#";

    private FcrepoEndpoint testEndpoint;

    private FcrepoProducer testProducer;

    private Exchange testExchange;

    @Mock
    private FcrepoClient mockClient, mockClient2;

    @Mock
    private GetBuilder mockGetBuilder, mockGetBuilder2, mockGetBuilder3;

    @Mock
    private HeadBuilder mockHeadBuilder;

    @Mock
    private DeleteBuilder mockDeleteBuilder;

    @Mock
    private PostBuilder mockPostBuilder, mockPostBuilder2, mockPostBuilder3;

    @Mock
    private PatchBuilder mockPatchBuilder;

    @Mock
    private PutBuilder mockPutBuilder;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() throws Exception {
        final FcrepoComponent mockComponent = mock(FcrepoComponent.class);

        final FcrepoConfiguration testConfig = new FcrepoConfiguration();
        testEndpoint = new FcrepoEndpoint("fcrepo:localhost:8080", "/rest", mockComponent, testConfig);
        testEndpoint.setBaseUrl("localhost:8080/rest");
        testExchange = new DefaultExchange(new DefaultCamelContext());
        testExchange.getIn().setBody(null);
        when(mockClient.get(any(URI.class))).thenReturn(mockGetBuilder);
        when(mockClient2.get(any(URI.class))).thenReturn(mockGetBuilder);
        when(mockClient2.head(any(URI.class))).thenReturn(mockHeadBuilder);
        when(mockGetBuilder.accept(any(String.class))).thenReturn(mockGetBuilder);
        when(mockGetBuilder.preferRepresentation(any(List.class), any(List.class))).thenReturn(mockGetBuilder);
        when(mockGetBuilder2.accept(any(String.class))).thenReturn(mockGetBuilder2);
        when(mockGetBuilder3.accept(any(String.class))).thenReturn(mockGetBuilder3);
        when(mockClient.head(any(URI.class))).thenReturn(mockHeadBuilder);
        when(mockClient.delete(any(URI.class))).thenReturn(mockDeleteBuilder);
        when(mockClient.patch(any(URI.class))).thenReturn(mockPatchBuilder);
        when(mockClient.post(any(URI.class))).thenReturn(mockPostBuilder);
        when(mockClient.put(any(URI.class))).thenReturn(mockPutBuilder);
        when(mockPatchBuilder.body(any(InputStream.class))).thenReturn(mockPatchBuilder);
        when(mockPostBuilder.body(any(InputStream.class), any(String.class))).thenReturn(mockPostBuilder);
        when(mockPostBuilder2.body(any(InputStream.class), any(String.class))).thenReturn(mockPostBuilder2);
        when(mockPostBuilder3.body(any(InputStream.class), any(String.class))).thenReturn(mockPostBuilder3);
        when(mockPutBuilder.body(any(InputStream.class), any(String.class))).thenReturn(mockPutBuilder);
    }

    public void init() throws IOException {
        testProducer = new FcrepoProducer(testEndpoint);
        TestUtils.setField(testProducer, "fcrepoClient", mockClient);
    }

    @Test
    public void testGetProducer() throws Exception {
        final URI uri = create(TestUtils.baseUrl);
        final int status = 200;
        final ByteArrayInputStream body = new ByteArrayInputStream(TestUtils.rdfXml.getBytes());
        final FcrepoResponse headResponse = new FcrepoResponse(uri, 200, emptyMap(), null);
        final FcrepoResponse getResponse = new FcrepoResponse(uri, status,
                singletonMap(CONTENT_TYPE, singletonList(TestUtils.RDF_XML)), body);

        init();

        testExchange.getIn().setHeader(FCREPO_IDENTIFIER, "/foo");

        when(mockHeadBuilder.perform()).thenReturn(headResponse);
        when(mockGetBuilder.perform()).thenReturn(getResponse);

        testProducer.process(testExchange);

        assertEquals(testExchange.getIn().getBody(String.class), TestUtils.rdfXml);
        assertEquals(testExchange.getIn().getHeader(CONTENT_TYPE, String.class), TestUtils.RDF_XML);
        assertEquals(testExchange.getIn().getHeader(HTTP_RESPONSE_CODE), status);
    }

    @Test
    public void testGetAcceptHeaderProducer() throws Exception {
        final URI uri = create(TestUtils.baseUrl);
        final ByteArrayInputStream body = new ByteArrayInputStream(TestUtils.rdfTriples.getBytes());
        final FcrepoResponse headResponse = new FcrepoResponse(uri, 200, emptyMap(), null);
        final FcrepoResponse getResponse = new FcrepoResponse(uri, 200,
                singletonMap(CONTENT_TYPE, singletonList(N_TRIPLES)), body);

        init();

        testExchange.getIn().setHeader(FCREPO_IDENTIFIER, "/foo");
        testExchange.getIn().setHeader("Accept", N_TRIPLES);

        when(mockHeadBuilder.perform()).thenReturn(headResponse);
        when(mockGetBuilder.perform()).thenReturn(getResponse);

        testProducer.process(testExchange);

        assertEquals(testExchange.getIn().getBody(String.class), TestUtils.rdfTriples);
        assertEquals(testExchange.getIn().getHeader(CONTENT_TYPE, String.class), N_TRIPLES);
    }

    @Test
    public void testGetPreferHeaderProducer() throws Exception {
        final URI uri = create(TestUtils.baseUrl);
        final ByteArrayInputStream body = new ByteArrayInputStream(TestUtils.rdfTriples.getBytes());
        final FcrepoResponse headResponse = new FcrepoResponse(uri, 200, emptyMap(), null);
        final FcrepoResponse getResponse = new FcrepoResponse(uri, 200,
                singletonMap(CONTENT_TYPE, singletonList(N_TRIPLES)) , body);
        final String prefer = "return=representation; omit=\"http://www.w3.org/ns/ldp#PreferContainment\";";

        init();

        testExchange.getIn().setHeader(FCREPO_IDENTIFIER, "/foo");
        testExchange.getIn().setHeader(FCREPO_PREFER, prefer);

        when(mockHeadBuilder.perform()).thenReturn(headResponse);
        when(mockGetBuilder.perform()).thenReturn(getResponse);

        testProducer.process(testExchange);

        assertEquals(testExchange.getIn().getBody(String.class), TestUtils.rdfTriples);
        assertEquals(testExchange.getIn().getHeader(CONTENT_TYPE, String.class), N_TRIPLES);
    }

    @Test
    public void testGetFixity() throws Exception {
        final String baseUrl = "http://localhost:8080/rest";
        final String path = "/binary";
        final URI uri = create(baseUrl + path);
        final ByteArrayInputStream body = new ByteArrayInputStream(TestUtils.fixityTriples.getBytes());
        final FcrepoResponse headResponse = new FcrepoResponse(uri, 200, emptyMap(), null);
        final FcrepoResponse getResponse = new FcrepoResponse(uri, 200,
                singletonMap(CONTENT_TYPE, singletonList(N_TRIPLES)), body);

        testEndpoint.setFixity(true);

        init();

        testExchange.getIn().setHeader(FCREPO_IDENTIFIER, path);

        when(mockHeadBuilder.perform()).thenReturn(headResponse);
        when(mockGetBuilder.perform()).thenReturn(getResponse);

        testProducer.process(testExchange);

        assertEquals(testExchange.getIn().getBody(String.class), TestUtils.fixityTriples);
        assertEquals(testExchange.getIn().getHeader(CONTENT_TYPE, String.class), N_TRIPLES);
    }

    @Test
    public void testGetPreferIncludeLongEndpointProducer() throws Exception {
        final URI uri = create(TestUtils.baseUrl);
        final ByteArrayInputStream body = new ByteArrayInputStream(TestUtils.rdfTriples.getBytes());
        final FcrepoResponse headResponse = new FcrepoResponse(uri, 200, emptyMap(), null);
        final FcrepoResponse getResponse = new FcrepoResponse(uri, 200,
                singletonMap(CONTENT_TYPE, singletonList(N_TRIPLES)), body);
        final String prefer = "return=representation; " +
                    "include=\"http://fedora.info/definitions/v4/repository#ServerManaged\";";

        testEndpoint.setPreferInclude("http://fedora.info/definitions/v4/repository#ServerManaged");

        init();

        testExchange.getIn().setHeader(FCREPO_IDENTIFIER, "/foo");

        when(mockHeadBuilder.perform()).thenReturn(headResponse);
        when(mockGetBuilder.perform()).thenReturn(getResponse);

        testProducer.process(testExchange);

        assertEquals(testExchange.getIn().getBody(String.class), TestUtils.rdfTriples);
        assertEquals(testExchange.getIn().getHeader(CONTENT_TYPE, String.class), N_TRIPLES);
    }

    @Test
    public void testGetPreferIncludeShortEndpointProducer() throws Exception {
        final URI uri = create(TestUtils.baseUrl);
        final ByteArrayInputStream body = new ByteArrayInputStream(TestUtils.rdfTriples.getBytes());
        final FcrepoResponse headResponse = new FcrepoResponse(uri, 200, emptyMap(), null);
        final FcrepoResponse getResponse = new FcrepoResponse(uri, 200,
                singletonMap(CONTENT_TYPE, singletonList(N_TRIPLES)), body);
        final String prefer = "return=representation; include=\"http://www.w3.org/ns/ldp#PreferMembership\";";

        testEndpoint.setPreferInclude("PreferMembership");

        init();

        testExchange.getIn().setHeader(FCREPO_IDENTIFIER, "/foo");

        when(mockHeadBuilder.perform()).thenReturn(headResponse);
        when(mockGetBuilder.perform()).thenReturn(getResponse);

        testProducer.process(testExchange);

        assertEquals(testExchange.getIn().getBody(String.class), TestUtils.rdfTriples);
        assertEquals(testExchange.getIn().getHeader(CONTENT_TYPE, String.class), N_TRIPLES);
    }

    @Test
    public void testGetPreferOmitLongEndpointProducer() throws Exception {
        final URI uri = create(TestUtils.baseUrl);
        final ByteArrayInputStream body = new ByteArrayInputStream(TestUtils.rdfTriples.getBytes());
        final FcrepoResponse headResponse = new FcrepoResponse(uri, 200, emptyMap(), null);
        final FcrepoResponse getResponse = new FcrepoResponse(uri, 200,
                singletonMap(CONTENT_TYPE, singletonList(N_TRIPLES)), body);
        final String embed = "http://fedora.info/definitions/v4/repository#EmbedResources";

        testEndpoint.setPreferOmit(embed);

        init();

        testExchange.getIn().setHeader(FCREPO_IDENTIFIER, "/foo");

        when(mockHeadBuilder.perform()).thenReturn(headResponse);
        when(mockGetBuilder.perform()).thenReturn(getResponse);

        testProducer.process(testExchange);

        assertEquals(testExchange.getIn().getBody(String.class), TestUtils.rdfTriples);
        assertEquals(testExchange.getIn().getHeader(CONTENT_TYPE, String.class), N_TRIPLES);
    }

    @Test
    public void testGetPreferOmitShortEndpointProducer() throws Exception {
        final URI uri = create(TestUtils.baseUrl);
        final ByteArrayInputStream body = new ByteArrayInputStream(TestUtils.rdfTriples.getBytes());
        final FcrepoResponse headResponse = new FcrepoResponse(uri, 200, emptyMap(), null);
        final FcrepoResponse getResponse = new FcrepoResponse(uri, 200,
                singletonMap(CONTENT_TYPE, singletonList(N_TRIPLES)), body);
        final String prefer = "return=representation; omit=\"http://www.w3.org/ns/ldp#PreferContainment\";";

        testEndpoint.setPreferOmit("PreferContainment");

        init();

        testExchange.getIn().setHeader(FCREPO_IDENTIFIER, "/foo");

        when(mockHeadBuilder.perform()).thenReturn(headResponse);
        when(mockGetBuilder.perform()).thenReturn(getResponse);

        testProducer.process(testExchange);

        assertEquals(testExchange.getIn().getBody(String.class), TestUtils.rdfTriples);
        assertEquals(testExchange.getIn().getHeader(CONTENT_TYPE, String.class), N_TRIPLES);
    }

    @Test
    public void testGetAcceptEndpointProducer() throws Exception {
        final URI uri = create(TestUtils.baseUrl);
        final ByteArrayInputStream body = new ByteArrayInputStream(TestUtils.rdfTriples.getBytes());
        final FcrepoResponse headResponse = new FcrepoResponse(uri, 200, emptyMap(), null);
        final FcrepoResponse getResponse = new FcrepoResponse(uri, 200,
                singletonMap(CONTENT_TYPE, singletonList(N_TRIPLES)), body);

        testEndpoint.setAccept(N_TRIPLES);

        init();

        testExchange.getIn().setHeader(FCREPO_IDENTIFIER, "/foo");

        when(mockHeadBuilder.perform()).thenReturn(headResponse);
        when(mockGetBuilder.perform()).thenReturn(getResponse);

        testProducer.process(testExchange);

        assertEquals(testExchange.getIn().getBody(String.class), TestUtils.rdfTriples);
        assertEquals(testExchange.getIn().getHeader(CONTENT_TYPE, String.class), N_TRIPLES);
    }

    @Test
    public void testGetRootProducer() throws Exception {
        final URI uri = create("http://localhost:8080/rest");
        final ByteArrayInputStream body = new ByteArrayInputStream(TestUtils.rdfXml.getBytes());
        final FcrepoResponse headResponse = new FcrepoResponse(uri, 200, emptyMap(), null);
        final FcrepoResponse getResponse = new FcrepoResponse(uri, 200,
                singletonMap(CONTENT_TYPE, singletonList(TestUtils.RDF_XML)), body);

        init();

        when(mockHeadBuilder.perform()).thenReturn(headResponse);
        when(mockGetBuilder.perform()).thenReturn(getResponse);

        testProducer.process(testExchange);

        assertEquals(testExchange.getIn().getBody(String.class), TestUtils.rdfXml);
        assertEquals(testExchange.getIn().getHeader(CONTENT_TYPE, String.class), TestUtils.RDF_XML);
    }

    @Test
    public void testGetBinaryProducer() throws Exception {
        final URI uri = create(TestUtils.baseUrl);
        final String content = "Foo";
        final ByteArrayInputStream body = new ByteArrayInputStream(content.getBytes());
        final FcrepoResponse getResponse = new FcrepoResponse(uri, 200,
                singletonMap(CONTENT_TYPE, singletonList(TestUtils.TEXT_PLAIN)), body);

        testEndpoint.setMetadata(false);

        init();

        testExchange.getIn().setHeader(FCREPO_IDENTIFIER, "/foo");
        testExchange.getIn().setHeader(ACCEPT_CONTENT_TYPE, TestUtils.TEXT_PLAIN);
        testExchange.getIn().setHeader(HTTP_METHOD, HttpMethods.GET);

        when(mockGetBuilder.perform()).thenReturn(getResponse);

        testProducer.process(testExchange);

        assertEquals(testExchange.getIn().getBody(String.class), content);
        assertEquals(testExchange.getIn().getHeader(CONTENT_TYPE, String.class), TestUtils.TEXT_PLAIN);
    }

    @Test
    public void testHeadProducer() throws Exception {
        final URI uri = create(TestUtils.baseUrl);
        final Map<String, List<String>> headers = new HashMap<>();
        headers.put(CONTENT_TYPE, singletonList(N_TRIPLES));
        headers.put("Link", singletonList("<" + TestUtils.baseUrl + "/bar>; rel=\"describedby\""));
        final int status = 200;
        final FcrepoResponse headResponse = new FcrepoResponse(uri, status, headers, null);

        init();

        testExchange.getIn().setHeader(FCREPO_IDENTIFIER, "/foo");
        testExchange.getIn().setHeader(HTTP_METHOD, HttpMethods.HEAD);

        when(mockHeadBuilder.perform()).thenReturn(headResponse);

        testProducer.process(testExchange);

        assertEquals(testExchange.getIn().getBody(), null);
        assertEquals(testExchange.getIn().getHeader(CONTENT_TYPE, String.class), N_TRIPLES);
        assertEquals(testExchange.getIn().getHeader(HTTP_RESPONSE_CODE), status);
    }

    @Test
    public void testDeleteProducer() throws Exception {
        final URI uri = create(TestUtils.baseUrl);
        final int status = 204;
        final FcrepoResponse deleteResponse = new FcrepoResponse(uri, status, emptyMap(), null);

        init();

        testExchange.getIn().setHeader(FCREPO_IDENTIFIER, "/foo");
        testExchange.getIn().setHeader(HTTP_METHOD, HttpMethods.DELETE);

        when(mockDeleteBuilder.perform()).thenReturn(deleteResponse);

        testProducer.process(testExchange);

        assertEquals(testExchange.getIn().getBody(), null);
        assertEquals(testExchange.getIn().getHeader(CONTENT_TYPE), null);
        assertEquals(testExchange.getIn().getHeader(HTTP_RESPONSE_CODE), status);
    }

    @Test
    public void testPostProducer() throws Exception {
        final URI uri = create(TestUtils.baseUrl);
        final String responseText = TestUtils.baseUrl + "/e8/0b/ab/e80bab60";
        final int status = 201;
        final FcrepoResponse postResponse = new FcrepoResponse(uri, status,
                singletonMap(CONTENT_TYPE, singletonList(TestUtils.TEXT_PLAIN)),
                new ByteArrayInputStream(responseText.getBytes()));

        init();

        testExchange.getIn().setHeader(FCREPO_IDENTIFIER, "/foo");
        testExchange.getIn().setHeader(HTTP_METHOD, HttpMethods.POST);

        when(mockPostBuilder.perform()).thenReturn(postResponse);

        testProducer.process(testExchange);

        assertEquals(testExchange.getIn().getBody(String.class), responseText);
        assertEquals(testExchange.getIn().getHeader(CONTENT_TYPE), TestUtils.TEXT_PLAIN);
        assertEquals(testExchange.getIn().getHeader(HTTP_RESPONSE_CODE), status);
    }

    @Test
    public void testPostContentTypeEndpointProducer() throws Exception {
        final URI uri = create(TestUtils.baseUrl);
        final int status = 204;
        final FcrepoResponse headResponse = new FcrepoResponse(uri, 200, emptyMap(), null);
        final FcrepoResponse postResponse = new FcrepoResponse(uri, status, emptyMap(), null);

        testEndpoint.setContentType(TestUtils.SPARQL_UPDATE);

        init();

        testExchange.getIn().setHeader(FCREPO_IDENTIFIER, "/foo");
        testExchange.getIn().setHeader(HTTP_METHOD, HttpMethods.POST);
        testExchange.getIn().setBody(new ByteArrayInputStream(TestUtils.sparqlUpdate.getBytes()));

        when(mockHeadBuilder.perform()).thenReturn(headResponse);
        when(mockPostBuilder.perform()).thenReturn(postResponse);

        testProducer.process(testExchange);

        assertEquals(testExchange.getIn().getBody(), null);
        assertEquals(testExchange.getIn().getHeader(Exchange.HTTP_RESPONSE_CODE), status);
    }

    @Test
    public void testPatchProducer() throws Exception {
        final URI uri = create(TestUtils.baseUrl);
        final String metadata = "<" + TestUtils.baseUrl + "/bar>; rel=\"describedby\"";
        final int status = 204;
        final FcrepoResponse headResponse = new FcrepoResponse(uri, 200,
                singletonMap("Link", singletonList(metadata)), null);
        final FcrepoResponse patchResponse = new FcrepoResponse(uri, status, emptyMap(), null);

        init();

        testExchange.getIn().setHeader(FCREPO_IDENTIFIER, "/foo");
        testExchange.getIn().setHeader(HTTP_METHOD, HttpMethods.PATCH);
        testExchange.getIn().setBody(new ByteArrayInputStream(TestUtils.sparqlUpdate.getBytes()));

        when(mockHeadBuilder.perform()).thenReturn(headResponse);
        when(mockPatchBuilder.perform()).thenReturn(patchResponse);

        testProducer.process(testExchange);

        assertEquals(testExchange.getIn().getBody(), null);
        assertEquals(testExchange.getIn().getHeader(Exchange.HTTP_RESPONSE_CODE), status);
    }

    @Test
    public void testPutProducer() throws Exception {
        final URI uri = create(TestUtils.baseUrl);
        final int status = 201;
        final FcrepoResponse putResponse = new FcrepoResponse(uri, status,
                singletonMap(CONTENT_TYPE, singletonList(TestUtils.TEXT_PLAIN)),
                new ByteArrayInputStream(TestUtils.baseUrl.getBytes()));

        init();

        testExchange.getIn().setHeader(FCREPO_IDENTIFIER, "/foo");
        testExchange.getIn().setHeader(HTTP_METHOD, HttpMethods.PUT);
        testExchange.getIn().setBody(null);

        when(mockPutBuilder.perform()).thenReturn(putResponse);

        testProducer.process(testExchange);

        assertEquals(testExchange.getIn().getBody(String.class), TestUtils.baseUrl);
        assertEquals(testExchange.getIn().getHeader(HTTP_RESPONSE_CODE), status);
        assertEquals(testExchange.getIn().getHeader(CONTENT_TYPE), TestUtils.TEXT_PLAIN);
    }

    @Test
    public void testPreferProperties() throws Exception {
        testProducer = new FcrepoProducer(testEndpoint);

        assertEquals(6, PREFER_PROPERTIES.size());
        final String[] fcrepoPrefer = new String[] { "ServerManaged", "EmbedResources", "InboundReferences" };
        for (final String s : fcrepoPrefer) {
            assertEquals(REPOSITORY + s, PREFER_PROPERTIES.get(s));
        }

        final String[] ldpPrefer = new String[] { "PreferContainment", "PreferMembership",
            "PreferMinimalContainer" };
        for (final String s : ldpPrefer) {
            assertEquals(LDP + s, PREFER_PROPERTIES.get(s));
        }
    }

    @Test
    public void testGetProducerWithScheme() throws Exception {
        final URI uri = create(TestUtils.baseUrl);
        final int status = 200;
        final ByteArrayInputStream body = new ByteArrayInputStream(TestUtils.rdfXml.getBytes());
        final FcrepoResponse headResponse = new FcrepoResponse(uri, 200, emptyMap(), null);
        final FcrepoResponse getResponse = new FcrepoResponse(uri, status,
                singletonMap(CONTENT_TYPE, singletonList(TestUtils.RDF_XML)), body);

        // set the baseUrl with an explicit http:// scheme
        testEndpoint.setBaseUrl("http://localhost:8080/rest");
        init();

        testExchange.getIn().setHeader(FCREPO_IDENTIFIER, "/foo");

        when(mockHeadBuilder.perform()).thenReturn(headResponse);
        when(mockGetBuilder.perform()).thenReturn(getResponse);

        testProducer.process(testExchange);

        assertEquals(testExchange.getIn().getBody(String.class), TestUtils.rdfXml);
        assertEquals(testExchange.getIn().getHeader(CONTENT_TYPE, String.class), TestUtils.RDF_XML);
        assertEquals(testExchange.getIn().getHeader(HTTP_RESPONSE_CODE), status);
    }

    @Test
    public void testGetSecureProducer() throws Exception {
        final URI uri = create(TestUtils.baseUrlSecure);
        final int status = 200;
        final ByteArrayInputStream body = new ByteArrayInputStream(TestUtils.rdfXml.getBytes());
        final FcrepoResponse headResponse = new FcrepoResponse(uri, 200, emptyMap(), null);
        final FcrepoResponse getResponse = new FcrepoResponse(uri, status,
                singletonMap(CONTENT_TYPE, singletonList(TestUtils.RDF_XML)), body);

        // set the baseUrl with no scheme but with a secure port
        testEndpoint.setBaseUrl("localhost:443/rest");
        init();

        testExchange.getIn().setHeader(FCREPO_IDENTIFIER, "/secure");

        when(mockHeadBuilder.perform()).thenReturn(headResponse);
        when(mockGetBuilder.perform()).thenReturn(getResponse);

        testProducer.process(testExchange);

        assertEquals(testExchange.getIn().getBody(String.class), TestUtils.rdfXml);
        assertEquals(testExchange.getIn().getHeader(CONTENT_TYPE, String.class), TestUtils.RDF_XML);
        assertEquals(testExchange.getIn().getHeader(HTTP_RESPONSE_CODE), status);
    }

    @Test
    public void testGetSecureProducerWithScheme() throws Exception {
        final URI uri = create(TestUtils.baseUrlSecureWithoutPort);
        final int status = 200;
        final ByteArrayInputStream body = new ByteArrayInputStream(TestUtils.rdfXml.getBytes());
        final FcrepoResponse headResponse = new FcrepoResponse(uri, 200, emptyMap(), null);
        final FcrepoResponse getResponse = new FcrepoResponse(uri, status,
                singletonMap(CONTENT_TYPE, singletonList(TestUtils.RDF_XML)), body);

        // set the baseUrl with explicit scheme but no port
        testEndpoint.setBaseUrl("https://localhost/rest");
        init();

        testExchange.getIn().setHeader(FCREPO_IDENTIFIER, "/secure");

        when(mockHeadBuilder.perform()).thenReturn(headResponse);
        when(mockGetBuilder.perform()).thenReturn(getResponse);

        testProducer.process(testExchange);

        assertEquals(testExchange.getIn().getBody(String.class), TestUtils.rdfXml);
        assertEquals(testExchange.getIn().getHeader(CONTENT_TYPE, String.class), TestUtils.RDF_XML);
        assertEquals(testExchange.getIn().getHeader(HTTP_RESPONSE_CODE), status);
    }

    @Test
    public void testTransactedGetProducer() throws Exception {
        final String baseUrl = "http://localhost:8080/rest";
        final String path = "/transact";
        final String path2 = "/transact2";
        final String tx = "tx:12345";
        final URI uri = create(baseUrl + "/" + tx + path);
        final URI uri2 = create(baseUrl + "/" + tx + path2);
        final URI commitUri = URI.create(baseUrl + "/" + tx + FcrepoConstants.COMMIT);
        final URI beginUri = URI.create(baseUrl + FcrepoConstants.TRANSACTION);
        final int status = 200;
        final ByteArrayInputStream body = new ByteArrayInputStream(TestUtils.rdfXml.getBytes());
        final ByteArrayInputStream body2 = new ByteArrayInputStream(TestUtils.rdfTriples.getBytes());
        final DefaultUnitOfWork uow = new DefaultUnitOfWork(testExchange);
        final FcrepoTransactionManager txMgr = new FcrepoTransactionManager();
        txMgr.setBaseUrl(baseUrl);

        testEndpoint.setTransactionManager(txMgr);

        when(mockClient2.post(eq(beginUri))).thenReturn(mockPostBuilder2);
        when(mockClient2.post(eq(commitUri))).thenReturn(mockPostBuilder2);

        init();
        TestUtils.setField(txMgr, "fcrepoClient", mockClient2);

        uow.beginTransactedBy((Object)tx);

        testExchange.getIn().setHeader(FCREPO_IDENTIFIER, path);
        testExchange.adapt(ExtendedExchange.class).setUnitOfWork(uow);

        when(mockPostBuilder2.perform()).thenReturn(
                new FcrepoResponse(beginUri, 201, singletonMap("Location", singletonList(baseUrl + "/" + tx)), null));
        when(mockPostBuilder3.perform()).thenReturn(
                new FcrepoResponse(commitUri, 201, emptyMap(), null));

        when(mockHeadBuilder.perform()).thenReturn(new FcrepoResponse(uri, 200, emptyMap(), null));
        when(mockClient.get(eq(uri))).thenReturn(mockGetBuilder2);
        when(mockClient.get(eq(uri2))).thenReturn(mockGetBuilder3);

        when(mockGetBuilder2.perform()).thenReturn(
            new FcrepoResponse(uri, status, singletonMap(CONTENT_TYPE, singletonList(TestUtils.RDF_XML)), body));
        when(mockGetBuilder3.perform()).thenReturn(
            new FcrepoResponse(uri2, status, singletonMap(CONTENT_TYPE, singletonList(N_TRIPLES)), body2));

        testProducer.process(testExchange);

        assertEquals(status, testExchange.getIn().getHeader(HTTP_RESPONSE_CODE));
        assertEquals(TestUtils.RDF_XML, testExchange.getIn().getHeader(CONTENT_TYPE, String.class));
        assertEquals(TestUtils.rdfXml, testExchange.getIn().getBody(String.class));

        testExchange.getIn().setHeader(HTTP_METHOD, "GET");
        testExchange.getIn().setHeader(ACCEPT_CONTENT_TYPE, N_TRIPLES);
        testExchange.getIn().setHeader(FCREPO_IDENTIFIER, path2);
        testExchange.adapt(ExtendedExchange.class).setUnitOfWork(uow);
        testProducer.process(testExchange);
        assertEquals(status, testExchange.getIn().getHeader(HTTP_RESPONSE_CODE));
        assertEquals(N_TRIPLES, testExchange.getIn().getHeader(CONTENT_TYPE, String.class));
        assertEquals(TestUtils.rdfTriples, testExchange.getIn().getBody(String.class));
    }

    @Test (expected = RuntimeException.class)
    public void testTransactedProducerWithError() throws Exception {
        final String baseUrl = "http://localhost:8080/rest";
        final String path = "/transact";
        final String path2 = "/transact2";
        final String tx = "tx:12345";
        final URI uri = create(baseUrl + "/" + tx + path);
        final URI uri2 = create(baseUrl + "/" + tx + path2);
        final URI commitUri = URI.create(baseUrl + "/" + tx + FcrepoConstants.COMMIT);
        final URI beginUri = URI.create(baseUrl + FcrepoConstants.TRANSACTION);
        final int status = 200;
        final ByteArrayInputStream body = new ByteArrayInputStream(TestUtils.rdfXml.getBytes());
        final ByteArrayInputStream body2 = new ByteArrayInputStream(TestUtils.rdfTriples.getBytes());
        final DefaultUnitOfWork uow = new DefaultUnitOfWork(testExchange);
        final FcrepoTransactionManager txMgr = new FcrepoTransactionManager();
        txMgr.setBaseUrl(baseUrl);

        testEndpoint.setTransactionManager(txMgr);

        init();
        TestUtils.setField(txMgr, "fcrepoClient", mockClient2);

        uow.beginTransactedBy((Object)tx);

        testExchange.getIn().setHeader(FCREPO_IDENTIFIER, path);
        testExchange.adapt(ExtendedExchange.class).setUnitOfWork(uow);

        when(mockClient2.post(eq(beginUri))).thenReturn(mockPostBuilder2);
        when(mockClient2.post(eq(commitUri))).thenReturn(mockPostBuilder3);
        when(mockPostBuilder2.perform()).thenReturn(
                new FcrepoResponse(beginUri, 201, singletonMap("Location", singletonList(baseUrl + "/" + tx)), null));
        when(mockPostBuilder3.perform()).thenReturn(
                new FcrepoResponse(commitUri, 201, emptyMap(), null));

        when(mockHeadBuilder.perform()).thenReturn(new FcrepoResponse(uri, 200, emptyMap(), null));
        when(mockClient.get(eq(uri))).thenReturn(mockGetBuilder2);
        when(mockClient.get(eq(uri2))).thenReturn(mockGetBuilder3);
        when(mockGetBuilder2.perform()).thenReturn(
            new FcrepoResponse(uri, status, singletonMap(CONTENT_TYPE, singletonList(TestUtils.RDF_XML)), body));
        when(mockGetBuilder3.perform()).thenThrow(
            new FcrepoOperationFailedException(uri2, 400, "Bad Request"));

        testProducer.process(testExchange);

        assertEquals(status, testExchange.getIn().getHeader(HTTP_RESPONSE_CODE));
        assertEquals(TestUtils.RDF_XML, testExchange.getIn().getHeader(CONTENT_TYPE, String.class));
        assertEquals(TestUtils.rdfXml, testExchange.getIn().getBody(String.class));

        testExchange.getIn().setHeader(HTTP_METHOD, "GET");
        testExchange.getIn().setHeader(ACCEPT_CONTENT_TYPE, N_TRIPLES);
        testExchange.getIn().setHeader(FCREPO_IDENTIFIER, path2);
        testExchange.adapt(ExtendedExchange.class).setUnitOfWork(uow);
        testProducer.process(testExchange);
    }

    @Test
    public void testNoStreamCaching() throws Exception {
        final URI uri = create(TestUtils.baseUrl);
        final int status = 200;
        final ByteArrayInputStream body = new ByteArrayInputStream(TestUtils.rdfXml.getBytes());
        final FcrepoResponse headResponse = new FcrepoResponse(uri, 200, emptyMap(), null);
        final FcrepoResponse getResponse = new FcrepoResponse(uri, status,
                singletonMap(CONTENT_TYPE, singletonList(TestUtils.RDF_XML)), body);

        init();

        testExchange.getIn().setHeader(FCREPO_IDENTIFIER, "/foo");
        testExchange.setProperty(DISABLE_HTTP_STREAM_CACHE, true);

        when(mockHeadBuilder.perform()).thenReturn(headResponse);
        when(mockGetBuilder.perform()).thenReturn(getResponse);

        testProducer.process(testExchange);

        assertEquals(testExchange.getIn().getBody(String.class), TestUtils.rdfXml);
        assertEquals(testExchange.getIn().getHeader(CONTENT_TYPE, String.class), TestUtils.RDF_XML);
        assertEquals(testExchange.getIn().getHeader(HTTP_RESPONSE_CODE), status);
    }

    @Test
    public void testStreamCaching() throws Exception {
        final URI uri = create(TestUtils.baseUrl);
        final int status = 200;
        final String rdfConcat = StringUtils.repeat(TestUtils.rdfXml, 10000);
        final ByteArrayInputStream body = new ByteArrayInputStream(rdfConcat.getBytes());
        final FcrepoResponse headResponse = new FcrepoResponse(uri, 200, emptyMap(), null);
        final FcrepoResponse getResponse = new FcrepoResponse(uri, status,
                singletonMap(CONTENT_TYPE, singletonList(TestUtils.RDF_XML)), body);

        init();

        testExchange.getIn().setHeader(FCREPO_IDENTIFIER, "/foo");
        testExchange.setProperty(DISABLE_HTTP_STREAM_CACHE, false);

        testExchange.getContext().getStreamCachingStrategy().setSpoolThreshold(1024);
        testExchange.getContext().getStreamCachingStrategy().setBufferSize(256);
        testExchange.getContext().setStreamCaching(true);

        when(mockHeadBuilder.perform()).thenReturn(headResponse);
        when(mockGetBuilder.perform()).thenReturn(getResponse);

        testProducer.process(testExchange);

        assertEquals(true, testExchange.getContext().isStreamCaching());
        assertNotNull(testExchange.getIn().getBody(InputStreamCache.class));
        assertEquals(rdfConcat.length(), testExchange.getIn().getBody(InputStreamCache.class).length());
        assertEquals(rdfConcat.length(), testExchange.getIn().getBody(InputStreamCache.class).length());
        assertEquals(testExchange.getIn().getHeader(CONTENT_TYPE, String.class), TestUtils.RDF_XML);
        assertEquals(testExchange.getIn().getHeader(HTTP_RESPONSE_CODE), status);
    }
}
