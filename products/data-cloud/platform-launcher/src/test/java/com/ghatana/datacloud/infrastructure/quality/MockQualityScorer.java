package com.ghatana.datacloud.infrastructure.quality;

import com.ghatana.datacloud.entity.Entity;
import com.ghatana.datacloud.entity.quality.QualityLevel;
import com.ghatana.datacloud.entity.quality.QualityMetrics;
import com.ghatana.datacloud.entity.quality.QualityScoreExplanation;
import com.ghatana.datacloud.entity.quality.QualityScorer;
import io.activej.promise.Promise;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Test adapter for QualityScorer providing configurable mock behavior.
 *
 * <p>
 * <b>Purpose</b><br>
 * Enables testing of QualityScoringService and quality-dependent workflows
 * without depending on actual scoring implementation. Supports configurable
 * responses, error simulation, and tenant-specific behavior.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * MockQualityScorer scorer = new MockQualityScorer()
 *     .respondWith(QualityMetrics.uniform(85))
 *     .forTenant("tenant-123", QualityMetrics.uniform(92))
 *     .throwError(new RuntimeException("Scoring failed"), "tenant-fail")
 *     .supportedDimensions("completeness", "consistency", "accuracy", "relevance");
 *
 * // Use as QualityScorer in tests
 * QualityScoringService service = new QualityScoringService(scorer, metrics);
 * }</pre>
 *
 * <p>
 * <b>Configuration Methods</b><br>
 * - respondWith(QualityMetrics): Set default response - forTenant(tenantId,
 * metrics): Tenant-specific response - throwError(exception, tenantId): Error
 * simulation for tenant - supportedDimensions(String...): Configure available
 * dimensions - configuration(Map): Tenant configuration responses
 *
 * @doc.type class
 * @doc.purpose Mock adapter for QualityScorer
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class MockQualityScorer implements QualityScorer {

    private QualityMetrics defaultMetrics = QualityMetrics.uniform(75);
    private final Map<String, QualityMetrics> tenantMetrics = new HashMap<>();
    private final Map<String, Exception> tenantErrors = new HashMap<>();
    private final Map<String, Map<String, Object>> tenantConfigurations = new HashMap<>();
    private List<String> supportedDimensions
            = List.of("completeness", "consistency", "accuracy", "relevance");
    private String defaultConfiguration = "";

    /**
     * Sets default quality metrics response for all tenants.
     *
     * @param metrics default metrics to return
     * @return this instance for fluent API
     */
    public MockQualityScorer respondWith(QualityMetrics metrics) {
        this.defaultMetrics = Objects.requireNonNull(metrics, "Metrics must not be null");
        return this;
    }

    /**
     * Sets tenant-specific quality metrics response.
     *
     * @param tenantId tenant identifier
     * @param metrics metrics for this tenant
     * @return this instance for fluent API
     */
    public MockQualityScorer forTenant(String tenantId, QualityMetrics metrics) {
        Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        Objects.requireNonNull(metrics, "Metrics must not be null");
        tenantMetrics.put(tenantId, metrics);
        return this;
    }

    /**
     * Configures error response for specific tenant.
     *
     * @param exception exception to throw
     * @param tenantId tenant identifier (if null, error applies to all tenants)
     * @return this instance for fluent API
     */
    public MockQualityScorer throwError(Exception exception, String tenantId) {
        Objects.requireNonNull(exception, "Exception must not be null");
        tenantErrors.put(tenantId != null ? tenantId : "__ALL__", exception);
        return this;
    }

    /**
     * Configures supported quality dimensions.
     *
     * @param dimensions dimension names
     * @return this instance for fluent API
     */
    public MockQualityScorer supportedDimensions(String... dimensions) {
        if (dimensions != null && dimensions.length > 0) {
            this.supportedDimensions = List.of(dimensions);
        }
        return this;
    }

    /**
     * Sets configuration for tenant.
     *
     * @param tenantId tenant identifier
     * @param configuration configuration map
     * @return this instance for fluent API
     */
    public MockQualityScorer configuration(String tenantId, Map<String, Object> configuration) {
        Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        Objects.requireNonNull(configuration, "Configuration must not be null");
        tenantConfigurations.put(tenantId, new HashMap<>(configuration));
        return this;
    }

    /**
     * Gets configured metrics for tenant.
     *
     * @param tenantId tenant identifier
     * @return metrics for tenant, or default if not configured
     */
    public QualityMetrics getTenantMetrics(String tenantId) {
        return tenantMetrics.getOrDefault(tenantId, defaultMetrics);
    }

    /**
     * Sets dynamic response for tenant at runtime.
     *
     * @param tenantId tenant identifier
     * @param metrics new metrics to return
     */
    public void setTenantMetrics(String tenantId, QualityMetrics metrics) {
        Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        Objects.requireNonNull(metrics, "Metrics must not be null");
        tenantMetrics.put(tenantId, metrics);
    }

    @Override
    public Promise<QualityMetrics> scoreEntity(
            String tenantId, Entity entity, Map<String, Object> context) {
        Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        Objects.requireNonNull(entity, "Entity must not be null");

        // Check for configured error
        if (tenantErrors.containsKey(tenantId)) {
            return Promise.ofException(tenantErrors.get(tenantId));
        }
        if (tenantErrors.containsKey("__ALL__")) {
            return Promise.ofException(tenantErrors.get("__ALL__"));
        }

        return Promise.of(getTenantMetrics(tenantId));
    }

    @Override
    public Promise<List<QualityMetrics>> scoreEntitiesBatch(
            String tenantId, List<Entity> entities, Map<String, Object> context) {
        Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        Objects.requireNonNull(entities, "Entities list must not be null");
        if (entities.isEmpty()) {
            throw new IllegalArgumentException("Entities list must not be empty");
        }

        // Check for configured error
        if (tenantErrors.containsKey(tenantId)) {
            return Promise.ofException(tenantErrors.get(tenantId));
        }
        if (tenantErrors.containsKey("__ALL__")) {
            return Promise.ofException(tenantErrors.get("__ALL__"));
        }

        QualityMetrics metrics = getTenantMetrics(tenantId);
        List<QualityMetrics> results = new ArrayList<>();
        for (int i = 0; i < entities.size(); i++) {
            // Vary metrics slightly for each entity for realistic batch behavior
            int variance = (i % 5) * 2 - 4; // -4, -2, 0, 2, 4
            int adjusted = Math.max(0, Math.min(100, metrics.getOverallScore() + variance));
            results.add(QualityMetrics.uniform(adjusted));
        }
        return Promise.of(results);
    }

    @Override
    public Promise<QualityScoreExplanation> explainScore(
            String tenantId, Entity entity, QualityMetrics metrics) {
        Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        Objects.requireNonNull(entity, "Entity must not be null");
        Objects.requireNonNull(metrics, "Metrics must not be null");

        QualityLevel level = metrics.getQualityLevel();
        QualityScoreExplanation.Builder builder
                = QualityScoreExplanation.builder()
                        .score(metrics.getOverallScore())
                        .level(level);

        // Add default findings based on score
        if (metrics.getCompleteness() < 80) {
            builder.finding("Incomplete entity: missing " + (100 - metrics.getCompleteness())
                    + "% of fields");
        }
        if (metrics.getConsistency() < 80) {
            builder.finding("Consistency issues detected in field formats");
        }
        if (metrics.getAccuracy() < 80) {
            builder.finding("Accuracy concerns: validation rules not fully met");
        }
        if (metrics.getRelevance() < 80) {
            builder.finding("Relevance: content may not align with entity type");
        }

        // Add recommendations based on lowest dimension
        String critical = metrics.getMostCriticalDimension();
        builder.recommendation("Improve " + critical + " score from "
                + getMetricForDimension(metrics, critical));

        // Add dimension feedback
        builder
                .dimensionFeedback("completeness",
                        "Score: " + metrics.getCompleteness() + "/100")
                .dimensionFeedback("consistency",
                        "Score: " + metrics.getConsistency() + "/100")
                .dimensionFeedback("accuracy",
                        "Score: " + metrics.getAccuracy() + "/100")
                .dimensionFeedback("relevance",
                        "Score: " + metrics.getRelevance() + "/100");

        return Promise.of(builder.build());
    }

    /**
     * Gets metric value for dimension.
     *
     * @param metrics quality metrics
     * @param dimension dimension name
     * @return dimension score or 0
     */
    private int getMetricForDimension(QualityMetrics metrics, String dimension) {
        return switch (dimension) {
            case "completeness" ->
                metrics.getCompleteness();
            case "consistency" ->
                metrics.getConsistency();
            case "accuracy" ->
                metrics.getAccuracy();
            case "relevance" ->
                metrics.getRelevance();
            default ->
                0;
        };
    }

    @Override
    public Promise<Void> updateConfiguration(
            String tenantId, Map<String, Object> configuration) {
        Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        Objects.requireNonNull(configuration, "Configuration must not be null");

        tenantConfigurations.put(tenantId, new HashMap<>(configuration));
        return Promise.complete();
    }

    @Override
    public Promise<Map<String, Object>> getConfiguration(String tenantId) {
        Objects.requireNonNull(tenantId, "Tenant ID must not be null");

        Map<String, Object> config = new HashMap<>(
                tenantConfigurations.getOrDefault(tenantId, Map.of()));
        return Promise.of(config);
    }

    @Override
    public Promise<List<String>> getSupportedDimensions() {
        return Promise.of(new ArrayList<>(supportedDimensions));
    }

    @Override
    public Promise<ValidationResult> validateEntity(String tenantId, Entity entity) {
        Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        Objects.requireNonNull(entity, "Entity must not be null");

        // Always valid for mock
        return Promise.of(ValidationResult.valid());
    }
}
