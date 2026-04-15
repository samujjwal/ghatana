package com.ghatana.services.featurestore;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.aiplatform.featurestore.MLFeature;
import com.ghatana.aiplatform.featurestore.FeatureStoreService;
import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.platform.domain.eventstore.TenantContext;
import com.ghatana.datacloud.storage.InMemoryEventLogStore;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.MetricsCollectorFactory;
import com.ghatana.platform.resilience.CircuitBreaker;
import com.ghatana.platform.resilience.DeadLetterQueue;
import com.ghatana.platform.types.identity.Identifier;
import com.ghatana.platform.types.identity.Offset;
import com.ghatana.datacloud.storage.WarmTierEventLogStore;
import com.ghatana.services.featurestore.config.FeatureIngestConfig;
import com.ghatana.services.featurestore.config.FeatureTransformSpec;
import com.ghatana.services.featurestore.exception.FeatureExtractionException;
import com.ghatana.services.featurestore.exception.FeatureIngestException;
import com.ghatana.services.featurestore.exception.FeatureStoreWriteException;
import io.activej.eventloop.Eventloop;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * EventLogStore tailing service for real-time feature ingestion.
 *
 * <p>Polls one or more EventLogStore tenants, extracts ML feature vectors
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
 *   <tr><td>FEATURE_INGEST_BATCH_SIZE</td><td>100</td><td>Number of events to read per poll</td></tr>
 *   <tr><td>FEATURE_INGEST_RETRY_DELAY_MS</td><td>5000</td><td>Delay before re-subscribing on error</td></tr>
 *   <tr><td>FEATURE_INGEST_POLL_DELAY_MS</td><td>1000</td><td>Delay between polls when no new events</td></tr>
 * </table>
 *
 * <h3>Performance targets</h3>
 * <ul>
 *   <li>Throughput: 10 000 events / second sustained</li>
 *   <li>Feature-store write latency: &lt;10 ms p99</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Real-time feature ingestion from EventLogStore into FeatureStore
 * @doc.layer product
 * @doc.pattern Pipeline
 */
public class FeatureStoreIngestLauncher {

    private static final Logger LOG = LoggerFactory.getLogger(FeatureStoreIngestLauncher.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final int  DEFAULT_BATCH_SIZE      = 100;
    private static final long DEFAULT_RETRY_DELAY_MS  = 5_000L;
    private static final long DEFAULT_POLL_DELAY_MS   = 1_000L;

    private final EventLogStore         eventLogStore;
    private final FeatureStoreService   featureStore;
    private final MetricsCollector      metrics;
    private final List<TenantId>        tenants;
    private final int                   batchSize;
    private final long                  retryDelayMs;
    private final long                  pollDelayMs;
    private final AtomicBoolean         running = new AtomicBoolean(false);
    /** Tracks the last-seen offset per tenant (as a numeric long). */
    private final Map<String, Long>     tenantOffsets = new ConcurrentHashMap<>();
    /**
     * Circuit breaker protecting FeatureStore writes. Opens after 10 consecutive
     * failures, resets after 30 seconds. Using sync execution since feature writes
     * are made outside the ActiveJ eventloop (blocking worker thread).
     */
    private final CircuitBreaker featureStoreCircuitBreaker;
    /**
     * Dead-letter queue for events whose feature extraction or write failed permanently
     * after all retries. Bounded to 50,000 entries with a 7-day TTL.
     */
    private final DeadLetterQueue deadLetterQueue;
    /**
     * YAML-configurable transform spec controlling event-type filtering and field selection.
     * Defaults to pass-through (all events, all fields) when not set.
     */
    private volatile FeatureTransformSpec transformSpec = FeatureTransformSpec.passThrough();
    /**
     * Single-thread scheduler for delayed poll rescheduling. Shared across all
     * tenants to avoid the per-call thread pool leak from creating a new executor
     * inside every {@link #reschedule} invocation.
     */
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "[feature-ingest]-scheduler");
        t.setDaemon(true);
        return t;
    });

    /**
     * Creates a launcher with all required dependencies injected.
     *
     * @param eventLogStore event log to poll
     * @param featureStore  destination feature store
     * @param metrics       observability collector
     * @param tenants       tenant IDs to subscribe to
     * @param batchSize     number of events to read per poll
     * @param retryDelayMs  milliseconds to wait before re-subscribing after an error
     * @param pollDelayMs   milliseconds to wait between polls when no new events arrive
     */
    public FeatureStoreIngestLauncher(
            EventLogStore eventLogStore,
            FeatureStoreService featureStore,
            MetricsCollector metrics,
            List<TenantId> tenants,
            int batchSize,
            long retryDelayMs,
            long pollDelayMs) {
        this.eventLogStore = eventLogStore;
        this.featureStore  = featureStore;
        this.metrics       = metrics;
        this.tenants       = tenants;
        this.batchSize     = batchSize;
        this.retryDelayMs  = retryDelayMs;
        this.pollDelayMs   = pollDelayMs;
        this.featureStoreCircuitBreaker = CircuitBreaker.builder("feature-store-writes")
            .failureThreshold(10)
            .resetTimeout(Duration.ofSeconds(30))
            .successThreshold(2)
            .build();
        this.deadLetterQueue = DeadLetterQueue.builder()
            .maxSize(50_000)
            .ttl(Duration.ofDays(7))
            .enableReplay(true)
            .build();
    }

    /**
     * Package-private constructor for tests only.
     *
     * <p>Accepts pre-built {@link CircuitBreaker} and {@link DeadLetterQueue}
     * so tests can configure them (e.g. already-open circuit breaker) without
     * relying on the scheduler-based threshold.
     */
    FeatureStoreIngestLauncher(
            EventLogStore eventLogStore,
            FeatureStoreService featureStore,
            MetricsCollector metrics,
            List<TenantId> tenants,
            int batchSize,
            long retryDelayMs,
            long pollDelayMs,
            CircuitBreaker circuitBreaker,
            DeadLetterQueue deadLetterQueue) {
        this.eventLogStore              = eventLogStore;
        this.featureStore               = featureStore;
        this.metrics                    = metrics;
        this.tenants                    = tenants;
        this.batchSize                  = batchSize;
        this.retryDelayMs               = retryDelayMs;
        this.pollDelayMs                = pollDelayMs;
        this.featureStoreCircuitBreaker = circuitBreaker;
        this.deadLetterQueue            = deadLetterQueue;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    /**
     * Starts the ingestion polling loops on the provided eventloop.
     * Returns immediately; ingestion runs asynchronously.
     *
     * @param eventloop ActiveJ eventloop to schedule work on
     */
    public void start(Eventloop eventloop) {
        if (!running.compareAndSet(false, true)) {
            LOG.warn("FeatureStoreIngestLauncher already running — ignoring duplicate start()");
            return;
        }
        for (TenantId tenant : tenants) {
            initTenantAndPoll(eventloop, tenant);
        }
        LOG.info("[feature-ingest] started — tenants={} batchSize={}", tenants, batchSize);
    }

    /**
     * Package-private — test use only.
     *
     * <p>Delegates directly to {@link #processEntry} so tests can verify
     * DLQ routing and circuit-breaker behaviour without starting the polling
     * loop.
     */
    void processEntryForTesting(TenantId tenant, EventLogStore.EventEntry entry) {
        processEntry(tenant, entry);
    }

    /**
     * Package-private — test use only.
     *
     * <p>Returns the dead-letter queue so tests can inspect stored entries.
     */
    DeadLetterQueue getDeadLetterQueueForTesting() {
        return deadLetterQueue;
    }

    /**
     * Gracefully stops all active polling loops.
     */
    public void stop() {
        running.set(false);
        scheduler.shutdownNow();
        LOG.info("[feature-ingest] shutdown requested");
    }

    /**
     * Applies the YAML-configurable feature transform spec.
     *
     * <p>When set, the spec controls which event types are processed and which payload
     * fields are materialized as features. Pass {@link FeatureTransformSpec#passThrough()}
     * to accept all events and fields (the default).
     *
     * @param spec non-null transform specification
     * @return this launcher (fluent)
     * @see FeatureTransformSpec
     */
    public FeatureStoreIngestLauncher withTransformSpec(FeatureTransformSpec spec) {
        if (spec == null) throw new IllegalArgumentException("transformSpec must not be null");
        this.transformSpec = spec;
        LOG.info("[feature-ingest] transform spec applied — eventTypes={} includeFields={} excludeFields={}",
                spec.getEventTypes(), spec.getIncludeFields(), spec.getExcludeFields());
        return this;
    }

    // ── Offset initialisation and poll scheduling ─────────────────────────

    /**
     * Fetches the current latest offset for {@code tenant} (StartAtLatest semantics),
     * stores it, and begins the poll loop.
     */
    private void initTenantAndPoll(Eventloop eventloop, TenantId tenant) {
        if (!running.get()) return;
        TenantContext tenantCtx = TenantContext.of(tenant.value());

        LOG.info("[feature-ingest] initialising offset for tenant={}", tenant.value());
        metrics.incrementCounter("feature.ingest.subscription.attempts", "tenant", tenant.value());

        eventLogStore.getLatestOffset(tenantCtx)
            .whenResult(latest -> {
                long startOffset = parseOffset(latest);
                tenantOffsets.put(tenant.value(), startOffset);
                LOG.info("[feature-ingest] tenant={} starting from offset={}", tenant.value(), startOffset);
                pollNext(eventloop, tenant, tenantCtx);
            })
            .whenException(e -> {
                LOG.error("[feature-ingest] failed to get initial offset tenant={}", tenant.value(), e);
                metrics.incrementCounter("feature.ingest.subscription.errors", "tenant", tenant.value());
                reschedule(eventloop, tenant, retryDelayMs);
            });
    }

    /**
     * Reads the next batch of events for {@code tenant} from the current offset,
     * processes them, advances the offset, then schedules the next poll.
     */
    private void pollNext(Eventloop eventloop, TenantId tenant, TenantContext tenantCtx) {
        if (!running.get()) return;

        long currentOffset = tenantOffsets.getOrDefault(tenant.value(), 0L);

        eventLogStore.read(tenantCtx, Offset.of(currentOffset), batchSize)
            .whenResult(entries -> {
                if (!entries.isEmpty()) {
                    long maxOffset = currentOffset - 1;
                    for (EventLogStore.EventEntry entry : entries) {
                        processEntry(tenant, entry);
                        // Advance offset using the embedded _x_dc_offset header written
                        // by WarmTierEventLogStore/KafkaEventLogStore.  This handles gaps
                        // in IDENTITY sequences correctly (Finding-4).
                        String rawOff = entry.headers().get("_x_dc_offset");
                        if (rawOff != null) {
                            try { maxOffset = Math.max(maxOffset, Long.parseLong(rawOff)); }
                            catch (NumberFormatException ignored) {}
                        }
                    }
                    // Fall back to size-based advancement (in-memory provider / legacy).
                    long nextOffset = maxOffset >= currentOffset
                            ? maxOffset + 1
                            : currentOffset + entries.size();
                    tenantOffsets.put(tenant.value(), nextOffset);
                    metrics.incrementCounter("feature.ingest.events.received",
                        "tenant", tenant.value(),
                        "batch_size", String.valueOf(entries.size()));
                }
                // back-off when idle to avoid busy-polling
                long delay = entries.isEmpty() ? pollDelayMs : 0L;
                reschedule(eventloop, tenant, delay);
            })
            .whenException(e -> {
                LOG.error("[feature-ingest] poll error tenant={}", tenant.value(), e);
                metrics.incrementCounter("feature.ingest.poll.errors", "tenant", tenant.value());
                reschedule(eventloop, tenant, retryDelayMs);
            });
    }

    private void reschedule(Eventloop eventloop, TenantId tenant, long delayMs) {
        if (!running.get()) return;
        if (delayMs > 0) {
            // Use the shared scheduler — avoids creating a new thread pool per invocation
            // (was a major thread pool leak with Executors.newSingleThreadExecutor() per call).
            scheduler.schedule(() -> {
                if (running.get()) {
                    eventloop.post(() -> {
                        TenantContext ctx = TenantContext.of(tenant.value());
                        pollNext(eventloop, tenant, ctx);
                    });
                }
            }, delayMs, TimeUnit.MILLISECONDS);
        } else {
            pollNext(eventloop, tenant, TenantContext.of(tenant.value()));
        }
    }

    // ── Feature extraction and ingestion ─────────────────────────────────

    /**
     * Extracts feature vectors from a single log entry and writes them to the
     * Feature Store. Uses a circuit breaker to protect against store unavailability.
     * Events that fail permanently are routed to the dead-letter queue.
     *
     * <p>Error classification:
     * <ul>
     *   <li>{@link FeatureExtractionException} — payload is malformed; sent to DLQ immediately.</li>
     *   <li>{@link FeatureStoreWriteException} — store write failed; circuit state determines routing.</li>
     *   <li>Fallback catch — unanticipated errors; counted but not swallowed.</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    private void processEntry(TenantId tenant, EventLogStore.EventEntry entry) {
        long start = System.nanoTime();
        String eventId = entry.eventId().toString();

        // ── Event-type filter (P3.3.1) ───────────────────────────────────────
        if (!transformSpec.acceptsEventType(entry.eventType())) {
            LOG.debug("[feature-ingest] skipping event type={} tenant={}", entry.eventType(), tenant.value());
            metrics.incrementCounter("feature.ingest.events.filtered",
                "tenant", tenant.value(), "event_type", entry.eventType());
            return;
        }

        // ── Feature lag metric (P3.3.1) — time from event timestamp to ingestion ──
        if (entry.timestamp() != null) {
            long lagMs = Duration.between(entry.timestamp(), Instant.now()).toMillis();
            metrics.recordTimer("feature.ingest.lag_ms", lagMs, "tenant", tenant.value());
        }

        // ── Feature extraction ───────────────────────────────────────────────
        List<MLFeature> features;
        try {
            byte[] raw = new byte[entry.payload().remaining()];
            entry.payload().duplicate().get(raw);
            Map<String, Object> payload = MAPPER.readValue(raw, Map.class);
            String entityId = entry.headers().getOrDefault("entityId", eventId);
            features = extractFeatures(entityId, payload, entry.timestamp(), transformSpec);
        } catch (Exception ex) {
            FeatureExtractionException extractEx = new FeatureExtractionException(
                eventId, tenant.value(),
                "Failed to extract features from event " + eventId + " tenant=" + tenant.value(), ex);
            LOG.error("[feature-ingest] {}", extractEx.getMessage(), ex);
            metrics.incrementCounter("feature.ingest.extraction.errors",
                "tenant", tenant.value(),
                "error_type", ex.getClass().getSimpleName());
            deadLetterQueue.store(entry, extractEx, "extraction-failure");
            metrics.incrementCounter("feature.ingest.dlq.stored",
                "tenant", tenant.value(), "reason", "extraction-failure");
            return;
        }

        // ── Feature store writes (circuit-breaker protected) ─────────────────
        for (MLFeature feature : features) {
            try {
                featureStoreCircuitBreaker.executeSync(
                    () -> { featureStore.ingest(tenant.value(), feature); return null; },
                    () -> {
                        // Circuit is OPEN: route to DLQ rather than dropping silently
                        metrics.incrementCounter("feature.ingest.circuit.open.rejections",
                            "tenant", tenant.value(), "feature", feature.getName());
                        deadLetterQueue.store(entry, new FeatureIngestException(
                            "Circuit breaker OPEN for feature-store-writes",
                            FeatureIngestException.ErrorCategory.STORE_UNAVAILABLE),
                            "circuit-open");
                        return null;
                    });
                metrics.incrementCounter("feature.ingest.features.written",
                    "tenant", tenant.value(), "feature", feature.getName());
            } catch (Exception ex) {
                FeatureStoreWriteException writeEx = new FeatureStoreWriteException(
                    feature.getName(), tenant.value(), 1,
                    "Write failed for feature=" + feature.getName() + " tenant=" + tenant.value(), ex);
                LOG.warn("[feature-ingest] {}", writeEx.getMessage(), ex);
                metrics.incrementCounter("feature.ingest.features.errors",
                    "tenant", tenant.value(), "feature", feature.getName(),
                    "error_category", writeEx.getCategory().name());
                deadLetterQueue.store(entry, writeEx, "write-failure");
                metrics.incrementCounter("feature.ingest.dlq.stored",
                    "tenant", tenant.value(), "reason", "write-failure");
            }
        }

        long durationNs = System.nanoTime() - start;
        metrics.recordTimer("feature.ingest.processing.duration",
            durationNs / 1_000_000, "tenant", tenant.value());

        LOG.debug("[feature-ingest] processed eventId={} tenant={} features={}",
            eventId, tenant.value(), features.size());
    }

    /**
     * Derives a set of numeric feature vectors from a raw event payload.
     * Numeric fields are stored directly; categorical fields are hashed.
     *
     * <p>The {@code eventTimestamp} is used (rather than {@code Instant.now()}) so that
     * re-processing the same event on replay produces features with identical timestamps.
     * Derived time features ({@code hour_of_day}, {@code day_of_week}) are also derived
     * from the event timestamp for the same reason — ML models trained on event-time
     * features must see consistent values across ingestion and replay.
     *
     * @param entityId       entity identifier for the feature record
     * @param payload        raw event payload map
     * @param eventTimestamp timestamp of the originating event (used for all feature records)
     * @return immutable list of extracted {@link MLFeature} objects (never null)
     */
    static List<MLFeature> extractFeatures(String entityId, Map<String, Object> payload, Instant eventTimestamp) {
        return extractFeatures(entityId, payload, eventTimestamp, FeatureTransformSpec.passThrough());
    }

    /**
     * Derives a set of numeric feature vectors from a raw event payload, applying the given
     * {@link FeatureTransformSpec} to control field selection.
     *
     * @param entityId       entity identifier for the feature record
     * @param payload        raw event payload map
     * @param eventTimestamp timestamp of the originating event
     * @param spec           transform spec controlling which fields are included
     * @return immutable list of extracted {@link MLFeature} objects (never null)
     */
    static List<MLFeature> extractFeatures(
            String entityId,
            Map<String, Object> payload,
            Instant eventTimestamp,
            FeatureTransformSpec spec) {
        Instant ts = eventTimestamp != null ? eventTimestamp : Instant.now();
        String resolvedEntityId = entityId != null ? entityId : Identifier.random().raw();
        var features = new java.util.ArrayList<MLFeature>();

        // ── Numeric pass-through ─────────────────────────────────────────
        // DC3-H7: Sort by key to guarantee deterministic feature-vector ordering across
        // JVM restarts, Map implementations, and JSON key-ordering variations.
        // ML models trained on one ordering produce garbage results on another.
        for (Map.Entry<String, Object> kv : new java.util.TreeMap<>(payload).entrySet()) {
            // P3.3.1: apply field filter from transform spec
            if (!spec.acceptsField(kv.getKey())) {
                continue;
            }
            Object val = kv.getValue();
            double numeric;
            if (val instanceof Number n) {
                numeric = n.doubleValue();
            } else if (val instanceof String s) {
                numeric = (double) (s.hashCode() & 0x7FFFFFFF);
            } else if (val instanceof Boolean b) {
                numeric = b ? 1.0 : 0.0;
            } else {
                continue; // skip null / complex objects
            }
            features.add(MLFeature.builder()
                .name(sanitizeFeatureName(kv.getKey()))
                .value(numeric)
                .entityId(resolvedEntityId)
                .timestamp(ts)
                .build());
        }

        // ── Derived time features ────────────────────────────────────────
        if (spec.isDerivedTimeFeatures()) {
            features.add(MLFeature.builder()
                .name("hour_of_day")
                .value((double) ts.atZone(java.time.ZoneOffset.UTC).getHour())
                .entityId(resolvedEntityId)
                .timestamp(ts)
                .build());
            features.add(MLFeature.builder()
                .name("day_of_week")
                .value((double) ts.atZone(java.time.ZoneOffset.UTC).getDayOfWeek().getValue())
                .entityId(resolvedEntityId)
                .timestamp(ts)
                .build());
        }

        return java.util.Collections.unmodifiableList(features);
    }

    private static String sanitizeFeatureName(String raw) {
        return raw.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
    }

    private static long parseOffset(Offset offset) {
        try {
            return Long.parseLong(offset.value());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    // ── Main / factory ────────────────────────────────────────────────────

    /**
     * Entry point. Reads configuration from environment via {@link FeatureIngestConfig},
     * wires dependencies, and runs the polling loop on an ActiveJ {@link Eventloop}.
     *
     * <p>Registers a JVM shutdown hook for graceful termination.
     */
    public static void main(String[] args) {
        FeatureIngestConfig cfg = FeatureIngestConfig.fromEnv();
        cfg.validate();

        List<TenantId> tenants = Arrays.stream(cfg.tenants.split(","))
            .map(String::strip)
            .filter(s -> !s.isBlank())
            .map(TenantId::of)
            .toList();

        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        MetricsCollector metrics = MetricsCollectorFactory.create(meterRegistry);

        // ── EventLogStore ─────────────────────────────────────────────────
        EventLogStore eventLogStore;
        if (cfg.isPostgresMode()) {
            eventLogStore = new WarmTierEventLogStore(cfg.buildEventLogStoreDataSource());
            LOG.info("[feature-ingest] EventLogStore backed by PostgreSQL: {}", cfg.dbUrl);
        } else {
            LOG.warn("[feature-ingest] using InMemoryEventLogStore — set FEATURE_INGEST_MODE=postgres for production");
            eventLogStore = new InMemoryEventLogStore();
        }

        // ── FeatureStoreService ──────────────────────────────────────────
        FeatureStoreService featureStore;
        if (cfg.isPostgresMode()) {
            featureStore = new FeatureStoreService(cfg.buildFeatureStoreDataSource(), metrics);
            LOG.info("[feature-ingest] FeatureStoreService wired to PostgreSQL: {}", cfg.dbUrl);
        } else {
            LOG.warn("[feature-ingest] FEATURE_INGEST_DB_URL not set — feature writes will fail; set for production");
            featureStore = new FeatureStoreService(null, metrics);
        }

        // ── Launcher ─────────────────────────────────────────────────────
        FeatureStoreIngestLauncher launcher = new FeatureStoreIngestLauncher(
            eventLogStore, featureStore, metrics, tenants,
            cfg.batchSize, cfg.retryDelayMs, cfg.pollDelayMs);

        // ── P3.3.1: YAML transform spec ───────────────────────────────────
        String transformConfigPath = env(FeatureIngestConfig.ENV_TRANSFORM_CONFIG, null);
        if (transformConfigPath != null) {
            try {
                FeatureTransformSpec spec = FeatureTransformSpec.fromYamlFile(transformConfigPath);
                launcher.withTransformSpec(spec);
                LOG.info("[feature-ingest] transform spec loaded from {}", transformConfigPath);
            } catch (Exception e) {
                LOG.warn("[feature-ingest] could not load transform spec from '{}', using pass-through: {}",
                    transformConfigPath, e.getMessage());
            }
        }

        Eventloop eventloop = Eventloop.create();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("[feature-ingest] shutdown hook fired — stopping");
            launcher.stop();
            eventloop.breakEventloop();
        }, "feature-ingest-shutdown"));

        eventloop.post(() -> launcher.start(eventloop));

        LOG.info("[feature-ingest] eventloop starting — mode={} tenants={}", cfg.mode, tenants);
        eventloop.run();
        LOG.info("[feature-ingest] eventloop exited");
    }

    /** Reads an environment variable, returning {@code defaultValue} when absent. */
    private static String env(String key, String defaultValue) {
        String val = System.getenv(key);
        return (val != null && !val.isBlank()) ? val : defaultValue;
    }
}
