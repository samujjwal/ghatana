package com.ghatana.datacloud.entity.quality;

import com.ghatana.datacloud.entity.Entity;
import io.activej.promise.Promise;
import java.util.Map;

/**
 * Port interface for content quality scoring operations.
 *
 * <p>
 * <b>Purpose</b><br>
 * Defines abstraction for scoring entity content quality across multiple
 * dimensions (completeness, consistency, accuracy, relevance). Implementations
 * may use rule-based, ML-based, or hybrid approaches. All operations are
 * Promise-based for async execution.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * Promise<QualityMetrics> metricsPromise = qualityScorer.scoreEntity(
 *     "tenant-123",
 *     entity,
 *     Map.of("includeExplanation", true)
 * );
 *
 * QualityMetrics metrics = metricsPromise.get();
 * Promise<QualityScoreExplanation> explanationPromise = qualityScorer.explainScore(
 *     "tenant-123",
 *     entity,
 *     metrics
 * );
 * }</pre>
 *
 * <p>
 * <b>Scoring Contract</b><br>
 * - All scores are in range [0, 100] - Scores are deterministic for identical
 * input - Multi-tenant isolation: Tenant context must not leak across requests
 * - Error handling: Failed scoring returns rejected Promise
 *
 * @doc.type interface
 * @doc.purpose Port for content quality scoring
 * @doc.layer product
 * @doc.pattern Port
 */
public interface QualityScorer {

    /**
     * Scores entity content quality across all dimensions.
     *
     * <p>
     * Evaluates completeness (field population), consistency (format/type
     * alignment), accuracy (validation compliance), and relevance (context
     * appropriateness).
     *
     * @param tenantId tenant identifier for multi-tenant isolation
     * @param entity entity to score
     * @param context optional context for scoring (e.g., entity type rules,
     * custom weights)
     * @return Promise resolving to QualityMetrics with scores 0-100 for each
     * dimension
     * @throws NullPointerException if tenantId or entity is null
     */
    Promise<QualityMetrics> scoreEntity(String tenantId, Entity entity, Map<String, Object> context);

    /**
     * Scores batch of entities efficiently.
     *
     * <p>
     * Processes multiple entities, returning results in same order as input. If
     * scoring one entity fails, entire batch fails (fail-fast semantics).
     *
     * @param tenantId tenant identifier for multi-tenant isolation
     * @param entities entities to score (non-empty list)
     * @param context optional context for scoring
     * @return Promise resolving to list of QualityMetrics in input order
     * @throws NullPointerException if tenantId or entities list is null
     * @throws IllegalArgumentException if entities list is empty
     */
    Promise<java.util.List<QualityMetrics>> scoreEntitiesBatch(
            String tenantId, java.util.List<Entity> entities, Map<String, Object> context);

    /**
     * Provides detailed explanation for quality score.
     *
     * <p>
     * Generates human-readable findings, recommendations, and
     * dimension-specific feedback explaining why the entity received the given
     * score.
     *
     * @param tenantId tenant identifier for multi-tenant isolation
     * @param entity entity being scored
     * @param metrics quality metrics to explain
     * @return Promise resolving to QualityScoreExplanation with detailed
     * reasoning
     * @throws NullPointerException if tenantId, entity, or metrics is null
     */
    Promise<QualityScoreExplanation> explainScore(
            String tenantId, Entity entity, QualityMetrics metrics);

    /**
     * Updates quality scoring configuration.
     *
     * <p>
     * Allows runtime configuration changes such as custom dimension weights,
     * validation rules, or model parameters (for ML-based scorers).
     *
     * @param tenantId tenant identifier for multi-tenant isolation
     * @param configuration configuration map with updated parameters
     * @return Promise resolving when configuration applied successfully
     * @throws NullPointerException if tenantId or configuration is null
     */
    Promise<Void> updateConfiguration(String tenantId, Map<String, Object> configuration);

    /**
     * Gets current quality scoring configuration.
     *
     * <p>
     * Returns current settings for the tenant including dimension weights,
     * validation rules, and other configuration parameters.
     *
     * @param tenantId tenant identifier for multi-tenant isolation
     * @return Promise resolving to configuration map
     * @throws NullPointerException if tenantId is null
     */
    Promise<Map<String, Object>> getConfiguration(String tenantId);

    /**
     * Gets supported quality dimensions for this scorer.
     *
     * <p>
     * Returns list of scoring dimensions this scorer supports. Default
     * implementation supports: completeness, consistency, accuracy, relevance.
     *
     * @return Promise resolving to list of supported dimension names
     */
    Promise<java.util.List<String>> getSupportedDimensions();

    /**
     * Validates that entity can be scored.
     *
     * <p>
     * Pre-scoring validation to check entity is suitable for quality
     * assessment. Returns validation result without performing full scoring.
     *
     * @param tenantId tenant identifier for multi-tenant isolation
     * @param entity entity to validate
     * @return Promise resolving to ValidationResult
     * @throws NullPointerException if tenantId or entity is null
     */
    Promise<ValidationResult> validateEntity(String tenantId, Entity entity);

    /**
     * Validation result for entity pre-checks.
     *
     * @param isValid whether entity can be scored
     * @param errors list of validation errors (empty if valid)
     */
    record ValidationResult(boolean isValid, java.util.List<String> errors) {

        public ValidationResult  {
            if (errors == null) {
                throw new NullPointerException("Errors list must not be null");
            }
            errors = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(errors));
        }

        /**
         * Creates valid result with no errors.
         *
         * @return ValidationResult with isValid=true and empty errors
         */
        public static ValidationResult valid() {
            return new ValidationResult(true, java.util.List.of());
        }

        /**
         * Creates invalid result with error message.
         *
         * @param error error description
         * @return ValidationResult with isValid=false and single error
         */
        public static ValidationResult invalid(String error) {
            return new ValidationResult(false, java.util.List.of(error));
        }

        /**
         * Creates invalid result with multiple error messages.
         *
         * @param errors error descriptions
         * @return ValidationResult with isValid=false and multiple errors
         */
        public static ValidationResult invalid(java.util.List<String> errors) {
            return new ValidationResult(false, errors);
        }
    }
}
