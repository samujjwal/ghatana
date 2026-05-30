package com.ghatana.datacloud.launcher.http.handlers;
import com.ghatana.datacloud.launcher.http.handlers.HttpHandlerSupport;
import com.ghatana.datacloud.launcher.http.handlers.HttpHandlerSupport.TenantResolutionResult;

import com.ghatana.datacloud.launcher.learning.DataCloudLearningBridge;
import com.ghatana.platform.observability.idempotency.IdempotencyStore;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Regression tests for LearningHandler tenant enforcement
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("LearningHandler")
@ExtendWith(MockitoExtension.class) 
class LearningHandlerTest extends EventloopTestBase {

    @Mock
    private DataCloudLearningBridge learningBridge;

    @Mock
    private HttpHandlerSupport http;

    @Mock
    private HttpRequest request;

    @Mock
    private HttpResponse errorResponse;

    @Mock
    private IdempotencyStore idempotencyStore;

    private LearningHandler handler;

    @BeforeEach
    void setUp() { 
        handler = new LearningHandler(learningBridge, http).withIdempotencyStore(idempotencyStore); 
        lenient().when(http.errorResponse(anyInt(), anyString())).thenReturn(errorResponse); 
    }

    @Test
    @DisplayName("learning trigger rejects missing tenant before bridge execution")
    void learningTriggerRejectsMissingTenant() { 
        when(http.requireTenantIdWithError(request)).thenReturn(TenantResolutionResult.error(401, "Unauthorized")); 

        HttpResponse response = runPromise(() -> handler.handleLearningTrigger(request)); 

        assertThat(response).isSameAs(errorResponse); 
        verify(learningBridge, never()).runLearning("default", true); 
    }

    @Test
    @DisplayName("learning review approve returns 409 when item already finalized")
    void learningReviewApproveReturns409WhenFinalized() {
        when(http.requireTenantIdWithError(request)).thenReturn(TenantResolutionResult.success("tenant-1", null));
        when(http.resolvePrincipalId(request)).thenReturn("user-1");
        when(request.getPathParameter("reviewId")).thenReturn("review-1");
        when(learningBridge.approveReview("tenant-1", "user-1", "review-1")).thenReturn(false);
        when(learningBridge.getReviewQueue()).thenReturn(java.util.Map.of("review-1", java.util.Map.of("status", "APPROVED")));
        when(http.errorResponse(409, "Review item already finalized: review-1")).thenReturn(errorResponse);

        HttpResponse response = runPromise(() -> handler.handleLearningReviewApprove(request));

        assertThat(response).isSameAs(errorResponse);
    }

    @Test
    @DisplayName("idempotency: same key returns cached response")
    void idempotencySameKeyReturnsCachedResponse() {
        when(http.requireTenantIdWithError(request)).thenReturn(TenantResolutionResult.success("tenant-1", null));
        when(http.resolvePrincipalId(request)).thenReturn("user-1");
        when(http.resolveCorrelationId(request)).thenReturn("corr-1");
        when(request.getHeader("Idempotency-Key")).thenReturn("key-123");
        when(http.computePayloadHash(request)).thenReturn("hash-123");
        
        Map<String, Object> cachedResponse = Map.of("status", "COMPLETED", "patternsDiscovered", 5);
        when(idempotencyStore.checkIdempotency("tenant-1", "learning:trigger", "key-123", "user-1"))
            .thenReturn(io.activej.promise.Promise.of(cachedResponse));
        
        HttpResponse httpResponse = io.activej.http.HttpResponse.ok(200).withBody("{\"data\":\"cached\"}".getBytes());
        when(http.jsonResponse(cachedResponse)).thenReturn(httpResponse);

        HttpResponse response = runPromise(() -> handler.handleLearningTrigger(request));

        assertThat(response).isNotNull();
        verify(learningBridge, never()).runLearning(anyString(), anyBoolean());
    }

    @Test
    @DisplayName("idempotency: conflicting payload returns 409")
    void idempotencyConflictingPayloadReturns409() {
        when(http.requireTenantIdWithError(request)).thenReturn(TenantResolutionResult.success("tenant-1", null));
        when(http.resolvePrincipalId(request)).thenReturn("user-1");
        when(request.getHeader("Idempotency-Key")).thenReturn("key-123");
        when(http.computePayloadHash(request)).thenReturn("hash-new");
        
        when(idempotencyStore.checkConflict("tenant-1", "learning:trigger", "key-123", "user-1", "hash-new"))
            .thenReturn(io.activej.promise.Promise.of(true));
        when(http.errorResponse(409, "Idempotency key conflict: same key used with different payload"))
            .thenReturn(errorResponse);

        HttpResponse response = runPromise(() -> handler.handleLearningTrigger(request));

        assertThat(response).isSameAs(errorResponse);
        verify(learningBridge, never()).runLearning(anyString(), anyBoolean());
    }

    @Test
    @DisplayName("non-idempotent approve: second call on approved item fails with conflict")
    void nonIdempotentApproveSecondCallFails() {
        when(http.requireTenantIdWithError(request)).thenReturn(TenantResolutionResult.success("tenant-1", null));
        when(http.resolvePrincipalId(request)).thenReturn("user-1");
        when(request.getPathParameter("reviewId")).thenReturn("review-1");
        
        when(learningBridge.approveReview("tenant-1", "user-1", "review-1")).thenReturn(false);
        when(learningBridge.getReviewQueue()).thenReturn(Map.of("review-1", Map.of("status", "APPROVED")));
        when(http.errorResponse(409, "Review item already finalized: review-1")).thenReturn(errorResponse);

        HttpResponse response = runPromise(() -> handler.handleLearningReviewApprove(request));

        assertThat(response).isSameAs(errorResponse);
    }
}