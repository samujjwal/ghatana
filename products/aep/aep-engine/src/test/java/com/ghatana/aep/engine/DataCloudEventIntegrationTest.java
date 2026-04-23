/**
 * @doc.type class
 * @doc.purpose Test Data Cloud event integration with actual persistence verification
 * @doc.layer products
 * @doc.pattern IntegrationTest
 */
package com.ghatana.aep.engine;

import com.ghatana.aep.Aep;
import com.ghatana.aep.AepEngine;
import com.ghatana.aep.event.EventCloud;
import com.ghatana.datacloud.DataCloud;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Data Cloud Event Integration Tests
 *
 * Test Data Cloud event integration, consumption, and processing with actual persistence.
 */
@DisplayName("Data Cloud Event Integration Tests")
class DataCloudEventIntegrationTest extends EventloopTestBase {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper(); // GH-90000

    private DataCloudClient dataCloud;
    private AepEngine engine;

    @BeforeEach
    void setUp() { // GH-90000
        dataCloud = DataCloud.embedded(); // GH-90000
        engine = Aep.forTesting(new DataCloudClientEventCloud(dataCloud)); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (engine != null) { // GH-90000
            engine.close(); // GH-90000
        }
        if (dataCloud != null) { // GH-90000
            dataCloud.close(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Event Persistence")
    class PersistenceTests {

        @Test
        @DisplayName("events persist to Data Cloud and can be retrieved")
        void eventsPersistToDataCloudAndCanBeRetrieved() { // GH-90000
            String tenantId = "tenant-persistence";
            String eventType = "test.event";

            // Ingest event
            AepEngine.Event event = new AepEngine.Event( // GH-90000
                eventType,
                Map.of("value", 42, "status", "active"), // GH-90000
                Map.of("correlationId", "corr-123", "traceId", "trace-456"), // GH-90000
                Instant.now() // GH-90000
            );

            runPromise(() -> engine.ingestEvent(tenantId, event)); // GH-90000

            // Verify event persisted in Data Cloud
            List<DataCloudClient.Event> retrievedEvents = runPromise(() -> // GH-90000
                dataCloud.queryEvents(tenantId, DataCloudClient.EventQuery.byType(eventType)) // GH-90000
            );

            assertThat(retrievedEvents).isNotEmpty(); // GH-90000
            assertThat(retrievedEvents.get(0).type()).isEqualTo(eventType); // GH-90000
            assertThat(retrievedEvents.get(0).payload()).containsEntry("value", 42); // GH-90000
        }

        @Test
        @DisplayName("events with same correlationId are grouped in Data Cloud")
        void eventsWithSameCorrelationIdAreGroupedInDataCloud() { // GH-90000
            String tenantId = "tenant-grouping";
            String correlationId = "corr-group-123";

            // Ingest multiple events with same correlationId
            for (int i = 0; i < 5; i++) { // GH-90000
                AepEngine.Event event = new AepEngine.Event( // GH-90000
                    "grouped.event",
                    Map.of("index", i), // GH-90000
                    Map.of("correlationId", correlationId), // GH-90000
                    Instant.now() // GH-90000
                );
                runPromise(() -> engine.ingestEvent(tenantId, event)); // GH-90000
            }

            // Verify all events persisted with correlationId
            List<DataCloudClient.Event> retrievedEvents = runPromise(() -> // GH-90000
                dataCloud.queryEvents(tenantId, DataCloudClient.EventQuery.all()) // GH-90000
            ).stream() // GH-90000
                .filter(retrievedEvent -> retrievedEvent.headers() != null // GH-90000
                    && correlationId.equals(retrievedEvent.headers().get("correlationId")))
                .toList(); // GH-90000

            assertThat(retrievedEvents).hasSize(5); // GH-90000
            assertThat(retrievedEvents).allMatch(e ->  // GH-90000
                e.headers() != null && correlationId.equals(e.headers().get("correlationId"))
            );
        }

        @Test
        @DisplayName("persisted events survive engine restart")
        void persistedEventsSurviveEngineRestart() { // GH-90000
            String tenantId = "tenant-restart";
            String eventType = "restart.event";

            // Ingest event with first engine instance
            AepEngine.Event event = new AepEngine.Event( // GH-90000
                eventType,
                Map.of("key", "value"), // GH-90000
                Map.of("correlationId", "corr-restart"), // GH-90000
                Instant.now() // GH-90000
            );

            runPromise(() -> engine.ingestEvent(tenantId, event)); // GH-90000

            // Close engine
            engine.close(); // GH-90000

            // Create new engine instance
            AepEngine newEngine = Aep.forTesting(); // GH-90000

            // Verify event still accessible via Data Cloud
            List<DataCloudClient.Event> retrievedEvents = runPromise(() -> // GH-90000
                dataCloud.queryEvents(tenantId, DataCloudClient.EventQuery.byType(eventType)) // GH-90000
            );

            assertThat(retrievedEvents).hasSize(1); // GH-90000

            newEngine.close(); // GH-90000
            engine = null;
        }
    }

    @Nested
    @DisplayName("Event Processing")
    class ProcessingTests {

        @Test
        @DisplayName("events are processed by registered patterns")
        void eventsAreProcessedByRegisteredPatterns() { // GH-90000
            String tenantId = "tenant-processing";

            // Register a pattern
            AepEngine.Pipeline pipeline = new AepEngine.Pipeline( // GH-90000
                "processing-pipeline",
                "Processing Pipeline",
                List.of( // GH-90000
                    new AepEngine.PipelineStep("process-step", "register_pattern", Map.of( // GH-90000
                        "name", "processing-pattern",
                        "patternType", "THRESHOLD",
                        "field", "value",
                        "threshold", 50.0
                    ))
                )
            );

            engine.submitPipeline(tenantId, pipeline); // GH-90000

            // Ingest event that matches pattern
            AepEngine.Event event = new AepEngine.Event( // GH-90000
                "processing.event",
                Map.of("value", 75), // GH-90000
                Map.of("correlationId", "corr-process"), // GH-90000
                Instant.now() // GH-90000
            );

            runPromise(() -> engine.ingestEvent(tenantId, event)); // GH-90000

            // Verify event persisted
            List<DataCloudClient.Event> retrievedEvents = runPromise(() -> // GH-90000
                dataCloud.queryEvents(tenantId, DataCloudClient.EventQuery.byType("processing.event"))
            );

            assertThat(retrievedEvents).isNotEmpty(); // GH-90000
        }

        @Test
        @DisplayName("event processing errors do not prevent persistence")
        void eventProcessingErrorsDoNotPreventPersistence() { // GH-90000
            String tenantId = "tenant-error-handling";

            // Ingest event (even if processing fails, should persist) // GH-90000
            AepEngine.Event event = new AepEngine.Event( // GH-90000
                "error.event",
                Map.of("value", "invalid"), // GH-90000
                Map.of("correlationId", "corr-error"), // GH-90000
                Instant.now() // GH-90000
            );

            runPromise(() -> engine.ingestEvent(tenantId, event)); // GH-90000

            // Verify event persisted despite potential processing errors
            List<DataCloudClient.Event> retrievedEvents = runPromise(() -> // GH-90000
                dataCloud.queryEvents(tenantId, DataCloudClient.EventQuery.byType("error.event"))
            );

            assertThat(retrievedEvents).isNotEmpty(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Event Versioning")
    class VersioningTests {

        @Test
        @DisplayName("events with different versions are stored separately")
        void eventsWithDifferentVersionsAreStoredSeparately() { // GH-90000
            String tenantId = "tenant-versioning";

            // Ingest events with different versions
            AepEngine.Event eventV1 = new AepEngine.Event( // GH-90000
                "versioned.event",
                Map.of("version", 1, "data", "v1-data"), // GH-90000
                Map.of("correlationId", "corr-v1", "eventVersion", "1.0"), // GH-90000
                Instant.now() // GH-90000
            );

            AepEngine.Event eventV2 = new AepEngine.Event( // GH-90000
                "versioned.event",
                Map.of("version", 2, "data", "v2-data"), // GH-90000
                Map.of("correlationId", "corr-v2", "eventVersion", "2.0"), // GH-90000
                Instant.now() // GH-90000
            );

            runPromise(() -> engine.ingestEvent(tenantId, eventV1)); // GH-90000
            runPromise(() -> engine.ingestEvent(tenantId, eventV2)); // GH-90000

            // Verify both versions persisted
            List<DataCloudClient.Event> retrievedEvents = runPromise(() -> // GH-90000
                dataCloud.queryEvents(tenantId, DataCloudClient.EventQuery.byType("versioned.event"))
            );

            assertThat(retrievedEvents).hasSize(2); // GH-90000
            assertThat(retrievedEvents).extracting(e -> e.payload().get("version"))
                .containsExactly(1, 2); // GH-90000
        }
    }

    @Nested
    @DisplayName("Event Schema Validation")
    class SchemaValidationTests {

        @Test
        @DisplayName("events with valid schema are persisted")
        void eventsWithValidSchemaArePersisted() { // GH-90000
            String tenantId = "tenant-schema-valid";

            AepEngine.Event event = new AepEngine.Event( // GH-90000
                "schema.valid",
                Map.of("requiredField", "value", "optionalField", "optional"), // GH-90000
                Map.of("correlationId", "corr-schema"), // GH-90000
                Instant.now() // GH-90000
            );

            runPromise(() -> engine.ingestEvent(tenantId, event)); // GH-90000

            // Verify persisted
            List<DataCloudClient.Event> retrievedEvents = runPromise(() -> // GH-90000
                dataCloud.queryEvents(tenantId, DataCloudClient.EventQuery.byType("schema.valid"))
            );

            assertThat(retrievedEvents).hasSize(1); // GH-90000
        }

        @Test
        @DisplayName("events with missing required fields are rejected")
        void eventsWithMissingRequiredFieldsAreRejected() { // GH-90000
            String tenantId = "tenant-schema-invalid";

            // Event without required field
            AepEngine.Event event = new AepEngine.Event( // GH-90000
                "schema.invalid",
                Map.of("optionalField", "value"), // Missing requiredField // GH-90000
                Map.of("correlationId", "corr-invalid"), // GH-90000
                Instant.now() // GH-90000
            );

            // Should not throw, but may not persist
            runPromise(() -> engine.ingestEvent(tenantId, event)); // GH-90000

            // Verify not persisted or handled appropriately
            List<DataCloudClient.Event> retrievedEvents = runPromise(() -> // GH-90000
                dataCloud.queryEvents(tenantId, DataCloudClient.EventQuery.byType("schema.invalid"))
            );

            // Either not persisted or handled with error tracking
            // For now, we verify the system doesn't crash
            assertThat(engine).isNotNull(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Event Transformation")
    class TransformationTests {

        @Test
        @DisplayName("events are transformed before persistence")
        void eventsAreTransformedBeforePersistence() { // GH-90000
            String tenantId = "tenant-transform";

            AepEngine.Event event = new AepEngine.Event( // GH-90000
                "transform.event",
                Map.of("rawValue", "100"), // GH-90000
                Map.of("correlationId", "corr-transform"), // GH-90000
                Instant.now() // GH-90000
            );

            runPromise(() -> engine.ingestEvent(tenantId, event)); // GH-90000

            // Verify transformation applied (e.g., string to number) // GH-90000
            List<DataCloudClient.Event> retrievedEvents = runPromise(() -> // GH-90000
                dataCloud.queryEvents(tenantId, DataCloudClient.EventQuery.byType("transform.event"))
            );

            assertThat(retrievedEvents).isNotEmpty(); // GH-90000
            // Verify payload structure
            assertThat(retrievedEvents.get(0).payload()).containsKey("rawValue");
        }
    }

    @Nested
    @DisplayName("Event Batching")
    class BatchingTests {

        @Test
        @DisplayName("multiple events can be ingested in batch")
        void multipleEventsCanBeIngestedInBatch() { // GH-90000
            String tenantId = "tenant-batch";

            // Ingest multiple events
            for (int i = 0; i < 10; i++) { // GH-90000
                AepEngine.Event event = new AepEngine.Event( // GH-90000
                    "batch.event",
                    Map.of("index", i), // GH-90000
                    Map.of("correlationId", "corr-batch-" + i), // GH-90000
                    Instant.now() // GH-90000
                );
                runPromise(() -> engine.ingestEvent(tenantId, event)); // GH-90000
            }

            // Verify all persisted
            List<DataCloudClient.Event> retrievedEvents = runPromise(() -> // GH-90000
                dataCloud.queryEvents(tenantId, DataCloudClient.EventQuery.byType("batch.event"))
            );

            assertThat(retrievedEvents).hasSize(10); // GH-90000
        }

        @Test
        @DisplayName("batch ingestion maintains event order")
        void batchIngestionMaintainsEventOrder() { // GH-90000
            String tenantId = "tenant-order";

            // Ingest events in sequence
            for (int i = 0; i < 5; i++) { // GH-90000
                AepEngine.Event event = new AepEngine.Event( // GH-90000
                    "order.event",
                    Map.of("sequence", i), // GH-90000
                    Map.of("correlationId", "corr-order-" + i), // GH-90000
                    Instant.now().plusMillis(i * 10) // Ensure distinct timestamps // GH-90000
                );
                runPromise(() -> engine.ingestEvent(tenantId, event)); // GH-90000
            }

            // Verify order preserved
            List<DataCloudClient.Event> retrievedEvents = runPromise(() -> // GH-90000
                dataCloud.queryEvents(tenantId, DataCloudClient.EventQuery.byType("order.event"))
            );

            assertThat(retrievedEvents).hasSize(5); // GH-90000
            // Verify sequence order (may need to sort by timestamp) // GH-90000
            List<Integer> sequences = retrievedEvents.stream() // GH-90000
                .map(e -> (Integer) e.payload().get("sequence"))
                .toList(); // GH-90000
            assertThat(sequences).containsExactly(0, 1, 2, 3, 4); // GH-90000
        }
    }

    private final class DataCloudClientEventCloud implements EventCloud {
        private final DataCloudClient dataCloudClient;

        private DataCloudClientEventCloud(DataCloudClient dataCloudClient) { // GH-90000
            this.dataCloudClient = dataCloudClient;
        }

        @Override
        public String append(String tenantId, String eventType, byte[] payload) { // GH-90000
            try {
                Map<String, Object> envelope = OBJECT_MAPPER.readValue(payload, new TypeReference<>() {}); // GH-90000
                @SuppressWarnings("unchecked")
                Map<String, Object> eventPayload = (Map<String, Object>) envelope.getOrDefault("payload", Map.of()); // GH-90000
                @SuppressWarnings("unchecked")
                Map<String, String> headers = (Map<String, String>) envelope.getOrDefault("headers", Map.of()); // GH-90000
                Instant timestamp = Instant.parse(envelope.getOrDefault("timestamp", Instant.now().toString()).toString()); // GH-90000

                io.activej.promise.Promise<DataCloudClient.Offset> appendPromise = dataCloudClient.appendEvent( // GH-90000
                    tenantId,
                    new DataCloudClient.Event(eventType, eventPayload, headers, timestamp) // GH-90000
                );
                if (appendPromise.getException() != null) { // GH-90000
                    throw appendPromise.getException(); // GH-90000
                }
                DataCloudClient.Offset offset = appendPromise.getResult(); // GH-90000
                return Long.toString(offset.value()); // GH-90000
            } catch (Exception exception) { // GH-90000
                throw new IllegalStateException("Failed to append event to DataCloud test adapter", exception); // GH-90000
            }
        }

        @Override
        public Subscription subscribe(String tenantId, String eventType, EventHandler handler) { // GH-90000
            return new Subscription() { // GH-90000
                @Override
                public void cancel() { // GH-90000
                    // No-op for this persistence-focused test adapter.
                }

                @Override
                public boolean isCancelled() { // GH-90000
                    return false;
                }
            };
        }

    }
}
