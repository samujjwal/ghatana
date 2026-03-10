package com.ghatana.products.collection.application.policy;

import com.ghatana.products.collection.domain.policy.*;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Application service for content policy validation.
 *
 * <p><b>Purpose</b><br>
 * Orchestrates multiple policy checkers to validate content against
 * tenant-specific policies. Coordinates rule-based and ML-based checkers,
 * aggregates results, and provides unified policy configuration.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * PolicyService policyService = new PolicyService(
 *     ruleBasedChecker,
 *     mlChecker,
 *     metricsCollector
 * );
 * 
 * // Validate content
 * PolicyCheckResult result = policyService.validateContent(
 *     "tenant-123",
 *     "Content to validate",
 *     Set.of(PolicyType.PROFANITY, PolicyType.HATE_SPEECH, PolicyType.PII)
 * ).getResult();
 * 
 * if (!result.passed()) {
 *     // Handle violations
 *     for (PolicyViolation violation : result.violations()) {
 *         System.out.println(violation.description());
 *     }
 * }
 * 
 * // Configure policy
 * policyService.configurePolicy("tenant-123", PolicyType.PROFANITY,
 *     Map.of("words", List.of("badword1", "badword2")));
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe. Delegates to thread-safe policy checkers.
 *
 * <p><b>Performance Characteristics</b><br>
 * - O(1) for checker routing
 * - O(n) for batch validation (parallelized internally)
 * - Policy checks run in parallel when multiple policies requested
 * - Typical latency:
 *   * Rule-based: 1-10ms
 *   * ML-based: 100-500ms
 *
 * <p><b>Architecture Role</b><br>
 * Application service coordinating multiple policy checking strategies.
 * Acts as facade simplifying policy validation for clients.
 *
 * @see ContentPolicyChecker
 * @see RuleBasedPolicyChecker
 * @see MLPolicyChecker
 * @see PolicyCheckResult
 * @doc.type class
 * @doc.purpose Content policy validation orchestration
 * @doc.layer application
 * @doc.pattern Service
 */
public class PolicyService {

    private final ContentPolicyChecker ruleBasedChecker;
    private final ContentPolicyChecker mlChecker;
    private final MetricsCollector metrics;

    /**
     * Creates a new policy service.
     *
     * @param ruleBasedChecker rule-based policy checker (for PROFANITY, PII, SPAM)
     * @param mlChecker ML-based policy checker (for HATE_SPEECH, NSFW, QUALITY_THRESHOLD)
     * @param metrics metrics collector for observability
     * @throws NullPointerException if any parameter is null
     */
    public PolicyService(
            ContentPolicyChecker ruleBasedChecker,
            ContentPolicyChecker mlChecker,
            MetricsCollector metrics
    ) {
        this.ruleBasedChecker = Objects.requireNonNull(ruleBasedChecker, "ruleBasedChecker cannot be null");
        this.mlChecker = Objects.requireNonNull(mlChecker, "mlChecker cannot be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics cannot be null");
    }

    /**
     * Validates content against specified policies.
     *
     * <p>Routes policy checks to appropriate checker:
     * - PROFANITY, PII, SPAM → rule-based checker
     * - HATE_SPEECH, NSFW, QUALITY_THRESHOLD → ML checker
     *
     * <p>Runs all checks in parallel and aggregates results.
     *
     * @param tenantId tenant identifier
     * @param content content to validate (non-null)
     * @param policiesToCheck policies to check against (non-null, non-empty)
     * @return promise of aggregated check result
     * @throws NullPointerException if any parameter is null
     * @throws IllegalArgumentException if content is blank or policiesToCheck is empty
     */
    public Promise<PolicyCheckResult> validateContent(
            String tenantId,
            String content,
            Set<PolicyType> policiesToCheck
    ) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(content, "content cannot be null");
        Objects.requireNonNull(policiesToCheck, "policiesToCheck cannot be null");

        if (content.isBlank()) {
            return Promise.ofException(new IllegalArgumentException("content cannot be blank"));
        }

        if (policiesToCheck.isEmpty()) {
            return Promise.ofException(new IllegalArgumentException("policiesToCheck cannot be empty"));
        }

        long startTime = System.currentTimeMillis();

        // Split policies by checker
        Set<PolicyType> ruleBasedPolicies = policiesToCheck.stream()
                .filter(p -> ruleBasedChecker.getSupportedPolicies().contains(p))
                .collect(Collectors.toSet());

        Set<PolicyType> mlPolicies = policiesToCheck.stream()
                .filter(p -> mlChecker.getSupportedPolicies().contains(p))
                .collect(Collectors.toSet());

        // Run checks in parallel
        List<Promise<PolicyCheckResult>> checkPromises = new ArrayList<>();

        if (!ruleBasedPolicies.isEmpty()) {
            checkPromises.add(ruleBasedChecker.checkContent(tenantId, content, ruleBasedPolicies));
        }

        if (!mlPolicies.isEmpty()) {
            checkPromises.add(mlChecker.checkContent(tenantId, content, mlPolicies));
        }

        // Aggregate results
        return Promise.ofAll(checkPromises)
                .map(results -> {
                    PolicyCheckResult aggregated = aggregateResults(results);

                    long duration = System.currentTimeMillis() - startTime;
                    metrics.incrementCounter("policy.validation.count",
                            "tenant", tenantId,
                            "passed", String.valueOf(aggregated.passed()));
                    metrics.recordTimer("policy.validation.duration", duration,
                            "tenant", tenantId,
                            "policy_count", String.valueOf(policiesToCheck.size()));

                    return aggregated;
                })
                .whenException(e -> {
                    metrics.incrementCounter("policy.validation.errors",
                            "tenant", tenantId,
                            "error", e.getClass().getSimpleName());
                });
    }

    /**
     * Validates a batch of content items.
     *
     * <p>Each content item is validated independently in parallel.
     * Results are returned in the same order as input.
     *
     * @param tenantId tenant identifier
     * @param contents list of content to validate (non-null, non-empty)
     * @param policiesToCheck policies to check against (non-null, non-empty)
     * @return promise of list of check results (same order as input)
     * @throws NullPointerException if any parameter is null
     * @throws IllegalArgumentException if contents is empty or policiesToCheck is empty
     */
    public Promise<List<PolicyCheckResult>> validateBatch(
            String tenantId,
            List<String> contents,
            Set<PolicyType> policiesToCheck
    ) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(contents, "contents cannot be null");
        Objects.requireNonNull(policiesToCheck, "policiesToCheck cannot be null");

        if (contents.isEmpty()) {
            return Promise.ofException(new IllegalArgumentException("contents cannot be empty"));
        }

        if (policiesToCheck.isEmpty()) {
            return Promise.ofException(new IllegalArgumentException("policiesToCheck cannot be empty"));
        }

        // Validate each content item in parallel
        List<Promise<PolicyCheckResult>> validationPromises = contents.stream()
                .map(content -> validateContent(tenantId, content, policiesToCheck))
                .collect(Collectors.toList());

        return Promise.ofAll(validationPromises)
                .whenComplete((results, e) -> {
                    if (e == null) {
                        metrics.incrementCounter("policy.validation.batch.count",
                                "tenant", tenantId,
                                "batch_size", String.valueOf(contents.size()));
                    }
                });
    }

    /**
     * Configures policy settings for a tenant.
     *
     * <p>Configuration format varies by policy type:
     *
     * <p><b>PROFANITY</b><br>
     * <pre>
     * {
     *   "words": ["badword1", "badword2"]
     * }
     * </pre>
     *
     * <p><b>PII</b><br>
     * No configuration needed (uses standard regex patterns)
     *
     * <p><b>SPAM</b><br>
     * <pre>
     * {
     *   "keywords": ["buy now", "click here"]
     * }
     * </pre>
     *
     * <p><b>HATE_SPEECH</b><br>
     * <pre>
     * {
     *   "endpoint": "/v1/moderate/hate-speech",
     *   "threshold": 0.75
     * }
     * </pre>
     *
     * <p><b>NSFW</b><br>
     * <pre>
     * {
     *   "endpoint": "/v1/moderate/nsfw",
     *   "threshold": 0.80
     * }
     * </pre>
     *
     * <p><b>QUALITY_THRESHOLD</b><br>
     * <pre>
     * {
     *   "endpoint": "/v1/analyze/quality",
     *   "threshold": 0.60
     * }
     * </pre>
     *
     * @param tenantId tenant identifier
     * @param policyType policy type to configure
     * @param configuration policy-specific configuration (non-null)
     * @return promise that completes when configuration is updated
     * @throws NullPointerException if any parameter is null
     * @throws IllegalArgumentException if policyType is not supported
     */
    public Promise<Void> configurePolicy(
            String tenantId,
            PolicyType policyType,
            Map<String, Object> configuration
    ) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(policyType, "policyType cannot be null");
        Objects.requireNonNull(configuration, "configuration cannot be null");

        // Route to appropriate checker
        ContentPolicyChecker checker = getCheckerForPolicy(policyType);
        if (checker == null) {
            return Promise.ofException(new IllegalArgumentException(
                    "Unsupported policy type: " + policyType));
        }

        return checker.updatePolicyConfiguration(tenantId, policyType, configuration)
                .whenComplete((v, e) -> {
                    if (e == null) {
                        metrics.incrementCounter("policy.configuration.updated",
                                "tenant", tenantId,
                                "policy", policyType.name());
                    }
                });
    }

    /**
     * Gets all policies supported by this service.
     *
     * @return set of all supported policy types
     */
    public Set<PolicyType> getSupportedPolicies() {
        Set<PolicyType> supported = new HashSet<>();
        supported.addAll(ruleBasedChecker.getSupportedPolicies());
        supported.addAll(mlChecker.getSupportedPolicies());
        return Collections.unmodifiableSet(supported);
    }

    /**
     * Checks if a policy type is supported.
     *
     * @param policyType policy type to check
     * @return true if supported
     */
    public boolean isSupported(PolicyType policyType) {
        return ruleBasedChecker.getSupportedPolicies().contains(policyType) ||
                mlChecker.getSupportedPolicies().contains(policyType);
    }

    /**
     * Gets the checker responsible for a policy type.
     *
     * @param policyType policy type
     * @return checker instance or null if not supported
     */
    private ContentPolicyChecker getCheckerForPolicy(PolicyType policyType) {
        if (ruleBasedChecker.getSupportedPolicies().contains(policyType)) {
            return ruleBasedChecker;
        }
        if (mlChecker.getSupportedPolicies().contains(policyType)) {
            return mlChecker;
        }
        return null;
    }

    /**
     * Aggregates multiple policy check results.
     *
     * <p>Strategy:
     * - If any result failed, aggregate fails
     * - All violations from all results are combined
     * - Final score is weighted average of individual scores
     *
     * @param results individual policy check results
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
}
