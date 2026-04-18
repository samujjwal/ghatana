package com.ghatana.aep;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.ghatana.aep.error.AepTenantException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for AEP engine features added during audit remediation:
 * <ul>
 *   <li>AEP-011: Idempotency key deduplication</li>
 *   <li>AEP-013: Tenant isolation (pattern and subscription scoping)</li>
 *   <li>AEP-014: Pipeline execution (sequential step processing)</li>
 *   <li>AEP-016: Sequence pattern timestamp ordering</li>
 *   <li>AEP-017: Subscriber failure tracking (dead-letter logging, no crash)</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Verify idempotency, tenant isolation, pipeline execution,
 *              sequence ordering, and subscriber fault tolerance
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AEP idempotency, isolation, pipeline, sequence and subscriber tests")
class AepIdempotencyAndIsolationTest extends EventloopTestBase {

    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";

    private AepEngine engine;

    @AfterEach
    void tearDown() {
        if (engine != null) {
            engine.close();
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // AEP-011: Idempotency
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("AEP-011: Idempotency key deduplication")
    class IdempotencyTests {

        @Test
        @DisplayName("second event with same idempotency key is suppressed")
        void shouldSuppressDuplicateIdempotencyKey() {
            engine = Aep.forTesting();

            AepEngine.Event first = AepEngine.Event.of("order.placed", Map.of("amount", 100))
                .withIdempotencyKey("order-42");
            AepEngine.Event duplicate = AepEngine.Event.of("order.placed", Map.of("amount", 100))
                .withIdempotencyKey("order-42");

            AepEngine.ProcessingResult r1 = runPromise(() -> engine.process(TENANT_A, first));
            AepEngine.ProcessingResult r2 = runPromise(() -> engine.process(TENANT_A, duplicate));

            assertThat(r1.success()).isTrue();
            assertThat(r2.success()).isFalse();
            assertThat(r2.metadata()).containsEntry("skipped", true);
            assertThat((String) r2.metadata().get("reason")).contains("Duplicate event suppressed");
        }

        @Test
        @DisplayName("same idempotency key for different tenants is NOT suppressed")
        void shouldNotSuppressAcrossTenantsWithSameKey() {
            engine = Aep.forTesting();

            AepEngine.Event eventA = AepEngine.Event.of("order.placed", Map.of())
                .withIdempotencyKey("key-1");
            AepEngine.Event eventB = AepEngine.Event.of("order.placed", Map.of())
                .withIdempotencyKey("key-1");

            AepEngine.ProcessingResult rA = runPromise(() -> engine.process(TENANT_A, eventA));
            AepEngine.ProcessingResult rB = runPromise(() -> engine.process(TENANT_B, eventB));

            assertThat(rA.success()).isTrue();
            assertThat(rB.success()).isTrue();
        }

        @Test
        @DisplayName("different idempotency keys are each processed once")
        void shouldProcessDistinctKeysIndependently() {
            engine = Aep.forTesting();

            AepEngine.Event e1 = AepEngine.Event.of("order.placed", Map.of())
                .withIdempotencyKey("key-A");
            AepEngine.Event e2 = AepEngine.Event.of("order.placed", Map.of())
                .withIdempotencyKey("key-B");

            AepEngine.ProcessingResult r1 = runPromise(() -> engine.process(TENANT_A, e1));
            AepEngine.ProcessingResult r2 = runPromise(() -> engine.process(TENANT_A, e2));

            assertThat(r1.success()).isTrue();
            assertThat(r2.success()).isTrue();
        }

        @Test
        @DisplayName("event without idempotency key is never suppressed")
        void shouldNeverSuppressEventsWithNoIdempotencyKey() {
            engine = Aep.forTesting();

            AepEngine.Event e1 = AepEngine.Event.of("click", Map.of("button", "buy"));
            AepEngine.Event e2 = AepEngine.Event.of("click", Map.of("button", "buy"));

            AepEngine.ProcessingResult r1 = runPromise(() -> engine.process(TENANT_A, e1));
            AepEngine.ProcessingResult r2 = runPromise(() -> engine.process(TENANT_A, e2));

            assertThat(r1.success()).isTrue();
            assertThat(r2.success()).isTrue();
        }

        @Test
        @DisplayName("expired idempotency keys are processed again after TTL")
        void shouldAllowProcessingAgainAfterConfiguredTtl() throws Exception {
            engine = Aep.create(Aep.AepConfig.builder()
                .idempotencyTtlSeconds(1)
                .build());

            AepEngine.Event first = AepEngine.Event.of("order.placed", Map.of("amount", 100))
                .withIdempotencyKey("ttl-key");
            AepEngine.Event second = AepEngine.Event.of("order.placed", Map.of("amount", 100))
                .withIdempotencyKey("ttl-key");

            AepEngine.ProcessingResult r1 = runPromise(() -> engine.process(TENANT_A, first));
            Thread.sleep(1_100L);
            AepEngine.ProcessingResult r2 = runPromise(() -> engine.process(TENANT_A, second));

            assertThat(r1.success()).isTrue();
            assertThat(r2.success()).isTrue();
        }

        @Test
        @DisplayName("oldest idempotency keys are evicted when tenant budget is exceeded")
        void shouldEvictOldestKeysWhenBudgetExceeded() {
            engine = Aep.create(Aep.AepConfig.builder()
                .maxIdempotencyKeysPerTenant(2)
                .build());

            runPromise(() -> engine.process(TENANT_A,
                AepEngine.Event.of("order.placed", Map.of()).withIdempotencyKey("key-1")));
            runPromise(() -> engine.process(TENANT_A,
                AepEngine.Event.of("order.placed", Map.of()).withIdempotencyKey("key-2")));
            runPromise(() -> engine.process(TENANT_A,
                AepEngine.Event.of("order.placed", Map.of()).withIdempotencyKey("key-3")));

            AepEngine.ProcessingResult result = runPromise(() -> engine.process(TENANT_A,
                AepEngine.Event.of("order.placed", Map.of()).withIdempotencyKey("key-1")));

            assertThat(result.success()).isTrue();
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // AEP-013: Tenant isolation
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("AEP-013: Tenant isolation")
    class TenantIsolationTests {

        @Test
        @DisplayName("pattern registered for tenant-A is not visible to tenant-B")
        void patternsShouldBeIsolatedByTenant() {
            engine = Aep.forTesting();

            runPromise(() -> engine.registerPattern(TENANT_A,
                new AepEngine.PatternDefinition("A-only", null, AepEngine.PatternType.THRESHOLD,
                    Map.of("field", "value", "threshold", 10.0))));

            List<AepEngine.Pattern> forA = runPromise(() -> engine.listPatterns(TENANT_A));
            List<AepEngine.Pattern> forB = runPromise(() -> engine.listPatterns(TENANT_B));

            assertThat(forA).hasSize(1);
            assertThat(forB).isEmpty();
        }

        @Test
        @DisplayName("getPattern() for wrong tenant rejects cross-tenant access")
        void getPatternShouldRespectTenant() {
            engine = Aep.forTesting();

            AepEngine.Pattern p = runPromise(() -> engine.registerPattern(TENANT_A,
                new AepEngine.PatternDefinition("my-pattern", null, AepEngine.PatternType.CUSTOM,
                    Map.of())));

            assertThatThrownBy(() -> runPromise(() -> engine.getPattern(TENANT_B, p.id())))
                .isInstanceOf(AepTenantException.class)
                .hasMessageContaining("different tenant");
        }

        @Test
        @DisplayName("deletePattern() for tenant-A does not affect tenant-B")
        void deletePatternShouldOnelyAffectOwnerTenant() {
            engine = Aep.forTesting();

            AepEngine.Pattern pA = runPromise(() -> engine.registerPattern(TENANT_A,
                new AepEngine.PatternDefinition("shared-name", null, AepEngine.PatternType.CUSTOM,
                    Map.of())));
            AepEngine.Pattern pB = runPromise(() -> engine.registerPattern(TENANT_B,
                new AepEngine.PatternDefinition("shared-name", null, AepEngine.PatternType.CUSTOM,
                    Map.of())));

            runPromise(() -> engine.deletePattern(TENANT_A, pA.id()));

            List<AepEngine.Pattern> forA = runPromise(() -> engine.listPatterns(TENANT_A));
            List<AepEngine.Pattern> forB = runPromise(() -> engine.listPatterns(TENANT_B));

            assertThat(forA).isEmpty();
            assertThat(forB).hasSize(1).extracting(AepEngine.Pattern::id).containsOnly(pB.id());
        }

        @Test
        @DisplayName("pattern detection does not fire across tenants")
        void detectionShouldBeIsolatedByTenant() {
            engine = Aep.forTesting();

            runPromise(() -> engine.registerPattern(TENANT_A,
                new AepEngine.PatternDefinition("A-pattern", null, AepEngine.PatternType.THRESHOLD,
                    Map.of("field", "level", "threshold", 5.0))));

            // Process a matching event but for TENANT_B — pattern is not visible
            AepEngine.ProcessingResult result = runPromise(() -> engine.process(
                TENANT_B,
                new AepEngine.Event("sensor.reading", Map.of("level", 99.0), Map.of(), Instant.now())
            ));

            assertThat(result.detections()).isEmpty();
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // AEP-014: Pipeline execution
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("AEP-014: Pipeline execution")
    class PipelineTests {

        @Test
        @DisplayName("submitPipeline() completes without exception for empty pipeline")
        void shouldHandleEmptyPipeline() {
            engine = Aep.forTesting();

            engine.submitPipeline(TENANT_A, new AepEngine.Pipeline(
                "pipe-1", "empty-pipeline", List.of()
            ));
            // No exception == pass
        }

        @Test
        @DisplayName("submitPipeline() registers a pattern from register_pattern step")
        void shouldRegisterPatternViaStep() {
            engine = Aep.forTesting();

            engine.submitPipeline(TENANT_A, new AepEngine.Pipeline(
                "pipe-2", "setup-pipeline",
                List.of(new AepEngine.PipelineStep("step-1", "register_pattern", Map.of(
                    "name", "pipeline-threshold",
                    "patternType", "THRESHOLD",
                    "field", "score",
                    "threshold", 70.0
                )))
            ));

            List<AepEngine.Pattern> patterns = runPromise(() -> engine.listPatterns(TENANT_A));
            assertThat(patterns).hasSize(1);
            assertThat(patterns.get(0).name()).isEqualTo("pipeline-threshold");
        }

        @Test
        @DisplayName("submitPipeline() continues after an unknown step type")
        void shouldContinueAfterUnknownStepType() {
            engine = Aep.forTesting();

            // register_pattern is a known step; custom_unknown is not
            engine.submitPipeline(TENANT_A, new AepEngine.Pipeline(
                "pipe-3", "mixed-pipeline",
                List.of(
                    new AepEngine.PipelineStep("step-1", "custom_unknown_step", Map.of("key", "val")),
                    new AepEngine.PipelineStep("step-2", "register_pattern", Map.of(
                        "name", "after-unknown", "patternType", "CUSTOM"))
                )
            ));

            List<AepEngine.Pattern> patterns = runPromise(() -> engine.listPatterns(TENANT_A));
            assertThat(patterns).hasSize(1);
            assertThat(patterns.get(0).name()).isEqualTo("after-unknown");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // AEP-016: Sequence ordering
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("AEP-016: Sequence pattern timestamp ordering")
    class SequenceOrderingTests {

        @Test
        @DisplayName("sequence detection fires when events arrive in correct temporal order")
        void shouldDetectSequenceInOrder() {
            engine = Aep.forTesting();

            runPromise(() -> engine.registerPattern(TENANT_A,
                new AepEngine.PatternDefinition("checkout-flow", null, AepEngine.PatternType.SEQUENCE,
                    Map.of("expectedTypes", List.of("cart.add", "checkout.start", "payment.complete"),
                           "correlationField", "orderId"))));

            Instant t0 = Instant.parse("2026-01-01T00:00:00Z");

            runPromise(() -> engine.process(TENANT_A,
                new AepEngine.Event("cart.add", Map.of("orderId", "ord-1"), Map.of(), t0)));
            runPromise(() -> engine.process(TENANT_A,
                new AepEngine.Event("checkout.start", Map.of("orderId", "ord-1"), Map.of(),
                    t0.plusSeconds(5))));
            AepEngine.ProcessingResult result = runPromise(() -> engine.process(TENANT_A,
                new AepEngine.Event("payment.complete", Map.of("orderId", "ord-1"), Map.of(),
                    t0.plusSeconds(10))));

            assertThat(result.detections()).hasSize(1);
            assertThat(result.detections().get(0).patternName()).isEqualTo("checkout-flow");
        }

        @Test
        @DisplayName("out-of-order event is ignored, sequence does not fire")
        void shouldIgnoreOutOfOrderEvent() {
            engine = Aep.forTesting();

            runPromise(() -> engine.registerPattern(TENANT_A,
                new AepEngine.PatternDefinition("two-step", null, AepEngine.PatternType.SEQUENCE,
                    Map.of("expectedTypes", List.of("step.one", "step.two"),
                           "correlationField", "userId"))));

            Instant t10 = Instant.parse("2026-01-01T00:00:10Z");
            Instant t5  = Instant.parse("2026-01-01T00:00:05Z");

            // step.one arrives at t10
            runPromise(() -> engine.process(TENANT_A,
                new AepEngine.Event("step.one", Map.of("userId", "u1"), Map.of(), t10)));
            // step.two arrives at t5 — BEFORE step.one, should be ignored
            AepEngine.ProcessingResult result = runPromise(() -> engine.process(TENANT_A,
                new AepEngine.Event("step.two", Map.of("userId", "u1"), Map.of(), t5)));

            assertThat(result.detections()).isEmpty();
        }

        @Test
        @DisplayName("events without a real correlation key do not share sequence state")
        void shouldNotUseGlobalFallbackForMissingCorrelationKey() {
            engine = Aep.forTesting();

            runPromise(() -> engine.registerPattern(TENANT_A,
                new AepEngine.PatternDefinition("anonymous-two-step", null, AepEngine.PatternType.SEQUENCE,
                    Map.of("expectedTypes", List.of("step.one", "step.two"),
                           "correlationField", "missingField"))));

            runPromise(() -> engine.process(TENANT_A,
                new AepEngine.Event("step.one", Map.of("other", "a"), Map.of(), Instant.now())));
            AepEngine.ProcessingResult result = runPromise(() -> engine.process(TENANT_A,
                new AepEngine.Event("step.two", Map.of("other", "b"), Map.of(), Instant.now().plusSeconds(1))));

            assertThat(result.detections()).isEmpty();
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // AEP-017: Subscriber fault tolerance
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("AEP-017: Subscriber exception isolation")
    class SubscriberFaultToleranceTests {

        @Test
        @DisplayName("exception in one subscriber does not prevent other subscribers from receiving detection")
        void exceptingSubscriberShouldNotBlockOthers() {
            engine = Aep.forTesting();

            AepEngine.Pattern pattern = runPromise(() -> engine.registerPattern(TENANT_A,
                new AepEngine.PatternDefinition("high-value", null, AepEngine.PatternType.THRESHOLD,
                    Map.of("field", "amount", "threshold", 100.0))));

            List<String> received = new ArrayList<>();

            // First subscriber throws
            engine.subscribe(TENANT_A, pattern.id(),
                detection -> { throw new RuntimeException("subscriber failure"); });
            // Second subscriber should still get called
            engine.subscribe(TENANT_A, pattern.id(),
                detection -> received.add(detection.patternId()));

            AepEngine.ProcessingResult result = runPromise(() -> engine.process(TENANT_A,
                new AepEngine.Event("payment", Map.of("amount", 500.0), Map.of(), Instant.now())));

            assertThat(result.success()).isTrue();
            assertThat(received).containsExactly(pattern.id());
        }

        @Test
        @DisplayName("process() itself succeeds even if all subscribers throw")
        void shouldSucceedEvenIfAllSubscribersThrow() {
            engine = Aep.forTesting();

            AepEngine.Pattern p = runPromise(() -> engine.registerPattern(TENANT_A,
                new AepEngine.PatternDefinition("threshold-p", null, AepEngine.PatternType.THRESHOLD,
                    Map.of("field", "val", "threshold", 0.0))));

            engine.subscribe(TENANT_A, p.id(),
                detection -> { throw new IllegalStateException("crash!"); });

            AepEngine.ProcessingResult result = runPromise(() -> engine.process(TENANT_A,
                new AepEngine.Event("metric", Map.of("val", 1.0), Map.of(), Instant.now())));

            assertThat(result.success()).isTrue();
        }

        @Test
        @DisplayName("cancelled subscription no longer receives detections")
        void cancelledSubscriptionShouldNotReceive() {
            engine = Aep.forTesting();

            AepEngine.Pattern p = runPromise(() -> engine.registerPattern(TENANT_A,
                new AepEngine.PatternDefinition("threshold-p2", null, AepEngine.PatternType.THRESHOLD,
                    Map.of("field", "score", "threshold", 1.0))));

            AtomicInteger count = new AtomicInteger();
            AepEngine.Subscription sub = engine.subscribe(TENANT_A, p.id(),
                d -> count.incrementAndGet());

            AepEngine.Event matchingEvent =
                new AepEngine.Event("metric", Map.of("score", 99.0), Map.of(), Instant.now());

            runPromise(() -> engine.process(TENANT_A, matchingEvent));
            sub.cancel();
            assertThat(sub.isCancelled()).isTrue();

            runPromise(() -> engine.process(TENANT_A, matchingEvent));

            assertThat(count.get()).isEqualTo(1); // only the first process fires
        }
    }
}
