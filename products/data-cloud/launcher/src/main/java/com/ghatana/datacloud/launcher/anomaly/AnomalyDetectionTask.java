package com.ghatana.datacloud.launcher.anomaly;

import com.ghatana.datacloud.analytics.anomaly.StatisticalAnomalyDetector;
import com.ghatana.datacloud.spi.ai.AnomalyDetectionCapability.Anomaly;
import com.ghatana.datacloud.spi.ai.AnomalyDetectionCapability.AnomalyContext;
import com.ghatana.datacloud.spi.ai.AnomalyDetectionCapability.DetectionType;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Continuously scans registered entity collections for anomalies on a fixed schedule.
 *
 * <p>The scheduler fires on a daemon thread and submits detection work to the ActiveJ
 * {@link Eventloop} via {@link Eventloop#execute(Runnable)} so the eventloop stays
 * unblocked and detection runs in the correct threading context.
 *
 * <p>Detected anomalies are persisted to the {@code __anomalies} event stream via
 * the configured anomaly persistence port.
 *
 * <p>Collections are registered via {@link #registerCollection(String, String)} and
 * can be removed via {@link #deregisterCollection(String, String)}.
 *
 * @doc.type    class
 * @doc.purpose Background anomaly detection task for continuous stream monitoring (P3.6.1)
 * @doc.layer   product
 * @doc.pattern Scheduler, Observer
 */
public final class AnomalyDetectionTask implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AnomalyDetectionTask.class);

    /** How often the scheduled scan fires. */
    public static final long SCAN_INTERVAL_MINUTES = 5L;

    /** Default max anomalies persisted per scan cycle across all collections. */
    static final int MAX_ANOMALIES_PER_SCAN = 500;

    private final StatisticalAnomalyDetector anomalyDetector;
    private final AnomalyPersistencePort anomalyPersistencePort;
    private final Eventloop eventloop;
    private final ScheduledExecutorService scheduler;

    /** Tenant → set of registered collection names. */
    private final ConcurrentHashMap<String, Set<String>> tenantCollections = new ConcurrentHashMap<>();

    private final AtomicBoolean scanning = new AtomicBoolean(false);
    private final AtomicReference<Instant> lastScanTime = new AtomicReference<>();
    private final AtomicLong totalAnomaliesDetected = new AtomicLong(0);

    /**
     * Creates an anomaly detection task.
     *
     * @param anomalyDetector detector used for statistical analysis
     * @param anomalyPersistencePort persistence port used for durable anomaly persistence
     * @param eventloop       the ActiveJ eventloop — detection is submitted via execute()
     */
    public AnomalyDetectionTask(
            StatisticalAnomalyDetector anomalyDetector,
            AnomalyPersistencePort anomalyPersistencePort,
            Eventloop eventloop) {
        this.anomalyDetector = Objects.requireNonNull(anomalyDetector, "anomalyDetector");
        this.anomalyPersistencePort = Objects.requireNonNull(anomalyPersistencePort, "anomalyPersistencePort");
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop");
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "dc-anomaly-scan");
            t.setDaemon(true);
            return t;
        });
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    /**
     * Starts the periodic anomaly detection schedule.
     * The first automatic scan fires after {@value #SCAN_INTERVAL_MINUTES} minutes.
     */
    public void start() {
        log.info("AnomalyDetectionTask: starting — scan every {} min", SCAN_INTERVAL_MINUTES);
        scheduler.scheduleAtFixedRate(
                this::triggerScan,
                SCAN_INTERVAL_MINUTES, SCAN_INTERVAL_MINUTES, TimeUnit.MINUTES
        );
    }

    @Override
    public void close() {
        log.info("AnomalyDetectionTask: shutting down");
        scheduler.shutdownNow();
    }

    // ─── Collection registration ──────────────────────────────────────────────

    /**
     * Registers a collection for continuous anomaly monitoring.
     *
     * @param tenantId   the tenant owning the collection
     * @param collection the collection name to monitor
     */
    public void registerCollection(String tenantId, String collection) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(collection, "collection");
        tenantCollections
                .computeIfAbsent(tenantId, t -> ConcurrentHashMap.newKeySet())
                .add(collection);
        log.debug("AnomalyDetectionTask: registered collection tenant={} collection={}", tenantId, collection);
    }

    /**
     * Removes a collection from continuous anomaly monitoring.
     *
     * @param tenantId   the tenant owning the collection
     * @param collection the collection name to deregister
     */
    public void deregisterCollection(String tenantId, String collection) {
        Set<String> collections = tenantCollections.get(tenantId);
        if (collections != null) {
            collections.remove(collection);
            log.debug("AnomalyDetectionTask: deregistered tenant={} collection={}", tenantId, collection);
        }
    }

    // ─── Scan logic ───────────────────────────────────────────────────────────

    /**
     * Triggers a scan on the next eventloop iteration.
     * Called by the background scheduler; safe to call from any thread.
     */
    void triggerScan() {
        if (tenantCollections.isEmpty()) {
            log.trace("AnomalyDetectionTask: no registered collections — skipping scan");
            return;
        }
        // Submit scan work to the eventloop so all Promise chains run correctly
        eventloop.execute(this::runScanOnEventloop);
    }

    /**
     * Runs one scan cycle — must be called on the ActiveJ eventloop thread.
     */
    private void runScanOnEventloop() {
        if (!scanning.compareAndSet(false, true)) {
            log.debug("AnomalyDetectionTask: skip — previous scan still running");
            return;
        }

        Instant scanStart = Instant.now();
        log.debug("AnomalyDetectionTask: scan start — {} tenant(s)", tenantCollections.size());

        tenantCollections.forEach((tenantId, collections) ->
                collections.forEach(collection -> scanCollection(tenantId, collection, scanStart)));

        lastScanTime.set(scanStart);
        scanning.set(false);
    }

    private void scanCollection(String tenantId, String collection, Instant scanStart) {
        AnomalyContext ctx = AnomalyContext.builder()
                .tenantId(tenantId)
                .collectionName(collection)
                .detectionType(DetectionType.DATA_QUALITY)
                .threshold(StatisticalAnomalyDetector.DEFAULT_Z_THRESHOLD)
                .build();

        anomalyDetector.detect(ctx)
                .then(anomalies -> {
                    if (!anomalies.isEmpty()) {
                        totalAnomaliesDetected.addAndGet(anomalies.size());
                        log.info("AnomalyDetectionTask: detected {} anomalies tenant={} collection={}",
                                anomalies.size(), tenantId, collection);
                        Promise<Void> persistence = anomalyPersistencePort.persist(tenantId, collection, anomalies);
                        return persistence != null ? persistence : Promise.of(null);
                    }
                    return null;
                })
                .whenException(e -> log.error(
                        "AnomalyDetectionTask: scan failed tenant={} collection={}: {}",
                        tenantId, collection, e.getMessage(), e));
    }

    // ─── Status ───────────────────────────────────────────────────────────────

    /**
     * Returns a status snapshot of the anomaly detection task.
     *
     * @return status map for monitoring/observability
     */
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new java.util.LinkedHashMap<>();
        status.put("scanning", scanning.get());
        status.put("lastScanTime", lastScanTime.get() != null ? lastScanTime.get().toString() : "never");
        status.put("totalAnomaliesDetected", totalAnomaliesDetected.get());
        status.put("registeredTenants", tenantCollections.size());
        status.put("registeredCollections", tenantCollections.values().stream().mapToInt(Set::size).sum());
        status.put("scanIntervalMinutes", SCAN_INTERVAL_MINUTES);
        return status;
    }

    @FunctionalInterface
    public interface AnomalyPersistencePort {
        Promise<Void> persist(String tenantId, String collection, java.util.List<Anomaly> anomalies);
    }
}
