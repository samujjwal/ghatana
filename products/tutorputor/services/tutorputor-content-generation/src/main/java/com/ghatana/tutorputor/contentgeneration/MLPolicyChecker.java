package com.ghatana.tutorputor.contentgeneration;

import com.ghatana.platform.observability.MetricsCollector;
import io.activej.http.HttpClient;
import io.activej.promise.Promise;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * ML-oriented policy checker for moderation-sensitive content classes.
 *
 * <p>This implementation currently uses lightweight local heuristics while preserving
 * the same public API and configuration surface as the original adapter. That keeps
 * the module compiling and the service behavior deterministic until the external ML
 * API contract is finalized again.
 *
 * @doc.type class
 * @doc.purpose ML-oriented policy checker for hate speech, NSFW, and quality validation
 * @doc.layer domain
 * @doc.pattern Adapter
 */
public class MLPolicyChecker implements ContentPolicyChecker {

    private final HttpClient httpClient;
    private final MetricsCollector metrics;
    private final String mlServiceBaseUrl;
    private final Map<String, Map<PolicyType, Map<String, Object>>> tenantConfigs;

    public MLPolicyChecker(
            HttpClient httpClient,
            MetricsCollector metrics,
            String mlServiceBaseUrl
    ) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient cannot be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics cannot be null");
        this.mlServiceBaseUrl = Objects.requireNonNull(mlServiceBaseUrl, "mlServiceBaseUrl cannot be null");
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
        List<PolicyCheckResult> results = policiesToCheck.stream()
                .filter(this::isSupported)
                .map(policyType -> checkSinglePolicy(tenantId, content, policyType))
                .collect(Collectors.toList());

        PolicyCheckResult aggregated = aggregateResults(results);
        metrics.incrementCounter(
                "ml.policy.check.count",
                "tenant", tenantId,
                "passed", String.valueOf(aggregated.passed()),
                "service_base_url", mlServiceBaseUrl,
                "client", httpClient.getClass().getSimpleName()
        );
        metrics.recordTimer(
                "ml.policy.check.duration",
                System.currentTimeMillis() - startTime,
                "tenant", tenantId
        );

        return Promise.of(aggregated);
    }

    @Override
    public Promise<List<PolicyCheckResult>> checkBatch(
            String tenantId,
            List<String> contents,
            Set<PolicyType> policiesToCheck
    ) {
        Objects.requireNonNull(contents, "contents cannot be null");
        return Promise.of(contents.stream()
                .map(content -> checkContent(tenantId, content, policiesToCheck).getResult())
                .toList());
    }

    @Override
    public Set<PolicyType> getSupportedPolicies() {
        return Set.of(PolicyType.HATE_SPEECH, PolicyType.NSFW, PolicyType.QUALITY_THRESHOLD);
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

        tenantConfigs.computeIfAbsent(tenantId, ignored -> new ConcurrentHashMap<>())
                .put(policyType, new HashMap<>(configuration));
        return Promise.complete();
    }

    private PolicyCheckResult checkSinglePolicy(String tenantId, String content, PolicyType policyType) {
        return switch (policyType) {
            case HATE_SPEECH -> checkHateSpeech(tenantId, content);
            case NSFW -> checkNsfw(tenantId, content);
            case QUALITY_THRESHOLD -> checkQuality(tenantId, content);
            default -> PolicyCheckResult.pass(policyType);
        };
    }

    private PolicyCheckResult checkHateSpeech(String tenantId, String content) {
        List<String> phrases = configuredTerms(
                tenantId,
                PolicyType.HATE_SPEECH,
                List.of("kill them", "subhuman", "ethnic cleansing", "racial superiority")
        );
        return evaluatePhraseMatches(content, PolicyType.HATE_SPEECH, phrases, 0.75, "Remove hateful or dehumanizing language");
    }

    private PolicyCheckResult checkNsfw(String tenantId, String content) {
        List<String> phrases = configuredTerms(
                tenantId,
                PolicyType.NSFW,
                List.of("explicit sexual", "graphic nudity", "pornographic", "sexual act")
        );
        return evaluatePhraseMatches(content, PolicyType.NSFW, phrases, 0.80, "Remove explicit or adult-only content");
    }

    private PolicyCheckResult checkQuality(String tenantId, String content) {
        double threshold = configuredThreshold(tenantId, PolicyType.QUALITY_THRESHOLD, 0.60);
        double score = estimateQuality(content);
        if (score >= threshold) {
            return PolicyCheckResult.pass(PolicyType.QUALITY_THRESHOLD, score);
        }

        List<PolicyCheckResult.PolicyViolation> violations = new ArrayList<>();
        if (content.isBlank()) {
            violations.add(new PolicyCheckResult.PolicyViolation(
                    PolicyType.QUALITY_THRESHOLD,
                    "HIGH",
                    "global",
                    "Content is blank",
                    "Provide substantive educational content"
            ));
        }
        if (content.length() < 80) {
            violations.add(new PolicyCheckResult.PolicyViolation(
                    PolicyType.QUALITY_THRESHOLD,
                    "MEDIUM",
                    "global",
                    "Content is too short for a reliable educational artifact",
                    "Expand the explanation with more detail and structure"
            ));
        }
        if (!content.contains(".") && !content.contains(":")) {
            violations.add(new PolicyCheckResult.PolicyViolation(
                    PolicyType.QUALITY_THRESHOLD,
                    "LOW",
                    "global",
                    "Content lacks sentence structure",
                    "Use complete sentences and clearer structure"
            ));
        }
        if (violations.isEmpty()) {
            violations.add(new PolicyCheckResult.PolicyViolation(
                    PolicyType.QUALITY_THRESHOLD,
                    "MEDIUM",
                    "global",
                    "Content quality is below the configured threshold",
                    "Improve clarity, depth, and instructional structure"
            ));
        }
        return PolicyCheckResult.failWithViolations(PolicyType.QUALITY_THRESHOLD, violations, score);
    }

    private PolicyCheckResult evaluatePhraseMatches(
            String content,
            PolicyType policyType,
            List<String> phrases,
            double threshold,
            String remediation
    ) {
        String normalized = content.toLowerCase();
        List<PolicyCheckResult.PolicyViolation> violations = new ArrayList<>();
        for (String phrase : phrases) {
            int position = normalized.indexOf(phrase.toLowerCase());
            while (position >= 0) {
                violations.add(new PolicyCheckResult.PolicyViolation(
                        policyType,
                        threshold >= 0.8 ? "HIGH" : "MEDIUM",
                        "position " + position,
                        "Detected phrase: " + phrase,
                        remediation
                ));
                position = normalized.indexOf(phrase.toLowerCase(), position + 1);
            }
        }

        if (violations.isEmpty()) {
            return PolicyCheckResult.pass(policyType, 1.0);
        }
        double score = Math.max(0.0, 1.0 - (violations.size() * 0.25));
        return PolicyCheckResult.failWithViolations(policyType, violations, score);
    }

    @SuppressWarnings("unchecked")
    private List<String> configuredTerms(String tenantId, PolicyType policyType, List<String> defaults) {
        Map<String, Object> configuration = getTenantConfig(tenantId, policyType);
        if (configuration != null && configuration.get("terms") instanceof List<?> terms) {
            return ((List<Object>) terms).stream().map(String::valueOf).toList();
        }
        return defaults;
    }

    private double configuredThreshold(String tenantId, PolicyType policyType, double defaultValue) {
        Map<String, Object> configuration = getTenantConfig(tenantId, policyType);
        if (configuration != null && configuration.get("threshold") instanceof Number number) {
            return number.doubleValue();
        }
        return defaultValue;
    }

    private Map<String, Object> getTenantConfig(String tenantId, PolicyType policyType) {
        Map<PolicyType, Map<String, Object>> tenantPolicyConfigs = tenantConfigs.get(tenantId);
        return tenantPolicyConfigs == null ? null : tenantPolicyConfigs.get(policyType);
    }

    private double estimateQuality(String content) {
        if (content == null || content.isBlank()) {
            return 0.0;
        }
        double lengthScore = Math.min(1.0, content.length() / 200.0);
        double sentenceScore = (content.contains(".") || content.contains(":")) ? 1.0 : 0.4;
        double listScore = content.contains("\n") ? 1.0 : 0.7;
        return Math.min(1.0, (lengthScore * 0.5) + (sentenceScore * 0.3) + (listScore * 0.2));
    }

    private PolicyCheckResult aggregateResults(List<PolicyCheckResult> results) {
        if (results.isEmpty()) {
            return PolicyCheckResult.pass(PolicyType.HATE_SPEECH, 1.0);
        }

        boolean allPassed = results.stream().allMatch(PolicyCheckResult::passed);
        List<PolicyCheckResult.PolicyViolation> allViolations = results.stream()
                .flatMap(result -> result.violations().stream())
                .collect(Collectors.toList());
        double averageScore = results.stream().mapToDouble(PolicyCheckResult::score).average().orElse(1.0);

        return allPassed
                ? PolicyCheckResult.pass(results.get(0).policyType(), averageScore)
                : PolicyCheckResult.failWithViolations(results.get(0).policyType(), allViolations, averageScore);
    }

    private boolean isSupported(PolicyType policyType) {
        return getSupportedPolicies().contains(policyType);
    }
}
