package com.ghatana.products.collection.domain.policy;

import io.activej.http.HttpClient;
import io.activej.http.HttpRequest;
import io.activej.promise.Promise;
import com.ghatana.platform.observability.MetricsCollector;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * ML-based policy checker calling external machine learning models.
 *
 * <p><b>Purpose</b><br>
 * Implements content policy checking using ML models:
 * - HATE_SPEECH: Detects toxic/hateful language via ML API
 * - NSFW: Identifies not-safe-for-work content via vision/text API
 * - QUALITY_THRESHOLD: Assesses content quality (grammar, coherence) via NLP API
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * MLPolicyChecker checker = new MLPolicyChecker(
 *     httpClient,
 *     metricsCollector,
 *     "https://ml-api.example.com"
 * );
 * 
 * // Configure model endpoint
 * checker.updatePolicyConfiguration("tenant-123", PolicyType.HATE_SPEECH,
 *     Map.of(
 *         "endpoint", "/v1/moderate/hate-speech",
 *         "threshold", 0.7
 *     )
 * );
 * 
 * // Check content
 * PolicyCheckResult result = checker.checkContent(
 *     "tenant-123",
 *     "Content to check",
 *     Set.of(PolicyType.HATE_SPEECH, PolicyType.QUALITY_THRESHOLD)
 * ).getResult();
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe using ConcurrentHashMap for configuration storage.
 * HTTP client is thread-safe by design.
 *
 * <p><b>Performance Characteristics</b><br>
 * - O(1) for configuration lookup
 * - O(n) for API call latency (typically 100-500ms per request)
 * - Batch operations reduce overhead via single API call
 * - Timeout: 30 seconds per request
 *
 * <p><b>Architecture Role</b><br>
 * Adapter bridging domain policy checking with external ML services.
 * Decouples policy logic from ML implementation details.
 *
 * @see ContentPolicyChecker
 * @see PolicyCheckResult
 * @see PolicyType
 * @doc.type class
 * @doc.purpose ML-based content policy checker
 * @doc.layer domain
 * @doc.pattern Adapter
 */
public class MLPolicyChecker implements ContentPolicyChecker {

    private final HttpClient httpClient;
    private final MetricsCollector metrics;
    private final String mlServiceBaseUrl;
    private final ObjectMapper objectMapper;

    // Tenant-specific ML model configurations
    private final Map<String, Map<PolicyType, Map<String, Object>>> tenantConfigs;

    // Default confidence thresholds
    private static final double DEFAULT_HATE_SPEECH_THRESHOLD = 0.75;
    private static final double DEFAULT_NSFW_THRESHOLD = 0.80;
    private static final double DEFAULT_QUALITY_THRESHOLD = 0.60;

    // Request timeout
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    /**
     * Creates a new ML-based policy checker.
     *
     * @param httpClient HTTP client for ML API calls
     * @param metrics metrics collector for observability
     * @param mlServiceBaseUrl base URL of ML service (e.g., "https://ml-api.example.com")
     * @throws NullPointerException if any parameter is null
     */
    public MLPolicyChecker(
            HttpClient httpClient,
            MetricsCollector metrics,
            String mlServiceBaseUrl
    ) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient cannot be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics cannot be null");
        this.mlServiceBaseUrl = Objects.requireNonNull(mlServiceBaseUrl, "mlServiceBaseUrl cannot be null");
        this.objectMapper = JsonUtils.getDefaultMapper();
        this.tenantConfigs = new ConcurrentHashMap<>();
    }

    @Override
    public Promise<PolicyCheckResult> checkContent(
            String tenantId,
            String content,
            Set<PolicyType> policiesToCheck
    ) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(content, "content cannot be null");
        Objects.requireNonNull(policiesToCheck, "policiesToCheck cannot be null");

        long startTime = System.currentTimeMillis();

        // Run all ML checks in parallel using Promise composition
        List<Promise<PolicyCheckResult>> checkPromises = policiesToCheck.stream()
                .filter(this::isSupported)
                .map(policyType -> checkSinglePolicyAsync(tenantId, content, policyType))
                .collect(Collectors.toList());

        // Wait for all checks to complete
        return Promise.ofAll(checkPromises)
                .map(results -> {
                    PolicyCheckResult aggregated = aggregateResults(results);
                    
                    long duration = System.currentTimeMillis() - startTime;
                    metrics.incrementCounter("ml.policy.check.count",
                            "tenant", tenantId,
                            "passed", String.valueOf(aggregated.passed()));
                    metrics.recordTimer("ml.policy.check.duration", duration,
                            "tenant", tenantId);

                    return aggregated;
                })
                .whenException(e -> {
                    metrics.incrementCounter("ml.policy.check.errors",
                            "tenant", tenantId,
                            "error", e.getClass().getSimpleName());
                });
    }

    @Override
    public Promise<List<PolicyCheckResult>> checkBatch(
            String tenantId,
            List<String> contents,
            Set<PolicyType> policiesToCheck
    ) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(contents, "contents cannot be null");
        Objects.requireNonNull(policiesToCheck, "policiesToCheck cannot be null");

        // Check each content item in parallel
        List<Promise<PolicyCheckResult>> checkPromises = contents.stream()
                .map(content -> checkContent(tenantId, content, policiesToCheck))
                .collect(Collectors.toList());

        return Promise.ofAll(checkPromises);
    }

    @Override
    public Set<PolicyType> getSupportedPolicies() {
        return Set.of(
                PolicyType.HATE_SPEECH,
                PolicyType.NSFW,
                PolicyType.QUALITY_THRESHOLD
        );
    }

    @Override
    public Promise<Void> updatePolicyConfiguration(
            String tenantId,
            PolicyType policyType,
            Map<String, Object> configuration
    ) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(policyType, "policyType cannot be null");
        Objects.requireNonNull(configuration, "configuration cannot be null");

        if (!isSupported(policyType)) {
            return Promise.ofException(new IllegalArgumentException(
                    "Unsupported policy type: " + policyType));
        }

        // Store configuration for tenant
        tenantConfigs
                .computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>())
                .put(policyType, new HashMap<>(configuration));

        metrics.incrementCounter("ml.policy.config.updated",
                "tenant", tenantId,
                "policy", policyType.name());

        return Promise.complete();
    }

    /**
     * Checks content against a single policy type asynchronously.
     *
     * @param tenantId tenant identifier
     * @param content content to check
     * @param policyType policy to check against
     * @return promise of check result
     */
    private Promise<PolicyCheckResult> checkSinglePolicyAsync(
            String tenantId,
            String content,
            PolicyType policyType
    ) {
        return switch (policyType) {
            case HATE_SPEECH -> checkHateSpeech(tenantId, content);
            case NSFW -> checkNSFW(tenantId, content);
            case QUALITY_THRESHOLD -> checkQuality(tenantId, content);
            default -> Promise.of(PolicyCheckResult.pass(policyType, 1.0));
        };
    }

    /**
     * Checks for hate speech using ML API.
     *
     * @param tenantId tenant identifier
     * @param content content to check
     * @return promise of hate speech check result
     */
    private Promise<PolicyCheckResult> checkHateSpeech(String tenantId, String content) {
        String endpoint = getEndpoint(tenantId, PolicyType.HATE_SPEECH, "/v1/moderate/hate-speech");
        double threshold = getThreshold(tenantId, PolicyType.HATE_SPEECH, DEFAULT_HATE_SPEECH_THRESHOLD);

        return callMLAPI(endpoint, content, tenantId)
                .map(response -> parseHateSpeechResponse(response, threshold))
                .whenException(e -> metrics.incrementCounter("ml.hate_speech.errors",
                        "tenant", tenantId));
    }

    /**
     * Checks for NSFW content using ML API.
     *
     * @param tenantId tenant identifier
     * @param content content to check
     * @return promise of NSFW check result
     */
    private Promise<PolicyCheckResult> checkNSFW(String tenantId, String content) {
        String endpoint = getEndpoint(tenantId, PolicyType.NSFW, "/v1/moderate/nsfw");
        double threshold = getThreshold(tenantId, PolicyType.NSFW, DEFAULT_NSFW_THRESHOLD);

        return callMLAPI(endpoint, content, tenantId)
                .map(response -> parseNSFWResponse(response, threshold))
                .whenException(e -> metrics.incrementCounter("ml.nsfw.errors",
                        "tenant", tenantId));
    }

    /**
     * Checks content quality using ML API.
     *
     * @param tenantId tenant identifier
     * @param content content to check
     * @return promise of quality check result
     */
    private Promise<PolicyCheckResult> checkQuality(String tenantId, String content) {
        String endpoint = getEndpoint(tenantId, PolicyType.QUALITY_THRESHOLD, "/v1/analyze/quality");
        double threshold = getThreshold(tenantId, PolicyType.QUALITY_THRESHOLD, DEFAULT_QUALITY_THRESHOLD);

        return callMLAPI(endpoint, content, tenantId)
                .map(response -> parseQualityResponse(response, threshold))
                .whenException(e -> metrics.incrementCounter("ml.quality.errors",
                        "tenant", tenantId));
    }

    /**
     * Calls ML API with content.
     *
     * @param endpoint API endpoint path
     * @param content content to analyze
     * @param tenantId tenant identifier for metrics
     * @return promise of JSON response
     */
    private Promise<JsonNode> callMLAPI(String endpoint, String content, String tenantId) {
        long startTime = System.currentTimeMillis();

        try {
            // Build request body
            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "content", content,
                    "tenant_id", tenantId
            ));

            // Build HTTP request
            HttpRequest request = HttpRequest.post(mlServiceBaseUrl + endpoint)
                    .withHeader("Content-Type", "application/json")
                    .withHeader("Accept", "application/json")
                    .withBody(requestBody.getBytes());

            // Execute with timeout
            return httpClient.request(request)
                    .map(response -> {
                        long duration = System.currentTimeMillis() - startTime;
                        metrics.recordTimer("ml.api.call.duration", duration,
                                "endpoint", endpoint,
                                "tenant", tenantId);

                        if (response.getCode() != 200) {
                            throw new RuntimeException("ML API returned status " + response.getCode());
                        }

                        return objectMapper.readTree(response.getBody());
                    })
                    .withTimeout(REQUEST_TIMEOUT);

        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }

    /**
     * Parses hate speech API response.
     *
     * @param response JSON response from ML API
     * @param threshold confidence threshold
     * @return policy check result
     */
    private PolicyCheckResult parseHateSpeechResponse(JsonNode response, double threshold) {
        double score = response.path("score").asDouble(0.0);
        boolean isHateSpeech = score >= threshold;

        if (!isHateSpeech) {
            return PolicyCheckResult.pass(PolicyType.HATE_SPEECH, 1.0 - score);
        }

        List<PolicyCheckResult.PolicyViolation> violations = new ArrayList<>();
        violations.add(new PolicyCheckResult.PolicyViolation(
                PolicyType.HATE_SPEECH,
                score >= 0.9 ? "CRITICAL" : "HIGH",
                "global",
                "Hate speech detected with confidence " + String.format("%.2f", score),
                "Remove hateful language"
        ));

        // Add category-specific violations if provided
        JsonNode categories = response.path("categories");
        if (categories.isArray()) {
            for (JsonNode category : categories) {
                String categoryName = category.path("name").asText();
                double categoryScore = category.path("score").asDouble(0.0);
                if (categoryScore >= threshold) {
                    violations.add(new PolicyCheckResult.PolicyViolation(
                            PolicyType.HATE_SPEECH,
                            "MEDIUM",
                            "category",
                            "Detected: " + categoryName + " (confidence: " + 
                                    String.format("%.2f", categoryScore) + ")",
                            "Review and remove " + categoryName + " content"
                    ));
                }
            }
        }

        return PolicyCheckResult.failWithViolations(
                PolicyType.HATE_SPEECH,
                violations,
                1.0 - score
        );
    }

    /**
     * Parses NSFW API response.
     *
     * @param response JSON response from ML API
     * @param threshold confidence threshold
     * @return policy check result
     */
    private PolicyCheckResult parseNSFWResponse(JsonNode response, double threshold) {
        double score = response.path("score").asDouble(0.0);
        boolean isNSFW = score >= threshold;

        if (!isNSFW) {
            return PolicyCheckResult.pass(PolicyType.NSFW, 1.0 - score);
        }

        PolicyCheckResult.PolicyViolation violation = new PolicyCheckResult.PolicyViolation(
                PolicyType.NSFW,
                score >= 0.95 ? "CRITICAL" : "HIGH",
                "global",
                "NSFW content detected with confidence " + String.format("%.2f", score),
                "Remove inappropriate content"
        );

        return PolicyCheckResult.failWithViolations(
                PolicyType.NSFW,
                List.of(violation),
                1.0 - score
        );
    }

    /**
     * Parses quality API response.
     *
     * @param response JSON response from ML API
     * @param threshold minimum quality threshold
     * @return policy check result
     */
    private PolicyCheckResult parseQualityResponse(JsonNode response, double threshold) {
        double score = response.path("quality_score").asDouble(0.0);
        boolean meetsQuality = score >= threshold;

        if (meetsQuality) {
            return PolicyCheckResult.pass(PolicyType.QUALITY_THRESHOLD, score);
        }

        List<PolicyCheckResult.PolicyViolation> violations = new ArrayList<>();

        // Grammar issues
        JsonNode grammar = response.path("grammar_score");
        if (!grammar.isMissingNode() && grammar.asDouble() < 0.6) {
            violations.add(new PolicyCheckResult.PolicyViolation(
                    PolicyType.QUALITY_THRESHOLD,
                    "MEDIUM",
                    "grammar",
                    "Grammar issues detected (score: " + String.format("%.2f", grammar.asDouble()) + ")",
                    "Review and correct grammar errors"
            ));
        }

        // Coherence issues
        JsonNode coherence = response.path("coherence_score");
        if (!coherence.isMissingNode() && coherence.asDouble() < 0.5) {
            violations.add(new PolicyCheckResult.PolicyViolation(
                    PolicyType.QUALITY_THRESHOLD,
                    "LOW",
                    "coherence",
                    "Coherence issues detected (score: " + String.format("%.2f", coherence.asDouble()) + ")",
                    "Improve logical flow and structure"
            ));
        }

        // Readability issues
        JsonNode readability = response.path("readability_score");
        if (!readability.isMissingNode() && readability.asDouble() < 0.5) {
            violations.add(new PolicyCheckResult.PolicyViolation(
                    PolicyType.QUALITY_THRESHOLD,
                    "LOW",
                    "readability",
                    "Readability issues detected (score: " + String.format("%.2f", readability.asDouble()) + ")",
                    "Simplify language and improve clarity"
            ));
        }

        if (violations.isEmpty()) {
            violations.add(new PolicyCheckResult.PolicyViolation(
                    PolicyType.QUALITY_THRESHOLD,
                    "MEDIUM",
                    "overall",
                    "Overall quality below threshold (score: " + String.format("%.2f", score) + ")",
                    "Improve content quality"
            ));
        }

        return PolicyCheckResult.failWithViolations(
                PolicyType.QUALITY_THRESHOLD,
                violations,
                score
        );
    }

    /**
     * Gets ML API endpoint for a policy type.
     *
     * @param tenantId tenant identifier
     * @param policyType policy type
     * @param defaultEndpoint default endpoint if not configured
     * @return API endpoint path
     */
    private String getEndpoint(String tenantId, PolicyType policyType, String defaultEndpoint) {
        Map<String, Object> config = getTenantConfig(tenantId, policyType);
        if (config != null && config.containsKey("endpoint")) {
            return config.get("endpoint").toString();
        }
        return defaultEndpoint;
    }

    /**
     * Gets confidence threshold for a policy type.
     *
     * @param tenantId tenant identifier
     * @param policyType policy type
     * @param defaultThreshold default threshold if not configured
     * @return confidence threshold (0-1)
     */
    private double getThreshold(String tenantId, PolicyType policyType, double defaultThreshold) {
        Map<String, Object> config = getTenantConfig(tenantId, policyType);
        if (config != null && config.containsKey("threshold")) {
            Object threshold = config.get("threshold");
            if (threshold instanceof Number) {
                return ((Number) threshold).doubleValue();
            }
        }
        return defaultThreshold;
    }

    /**
     * Gets tenant-specific configuration for a policy.
     *
     * @param tenantId tenant identifier
     * @param policyType policy type
     * @return configuration map or null
     */
    private Map<String, Object> getTenantConfig(String tenantId, PolicyType policyType) {
        Map<PolicyType, Map<String, Object>> configs = tenantConfigs.get(tenantId);
        return configs != null ? configs.get(policyType) : null;
    }

    /**
     * Aggregates multiple policy check results.
     *
     * @param results individual policy results
     * @return aggregated result
     */
    private PolicyCheckResult aggregateResults(List<PolicyCheckResult> results) {
        if (results.isEmpty()) {
            return PolicyCheckResult.pass(null, 1.0);
        }

        boolean allPassed = results.stream().allMatch(PolicyCheckResult::passed);
        List<PolicyCheckResult.PolicyViolation> allViolations = results.stream()
                .flatMap(r -> r.violations().stream())
                .collect(Collectors.toList());

        double avgScore = results.stream()
                .mapToDouble(PolicyCheckResult::score)
                .average()
                .orElse(0.0);

        if (allPassed) {
            return PolicyCheckResult.pass(null, avgScore);
        }

        return PolicyCheckResult.failWithViolations(null, allViolations, avgScore);
    }

    /**
     * Checks if a policy type is supported.
     *
     * @param policyType policy type to check
     * @return true if supported
     */
    private boolean isSupported(PolicyType policyType) {
        return getSupportedPolicies().contains(policyType);
    }
}
