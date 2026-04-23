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
 * MockQualityScorer scorer = new MockQualityScorer() // GH-90000
 *     .respondWith(QualityMetrics.uniform(85)) // GH-90000
 *     .forTenant("tenant-123", QualityMetrics.uniform(92)) // GH-90000
 *     .throwError(new RuntimeException("Scoring failed"), "tenant-fail")
 *     .supportedDimensions("completeness", "consistency", "accuracy", "relevance"); // GH-90000
 *
 * // Use as QualityScorer in tests
 * QualityScoringService service = new QualityScoringService(scorer, metrics); // GH-90000
 * }</pre>
 *
 * <p>
 * <b>Configuration Methods</b><br>
 * - respondWith(QualityMetrics): Set default response - forTenant(tenantId, // GH-90000
 * metrics): Tenant-specific response - throwError(exception, tenantId): Error // GH-90000
 * simulation for tenant - supportedDimensions(String...): Configure available // GH-90000
 * dimensions - configuration(Map): Tenant configuration responses // GH-90000
 *
 * @doc.type class
 * @doc.purpose Mock adapter for QualityScorer
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class MockQualityScorer implements QualityScorer {

    private QualityMetrics defaultMetrics = QualityMetrics.uniform(75); // GH-90000
    private final Map<String, QualityMetrics> tenantMetrics = new HashMap<>(); // GH-90000
    private final Map<String, Exception> tenantErrors = new HashMap<>(); // GH-90000
    private final Map<String, Map<String, Object>> tenantConfigurations = new HashMap<>(); // GH-90000
    private List<String> supportedDimensions
            = List.of("completeness", "consistency", "accuracy", "relevance"); // GH-90000
    private String defaultConfiguration = "";

    /**
     * Sets default quality metrics response for all tenants.
     *
     * @param metrics default metrics to return
     * @return this instance for fluent API
     */
    public MockQualityScorer respondWith(QualityMetrics metrics) { // GH-90000
        this.defaultMetrics = Objects.requireNonNull(metrics, "Metrics must not be null"); // GH-90000
        return this;
    }

    /**
     * Sets tenant-specific quality metrics response.
     *
     * @param tenantId tenant identifier
     * @param metrics metrics for this tenant
     * @return this instance for fluent API
     */
    public MockQualityScorer forTenant(String tenantId, QualityMetrics metrics) { // GH-90000
        Objects.requireNonNull(tenantId, "Tenant ID must not be null"); // GH-90000
        Objects.requireNonNull(metrics, "Metrics must not be null"); // GH-90000
        tenantMetrics.put(tenantId, metrics); // GH-90000
        return this;
    }

    /**
     * Configures error response for specific tenant.
     *
     * @param exception exception to throw
     * @param tenantId tenant identifier (if null, error applies to all tenants) // GH-90000
     * @return this instance for fluent API
     */
    public MockQualityScorer throwError(Exception exception, String tenantId) { // GH-90000
        Objects.requireNonNull(exception, "Exception must not be null"); // GH-90000
        tenantErrors.put(tenantId != null ? tenantId : "__ALL__", exception); // GH-90000
        return this;
    }

    /**
     * Configures supported quality dimensions.
     *
     * @param dimensions dimension names
     * @return this instance for fluent API
     */
    public MockQualityScorer supportedDimensions(String... dimensions) { // GH-90000
        if (dimensions != null && dimensions.length > 0) { // GH-90000
            this.supportedDimensions = List.of(dimensions); // GH-90000
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
    public MockQualityScorer configuration(String tenantId, Map<String, Object> configuration) { // GH-90000
        Objects.requireNonNull(tenantId, "Tenant ID must not be null"); // GH-90000
        Objects.requireNonNull(configuration, "Configuration must not be null"); // GH-90000
        tenantConfigurations.put(tenantId, new HashMap<>(configuration)); // GH-90000
        return this;
    }

    /**
     * Gets configured metrics for tenant.
     *
     * @param tenantId tenant identifier
     * @return metrics for tenant, or default if not configured
     */
    public QualityMetrics getTenantMetrics(String tenantId) { // GH-90000
        return tenantMetrics.getOrDefault(tenantId, defaultMetrics); // GH-90000
    }

    /**
     * Sets dynamic response for tenant at runtime.
     *
     * @param tenantId tenant identifier
     * @param metrics new metrics to return
     */
    public void setTenantMetrics(String tenantId, QualityMetrics metrics) { // GH-90000
        Objects.requireNonNull(tenantId, "Tenant ID must not be null"); // GH-90000
        Objects.requireNonNull(metrics, "Metrics must not be null"); // GH-90000
        tenantMetrics.put(tenantId, metrics); // GH-90000
    }

    @Override
    public Promise<QualityMetrics> scoreEntity( // GH-90000
            String tenantId, Entity entity, Map<String, Object> context) {
        Objects.requireNonNull(tenantId, "Tenant ID must not be null"); // GH-90000
        Objects.requireNonNull(entity, "Entity must not be null"); // GH-90000

        // Check for configured error
        if (tenantErrors.containsKey(tenantId)) { // GH-90000
            return Promise.ofException(tenantErrors.get(tenantId)); // GH-90000
        }
        if (tenantErrors.containsKey("__ALL__")) {
            return Promise.ofException(tenantErrors.get("__ALL__"));
        }

        return Promise.of(getTenantMetrics(tenantId)); // GH-90000
    }

    @Override
    public Promise<List<QualityMetrics>> scoreEntitiesBatch( // GH-90000
            String tenantId, List<Entity> entities, Map<String, Object> context) {
        Objects.requireNonNull(tenantId, "Tenant ID must not be null"); // GH-90000
        Objects.requireNonNull(entities, "Entities list must not be null"); // GH-90000
        if (entities.isEmpty()) { // GH-90000
            throw new IllegalArgumentException("Entities list must not be empty");
        }

        // Check for configured error
        if (tenantErrors.containsKey(tenantId)) { // GH-90000
            return Promise.ofException(tenantErrors.get(tenantId)); // GH-90000
        }
        if (tenantErrors.containsKey("__ALL__")) {
            return Promise.ofException(tenantErrors.get("__ALL__"));
        }

        QualityMetrics metrics = getTenantMetrics(tenantId); // GH-90000
        List<QualityMetrics> results = new ArrayList<>(); // GH-90000
        for (int i = 0; i < entities.size(); i++) { // GH-90000
            // Vary metrics slightly for each entity for realistic batch behavior
            int variance = (i % 5) * 2 - 4; // -4, -2, 0, 2, 4 // GH-90000
            int adjusted = Math.max(0, Math.min(100, metrics.getOverallScore() + variance)); // GH-90000
            results.add(QualityMetrics.uniform(adjusted)); // GH-90000
        }
        return Promise.of(results); // GH-90000
    }

    @Override
    public Promise<QualityScoreExplanation> explainScore( // GH-90000
            String tenantId, Entity entity, QualityMetrics metrics) {
        Objects.requireNonNull(tenantId, "Tenant ID must not be null"); // GH-90000
        Objects.requireNonNull(entity, "Entity must not be null"); // GH-90000
        Objects.requireNonNull(metrics, "Metrics must not be null"); // GH-90000

        QualityLevel level = metrics.getQualityLevel(); // GH-90000
        QualityScoreExplanation.Builder builder
                = QualityScoreExplanation.builder() // GH-90000
                        .score(metrics.getOverallScore()) // GH-90000
                        .level(level); // GH-90000

        // Add default findings based on score
        if (metrics.getCompleteness() < 80) { // GH-90000
            builder.finding("Incomplete entity: missing " + (100 - metrics.getCompleteness()) // GH-90000
                    + "% of fields");
        }
        if (metrics.getConsistency() < 80) { // GH-90000
            builder.finding("Consistency issues detected in field formats");
        }
        if (metrics.getAccuracy() < 80) { // GH-90000
            builder.finding("Accuracy concerns: validation rules not fully met");
        }
        if (metrics.getRelevance() < 80) { // GH-90000
            builder.finding("Relevance: content may not align with entity type");
        }

        // Add recommendations based on lowest dimension
        String critical = metrics.getMostCriticalDimension(); // GH-90000
        builder.recommendation("Improve " + critical + " score from " // GH-90000
                + getMetricForDimension(metrics, critical)); // GH-90000

        // Add dimension feedback
        builder
                .dimensionFeedback("completeness", // GH-90000
                        "Score: " + metrics.getCompleteness() + "/100") // GH-90000
                .dimensionFeedback("consistency", // GH-90000
                        "Score: " + metrics.getConsistency() + "/100") // GH-90000
                .dimensionFeedback("accuracy", // GH-90000
                        "Score: " + metrics.getAccuracy() + "/100") // GH-90000
                .dimensionFeedback("relevance", // GH-90000
                        "Score: " + metrics.getRelevance() + "/100"); // GH-90000

        return Promise.of(builder.build()); // GH-90000
    }

    /**
     * Gets metric value for dimension.
     *
     * @param metrics quality metrics
     * @param dimension dimension name
     * @return dimension score or 0
     */
    private int getMetricForDimension(QualityMetrics metrics, String dimension) { // GH-90000
        return switch (dimension) { // GH-90000
            case "completeness" ->
                metrics.getCompleteness(); // GH-90000
            case "consistency" ->
                metrics.getConsistency(); // GH-90000
            case "accuracy" ->
                metrics.getAccuracy(); // GH-90000
            case "relevance" ->
                metrics.getRelevance(); // GH-90000
            default ->
                0;
        };
    }

    @Override
    public Promise<Void> updateConfiguration( // GH-90000
            String tenantId, Map<String, Object> configuration) {
        Objects.requireNonNull(tenantId, "Tenant ID must not be null"); // GH-90000
        Objects.requireNonNull(configuration, "Configuration must not be null"); // GH-90000

        tenantConfigurations.put(tenantId, new HashMap<>(configuration)); // GH-90000
        return Promise.complete(); // GH-90000
    }

    @Override
    public Promise<Map<String, Object>> getConfiguration(String tenantId) { // GH-90000
        Objects.requireNonNull(tenantId, "Tenant ID must not be null"); // GH-90000

        Map<String, Object> config = new HashMap<>( // GH-90000
                tenantConfigurations.getOrDefault(tenantId, Map.of())); // GH-90000
        return Promise.of(config); // GH-90000
    }

    @Override
    public Promise<List<String>> getSupportedDimensions() { // GH-90000
        return Promise.of(new ArrayList<>(supportedDimensions)); // GH-90000
    }

    @Override
    public Promise<ValidationResult> validateEntity(String tenantId, Entity entity) { // GH-90000
        Objects.requireNonNull(tenantId, "Tenant ID must not be null"); // GH-90000
        Objects.requireNonNull(entity, "Entity must not be null"); // GH-90000

        // Always valid for mock
        return Promise.of(ValidationResult.valid()); // GH-90000
    }
}
