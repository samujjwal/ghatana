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

    /** Maximum number of items retained in the in-memory review queue. */
    static final int MAX_REVIEW_QUEUE_SIZE = 1_000;

    private final DataCloudBrain brain;
    private final ScheduledExecutorService scheduler;
    private final AgentLearningAuditBridge auditBridge;

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
     * @param auditBridge the audit bridge for logging learning events; may be {@code null}
     */
    public DataCloudLearningBridge(DataCloudBrain brain, AgentLearningAuditBridge auditBridge) {
        if (brain == null) throw new IllegalArgumentException("brain must not be null");
        this.brain = brain;
        this.auditBridge = auditBridge;
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

            // Enqueue low-confidence discovered patterns for human review,
            // but respect the MAX_REVIEW_QUEUE_SIZE cap to bound memory usage
            result.getPatternsDiscovered().forEach(p -> {
                if (p.getConfidence() < 0.7f && reviewQueue.size() < MAX_REVIEW_QUEUE_SIZE) {
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
            
            // Log learning cycle to audit trail if audit bridge is available
            if (auditBridge != null) {
                auditBridge.logLearningCycle(tenantId, manual ? "manual-trigger" : "system-scheduler",
                    discovered, updated, durMs, Map.of("manual", manual));
            }
            
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
     * @param tenantId the tenant ID
     * @param userId the user ID approving the pattern
     * @param reviewId the review item identifier
     * @return {@code true} if found and updated; {@code false} if not found
     */
    public boolean approveReview(String tenantId, String userId, String reviewId) {
        boolean result = applyDecision(tenantId, userId, reviewId, "APPROVED");
        if (result && auditBridge != null) {
            Map<String, Object> item = reviewQueue.get(reviewId);
            if (item != null) {
                String patternId = (String) item.getOrDefault("patternId", "unknown");
                auditBridge.logPatternApproval(tenantId, userId, reviewId, patternId, Map.of());
            }
        }
        return result;
    }

    /**
     * Rejects a pattern review item.
     *
     * @param tenantId the tenant ID
     * @param userId the user ID rejecting the pattern
     * @param reviewId the review item identifier
     * @param reason the rejection reason
     * @return {@code true} if found and updated; {@code false} if not found
     */
    public boolean rejectReview(String tenantId, String userId, String reviewId, String reason) {
        boolean result = applyDecision(tenantId, userId, reviewId, "REJECTED");
        if (result && auditBridge != null) {
            Map<String, Object> item = reviewQueue.get(reviewId);
            if (item != null) {
                String patternId = (String) item.getOrDefault("patternId", "unknown");
                auditBridge.logPatternRejection(tenantId, userId, reviewId, patternId, reason, Map.of());
            }
        }
        return result;
    }

    /**
     * Removes all APPROVED and REJECTED items from the review queue.
     *
     * @return number of items removed
     */
    public int purgeCompletedReviews() {
        int[] count = {0};
        reviewQueue.entrySet().removeIf(entry -> {
            Object status = entry.getValue().get("status");
            boolean completed = "APPROVED".equals(status) || "REJECTED".equals(status);
            if (completed) count[0]++;
            return completed;
        });
        log.info("DataCloudLearningBridge: purged {} completed review(s)", count[0]);
        return count[0];
    }

    private boolean applyDecision(String tenantId, String userId, String reviewId, String decision) {
        Map<String, Object> existing = reviewQueue.get(reviewId);
        if (existing == null) return false;

        Object currentStatus = existing.get("status");
        if (decision.equals(currentStatus)) {
            log.info("DataCloudLearningBridge: review {} already {}", reviewId, decision);
            return true;
        }

        if ("APPROVED".equals(currentStatus) || "REJECTED".equals(currentStatus)) {
            log.warn("DataCloudLearningBridge: review {} already finalized as {}; refusing {} transition", reviewId, currentStatus, decision);
            return false;
        }

        LinkedHashMap<String, Object> updated = new LinkedHashMap<>(existing);
        updated.put("status", decision);
        updated.put("reviewedAt", Instant.now().toString());
        updated.put("reviewedBy", userId);
        reviewQueue.put(reviewId, Map.copyOf(updated));
        log.info("DataCloudLearningBridge: review {} → {} by user {}", reviewId, decision, userId);
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
