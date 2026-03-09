package com.ghatana.refactorer.server.jobs;

import java.util.Objects;

/**
 * Factory for creating job store implementations.

 *

 * <p>Supports both in-memory and hybrid (local+central) implementations

 * based on configuration.</p>

 *

 * @doc.type class

 * @doc.purpose Build concrete JobStore instances (in-memory, Redis, Postgres) per environment.

 * @doc.layer product

 * @doc.pattern Factory

 */

public final class JobStoreFactory {

    private JobStoreFactory() {
        // Factory class
    }

    /**
     * Job store mode: in-memory only or hybrid with central backend.
     */
    public enum JobStoreMode {
        /**
 * In-memory only (for testing/development) */
        IN_MEMORY,
        /**
 * Hybrid with Redis central store (production) */
        HYBRID_REDIS,
        /**
 * Hybrid with PostgreSQL central store (alternative production) */
        HYBRID_POSTGRES
    }

    /**
     * Create a job store based on the configured mode.
     *
     * @param mode the job store mode
     * @param tenantExtractor the tenant context extractor
     * @return configured job store instance
     */
    public static JobStore create(
            JobStoreMode mode, HybridJobStore.TenantContextExtractor tenantExtractor) {
        Objects.requireNonNull(mode, "mode must not be null");
        Objects.requireNonNull(tenantExtractor, "tenantExtractor must not be null");

        switch (mode) {
            case IN_MEMORY:
                return new InMemoryJobStore();

            case HYBRID_REDIS:
                HybridJobStore.CentralStateStore redisStore = new RedisCentralJobStore();
                return new HybridJobStore(
                        redisStore,
                        HybridJobStore.SyncStrategy.BATCHED,
                        tenantExtractor);

            case HYBRID_POSTGRES:
                // PostgreSQL implementation would go here
                HybridJobStore.CentralStateStore postgresStore =
                        new PostgreSQLCentralJobStore();
                return new HybridJobStore(
                        postgresStore,
                        HybridJobStore.SyncStrategy.BATCHED,
                        tenantExtractor);

            default:
                throw new IllegalArgumentException("Unsupported job store mode: " + mode);
        }
    }

    /**
     * Create a hybrid job store with custom sync strategy.
     *
     * @param centralStore the central state store
     * @param syncStrategy the sync strategy
     * @param tenantExtractor the tenant context extractor
     * @return configured hybrid job store
     */
    public static JobStore createHybrid(
            HybridJobStore.CentralStateStore centralStore,
            HybridJobStore.SyncStrategy syncStrategy,
            HybridJobStore.TenantContextExtractor tenantExtractor) {
        return new HybridJobStore(centralStore, syncStrategy, tenantExtractor);
    }
}
