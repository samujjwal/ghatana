package com.ghatana.services.featurestore;

import com.ghatana.aiplatform.featurestore.Feature;
import com.ghatana.aiplatform.featurestore.FeatureStoreService;
import com.ghatana.core.event.cloud.EventCloud;
import com.ghatana.core.event.cloud.EventCloud.EventConsumer;
import com.ghatana.core.event.cloud.EventCloud.EventEnvelope;
import com.ghatana.core.event.cloud.EventCloud.Selection;
import com.ghatana.core.event.cloud.EventCloud.StartAtLatest;
import com.ghatana.core.event.cloud.EventStream;
import com.ghatana.core.event.cloud.InMemoryEventCloud;
import com.ghatana.platform.types.identity.Identifier;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.MetricsCollectorFactory;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * EventCloud tailing service for real-time feature ingestion.
 *
 * <p>Subscribes to one or more EventCloud tenants, extracts ML feature vectors
 * from incoming events, and writes them to the Feature Store (Redis + PostgreSQL)
 * via {@link FeatureStoreService}.
 *
 * <h3>Configuration (environment variables)</h3>
 * <table>
 *   <tr><th>Variable</th><th>Default</th><th>Description</th></tr>
 *   <tr><td>FEATURE_INGEST_MODE</td><td>inmemory</td><td>{@code inmemory} or {@code postgres}</td></tr>
 *   <tr><td>FEATURE_INGEST_DB_URL</td><td>—</td><td>JDBC URL (postgres mode)</td></tr>
 *   <tr><td>FEATURE_INGEST_DB_USER</td><td>—</td><td>DB user (postgres mode)</td></tr>
 *   <tr><td>FEATURE_INGEST_DB_PASSWORD</td><td>—</td><td>DB password (postgres mode)</td></tr>
 *   <tr><td>FEATURE_INGEST_TENANTS</td><td>default</td><td>Comma-separated tenant IDs to subscribe</td></tr>
 *   <tr><td>FEATURE_INGEST_BATCH_SIZE</td><td>100</td><td>Backpressure demand per request</td></tr>
 *   <tr><td>FEATURE_INGEST_RETRY_DELAY_MS</td><td>5000</td><td>Delay before re-subscribing on error</td></tr>
 * </table>
 *
 * <h3>Performance targets</h3>
 * <ul>
 *   <li>Throughput: 10 000 events / second sustained</li>
 *   <li>Feature-store write latency: &lt;10 ms p99</li>
 *   <li>Backpressure: auto-throttle when feature store is slow</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Real-time feature ingestion from EventCloud into FeatureStore
 * @doc.layer product
 * @doc.pattern Pipeline
 */
public class FeatureStoreIngestLauncher {

    private static final Logger log = LoggerFactory.getLogger(FeatureStoreIngestLauncher.class);

    // ── Configuration keys ────────────────────────────────────────────────
    private static final String ENV_MODE           = "FEATURE_INGEST_MODE";
    private static final String ENV_DB_URL         = "FEATURE_INGEST_DB_URL";
    private static final String ENV_DB_USER        = "FEATURE_INGEST_DB_USER";
    private static final String ENV_DB_PASSWORD    = "FEATURE_INGEST_DB_PASSWORD";
    private static final String ENV_TENANTS        = "FEATURE_INGEST_TENANTS";
    private static final String ENV_BATCH_SIZE     = "FEATURE_INGEST_BATCH_SIZE";
    private static final String ENV_RETRY_DELAY_MS = "FEATURE_INGEST_RETRY_DELAY_MS";

    private static final int    DEFAULT_BATCH_SIZE     = 100;
    private static final long   DEFAULT_RETRY_DELAY_MS = 5_000L;

    private final EventCloud         eventCloud;
    private final FeatureStoreService featureStore;
    private final MetricsCollector   metrics;
    private final List<TenantId>     tenants;
    private final int                batchSize;
    private final long               retryDelayMs;
    private final AtomicBoolean      running = new AtomicBoolean(false);

    /**
     * Creates a launcher with all required dependencies injected.
     *
     * @param eventCloud   EventCloud subscription source
     * @param featureStore destination feature store
     * @param metrics      observability collector
     * @param tenants      tenant IDs to subscribe to
     * @param batchSize    number of events to request per backpressure pull
     * @param retryDelayMs milliseconds to wait before re-subscribing after an error
     */
    public FeatureStoreIngestLauncher(
            EventCloud eventCloud,
            FeatureStoreService featureStore,
            MetricsCollector metrics,
            List<TenantId> tenants,
            int batchSize,
            long retryDelayMs) {
        this.eventCloud   = eventCloud;
        this.featureStore = featureStore;
        this.metrics      = metrics;
        this.tenants      = tenants;
        this.batchSize    = batchSize;
        this.retryDelayMs = retryDelayMs;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    /**
     * Starts the ingestion loop on the provided eventloop.
     * Returns immediately; ingestion runs asynchronously.
     *
     * @param eventloop ActiveJ eventloop to schedule work on
     */
    public void start(Eventloop eventloop) {
        if (!running.compareAndSet(false, true)) {
            log.warn("FeatureStoreIngestLauncher already running — ignoring duplicate start()");
            return;
        }
        for (TenantId tenant : tenants) {
            scheduleSubscription(eventloop, tenant);
        }
        log.info("[feature-ingest] started — tenants={} batchSize={}", tenants, batchSize);
    }

    /**
     * Gracefully stops all active subscriptions.
     */
    public void stop() {
        running.set(false);
        log.info("[feature-ingest] shutdown requested");
    }

    // ── Subscription management ───────────────────────────────────────────

    /**
     * Opens an EventCloud subscription for {@code tenant} and wires the
     * event consumer. If the stream errors, reschedules with {@link #retryDelayMs}.
     */
    private void scheduleSubscription(Eventloop eventloop, TenantId tenant) {
        if (!running.get()) return;

        log.info("[feature-ingest] subscribing tenant={}", tenant.asString());
        metrics.incrementCounter("feature.ingest.subscription.attempts",
                "tenant", tenant.asString());

        try {
            EventStream stream = eventCloud.subscribe(
                    tenant,
                    Selection.all(),           // subscribe to all event types
                    new StartAtLatest());      // tail from head; replay handled separately

            stream.onEvent(new EventConsumer() {
                @Override
                public void onChunk(EventCloud.EventChunk chunk) {
                    for (EventEnvelope envelope : chunk.events()) {
                        processEnvelope(tenant, envelope);
                    }
                    // Request next batch (backpressure demand)
                    if (running.get()) {
                        stream.request(batchSize);
                    }
                }

                @Override
                public void onError(Throwable t) {
                    log.error("[feature-ingest] stream error tenant={}", tenant.asString(), t);
                    metrics.incrementCounter("feature.ingest.stream.errors",
                            "tenant", tenant.asString());
                    stream.close();
                    reschedule(eventloop, tenant);
                }

                @Override
                public void onComplete() {
                    log.info("[feature-ingest] stream completed tenant={} — rescheduling",
                            tenant.asString());
                    reschedule(eventloop, tenant);
                }
            });

            // Initial demand pull
            stream.request(batchSize);

        } catch (Exception ex) {
            log.error("[feature-ingest] failed to subscribe tenant={}", tenant.asString(), ex);
            metrics.incrementCounter("feature.ingest.subscription.errors",
                    "tenant", tenant.asString());
            reschedule(eventloop, tenant);
        }
    }

    /**
     * Schedules a re-subscription after {@link #retryDelayMs} milliseconds,
     * honouring the running flag so we don't restart after shutdown.
     */
    private void reschedule(Eventloop eventloop, TenantId tenant) {
        if (!running.get()) return;
        log.info("[feature-ingest] re-subscribing tenant={} in {}ms",
                tenant.asString(), retryDelayMs);
        Promise.ofBlocking(
                java.util.concurrent.Executors.newSingleThreadScheduledExecutor(),
                () -> { Thread.sleep(retryDelayMs); return null; })
            .whenResult(ignored -> {
                if (running.get()) scheduleSubscription(eventloop, tenant);
            });
    }

    // ── Feature extraction and ingestion ─────────────────────────────────

    /**
     * Extracts feature vectors from a single EventCloud envelope and writes
     * them to the Feature Store. Errors are logged and counted but never
     * propagate — the subscription loop must remain alive.
     */
    private void processEnvelope(TenantId tenant, EventEnvelope envelope) {
        long start = System.nanoTime();
        metrics.incrementCounter("feature.ingest.events.received",
                "tenant", tenant.asString());

        try {
            Map<String, Object> payload = envelope.record().payload();
            List<Feature> features = extractFeatures(envelope.record().entityId(), payload);

            for (Feature feature : features) {
                try {
                    featureStore.ingest(tenant.asString(), feature);
                    metrics.incrementCounter("feature.ingest.features.written",
                            "tenant", tenant.asString(),
                            "feature", feature.getName());
                } catch (Exception ex) {
                    log.warn("[feature-ingest] failed to write feature={} tenant={}",
                            feature.getName(), tenant.asString(), ex);
                    metrics.incrementCounter("feature.ingest.features.errors",
                            "tenant", tenant.asString(),
                            "feature", feature.getName());
                }
            }

            long durationNs = System.nanoTime() - start;
            metrics.recordTimer("feature.ingest.processing.duration",
                    durationNs / 1_000_000,
                    "tenant", tenant.asString());

            log.debug("[feature-ingest] processed event id={} tenant={} features={}",
                    envelope.record().id(), tenant.asString(), features.size());

        } catch (Exception ex) {
            log.error("[feature-ingest] unhandled error processing event id={} tenant={}",
                    envelope.record().id(), tenant.asString(), ex);
            metrics.incrementCounter("feature.ingest.events.errors",
                    "tenant", tenant.asString());
        }
    }

    /**
     * Derives a set of numeric feature vectors from a raw event payload.
     * Numeric fields are stored directly; categorical fields are hashed.
     *
     * @param entityId  entity identifier for the feature record
     * @param payload   raw event payload map
     * @return immutable list of extracted {@link Feature} objects (never null)
     */
    static List<Feature> extractFeatures(String entityId, Map<String, Object> payload) {
        Instant now = Instant.now();
        var features = new java.util.ArrayList<Feature>();

        // ── Numeric pass-through ─────────────────────────────────────────
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            Object val = entry.getValue();
            double numeric;
            if (val instanceof Number n) {
                numeric = n.doubleValue();
            } else if (val instanceof String s) {
                // Use unsigned hash of string value as a categorical encoding
                numeric = (double) (s.hashCode() & 0x7FFFFFFF);
            } else if (val instanceof Boolean b) {
                numeric = b ? 1.0 : 0.0;
            } else {
                // Skip null / complex objects
                continue;
            }
            features.add(Feature.builder()
                    .name(sanitizeFeatureName(entry.getKey()))
                    .value(numeric)
                    .entityId(entityId != null ? entityId : Identifier.random().raw())
                    .timestamp(now)
                    .build());
        }

        // ── Derived time features ────────────────────────────────────────
        features.add(Feature.builder()
                .name("hour_of_day")
                .value((double) now.atZone(java.time.ZoneOffset.UTC).getHour())
                .entityId(entityId != null ? entityId : Identifier.random().raw())
                .timestamp(now)
                .build());
        features.add(Feature.builder()
                .name("day_of_week")
                .value((double) now.atZone(java.time.ZoneOffset.UTC).getDayOfWeek().getValue())
                .entityId(entityId != null ? entityId : Identifier.random().raw())
                .timestamp(now)
                .build());

        return java.util.Collections.unmodifiableList(features);
    }

    /**
     * Ensures feature names are lowercase alphanumeric-and-underscore only,
     * preventing injection into downstream ML pipelines.
     */
    private static String sanitizeFeatureName(String raw) {
        return raw.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
    }

    // ── Main / factory ────────────────────────────────────────────────────

    /**
     * Entry point. Reads configuration from environment, wires dependencies,
     * and runs the subscription loop on an ActiveJ {@link Eventloop}.
     *
     * <p>Registers a JVM shutdown hook for graceful termination.
     */
    public static void main(String[] args) {
        String mode           = env(ENV_MODE, "inmemory");
        String tenantsRaw     = env(ENV_TENANTS, "default");
        int    batchSize      = Integer.parseInt(env(ENV_BATCH_SIZE, String.valueOf(DEFAULT_BATCH_SIZE)));
        long   retryDelayMs   = Long.parseLong(env(ENV_RETRY_DELAY_MS, String.valueOf(DEFAULT_RETRY_DELAY_MS)));

        List<TenantId> tenants = Arrays.stream(tenantsRaw.split(","))
                .map(String::strip)
                .filter(s -> !s.isBlank())
                .map(TenantId::of)
                .toList();

        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        MetricsCollector metrics     = MetricsCollectorFactory.create(meterRegistry);

        // ── EventCloud ───────────────────────────────────────────────────
        EventCloud eventCloud;
        if ("postgres".equalsIgnoreCase(mode)) {
            // Production: delegate to a PostgresEventCloudAdapter (injected via
            // platform:java:event-cloud when the adapter module is available).
            // Until the adapter is published, fail fast with a clear message.
            throw new IllegalStateException(
                "FEATURE_INGEST_MODE=postgres requires PostgresEventCloudAdapter "
                + "from platform:java:event-cloud. Set FEATURE_INGEST_MODE=inmemory "
                + "for local development.");
        } else {
            log.warn("[feature-ingest] using InMemoryEventCloud — set FEATURE_INGEST_MODE=postgres for production");
            eventCloud = new InMemoryEventCloud();
        }

        // ── FeatureStoreService ──────────────────────────────────────────
        FeatureStoreService featureStore;
        String dbUrl = env(ENV_DB_URL, null);
        if (dbUrl != null) {
            HikariConfig hikari = new HikariConfig();
            hikari.setJdbcUrl(dbUrl);
            hikari.setUsername(env(ENV_DB_USER, "featureingest"));
            hikari.setPassword(env(ENV_DB_PASSWORD, ""));
            hikari.setMaximumPoolSize(10);
            hikari.setMinimumIdle(2);
            hikari.setConnectionTimeout(Duration.ofSeconds(5).toMillis());
            hikari.setIdleTimeout(Duration.ofMinutes(10).toMillis());
            hikari.setMaxLifetime(Duration.ofMinutes(30).toMillis());
            hikari.setPoolName("feature-ingest-pool");
            DataSource dataSource = new HikariDataSource(hikari);
            featureStore = new FeatureStoreService(dataSource, metrics);
            log.info("[feature-ingest] FeatureStoreService wired to PostgreSQL: {}", dbUrl);
        } else {
            // Null datasource → service will throw on ingest; caught per-feature
            log.warn("[feature-ingest] FEATURE_INGEST_DB_URL not set — feature writes will fail; set for production");
            featureStore = new FeatureStoreService(null, metrics);
        }

        // ── Launcher ────────────────────────────────────────────────────
        FeatureStoreIngestLauncher launcher = new FeatureStoreIngestLauncher(
                eventCloud, featureStore, metrics, tenants, batchSize, retryDelayMs);

        Eventloop eventloop = Eventloop.create();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("[feature-ingest] shutdown hook fired — stopping");
            launcher.stop();
            eventloop.breakEventloop();
        }, "feature-ingest-shutdown"));

        eventloop.post(() -> launcher.start(eventloop));

        log.info("[feature-ingest] eventloop starting — mode={} tenants={}", mode, tenants);
        eventloop.run();
        log.info("[feature-ingest] eventloop exited");
    }

    /** Reads an environment variable, returning {@code defaultValue} when absent. */
    private static String env(String key, String defaultValue) {
        String val = System.getenv(key);
        return (val != null && !val.isBlank()) ? val : defaultValue;
    }
}
