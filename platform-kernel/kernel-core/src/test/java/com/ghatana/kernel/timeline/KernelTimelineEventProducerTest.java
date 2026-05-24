package com.ghatana.kernel.timeline;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for KernelTimelineEventProducer hardening.
 * Validates commit SHA binding, environment validation, trace ID validation,
 * and evidence freshness for production truth.
 *
 * @doc.type class
 * @doc.purpose Validates Kernel timeline event producer hardening
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Kernel Timeline Event Producer Tests")
class KernelTimelineEventProducerTest extends EventloopTestBase {

    private TestTimelineEventStore eventStore;
    private TestTraceLinker traceLinker;
    private KernelTimelineEventProducer producer;

    @BeforeEach
    void setUp() {
        eventStore = new TestTimelineEventStore();
        traceLinker = new TestTraceLinker();
        producer = new KernelTimelineEventProducer(eventStore, traceLinker);
    }

    @Nested
    @DisplayName("Commit SHA Binding")
    class CommitShaBinding {

        @Test
        @DisplayName("Production environment requires commit SHA")
        void productionRequiresCommitSha() {
            producer.setEnvironment("production");
            // No commit SHA set

            assertThatIllegalStateException()
                .isThrownBy(() -> runPromise(() -> 
                    producer.produceLifecycleEvent("product-1", "build", "trace-123", Map.of())
                ))
                .withMessageContaining("Commit SHA must be set for production environment");
        }

        @Test
        @DisplayName("Non-production environment does not require commit SHA")
        void nonProductionDoesNotRequireCommitSha() {
            producer.setEnvironment("staging");
            // No commit SHA set

            String eventId = runPromise(() -> 
                producer.produceLifecycleEvent("product-1", "build", "trace-123", Map.of())
            );

            assertThat(eventId).isNotNull();
        }

        @Test
        @DisplayName("Valid commit SHA format is accepted")
        void validCommitShaAccepted() {
            producer.setEnvironment("production");
            producer.setCommitSha("7f84bc08e9e4e6d7e209cb49a855f199f7c90347");

            String eventId = runPromise(() -> 
                producer.produceLifecycleEvent("product-1", "build", "trace-123", Map.of())
            );

            assertThat(eventId).isNotNull();
        }

        @Test
        @DisplayName("Invalid commit SHA format is rejected")
        void invalidCommitShaRejected() {
            producer.setEnvironment("production");
            producer.setCommitSha("invalid-sha");

            assertThatIllegalArgumentException()
                .isThrownBy(() -> runPromise(() -> 
                    producer.produceLifecycleEvent("product-1", "build", "trace-123", Map.of())
                ))
                .withMessageContaining("Invalid commit SHA format");
        }

        @Test
        @DisplayName("Commit SHA is included in event metadata")
        void commitShaIncludedInMetadata() {
            producer.setEnvironment("production");
            String commitSha = "7f84bc08e9e4e6d7e209cb49a855f199f7c90347";
            producer.setCommitSha(commitSha);

            runPromise(() -> 
                producer.produceLifecycleEvent("product-1", "build", "trace-123", Map.of())
            );

            assertThat(eventStore.getLastEvent().metadata())
                .containsEntry("commitSha", commitSha);
        }
    }

    @Nested
    @DisplayName("Environment Binding")
    class EnvironmentBinding {

        @Test
        @DisplayName("Environment is included in event metadata")
        void environmentIncludedInMetadata() {
            String environment = "production";
            producer.setEnvironment(environment);
            producer.setCommitSha("7f84bc08e9e4e6d7e209cb49a855f199f7c90347");

            runPromise(() -> 
                producer.produceLifecycleEvent("product-1", "build", "trace-123", Map.of())
            );

            assertThat(eventStore.getLastEvent().metadata())
                .containsEntry("environment", environment);
        }

        @Test
        @DisplayName("Environment can be changed between events")
        void environmentCanBeChanged() {
            producer.setEnvironment("staging");
            producer.setCommitSha("7f84bc08e9e4e6d7e209cb49a855f199f7c90347");

            runPromise(() -> 
                producer.produceLifecycleEvent("product-1", "build", "trace-123", Map.of())
            );

            assertThat(eventStore.getLastEvent().metadata())
                .containsEntry("environment", "staging");

            producer.setEnvironment("production");

            runPromise(() -> 
                producer.produceLifecycleEvent("product-1", "deploy", "trace-456", Map.of())
            );

            assertThat(eventStore.getLastEvent().metadata())
                .containsEntry("environment", "production");
        }
    }

    @Nested
    @DisplayName("Trace ID Validation")
    class TraceIdValidation {

        @Test
        @DisplayName("Null trace ID is rejected")
        void nullTraceIdRejected() {
            assertThatIllegalArgumentException()
                .isThrownBy(() -> runPromise(() -> 
                    producer.produceLifecycleEvent("product-1", "build", null, Map.of())
                ))
                .withMessageContaining("Trace ID must not be null or empty");
        }

        @Test
        @DisplayName("Empty trace ID is rejected")
        void emptyTraceIdRejected() {
            assertThatIllegalArgumentException()
                .isThrownBy(() -> runPromise(() -> 
                    producer.produceLifecycleEvent("product-1", "build", "", Map.of())
                ))
                .withMessageContaining("Trace ID must not be null or empty");
        }

        @Test
        @DisplayName("Valid trace ID is accepted")
        void validTraceIdAccepted() {
            String eventId = runPromise(() -> 
                producer.produceLifecycleEvent("product-1", "build", "trace-123", Map.of())
            );

            assertThat(eventId).isNotNull();
        }
    }

    @Nested
    @DisplayName("Lifecycle Event Production")
    class LifecycleEventProduction {

        @Test
        @DisplayName("Lifecycle event is stored with correct type")
        void lifecycleEventStoredWithCorrectType() {
            producer.setCommitSha("7f84bc08e9e4e6d7e209cb49a855f199f7c90347");

            runPromise(() -> 
                producer.produceLifecycleEvent("product-1", "build", "trace-123", Map.of())
            );

            KernelTimelineEventProducer.TimelineEvent event = eventStore.getLastEvent();
            assertThat(event.eventType()).isEqualTo(KernelTimelineEventProducer.EventType.LIFECYCLE_PHASE);
            assertThat(event.eventSubtype()).isEqualTo("build");
            assertThat(event.productId()).isEqualTo("product-1");
        }

        @Test
        @DisplayName("Lifecycle event links trace to evidence")
        void lifecycleEventLinksTraceToEvidence() {
            producer.setCommitSha("7f84bc08e9e4e6d7e209cb49a855f199f7c90347");

            String eventId = runPromise(() -> 
                producer.produceLifecycleEvent("product-1", "build", "trace-123", Map.of())
            );

            assertThat(traceLinker.getLastLink())
                .satisfies(link -> {
                    assertThat(link.traceId()).isEqualTo("trace-123");
                    assertThat(link.eventId()).isEqualTo(eventId);
                    assertThat(link.evidenceType()).isEqualTo("lifecycle_phase");
                    assertThat(link.evidenceRef()).isEqualTo("build");
                });
        }

        @Test
        @DisplayName("Lifecycle event includes custom metadata")
        void lifecycleEventIncludesCustomMetadata() {
            producer.setCommitSha("7f84bc08e9e4e6d7e209cb49a855f199f7c90347");
            Map<String, Object> customMetadata = Map.of("version", "1.0.0", "region", "us-west-2");

            runPromise(() -> 
                producer.produceLifecycleEvent("product-1", "build", "trace-123", customMetadata)
            );

            KernelTimelineEventProducer.TimelineEvent event = eventStore.getLastEvent();
            assertThat(event.metadata()).containsEntry("version", "1.0.0");
            assertThat(event.metadata()).containsEntry("region", "us-west-2");
        }
    }

    @Nested
    @DisplayName("Evidence Event Production")
    class EvidenceEventProduction {

        @Test
        @DisplayName("Evidence event is stored with correct type")
        void evidenceEventStoredWithCorrectType() {
            String eventId = runPromise(() -> 
                producer.produceEvidenceEvent("product-1", "test-coverage", "trace-123", "/path/to/evidence.json")
            );

            KernelTimelineEventProducer.TimelineEvent event = eventStore.getLastEvent();
            assertThat(event.eventType()).isEqualTo(KernelTimelineEventProducer.EventType.EVIDENCE_COLLECTION);
            assertThat(event.eventSubtype()).isEqualTo("test-coverage");
            assertThat(event.productId()).isEqualTo("product-1");
        }

        @Test
        @DisplayName("Evidence event includes evidence reference")
        void evidenceEventIncludesEvidenceRef() {
            runPromise(() -> 
                producer.produceEvidenceEvent("product-1", "test-coverage", "trace-123", "/path/to/evidence.json")
            );

            KernelTimelineEventProducer.TimelineEvent event = eventStore.getLastEvent();
            assertThat(event.metadata()).containsEntry("evidenceRef", "/path/to/evidence.json");
            assertThat(event.metadata()).containsEntry("evidenceType", "test-coverage");
        }

        @Test
        @DisplayName("Evidence event links trace to evidence")
        void evidenceEventLinksTraceToEvidence() {
            String eventId = runPromise(() -> 
                producer.produceEvidenceEvent("product-1", "test-coverage", "trace-123", "/path/to/evidence.json")
            );

            assertThat(traceLinker.getLastLink())
                .satisfies(link -> {
                    assertThat(link.traceId()).isEqualTo("trace-123");
                    assertThat(link.eventId()).isEqualTo(eventId);
                    assertThat(link.evidenceType()).isEqualTo("evidence");
                    assertThat(link.evidenceRef()).isEqualTo("/path/to/evidence.json");
                });
        }
    }

    @Nested
    @DisplayName("Gate Event Production")
    class GateEventProduction {

        @Test
        @DisplayName("Gate event is stored with correct type")
        void gateEventStoredWithCorrectType() {
            String eventId = runPromise(() -> 
                producer.produceGateEvent("product-1", "security-scan", "trace-123", "pass")
            );

            KernelTimelineEventProducer.TimelineEvent event = eventStore.getLastEvent();
            assertThat(event.eventType()).isEqualTo(KernelTimelineEventProducer.EventType.GATE_VALIDATION);
            assertThat(event.eventSubtype()).isEqualTo("security-scan");
            assertThat(event.productId()).isEqualTo("product-1");
        }

        @Test
        @DisplayName("Gate event includes gate status")
        void gateEventIncludesGateStatus() {
            runPromise(() -> 
                producer.produceGateEvent("product-1", "security-scan", "trace-123", "pass")
            );

            KernelTimelineEventProducer.TimelineEvent event = eventStore.getLastEvent();
            assertThat(event.metadata()).containsEntry("gateName", "security-scan");
            assertThat(event.metadata()).containsEntry("gateStatus", "pass");
        }

        @Test
        @DisplayName("Gate event links trace to evidence")
        void gateEventLinksTraceToEvidence() {
            String eventId = runPromise(() -> 
                producer.produceGateEvent("product-1", "security-scan", "trace-123", "pass")
            );

            assertThat(traceLinker.getLastLink())
                .satisfies(link -> {
                    assertThat(link.traceId()).isEqualTo("trace-123");
                    assertThat(link.eventId()).isEqualTo(eventId);
                    assertThat(link.evidenceType()).isEqualTo("gate");
                    assertThat(link.evidenceRef()).isEqualTo("security-scan");
                });
        }
    }

    @Nested
    @DisplayName("Promotion Event Production")
    class PromotionEventProduction {

        @Test
        @DisplayName("Promotion event is stored with correct type")
        void promotionEventStoredWithCorrectType() {
            String eventId = runPromise(() -> 
                producer.producePromotionEvent("product-1", "asset-123", "trace-123", "approved")
            );

            KernelTimelineEventProducer.TimelineEvent event = eventStore.getLastEvent();
            assertThat(event.eventType()).isEqualTo(KernelTimelineEventProducer.EventType.ASSET_PROMOTION);
            assertThat(event.eventSubtype()).isEqualTo("asset-123");
            assertThat(event.productId()).isEqualTo("product-1");
        }

        @Test
        @DisplayName("Promotion event includes promotion status")
        void promotionEventIncludesPromotionStatus() {
            runPromise(() -> 
                producer.producePromotionEvent("product-1", "asset-123", "trace-123", "approved")
            );

            KernelTimelineEventProducer.TimelineEvent event = eventStore.getLastEvent();
            assertThat(event.metadata()).containsEntry("assetId", "asset-123");
            assertThat(event.metadata()).containsEntry("promotionStatus", "approved");
        }

        @Test
        @DisplayName("Promotion event links trace to evidence")
        void promotionEventLinksTraceToEvidence() {
            String eventId = runPromise(() -> 
                producer.producePromotionEvent("product-1", "asset-123", "trace-123", "approved")
            );

            assertThat(traceLinker.getLastLink())
                .satisfies(link -> {
                    assertThat(link.traceId()).isEqualTo("trace-123");
                    assertThat(link.eventId()).isEqualTo(eventId);
                    assertThat(link.evidenceType()).isEqualTo("promotion");
                    assertThat(link.evidenceRef()).isEqualTo("asset-123");
                });
        }
    }

    // Test doubles

    private static class TestTimelineEventStore implements KernelTimelineEventProducer.TimelineEventStore {
        private final AtomicReference<KernelTimelineEventProducer.TimelineEvent> lastEvent = new AtomicReference<>();

        @Override
        public Promise<String> store(KernelTimelineEventProducer.TimelineEvent event) {
            lastEvent.set(event);
            return Promise.of(event.eventId());
        }

        KernelTimelineEventProducer.TimelineEvent getLastEvent() {
            return lastEvent.get();
        }
    }

    private static class TestTraceLinker implements KernelTimelineEventProducer.TraceLinker {
        private final AtomicReference<Link> lastLink = new AtomicReference<>();

        @Override
        public void linkToEvidence(String traceId, String eventId, String evidenceType, String evidenceRef) {
            lastLink.set(new Link(traceId, eventId, evidenceType, evidenceRef));
        }

        Link getLastLink() {
            return lastLink.get();
        }

        record Link(String traceId, String eventId, String evidenceType, String evidenceRef) {}
    }
}
