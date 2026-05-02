/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.bridge;

import com.ghatana.kernel.bridge.port.BridgeAuditEmitter;
import com.ghatana.kernel.bridge.port.BridgeAuditEmitter.BridgeAuditEvent;
import com.ghatana.kernel.bridge.port.BridgeAuthorizationService;
import com.ghatana.kernel.bridge.port.BridgeContext;
import com.ghatana.kernel.bridge.port.BridgeHealthIndicator;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 4.3 contract tests for {@link AbstractKernelBridge}.
 *
 * <p>Covers:
 * <ul>
 *   <li>Authorization checks delegated to the port</li>
 *   <li>Audit events emitted for allowed and denied calls</li>
 *   <li>Health reporting on success and failure</li>
 *   <li>Retry logic with bounded attempts</li>
 *   <li>Metadata redaction</li>
 *   <li>Lifecycle state guards</li>
 *   <li>Context propagation (tenant, principal, correlation) through audit events</li>
 * </ul>
 * </p>
 *
 * @doc.type class
 * @doc.purpose AbstractKernelBridge contract tests
 * @doc.layer core
 * @doc.pattern Test
 * @author Ghatana Kernel Team
 * @since 1.3.0
 */
@DisplayName("AbstractKernelBridge Contract Tests")
@ExtendWith(MockitoExtension.class)
class AbstractKernelBridgeTest extends EventloopTestBase {

    // -------------------------------------------------------------------------
    // Minimal test double
    // -------------------------------------------------------------------------

    /**
     * Minimal concrete subclass exposing protected AbstractKernelBridge methods
     * under package-visible test helpers.
     */
    static final class TestBridge extends AbstractKernelBridge {

        TestBridge(
                BridgeAuthorizationService auth,
                BridgeAuditEmitter auditor,
                BridgeHealthIndicator health) {
            super("test-bridge", auth, auditor, health);
        }

        void start() { markStarted(); }
        void stop()  { markStopped(); }

        Promise<Boolean> authorize(BridgeContext ctx, String resource, String action) {
            return checkAuthorized(ctx, resource, action);
        }

        <T> Promise<T> retryOp(
                String opName, BridgeContext ctx, String resource, String action,
                java.util.function.Supplier<CompletableFuture<T>> supplier) {
            return executeWithRetry(opName, ctx, resource, action, supplier);
        }

        String callRedact(String input) { return redact(input); }
    }

    // -------------------------------------------------------------------------
    // Test infrastructure
    // -------------------------------------------------------------------------

    private final List<BridgeAuditEvent> emittedEvents = new ArrayList<>();
    private final List<String> healthCalls = new ArrayList<>();

    private BridgeAuditEmitter capturingAuditor;
    private BridgeHealthIndicator capturingHealth;
    private BridgeContext testContext;

    @BeforeEach
    void setUp() {
        capturingAuditor = emittedEvents::add;
        capturingHealth = new BridgeHealthIndicator() {
            @Override public void reportHealthy(String id) { healthCalls.add("HEALTHY:" + id); }
            @Override public void reportDegraded(String id, String reason) { healthCalls.add("DEGRADED:" + id); }
            @Override public void reportUnhealthy(String id, String reason) { healthCalls.add("UNHEALTHY:" + id); }
        };
        testContext = BridgeContext.builder()
                .tenantId("tenant-42")
                .principalId("user-99")
                .correlationId("corr-abc")
                .build();
        emittedEvents.clear();
        healthCalls.clear();
    }

    // -------------------------------------------------------------------------
    // Lifecycle tests
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Lifecycle")
    class LifecycleTests {

        @Test
        @DisplayName("requireStarted() throws before markStarted()")
        void requireStartedThrowsBeforeStart() {
            TestBridge bridge = new TestBridge(
                    BridgeAuthorizationService.allowAll(), capturingAuditor, capturingHealth);
            assertThatThrownBy(bridge::requireStarted)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("test-bridge");
        }

        @Test
        @DisplayName("requireStarted() passes after markStarted()")
        void requireStartedPassesAfterStart() {
            TestBridge bridge = new TestBridge(
                    BridgeAuthorizationService.allowAll(), capturingAuditor, capturingHealth);
            bridge.start();
            bridge.requireStarted(); // must not throw
        }

        @Test
        @DisplayName("markStarted() reports healthy via health port")
        void markStartedReportsHealthy() {
            TestBridge bridge = new TestBridge(
                    BridgeAuthorizationService.allowAll(), capturingAuditor, capturingHealth);
            bridge.start();
            assertThat(healthCalls).contains("HEALTHY:test-bridge");
        }
    }

    // -------------------------------------------------------------------------
    // Authorization and audit
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Authorization and audit")
    class AuthorizationTests {

        @Test
        @DisplayName("allowed call emits ALLOWED audit event")
        void allowedCallEmitsAllowedAuditEvent() {
            TestBridge bridge = new TestBridge(
                    BridgeAuthorizationService.allowAll(), capturingAuditor, capturingHealth);
            bridge.start();

            Boolean result = bridge.authorize(testContext, "dataset:read", "read").getResult();

            assertThat(result).isTrue();
            assertThat(emittedEvents).hasSize(1);
            assertThat(emittedEvents.get(0).outcome()).isEqualTo("ALLOWED");
            assertThat(emittedEvents.get(0).tenantId()).isEqualTo("tenant-42");
            assertThat(emittedEvents.get(0).principalId()).isEqualTo("user-99");
            assertThat(emittedEvents.get(0).correlationId()).isEqualTo("corr-abc");
        }

        @Test
        @DisplayName("denied call emits DENIED audit event")
        void deniedCallEmitsDeniedAuditEvent() {
            BridgeAuthorizationService denyAll = (ctx, resource, action) -> Promise.of(Boolean.FALSE);
            TestBridge bridge = new TestBridge(denyAll, capturingAuditor, capturingHealth);
            bridge.start();

            Boolean result = bridge.authorize(testContext, "dataset:write", "write").getResult();

            assertThat(result).isFalse();
            assertThat(emittedEvents).hasSize(1);
            assertThat(emittedEvents.get(0).outcome()).isEqualTo("DENIED");
            assertThat(emittedEvents.get(0).resource()).isEqualTo("dataset:write");
            assertThat(emittedEvents.get(0).action()).isEqualTo("write");
        }

        @Test
        @DisplayName("audit event carries tenant, principal, correlation from BridgeContext")
        void auditEventCarriesContextFields() {
            TestBridge bridge = new TestBridge(
                    BridgeAuthorizationService.allowAll(), capturingAuditor, capturingHealth);
            bridge.start();
            bridge.authorize(testContext, "resource", "action").getResult();

            BridgeAuditEvent event = emittedEvents.get(0);
            assertThat(event.tenantId()).isEqualTo("tenant-42");
            assertThat(event.principalId()).isEqualTo("user-99");
            assertThat(event.correlationId()).isEqualTo("corr-abc");
            assertThat(event.bridgeId()).isEqualTo("test-bridge");
        }
    }

    // -------------------------------------------------------------------------
    // Resilience: retry
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Retry resilience")
    class RetryTests {

        @Test
        @DisplayName("succeeds on first attempt without retrying")
        void succeedsOnFirstAttempt() {
            TestBridge bridge = new TestBridge(
                    BridgeAuthorizationService.allowAll(), capturingAuditor, capturingHealth);
            bridge.start();

            String result = runPromise(() -> bridge.retryOp(
                    "testOp", testContext, "res", "read",
                    () -> CompletableFuture.completedFuture("success")
            ));

            assertThat(result).isEqualTo("success");
            // HEALTHY:test-bridge from markStarted + HEALTHY from success
            assertThat(healthCalls.stream().filter(s -> s.startsWith("HEALTHY")).count())
                    .isGreaterThanOrEqualTo(1L);
        }

        @Test
        @DisplayName("exhausts all retries and reports unhealthy after max failures")
        void exhaustsRetriesAndReportsUnhealthy() {
            TestBridge bridge = new TestBridge(
                    BridgeAuthorizationService.allowAll(), capturingAuditor, capturingHealth);
            bridge.start();

            assertThatThrownBy(() -> runPromise(() -> bridge.retryOp(
                    "failOp", testContext, "res", "write",
                    () -> {
                        CompletableFuture<String> future = new CompletableFuture<>();
                        future.completeExceptionally(new RuntimeException("transient"));
                        return future;
                    }
            ))).isInstanceOf(RuntimeException.class);

            assertThat(healthCalls).contains("UNHEALTHY:test-bridge");
            // Error audit event must be emitted
            assertThat(emittedEvents.stream().anyMatch(e -> "ERROR".equals(e.outcome()))).isTrue();
        }
    }

    // -------------------------------------------------------------------------
    // Metadata redaction
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Metadata redaction")
    class RedactionTests {

        @Test
        @DisplayName("redact() replaces sensitive key=value pairs")
        void redactsPasswordAndToken() {
            TestBridge bridge = new TestBridge(
                    BridgeAuthorizationService.allowAll(), capturingAuditor, capturingHealth);

            String raw = "user=alice,password=s3cr3t,token=Bearer xyz123,host=db.example.com";
            String redacted = bridge.callRedact(raw);

            assertThat(redacted).doesNotContain("s3cr3t");
            assertThat(redacted).doesNotContain("Bearer xyz123");
            assertThat(redacted).contains("user=alice");
            assertThat(redacted).contains("host=db.example.com");
            assertThat(redacted).contains("***REDACTED***");
        }

        @Test
        @DisplayName("redact() handles null input safely")
        void redactsNullSafely() {
            TestBridge bridge = new TestBridge(
                    BridgeAuthorizationService.allowAll(), capturingAuditor, capturingHealth);
            assertThat(bridge.callRedact(null)).isEqualTo("<null>");
        }
    }

    // -------------------------------------------------------------------------
    // BridgeContext propagation
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("BridgeContext construction")
    class BridgeContextTests {

        @Test
        @DisplayName("tenantId must not be null")
        void tenantIdRequired() {
            assertThatThrownBy(() -> BridgeContext.builder()
                    .principalId("user")
                    .correlationId("corr")
                    .build())
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("missing principalId defaults to 'anonymous'")
        void missingPrincipalDefaultsToAnonymous() {
            BridgeContext ctx = BridgeContext.builder()
                    .tenantId("tenant")
                    .correlationId("corr")
                    .build();
            assertThat(ctx.getPrincipalId()).isEqualTo("anonymous");
        }

        @Test
        @DisplayName("idempotencyKey is null when not set")
        void idempotencyKeyNullableByDefault() {
            BridgeContext ctx = BridgeContext.builder()
                    .tenantId("tenant")
                    .build();
            assertThat(ctx.getIdempotencyKey()).isNull();
        }
    }
}
