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

/**
 * Product release readiness producer.
 *
 * <p>Generates release readiness evidence for products (PHR, DMOS, etc.) by
 * collecting build, test, API, FHIR, consent, audit, tenant, cache, rollback, and
 * deployment evidence from lifecycle execution outputs.</p>
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

    public ProductReleaseReadinessProducer(
        ReleaseEvidenceCollector evidenceCollector,
        ReleaseScorecardCalculator scorecardCalculator
    ) {
        this.evidenceCollector = evidenceCollector;
        this.scorecardCalculator = scorecardCalculator;
    }

    /**
     * Generate release readiness evidence for a product.
     *
     * @param productId The product ID (e.g., "phr", "digital-marketing")
     * @param environment The environment (e.g., "local", "staging", "prod")
     * @return Promise containing the release readiness evidence
     */
    public Promise<ProductReleaseReadinessEvidence> generateReleaseReadiness(
        String productId,
        String environment
    ) {
        logger.info("Generating release readiness evidence for product: {} in environment: {}", productId, environment);

        return evidenceCollector.collectEvidence(productId, environment)
            .thenMap(evidence -> {
                ProductReleaseReadinessEvidence releaseEvidence = buildReleaseEvidence(productId, environment, evidence);
                return Promise.of(releaseEvidence);
            });
    }

    private ProductReleaseReadinessEvidence buildReleaseEvidence(
        String productId,
        String environment,
        Map<String, Object> evidence
    ) {
        ProductReleaseReadinessEvidence.ReleaseReadiness readiness = calculateReadiness(productId, evidence);
        Map<String, ProductReleaseReadinessEvidence.EvidenceCategory> categories = categorizeEvidence(evidence);
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

    private Map<String, ProductReleaseReadinessEvidence.EvidenceCategory> categorizeEvidence(
        Map<String, Object> evidence
    ) {
        Map<String, ProductReleaseReadinessEvidence.EvidenceCategory> categories = new HashMap<>();

        // Build evidence
        categories.put("build", new ProductReleaseReadinessEvidence.EvidenceCategory(
            extractStatus(evidence, "build"),
            extractTimestamp(evidence, "build"),
            extractEvidenceRefs(evidence, "build"),
            extractArtifacts(evidence, "build"),
            extractQualityMetrics(evidence, "build")
        ));

        // Test evidence
        categories.put("test", new ProductReleaseReadinessEvidence.EvidenceCategory(
            extractStatus(evidence, "test"),
            extractTimestamp(evidence, "test"),
            extractEvidenceRefs(evidence, "test"),
            extractTestSuites(evidence, "test")
        ));

        // API evidence
        categories.put("api", new ProductReleaseReadinessEvidence.EvidenceCategory(
            extractStatus(evidence, "api"),
            extractTimestamp(evidence, "api"),
            extractEvidenceRefs(evidence, "api"),
            extractContractConformance(evidence, "api")
        ));

        // Rollback evidence
        categories.put("rollback", new ProductReleaseReadinessEvidence.EvidenceCategory(
            extractStatus(evidence, "rollback"),
            extractTimestamp(evidence, "rollback"),
            extractEvidenceRefs(evidence, "rollback"),
            extractRollbackReadiness(evidence, "rollback")
        ));

        // Deployment evidence
        categories.put("deployment", new ProductReleaseReadinessEvidence.EvidenceCategory(
            extractStatus(evidence, "deployment"),
            extractTimestamp(evidence, "deployment"),
            extractEvidenceRefs(evidence, "deployment"),
            extractDeploymentEnvironments(evidence, "deployment")
        ));

        return categories;
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

        if ("phr".equals(productId)) {
            if (!evidence.containsKey("fhir") || !"passed".equals(extractStatus(evidence, "fhir"))) {
                nextWork.add("Complete FHIR R4 validation");
            }
            if (!evidence.containsKey("cache") || !"passed".equals(extractStatus(evidence, "cache"))) {
                nextWork.add("Add staging/prod cache proof");
            }
        }

        if ("digital-marketing".equals(productId)) {
            if (!evidence.containsKey("connector") || !"passed".equals(extractStatus(evidence, "connector"))) {
                nextWork.add("Complete Google Ads connector proof");
            }
        }

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
    private Map<String, Object> extractArtifacts(Map<String, Object> evidence, String category) {
        Map<String, Object> categoryData = (Map<String, Object>) evidence.get(category);
        return categoryData != null ? (Map<String, Object>) categoryData.get("artifacts") : new HashMap<>();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractQualityMetrics(Map<String, Object> evidence, String category) {
        Map<String, Object> categoryData = (Map<String, Object>) evidence.get(category);
        return categoryData != null ? (Map<String, Object>) categoryData.get("qualityMetrics") : new HashMap<>();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractTestSuites(Map<String, Object> evidence, String category) {
        Map<String, Object> categoryData = (Map<String, Object>) evidence.get(category);
        return categoryData != null ? (Map<String, Object>) categoryData.get("testSuites") : new HashMap<>();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractContractConformance(Map<String, Object> evidence, String category) {
        Map<String, Object> categoryData = (Map<String, Object>) evidence.get(category);
        return categoryData != null ? (Map<String, Object>) categoryData.get("contractConformance") : new HashMap<>();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractRollbackReadiness(Map<String, Object> evidence, String category) {
        Map<String, Object> categoryData = (Map<String, Object>) evidence.get(category);
        return categoryData != null ? (Map<String, Object>) categoryData.get("readiness") : new HashMap<>();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractDeploymentEnvironments(Map<String, Object> evidence, String category) {
        Map<String, Object> categoryData = (Map<String, Object>) evidence.get(category);
        return categoryData != null ? (Map<String, Object>) categoryData.get("environments") : new HashMap<>();
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
        return switch (productId) {
            case "phr" -> "Personal Health Records";
            case "digital-marketing" -> "Digital Marketing Operations System";
            case "data-cloud" -> "Data Cloud";
            case "yappc" -> "YAPPC";
            default -> productId;
        };
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
