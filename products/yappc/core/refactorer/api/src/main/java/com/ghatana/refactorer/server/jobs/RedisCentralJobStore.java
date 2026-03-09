package com.ghatana.refactorer.server.jobs;

import io.activej.promise.Promise;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Mock Redis-based central state store for job records.
 *
 *
 *
 * <p>
 * This is a reference implementation showing how to implement
 *
 * {@link HybridJobStore.CentralStateStore} with a distributed backend.
 *
 * In production, this would use Redis or PostgreSQL.</p>
 *
 *
 *
 * @doc.type class
 *
 * @doc.purpose Provide CRUD access to job metadata while abstracting the
 * backing storage.
 *
 * @doc.layer product
 *
 * @doc.pattern Repository
 *
 */
public final class RedisCentralJobStore implements HybridJobStore.CentralStateStore {

    private static final Logger logger = LogManager.getLogger(RedisCentralJobStore.class);

    // Mock Redis storage (in production, use real Redis client)
    private final ConcurrentHashMap<String, String> redisSimulation = new ConcurrentHashMap<>();

    @Override
    public Promise<Void> write(String tenantId, String jobId, JobRecord job) {
        String key = buildKey(tenantId, jobId);
        // In production: serialize job to JSON and store in Redis
        redisSimulation.put(key, job.toString());
        logger.trace("Wrote job {} to Redis (key: {})", jobId, key);
        return Promise.complete();
    }

    @Override
    public Promise<Optional<JobRecord>> read(String tenantId, String jobId) {
        String key = buildKey(tenantId, jobId);
        // In production: retrieve from Redis and deserialize
        String value = redisSimulation.get(key);
        logger.trace("Read job {} from Redis (key: {})", jobId, key);
        // Mock: return empty (would deserialize in production)
        return Promise.of(Optional.empty());
    }

    @Override
    public Promise<Void> delete(String tenantId, String jobId) {
        String key = buildKey(tenantId, jobId);
        redisSimulation.remove(key);
        logger.trace("Deleted job {} from Redis (key: {})", jobId, key);
        return Promise.complete();
    }

    @Override
    public Promise<Boolean> isHealthy() {
        // Mock: always healthy
        return Promise.of(true);
    }

    private String buildKey(String tenantId, String jobId) {
        return String.format("job:%s:%s", tenantId, jobId);
    }

    public int getStoredJobCount() {
        return redisSimulation.size();
    }
}
