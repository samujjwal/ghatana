package com.ghatana.services.featurestore;

import com.ghatana.aiplatform.featurestore.Feature;
import com.ghatana.platform.types.identity.Identifier;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.MetricsCollectorFactory;
import io.activej.eventloop.Eventloop;
import io.activej.http.HttpMethod;
import io.activej.http.HttpResponse;
import io.activej.http.HttpServer;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * EventLogStore tailing service for real-time feature ingestion.
 *
 * <p>
 * <b>Purpose</b><br>
 * Consumes events from EventLogStore and ingests features into Feature Store for
 * ML pipelines: subscribes to EventLogStore partitions, extracts features from
 * events, enriches features with context, and stores in Feature Store (Redis +
 * PostgreSQL).
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * // Start service
 * ./gradlew :products:shared-services:feature-store-ingest:run
 * }</pre>
 *
 * <p>
 * <b>Health Check</b><br>
 * Exposes {@code GET /health} on the port configured by {@code FEATURE_STORE_HEALTH_PORT}
 * (default: 8087). Returns {@code {"status":"UP"}} when the service is running.
 *
 * <p>
 * <b>Architecture Role</b><br>
 * Real-time feature engineering pipeline. Bridges EventLogStore and Feature Store.
 * Integrates with AI Platform for ML model serving.
 *
 * <p>
 * <b>Performance Targets</b><br>
 * - Throughput: 10k events/sec sustained<br>
 * - Latency: &lt;10ms p99 from event to feature store<br>
 * - Backpressure: Auto-throttle when feature store slow
 *
 * <p>
 * <b>Thread Safety</b><br>
 * Thread-safe — uses ActiveJ Eventloop for single-threaded async execution.
 *
 * @doc.type class
 * @doc.purpose Real-time feature ingestion from EventLogStore
 * @doc.layer product
 * @doc.pattern Pipeline
 */
public class FeatureStoreIngestLauncher {

    private static final Logger logger = LoggerFactory.getLogger(FeatureStoreIngestLauncher.class);

    public static void main(String[] args) {
        // Initialize metrics
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        MetricsCollector metrics = MetricsCollectorFactory.create(meterRegistry);

        // Initialize feature store
        // Production: Wire FeatureStoreService from config (Redis + PostgreSQL backend)
        // FeatureStoreService featureStore = FeatureStoreService.create(config);
        logger.info("FeatureStoreService not configured — using log-only mode");

        // Initialize EventLogStore subscriber
        // Production: Wire EventLogStore connection from config
        // EventLogStoreSubscriber subscriber = EventLogStoreSubscriber.create(config);
        logger.info("EventLogStore subscriber not configured — running mock ingestion");

        // Create eventloop
        Eventloop eventloop = Eventloop.create();

        // Start health-check HTTP server so orchestration platforms can probe readiness
        int healthPort = Integer.parseInt(System.getenv().getOrDefault("FEATURE_STORE_HEALTH_PORT", "8087"));
        HttpServer healthServer = HttpServer.builder(eventloop, request -> {
            if (HttpMethod.GET.equals(request.getMethod()) && "/health".equals(request.getPath())) {
                return Promise.of(HttpResponse.ok200()
                        .withHeader(io.activej.http.HttpHeaders.CONTENT_TYPE, "application/json")
                        .withBody("{\"status\":\"UP\",\"service\":\"feature-store-ingest\"}".getBytes())
                        .build());
            }
            return Promise.of(HttpResponse.ofCode(404).build());
        }).withListenPort(healthPort).build();

        eventloop.post(() -> {
            logger.info("Feature Store Ingest Service starting...");

            try {
                healthServer.listen();
                logger.info("Health check endpoint available on port {}", healthPort);
            } catch (Exception e) {
                logger.error("Failed to start health check server on port {}", healthPort, e);
            }

            // Production flow:
            // eventLogStore.subscribe(tenantId, selection, startAt)
            //     .thenApply(stream -> processEventStream(stream, featureStore, metrics));
            startMockIngestion(metrics);
        });

        // Shutdown hook for graceful stop
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Feature Store Ingest Service shutting down...");
            healthServer.close();
        }));

        // Start eventloop
        eventloop.run();
    }

    /**
     * Processes event stream from EventLogStore.
     *
     * <p>Given an EventLogStore subscription stream, when events are received,
     * features are extracted and stored.
     *
     * @param event   raw event payload
     * @param metrics metrics collector
     * @return promise that completes once the event is processed
     */
    private static Promise<Void> processEventStream(
            Map<String, Object> event,
            MetricsCollector metrics
    ) {
        metrics.incrementCounter("featurestore.ingest.events_received");

        // 1. Extract features from event
        List<Feature> features = extractFeatures(event);
        metrics.incrementCounter("featurestore.ingest.features_extracted",
                "count", String.valueOf(features.size()));

        // 2. Log extracted features (production: store via FeatureStoreService)
        for (Feature feature : features) {
            logger.debug("Extracted feature: {} = {} (entity={})",
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
     * <p>Given an event from EventLogStore, when {@code extractFeatures()} is called,
     * a list of Feature objects is returned. Device fingerprints are hashed with
     * SHA-256 before storage to avoid retaining raw PII.
     *
     * @param event event data
     * @return list of extracted features
     */
    private static List<Feature> extractFeatures(Map<String, Object> event) {
        TenantId tenantId = TenantId.of(
                (String) event.getOrDefault("tenantId", "00000000-0000-0000-0000-000000000001"));
        Identifier entityId = Identifier.random();
        Instant now = Instant.now();
        String entityIdRaw = entityId.raw();

        // Hash the device fingerprint with SHA-256 before storing to avoid retaining raw PII.
        String rawFingerprint = (String) event.getOrDefault("deviceFingerprint", "unknown");
        double fingerprintHash = sha256AsDouble(rawFingerprint);

        return List.of(
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
                        .value(fingerprintHash)
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
     * Produces a stable double fingerprint by taking the first 8 bytes of a
     * SHA-256 digest and interpreting them as a big-endian {@code long}.
     * This is irreversible (unlike {@link String#hashCode()}) and avoids
     * storing raw PII.
     *
     * @param input the raw fingerprint string
     * @return a double derived from the SHA-256 digest
     */
    private static double sha256AsDouble(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            long bits = 0;
            for (int i = 0; i < 8; i++) {
                bits = (bits << 8) | (hash[i] & 0xFF);
            }
            return (double) bits;
        } catch (java.security.NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available in all JVMs
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Mock ingestion for demo and integration testing.
     * Generates sample events and runs them through the feature extraction pipeline.
     */
    private static void startMockIngestion(MetricsCollector metrics) {
        logger.info("Starting mock feature ingestion pipeline...");

        // Generate a sample event and process it through the pipeline
        Map<String, Object> sampleEvent = Map.of(
                "tenantId", "00000000-0000-0000-0000-000000000001",
                "type", "transaction",
                "amount", 149.99,
                "historyScore", 0.82,
                "deviceFingerprint", "abc123-device-fp"
        );

        logger.info("Processing sample event: {}", sampleEvent.get("type"));
        processEventStream(sampleEvent, metrics);

        logger.info("Mock ingestion complete. Connect EventLogStore subscriber for production use.");
    }
}

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
 * EventLogStore tailing service for real-time feature ingestion.
 *
 * <p>
 * <b>Purpose</b><br>
 * Consumes events from EventLogStore and ingests features into Feature Store for
 * ML pipelines: - Subscribes to EventLogStore partitions - Extracts features from
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
 * // 1. Subscribes to EventLogStore
 * // 2. Processes incoming events
 * // 3. Extracts features
 * // 4. Stores in Feature Store
 * }</pre>
 *
 * <p>
 * <b>Architecture Role</b><br>
 * Real-time feature engineering pipeline. Bridges EventLogStore and Feature Store.
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
 * @doc.purpose Real-time feature ingestion from EventLogStore
 * @doc.layer product
 * @doc.pattern Pipeline
 */
public class FeatureStoreIngestLauncher {

    public static void main(String[] args) {
        // Initialize metrics
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        MetricsCollector metrics = MetricsCollectorFactory.create(meterRegistry);

        // Initialize feature store
        // Production: Wire FeatureStoreService from config (Redis + PostgreSQL backend)
        // FeatureStoreService featureStore = FeatureStoreService.create(config);
        System.out.println("FeatureStoreService not configured — using log-only mode");

        // Initialize EventLogStore subscriber
        // Production: Wire EventLogStore connection from config
        // EventLogStoreSubscriber subscriber = EventLogStoreSubscriber.create(config);
        System.out.println("EventLogStore subscriber not configured — running mock ingestion");
        // Create eventloop
        Eventloop eventloop = Eventloop.create();

        eventloop.post(() -> {
            System.out.println("Feature Store Ingest Service starting...");

            // Production flow:
                        // eventLogStore.subscribe(tenantId, selection, startAt)
            //     .thenApply(stream -> processEventStream(stream, featureStore, metrics));
            startMockIngestion(metrics);
        });

        // Start eventloop
        eventloop.run();
    }

    /**
     * Processes event stream from EventLogStore.
     *
     * GIVEN: EventLogStore subscription stream WHEN: Events are received THEN:
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
     * GIVEN: Event from EventLogStore WHEN: extractFeatures() is called THEN: List
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
                + "Connect EventLogStore subscriber for production use.");
    }
}
