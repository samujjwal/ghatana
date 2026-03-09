package com.ghatana.datacloud.application.service;

import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.datacloud.entity.Entity;
import com.ghatana.datacloud.entity.quality.QualityLevel;
import com.ghatana.datacloud.entity.quality.QualityMetrics;
import com.ghatana.datacloud.entity.quality.QualityScoreExplanation;
import com.ghatana.datacloud.entity.quality.QualityScorer;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Application service orchestrating entity quality scoring.
 *
 * <p><b>Purpose</b><br>
 * Manages quality scoring workflow including entity validation, score generation,
 * explanation generation, and metrics collection. Ensures tenant isolation and
 * consistent error handling across scoring operations.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * QualityScoringService service = new QualityScoringService(
 *     qualityScorer,
 *     metricsCollector
 * );
 *
 * QualityScoringService.ScoringResponse response = runPromise(() ->
 *     service.scoreEntity("tenant-123", entity)
 * );
 *
 * if (response.isSuccess()) {
 *     QualityMetrics metrics = response.getMetrics();
 *     QualityLevel level = metrics.getQualityLevel();
 * }
 * }</pre>
 *
 * <p><b>Responsibilities</b><br>
 * - Validate entity before scoring
 * - Delegate to QualityScorer for metric calculation
 * - Generate detailed explanations
 * - Collect scoring metrics (timing, results)
 * - Aggregate batch results with error handling
 * - Maintain tenant isolation throughout workflow
 *
 * @doc.type class
 * @doc.purpose Application service for entity quality scoring
 * @doc.layer product
 * @doc.pattern Service
 */
public final class QualityScoringService {
  private final QualityScorer qualityScorer;
  private final MetricsCollector metrics;

  /**
   * Constructs QualityScoringService with required dependencies.
   *
   * @param qualityScorer QualityScorer port implementation
   * @param metrics MetricsCollector for observability
   * @throws NullPointerException if qualityScorer or metrics is null
   */
  public QualityScoringService(QualityScorer qualityScorer, MetricsCollector metrics) {
    this.qualityScorer = Objects.requireNonNull(qualityScorer, "Quality scorer must not be null");
    this.metrics = Objects.requireNonNull(metrics, "Metrics collector must not be null");
  }

  /**
   * Scores single entity and returns detailed response.
   *
   * <p>GIVEN: Entity and tenant context
   * WHEN: scoreEntity() called
   * THEN: Entity validated → scored → explained, with metrics recorded
   *
   * @param tenantId tenant identifier for multi-tenant isolation
   * @param entity entity to score
   * @return Promise resolving to ScoringResponse with metrics and explanation
   * @throws NullPointerException if tenantId or entity is null
   */
  public Promise<ScoringResponse> scoreEntity(String tenantId, Entity entity) {
    Objects.requireNonNull(tenantId, "Tenant ID must not be null");
    Objects.requireNonNull(entity, "Entity must not be null");

    long startTime = System.currentTimeMillis();

    return qualityScorer
        .validateEntity(tenantId, entity)
        .then(
            validationResult -> {
              if (!validationResult.isValid()) {
                metrics.incrementCounter("quality.scoring.validation.failed", "tenant", tenantId);
                return Promise.of(
                    ScoringResponse.failure(
                        "Entity validation failed: " + String.join(", ", validationResult.errors())));
              }

              return qualityScorer
                  .scoreEntity(tenantId, entity, Map.of())
                  .then(
                      qualityMetrics ->
                          qualityScorer
                              .explainScore(tenantId, entity, qualityMetrics)
                              .map(
                                  explanation -> {
                                    long duration = System.currentTimeMillis() - startTime;
                                    metrics.recordTimer(
                                        "quality.scoring.duration",
                                        duration,
                                        "tenant",
                                        tenantId,
                                        "level",
                                        qualityMetrics.getQualityLevel().getDisplayName());
                                    metrics.incrementCounter(
                                        "quality.scoring.completed",
                                        "tenant",
                                        tenantId,
                                        "level",
                                        qualityMetrics.getQualityLevel().getDisplayName());

                                    return ScoringResponse.success(
                                        qualityMetrics, explanation, duration);
                                  }),
                      ex -> {
                        metrics.incrementCounter(
                            "quality.scoring.error", "tenant", tenantId, "error",
                            ex.getClass().getSimpleName());
                        return Promise.of(ScoringResponse.failure(ex.getMessage()));
                      });
            },
            ex -> {
              metrics.incrementCounter(
                  "quality.scoring.error", "tenant", tenantId, "error",
                  ex.getClass().getSimpleName());
              return Promise.of(ScoringResponse.failure(ex.getMessage()));
            });
  }

  /**
   * Scores batch of entities efficiently.
   *
   * <p>GIVEN: List of entities and tenant context
   * WHEN: scoreEntitiesBatch() called
   * THEN: Entities validated → scored in sequence → results aggregated
   *
   * @param tenantId tenant identifier for multi-tenant isolation
   * @param entities entities to score (non-empty list)
   * @return Promise resolving to BatchScoringResponse with results and errors
   * @throws NullPointerException if tenantId or entities is null
   * @throws IllegalArgumentException if entities list is empty
   */
  public Promise<BatchScoringResponse> scoreEntitiesBatch(String tenantId, List<Entity> entities) {
    Objects.requireNonNull(tenantId, "Tenant ID must not be null");
    Objects.requireNonNull(entities, "Entities list must not be null");
    if (entities.isEmpty()) {
      throw new IllegalArgumentException("Entities list must not be empty");
    }

    long startTime = System.currentTimeMillis();
    List<ScoringResult> results = new ArrayList<>();
    List<String> errors = new ArrayList<>();

    return scoreEntitiesBatchRecursive(tenantId, entities, 0, results, errors)
        .then(
            v -> {
              long duration = System.currentTimeMillis() - startTime;
              int successCount = (int) results.stream().filter(ScoringResult::isSuccess).count();
              int errorCount = results.size() - successCount;

              metrics.recordTimer(
                  "quality.scoring.batch.duration",
                  duration,
                  "tenant",
                  tenantId,
                  "count",
                  String.valueOf(entities.size()));
              metrics.incrementCounter(
                  "quality.scoring.batch.completed",
                  "tenant",
                  tenantId,
                  "size",
                  String.valueOf(entities.size()));
              metrics.incrementCounter(
                  "quality.scoring.batch.success",
                  "tenant",
                  tenantId,
                  "count",
                  String.valueOf(successCount));
              if (errorCount > 0) {
                metrics.incrementCounter(
                    "quality.scoring.batch.error",
                    "tenant",
                    tenantId,
                    "count",
                    String.valueOf(errorCount));
              }

              return Promise.of(BatchScoringResponse.completed(results, errors, successCount, errorCount,
                  duration));
            },
            ex -> {
              metrics.incrementCounter(
                  "quality.scoring.batch.failed", "tenant", tenantId, "error",
                  ex.getClass().getSimpleName());
              return Promise.of(BatchScoringResponse.failure(ex.getMessage()));
            });
  }

  /**
   * Recursively scores entities in batch, continuing on errors.
   *
   * @param tenantId tenant identifier
   * @param entities entities to score
   * @param index current index
   * @param results accumulated results
   * @param errors accumulated error messages
   * @return Promise resolving when all entities scored
   */
  private Promise<Void> scoreEntitiesBatchRecursive(
      String tenantId,
      List<Entity> entities,
      int index,
      List<ScoringResult> results,
      List<String> errors) {
    if (index >= entities.size()) {
      return Promise.complete();
    }

    Entity currentEntity = entities.get(index);
    return qualityScorer
        .scoreEntity(tenantId, currentEntity, Map.of())
        .then(
            qualityMetrics ->
                qualityScorer
                    .explainScore(tenantId, currentEntity, qualityMetrics)
                    .map(
                        explanation -> {
                          results.add(ScoringResult.success(currentEntity, qualityMetrics,
                              explanation));
                          return null;
                        }),
            ex -> {
              errors.add(
                  String.format("Entity %d: %s", index, ex.getMessage()));
              results.add(ScoringResult.failure(currentEntity, ex.getMessage()));
              return Promise.complete();
            })
        .then(
            v -> scoreEntitiesBatchRecursive(tenantId, entities, index + 1, results, errors));
  }

  /**
   * Updates quality scoring configuration for tenant.
   *
   * @param tenantId tenant identifier
   * @param configuration configuration map
   * @return Promise resolving when configuration updated
   * @throws NullPointerException if tenantId or configuration is null
   */
  public Promise<Void> updateConfiguration(String tenantId, Map<String, Object> configuration) {
    Objects.requireNonNull(tenantId, "Tenant ID must not be null");
    Objects.requireNonNull(configuration, "Configuration must not be null");

    return qualityScorer.updateConfiguration(tenantId, configuration)
        .whenResult(
            v -> {
              metrics.incrementCounter(
                  "quality.scoring.config.updated", "tenant", tenantId);
            })
        .whenException(
            ex -> {
              metrics.incrementCounter(
                  "quality.scoring.config.failed", "tenant", tenantId, "error",
                  ex.getClass().getSimpleName());
            });
  }

  /**
   * Gets current quality scoring configuration for tenant.
   *
   * @param tenantId tenant identifier
   * @return Promise resolving to configuration map
   * @throws NullPointerException if tenantId is null
   */
  public Promise<Map<String, Object>> getConfiguration(String tenantId) {
    Objects.requireNonNull(tenantId, "Tenant ID must not be null");
    return qualityScorer.getConfiguration(tenantId);
  }

  /**
   * Gets supported quality scoring dimensions.
   *
   * @return Promise resolving to list of dimension names
   */
  public Promise<List<String>> getSupportedDimensions() {
    return qualityScorer.getSupportedDimensions();
  }

  /**
   * Response from single entity scoring operation.
   *
   * @param metrics quality metrics if successful, null if failed
   * @param explanation quality score explanation if successful, null if failed
   * @param durationMillis time taken to score entity
   * @param isSuccess whether scoring succeeded
   * @param errorMessage error message if failed, null if successful
   */
  public record ScoringResponse(
      QualityMetrics metrics,
      QualityScoreExplanation explanation,
      long durationMillis,
      boolean isSuccess,
      String errorMessage) {

    /**
     * Creates successful scoring response.
     *
     * @param metrics quality metrics
     * @param explanation score explanation
     * @param durationMillis scoring duration
     * @return ScoringResponse indicating success
     */
    public static ScoringResponse success(
        QualityMetrics metrics, QualityScoreExplanation explanation, long durationMillis) {
      return new ScoringResponse(metrics, explanation, durationMillis, true, null);
    }

    /**
     * Creates failed scoring response.
     *
     * @param errorMessage error description
     * @return ScoringResponse indicating failure
     */
    public static ScoringResponse failure(String errorMessage) {
      return new ScoringResponse(null, null, 0, false, errorMessage);
    }

    /**
     * Gets quality level if successful.
     *
     * @return quality level, or null if failed
     */
    public QualityLevel getQualityLevel() {
      return isSuccess ? metrics.getQualityLevel() : null;
    }
  }

  /**
   * Result for individual entity in batch scoring.
   *
   * @param entity entity being scored
   * @param metrics quality metrics if successful, null if failed
   * @param explanation score explanation if successful, null if failed
   * @param isSuccess whether scoring succeeded
   * @param errorMessage error message if failed, null if successful
   */
  public record ScoringResult(
      Entity entity,
      QualityMetrics metrics,
      QualityScoreExplanation explanation,
      boolean isSuccess,
      String errorMessage) {

    /**
     * Creates successful scoring result.
     *
     * @param entity entity that was scored
     * @param metrics quality metrics
     * @param explanation score explanation
     * @return ScoringResult indicating success
     */
    public static ScoringResult success(
        Entity entity, QualityMetrics metrics, QualityScoreExplanation explanation) {
      return new ScoringResult(entity, metrics, explanation, true, null);
    }

    /**
     * Creates failed scoring result.
     *
     * @param entity entity that could not be scored
     * @param errorMessage error description
     * @return ScoringResult indicating failure
     */
    public static ScoringResult failure(Entity entity, String errorMessage) {
      return new ScoringResult(entity, null, null, false, errorMessage);
    }
  }

  /**
   * Response from batch entity scoring operation.
   *
   * @param results list of individual scoring results
   * @param errors accumulated error messages
   * @param successCount number of successful scores
   * @param errorCount number of failed scores
   * @param durationMillis total time taken
   * @param isSuccess whether batch operation completed successfully
   * @param errorMessage error message if batch failed, null if successful
   */
  public record BatchScoringResponse(
      List<ScoringResult> results,
      List<String> errors,
      int successCount,
      int errorCount,
      long durationMillis,
      boolean isSuccess,
      String errorMessage) {

    /**
     * Creates successful batch response.
     *
     * @param results list of individual results
     * @param errors list of error messages
     * @param successCount successful count
     * @param errorCount failed count
     * @param durationMillis total duration
     * @return BatchScoringResponse indicating success
     */
    public static BatchScoringResponse completed(
        List<ScoringResult> results,
        List<String> errors,
        int successCount,
        int errorCount,
        long durationMillis) {
      return new BatchScoringResponse(
          List.copyOf(results), List.copyOf(errors), successCount, errorCount, durationMillis,
          true, null);
    }

    /**
     * Creates failed batch response.
     *
     * @param errorMessage error description
     * @return BatchScoringResponse indicating failure
     */
    public static BatchScoringResponse failure(String errorMessage) {
      return new BatchScoringResponse(
          List.of(), List.of(), 0, 0, 0, false, errorMessage);
    }

    /**
     * Gets total count of entities scored.
     *
     * @return total count (success + error)
     */
    public int getTotalCount() {
      return successCount + errorCount;
    }

    /**
     * Gets success rate as percentage.
     *
     * @return success rate 0-100, or 0 if no entities
     */
    public double getSuccessRate() {
      if (getTotalCount() == 0) {
        return 0;
      }
      return (successCount * 100.0) / getTotalCount();
    }
  }
}
