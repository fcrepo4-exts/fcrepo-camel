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

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.net.URI;
import java.util.List;

import org.fcrepo.client.FcrepoClient;
import org.fcrepo.client.FcrepoOperationFailedException;
import org.fcrepo.client.FcrepoResponse;
import org.fcrepo.client.GetBuilder;
import org.fcrepo.client.HeadBuilder;
import org.fcrepo.client.PostBuilder;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * @author Aaron Coburn
 */
@RunWith(MockitoJUnitRunner.class)
public class FcrepoTransactionManagerTest {

    @Mock
    private FcrepoClient mockClient;

    @Mock
    private PostBuilder mockPostBuilder, mockPostBuilder2;

    @Mock
    private GetBuilder mockGetBuilder;

    @Mock
    private HeadBuilder mockHeadBuilder;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() throws Exception {
        when(mockClient.head(any(URI.class))).thenReturn(mockHeadBuilder);
        when(mockClient.get(any(URI.class))).thenReturn(mockGetBuilder);
        when(mockGetBuilder.accept(any(String.class))).thenReturn(mockGetBuilder);
        when(mockGetBuilder.preferRepresentation(any(List.class), any(List.class))).thenReturn(mockGetBuilder);
        when(mockClient.post(any(URI.class))).thenReturn(mockPostBuilder);
        when(mockPostBuilder.body(any(InputStream.class), any(String.class))).thenReturn(mockPostBuilder);
        when(mockPostBuilder2.body(any(InputStream.class), any(String.class))).thenReturn(mockPostBuilder2);
    }

    @Test
    public void testProperties() {
        final FcrepoTransactionManager txMgr = new FcrepoTransactionManager();
        final String baseUrl = "http://localhost:8080/rest";
        final String authUsername = "foo";
        final String authPassword = "bar";
        final String authHost = "baz";
        final String transactionId = "1234567890";

        assertNull(txMgr.getAuthUsername());
        assertNull(txMgr.getAuthPassword());
        assertNull(txMgr.getAuthHost());
        assertNull(txMgr.getBaseUrl());

        txMgr.setBaseUrl(baseUrl);
        txMgr.setAuthUsername(authUsername);
        txMgr.setAuthPassword(authPassword);
        txMgr.setAuthHost(authHost);

        assertEquals(baseUrl, txMgr.getBaseUrl());
        assertEquals(authUsername, txMgr.getAuthUsername());
        assertEquals(authPassword, txMgr.getAuthPassword());
        assertEquals(authHost, txMgr.getAuthHost());
    }

    @Test
    public void testTransactionCommit() throws FcrepoOperationFailedException {
        final String baseUrl = "http://localhost:8080/rest";
        final String tx = "tx:1234567890";
        final URI commitUri = URI.create(baseUrl + "/" + tx + FcrepoConstants.COMMIT);
        final URI beginUri = URI.create(baseUrl + FcrepoConstants.TRANSACTION);
        final FcrepoTransactionManager txMgr = new FcrepoTransactionManager();
        txMgr.setBaseUrl(baseUrl);
        TestUtils.setField(txMgr, "fcrepoClient", mockClient);

        final TransactionTemplate transactionTemplate = new TransactionTemplate(txMgr);
        final DefaultTransactionDefinition txDef = new DefaultTransactionDefinition(
                TransactionDefinition.PROPAGATION_REQUIRED);

        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        transactionTemplate.afterPropertiesSet();

        when(mockClient.post(eq(beginUri))).thenReturn(mockPostBuilder);
        when(mockClient.post(eq(commitUri))).thenReturn(mockPostBuilder2);
        when(mockPostBuilder.perform()).thenReturn(
                new FcrepoResponse(beginUri, 201, singletonMap("Location", singletonList(baseUrl + "/" + tx)), null));
        when(mockPostBuilder2.perform()).thenReturn(
                new FcrepoResponse(commitUri, 201, emptyMap(), null));

        DefaultTransactionStatus status = (DefaultTransactionStatus)txMgr.getTransaction(txDef);
        FcrepoTransactionObject txObj = (FcrepoTransactionObject)status.getTransaction();

        assertEquals(tx, txObj.getSessionId());
        assertFalse(status.isCompleted());

        status = (DefaultTransactionStatus)txMgr.getTransaction(txDef);

        txMgr.commit(status);

        txObj = (FcrepoTransactionObject)status.getTransaction();

        assertNull(txObj.getSessionId());
        assertTrue(status.isCompleted());
    }

    @Test
    public void testTransactionRollback() throws FcrepoOperationFailedException {
        final String baseUrl = "http://localhost:8080/rest";
        final String tx = "tx:1234567890";
        final URI commitUri = URI.create(baseUrl + "/" + tx + FcrepoConstants.COMMIT);
        final URI beginUri = URI.create(baseUrl + FcrepoConstants.TRANSACTION);
        final FcrepoTransactionManager txMgr = new FcrepoTransactionManager();
        txMgr.setBaseUrl(baseUrl);
        TestUtils.setField(txMgr, "fcrepoClient", mockClient);

        final TransactionTemplate transactionTemplate = new TransactionTemplate(txMgr);
        final DefaultTransactionDefinition txDef = new DefaultTransactionDefinition(
                TransactionDefinition.PROPAGATION_REQUIRED);

        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        transactionTemplate.afterPropertiesSet();

        when(mockClient.post(eq(beginUri))).thenReturn(mockPostBuilder);
        when(mockClient.post(eq(commitUri))).thenReturn(mockPostBuilder2);
        when(mockPostBuilder.perform()).thenReturn(
                new FcrepoResponse(beginUri, 201, singletonMap("Location", singletonList(baseUrl + "/" + tx)), null));
        when(mockPostBuilder2.perform()).thenReturn(
                new FcrepoResponse(commitUri, 201, emptyMap(), null));

        DefaultTransactionStatus status = (DefaultTransactionStatus)txMgr.getTransaction(txDef);
        FcrepoTransactionObject txObj = (FcrepoTransactionObject)status.getTransaction();

        assertEquals(tx, txObj.getSessionId());
        assertFalse(status.isCompleted());

        status = (DefaultTransactionStatus)txMgr.getTransaction(txDef);

        txMgr.rollback(status);

        txObj = (FcrepoTransactionObject)status.getTransaction();

        assertNull(txObj.getSessionId());
        assertTrue(status.isCompleted());
    }

    @Test (expected = CannotCreateTransactionException.class)
    public void testTransactionBeginError() throws FcrepoOperationFailedException {
        final String baseUrl = "http://localhost:8080/rest";
        final String tx = "tx:1234567890";
        final URI beginUri = URI.create(baseUrl + FcrepoConstants.TRANSACTION);
        final FcrepoTransactionManager txMgr = new FcrepoTransactionManager();
        txMgr.setBaseUrl(baseUrl);
        TestUtils.setField(txMgr, "fcrepoClient", mockClient);

        final TransactionTemplate transactionTemplate = new TransactionTemplate(txMgr);
        final DefaultTransactionDefinition txDef = new DefaultTransactionDefinition(
                TransactionDefinition.PROPAGATION_REQUIRED);

        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        transactionTemplate.afterPropertiesSet();

        when(mockPostBuilder.perform()).thenThrow(
                new FcrepoOperationFailedException(beginUri, 400, "Bad Request"));

        txMgr.getTransaction(txDef);
    }

    @Test (expected = CannotCreateTransactionException.class)
    public void testTransactionBeginNoLocationError() throws FcrepoOperationFailedException {
        final String baseUrl = "http://localhost:8080/rest";
        final String tx = "tx:1234567890";
        final URI beginUri = URI.create(baseUrl + FcrepoConstants.TRANSACTION);
        final FcrepoTransactionManager txMgr = new FcrepoTransactionManager();
        txMgr.setBaseUrl(baseUrl);
        TestUtils.setField(txMgr, "fcrepoClient", mockClient);

        final TransactionTemplate transactionTemplate = new TransactionTemplate(txMgr);
        final DefaultTransactionDefinition txDef = new DefaultTransactionDefinition(
                TransactionDefinition.PROPAGATION_REQUIRED);

        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        transactionTemplate.afterPropertiesSet();

        when(mockPostBuilder.perform()).thenReturn(
                new FcrepoResponse(beginUri, 201, emptyMap(), null));

        txMgr.getTransaction(txDef);
    }

    @Test (expected = CannotCreateTransactionException.class)
    public void testTransactionNullResponseError() throws FcrepoOperationFailedException {
        final String baseUrl = "http://localhost:8080/rest";
        final String tx = "tx:1234567890";
        final URI beginUri = URI.create(baseUrl + FcrepoConstants.TRANSACTION);
        final FcrepoTransactionManager txMgr = new FcrepoTransactionManager();
        txMgr.setBaseUrl(baseUrl);
        TestUtils.setField(txMgr, "fcrepoClient", mockClient);

        final TransactionTemplate transactionTemplate = new TransactionTemplate(txMgr);
        final DefaultTransactionDefinition txDef = new DefaultTransactionDefinition(
                TransactionDefinition.PROPAGATION_REQUIRED);

        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        transactionTemplate.afterPropertiesSet();

        when(mockPostBuilder.perform()).thenReturn(null);

        txMgr.getTransaction(txDef);
    }

    @Test (expected = TransactionSystemException.class)
    public void testTransactionCommitError() throws FcrepoOperationFailedException {
        final String baseUrl = "http://localhost:8080/rest";
        final String tx = "tx:1234567890";
        final URI commitUri = URI.create(baseUrl + "/" + tx + FcrepoConstants.COMMIT);
        final URI beginUri = URI.create(baseUrl + FcrepoConstants.TRANSACTION);
        final FcrepoTransactionManager txMgr = new FcrepoTransactionManager();
        txMgr.setBaseUrl(baseUrl);
        TestUtils.setField(txMgr, "fcrepoClient", mockClient);

        final TransactionTemplate transactionTemplate = new TransactionTemplate(txMgr);
        final DefaultTransactionDefinition txDef = new DefaultTransactionDefinition(
                TransactionDefinition.PROPAGATION_REQUIRED);

        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        transactionTemplate.afterPropertiesSet();

        when(mockClient.post(eq(beginUri))).thenReturn(mockPostBuilder);
        when(mockClient.post(eq(commitUri))).thenReturn(mockPostBuilder2);
        when(mockPostBuilder.perform()).thenReturn(
                new FcrepoResponse(beginUri, 201, singletonMap("Location", singletonList(baseUrl + "/" + tx)), null));
        when(mockPostBuilder2.perform()).thenThrow(
                new FcrepoOperationFailedException(commitUri, 400, "Bad Request"));

        DefaultTransactionStatus status = (DefaultTransactionStatus)txMgr.getTransaction(txDef);

        final FcrepoTransactionObject txObj = (FcrepoTransactionObject)status.getTransaction();
        assertEquals(tx, txObj.getSessionId());
        assertFalse(status.isCompleted());

        status = (DefaultTransactionStatus)txMgr.getTransaction(txDef);
        txMgr.commit(status);
    }

    @Test (expected = TransactionSystemException.class)
    public void testTransactionRollbackError() throws FcrepoOperationFailedException {
        final String baseUrl = "http://localhost:8080/rest";
        final String tx = "tx:1234567890";
        final URI rollbackUri = URI.create(baseUrl + "/" + tx + FcrepoConstants.ROLLBACK);
        final URI beginUri = URI.create(baseUrl + FcrepoConstants.TRANSACTION);
        final FcrepoTransactionManager txMgr = new FcrepoTransactionManager();
        txMgr.setBaseUrl(baseUrl);
        TestUtils.setField(txMgr, "fcrepoClient", mockClient);

        final TransactionTemplate transactionTemplate = new TransactionTemplate(txMgr);
        final DefaultTransactionDefinition txDef = new DefaultTransactionDefinition(
                TransactionDefinition.PROPAGATION_REQUIRED);

        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        transactionTemplate.afterPropertiesSet();

        when(mockClient.post(eq(beginUri))).thenReturn(mockPostBuilder);
        when(mockClient.post(eq(rollbackUri))).thenReturn(mockPostBuilder2);
        when(mockPostBuilder.perform()).thenReturn(
                new FcrepoResponse(beginUri, 201, singletonMap("Location", singletonList(baseUrl + "/" + tx)), null));
        when(mockPostBuilder2.perform()).thenThrow(
                new FcrepoOperationFailedException(rollbackUri, 400, "Bad Request"));

        DefaultTransactionStatus status = (DefaultTransactionStatus)txMgr.getTransaction(txDef);

        final FcrepoTransactionObject txObj = (FcrepoTransactionObject)status.getTransaction();
        assertEquals(tx, txObj.getSessionId());
        assertFalse(status.isCompleted());

        status = (DefaultTransactionStatus)txMgr.getTransaction(txDef);
        txMgr.rollback(status);
    }
}
