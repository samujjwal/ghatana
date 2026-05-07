/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.learning;

import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for model versioning and rollback.
 *
 * <p>Manages model versions, tags, and rollback capabilities.
 *
 * @doc.type interface
 * @doc.purpose Model version management and rollback
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface ModelVersionRepository {

    /**
     * Save a new model version.
     *
     * @param version model version
     * @return promise of saved version
     */
    Promise<ModelVersion> save(ModelVersion version);

    /**
     * Get version by ID.
     *
     * @param versionId version identifier
     * @return promise of version if found
     */
    Promise<Optional<ModelVersion>> findById(String versionId);

    /**
     * Get latest version for model.
     *
     * @param modelId model identifier
     * @return promise of latest version
     */
    Promise<Optional<ModelVersion>> findLatest(String modelId);

    /**
     * Get version by tag.
     *
     * @param modelId model identifier
     * @param tag version tag
     * @return promise of tagged version
     */
    Promise<Optional<ModelVersion>> findByTag(String modelId, String tag);

    /**
     * List all versions for model.
     *
     * @param modelId model identifier
     * @return promise of version list
     */
    Promise<List<ModelVersion>> listVersions(String modelId);

    /**
     * Tag a version.
     *
     * @param versionId version identifier
     * @param tag tag name
     * @return promise completing when tagged
     */
    Promise<Void> tagVersion(String versionId, String tag);

    /**
     * Remove tag from version.
     *
     * @param modelId model identifier
     * @param tag tag name
     * @return promise completing when removed
     */
    Promise<Void> removeTag(String modelId, String tag);

    /**
     * Rollback to version.
     *
     * @param modelId model identifier
     * @param versionId version to rollback to
     * @return promise of current version after rollback
     */
    Promise<ModelVersion> rollbackTo(String modelId, String versionId);

    /**
     * Delete a version.
     *
     * @param versionId version identifier
     * @return promise of true if deleted
     */
    Promise<Boolean> deleteVersion(String versionId);

    /**
     * Model version.
     */
    record ModelVersion(
        String id,
        String modelId,
        String tenantId,
        String versionNumber,
        List<String> tags,
        String description,
        String artifactPath,
        long sizeBytes,
        ModelMetrics metrics,
        String trainingJobId,
        Instant createdAt,
        String createdBy,
        boolean isCurrent
    ) {
        /**
         * Check if version has tag.
         */
        public boolean hasTag(String tag) {
            return tags != null && tags.contains(tag);
        }

        /**
         * Check if production version.
         */
        public boolean isProduction() {
            return hasTag("production") || hasTag("prod");
        }
    }

    /**
     * Model metrics.
     */
    record ModelMetrics(
        double accuracy,
        double precision,
        double recall,
        double f1Score,
        double loss,
        int trainingSamples,
        int validationSamples,
        long trainingDurationMs
    ) {
        public boolean meetsThreshold(double minAccuracy, double minF1) {
            return accuracy >= minAccuracy && f1Score >= minF1;
        }
    }
}
