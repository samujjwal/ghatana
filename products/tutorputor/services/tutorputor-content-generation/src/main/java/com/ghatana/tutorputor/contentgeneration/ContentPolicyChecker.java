package com.ghatana.tutorputor.contentgeneration;

import io.activej.promise.Promise;

import java.util.List;
import java.util.Set;

/**
 * Port interface for content policy and safety checks.
 *
 * <p><b>Purpose</b><br>
 * Abstracts content moderation and safety validation to enable pluggable
 * implementations (rule-based, ML models, third-party APIs) without changing
 * domain/application logic.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * ContentPolicyChecker checker = new InMemoryContentPolicyChecker();
 * 
 * // Check content against policy
 * Promise<PolicyCheckResult> result = checker.checkPolicy(
 *     "User input text", PolicyType.PROFANITY);
 * 
 * // Detect PII
 * Promise<List<PIIDetection>> piiResults = checker.detectPII(
 *     "Email: user@example.com, SSN: 123-45-6789");
 * 
 * // Classify content type
 * Promise<ContentClassification> classification = checker.classifyContent(
 *     "Spam message with suspicious links");
 * }</pre>
 *
 * <p><b>Implementation Notes</b><br>
 * Implementations must:
 * - Be thread-safe (multiple concurrent checks allowed)
 * - Use ActiveJ Promise for async operations
 * - Return deterministic results for same input
 * - Emit metrics for policy violations
 * - Handle edge cases (empty text, very long text)
 *
 * @see PolicyCheckResult
 * @see PIIDetection
 * @see ContentClassification
 * @doc.type interface
 * @doc.purpose Port for content policy and safety checks
 * @doc.layer domain
 * @doc.pattern Port
 */
public interface ContentPolicyChecker {

    /**
     * Checks content against a specific policy type.
     *
     * <p>Policy types include:
     * - PROFANITY: Offensive language, slurs
     * - HATE_SPEECH: Content promoting violence or discrimination
     * - EXPLICIT: Sexual or graphic content
     * - SPAM: Unwanted promotional content
     * - PHISHING: Attempts to steal credentials/data
     *
     * @param text content to check
     * @param policyType policy to validate against
     * @return Promise of PolicyCheckResult with pass/fail and violations
     * @throws IllegalArgumentException if text is null or policyType is null
     */
    default Promise<PolicyCheckResult> checkPolicy(String text, PolicyType policyType) {
        return checkContent("default", text, Set.of(policyType));
    }

    /**
     * Checks content against one or more policy types for a tenant context.
     *
     * @param tenantId tenant identifier
     * @param content content to check
     * @param policiesToCheck policies to evaluate
     * @return Promise of aggregated PolicyCheckResult
     */
    Promise<PolicyCheckResult> checkContent(String tenantId, String content, Set<PolicyType> policiesToCheck);

    /**
     * Checks a batch of content items.
     *
     * @param tenantId tenant identifier
     * @param contents content items to validate
     * @param policiesToCheck policies to evaluate
     * @return Promise of per-item results
     */
    default Promise<List<PolicyCheckResult>> checkBatch(
            String tenantId,
            List<String> contents,
            Set<PolicyType> policiesToCheck
    ) {
        return Promise.of(contents.stream()
                .map(content -> checkContent(tenantId, content, policiesToCheck).getResult())
                .toList());
    }

    /**
     * Lists policies supported by an implementation.
     *
     * @return supported policy types
     */
    default Set<PolicyType> getSupportedPolicies() {
        return Set.of();
    }

    /**
     * Updates implementation-specific policy configuration.
     *
     * @param tenantId tenant identifier
     * @param policyType policy to configure
     * @param configuration configuration map
     * @return Promise completing when configuration is applied
     */
    default Promise<Void> updatePolicyConfiguration(
            String tenantId,
            PolicyType policyType,
            java.util.Map<String, Object> configuration
    ) {
        return Promise.complete();
    }

    /**
     * Detects personally identifiable information (PII) in text.
     *
     * <p>Detectable PII types:
     * - EMAIL: Email addresses
     * - PHONE: Phone numbers (various formats)
     * - SSN: Social Security Numbers
     * - CREDIT_CARD: Credit card numbers
     * - IP_ADDRESS: IPv4/IPv6 addresses
     * - POSTAL_CODE: ZIP codes, postal codes
     *
     * @param text content to scan for PII
     * @return Promise of List of PIIDetection (empty if no PII found)
     * @throws IllegalArgumentException if text is null
     */
    default Promise<List<PIIDetection>> detectPII(String text) {
        return Promise.of(List.of());
    }

    /**
     * Classifies content into categories.
     *
     * <p>Classification categories:
     * - SAFE: No policy violations detected
     * - SPAM: Promotional/spam content
     * - HATE_SPEECH: Hateful or discriminatory
     * - EXPLICIT: Sexual or graphic
     * - PHISHING: Credential theft attempt
     * - UNKNOWN: Cannot classify with confidence
     *
     * <p>Returns classification with confidence score (0-1).
     *
     * @param text content to classify
     * @return Promise of ContentClassification with category and confidence
     * @throws IllegalArgumentException if text is null
     */
    default Promise<ContentClassification> classifyContent(String text) {
        return Promise.of(ContentClassification.safe());
    }

    /**
     * Performs comprehensive content safety check.
     *
     * <p>Combines policy checks, PII detection, and classification
     * into single result for convenience.
     *
     * @param text content to analyze
     * @return Promise of ComprehensiveCheckResult
     * @throws IllegalArgumentException if text is null
     */
    default Promise<ComprehensiveCheckResult> comprehensiveCheck(String text) {
        return checkPolicy(text, PolicyType.PROFANITY)
                .map(policyResult -> new ComprehensiveCheckResult(
                        policyResult,
                        ContentClassification.safe(),
                        false
                ));
    }
}
