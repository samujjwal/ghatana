package com.ghatana.kernel.release;

import com.ghatana.platform.health.HealthStatus;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;

/**
 * Product release readiness producer.
 *
 * <p>Generates release readiness evidence for products by
 * collecting build, test, API, consent, audit, tenant, cache, rollback, and
 * deployment evidence from lifecycle execution outputs.</p>
 *
 * <p><b>Hardening (KER-006)</b><br>
 * - Generalizes evidence categories for all products
 * - Supports product-specific evidence category configuration
 * - Allows custom evidence extraction strategies per product
 * - Maintains backward compatibility with existing product categories
 *
 * @doc.type class
 * @doc.purpose Generate release readiness evidence for products
 * @doc.layer platform
 * @doc.pattern Producer
 */
public class ProductReleaseReadinessProducer {

    private static final Logger logger = LoggerFactory.getLogger(ProductReleaseReadinessProducer.class);

    private final ReleaseEvidenceCollector evidenceCollector;
    private final ReleaseScorecardCalculator scorecardCalculator;
    private final EvidenceCategoryConfig categoryConfig;

    public ProductReleaseReadinessProducer(
        ReleaseEvidenceCollector evidenceCollector,
        ReleaseScorecardCalculator scorecardCalculator
    ) {
        this(evidenceCollector, scorecardCalculator, new DefaultEvidenceCategoryConfig());
    }

    public ProductReleaseReadinessProducer(
        ReleaseEvidenceCollector evidenceCollector,
        ReleaseScorecardCalculator scorecardCalculator,
        EvidenceCategoryConfig categoryConfig
    ) {
        this.evidenceCollector = evidenceCollector;
        this.scorecardCalculator = scorecardCalculator;
        this.categoryConfig = categoryConfig;
    }

    /**
     * Generate release readiness evidence for a product.
     *
     * @param productId The product ID
     * @param environment The environment (e.g., "local", "staging", "prod")
     * @return Promise containing the release readiness evidence
     */
    public Promise<ProductReleaseReadinessEvidence> generateReleaseReadiness(
        String productId,
        String environment
    ) {
        logger.info("Generating release readiness evidence for product: {} in environment: {}", productId, environment);

        return evidenceCollector.collectEvidence(productId, environment)
            .map(evidence -> buildReleaseEvidence(productId, environment, evidence));
    }

    private ProductReleaseReadinessEvidence buildReleaseEvidence(
        String productId,
        String environment,
        Map<String, Object> evidence
    ) {
        ProductReleaseReadinessEvidence.ReleaseReadiness readiness = calculateReadiness(productId, evidence);
        Map<String, ProductReleaseReadinessEvidence.EvidenceCategory> categories = categorizeEvidence(productId, evidence);
        Map<String, ProductReleaseReadinessEvidence.GateStatus> gates = extractGateStatus(evidence);
        ProductReleaseReadinessEvidence.Summary summary = calculateSummary(gates);

        return new ProductReleaseReadinessEvidence(
            "1.0.0",
            productId,
            getProductName(productId),
            Instant.now(),
            readiness,
            categories,
            gates,
            summary,
            extractNextRequiredWork(productId, evidence)
        );
    }

    private ProductReleaseReadinessEvidence.ReleaseReadiness calculateReadiness(
        String productId,
        Map<String, Object> evidence
    ) {
        double overallScore = scorecardCalculator.calculateOverallScore(evidence);
        List<String> blockingIssues = extractBlockingIssues(evidence);
        List<String> warnings = extractWarnings(evidence);

        String status = determineStatus(overallScore, blockingIssues);

        return new ProductReleaseReadinessEvidence.ReleaseReadiness(
            status,
            overallScore,
            blockingIssues,
            warnings
        );
    }

    private String determineStatus(double score, List<String> blockingIssues) {
        if (!blockingIssues.isEmpty()) {
            return "blocked";
        }
        if (score >= 8.0) {
            return "ready-for-production";
        }
        if (score >= 6.0) {
            return "ready-for-staging";
        }
        return "not-ready";
    }

    /**
     * Categorizes evidence based on product-specific configuration.
     * KER-006: Generalized to support custom categories per product.
     */
    private Map<String, ProductReleaseReadinessEvidence.EvidenceCategory> categorizeEvidence(
        String productId,
        Map<String, Object> evidence
    ) {
        Map<String, ProductReleaseReadinessEvidence.EvidenceCategory> categories = new HashMap<>();
        Set<String> categoryNames = categoryConfig.getCategoriesForProduct(productId);

        for (String categoryName : categoryNames) {
            categories.put(categoryName, extractCategory(evidence, categoryName));
        }

        return categories;
    }

    /**
     * Extracts a single evidence category.
     * KER-006: Uses product-specific extraction strategy.
     */
    private ProductReleaseReadinessEvidence.EvidenceCategory extractCategory(
        Map<String, Object> evidence,
        String categoryName
    ) {
        return new ProductReleaseReadinessEvidence.EvidenceCategory(
            extractStatus(evidence, categoryName),
            extractTimestamp(evidence, categoryName),
            extractEvidenceRefs(evidence, categoryName),
            extractCategoryData(evidence, categoryName)
        );
    }

    /**
     * Extracts category-specific data using product-specific strategy.
     * KER-006: Supports custom data extraction per product.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractCategoryData(
        Map<String, Object> evidence,
        String categoryName
    ) {
        Map<String, Object> categoryData = (Map<String, Object>) evidence.get(categoryName);
        if (categoryData == null) {
            return new HashMap<>();
        }

        return categoryConfig.extractCategoryData(categoryName, categoryData);
    }

    private Map<String, ProductReleaseReadinessEvidence.GateStatus> extractGateStatus(
        Map<String, Object> evidence
    ) {
        Map<String, ProductReleaseReadinessEvidence.GateStatus> gates = new HashMap<>();

        @SuppressWarnings("unchecked")
        Map<String, Object> gateResults = (Map<String, Object>) evidence.get("gates");

        if (gateResults != null) {
            for (Map.Entry<String, Object> entry : gateResults.entrySet()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> gateData = (Map<String, Object>) entry.getValue();
                gates.put(entry.getKey(), new ProductReleaseReadinessEvidence.GateStatus(
                    (String) gateData.get("status"),
                    (String) gateData.get("evidenceRef")
                ));
            }
        }

        return gates;
    }

    private ProductReleaseReadinessEvidence.Summary calculateSummary(
        Map<String, ProductReleaseReadinessEvidence.GateStatus> gates
    ) {
        int total = gates.size();
        int passed = (int) gates.values().stream().filter(g -> "passed".equals(g.status())).count();
        int partial = (int) gates.values().stream().filter(g -> "partial".equals(g.status())).count();
        int failed = (int) gates.values().stream().filter(g -> "failed".equals(g.status())).count();
        int blocked = (int) gates.values().stream().filter(g -> "blocked".equals(g.status())).count();

        String overallStatus = determineOverallStatus(passed, partial, failed, blocked);

        return new ProductReleaseReadinessEvidence.Summary(
            total,
            passed,
            partial,
            failed,
            blocked,
            overallStatus
        );
    }

    private String determineOverallStatus(int passed, int partial, int failed, int blocked) {
        if (blocked > 0) return "blocked";
        if (failed > 0) return "failed";
        if (partial > 0) return "partial";
        if (passed == 0) return "pending";
        return "ready";
    }

    private List<String> extractNextRequiredWork(String productId, Map<String, Object> evidence) {
        List<String> nextWork = new ArrayList<>();

        // Product-specific evidence checks are handled by product implementations
        // This method only extracts generic next work from evidence

        @SuppressWarnings("unchecked")
        List<String> evidenceNextWork = (List<String>) evidence.get("nextRequiredWork");
        if (evidenceNextWork != null) {
            nextWork.addAll(evidenceNextWork);
        }

        return nextWork;
    }

    // Helper methods for extracting evidence data
    private String extractStatus(Map<String, Object> evidence, String category) {
        @SuppressWarnings("unchecked")
        Map<String, Object> categoryData = (Map<String, Object>) evidence.get(category);
        return categoryData != null ? (String) categoryData.get("status") : "pending";
    }

    private String extractTimestamp(Map<String, Object> evidence, String category) {
        @SuppressWarnings("unchecked")
        Map<String, Object> categoryData = (Map<String, Object>) evidence.get(category);
        return categoryData != null ? (String) categoryData.get("lastChecked") : null;
    }

    @SuppressWarnings("unchecked")
    private List<String> extractEvidenceRefs(Map<String, Object> evidence, String category) {
        Map<String, Object> categoryData = (Map<String, Object>) evidence.get(category);
        return categoryData != null ? (List<String>) categoryData.get("evidenceRefs") : new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private List<String> extractBlockingIssues(Map<String, Object> evidence) {
        Map<String, Object> readiness = (Map<String, Object>) evidence.get("releaseReadiness");
        return readiness != null ? (List<String>) readiness.get("blockingIssues") : new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private List<String> extractWarnings(Map<String, Object> evidence) {
        Map<String, Object> readiness = (Map<String, Object>) evidence.get("releaseReadiness");
        return readiness != null ? (List<String>) readiness.get("warnings") : new ArrayList<>();
    }

    private String getProductName(String productId) {
        // Product name mapping is handled by product implementations
        // This method returns the productId as a fallback
        return productId;
    }

    /**
     * Release evidence collector interface.
     */
    public interface ReleaseEvidenceCollector {
        Promise<Map<String, Object>> collectEvidence(String productId, String environment);
    }

    /**
     * Release scorecard calculator interface.
     */
    public interface ReleaseScorecardCalculator {
        double calculateOverallScore(Map<String, Object> evidence);
    }

    /**
     * Evidence category configuration interface.
     * KER-006: Allows product-specific evidence category configuration.
     */
    public interface EvidenceCategoryConfig {
        Set<String> getCategoriesForProduct(String productId);
        Map<String, Object> extractCategoryData(String categoryName, Map<String, Object> categoryData);
    }

    /**
     * Default evidence category configuration.
     * KER-006: Provides backward-compatible categories for existing products.
     */
    public static class DefaultEvidenceCategoryConfig implements EvidenceCategoryConfig {
        private static final Set<String> DEFAULT_CATEGORIES = Set.of(
            "build", "test", "api", "rollback", "deployment"
        );

        @Override
        public Set<String> getCategoriesForProduct(String productId) {
            // Existing products use default categories
            // New products can override with custom config
            return DEFAULT_CATEGORIES;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Map<String, Object> extractCategoryData(String categoryName, Map<String, Object> categoryData) {
            return switch (categoryName) {
                case "build" -> buildEvidenceData(
                    "artifacts", (Map<String, Object>) categoryData.get("artifacts"),
                    "qualityMetrics", (Map<String, Object>) categoryData.get("qualityMetrics")
                );
                case "test" -> (Map<String, Object>) categoryData.getOrDefault("testSuites", new HashMap<>());
                case "api" -> (Map<String, Object>) categoryData.getOrDefault("contractConformance", new HashMap<>());
                case "rollback" -> (Map<String, Object>) categoryData.getOrDefault("readiness", new HashMap<>());
                case "deployment" -> (Map<String, Object>) categoryData.getOrDefault("environments", new HashMap<>());
                default -> new HashMap<>();
            };
        }

        private Map<String, Object> buildEvidenceData(
            String firstKey,
            Map<String, Object> firstValue,
            String secondKey,
            Map<String, Object> secondValue
        ) {
            Map<String, Object> data = new HashMap<>();
            data.put(firstKey, firstValue);
            data.put(secondKey, secondValue);
            return data;
        }
    }

    /**
     * Product release readiness evidence record.
     */
    public record ProductReleaseReadinessEvidence(
        String schemaVersion,
        String productId,
        String productName,
        Instant checkedAt,
        ReleaseReadiness releaseReadiness,
        Map<String, EvidenceCategory> evidenceCategories,
        Map<String, GateStatus> gates,
        Summary summary,
        List<String> nextRequiredWork
    ) {
        public record ReleaseReadiness(
            String status,
            double overallScore,
            List<String> blockingIssues,
            List<String> warnings
        ) {}

        public record EvidenceCategory(
            String status,
            String lastChecked,
            List<String> evidenceRefs,
            Map<String, Object> data
        ) {
            public EvidenceCategory(String status, String lastChecked, List<String> evidenceRefs) {
                this(status, lastChecked, evidenceRefs, new HashMap<>());
            }
        }

        public record GateStatus(
            String status,
            String evidenceRef
        ) {}

        public record Summary(
            int totalChecks,
            int passed,
            int partial,
            int failed,
            int blocked,
            String overallStatus
        ) {}
    }
}
