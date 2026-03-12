package com.ghatana.datacloud.launcher.learning;

import com.ghatana.datacloud.brain.BrainContext;
import com.ghatana.datacloud.brain.DataCloudBrain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Periodically triggers {@link DataCloudBrain#learn} (every {@value #INTERVAL_MINUTES} minutes)
 * and provides a manual trigger + status API consumed by the DC-8 LearningRoutes.
 *
 * <p>All mutable state is thread-safe via {@link AtomicReference} and {@link ConcurrentHashMap}.
 * The bridge maintains an in-memory review queue that is populated with low-confidence patterns
 * (confidence &lt; 0.7) discovered during each learning cycle.
 *
 * @doc.type class
 * @doc.purpose Scheduled brain-learning bridge with manual trigger and pattern review queue (DC-7)
 * @doc.layer product
 * @doc.pattern Bridge, Scheduler
 */
public class DataCloudLearningBridge implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DataCloudLearningBridge.class);

    /** How often the scheduled trigger fires. */
    static final long INTERVAL_MINUTES = 5L;

    private final DataCloudBrain brain;
    private final ScheduledExecutorService scheduler;

    private final AtomicReference<Instant> lastRunTime       = new AtomicReference<>();
    private final AtomicReference<Instant> nextScheduledRun  = new AtomicReference<>();
    private final AtomicReference<Map<String, Object>> lastResult =
            new AtomicReference<>(Map.of("status", "NOT_RUN"));
    private final AtomicBoolean learning = new AtomicBoolean(false);

    /**
     * In-memory review queue.
     * Key: reviewId, Value: review item map containing patternId, confidence, status, etc.
     */
    private final ConcurrentHashMap<String, Map<String, Object>> reviewQueue =
            new ConcurrentHashMap<>();

    // ═══════════════════════════════════════════════════════════════════════════
    // Construction
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Creates a new learning bridge.
     *
     * @param brain the brain facade; must not be {@code null}
     */
    public DataCloudLearningBridge(DataCloudBrain brain) {
        if (brain == null) throw new IllegalArgumentException("brain must not be null");
        this.brain = brain;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "dc-learning-bridge");
            t.setDaemon(true);
            return t;
        });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Lifecycle
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Starts the periodic learning schedule.
     * The first automatic run fires after {@value #INTERVAL_MINUTES} minutes.
     */
    public void start() {
        log.info("DataCloudLearningBridge: starting — brain.learn() every {} min", INTERVAL_MINUTES);
        nextScheduledRun.set(Instant.now().plusSeconds(INTERVAL_MINUTES * 60));
        scheduler.scheduleAtFixedRate(
            () -> runLearning("system", false),
            INTERVAL_MINUTES, INTERVAL_MINUTES, TimeUnit.MINUTES
        );
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Learning Cycle
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Executes a learning cycle synchronously on the calling thread.
     *
     * <p>This method is safe to call only from a non-eventloop thread (e.g., a virtual-thread
     * executor or the internal scheduler). HTTP handlers must wrap this via
     * {@code Promise.ofBlocking(executor, () -> bridge.runLearning(...))} to avoid blocking
     * the ActiveJ eventloop.
     *
     * @param tenantId the tenant context for the learning run
     * @param manual   {@code true} when triggered manually via the HTTP API
     * @return a result summary map; never {@code null}
     */
    public Map<String, Object> runLearning(String tenantId, boolean manual) {
        if (!learning.compareAndSet(false, true)) {
            log.debug("DataCloudLearningBridge: skip — already running");
            return Map.of(
                "status",    "SKIPPED",
                "reason",    "Learning cycle already in progress",
                "timestamp", Instant.now().toString()
            );
        }

        Instant start = Instant.now();
        try {
            log.info("DataCloudLearningBridge: cycle start [tenantId={}, manual={}]",
                    tenantId, manual);

            BrainContext ctx = BrainContext.forTenant(tenantId);
            DataCloudBrain.LearningConfig config = DataCloudBrain.LearningConfig.builder()
                    .minSamples(10)
                    .maxPatterns(50)
                    .build();

            // brain.learn() returns Promise.of(stub-result) — already resolved.
            // getResult() is safe here because this method exclusively runs on
            // non-eventloop threads (scheduler daemon or virtual-thread executor).
            DataCloudBrain.LearningResult result = brain.learn(config, ctx).getResult();

            long durMs = Instant.now().toEpochMilli() - start.toEpochMilli();
            lastRunTime.set(start);
            if (!manual) {
                nextScheduledRun.set(Instant.now().plusSeconds(INTERVAL_MINUTES * 60));
            }

            int discovered = result.getPatternsDiscovered().size();
            int updated    = result.getPatternsUpdated().size();

            // Enqueue low-confidence discovered patterns for human review
            result.getPatternsDiscovered().forEach(p -> {
                if (p.getConfidence() < 0.7f) {
                    String reviewId = "review-" + p.getId();
                    reviewQueue.putIfAbsent(reviewId, Map.of(
                        "reviewId",       reviewId,
                        "patternId",      p.getId(),
                        "patternName",    p.getName() != null ? p.getName() : "unnamed",
                        "confidence",     p.getConfidence(),
                        "status",         "PENDING",
                        "discoveredAt",   start.toString()
                    ));
                }
            });

            Map<String, Object> summary = Map.of(
                "status",            "COMPLETED",
                "tenantId",          tenantId,
                "manual",            manual,
                "durationMs",        durMs,
                "patternsDiscovered", discovered,
                "patternsUpdated",   updated,
                "recordsAnalyzed",   result.getRecordsAnalyzed(),
                "ranAt",             start.toString()
            );
            lastResult.set(summary);
            log.info("DataCloudLearningBridge: cycle done — discovered={}, updated={}, ms={}",
                    discovered, updated, durMs);
            return summary;

        } catch (Exception e) {
            log.error("DataCloudLearningBridge: cycle failed", e);
            Map<String, Object> errSummary = Map.of(
                "status",  "FAILED",
                "tenantId", tenantId,
                "manual",   manual,
                "error",    e.getMessage() != null ? e.getMessage() : "unknown",
                "ranAt",    start.toString()
            );
            lastResult.set(errSummary);
            return errSummary;
        } finally {
            learning.set(false);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Status & Review Queue
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Returns a snapshot of the current bridge status.
     *
     * @return status map; never {@code null}
     */
    public Map<String, Object> getStatus() {
        Instant lastRun = lastRunTime.get();
        Instant nextRun = nextScheduledRun.get();
        long pending = reviewQueue.values().stream()
                .filter(m -> "PENDING".equals(m.get("status")))
                .count();
        return Map.of(
            "running",          learning.get(),
            "lastRunTime",      lastRun != null ? lastRun.toString() : "never",
            "nextScheduledRun", nextRun != null ? nextRun.toString() : "not started",
            "intervalMinutes",  INTERVAL_MINUTES,
            "pendingReviews",   pending,
            "lastResult",       lastResult.get()
        );
    }

    /**
     * Returns a snapshot of all items in the review queue.
     *
     * @return unmodifiable copy of the review queue; never {@code null}
     */
    public Map<String, Map<String, Object>> getReviewQueue() {
        return Map.copyOf(reviewQueue);
    }

    /**
     * Approves a pattern review item.
     *
     * @param reviewId the review item identifier
     * @return {@code true} if found and updated; {@code false} if not found
     */
    public boolean approveReview(String reviewId) {
        return applyDecision(reviewId, "APPROVED");
    }

    /**
     * Rejects a pattern review item.
     *
     * @param reviewId the review item identifier
     * @return {@code true} if found and updated; {@code false} if not found
     */
    public boolean rejectReview(String reviewId) {
        return applyDecision(reviewId, "REJECTED");
    }

    private boolean applyDecision(String reviewId, String decision) {
        Map<String, Object> existing = reviewQueue.get(reviewId);
        if (existing == null) return false;
        LinkedHashMap<String, Object> updated = new LinkedHashMap<>(existing);
        updated.put("status", decision);
        updated.put("reviewedAt", Instant.now().toString());
        reviewQueue.put(reviewId, Map.copyOf(updated));
        log.info("DataCloudLearningBridge: review {} → {}", reviewId, decision);
        return true;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AutoCloseable
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public void close() {
        log.info("DataCloudLearningBridge: shutting down scheduler");
        scheduler.shutdownNow();
    }
}
