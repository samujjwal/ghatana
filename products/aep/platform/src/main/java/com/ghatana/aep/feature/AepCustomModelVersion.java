/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.feature;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Extended model version descriptor for AEP custom model artifacts.
 *
 * <p>While {@link com.ghatana.aiplatform.registry.ModelMetadata} tracks deployment
 * lifecycle metadata, {@code AepCustomModelVersion} captures the full
 * <em>artifact provenance</em> needed for deterministic reproducibility:
 * <ul>
 *   <li>SHA-256 artifact checksum — detects tampering or corruption</li>
 *   <li>Artifact storage URI — where the model weights/ONNX file live (S3, GCS, …)</li>
 *   <li>Git commit SHA — links the version to the exact training code</li>
 *   <li>Training dataset hash — reproducibility of training data</li>
 *   <li>Hyperparameters — configuration snapshot used during training</li>
 *   <li>Validation thresholds — gates that must pass before a canary or production promotion</li>
 * </ul>
 *
 * @param id                  surrogate identifier
 * @param tenantId            owning tenant
 * @param modelId             FK to {@code model_registry.id}
 * @param modelName           logical model name (duplicate for fast lookup)
 * @param version             semantic version string (e.g. {@code "v3.1.0"})
 * @param artifactUri         URI to the model artifact (S3/GCS path)
 * @param artifactSha256      hex-encoded SHA-256 checksum of the artifact
 * @param gitCommitSha        commit that produced this artifact
 * @param trainingDatasetHash SHA-256 of the training dataset manifest
 * @param hyperparameters     training hyperparameter snapshot (key → value)
 * @param validationThresholds minimum metric values required for promotion
 *                             (e.g. {@code {"f1_score": 0.90, "auc_roc": 0.95}})
 * @param createdAt           when this version record was created
 *
 * @doc.type record
 * @doc.purpose Extended artifact provenance and validation thresholds for AEP custom models
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record AepCustomModelVersion(
        UUID id,
        String tenantId,
        UUID modelId,
        String modelName,
        String version,
        String artifactUri,
        String artifactSha256,
        String gitCommitSha,
        String trainingDatasetHash,
        Map<String, String> hyperparameters,
        Map<String, Double> validationThresholds,
        Instant createdAt
) {
    public AepCustomModelVersion {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(modelName, "modelName");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(createdAt, "createdAt");
        hyperparameters = hyperparameters != null ? Map.copyOf(hyperparameters) : Map.of();
        validationThresholds = validationThresholds != null
                ? Map.copyOf(validationThresholds) : Map.of();
    }

    /**
     * Convenience factory for common AEP use cases.
     *
     * @param tenantId            owning tenant
     * @param modelId             FK to model_registry.id
     * @param modelName           logical model name
     * @param version             semantic version string
     * @param artifactUri         storage URI for the artifact
     * @param artifactSha256      SHA-256 checksum
     * @param hyperparameters     training hyperparameters
     * @param validationThresholds promotion gates
     * @return new version record with generated id and current timestamp
     */
    public static AepCustomModelVersion of(
            String tenantId, UUID modelId, String modelName, String version,
            String artifactUri, String artifactSha256,
            Map<String, String> hyperparameters,
            Map<String, Double> validationThresholds) {
        return new AepCustomModelVersion(
                UUID.randomUUID(), tenantId, modelId, modelName, version,
                artifactUri, artifactSha256, null, null,
                hyperparameters, validationThresholds, Instant.now());
    }
}
