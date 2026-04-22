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
 *   <li>AEP-013: Tenant isolation (pattern and subscription scoping)</li> // GH-90000
 *   <li>AEP-014: Pipeline execution (sequential step processing)</li> // GH-90000
 *   <li>AEP-016: Sequence pattern timestamp ordering</li>
 *   <li>AEP-017: Subscriber failure tracking (dead-letter logging, no crash)</li> // GH-90000
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Verify idempotency, tenant isolation, pipeline execution,
 *              sequence ordering, and subscriber fault tolerance
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AEP idempotency, isolation, pipeline, sequence and subscriber tests [GH-90000]")
class AepIdempotencyAndIsolationTest extends EventloopTestBase {

    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";

    private AepEngine engine;

    @AfterEach
    void tearDown() { // GH-90000
        if (engine != null) { // GH-90000
            engine.close(); // GH-90000
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // AEP-011: Idempotency
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("AEP-011: Idempotency key deduplication [GH-90000]")
    class IdempotencyTests {

        @Test
        @DisplayName("second event with same idempotency key is suppressed [GH-90000]")
        void shouldSuppressDuplicateIdempotencyKey() { // GH-90000
            engine = Aep.forTesting(); // GH-90000

            AepEngine.Event first = AepEngine.Event.of("order.placed", Map.of("amount", 100)) // GH-90000
                .withIdempotencyKey("order-42 [GH-90000]");
            AepEngine.Event duplicate = AepEngine.Event.of("order.placed", Map.of("amount", 100)) // GH-90000
                .withIdempotencyKey("order-42 [GH-90000]");

            AepEngine.ProcessingResult r1 = runPromise(() -> engine.process(TENANT_A, first)); // GH-90000
            AepEngine.ProcessingResult r2 = runPromise(() -> engine.process(TENANT_A, duplicate)); // GH-90000

            assertThat(r1.success()).isTrue(); // GH-90000
            assertThat(r2.success()).isFalse(); // GH-90000
            assertThat(r2.metadata()).containsEntry("skipped", true); // GH-90000
            assertThat((String) r2.metadata().get("reason [GH-90000]")).contains("Duplicate event suppressed [GH-90000]");
        }

        @Test
        @DisplayName("same idempotency key for different tenants is NOT suppressed [GH-90000]")
        void shouldNotSuppressAcrossTenantsWithSameKey() { // GH-90000
            engine = Aep.forTesting(); // GH-90000

            AepEngine.Event eventA = AepEngine.Event.of("order.placed", Map.of()) // GH-90000
                .withIdempotencyKey("key-1 [GH-90000]");
            AepEngine.Event eventB = AepEngine.Event.of("order.placed", Map.of()) // GH-90000
                .withIdempotencyKey("key-1 [GH-90000]");

            AepEngine.ProcessingResult rA = runPromise(() -> engine.process(TENANT_A, eventA)); // GH-90000
            AepEngine.ProcessingResult rB = runPromise(() -> engine.process(TENANT_B, eventB)); // GH-90000

            assertThat(rA.success()).isTrue(); // GH-90000
            assertThat(rB.success()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("different idempotency keys are each processed once [GH-90000]")
        void shouldProcessDistinctKeysIndependently() { // GH-90000
            engine = Aep.forTesting(); // GH-90000

            AepEngine.Event e1 = AepEngine.Event.of("order.placed", Map.of()) // GH-90000
                .withIdempotencyKey("key-A [GH-90000]");
            AepEngine.Event e2 = AepEngine.Event.of("order.placed", Map.of()) // GH-90000
                .withIdempotencyKey("key-B [GH-90000]");

            AepEngine.ProcessingResult r1 = runPromise(() -> engine.process(TENANT_A, e1)); // GH-90000
            AepEngine.ProcessingResult r2 = runPromise(() -> engine.process(TENANT_A, e2)); // GH-90000

            assertThat(r1.success()).isTrue(); // GH-90000
            assertThat(r2.success()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("event without idempotency key is never suppressed [GH-90000]")
        void shouldNeverSuppressEventsWithNoIdempotencyKey() { // GH-90000
            engine = Aep.forTesting(); // GH-90000

            AepEngine.Event e1 = AepEngine.Event.of("click", Map.of("button", "buy")); // GH-90000
            AepEngine.Event e2 = AepEngine.Event.of("click", Map.of("button", "buy")); // GH-90000

            AepEngine.ProcessingResult r1 = runPromise(() -> engine.process(TENANT_A, e1)); // GH-90000
            AepEngine.ProcessingResult r2 = runPromise(() -> engine.process(TENANT_A, e2)); // GH-90000

            assertThat(r1.success()).isTrue(); // GH-90000
            assertThat(r2.success()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("expired idempotency keys are processed again after TTL [GH-90000]")
        void shouldAllowProcessingAgainAfterConfiguredTtl() throws Exception { // GH-90000
            engine = Aep.create(Aep.AepConfig.builder() // GH-90000
                .idempotencyTtlSeconds(1) // GH-90000
                .build()); // GH-90000

            AepEngine.Event first = AepEngine.Event.of("order.placed", Map.of("amount", 100)) // GH-90000
                .withIdempotencyKey("ttl-key [GH-90000]");
            AepEngine.Event second = AepEngine.Event.of("order.placed", Map.of("amount", 100)) // GH-90000
                .withIdempotencyKey("ttl-key [GH-90000]");

            AepEngine.ProcessingResult r1 = runPromise(() -> engine.process(TENANT_A, first)); // GH-90000
            Thread.sleep(1_100L); // GH-90000
            AepEngine.ProcessingResult r2 = runPromise(() -> engine.process(TENANT_A, second)); // GH-90000

            assertThat(r1.success()).isTrue(); // GH-90000
            assertThat(r2.success()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("oldest idempotency keys are evicted when tenant budget is exceeded [GH-90000]")
        void shouldEvictOldestKeysWhenBudgetExceeded() { // GH-90000
            engine = Aep.create(Aep.AepConfig.builder() // GH-90000
                .maxIdempotencyKeysPerTenant(2) // GH-90000
                .build()); // GH-90000

            runPromise(() -> engine.process(TENANT_A, // GH-90000
                AepEngine.Event.of("order.placed", Map.of()).withIdempotencyKey("key-1 [GH-90000]")));
            runPromise(() -> engine.process(TENANT_A, // GH-90000
                AepEngine.Event.of("order.placed", Map.of()).withIdempotencyKey("key-2 [GH-90000]")));
            runPromise(() -> engine.process(TENANT_A, // GH-90000
                AepEngine.Event.of("order.placed", Map.of()).withIdempotencyKey("key-3 [GH-90000]")));

            AepEngine.ProcessingResult result = runPromise(() -> engine.process(TENANT_A, // GH-90000
                AepEngine.Event.of("order.placed", Map.of()).withIdempotencyKey("key-1 [GH-90000]")));

            assertThat(result.success()).isTrue(); // GH-90000
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // AEP-013: Tenant isolation
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("AEP-013: Tenant isolation [GH-90000]")
    class TenantIsolationTests {

        @Test
        @DisplayName("pattern registered for tenant-A is not visible to tenant-B [GH-90000]")
        void patternsShouldBeIsolatedByTenant() { // GH-90000
            engine = Aep.forTesting(); // GH-90000

            runPromise(() -> engine.registerPattern(TENANT_A, // GH-90000
                new AepEngine.PatternDefinition("A-only", null, AepEngine.PatternType.THRESHOLD, // GH-90000
                    Map.of("field", "value", "threshold", 10.0)))); // GH-90000

            List<AepEngine.Pattern> forA = runPromise(() -> engine.listPatterns(TENANT_A)); // GH-90000
            List<AepEngine.Pattern> forB = runPromise(() -> engine.listPatterns(TENANT_B)); // GH-90000

            assertThat(forA).hasSize(1); // GH-90000
            assertThat(forB).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("getPattern() for wrong tenant rejects cross-tenant access [GH-90000]")
        void getPatternShouldRespectTenant() { // GH-90000
            engine = Aep.forTesting(); // GH-90000

            AepEngine.Pattern p = runPromise(() -> engine.registerPattern(TENANT_A, // GH-90000
                new AepEngine.PatternDefinition("my-pattern", null, AepEngine.PatternType.CUSTOM, // GH-90000
                    Map.of()))); // GH-90000

            assertThatThrownBy(() -> runPromise(() -> engine.getPattern(TENANT_B, p.id()))) // GH-90000
                .isInstanceOf(AepTenantException.class) // GH-90000
                .hasMessageContaining("different tenant [GH-90000]");
        }

        @Test
        @DisplayName("deletePattern() for tenant-A does not affect tenant-B [GH-90000]")
        void deletePatternShouldOnelyAffectOwnerTenant() { // GH-90000
            engine = Aep.forTesting(); // GH-90000

            AepEngine.Pattern pA = runPromise(() -> engine.registerPattern(TENANT_A, // GH-90000
                new AepEngine.PatternDefinition("shared-name", null, AepEngine.PatternType.CUSTOM, // GH-90000
                    Map.of()))); // GH-90000
            AepEngine.Pattern pB = runPromise(() -> engine.registerPattern(TENANT_B, // GH-90000
                new AepEngine.PatternDefinition("shared-name", null, AepEngine.PatternType.CUSTOM, // GH-90000
                    Map.of()))); // GH-90000

            runPromise(() -> engine.deletePattern(TENANT_A, pA.id())); // GH-90000

            List<AepEngine.Pattern> forA = runPromise(() -> engine.listPatterns(TENANT_A)); // GH-90000
            List<AepEngine.Pattern> forB = runPromise(() -> engine.listPatterns(TENANT_B)); // GH-90000

            assertThat(forA).isEmpty(); // GH-90000
            assertThat(forB).hasSize(1).extracting(AepEngine.Pattern::id).containsOnly(pB.id()); // GH-90000
        }

        @Test
        @DisplayName("pattern detection does not fire across tenants [GH-90000]")
        void detectionShouldBeIsolatedByTenant() { // GH-90000
            engine = Aep.forTesting(); // GH-90000

            runPromise(() -> engine.registerPattern(TENANT_A, // GH-90000
                new AepEngine.PatternDefinition("A-pattern", null, AepEngine.PatternType.THRESHOLD, // GH-90000
                    Map.of("field", "level", "threshold", 5.0)))); // GH-90000

            // Process a matching event but for TENANT_B — pattern is not visible
            AepEngine.ProcessingResult result = runPromise(() -> engine.process( // GH-90000
                TENANT_B,
                new AepEngine.Event("sensor.reading", Map.of("level", 99.0), Map.of(), Instant.now()) // GH-90000
            ));

            assertThat(result.detections()).isEmpty(); // GH-90000
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // AEP-014: Pipeline execution
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("AEP-014: Pipeline execution [GH-90000]")
    class PipelineTests {

        @Test
        @DisplayName("submitPipeline() completes without exception for empty pipeline [GH-90000]")
        void shouldHandleEmptyPipeline() { // GH-90000
            engine = Aep.forTesting(); // GH-90000

            engine.submitPipeline(TENANT_A, new AepEngine.Pipeline( // GH-90000
                "pipe-1", "empty-pipeline", List.of() // GH-90000
            ));
            // No exception == pass
        }

        @Test
        @DisplayName("submitPipeline() registers a pattern from register_pattern step [GH-90000]")
        void shouldRegisterPatternViaStep() { // GH-90000
            engine = Aep.forTesting(); // GH-90000

            engine.submitPipeline(TENANT_A, new AepEngine.Pipeline( // GH-90000
                "pipe-2", "setup-pipeline",
                List.of(new AepEngine.PipelineStep("step-1", "register_pattern", Map.of( // GH-90000
                    "name", "pipeline-threshold",
                    "patternType", "THRESHOLD",
                    "field", "score",
                    "threshold", 70.0
                )))
            ));

            List<AepEngine.Pattern> patterns = runPromise(() -> engine.listPatterns(TENANT_A)); // GH-90000
            assertThat(patterns).hasSize(1); // GH-90000
            assertThat(patterns.get(0).name()).isEqualTo("pipeline-threshold [GH-90000]");
        }

        @Test
        @DisplayName("submitPipeline() continues after an unknown step type [GH-90000]")
        void shouldContinueAfterUnknownStepType() { // GH-90000
            engine = Aep.forTesting(); // GH-90000

            // register_pattern is a known step; custom_unknown is not
            engine.submitPipeline(TENANT_A, new AepEngine.Pipeline( // GH-90000
                "pipe-3", "mixed-pipeline",
                List.of( // GH-90000
                    new AepEngine.PipelineStep("step-1", "custom_unknown_step", Map.of("key", "val")), // GH-90000
                    new AepEngine.PipelineStep("step-2", "register_pattern", Map.of( // GH-90000
                        "name", "after-unknown", "patternType", "CUSTOM"))
                )
            ));

            List<AepEngine.Pattern> patterns = runPromise(() -> engine.listPatterns(TENANT_A)); // GH-90000
            assertThat(patterns).hasSize(1); // GH-90000
            assertThat(patterns.get(0).name()).isEqualTo("after-unknown [GH-90000]");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // AEP-016: Sequence ordering
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("AEP-016: Sequence pattern timestamp ordering [GH-90000]")
    class SequenceOrderingTests {

        @Test
        @DisplayName("sequence detection fires when events arrive in correct temporal order [GH-90000]")
        void shouldDetectSequenceInOrder() { // GH-90000
            engine = Aep.forTesting(); // GH-90000

            runPromise(() -> engine.registerPattern(TENANT_A, // GH-90000
                new AepEngine.PatternDefinition("checkout-flow", null, AepEngine.PatternType.SEQUENCE, // GH-90000
                    Map.of("expectedTypes", List.of("cart.add", "checkout.start", "payment.complete"), // GH-90000
                           "correlationField", "orderId"))));

            Instant t0 = Instant.parse("2026-01-01T00:00:00Z [GH-90000]");

            runPromise(() -> engine.process(TENANT_A, // GH-90000
                new AepEngine.Event("cart.add", Map.of("orderId", "ord-1"), Map.of(), t0))); // GH-90000
            runPromise(() -> engine.process(TENANT_A, // GH-90000
                new AepEngine.Event("checkout.start", Map.of("orderId", "ord-1"), Map.of(), // GH-90000
                    t0.plusSeconds(5)))); // GH-90000
            AepEngine.ProcessingResult result = runPromise(() -> engine.process(TENANT_A, // GH-90000
                new AepEngine.Event("payment.complete", Map.of("orderId", "ord-1"), Map.of(), // GH-90000
                    t0.plusSeconds(10)))); // GH-90000

            assertThat(result.detections()).hasSize(1); // GH-90000
            assertThat(result.detections().get(0).patternName()).isEqualTo("checkout-flow [GH-90000]");
        }

        @Test
        @DisplayName("out-of-order event is ignored, sequence does not fire [GH-90000]")
        void shouldIgnoreOutOfOrderEvent() { // GH-90000
            engine = Aep.forTesting(); // GH-90000

            runPromise(() -> engine.registerPattern(TENANT_A, // GH-90000
                new AepEngine.PatternDefinition("two-step", null, AepEngine.PatternType.SEQUENCE, // GH-90000
                    Map.of("expectedTypes", List.of("step.one", "step.two"), // GH-90000
                           "correlationField", "userId"))));

            Instant t10 = Instant.parse("2026-01-01T00:00:10Z [GH-90000]");
            Instant t5  = Instant.parse("2026-01-01T00:00:05Z [GH-90000]");

            // step.one arrives at t10
            runPromise(() -> engine.process(TENANT_A, // GH-90000
                new AepEngine.Event("step.one", Map.of("userId", "u1"), Map.of(), t10))); // GH-90000
            // step.two arrives at t5 — BEFORE step.one, should be ignored
            AepEngine.ProcessingResult result = runPromise(() -> engine.process(TENANT_A, // GH-90000
                new AepEngine.Event("step.two", Map.of("userId", "u1"), Map.of(), t5))); // GH-90000

            assertThat(result.detections()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("events without a real correlation key do not share sequence state [GH-90000]")
        void shouldNotUseGlobalFallbackForMissingCorrelationKey() { // GH-90000
            engine = Aep.forTesting(); // GH-90000

            runPromise(() -> engine.registerPattern(TENANT_A, // GH-90000
                new AepEngine.PatternDefinition("anonymous-two-step", null, AepEngine.PatternType.SEQUENCE, // GH-90000
                    Map.of("expectedTypes", List.of("step.one", "step.two"), // GH-90000
                           "correlationField", "missingField"))));

            runPromise(() -> engine.process(TENANT_A, // GH-90000
                new AepEngine.Event("step.one", Map.of("other", "a"), Map.of(), Instant.now()))); // GH-90000
            AepEngine.ProcessingResult result = runPromise(() -> engine.process(TENANT_A, // GH-90000
                new AepEngine.Event("step.two", Map.of("other", "b"), Map.of(), Instant.now().plusSeconds(1)))); // GH-90000

            assertThat(result.detections()).isEmpty(); // GH-90000
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // AEP-017: Subscriber fault tolerance
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("AEP-017: Subscriber exception isolation [GH-90000]")
    class SubscriberFaultToleranceTests {

        @Test
        @DisplayName("exception in one subscriber does not prevent other subscribers from receiving detection [GH-90000]")
        void exceptingSubscriberShouldNotBlockOthers() { // GH-90000
            engine = Aep.forTesting(); // GH-90000

            AepEngine.Pattern pattern = runPromise(() -> engine.registerPattern(TENANT_A, // GH-90000
                new AepEngine.PatternDefinition("high-value", null, AepEngine.PatternType.THRESHOLD, // GH-90000
                    Map.of("field", "amount", "threshold", 100.0)))); // GH-90000

            List<String> received = new ArrayList<>(); // GH-90000

            // First subscriber throws
            engine.subscribe(TENANT_A, pattern.id(), // GH-90000
                detection -> { throw new RuntimeException("subscriber failure [GH-90000]"); });
            // Second subscriber should still get called
            engine.subscribe(TENANT_A, pattern.id(), // GH-90000
                detection -> received.add(detection.patternId())); // GH-90000

            AepEngine.ProcessingResult result = runPromise(() -> engine.process(TENANT_A, // GH-90000
                new AepEngine.Event("payment", Map.of("amount", 500.0), Map.of(), Instant.now()))); // GH-90000

            assertThat(result.success()).isTrue(); // GH-90000
            assertThat(received).containsExactly(pattern.id()); // GH-90000
        }

        @Test
        @DisplayName("process() itself succeeds even if all subscribers throw [GH-90000]")
        void shouldSucceedEvenIfAllSubscribersThrow() { // GH-90000
            engine = Aep.forTesting(); // GH-90000

            AepEngine.Pattern p = runPromise(() -> engine.registerPattern(TENANT_A, // GH-90000
                new AepEngine.PatternDefinition("threshold-p", null, AepEngine.PatternType.THRESHOLD, // GH-90000
                    Map.of("field", "val", "threshold", 0.0)))); // GH-90000

            engine.subscribe(TENANT_A, p.id(), // GH-90000
                detection -> { throw new IllegalStateException("crash! [GH-90000]"); });

            AepEngine.ProcessingResult result = runPromise(() -> engine.process(TENANT_A, // GH-90000
                new AepEngine.Event("metric", Map.of("val", 1.0), Map.of(), Instant.now()))); // GH-90000

            assertThat(result.success()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("cancelled subscription no longer receives detections [GH-90000]")
        void cancelledSubscriptionShouldNotReceive() { // GH-90000
            engine = Aep.forTesting(); // GH-90000

            AepEngine.Pattern p = runPromise(() -> engine.registerPattern(TENANT_A, // GH-90000
                new AepEngine.PatternDefinition("threshold-p2", null, AepEngine.PatternType.THRESHOLD, // GH-90000
                    Map.of("field", "score", "threshold", 1.0)))); // GH-90000

            AtomicInteger count = new AtomicInteger(); // GH-90000
            AepEngine.Subscription sub = engine.subscribe(TENANT_A, p.id(), // GH-90000
                d -> count.incrementAndGet()); // GH-90000

            AepEngine.Event matchingEvent =
                new AepEngine.Event("metric", Map.of("score", 99.0), Map.of(), Instant.now()); // GH-90000

            runPromise(() -> engine.process(TENANT_A, matchingEvent)); // GH-90000
            sub.cancel(); // GH-90000
            assertThat(sub.isCancelled()).isTrue(); // GH-90000

            runPromise(() -> engine.process(TENANT_A, matchingEvent)); // GH-90000

            assertThat(count.get()).isEqualTo(1); // only the first process fires // GH-90000
        }
    }
}
