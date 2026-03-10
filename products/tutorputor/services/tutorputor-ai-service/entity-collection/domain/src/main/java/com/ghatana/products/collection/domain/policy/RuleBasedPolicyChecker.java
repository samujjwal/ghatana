package com.ghatana.products.collection.domain.policy;

import io.activej.promise.Promise;
import com.ghatana.platform.observability.MetricsCollector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Rule-based policy checker using pattern matching and word lists.
 *
 * <p><b>Purpose</b><br>
 * Implements content policy checking using deterministic rules:
 * - PROFANITY: Word list matching with case-insensitive detection
 * - PII: Regex patterns for email, phone, SSN, credit cards
 * - SPAM: Keyword detection with frequency analysis
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * RuleBasedPolicyChecker checker = new RuleBasedPolicyChecker(metricsCollector);
 * 
 * // Configure profanity list
 * checker.updatePolicyConfiguration("tenant-123", PolicyType.PROFANITY,
 *     Map.of("words", List.of("badword1", "badword2")));
 * 
 * // Check content
 * PolicyCheckResult result = checker.checkContent(
 *     "tenant-123",
 *     "Some text with badword1",
 *     Set.of(PolicyType.PROFANITY, PolicyType.PII)
 * ).getResult();
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe using ConcurrentHashMap for rule storage.
 * Individual checks are stateless.
 *
 * <p><b>Performance Characteristics</b><br>
 * - O(n*m) for profanity checking (n = content length, m = word list size)
 * - O(n) for PII regex scanning
 * - O(n) for spam keyword detection
 * - All checks run in parallel when multiple policies requested
 *
 * @see ContentPolicyChecker
 * @see PolicyCheckResult
 * @see PolicyType
 * @doc.type class
 * @doc.purpose Rule-based content policy checker
 * @doc.layer domain
 * @doc.pattern Adapter
 */
public class RuleBasedPolicyChecker implements ContentPolicyChecker {

    private final MetricsCollector metrics;
    
    // Tenant-specific rule configurations
    private final Map<String, Map<PolicyType, Map<String, Object>>> tenantConfigs;

    // PII detection patterns (compiled once for performance)
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b"
    );
    
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "\\b(?:\\+?1[-.]?)?" +
            "(?:\\(?([2-9][0-9]{2})\\)?[-.]?)?" +
            "([2-9][0-9]{2})[-.]?([0-9]{4})\\b"
    );
    
    private static final Pattern SSN_PATTERN = Pattern.compile(
            "\\b(?!000|666|9\\d{2})\\d{3}-?(?!00)\\d{2}-?(?!0000)\\d{4}\\b"
    );
    
    private static final Pattern CREDIT_CARD_PATTERN = Pattern.compile(
            "\\b(?:4[0-9]{12}(?:[0-9]{3})?|" +  // Visa
            "5[1-5][0-9]{14}|" +                 // MasterCard
            "3[47][0-9]{13}|" +                  // Amex
            "6(?:011|5[0-9]{2})[0-9]{12})\\b"    // Discover
    );
    
    private static final Pattern IP_PATTERN = Pattern.compile(
            "\\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}" +
            "(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\b"
    );
    
    private static final Pattern POSTAL_CODE_PATTERN = Pattern.compile(
            "\\b\\d{5}(?:-\\d{4})?\\b"  // US ZIP codes
    );

    // Default profanity list (can be overridden per tenant)
    private static final Set<String> DEFAULT_PROFANITY_WORDS = Set.of(
            "badword", "offensive", "inappropriate"
            // Add more words as needed
    );

    // Default spam keywords
    private static final Set<String> DEFAULT_SPAM_KEYWORDS = Set.of(
            "buy now", "click here", "limited time", "act now",
            "free money", "guarantee", "no risk", "winner"
    );

    /**
     * Creates a new rule-based policy checker.
     *
     * @param metrics metrics collector for observability
     * @throws NullPointerException if metrics is null
     */
    public RuleBasedPolicyChecker(MetricsCollector metrics) {
        this.metrics = Objects.requireNonNull(metrics, "metrics cannot be null");
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

        try {
            // Run all policy checks in parallel
            List<PolicyCheckResult> results = policiesToCheck.stream()
                    .filter(this::isSupported)
                    .map(policyType -> checkSinglePolicy(tenantId, content, policyType))
                    .collect(Collectors.toList());

            // Aggregate results
            PolicyCheckResult aggregated = aggregateResults(results);

            long duration = System.currentTimeMillis() - startTime;
            metrics.incrementCounter("policy.check.count",
                    "tenant", tenantId,
                    "passed", String.valueOf(aggregated.passed()));
            metrics.recordTimer("policy.check.duration", duration,
                    "tenant", tenantId);

            return Promise.of(aggregated);

        } catch (Exception e) {
            metrics.incrementCounter("policy.check.errors",
                    "tenant", tenantId,
                    "error", e.getClass().getSimpleName());
            return Promise.ofException(e);
        }
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

        // Check each content item sequentially
        List<PolicyCheckResult> results = contents.stream()
                .map(content -> checkContent(tenantId, content, policiesToCheck))
                .map(Promise::getResult)  // Wait for each result
                .collect(Collectors.toList());

        return Promise.of(results);
    }

    @Override
    public Set<PolicyType> getSupportedPolicies() {
        return Set.of(PolicyType.PROFANITY, PolicyType.PII, PolicyType.SPAM);
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

        metrics.incrementCounter("policy.config.updated",
                "tenant", tenantId,
                "policy", policyType.name());

        return Promise.complete();
    }

    /**
     * Checks content against a single policy type.
     *
     * @param tenantId tenant identifier
     * @param content content to check
     * @param policyType policy to check against
     * @return check result for this policy
     */
    private PolicyCheckResult checkSinglePolicy(
            String tenantId,
            String content,
            PolicyType policyType
    ) {
        return switch (policyType) {
            case PROFANITY -> checkProfanity(tenantId, content);
            case PII -> checkPII(tenantId, content);
            case SPAM -> checkSpam(tenantId, content);
            default -> PolicyCheckResult.pass(policyType, 1.0);
        };
    }

    /**
     * Checks for profane words using word list matching.
     *
     * @param tenantId tenant identifier
     * @param content content to check
     * @return profanity check result
     */
    private PolicyCheckResult checkProfanity(String tenantId, String content) {
        Set<String> wordList = getProfanityWords(tenantId);
        List<PolicyCheckResult.PolicyViolation> violations = new ArrayList<>();

        String lowerContent = content.toLowerCase();
        for (String badWord : wordList) {
            int index = lowerContent.indexOf(badWord.toLowerCase());
            while (index >= 0) {
                violations.add(new PolicyCheckResult.PolicyViolation(
                        PolicyType.PROFANITY,
                        "HIGH",
                        "position " + index,
                        "Profane word detected: " + badWord,
                        "Remove or replace the offensive word"
                ));
                index = lowerContent.indexOf(badWord.toLowerCase(), index + 1);
            }
        }

        if (violations.isEmpty()) {
            return PolicyCheckResult.pass(PolicyType.PROFANITY, 1.0);
        }

        double score = Math.max(0.0, 1.0 - (violations.size() * 0.2));
        return PolicyCheckResult.failWithViolations(
                PolicyType.PROFANITY,
                violations,
                score
        );
    }

    /**
     * Checks for PII using regex patterns.
     *
     * @param tenantId tenant identifier
     * @param content content to check
     * @return PII check result
     */
    private PolicyCheckResult checkPII(String tenantId, String content) {
        List<PolicyCheckResult.PolicyViolation> violations = new ArrayList<>();

        // Check for emails
        violations.addAll(findPIIMatches(content, EMAIL_PATTERN, "EMAIL"));

        // Check for phone numbers
        violations.addAll(findPIIMatches(content, PHONE_PATTERN, "PHONE"));

        // Check for SSNs
        violations.addAll(findPIIMatches(content, SSN_PATTERN, "SSN"));

        // Check for credit cards
        violations.addAll(findPIIMatches(content, CREDIT_CARD_PATTERN, "CREDIT_CARD"));

        // Check for IP addresses
        violations.addAll(findPIIMatches(content, IP_PATTERN, "IP_ADDRESS"));

        // Check for postal codes
        violations.addAll(findPIIMatches(content, POSTAL_CODE_PATTERN, "POSTAL_CODE"));

        if (violations.isEmpty()) {
            return PolicyCheckResult.pass(PolicyType.PII, 1.0);
        }

        double score = Math.max(0.0, 1.0 - (violations.size() * 0.15));
        return PolicyCheckResult.failWithViolations(
                PolicyType.PII,
                violations,
                score
        );
    }

    /**
     * Checks for spam keywords and patterns.
     *
     * @param tenantId tenant identifier
     * @param content content to check
     * @return spam check result
     */
    private PolicyCheckResult checkSpam(String tenantId, String content) {
        Set<String> spamKeywords = getSpamKeywords(tenantId);
        List<PolicyCheckResult.PolicyViolation> violations = new ArrayList<>();

        String lowerContent = content.toLowerCase();
        for (String keyword : spamKeywords) {
            int index = lowerContent.indexOf(keyword.toLowerCase());
            while (index >= 0) {
                violations.add(new PolicyCheckResult.PolicyViolation(
                        PolicyType.SPAM,
                        "MEDIUM",
                        "position " + index,
                        "Spam keyword detected: " + keyword,
                        "Remove marketing language"
                ));
                index = lowerContent.indexOf(keyword.toLowerCase(), index + 1);
            }
        }

        // Additional spam signals
        long capsCount = content.chars().filter(Character::isUpperCase).count();
        long totalLetters = content.chars().filter(Character::isLetter).count();
        if (totalLetters > 0 && (double) capsCount / totalLetters > 0.5) {
            violations.add(new PolicyCheckResult.PolicyViolation(
                    PolicyType.SPAM,
                    "LOW",
                    "global",
                    "Excessive capitalization detected",
                    "Use normal case"
            ));
        }

        if (violations.isEmpty()) {
            return PolicyCheckResult.pass(PolicyType.SPAM, 1.0);
        }

        double score = Math.max(0.0, 1.0 - (violations.size() * 0.1));
        return PolicyCheckResult.failWithViolations(
                PolicyType.SPAM,
                violations,
                score
        );
    }

    /**
     * Finds PII matches using a regex pattern.
     *
     * @param content content to scan
     * @param pattern regex pattern
     * @param piiTypeName PII type name for violation
     * @return list of violations
     */
    private List<PolicyCheckResult.PolicyViolation> findPIIMatches(
            String content,
            Pattern pattern,
            String piiTypeName
    ) {
        List<PolicyCheckResult.PolicyViolation> violations = new ArrayList<>();
        Matcher matcher = pattern.matcher(content);

        while (matcher.find()) {
            violations.add(new PolicyCheckResult.PolicyViolation(
                    PolicyType.PII,
                    "CRITICAL",
                    "position " + matcher.start(),
                    piiTypeName + " detected: " + redactPII(matcher.group(), piiTypeName),
                    "Remove or redact the " + piiTypeName
            ));
        }

        return violations;
    }

    /**
     * Redacts PII for violation messages.
     *
     * @param value PII value
     * @param type PII type
     * @return redacted version
     */
    private String redactPII(String value, String type) {
        if (value.length() <= 4) {
            return "***";
        }
        return "***" + value.substring(value.length() - 4);
    }

    /**
     * Gets profanity word list for tenant.
     *
     * @param tenantId tenant identifier
     * @return profanity word set
     */
    @SuppressWarnings("unchecked")
    private Set<String> getProfanityWords(String tenantId) {
        Map<String, Object> config = getTenantConfig(tenantId, PolicyType.PROFANITY);
        if (config != null && config.containsKey("words")) {
            Object words = config.get("words");
            if (words instanceof List) {
                return new HashSet<>((List<String>) words);
            }
        }
        return DEFAULT_PROFANITY_WORDS;
    }

    /**
     * Gets spam keyword list for tenant.
     *
     * @param tenantId tenant identifier
     * @return spam keyword set
     */
    @SuppressWarnings("unchecked")
    private Set<String> getSpamKeywords(String tenantId) {
        Map<String, Object> config = getTenantConfig(tenantId, PolicyType.SPAM);
        if (config != null && config.containsKey("keywords")) {
            Object keywords = config.get("keywords");
            if (keywords instanceof List) {
                return new HashSet<>((List<String>) keywords);
            }
        }
        return DEFAULT_SPAM_KEYWORDS;
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
