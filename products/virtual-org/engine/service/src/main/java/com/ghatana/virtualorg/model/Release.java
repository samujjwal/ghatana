package com.ghatana.virtualorg.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Deployment release metadata.
 *
 * <p><b>Purpose</b><br>
 * Represents a software release for deployment workflows.
 * Tracks version, environment, scheduling, and metadata.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * Release release = Release.builder()
 *     .releaseId("release-2025-10-30-001")
 *     .version("1.5.0")
 *     .environment("production")
 *     .scheduledTime(Instant.now().plusSeconds(3600))
 *     .metadata(Map.of(
 *         "jira_ticket", "PROJ-123",
 *         "approver", "cto@company.com"
 *     ))
 *     .build();
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * Value object for deployment pipelines. Used by:
 * - DeploymentPipelineWorkflow for release tracking
 * - QA workflows for version validation
 * - DevOps workflows for deployment orchestration
 *
 * <p><b>Thread Safety</b><br>
 * Immutable value object - thread-safe.
 *
 * @param releaseId Unique release identifier
 * @param version Semantic version (e.g., "1.5.0")
 * @param environment Target environment (staging, production, etc.)
 * @param scheduledTime When deployment is scheduled
 * @param metadata Additional metadata (approvals, tickets, etc.)
 *
 * @doc.type record
 * @doc.purpose Release metadata for deployment workflows
 * @doc.layer product
 * @doc.pattern Value Object
 */
public record Release(
    String releaseId,
    String version,
    String environment,
    Instant scheduledTime,
    Map<String, String> metadata
) {
    /**
     * Compact constructor for validation.
     */
    public Release {
        Objects.requireNonNull(releaseId, "releaseId must not be null");
        Objects.requireNonNull(version, "version must not be null");
        Objects.requireNonNull(environment, "environment must not be null");
        Objects.requireNonNull(scheduledTime, "scheduledTime must not be null");
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }
    
    // Helper methods for workflow compatibility
    public String getVersion() { return version; }
    public String getReleaseManager() { return metadata.getOrDefault("release_manager", "unknown"); }
    public String getType() { return metadata.getOrDefault("release_type", "standard"); }
    public java.util.List<String> getFeatures() { 
        return java.util.List.of(metadata.getOrDefault("features", "").split(","));
    }
    public java.util.List<String> getBugFixes() { 
        return java.util.List.of(metadata.getOrDefault("bug_fixes", "").split(","));
    }
    public Instant getPlannedDeploymentDate() { return scheduledTime; }
    public String getChangeWindow() { return metadata.getOrDefault("change_window", "standard"); }
    public String getRiskLevel() { return metadata.getOrDefault("risk_level", "medium"); }
    
    /**
     * Creates a builder for Release.
     *
     * @return New Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder for Release instances.
     */
    public static class Builder {
        private String releaseId;
        private String version;
        private String environment;
        private Instant scheduledTime;
        private Map<String, String> metadata = Map.of();
        
        /**
         * Sets the release ID.
         *
         * @param releaseId Unique release identifier
         * @return this builder
         */
        public Builder releaseId(String releaseId) {
            this.releaseId = releaseId;
            return this;
        }
        
        /**
         * Sets the version.
         *
         * @param version Semantic version
         * @return this builder
         */
        public Builder version(String version) {
            this.version = version;
            return this;
        }
        
        /**
         * Sets the target environment.
         *
         * @param environment Environment (staging, production, etc.)
         * @return this builder
         */
        public Builder environment(String environment) {
            this.environment = environment;
            return this;
        }
        
        /**
         * Sets the scheduled deployment time.
         *
         * @param scheduledTime When deployment is scheduled
         * @return this builder
         */
        public Builder scheduledTime(Instant scheduledTime) {
            this.scheduledTime = scheduledTime;
            return this;
        }
        
        /**
         * Sets metadata.
         *
         * @param metadata Additional metadata
         * @return this builder
         */
        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }
        
        /**
         * Adds a single metadata entry.
         *
         * @param key Metadata key
         * @param value Metadata value
         * @return this builder
         */
        public Builder addMetadata(String key, String value) {
            if (this.metadata.isEmpty()) {
                this.metadata = new java.util.HashMap<>();
            } else if (!(this.metadata instanceof java.util.HashMap)) {
                this.metadata = new java.util.HashMap<>(this.metadata);
            }
            ((java.util.HashMap<String, String>) this.metadata).put(key, value);
            return this;
        }
        
        /**
         * Builds the Release instance.
         *
         * @return New Release
         * @throws NullPointerException if required fields are null
         */
        public Release build() {
            return new Release(releaseId, version, environment, scheduledTime, metadata);
        }
    }
}
