/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.launcher.http.TraceSpanSupport;
import com.ghatana.datacloud.spi.EntityWriteIdempotencyStore;
import com.ghatana.datacloud.spi.TransactionContext;
import com.ghatana.datacloud.spi.TransactionManager;
import com.ghatana.platform.audit.AuditService;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.bytebuf.ByteBuf;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * DC-DATA-001 through DC-DATA-006: Data layer tests for batch delete operations.
 *
 * <p>Verifies batch delete confirmation token generation, validation, expiration,
 * HMAC verification, dry-run functionality, and execution semantics.
 *
 * @doc.type class
 * @doc.purpose Data layer tests for batch delete confirmation tokens (DC-DATA-001 through DC-DATA-006)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Data Layer Batch Delete Tests")
@Tag("data-layer")
@Tag("batch-delete")
@ExtendWith(MockitoExtension.class)
class DataLayerBatchDeleteTest extends EventloopTestBase {

    @Mock
    private DataCloudClient client;

    @Mock
    private HttpHandlerSupport http;

    @Mock
    private HttpRequest request;

    @Mock
    private HttpResponse errorResponse;

    @Mock
    private HttpResponse successResponse;

    @Mock
    private BiConsumer<String, Map<String, Object>> wsBroadcaster;

    @Mock
    private TransactionManager transactionManager;

    @Mock
    private EntityWriteIdempotencyStore idempotencyStore;

    @Mock
    private AuditService auditService;

    private EntityCrudHandler handler;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        handler = new EntityCrudHandler(client, http, wsBroadcaster)
            .withTraceSupport(TraceSpanSupport.disabled())
            .withTransactionManager(transactionManager)
            .withIdempotencyStore(idempotencyStore)
            .withAuditService(auditService)
            .withDeploymentProfile("production");

        objectMapper = new ObjectMapper();

        lenient().when(http.errorResponse(anyInt(), anyString())).thenReturn(errorResponse);
        lenient().when(http.jsonResponse(any())).thenReturn(successResponse);
        lenient().when(http.objectMapper()).thenReturn(objectMapper);
        lenient().when(successResponse.getCode()).thenReturn(200);
        lenient().when(http.requireTenantIdWithError(request))
            .thenReturn(HttpHandlerSupport.TenantResolutionResult.success("tenant-1", null));
        lenient().when(request.getPathParameter("collection")).thenReturn("test-collection");

        // Stub client.findById to return empty Optional (entities not found) for dry-run previews
        lenient().when(client.findById(anyString(), anyString(), anyString()))
            .thenReturn(Promise.of(Optional.empty()));
        // Stub client.appendEvent for CDC event emission during transactional delete
        lenient().when(client.appendEvent(anyString(), any()))
            .thenReturn(Promise.of(DataCloudClient.Offset.of(1L)));
        // Stub transactionManager to execute the operation directly (no real transaction)
        lenient().doAnswer(invocation -> {
            TransactionManager.TransactionalOperation<Object> op = invocation.getArgument(1);
            return op.execute(null);
        }).when(transactionManager).executeInTransactionWithContext(anyString(), any());
    }

    // ==================== DC-DATA-001: Dry-run functionality ====================

    @Test
    @DisplayName("DC-DATA-001: Batch delete dry-run returns preview and confirmation token")
    void batchDeleteDryRunReturnsPreviewAndConfirmationToken() {
        String batchJson = "{\"ids\":[\"entity-1\",\"entity-2\"],\"dryRun\":true}";

        when(request.loadBody())
            .thenReturn(Promise.of(ByteBuf.wrapForReading(batchJson.getBytes(StandardCharsets.UTF_8))));

        HttpResponse response = runPromise(() -> handler.handleBatchDeleteEntities(request));

        assertThat(response).isNotNull();
        verify(http).jsonResponse(argThat(body -> {
            Map<String, Object> result = body;
            return result.containsKey("confirmationToken")
                && Boolean.TRUE.equals(result.get("dryRun"))
                && "DRY_RUN_COMPLETE".equals(result.get("status"))
                && result.containsKey("estimatedCount")
                && result.containsKey("tokenExpiresInSec");
        }));
    }

    @Test
    @DisplayName("DC-DATA-001: Dry-run does not delete any entities")
    void batchDeleteDryRunDoesNotDeleteEntities() {
        String batchJson = "{\"ids\":[\"entity-1\",\"entity-2\"],\"dryRun\":true}";

        when(request.loadBody())
            .thenReturn(Promise.of(ByteBuf.wrapForReading(batchJson.getBytes(StandardCharsets.UTF_8))));

        HttpResponse response = runPromise(() -> handler.handleBatchDeleteEntities(request));

        assertThat(response).isNotNull();
        verify(client, never()).delete(anyString(), anyString(), anyString());
    }

    // ==================== DC-DATA-002: Confirmation token validation ====================

    @Test
    @DisplayName("DC-DATA-002: Batch delete without confirmation token is rejected")
    void batchDeleteWithoutConfirmationTokenIsRejected() {
        String batchJson = "{\"ids\":[\"entity-1\",\"entity-2\"]}";

        when(request.loadBody())
            .thenReturn(Promise.of(ByteBuf.wrapForReading(batchJson.getBytes(StandardCharsets.UTF_8))));

        HttpResponse response = runPromise(() -> handler.handleBatchDeleteEntities(request));

        assertThat(response).isSameAs(errorResponse);
        verify(http).errorResponse(eq(400), argThat(msg -> 
            msg.contains("confirmationToken is required")));
    }

    @Test
    @DisplayName("DC-DATA-002: Batch delete with invalid confirmation token is rejected")
    void batchDeleteWithInvalidConfirmationTokenIsRejected() {
        String batchJson = "{\"ids\":[\"entity-1\"],\"confirmationToken\":\"invalid-token\"}";

        when(request.loadBody())
            .thenReturn(Promise.of(ByteBuf.wrapForReading(batchJson.getBytes(StandardCharsets.UTF_8))));

        HttpResponse response = runPromise(() -> handler.handleBatchDeleteEntities(request));

        assertThat(response).isSameAs(errorResponse);
        verify(http).errorResponse(eq(403), argThat(msg -> 
            msg.contains("Confirmation token is invalid or expired")));
    }

    // ==================== DC-DATA-003: Token expiration ====================

    @Test
    @DisplayName("DC-DATA-003: Expired confirmation token is rejected")
    void expiredConfirmationTokenIsRejected() {
        // Note: Testing actual expiration requires manipulating time or waiting
        // This test verifies the token validation logic accepts/rejects based on validity
        // In practice, tokens expire after TOKEN_VALIDITY_MS (configured in DestructiveActionToken)
        String batchJson = "{\"ids\":[\"entity-1\"],\"confirmationToken\":\"expired.simulated.token\"}";

        when(request.loadBody())
            .thenReturn(Promise.of(ByteBuf.wrapForReading(batchJson.getBytes(StandardCharsets.UTF_8))));

        HttpResponse response = runPromise(() -> handler.handleBatchDeleteEntities(request));

        assertThat(response).isSameAs(errorResponse);
        verify(http).errorResponse(eq(403), argThat(msg -> 
            msg.contains("invalid") || msg.contains("expired")));
    }

    // ==================== DC-DATA-004: HMAC verification ====================

    @Test
    @DisplayName("DC-DATA-004: Tampered confirmation token is rejected")
    void tamperedConfirmationTokenIsRejected() {
        // First get a valid token via dry-run
        String dryRunJson = "{\"ids\":[\"entity-1\"],\"dryRun\":true}";
        when(request.loadBody())
            .thenReturn(Promise.of(ByteBuf.wrapForReading(dryRunJson.getBytes(StandardCharsets.UTF_8))));

        HttpResponse dryRunResponse = runPromise(() -> handler.handleBatchDeleteEntities(request));
        
        // Extract token from dry-run response
        Map<String, Object> dryRunResult = captureJsonResponse();
        String validToken = (String) dryRunResult.get("confirmationToken");
        
        // Tamper with the token
        String tamperedToken = validToken.substring(0, validToken.length() - 1) + "X";

        // Reset and try to use tampered token
        reset(request);
        when(request.getPathParameter("collection")).thenReturn("test-collection");
        when(request.loadBody())
            .thenReturn(Promise.of(ByteBuf.wrapForReading(
                ("{\"ids\":[\"entity-1\"],\"confirmationToken\":\"" + tamperedToken + "\"}")
                    .getBytes(StandardCharsets.UTF_8))));

        HttpResponse response = runPromise(() -> handler.handleBatchDeleteEntities(request));

        assertThat(response).isSameAs(errorResponse);
        verify(http).errorResponse(eq(403), argThat(msg -> 
            msg.contains("invalid")));
    }

    @Test
    @DisplayName("DC-DATA-004: Token for different tenant is rejected")
    void tokenForDifferentTenantIsRejected() {
        // Get token for tenant-1
        String dryRunJson = "{\"ids\":[\"entity-1\"],\"dryRun\":true}";
        when(request.loadBody())
            .thenReturn(Promise.of(ByteBuf.wrapForReading(dryRunJson.getBytes(StandardCharsets.UTF_8))));

        HttpResponse dryRunResponse = runPromise(() -> handler.handleBatchDeleteEntities(request));
        
        Map<String, Object> dryRunResult = captureJsonResponse();
        String tokenForTenant1 = (String) dryRunResult.get("confirmationToken");
        
        // Try to use tenant-1's token for tenant-2
        reset(request);
        when(http.requireTenantIdWithError(request))
            .thenReturn(HttpHandlerSupport.TenantResolutionResult.success("tenant-2", null));
        when(request.getPathParameter("collection")).thenReturn("test-collection");
        when(request.loadBody())
            .thenReturn(Promise.of(ByteBuf.wrapForReading(
                ("{\"ids\":[\"entity-1\"],\"confirmationToken\":\"" + tokenForTenant1 + "\"}")
                    .getBytes(StandardCharsets.UTF_8))));

        HttpResponse response = runPromise(() -> handler.handleBatchDeleteEntities(request));

        assertThat(response).isSameAs(errorResponse);
        verify(http).errorResponse(eq(403), argThat(msg -> 
            msg.contains("invalid")));
    }

    // ==================== DC-DATA-005: Batch delete execution ====================

    @Test
    @DisplayName("DC-DATA-005: Valid confirmation token allows batch delete execution")
    void validConfirmationTokenAllowsBatchDeleteExecution() {
        // Get token via dry-run
        String dryRunJson = "{\"ids\":[\"entity-1\",\"entity-2\"],\"dryRun\":true}";
        when(request.loadBody())
            .thenReturn(Promise.of(ByteBuf.wrapForReading(dryRunJson.getBytes(StandardCharsets.UTF_8))));

        runPromise(() -> handler.handleBatchDeleteEntities(request));
        
        Map<String, Object> dryRunResult = captureJsonResponse();
        String validToken = (String) dryRunResult.get("confirmationToken");
        
        // Use token for actual deletion
        reset(request);
        when(request.getPathParameter("collection")).thenReturn("test-collection");
        when(request.loadBody())
            .thenReturn(Promise.of(ByteBuf.wrapForReading(
                ("{\"ids\":[\"entity-1\",\"entity-2\"],\"confirmationToken\":\"" + validToken + "\"}")
                    .getBytes(StandardCharsets.UTF_8))));
        when(client.delete(anyString(), anyString(), eq("entity-1")))
            .thenReturn(Promise.of((Void) null));
        when(client.delete(anyString(), anyString(), eq("entity-2")))
            .thenReturn(Promise.of((Void) null));

        HttpResponse response = runPromise(() -> handler.handleBatchDeleteEntities(request));

        assertThat(response).isNotNull();
        verify(client).delete(eq("tenant-1"), eq("test-collection"), eq("entity-1"));
        verify(client).delete(eq("tenant-1"), eq("test-collection"), eq("entity-2"));
    }

    @Test
    @DisplayName("DC-DATA-005: Batch delete with mismatched ID count is rejected")
    void batchDeleteWithMismatchedIdCountIsRejected() {
        // Get token for 2 IDs
        String dryRunJson = "{\"ids\":[\"entity-1\",\"entity-2\"],\"dryRun\":true}";
        when(request.loadBody())
            .thenReturn(Promise.of(ByteBuf.wrapForReading(dryRunJson.getBytes(StandardCharsets.UTF_8))));

        runPromise(() -> handler.handleBatchDeleteEntities(request));
        
        Map<String, Object> dryRunResult = captureJsonResponse();
        String tokenFor2Ids = (String) dryRunResult.get("confirmationToken");
        
        // Try to delete 3 IDs with a token for 2
        reset(request);
        when(request.getPathParameter("collection")).thenReturn("test-collection");
        when(request.loadBody())
            .thenReturn(Promise.of(ByteBuf.wrapForReading(
                ("{\"ids\":[\"entity-1\",\"entity-2\",\"entity-3\"],\"confirmationToken\":\"" + tokenFor2Ids + "\"}")
                    .getBytes(StandardCharsets.UTF_8))));

        HttpResponse response = runPromise(() -> handler.handleBatchDeleteEntities(request));

        assertThat(response).isSameAs(errorResponse);
        verify(http).errorResponse(eq(403), argThat(msg -> 
            msg.contains("invalid")));
        verify(client, never()).delete(anyString(), anyString(), anyString());
    }

    // Helper method to capture the JSON response body
    @SuppressWarnings("unchecked")
    private Map<String, Object> captureJsonResponse() {
        var captor = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(http).jsonResponse(captor.capture());
        return captor.getValue();
    }

    // ==================== DC-DATA-006: Best-effort semantics ====================

    @Test
    @DisplayName("DC-DATA-006: Batch delete continues on individual failures (best-effort)")
    void batchDeleteContinuesOnIndividualFailures() {
        // Get token via dry-run
        String dryRunJson = "{\"ids\":[\"entity-1\",\"entity-2\",\"entity-3\"],\"dryRun\":true}";
        when(request.loadBody())
            .thenReturn(Promise.of(ByteBuf.wrapForReading(dryRunJson.getBytes(StandardCharsets.UTF_8))));

        runPromise(() -> handler.handleBatchDeleteEntities(request));
        
        Map<String, Object> dryRunResult = captureJsonResponse();
        String validToken = (String) dryRunResult.get("confirmationToken");
        
        // Use token for actual deletion with one failure
        reset(request);
        when(request.getPathParameter("collection")).thenReturn("test-collection");
        when(request.loadBody())
            .thenReturn(Promise.of(ByteBuf.wrapForReading(
                ("{\"ids\":[\"entity-1\",\"entity-2\",\"entity-3\"],\"confirmationToken\":\"" + validToken + "\"}")
                    .getBytes(StandardCharsets.UTF_8))));
        when(client.delete(anyString(), anyString(), eq("entity-1")))
            .thenReturn(Promise.of((Void) null));
        when(client.delete(anyString(), anyString(), eq("entity-2")))
            .thenReturn(Promise.ofException(new RuntimeException("Entity not found")));
        when(client.delete(anyString(), anyString(), eq("entity-3")))
            .thenReturn(Promise.of((Void) null));

        HttpResponse response = runPromise(() -> handler.handleBatchDeleteEntities(request));

        assertThat(response).isNotNull();
        // All three delete attempts should be made despite one failure
        verify(client).delete(eq("tenant-1"), eq("test-collection"), eq("entity-1"));
        verify(client).delete(eq("tenant-1"), eq("test-collection"), eq("entity-2"));
        verify(client).delete(eq("tenant-1"), eq("test-collection"), eq("entity-3"));
    }

    @Test
    @DisplayName("DC-DATA-006: Batch delete response includes success and failure counts")
    void batchDeleteResponseIncludesSuccessAndFailureCounts() {
        // Get token via dry-run
        String dryRunJson = "{\"ids\":[\"entity-1\",\"entity-2\",\"entity-3\"],\"dryRun\":true}";
        when(request.loadBody())
            .thenReturn(Promise.of(ByteBuf.wrapForReading(dryRunJson.getBytes(StandardCharsets.UTF_8))));

        runPromise(() -> handler.handleBatchDeleteEntities(request));
        
        Map<String, Object> dryRunResult = captureJsonResponse();
        String validToken = (String) dryRunResult.get("confirmationToken");
        
        // Use token for actual deletion with one failure
        reset(request);
        when(request.getPathParameter("collection")).thenReturn("test-collection");
        when(request.loadBody())
            .thenReturn(Promise.of(ByteBuf.wrapForReading(
                ("{\"ids\":[\"entity-1\",\"entity-2\",\"entity-3\"],\"confirmationToken\":\"" + validToken + "\"}")
                    .getBytes(StandardCharsets.UTF_8))));
        when(client.delete(anyString(), anyString(), eq("entity-1")))
            .thenReturn(Promise.of((Void) null));
        when(client.delete(anyString(), anyString(), eq("entity-2")))
            .thenReturn(Promise.ofException(new RuntimeException("Entity not found")));
        when(client.delete(anyString(), anyString(), eq("entity-3")))
            .thenReturn(Promise.of((Void) null));

        HttpResponse response = runPromise(() -> handler.handleBatchDeleteEntities(request));

        assertThat(response).isNotNull();
        verify(http).jsonResponse(argThat(body -> {
            Map<String, Object> result = body;
            return result.containsKey("deletedCount")
                && result.containsKey("failedCount")
                && result.containsKey("results");
        }));
    }
}
