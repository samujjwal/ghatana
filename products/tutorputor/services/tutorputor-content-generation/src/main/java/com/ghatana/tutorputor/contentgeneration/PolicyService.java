package com.ghatana.tutorputor.contentgeneration;

import com.ghatana.tutorputor.contentgeneration.*;
import com.ghatana.core.activej.promise.PromiseCompat;
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

        Map<ContentPolicyChecker, Set<PolicyType>> policiesByChecker = groupPoliciesByChecker(policiesToCheck);
        List<Promise<PolicyCheckResult>> checkPromises = new ArrayList<>();
        for (Map.Entry<ContentPolicyChecker, Set<PolicyType>> entry : policiesByChecker.entrySet()) {
            checkPromises.add(entry.getKey()
                .checkContent(tenantId, content, entry.getValue())
                .mapException(e -> e));
        }

        return PromiseCompat.all(checkPromises)
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
                .mapException(e -> {
                    metrics.incrementCounter("policy.validation.errors",
                            "tenant", tenantId,
                            "error", e.getClass().getSimpleName());
                    return e;
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

        return PromiseCompat.all(validationPromises)
                .map(results -> {
                    metrics.incrementCounter("policy.validation.batch.count",
                            "tenant", tenantId,
                            "batch_size", String.valueOf(contents.size()));
                    return results;
                });
    }

    /**
     * Configures policy settings for a tenant.
     *
     * @param tenantId tenant identifier
     * @param policyType policy type to configure
     * @param configuration policy-specific configuration (non-null)
     * @return promise that completes when configuration is updated
     */
    public Promise<Void> configurePolicy(
            String tenantId,
            PolicyType policyType,
            Map<String, Object> configuration
    ) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(policyType, "policyType cannot be null");
        Objects.requireNonNull(configuration, "configuration cannot be null");

        // For now, just log the configuration
        metrics.incrementCounter("policy.configuration.attempted",
                "tenant", tenantId,
                "policy", policyType.name());
        return checkerFor(policyType).updatePolicyConfiguration(tenantId, policyType, configuration);
    }

    /**
     * Gets all policies supported by this service.
     *
     * @return set of all supported policy types
     */
    public Set<PolicyType> getSupportedPolicies() {
        Set<PolicyType> supportedPolicies = new HashSet<>(ruleBasedChecker.getSupportedPolicies());
        supportedPolicies.addAll(mlChecker.getSupportedPolicies());
        return Collections.unmodifiableSet(supportedPolicies);
    }

    /**
     * Checks if a policy type is supported.
     *
     * @param policyType policy type to check
     * @return true if supported
     */
    public boolean isSupported(PolicyType policyType) {
        return getSupportedPolicies().contains(policyType);
    }

    /**
     * Aggregates multiple policy check results.
     *
     * <p>Strategy:
     * - If any result failed, aggregate fails
     * - All violations from all results are combined
     *
     * @param results individual policy check results
     * @return aggregated result
     */
    private PolicyCheckResult aggregateResults(List<PolicyCheckResult> results) {
        if (results.isEmpty()) {
            return PolicyCheckResult.pass(PolicyType.PROFANITY, 1.0);
        }

        boolean allPassed = results.stream().allMatch(PolicyCheckResult::passed);
        double averageScore = results.stream()
                .mapToDouble(PolicyCheckResult::score)
                .average()
                .orElse(1.0);

        List<PolicyCheckResult.PolicyViolation> allViolations = results.stream()
                .flatMap(r -> r.violations().stream())
                .collect(Collectors.toList());

        if (allPassed) {
            return PolicyCheckResult.pass(results.get(0).policyType(), averageScore);
        }

        PolicyType policyType = results.stream()
                .filter(r -> !r.passed())
                .map(r -> r.policyType())
                .findFirst()
                .orElse(PolicyType.PROFANITY);

        return PolicyCheckResult.failWithViolations(policyType, allViolations, averageScore);
    }

    private Map<ContentPolicyChecker, Set<PolicyType>> groupPoliciesByChecker(Set<PolicyType> policiesToCheck) {
        Map<ContentPolicyChecker, Set<PolicyType>> grouped = new LinkedHashMap<>();
        for (PolicyType policyType : policiesToCheck) {
            ContentPolicyChecker checker = checkerFor(policyType);
            grouped.computeIfAbsent(checker, ignored -> new LinkedHashSet<>()).add(policyType);
        }
        return grouped;
    }

    private ContentPolicyChecker checkerFor(PolicyType policyType) {
        if (ruleBasedChecker.getSupportedPolicies().contains(policyType)) {
            return ruleBasedChecker;
        }
        if (mlChecker.getSupportedPolicies().contains(policyType)) {
            return mlChecker;
        }
        throw new IllegalArgumentException("Unsupported policy type: " + policyType);
    }
}
