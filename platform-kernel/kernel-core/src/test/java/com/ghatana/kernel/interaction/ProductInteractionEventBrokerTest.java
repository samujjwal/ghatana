package com.ghatana.kernel.interaction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ProductInteractionEventBroker")
class ProductInteractionEventBrokerTest extends EventloopTestBase {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    @Test
    @DisplayName("publishes event to registered subscribers and writes evidence")
    void publishesEventToRegisteredSubscribersAndWritesEvidence() {
        RecordingEventEvidenceWriter evidenceWriter = new RecordingEventEvidenceWriter();
        RecordingSubscriber subscriber = new RecordingSubscriber("consumer-a");
        ProductInteractionEventBroker broker = ProductInteractionEventBroker.builder()
                .subscribe(subscriber)
                .evidenceWriter(evidenceWriter)
                .brokerMode(BrokerMode.TEST)
                .build();

        ProductInteractionEventOutcome outcome = runPromise(() -> broker.publish(baseEnvelope("event-success")));

        assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.SUCCEEDED);
        assertThat(outcome.evidenceRefs()).contains("kernel://interaction-events/event-success");
        assertThat(outcome.deliveredSubscriberIds()).containsExactly("consumer-a");
        assertThat(subscriber.events).hasSize(1);
        assertThat(evidenceWriter.records).hasSize(1);
        assertThat(broker.metrics().published()).isEqualTo(1);
        assertThat(broker.metrics().delivered()).isEqualTo(1);
        assertThat(broker.metrics().blocked()).isZero();
        assertThat(broker.metrics().maxLatencyMs()).isGreaterThanOrEqualTo(0L);
        assertThat(broker.metrics().averageLatencyMs()).isGreaterThanOrEqualTo(0.0D);
    }

    @Test
    @DisplayName("persists canonical event evidence to the local filesystem")
    void persistsCanonicalEventEvidenceToLocalFilesystem(@TempDir Path tempDir) throws Exception {
        FileProductInteractionEventEvidenceWriter evidenceWriter =
                new FileProductInteractionEventEvidenceWriter(tempDir, Runnable::run);
        ProductInteractionEventBroker broker = ProductInteractionEventBroker.builder()
                .subscribe(new RecordingSubscriber("consumer-a"))
                .evidenceWriter(evidenceWriter)
                .brokerMode(BrokerMode.TEST)
                .build();
        ProductInteractionEventEnvelope<TestEvent> envelope = baseEnvelope("event-file-evidence");

        ProductInteractionEventOutcome outcome = runPromise(() -> broker.publish(envelope));

        Path evidencePath = evidenceWriter.evidencePath(envelope);
        assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.SUCCEEDED);
        assertThat(Files.exists(evidencePath)).isTrue();

        JsonNode record = OBJECT_MAPPER.readTree(evidencePath.toFile());
        assertThat(record.path("schemaVersion").asText()).isEqualTo("1.0.0");
        assertThat(record.path("evidenceId").asText()).startsWith("interaction-event-evidence-");
        assertThat(record.path("manifestType").asText()).isEqualTo("interaction-event-evidence");
        assertThat(record.path("contractId").asText()).isEqualTo("kernel://interactions/test.event.v1");
        assertThat(record.path("contractVersion").asText()).isEqualTo("1.0.0");
        assertThat(record.path("providerProductId").asText()).isEqualTo("provider-product");
        assertThat(record.path("consumerProductIds")).hasSize(1);
        assertThat(record.path("consumerProductIds").get(0).asText()).isEqualTo("consumer-product");
        assertThat(record.path("mode").asText()).isEqualTo("event-publish");
        assertThat(record.path("topic").asText()).isEqualTo("kernel.product.event");
        assertThat(record.path("tenantId").asText()).isEqualTo("tenant-1");
        assertThat(record.path("workspaceId").asText()).isEqualTo("workspace-1");
        assertThat(record.path("productUnitId").asText()).isEqualTo("provider-product");
        assertThat(record.path("runId").asText()).isEqualTo("run-1");
        assertThat(record.path("correlationId").asText()).isEqualTo("corr-1");
        assertThat(record.path("publishedAt").asText()).isEqualTo("2026-05-21T00:00:00Z");
        assertThat(record.path("requestedAt").asText()).isEqualTo("2026-05-21T00:00:00Z");
        assertThat(record.path("completedAt").asText()).isNotBlank();
        assertThat(record.path("status").asText()).isEqualTo("succeeded");
        assertThat(record.path("policyDecision").asText()).isEqualTo("allowed");
        assertThat(record.path("evidenceRefs").get(0).asText())
                .isEqualTo("kernel://interaction-events/event-file-evidence");
        assertThat(record.path("deliveredSubscriberIds").get(0).asText()).isEqualTo("consumer-a");
    }

    @Test
    @DisplayName("blocks event when tenant scope is missing")
    void blocksEventWhenTenantScopeIsMissing() {
        ProductInteractionEventBroker broker = ProductInteractionEventBroker.builder()
                .subscribe(new RecordingSubscriber("consumer-a"))
                .brokerMode(BrokerMode.TEST)
                .build();

        ProductInteractionEventOutcome outcome = runPromise(() -> broker.publish(new ProductInteractionEventEnvelope<>(
                "1.0.0",
                "event-missing-tenant",
                "kernel://interactions/test.event.v1",
                "1.0.0",
                "provider-product",
                List.of("consumer-product"),
                "provider-product",
                "",
                "workspace-1",
                "run-1",
                "corr-1",
                Instant.parse("2026-05-21T00:00:00Z"),
                Map.of("purpose", "test"),
                "kernel.product.event",
                new TestEvent("created"))));

        assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.BLOCKED);
        assertThat(outcome.reasonCode()).isEqualTo("product_interaction.tenant_required");
        assertThat(broker.metrics().blocked()).isEqualTo(1);
        assertThat(broker.metrics().published()).isEqualTo(1);
        assertThat(broker.metrics().averageLatencyMs()).isGreaterThanOrEqualTo(0.0D);
    }

    @Test
    @DisplayName("blocks event when policy denies publication")
    void blocksEventWhenPolicyDeniesPublication() {
        ProductInteractionEventBroker broker = ProductInteractionEventBroker.builder()
                .subscribe(new RecordingSubscriber("consumer-a"))
                .policyEvaluator(envelope -> ProductInteractionPolicyDecision.denied("product_interaction.policy_denied"))
                .brokerMode(BrokerMode.TEST)
                .build();

        ProductInteractionEventOutcome outcome = runPromise(() -> broker.publish(baseEnvelope("event-denied")));

        assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.BLOCKED);
        assertThat(outcome.reasonCode()).isEqualTo("product_interaction.policy_denied");
    }

    @Test
    @DisplayName("default policy requires event purpose")
    void defaultPolicyRequiresEventPurpose() {
        ProductInteractionEventBroker broker = ProductInteractionEventBroker.builder()
                .subscribe(new RecordingSubscriber("consumer-a"))
                .brokerMode(BrokerMode.TEST)
                .build();

        ProductInteractionEventOutcome outcome = runPromise(() -> broker.publish(new ProductInteractionEventEnvelope<>(
                "1.0.0",
                "event-purpose-required",
                "kernel://interactions/test.event.v1",
                "1.0.0",
                "provider-product",
                List.of("consumer-product"),
                "provider-product",
                "tenant-1",
                "workspace-1",
                "run-1",
                "corr-1",
                Instant.parse("2026-05-21T00:00:00Z"),
                Map.of(),
                "kernel.product.event",
                new TestEvent("created"))));

        assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.BLOCKED);
        assertThat(outcome.reasonCode()).isEqualTo("product_interaction.purpose_required");
    }

    @Test
    @DisplayName("blocks event when no subscriber is registered")
    void blocksEventWhenNoSubscriberIsRegistered() {
        ProductInteractionEventBroker broker = ProductInteractionEventBroker.builder()
                .brokerMode(BrokerMode.TEST)
                .build();

        ProductInteractionEventOutcome outcome = runPromise(() -> broker.publish(baseEnvelope("event-no-subscriber")));

        assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.BLOCKED);
        assertThat(outcome.reasonCode()).isEqualTo("product_interaction.event_handler_unavailable");
    }

    @Test
    @DisplayName("blocks event when subscriber delivery fails")
    void blocksEventWhenSubscriberDeliveryFails() {
        ProductInteractionEventBroker broker = ProductInteractionEventBroker.builder()
                .subscribe(new FailingSubscriber("consumer-a"))
                .brokerMode(BrokerMode.TEST)
                .build();

        ProductInteractionEventOutcome outcome = runPromise(() -> broker.publish(baseEnvelope("event-failed")));

        assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.BLOCKED);
        assertThat(outcome.reasonCode()).isEqualTo("product_interaction.event_delivery_failed");
    }

    @Test
    @DisplayName("converts evidence writer failures into blocked outcomes")
    void convertsEvidenceWriterFailuresIntoBlockedOutcomes() {
        ProductInteractionEventBroker broker = ProductInteractionEventBroker.builder()
                .subscribe(new RecordingSubscriber("consumer-a"))
                .evidenceWriter((envelope, outcome) -> Promise.ofException(new IllegalStateException("evidence unavailable")))
                .brokerMode(BrokerMode.TEST)
                .build();

        ProductInteractionEventOutcome outcome = runPromise(() -> broker.publish(baseEnvelope("event-evidence-failed")));

        assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.BLOCKED);
        assertThat(outcome.reasonCode()).isEqualTo("product_interaction.evidence_persistence_failed");
        assertThat(broker.metrics().evidenceFailures()).isEqualTo(1);
    }

    @Test
    @DisplayName("records event latency metrics within the local interaction SLO")
    void recordsEventLatencyMetricsWithinLocalSlo() {
        ProductInteractionEventBroker broker = ProductInteractionEventBroker.builder()
                .subscribe(new RecordingSubscriber("consumer-a"))
                .brokerMode(BrokerMode.TEST)
                .build();

        for (int index = 0; index < 20; index++) {
            int eventIndex = index;
            ProductInteractionEventOutcome outcome = runPromise(() ->
                    broker.publish(baseEnvelope("event-performance-" + eventIndex)));
            assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.SUCCEEDED);
        }

        ProductInteractionEventBrokerMetrics metrics = broker.metrics();
        assertThat(metrics.published()).isEqualTo(20);
        assertThat(metrics.delivered()).isEqualTo(20);
        assertThat(metrics.blocked()).isZero();
        assertThat(metrics.maxLatencyMs()).isLessThan(500L);
        assertThat(metrics.averageLatencyMs()).isLessThanOrEqualTo(metrics.maxLatencyMs());
    }

    @Test
    @DisplayName("rejects duplicate subscriber registration for the same topic")
    void rejectsDuplicateSubscriberRegistrationForSameTopic() {
        ProductInteractionEventBroker.Builder builder = ProductInteractionEventBroker.builder()
                .subscribe(new RecordingSubscriber("consumer-a"))
                .brokerMode(BrokerMode.TEST);

        assertThatThrownBy(() -> builder.subscribe(new RecordingSubscriber("consumer-a")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("consumer-a");
    }

    // P0-04: Parallel dispatch and retry tests
    @Test
    @DisplayName("P0-04: dispatches events to multiple subscribers in parallel")
    void dispatchesEventsToMultipleSubscribersInParallel() {
        RecordingSubscriber subscriberA = new RecordingSubscriber("consumer-a");
        RecordingSubscriber subscriberB = new RecordingSubscriber("consumer-b");
        ProductInteractionEventBroker broker = ProductInteractionEventBroker.builder()
                .subscribe(subscriberA)
                .subscribe(subscriberB)
                .brokerMode(BrokerMode.TEST)
                .build();

        ProductInteractionEventOutcome outcome = runPromise(() -> broker.publish(baseEnvelope("event-parallel")));

        assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.SUCCEEDED);
        assertThat(outcome.deliveredSubscriberIds()).containsExactlyInAnyOrder("consumer-a", "consumer-b");
        assertThat(subscriberA.events).hasSize(1);
        assertThat(subscriberB.events).hasSize(1);
        assertThat(broker.metrics().delivered()).isEqualTo(2);
    }

    @Test
    @DisplayName("P0-04: tracks subscriber timeout metrics")
    void tracksSubscriberTimeoutMetrics() {
        RecordingSubscriber slowSubscriber = new RecordingSubscriber("consumer-a") {
            @Override
            public Promise<Void> handle(ProductInteractionEventEnvelope<TestEvent> envelope) {
                return Promise.ofCallback(cb -> {
                    // Simulate slow handler
                });
            }
        };
        ProductInteractionEventBroker broker = ProductInteractionEventBroker.builder()
                .subscribe(slowSubscriber)
                .brokerMode(BrokerMode.TEST)
                .build();

        // The slow subscriber will timeout and be counted
        ProductInteractionEventOutcome outcome = runPromise(() -> broker.publish(baseEnvelope("event-timeout")));

        // Should fail due to timeout
        assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.BLOCKED);
        assertThat(broker.metrics().subscriberTimeouts()).isGreaterThan(0);
    }

    @Test
    @DisplayName("P0-04: configures max retries for failed subscribers")
    void configuresMaxRetriesForFailedSubscribers() {
        FailingSubscriber failingSubscriber = new FailingSubscriber("consumer-a");
        ProductInteractionEventBroker broker = ProductInteractionEventBroker.builder()
                .subscribe(failingSubscriber)
                .maxRetries(2)
                .brokerMode(BrokerMode.TEST)
                .build();

        ProductInteractionEventOutcome outcome = runPromise(() -> broker.publish(baseEnvelope("event-retry")));

        assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.BLOCKED);
        assertThat(broker.metrics().subscriberRetries()).isGreaterThan(0);
    }

    @Test
    @DisplayName("P0-04: configures subscriber timeout")
    void configuresSubscriberTimeout() {
        RecordingSubscriber subscriber = new RecordingSubscriber("consumer-a");
        ProductInteractionEventBroker broker = ProductInteractionEventBroker.builder()
                .subscribe(subscriber)
                .subscriberTimeout(Duration.ofSeconds(60))
                .brokerMode(BrokerMode.TEST)
                .build();

        ProductInteractionEventOutcome outcome = runPromise(() -> broker.publish(baseEnvelope("event-timeout-config")));

        assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.SUCCEEDED);
        assertThat(broker.metrics().delivered()).isEqualTo(1);
    }

    private static ProductInteractionEventEnvelope<TestEvent> baseEnvelope(String eventId) {
        return new ProductInteractionEventEnvelope<>(
                "1.0.0",
                eventId,
                "kernel://interactions/test.event.v1",
                "1.0.0",
                "provider-product",
                List.of("consumer-product"),
                "provider-product",
                "tenant-1",
                "workspace-1",
                "run-1",
                "corr-1",
                Instant.parse("2026-05-21T00:00:00Z"),
                Map.of("purpose", "test"),
                "kernel.product.event",
                new TestEvent("created"));
    }

    private record TestEvent(String action) {
    }

    private static class RecordingSubscriber implements ProductInteractionEventHandler<TestEvent> {
        private final String subscriberId;
        private final ConcurrentLinkedQueue<ProductInteractionEventEnvelope<TestEvent>> events = new ConcurrentLinkedQueue<>();

        private RecordingSubscriber(String subscriberId) {
            this.subscriberId = subscriberId;
        }

        @Override
        public String subscriberId() {
            return subscriberId;
        }

        @Override
        public String topic() {
            return "kernel.product.event";
        }

        @Override
        public Class<TestEvent> eventType() {
            return TestEvent.class;
        }

        @Override
        public Promise<Void> handle(ProductInteractionEventEnvelope<TestEvent> envelope) {
            events.add(envelope);
            return Promise.complete();
        }
    }

    private static final class FailingSubscriber extends RecordingSubscriber {
        private FailingSubscriber(String subscriberId) {
            super(subscriberId);
        }

        @Override
        public Promise<Void> handle(ProductInteractionEventEnvelope<TestEvent> envelope) {
            return Promise.ofException(new IllegalStateException("subscriber unavailable"));
        }
    }

    private static final class RecordingEventEvidenceWriter implements ProductInteractionEventEvidenceWriter {
        private final ConcurrentLinkedQueue<ProductInteractionEventOutcome> records = new ConcurrentLinkedQueue<>();

        @Override
        public Promise<Void> write(ProductInteractionEventEnvelope<?> envelope, ProductInteractionEventOutcome outcome) {
            records.add(outcome);
            return Promise.complete();
        }
    }
}
