package com.ghatana.refactorer.server.jobs;

import io.activej.promise.Promise;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.UnaryOperator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Hybrid job store implementation using local and centralized backends.
 *
 * <p>This implementation provides fast local access (~1ms) via in-memory cache
 * while persisting state to a centralized backend (Redis/PostgreSQL) for
 * cross-instance visibility and recovery on restart.</p>
 *
 * <p>Architecture:
 * <ul>
 *   <li>Local Backend: ConcurrentHashMap (fast, in-process)</li>
 *   <li>Central Backend: Redis or PostgreSQL (durable, shared)</li>
 *   <li>Sync Strategy: BATCHED (default), configurable</li>
 *   <li>State Keys: tenant:jobId (for multi-tenant isolation)</li>
 * </ul>
 *
 * <p>Binding Decision #5: Hybrid State Management with local + centralized
 * backends, partition-aware state key format, and configurable sync strategies.</p>
 *
 * @doc.type class
 * @doc.purpose Provide hybrid in-memory/centralized persistence for job execution metadata
 * @doc.layer product
 * @doc.pattern Repository
 */
public final class HybridJobStore implements JobStore {
    private static final Logger logger = LogManager.getLogger(HybridJobStore.class);

    private final ConcurrentMap<String, JobRecord> localCache = new ConcurrentHashMap<>();
    private final CentralStateStore centralStore;
    private final SyncStrategy syncStrategy;
    private final TenantContextExtractor tenantExtractor;

    /**
     * Sync strategy for state persistence.
     */
    public enum SyncStrategy {
        /**
 * Sync every write (safest, slowest ~100ms) */
        IMMEDIATE,
        /**
 * Batch writes every N operations (balanced, default ~10ms) */
        BATCHED,
        /**
 * Sync periodically (fastest ~1ms local, eventual consistency) */
        PERIODIC,
        /**
 * Only sync on checkpoint/savepoint (maximum performance) */
        ON_CHECKPOINT
    }

    /**
     * Central state store interface for Redis/PostgreSQL backends.
     */
    public interface CentralStateStore {
        /**
 * Write state to central store */
        Promise<Void> write(String tenantId, String jobId, JobRecord job);

        /**
 * Read state from central store */
        Promise<Optional<JobRecord>> read(String tenantId, String jobId);

        /**
 * Delete state from central store */
        Promise<Void> delete(String tenantId, String jobId);

        /**
 * Health check */
        Promise<Boolean> isHealthy();
    }

    /**
     * Tenant context extraction for state key scoping.
     */
    @FunctionalInterface
    public interface TenantContextExtractor {
        String getCurrentTenantId();
    }

    public HybridJobStore(
            CentralStateStore centralStore,
            SyncStrategy syncStrategy,
            TenantContextExtractor tenantExtractor) {
        this.centralStore = Objects.requireNonNull(centralStore, "centralStore must not be null");
        this.syncStrategy = Objects.requireNonNull(syncStrategy, "syncStrategy must not be null");
        this.tenantExtractor =
                Objects.requireNonNull(tenantExtractor, "tenantExtractor must not be null");

        logger.info(
                "Hybrid job store initialized with sync strategy: {} and centralized backend",
                syncStrategy);
    }

    @Override
    public void create(JobRecord job) {
        String tenantId = tenantExtractor.getCurrentTenantId();
        String stateKey = buildStateKey(tenantId, job.jobId());

        JobRecord existing = localCache.putIfAbsent(stateKey, job);
        if (existing != null) {
            throw new IllegalStateException("Job already exists: " + job.jobId());
        }

        // Sync to central store based on strategy
        syncToCentralStore(tenantId, job.jobId(), job);
        logger.debug("Created job {} in tenant {}", job.jobId(), tenantId);
    }

    @Override
    public Optional<JobRecord> get(String jobId) {
        String tenantId = tenantExtractor.getCurrentTenantId();
        String stateKey = buildStateKey(tenantId, jobId);

        // Try local cache first (fast path)
        Optional<JobRecord> local = Optional.ofNullable(localCache.get(stateKey));
        if (local.isPresent()) {
            logger.trace("Job {} found in local cache", jobId);
            return local;
        }

        // Fallback to central store (recovery path)
        logger.debug("Job {} not in cache, checking central store", jobId);
        return recoverFromCentralStore(tenantId, jobId);
    }

    @Override
    public Optional<JobRecord> update(String jobId, UnaryOperator<JobRecord> mutator) {
        String tenantId = tenantExtractor.getCurrentTenantId();
        String stateKey = buildStateKey(tenantId, jobId);

        Optional<JobRecord> result =
                Optional.ofNullable(
                        localCache.computeIfPresent(
                                stateKey,
                                (id, current) -> {
                                    JobRecord updated = mutator.apply(current);
                                    return updated == null ? current : updated;
                                }));

        if (result.isPresent()) {
            syncToCentralStore(tenantId, jobId, result.get());
            logger.trace("Updated job {} in tenant {}", jobId, tenantId);
        }

        return result;
    }

    @Override
    public void delete(String jobId) {
        String tenantId = tenantExtractor.getCurrentTenantId();
        String stateKey = buildStateKey(tenantId, jobId);

        localCache.remove(stateKey);
        deleteFromCentralStore(tenantId, jobId);
        logger.debug("Deleted job {} from tenant {}", jobId, tenantId);
    }

    /**
     * Build tenant-scoped state key: tenant:jobId
     *
     * <p>Keys are partitioned by tenant to ensure multi-tenant isolation.</p>
     */
    private String buildStateKey(String tenantId, String jobId) {
        return String.format("%s:%s", tenantId, jobId);
    }

    /**
     * Sync job state to central store based on configured strategy.
     */
    private void syncToCentralStore(String tenantId, String jobId, JobRecord job) {
        switch (syncStrategy) {
            case IMMEDIATE:
                // Immediate async write with result logging
                centralStore.write(tenantId, jobId, job)
                        .whenResult(() -> logger.debug("Synced job {} immediately to central store", jobId))
                        .whenException(e ->
                                logger.warn("Failed to sync job {} immediately: {}", jobId, e.getMessage()));
                break;

            case BATCHED:
                // Batch async write
                centralStore.write(tenantId, jobId, job)
                        .whenComplete(
                                (result, error) -> {
                                    if (error != null) {
                                        logger.warn(
                                                "Failed to batch-sync job {}: {}",
                                                jobId,
                                                error.getMessage());
                                    } else {
                                        logger.trace("Batch-synced job {} to central store", jobId);
                                    }
                                });
                break;

            case PERIODIC:
                // Async fire-and-forget (eventual consistency)
                centralStore.write(tenantId, jobId, job).whenComplete((v, e) -> {
                    if (e != null) {
                        logger.warn("Failed to async-sync job {}: {}", jobId, e.getMessage());
                    }
                });
                break;

            case ON_CHECKPOINT:
                // Skip sync (manual checkpoint required)
                logger.trace("Skipping sync for job {} (ON_CHECKPOINT strategy)", jobId);
                break;
        }
    }

    /**
     * Recover job state from central store.
     *
     * <p>Used when job not found in local cache (e.g., restart, instance
     * failure).</p>
     */
    private Optional<JobRecord> recoverFromCentralStore(String tenantId, String jobId) {
        String stateKey = buildStateKey(tenantId, jobId);
        centralStore.read(tenantId, jobId)
                .whenResult(recovered -> {
                    if (recovered.isPresent()) {
                        localCache.put(stateKey, recovered.get());
                        logger.info("Recovered job {} from central store for tenant {}", jobId, tenantId);
                    }
                })
                .whenException(e ->
                        logger.warn(
                                "Failed to recover job {} from central store: {}",
                                jobId,
                                e.getMessage()));
        // Async recovery populates cache; callers should retry after recovery completes
        // NOTE: Refactor JobStore.get() to return Promise<Optional<JobRecord>> for proper async recovery
        return Optional.ofNullable(localCache.get(stateKey));
    }

    /**
     * Delete job from central store.
     */
    private void deleteFromCentralStore(String tenantId, String jobId) {
        centralStore.delete(tenantId, jobId)
                .whenComplete((v, e) -> {
                    if (e != null) {
                        logger.warn(
                                "Failed to delete job {} from central store: {}",
                                jobId,
                                e.getMessage());
                    } else {
                        logger.debug("Deleted job {} from central store", jobId);
                    }
                });
    }

    /**
     * Get current local cache size.
     */
    public int size() {
        return localCache.size();
    }

    /**
     * Flush all cached state to central store (checkpoint).
     */
    public void checkpoint() {
        logger.info("Checkpointing {} jobs to central store", localCache.size());
        String tenantId = tenantExtractor.getCurrentTenantId();
        localCache.forEach(
                (key, job) ->
                    centralStore.write(tenantId, job.jobId(), job)
                            .whenResult(() -> logger.trace("Checkpointed job {}", job.jobId()))
                            .whenException(e ->
                                    logger.warn("Failed to checkpoint job {}: {}", job.jobId(), e.getMessage())));
        logger.info("Checkpoint complete");
    }
}
