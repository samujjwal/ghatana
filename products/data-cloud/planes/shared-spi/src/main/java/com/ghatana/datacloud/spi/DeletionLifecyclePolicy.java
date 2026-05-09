package com.ghatana.datacloud.spi;

import java.time.Duration;
import java.time.Period;
import java.util.Objects;

/**
 * Deletion lifecycle policy for determining deletion modes and retention periods (DC-BE-004).
 *
 * <p>Defines the rules for selecting the appropriate deletion mode based on deployment mode,
 * data sensitivity, and governance requirements. Also defines retention periods for each
 * deletion mode to ensure data is retained according to policy.
 *
 * <h2>DC-BE-004: Deletion Lifecycle Standardization</h2>
 * This policy provides a centralized, configurable way to determine how data should be deleted
 * across all data planes:
 * - Entity plane (collections, entities)
 * - Event plane (event logs, event streams)
 * - Pipeline plane (pipelines, checkpoints)
 * - Governance plane (audit logs, compliance records)
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * DeletionLifecyclePolicy policy = DeletionLifecyclePolicy.production();
 * DeletionMode mode = policy.getDeletionMode("my-collection", DeploymentMode.PRODUCTION);
 * Duration retention = policy.getRetentionPeriod(mode);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Centralized deletion lifecycle policy for standardized data deletion
 * @doc.layer spi
 * @doc.pattern Policy
 */
public final class DeletionLifecyclePolicy {

    private final DeploymentMode deploymentMode;
    private final boolean enableSoftDelete;
    private final boolean enableArchive;
    private final boolean enableRetentionPurge;

    // Default retention periods for each deletion mode
    private final Duration softDeleteRetention;
    private final Duration archiveRetention;
    private final Duration complianceRetention;

    private DeletionLifecyclePolicy(Builder builder) {
        this.deploymentMode = builder.deploymentMode;
        this.enableSoftDelete = builder.enableSoftDelete;
        this.enableArchive = builder.enableArchive;
        this.enableRetentionPurge = builder.enableRetentionPurge;
        this.softDeleteRetention = builder.softDeleteRetention;
        this.archiveRetention = builder.archiveRetention;
        this.complianceRetention = builder.complianceRetention;
    }

    /**
     * Creates a production-ready deletion lifecycle policy.
     *
     * <p>Production policy:
     * - Soft delete enabled (30-day retention)
     * - Archive enabled (1-year retention)
     * - Retention purge enabled (7-year compliance retention)
     *
     * @return production deletion lifecycle policy
     */
    public static DeletionLifecyclePolicy production() {
        return new Builder()
                .deploymentMode(DeploymentMode.PRODUCTION)
                .enableSoftDelete(true)
                .enableArchive(true)
                .enableRetentionPurge(true)
                .softDeleteRetention(Duration.ofDays(30))
                .archiveRetention(Duration.ofDays(365))
                .complianceRetention(Duration.ofDays(365 * 7))
                .build();
    }

    /**
     * Creates a development deletion lifecycle policy.
     *
     * <p>Development policy:
     * - Hard delete only (immediate removal)
     * - No soft delete, archive, or retention purge
     *
     * @return development deletion lifecycle policy
     */
    public static DeletionLifecyclePolicy development() {
        return new Builder()
                .deploymentMode(DeploymentMode.DEVELOPMENT)
                .enableSoftDelete(false)
                .enableArchive(false)
                .enableRetentionPurge(false)
                .build();
    }

    /**
     * Creates a testing deletion lifecycle policy.
     *
     * <p>Testing policy:
     * - Hard delete only (immediate removal for test isolation)
     * - No soft delete, archive, or retention purge
     *
     * @return testing deletion lifecycle policy
     */
    public static DeletionLifecyclePolicy testing() {
        return new Builder()
                .deploymentMode(DeploymentMode.TESTING)
                .enableSoftDelete(false)
                .enableArchive(false)
                .enableRetentionPurge(false)
                .build();
    }

    /**
     * Determines the appropriate deletion mode for a given collection.
     *
     * <p>Logic:
     * - Development/Testing: Always HARD_DELETE
     * - Production with soft delete enabled: SOFT_DELETE
     * - Production with soft delete disabled: HARD_DELETE
     *
     * @param collectionName the collection name
     * @return the deletion mode to use
     */
    public DeletionMode getDeletionMode(String collectionName) {
        Objects.requireNonNull(collectionName, "collectionName");

        if (deploymentMode == DeploymentMode.DEVELOPMENT || deploymentMode == DeploymentMode.TESTING) {
            return DeletionMode.HARD_DELETE;
        }

        // Production mode
        if (enableSoftDelete) {
            return DeletionMode.SOFT_DELETE;
        }

        return DeletionMode.HARD_DELETE;
    }

    /**
     * Gets the retention period for a given deletion mode.
     *
     * <p>Retention periods:
     * - SOFT_DELETE: 30 days (configurable)
     * - ARCHIVE: 1 year (configurable)
     * - RETENTION_PURGE: 7 years (configurable)
     * - HARD_DELETE: 0 (immediate)
     *
     * @param mode the deletion mode
     * @return the retention period
     */
    public Duration getRetentionPeriod(DeletionMode mode) {
        Objects.requireNonNull(mode, "mode");

        return switch (mode) {
            case SOFT_DELETE -> softDeleteRetention;
            case ARCHIVE -> archiveRetention;
            case RETENTION_PURGE -> complianceRetention;
            case HARD_DELETE -> Duration.ZERO;
        };
    }

    /**
     * Checks if a deletion mode is enabled in this policy.
     *
     * @param mode the deletion mode
     * @return true if the mode is enabled
     */
    public boolean isModeEnabled(DeletionMode mode) {
        return switch (mode) {
            case SOFT_DELETE -> enableSoftDelete;
            case ARCHIVE -> enableArchive;
            case RETENTION_PURGE -> enableRetentionPurge;
            case HARD_DELETE -> true; // Hard delete is always enabled
        };
    }

    /**
     * Deployment mode for the policy.
     */
    public enum DeploymentMode {
        PRODUCTION,
        DEVELOPMENT,
        TESTING,
        EMBEDDED
    }

    /**
     * Builder for creating custom deletion lifecycle policies.
     */
    public static class Builder {
        private DeploymentMode deploymentMode = DeploymentMode.PRODUCTION;
        private boolean enableSoftDelete = true;
        private boolean enableArchive = true;
        private boolean enableRetentionPurge = true;
        private Duration softDeleteRetention = Duration.ofDays(30);
        private Duration archiveRetention = Duration.ofDays(365);
        private Duration complianceRetention = Duration.ofDays(365 * 7);

        public Builder deploymentMode(DeploymentMode deploymentMode) {
            this.deploymentMode = Objects.requireNonNull(deploymentMode, "deploymentMode");
            return this;
        }

        public Builder enableSoftDelete(boolean enableSoftDelete) {
            this.enableSoftDelete = enableSoftDelete;
            return this;
        }

        public Builder enableArchive(boolean enableArchive) {
            this.enableArchive = enableArchive;
            return this;
        }

        public Builder enableRetentionPurge(boolean enableRetentionPurge) {
            this.enableRetentionPurge = enableRetentionPurge;
            return this;
        }

        public Builder softDeleteRetention(Duration duration) {
            this.softDeleteRetention = Objects.requireNonNull(duration, "duration");
            return this;
        }

        public Builder archiveRetention(Duration duration) {
            this.archiveRetention = Objects.requireNonNull(duration, "duration");
            return this;
        }

        public Builder complianceRetention(Duration duration) {
            this.complianceRetention = Objects.requireNonNull(duration, "duration");
            return this;
        }

        public DeletionLifecyclePolicy build() {
            return new DeletionLifecyclePolicy(this);
        }
    }
}
