/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.resilience;

import com.ghatana.platform.database.adapter.PostgreSQLAdapter;
import com.ghatana.platform.observability.clickhouse.ClickHouseTraceStorage;
import com.ghatana.platform.messaging.s3.S3Connector;
import com.ghatana.platform.policyascode.PolicyAsCodeEngine;
import com.ghatana.platform.ai.integration.LlmGateway;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import com.ghatana.platform.testing.activej.EventloopTestBase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * P1-2: Real executable runtime dependency failure-injection tests.
 *
 * <p>Validates system resilience by executing real dependency-failure scenarios:
 * <ul>
 *   <li>Postgres unavailable</li>
 *   <li>ClickHouse unavailable</li>
 *   <li>OpenSearch unavailable</li>
 *   <li>S3 unavailable</li>
 *   <li>Audit sink unavailable</li>
 *   <li>Policy engine unavailable</li>
 *   <li>AI completion unavailable</li>
 *   <li>Network timeout</li>
 *   <li>Queue saturation</li>
 * </ul>
 *
 * <p>This replaces token-based release checks with executable failure-injection
 * scenarios that prove the system can handle dependency failures gracefully.
 *
 * @doc.type class
 * @doc.purpose Real executable runtime dependency failure-injection tests (P1-2)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Runtime Dependency Failure-Injection Tests (P1-2)")
@Tag("production")
@Tag("resilience")
@Tag("failure-injection")
@Tag("chaos")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RuntimeDependencyFailureInjectionTest extends EventloopTestBase {

    @Mock
    private PostgreSQLAdapter postgresAdapter;

    @Mock
    private ClickHouseTraceStorage clickHouseStorage;

    @Mock
    private S3Connector s3Connector;

    @Mock
    private PolicyAsCodeEngine policyEngine;

    @Mock
    private LlmGateway llmGateway;

    private DependencyResilienceManager resilienceManager;

    @BeforeEach
    void setUp() {
        resilienceManager = spy(new DependencyResilienceManager(
            postgresAdapter,
            clickHouseStorage,
            s3Connector,
            policyEngine,
            llmGateway
        ));
    }

    // ==================== Postgres Unavailability ====================

    @Test
    @DisplayName("P1-2: System degrades gracefully when Postgres is unavailable")
    void systemDegradesGracefullyWhenPostgresUnavailable() {
        // Given: Postgres is unavailable
        when(postgresAdapter.executeQuery(any(), any()))
            .thenReturn(Promise.ofException(new RuntimeException("Connection refused")));

        // When: Execute operation that requires Postgres
        DependencyOperationResult result = runPromise(() -> 
            resilienceManager.executeWithFallback("postgres-operation", () -> 
                postgresAdapter.executeQuery("SELECT 1", new Object[]{})
            )
        );

        // Then: Should return degraded state
        assertThat(result.getStatus()).isEqualTo(DependencyOperationResult.Status.DEGRADED);
        assertThat(result.getFallbackUsed()).isTrue();
        assertThat(result.getErrorMessage()).contains("Postgres unavailable");
    }

    @Test
    @DisplayName("P1-2: Circuit breaker opens after repeated Postgres failures")
    void circuitBreakerOpensAfterRepeatedPostgresFailures() {
        // Given: Postgres fails repeatedly
        when(postgresAdapter.executeQuery(any(), any()))
            .thenReturn(Promise.ofException(new RuntimeException("Connection refused")));

        // When: Execute multiple failing operations
        for (int i = 0; i < 5; i++) {
            runPromise(() -> 
                resilienceManager.executeWithFallback("postgres-operation", () -> 
                    postgresAdapter.executeQuery("SELECT 1", new Object[]{})
                )
            );
        }

        // Then: Circuit breaker should be open
        assertThat(resilienceManager.isCircuitBreakerOpen("postgres-operation")).isTrue();

        // And: Subsequent calls should fail fast
        DependencyOperationResult result = runPromise(() -> 
            resilienceManager.executeWithFallback("postgres-operation", () -> 
                postgresAdapter.executeQuery("SELECT 1", new Object[]{})
            )
        );
        assertThat(result.getStatus()).isEqualTo(DependencyOperationResult.Status.CIRCUIT_OPEN);
    }

    // ==================== ClickHouse Unavailability ====================

    @Test
    @DisplayName("P1-2: System continues when ClickHouse is unavailable")
    void systemContinuesWhenClickHouseUnavailable() {
        // Given: ClickHouse is unavailable
        when(clickHouseStorage.storeTrace(any(), any()))
            .thenReturn(Promise.ofException(new RuntimeException("ClickHouse connection failed")));

        // When: Execute operation that requires ClickHouse
        DependencyOperationResult result = runPromise(() -> 
            resilienceManager.executeWithFallback("clickhouse-operation", () -> 
                clickHouseStorage.storeTrace("trace-id", "trace-data")
            )
        );

        // Then: Should succeed with fallback (in-memory storage)
        assertThat(result.getStatus()).isEqualTo(DependencyOperationResult.Status.SUCCESS_WITH_FALLBACK);
        assertThat(result.getFallbackUsed()).isTrue();
    }

    // ==================== OpenSearch Unavailability ====================

    @Test
    @DisplayName("P1-2: Search operations degrade when OpenSearch is unavailable")
    void searchOperationsDegradeWhenOpenSearchUnavailable() {
        // Given: OpenSearch is unavailable
        when(resilienceManager.executeSearch(any(), any()))
            .thenReturn(Promise.ofException(new RuntimeException("OpenSearch unavailable")));

        // When: Execute search operation
        DependencyOperationResult result = runPromise(() -> 
            resilienceManager.executeWithFallback("search-operation", () -> 
                resilienceManager.executeSearch("index", "query")
            )
        );

        // Then: Should return cached results or empty set
        assertThat(result.getStatus()).isEqualTo(DependencyOperationResult.Status.DEGRADED);
        assertThat(result.getFallbackUsed()).isTrue();
    }

    // ==================== S3 Unavailability ====================

    @Test
    @DisplayName("P1-2: File uploads retry when S3 is temporarily unavailable")
    void fileUploadsRetryWhenS3TemporarilyUnavailable() {
        // Given: S3 fails initially then succeeds
        when(s3Connector.upload(any(), any()))
            .thenReturn(Promise.ofException(new RuntimeException("S3 timeout")))
            .thenReturn(Promise.of("s3://bucket/key"));

        // When: Execute upload with retry
        DependencyOperationResult result = runPromise(() -> 
            resilienceManager.executeWithRetry("s3-upload", 3, () -> 
                s3Connector.upload("bucket", "key")
            )
        );

        // Then: Should succeed after retry
        assertThat(result.getStatus()).isEqualTo(DependencyOperationResult.Status.SUCCESS);
        assertThat(result.getRetryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("P1-2: File uploads fail after exhausting S3 retries")
    void fileUploadsFailAfterExhaustingS3Retries() {
        // Given: S3 consistently fails
        when(s3Connector.upload(any(), any()))
            .thenReturn(Promise.ofException(new RuntimeException("S3 timeout")));

        // When: Execute upload with limited retries
        assertThatThrownBy(() -> runPromise(() -> 
            resilienceManager.executeWithRetry("s3-upload", 2, () -> 
                s3Connector.upload("bucket", "key")
            )
        )).isInstanceOf(RuntimeException.class).hasMessageContaining("S3 timeout");

        // Then: Should have attempted all retries
        verify(s3Connector, times(3)).upload(any(), any());
    }

    // ==================== Audit Sink Unavailability ====================

    @Test
    @DisplayName("P1-2: Critical operations fail when audit sink is unavailable")
    void criticalOperationsFailWhenAuditSinkUnavailable() {
        // Given: Audit sink is unavailable
        when(resilienceManager.writeAudit(any()))
            .thenReturn(Promise.ofException(new RuntimeException("Audit sink unavailable")));

        // When: Execute critical operation requiring audit
        assertThatThrownBy(() -> runPromise(() -> 
            resilienceManager.executeCriticalWithAudit("critical-operation", () -> 
                Promise.complete()
            )
        )).isInstanceOf(IllegalStateException.class)
         .hasMessageContaining("P1-2")
         .hasMessageContaining("Audit sink unavailable")
         .hasMessageContaining("operation blocked");
    }

    // ==================== Policy Engine Unavailability ====================

    @Test
    @DisplayName("P1-2: Policy evaluation uses cached rules when engine unavailable")
    void policyEvaluationUsesCachedRulesWhenEngineUnavailable() {
        // Given: Policy engine is unavailable
        when(policyEngine.evaluate(any(), any()))
            .thenReturn(Promise.ofException(new RuntimeException("Policy engine unavailable")));

        // When: Evaluate policy with fallback
        DependencyOperationResult result = runPromise(() -> 
            resilienceManager.evaluatePolicyWithFallback("policy-check", "resource", "action")
        );

        // Then: Should use cached policy rules
        assertThat(result.getStatus()).isEqualTo(DependencyOperationResult.Status.SUCCESS_WITH_FALLBACK);
        assertThat(result.getFallbackUsed()).isTrue();
        assertThat(result.getDecision()).isNotNull();
    }

    // ==================== AI Completion Unavailability ====================

    @Test
    @DisplayName("P1-2: AI operations use fallback model when primary unavailable")
    void aiOperationsUseFallbackModelWhenPrimaryUnavailable() {
        // Given: Primary LLM is unavailable
        when(llmGateway.complete(any(), any()))
            .thenReturn(Promise.ofException(new RuntimeException("LLM unavailable")));

        // When: Execute AI completion with fallback
        DependencyOperationResult result = runPromise(() -> 
            resilienceManager.executeAiWithFallback("ai-completion", "prompt")
        );

        // Then: Should use fallback model
        assertThat(result.getStatus()).isEqualTo(DependencyOperationResult.Status.SUCCESS_WITH_FALLBACK);
        assertThat(result.getFallbackUsed()).isTrue();
        assertThat(result.getResponse()).isNotNull();
    }

    @Test
    @DisplayName("P1-2: AI operations fail gracefully when all models unavailable")
    void aiOperationsFailGracefullyWhenAllModelsUnavailable() {
        // Given: All LLMs are unavailable
        when(llmGateway.complete(any(), any()))
            .thenReturn(Promise.ofException(new RuntimeException("All LLMs unavailable")));

        // When: Execute AI completion
        DependencyOperationResult result = runPromise(() -> 
            resilienceManager.executeAiWithFallback("ai-completion", "prompt")
        );

        // Then: Should return error response
        assertThat(result.getStatus()).isEqualTo(DependencyOperationResult.Status.ERROR);
        assertThat(result.getErrorMessage()).contains("AI service unavailable");
    }

    // ==================== Network Timeout ====================

    @Test
    @DisplayName("P1-2: Operations retry on network timeout")
    void operationsRetryOnNetworkTimeout() {
        // Given: Network timeout occurs
        when(postgresAdapter.executeQuery(any(), any()))
            .thenReturn(Promise.ofException(new java.util.concurrent.TimeoutException("Network timeout")))
            .thenReturn(Promise.of(new Object()));

        // When: Execute operation with timeout handling
        DependencyOperationResult result = runPromise(() -> 
            resilienceManager.executeWithTimeoutHandling("network-operation", () -> 
                postgresAdapter.executeQuery("SELECT 1", new Object[]{})
            )
        );

        // Then: Should succeed after retry
        assertThat(result.getStatus()).isEqualTo(DependencyOperationResult.Status.SUCCESS);
        assertThat(result.getRetryCount()).isEqualTo(1);
    }

    // ==================== Queue Saturation ====================

    @Test
    @DisplayName("P1-2: Operations backpressure when queue saturated")
    void operationsBackpressureWhenQueueSaturated() {
        // Given: Queue is saturated
        when(resilienceManager.enqueue(any(), any()))
            .thenReturn(Promise.ofException(new RuntimeException("Queue saturated")));

        // When: Attempt to enqueue
        DependencyOperationResult result = runPromise(() -> 
            resilienceManager.executeWithBackpressure("queue-operation", "data")
        );

        // Then: Should apply backpressure
        assertThat(result.getStatus()).isEqualTo(DependencyOperationResult.Status.BACKPRESSURE);
        assertThat(result.getBackpressureApplied()).isTrue();
    }

    @Test
    @DisplayName("P1-2: Queue operations succeed after backpressure clears")
    void queueOperationsSucceedAfterBackpressureClears() {
        // Given: Queue is saturated initially
        when(resilienceManager.enqueue(any(), any()))
            .thenReturn(Promise.ofException(new RuntimeException("Queue saturated")))
            .thenReturn(Promise.complete());

        // When: Attempt to enqueue with backpressure handling
        DependencyOperationResult result = runPromise(() -> 
            resilienceManager.executeWithBackpressure("queue-operation", "data")
        );

        // Then: Should succeed after backpressure clears
        assertThat(result.getStatus()).isEqualTo(DependencyOperationResult.Status.SUCCESS);
        assertThat(result.getBackpressureApplied()).isTrue();
    }

    // ==================== Retry with Exponential Backoff ====================

    @Test
    @DisplayName("P1-2: Retry uses exponential backoff")
    void retryUsesExponentialBackoff() {
        // Given: Operation fails initially
        when(postgresAdapter.executeQuery(any(), any()))
            .thenReturn(Promise.ofException(new RuntimeException("Temporary failure")))
            .thenReturn(Promise.of(new Object()));

        // When: Execute with exponential backoff
        long startTime = System.currentTimeMillis();
        DependencyOperationResult result = runPromise(() -> 
            resilienceManager.executeWithExponentialBackoff("backoff-operation", 3, () -> 
                postgresAdapter.executeQuery("SELECT 1", new Object[]{})
            )
        );
        long duration = System.currentTimeMillis() - startTime;

        // Then: Should have exponential backoff delays
        assertThat(result.getStatus()).isEqualTo(DependencyOperationResult.Status.SUCCESS);
        assertThat(result.getRetryCount()).isEqualTo(1);
        assertThat(duration).isGreaterThan(100); // At least 100ms backoff
    }

    // ==================== Success Path ====================

    @Test
    @DisplayName("P1-2: Operations succeed when all dependencies are healthy")
    void operationsSucceedWhenAllDependenciesHealthy() {
        // Given: All dependencies are healthy
        when(postgresAdapter.executeQuery(any(), any()))
            .thenReturn(Promise.of(new Object()));
        when(clickHouseStorage.storeTrace(any(), any()))
            .thenReturn(Promise.complete());
        when(s3Connector.upload(any(), any()))
            .thenReturn(Promise.of("s3://bucket/key"));
        when(policyEngine.evaluate(any(), any()))
            .thenReturn(Promise.of(true));
        when(llmGateway.complete(any(), any()))
            .thenReturn(Promise.of("response"));

        // When: Execute operations
        DependencyOperationResult result = runPromise(() -> 
            resilienceManager.executeCompositeOperation("composite-operation")
        );

        // Then: Should succeed without fallbacks
        assertThat(result.getStatus()).isEqualTo(DependencyOperationResult.Status.SUCCESS);
        assertThat(result.getFallbackUsed()).isFalse();
    }
}
