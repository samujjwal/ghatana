package com.ghatana.services.featurestore;

import com.ghatana.aiplatform.featurestore.Feature;
import com.ghatana.platform.types.identity.Identifier;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.MetricsCollectorFactory;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.time.Instant;
import java.util.Map;

/**
 * EventCloud tailing service for real-time feature ingestion.
 *
 * <p>
 * <b>Purpose</b><br>
 * Consumes events from EventCloud and ingests features into Feature Store for
 * ML pipelines: - Subscribes to EventCloud partitions - Extracts features from
 * events - Enriches features with context - Stores in Feature Store (Redis +
 * PostgreSQL)
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * // Start service
 * ./gradlew :products:shared-services:feature-store-ingest:run
 *
 * // Service automatically:
 * // 1. Subscribes to EventCloud
 * // 2. Processes incoming events
 * // 3. Extracts features
 * // 4. Stores in Feature Store
 * }</pre>
 *
 * <p>
 * <b>Architecture Role</b><br>
 * Real-time feature engineering pipeline. Bridges EventCloud and Feature Store.
 * Integrates with AI Platform for ML model serving.
 *
 * <p>
 * <b>Performance Targets</b><br>
 * - Throughput: 10k events/sec sustained - Latency: <10ms p99 from event to
 * feature store - Backpressure: Auto-throttle when feature store slow
 *
 * <p>
 * <b>Thread Safety</b><br>
 * Thread-safe - uses ActiveJ Eventloop for single-threaded async execution.
 *
 * @doc.type class
 * @doc.purpose Real-time feature ingestion from EventCloud
 * @doc.layer product
 * @doc.pattern Pipeline
 */
public class FeatureStoreIngestLauncher {

    private static final String EVENTCLOUD_SUBSCRIPTION = "feature-extraction";

    public static void main(String[] args) {
        // Initialize metrics
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        MetricsCollector metrics = MetricsCollectorFactory.create(meterRegistry);

        // Initialize feature store
        // Production: Wire FeatureStoreService from config (Redis + PostgreSQL backend)
        // FeatureStoreService featureStore = FeatureStoreService.create(config);
        System.out.println("FeatureStoreService not configured — using log-only mode");

        // Initialize EventCloud subscriber
        // Production: Wire EventCloud connection from config
        // EventCloudSubscriber subscriber = EventCloudSubscriber.create(config);
        System.out.println("EventCloud subscriber not configured — running mock ingestion");
        // Create eventloop
        Eventloop eventloop = Eventloop.create();

        eventloop.post(() -> {
            System.out.println("Feature Store Ingest Service starting...");

            // Production flow:
            // eventCloud.subscribe(tenantId, selection, startAt)
            //     .thenApply(stream -> processEventStream(stream, featureStore, metrics));
            startMockIngestion(metrics);
        });

        // Start eventloop
        eventloop.run();
    }

    /**
     * Processes event stream from EventCloud.
     *
     * GIVEN: EventCloud subscription stream WHEN: Events are received THEN:
     * Features are extracted and stored
     *
     * @param event raw event payload
     * @param metrics metrics collector
     */
    private static Promise<Void> processEventStream(
            Map<String, Object> event,
            MetricsCollector metrics
    ) {
        metrics.incrementCounter("featurestore.ingest.events_received");

        // 1. Extract features from event
        java.util.List<Feature> features = extractFeatures(event);
        metrics.incrementCounter("featurestore.ingest.features_extracted", "count", String.valueOf(features.size()));

        // 2. Log extracted features (production: store via FeatureStoreService)
        for (Feature feature : features) {
            System.out.printf("  Extracted feature: %s = %s (entity=%s)%n",
                    feature.getName(), feature.getValue(), feature.getEntityId());
        }

        // 3. Production: featureStore.ingest(features) with backpressure
        // featureStore.ingest(features)
        //     .whenException(ex -> metrics.incrementCounter("featurestore.ingest.errors"));

        return Promise.complete();
    }

    /**
     * Extracts features from an event.
     *
     * GIVEN: Event from EventCloud WHEN: extractFeatures() is called THEN: List
     * of Feature objects is returned
     *
     * @param event event data
     * @return list of extracted features
     */
    private static java.util.List<Feature> extractFeatures(Map<String, Object> event) {
        TenantId tenantId = TenantId.of(
                (String) event.getOrDefault("tenantId", "00000000-0000-0000-0000-000000000001"));
        Identifier entityId = Identifier.random();
        Instant now = Instant.now();
        String entityIdRaw = entityId.raw();

        return java.util.List.of(
                Feature.builder()
                        .name("transaction_amount")
                        .value((Double) event.getOrDefault("amount", 0.0))
                        .entityId(entityIdRaw)
                        .timestamp(now)
                        .build(),
                Feature.builder()
                        .name("user_history_score")
                        .value((Double) event.getOrDefault("historyScore", 0.5))
                        .entityId(entityIdRaw)
                        .timestamp(now)
                        .build(),
                Feature.builder()
                        .name("device_fingerprint_hash")
                        .value((double) ((String) event.getOrDefault("deviceFingerprint", "unknown")).hashCode())
                        .entityId(entityIdRaw)
                        .timestamp(now)
                        .build(),
                Feature.builder()
                        .name("hour_of_day")
                        .value((double) now.atZone(java.time.ZoneOffset.UTC).getHour())
                        .entityId(entityIdRaw)
                        .timestamp(now)
                        .build()
        );
    }

    /**
     * Mock ingestion for demo and integration testing.
     * Generates sample events and runs them through the feature extraction pipeline.
     */
    private static void startMockIngestion(MetricsCollector metrics) {
        System.out.println("Starting mock feature ingestion pipeline...");

        // Generate a sample event and process it through the pipeline
        Map<String, Object> sampleEvent = Map.of(
                "tenantId", "00000000-0000-0000-0000-000000000001",
                "type", "transaction",
                "amount", 149.99,
                "historyScore", 0.82,
                "deviceFingerprint", "abc123-device-fp"
        );

        System.out.println("Processing sample event: " + sampleEvent.get("type"));
        processEventStream(sampleEvent, metrics);

        System.out.println("Mock ingestion complete. "
                + "Connect EventCloud subscriber for production use.");
    }
}
