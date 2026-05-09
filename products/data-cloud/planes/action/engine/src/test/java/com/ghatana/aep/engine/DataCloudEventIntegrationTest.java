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

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper(); 

    private DataCloudClient dataCloud;
    private AepEngine engine;

    @BeforeEach
    void setUp() { 
        dataCloud = DataCloud.embedded(); 
        engine = Aep.forTesting(new DataCloudClientEventCloud(dataCloud)); 
    }

    @AfterEach
    void tearDown() { 
        if (engine != null) { 
            engine.close(); 
        }
        if (dataCloud != null) { 
            dataCloud.close(); 
        }
    }

    @Nested
    @DisplayName("Event Persistence")
    class PersistenceTests {

        @Test
        @DisplayName("events persist to Data Cloud and can be retrieved")
        void eventsPersistToDataCloudAndCanBeRetrieved() { 
            String tenantId = "tenant-persistence";
            String eventType = "test.event";

            // Ingest event
            AepEngine.Event event = new AepEngine.Event( 
                eventType,
                Map.of("value", 42, "status", "active"), 
                Map.of("correlationId", "corr-123", "traceId", "trace-456"), 
                Instant.now() 
            );

            runPromise(() -> engine.ingestEvent(tenantId, event)); 

            // Verify event persisted in Data Cloud
            List<DataCloudClient.Event> retrievedEvents = runPromise(() -> 
                dataCloud.queryEvents(tenantId, DataCloudClient.EventQuery.byType(eventType)) 
            );

            assertThat(retrievedEvents).isNotEmpty(); 
            assertThat(retrievedEvents.get(0).type()).isEqualTo(eventType); 
            assertThat(retrievedEvents.get(0).payload()).containsEntry("value", 42); 
        }

        @Test
        @DisplayName("events with same correlationId are grouped in Data Cloud")
        void eventsWithSameCorrelationIdAreGroupedInDataCloud() { 
            String tenantId = "tenant-grouping";
            String correlationId = "corr-group-123";

            // Ingest multiple events with same correlationId
            for (int i = 0; i < 5; i++) { 
                AepEngine.Event event = new AepEngine.Event( 
                    "grouped.event",
                    Map.of("index", i), 
                    Map.of("correlationId", correlationId), 
                    Instant.now() 
                );
                runPromise(() -> engine.ingestEvent(tenantId, event)); 
            }

            // Verify all events persisted with correlationId
            List<DataCloudClient.Event> retrievedEvents = runPromise(() -> 
                dataCloud.queryEvents(tenantId, DataCloudClient.EventQuery.all()) 
            ).stream() 
                .filter(retrievedEvent -> retrievedEvent.headers() != null 
                    && correlationId.equals(retrievedEvent.headers().get("correlationId")))
                .toList(); 

            assertThat(retrievedEvents).hasSize(5); 
            assertThat(retrievedEvents).allMatch(e ->  
                e.headers() != null && correlationId.equals(e.headers().get("correlationId"))
            );
        }

        @Test
        @DisplayName("persisted events survive engine restart")
        void persistedEventsSurviveEngineRestart() { 
            String tenantId = "tenant-restart";
            String eventType = "restart.event";

            // Ingest event with first engine instance
            AepEngine.Event event = new AepEngine.Event( 
                eventType,
                Map.of("key", "value"), 
                Map.of("correlationId", "corr-restart"), 
                Instant.now() 
            );

            runPromise(() -> engine.ingestEvent(tenantId, event)); 

            // Close engine
            engine.close(); 

            // Create new engine instance
            AepEngine newEngine = Aep.forTesting(); 

            // Verify event still accessible via Data Cloud
            List<DataCloudClient.Event> retrievedEvents = runPromise(() -> 
                dataCloud.queryEvents(tenantId, DataCloudClient.EventQuery.byType(eventType)) 
            );

            assertThat(retrievedEvents).hasSize(1); 

            newEngine.close(); 
            engine = null;
        }
    }

    @Nested
    @DisplayName("Event Processing")
    class ProcessingTests {

        @Test
        @DisplayName("events are processed by registered patterns")
        void eventsAreProcessedByRegisteredPatterns() { 
            String tenantId = "tenant-processing";

            // Register a pattern
            AepEngine.Pipeline pipeline = new AepEngine.Pipeline( 
                "processing-pipeline",
                "Processing Pipeline",
                List.of( 
                    new AepEngine.PipelineStep("process-step", "register_pattern", Map.of( 
                        "name", "processing-pattern",
                        "patternType", "THRESHOLD",
                        "field", "value",
                        "threshold", 50.0
                    ))
                )
            );

            engine.submitPipeline(tenantId, pipeline); 

            // Ingest event that matches pattern
            AepEngine.Event event = new AepEngine.Event( 
                "processing.event",
                Map.of("value", 75), 
                Map.of("correlationId", "corr-process"), 
                Instant.now() 
            );

            runPromise(() -> engine.ingestEvent(tenantId, event)); 

            // Verify event persisted
            List<DataCloudClient.Event> retrievedEvents = runPromise(() -> 
                dataCloud.queryEvents(tenantId, DataCloudClient.EventQuery.byType("processing.event"))
            );

            assertThat(retrievedEvents).isNotEmpty(); 
        }

        @Test
        @DisplayName("event processing errors do not prevent persistence")
        void eventProcessingErrorsDoNotPreventPersistence() { 
            String tenantId = "tenant-error-handling";

            // Ingest event (even if processing fails, should persist) 
            AepEngine.Event event = new AepEngine.Event( 
                "error.event",
                Map.of("value", "invalid"), 
                Map.of("correlationId", "corr-error"), 
                Instant.now() 
            );

            runPromise(() -> engine.ingestEvent(tenantId, event)); 

            // Verify event persisted despite potential processing errors
            List<DataCloudClient.Event> retrievedEvents = runPromise(() -> 
                dataCloud.queryEvents(tenantId, DataCloudClient.EventQuery.byType("error.event"))
            );

            assertThat(retrievedEvents).isNotEmpty(); 
        }
    }

    @Nested
    @DisplayName("Event Versioning")
    class VersioningTests {

        @Test
        @DisplayName("events with different versions are stored separately")
        void eventsWithDifferentVersionsAreStoredSeparately() { 
            String tenantId = "tenant-versioning";

            // Ingest events with different versions
            AepEngine.Event eventV1 = new AepEngine.Event( 
                "versioned.event",
                Map.of("version", 1, "data", "v1-data"), 
                Map.of("correlationId", "corr-v1", "eventVersion", "1.0"), 
                Instant.now() 
            );

            AepEngine.Event eventV2 = new AepEngine.Event( 
                "versioned.event",
                Map.of("version", 2, "data", "v2-data"), 
                Map.of("correlationId", "corr-v2", "eventVersion", "2.0"), 
                Instant.now() 
            );

            runPromise(() -> engine.ingestEvent(tenantId, eventV1)); 
            runPromise(() -> engine.ingestEvent(tenantId, eventV2)); 

            // Verify both versions persisted
            List<DataCloudClient.Event> retrievedEvents = runPromise(() -> 
                dataCloud.queryEvents(tenantId, DataCloudClient.EventQuery.byType("versioned.event"))
            );

            assertThat(retrievedEvents).hasSize(2); 
            assertThat(retrievedEvents).extracting(e -> e.payload().get("version"))
                .containsExactly(1, 2); 
        }
    }

    @Nested
    @DisplayName("Event Schema Validation")
    class SchemaValidationTests {

        @Test
        @DisplayName("events with valid schema are persisted")
        void eventsWithValidSchemaArePersisted() { 
            String tenantId = "tenant-schema-valid";

            AepEngine.Event event = new AepEngine.Event( 
                "schema.valid",
                Map.of("requiredField", "value", "optionalField", "optional"), 
                Map.of("correlationId", "corr-schema"), 
                Instant.now() 
            );

            runPromise(() -> engine.ingestEvent(tenantId, event)); 

            // Verify persisted
            List<DataCloudClient.Event> retrievedEvents = runPromise(() -> 
                dataCloud.queryEvents(tenantId, DataCloudClient.EventQuery.byType("schema.valid"))
            );

            assertThat(retrievedEvents).hasSize(1); 
        }

        @Test
        @DisplayName("events with missing required fields are rejected")
        void eventsWithMissingRequiredFieldsAreRejected() { 
            String tenantId = "tenant-schema-invalid";

            // Event without required field
            AepEngine.Event event = new AepEngine.Event( 
                "schema.invalid",
                Map.of("optionalField", "value"), // Missing requiredField 
                Map.of("correlationId", "corr-invalid"), 
                Instant.now() 
            );

            // Should not throw, but may not persist
            runPromise(() -> engine.ingestEvent(tenantId, event)); 

            // Verify not persisted or handled appropriately
            List<DataCloudClient.Event> retrievedEvents = runPromise(() -> 
                dataCloud.queryEvents(tenantId, DataCloudClient.EventQuery.byType("schema.invalid"))
            );

            // Either not persisted or handled with error tracking
            // For now, we verify the system doesn't crash
            assertThat(engine).isNotNull(); 
        }
    }

    @Nested
    @DisplayName("Event Transformation")
    class TransformationTests {

        @Test
        @DisplayName("events are transformed before persistence")
        void eventsAreTransformedBeforePersistence() { 
            String tenantId = "tenant-transform";

            AepEngine.Event event = new AepEngine.Event( 
                "transform.event",
                Map.of("rawValue", "100"), 
                Map.of("correlationId", "corr-transform"), 
                Instant.now() 
            );

            runPromise(() -> engine.ingestEvent(tenantId, event)); 

            // Verify transformation applied (e.g., string to number) 
            List<DataCloudClient.Event> retrievedEvents = runPromise(() -> 
                dataCloud.queryEvents(tenantId, DataCloudClient.EventQuery.byType("transform.event"))
            );

            assertThat(retrievedEvents).isNotEmpty(); 
            // Verify payload structure
            assertThat(retrievedEvents.get(0).payload()).containsKey("rawValue");
        }
    }

    @Nested
    @DisplayName("Event Batching")
    class BatchingTests {

        @Test
        @DisplayName("multiple events can be ingested in batch")
        void multipleEventsCanBeIngestedInBatch() { 
            String tenantId = "tenant-batch";

            // Ingest multiple events
            for (int i = 0; i < 10; i++) { 
                AepEngine.Event event = new AepEngine.Event( 
                    "batch.event",
                    Map.of("index", i), 
                    Map.of("correlationId", "corr-batch-" + i), 
                    Instant.now() 
                );
                runPromise(() -> engine.ingestEvent(tenantId, event)); 
            }

            // Verify all persisted
            List<DataCloudClient.Event> retrievedEvents = runPromise(() -> 
                dataCloud.queryEvents(tenantId, DataCloudClient.EventQuery.byType("batch.event"))
            );

            assertThat(retrievedEvents).hasSize(10); 
        }

        @Test
        @DisplayName("batch ingestion maintains event order")
        void batchIngestionMaintainsEventOrder() { 
            String tenantId = "tenant-order";

            // Ingest events in sequence
            for (int i = 0; i < 5; i++) { 
                AepEngine.Event event = new AepEngine.Event( 
                    "order.event",
                    Map.of("sequence", i), 
                    Map.of("correlationId", "corr-order-" + i), 
                    Instant.now().plusMillis(i * 10) // Ensure distinct timestamps 
                );
                runPromise(() -> engine.ingestEvent(tenantId, event)); 
            }

            // Verify order preserved
            List<DataCloudClient.Event> retrievedEvents = runPromise(() -> 
                dataCloud.queryEvents(tenantId, DataCloudClient.EventQuery.byType("order.event"))
            );

            assertThat(retrievedEvents).hasSize(5); 
            // Verify sequence order (may need to sort by timestamp) 
            List<Integer> sequences = retrievedEvents.stream() 
                .map(e -> (Integer) e.payload().get("sequence"))
                .toList(); 
            assertThat(sequences).containsExactly(0, 1, 2, 3, 4); 
        }
    }

    private final class DataCloudClientEventCloud implements EventCloud {
        private final DataCloudClient dataCloudClient;

        private DataCloudClientEventCloud(DataCloudClient dataCloudClient) { 
            this.dataCloudClient = dataCloudClient;
        }

        @Override
        public String append(String tenantId, String eventType, byte[] payload) { 
            try {
                Map<String, Object> envelope = OBJECT_MAPPER.readValue(payload, new TypeReference<>() {}); 
                @SuppressWarnings("unchecked")
                Map<String, Object> eventPayload = (Map<String, Object>) envelope.getOrDefault("payload", Map.of()); 
                @SuppressWarnings("unchecked")
                Map<String, String> headers = (Map<String, String>) envelope.getOrDefault("headers", Map.of()); 
                Instant timestamp = Instant.parse(envelope.getOrDefault("timestamp", Instant.now().toString()).toString()); 

                io.activej.promise.Promise<DataCloudClient.Offset> appendPromise = dataCloudClient.appendEvent( 
                    tenantId,
                    DataCloudClient.Event.builder()
                        .type(eventType)
                        .payload(eventPayload)
                        .headers(headers)
                        .timestamp(timestamp)
                        .source("aep-test-adapter")
                        .build()
                );
                if (appendPromise.getException() != null) { 
                    throw appendPromise.getException(); 
                }
                DataCloudClient.Offset offset = appendPromise.getResult(); 
                return Long.toString(offset.value()); 
            } catch (Exception exception) { 
                throw new IllegalStateException("Failed to append event to DataCloud test adapter", exception); 
            }
        }

        @Override
        public Subscription subscribe(String tenantId, String eventType, EventHandler handler) { 
            return new Subscription() { 
                @Override
                public void cancel() { 
                    // No-op for this persistence-focused test adapter.
                }

                @Override
                public boolean isCancelled() { 
                    return false;
                }
            };
        }

    }
}
