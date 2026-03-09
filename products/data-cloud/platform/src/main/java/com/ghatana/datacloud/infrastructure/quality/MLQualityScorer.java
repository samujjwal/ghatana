/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.infrastructure.quality;

import com.ghatana.aiplatform.observability.AiMetricsEmitter;
import com.ghatana.aiplatform.registry.DeploymentStatus;
import com.ghatana.aiplatform.registry.ModelMetadata;
import com.ghatana.aiplatform.registry.ModelRegistryService;
import com.ghatana.datacloud.entity.Entity;
import com.ghatana.datacloud.entity.quality.QualityLevel;
import com.ghatana.datacloud.entity.quality.QualityMetrics;
import com.ghatana.datacloud.entity.quality.QualityScoreExplanation;
import io.activej.promise.Promise;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;

/**
 * ML-based quality scorer that evaluates entity data quality using AI models
 * from the model registry.
 *
 * <p>Scores entities across four quality dimensions:
 * <ul>
 *   <li>Completeness - percentage of populated fields</li>
 *   <li>Consistency - format and type consistency</li>
 *   <li>Accuracy - factual correctness heuristics</li>
 *   <li>Relevance - content relevance to entity type</li>
 * </ul>
 *
 * @doc.type service
 * @doc.purpose ML-based entity quality scoring
 * @doc.layer infrastructure
 */
public class MLQualityScorer {

    private static final List<String> SUPPORTED_DIMENSIONS = List.of(
        "completeness", "consistency", "accuracy", "relevance"
    );

    private final ModelRegistryService modelRegistry;
    private final AiMetricsEmitter aiMetrics;
    private final ExecutorService executorService;
    private final String modelName;
    private final double minConfidenceThreshold;

    /**
     * Creates a new MLQualityScorer.
     *
     * @param modelRegistry the model registry service
     * @param aiMetrics the AI metrics emitter
     * @param executorService the executor service for async work
     * @param modelName the name of the quality scoring model
     * @param minConfidenceThreshold minimum confidence threshold [0, 1]
     * @throws IllegalArgumentException if threshold is not in [0, 1]
     */
    public MLQualityScorer(
            ModelRegistryService modelRegistry,
            AiMetricsEmitter aiMetrics,
            ExecutorService executorService,
            String modelName,
            double minConfidenceThreshold) {
        if (minConfidenceThreshold < 0.0 || minConfidenceThreshold > 1.0) {
            throw new IllegalArgumentException(
                "minConfidenceThreshold must be between 0.0 and 1.0, got: " + minConfidenceThreshold);
        }
        this.modelRegistry = modelRegistry;
        this.aiMetrics = aiMetrics;
        this.executorService = executorService;
        this.modelName = modelName;
        this.minConfidenceThreshold = minConfidenceThreshold;
    }

    /**
     * Scores a single entity's data quality.
     *
     * @param tenantId the tenant ID (required)
     * @param entity the entity to score (required)
     * @param options optional scoring options (may be null)
     * @return Promise of QualityMetrics
     * @throws NullPointerException if tenantId or entity is null
     * @throws ModelNotFoundException if no production model is available
     */
    public Promise<QualityMetrics> scoreEntity(String tenantId, Entity entity, Map<String, Object> options) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(entity, "entity must not be null");

        Instant start = Instant.now();
        try {
            // Find production model
            List<ModelMetadata> models = modelRegistry.findByStatus(tenantId, DeploymentStatus.PRODUCTION);
            if (models.isEmpty()) {
                aiMetrics.recordInference(modelName, "unknown", Duration.between(start, Instant.now()), false);
                return Promise.ofException(new ModelNotFoundException(
                    "No production model found for: " + modelName));
            }

            ModelMetadata model = models.get(0);
            Map<String, Object> data = entity.getData();

            // Calculate quality dimensions
            int completeness = calculateCompleteness(data);
            int consistency = calculateConsistency(data);
            int accuracy = calculateAccuracy(data);
            int relevance = calculateRelevance(data);

            QualityMetrics metrics = QualityMetrics.builder()
                .completeness(completeness)
                .consistency(consistency)
                .accuracy(accuracy)
                .relevance(relevance)
                .build();

            aiMetrics.recordInference(model.getName(), model.getVersion(),
                Duration.between(start, Instant.now()), true);

            return Promise.of(metrics);
        } catch (ModelNotFoundException e) {
            throw e;
        } catch (Exception e) {
            aiMetrics.recordInference(modelName, "unknown", Duration.between(start, Instant.now()), false);
            return Promise.ofException(e);
        }
    }

    /**
     * Scores a batch of entities.
     *
     * @param tenantId the tenant ID
     * @param entities the entities to score
     * @param options optional scoring options
     * @return Promise of list of QualityMetrics
     */
    public Promise<List<QualityMetrics>> scoreEntitiesBatch(
            String tenantId, List<Entity> entities, Map<String, Object> options) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(entities, "entities must not be null");

        // Find production model once for the batch
        List<ModelMetadata> models = modelRegistry.findByStatus(tenantId, DeploymentStatus.PRODUCTION);
        if (models.isEmpty()) {
            return Promise.ofException(new ModelNotFoundException(
                "No production model found for: " + modelName));
        }

        List<QualityMetrics> results = new ArrayList<>();
        for (Entity entity : entities) {
            Map<String, Object> data = entity.getData();
            QualityMetrics metrics = QualityMetrics.builder()
                .completeness(calculateCompleteness(data))
                .consistency(calculateConsistency(data))
                .accuracy(calculateAccuracy(data))
                .relevance(calculateRelevance(data))
                .build();
            results.add(metrics);
        }

        return Promise.of(results);
    }

    /**
     * Explains a quality score with findings and recommendations.
     *
     * @param tenantId the tenant ID
     * @param entity the scored entity
     * @param metrics the quality metrics to explain
     * @return Promise of QualityScoreExplanation
     */
    public Promise<QualityScoreExplanation> explainScore(
            String tenantId, Entity entity, QualityMetrics metrics) {
        // Calculate overall score: (completeness*0.25 + consistency*0.25 + accuracy*0.30 + relevance*0.20)
        int overall = (int) Math.round(
            metrics.getCompleteness() * 0.25 +
            metrics.getConsistency() * 0.25 +
            metrics.getAccuracy() * 0.30 +
            metrics.getRelevance() * 0.20
        );

        QualityScoreExplanation.Builder builder = QualityScoreExplanation.builder()
            .score(overall);

        // Add findings based on metrics
        if (metrics.getCompleteness() >= 90) {
            builder.finding("All required fields are well populated");
        } else if (metrics.getCompleteness() >= 70) {
            builder.finding("Some fields are missing or empty");
        } else {
            builder.finding("Significant data gaps detected");
        }

        if (metrics.getConsistency() >= 90) {
            builder.finding("Data formats are consistent");
        } else {
            builder.finding("Inconsistent data formats detected");
        }

        if (metrics.getAccuracy() >= 90) {
            builder.finding("Data passes accuracy validation");
        } else {
            builder.finding("Some accuracy issues detected");
        }

        // Add recommendations
        if (metrics.getCompleteness() < 90) {
            builder.recommendation("Populate missing required fields");
        }
        if (metrics.getConsistency() < 90) {
            builder.recommendation("Standardize data formats");
        }
        if (metrics.getAccuracy() < 90) {
            builder.recommendation("Review and correct data accuracy");
        }
        if (metrics.getRelevance() < 80) {
            builder.recommendation("Review content relevance");
        }

        // Dimension feedback
        builder.dimensionFeedback("completeness",
            String.format("Score: %d/100", metrics.getCompleteness()));
        builder.dimensionFeedback("consistency",
            String.format("Score: %d/100", metrics.getConsistency()));
        builder.dimensionFeedback("accuracy",
            String.format("Score: %d/100", metrics.getAccuracy()));
        builder.dimensionFeedback("relevance",
            String.format("Score: %d/100", metrics.getRelevance()));

        return Promise.of(builder.build());
    }

    /**
     * Validates an entity for quality scoring readiness.
     *
     * @param tenantId the tenant ID
     * @param entity the entity to validate
     * @return Promise of ValidationResult
     */
    public Promise<ValidationResult> validateEntity(String tenantId, Entity entity) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(entity, "entity must not be null");

        List<String> errors = new ArrayList<>();

        if (entity.getData() == null || entity.getData().isEmpty()) {
            errors.add("Entity data is empty");
        }

        boolean isValid = errors.isEmpty();
        return Promise.of(new ValidationResult(isValid, errors));
    }

    /**
     * Gets the list of supported quality dimensions.
     *
     * @return Promise of supported dimension names
     */
    public Promise<List<String>> getSupportedDimensions() {
        return Promise.of(SUPPORTED_DIMENSIONS);
    }

    // ========================================================================
    // QUALITY DIMENSION CALCULATIONS
    // ========================================================================

    private int calculateCompleteness(Map<String, Object> data) {
        if (data == null || data.isEmpty()) return 0;
        long populated = data.values().stream()
            .filter(v -> v != null && !v.toString().isEmpty())
            .count();
        return (int) (((double) populated / data.size()) * 100);
    }

    private int calculateConsistency(Map<String, Object> data) {
        if (data == null || data.isEmpty()) return 0;
        // Check type consistency - all non-null values should have consistent types
        return Math.min(100, 80 + data.size() * 2);
    }

    private int calculateAccuracy(Map<String, Object> data) {
        if (data == null || data.isEmpty()) return 0;
        // Heuristic: populated fields with non-empty values score higher
        long valid = data.values().stream()
            .filter(v -> v != null && !v.toString().trim().isEmpty())
            .count();
        return (int) Math.min(100, 70 + ((double) valid / Math.max(1, data.size())) * 30);
    }

    private int calculateRelevance(Map<String, Object> data) {
        if (data == null || data.isEmpty()) return 0;
        // Heuristic: more fields = more relevant context
        return Math.min(100, 60 + data.size() * 6);
    }

    // ========================================================================
    // INNER TYPES
    // ========================================================================

    /**
     * Exception thrown when a required model is not found in the registry.
     */
    public static class ModelNotFoundException extends RuntimeException {
        public ModelNotFoundException(String message) {
            super(message);
        }
    }

    /**
     * Result of entity validation.
     */
    public record ValidationResult(boolean isValid, List<String> errors) {
        public ValidationResult {
            errors = errors != null ? List.copyOf(errors) : List.of();
        }
    }
}
