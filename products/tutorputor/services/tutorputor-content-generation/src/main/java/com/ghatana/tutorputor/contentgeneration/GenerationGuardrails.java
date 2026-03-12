package com.ghatana.tutorputor.contentgeneration;

import java.util.*;

/**
 * Immutable value object for content generation guardrails.
 *
 * <p><b>Purpose</b><br>
 * Defines safety constraints for content generation operations.
 * Enforces maximum length, forbidden patterns, and policy compliance.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * GenerationGuardrails guardrails = GenerationGuardrails.builder()
 *     .maxLength(1000)
 *     .minLength(50)
 *     .forbiddenPatterns("hate", "violence")
 *     .requiresPolicyCheck(true)
 *     .build();
 * 
 * if (guardrails.exceeds("Very long text...", guardrails.getMaxLength())) {
 *     // Handle length violation
 * }
 * }</pre>
 *
 * <p><b>Guardrails</b><br>
 * - maxLength: Maximum output length (default 2000)
 * - minLength: Minimum output length (default 10)
 * - forbiddenPatterns: Patterns that should not appear in output
 * - requiredPatterns: Patterns that must appear in output
 * - requiresPolicyCheck: Whether output must pass policy validation
 * - allowsHTML: Whether HTML is permitted in output
 * - allowsMarkdown: Whether Markdown is permitted in output
 *
 * <p><b>Thread Safety</b><br>
 * Immutable after construction; thread-safe.
 *
 * @doc.type class
 * @doc.purpose Immutable generation guardrails and safety constraints
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public final class GenerationGuardrails {

    private final int maxLength;
    private final int minLength;
    private final Set<String> forbiddenPatterns;
    private final Set<String> requiredPatterns;
    private final boolean requiresPolicyCheck;
    private final boolean allowsHTML;
    private final boolean allowsMarkdown;
    private final Set<String> allowedLanguages;
    private static final int MAX_LENGTH_LIMIT = 50000;
    private static final int MIN_LENGTH_MIN = 1;

    /**
     * Creates new generation guardrails.
     *
     * @param maxLength maximum output length (1 to 50,000)
     * @param minLength minimum output length (>= 1)
     * @param forbiddenPatterns patterns forbidden in output (non-null, case-insensitive)
     * @param requiredPatterns patterns required in output (non-null)
     * @param requiresPolicyCheck whether policy validation required
     * @param allowsHTML whether HTML is allowed
     * @param allowsMarkdown whether Markdown is allowed
     * @param allowedLanguages permitted output languages (non-null)
     * @throws IllegalArgumentException if lengths invalid
     * @throws NullPointerException if collections null
     */
    private GenerationGuardrails(
            int maxLength,
            int minLength,
            Set<String> forbiddenPatterns,
            Set<String> requiredPatterns,
            boolean requiresPolicyCheck,
            boolean allowsHTML,
            boolean allowsMarkdown,
            Set<String> allowedLanguages) {
        
        if (maxLength < 1 || maxLength > MAX_LENGTH_LIMIT) {
            throw new IllegalArgumentException(
                    "maxLength must be between 1 and " + MAX_LENGTH_LIMIT + ", got " + maxLength
            );
        }
        if (minLength < MIN_LENGTH_MIN || minLength > maxLength) {
            throw new IllegalArgumentException(
                    "minLength must be between " + MIN_LENGTH_MIN + " and " + maxLength + ", got " + minLength
            );
        }

        this.maxLength = maxLength;
        this.minLength = minLength;
        this.forbiddenPatterns = Collections.unmodifiableSet(
                new HashSet<>(Objects.requireNonNull(forbiddenPatterns, "forbiddenPatterns cannot be null"))
        );
        this.requiredPatterns = Collections.unmodifiableSet(
                new HashSet<>(Objects.requireNonNull(requiredPatterns, "requiredPatterns cannot be null"))
        );
        this.requiresPolicyCheck = requiresPolicyCheck;
        this.allowsHTML = allowsHTML;
        this.allowsMarkdown = allowsMarkdown;
        this.allowedLanguages = Collections.unmodifiableSet(
                new HashSet<>(Objects.requireNonNull(allowedLanguages, "allowedLanguages cannot be null"))
        );
    }

    /**
     * Gets maximum allowed output length.
     *
     * @return max length in characters
     */
    public int getMaxLength() {
        return maxLength;
    }

    /**
     * Gets minimum required output length.
     *
     * @return min length in characters
     */
    public int getMinLength() {
        return minLength;
    }

    /**
     * Gets forbidden patterns.
     *
     * @return unmodifiable set of forbidden patterns
     */
    public Set<String> getForbiddenPatterns() {
        return forbiddenPatterns;
    }

    /**
     * Gets required patterns.
     *
     * @return unmodifiable set of required patterns
     */
    public Set<String> getRequiredPatterns() {
        return requiredPatterns;
    }

    /**
     * Gets whether policy validation is required.
     *
     * @return true if policy check required
     */
    public boolean isRequiresPolicyCheck() {
        return requiresPolicyCheck;
    }

    /**
     * Gets whether HTML is allowed.
     *
     * @return true if HTML permitted
     */
    public boolean isAllowsHTML() {
        return allowsHTML;
    }

    /**
     * Gets whether Markdown is allowed.
     *
     * @return true if Markdown permitted
     */
    public boolean isAllowsMarkdown() {
        return allowsMarkdown;
    }

    /**
     * Gets allowed output languages.
     *
     * @return unmodifiable set of language codes
     */
    public Set<String> getAllowedLanguages() {
        return allowedLanguages;
    }

    /**
     * Validates generated content against guardrails.
     *
     * @param content generated content to validate (non-null)
     * @return validation result with violations if any
     * @throws NullPointerException if content is null
     */
    public GuardrailValidationResult validate(String content) {
        Objects.requireNonNull(content, "content cannot be null");
        List<String> violations = new ArrayList<>();

        // Check length
        if (content.length() > maxLength) {
            violations.add("Content exceeds maximum length: " + content.length() + " > " + maxLength);
        }
        if (content.length() < minLength) {
            violations.add("Content below minimum length: " + content.length() + " < " + minLength);
        }

        // Check forbidden patterns (case-insensitive)
        String lowerContent = content.toLowerCase();
        for (String pattern : forbiddenPatterns) {
            if (lowerContent.contains(pattern.toLowerCase())) {
                violations.add("Forbidden pattern found: " + pattern);
            }
        }

        // Check required patterns (case-insensitive)
        for (String pattern : requiredPatterns) {
            if (!lowerContent.contains(pattern.toLowerCase())) {
                violations.add("Required pattern not found: " + pattern);
            }
        }

        // Check HTML
        if (!allowsHTML && containsHTML(content)) {
            violations.add("HTML content not allowed");
        }

        // Check Markdown
        if (!allowsMarkdown && containsMarkdown(content)) {
            violations.add("Markdown content not allowed");
        }

        return violations.isEmpty()
                ? GuardrailValidationResult.valid()
                : GuardrailValidationResult.invalid(violations);
    }

    /**
     * Checks if output exceeds length limit.
     *
     * @param content content to check (non-null)
     * @param limit length limit
     * @return true if content exceeds limit
     * @throws NullPointerException if content is null
     */
    public boolean exceedsLength(String content, int limit) {
        Objects.requireNonNull(content, "content cannot be null");
        return content.length() > limit;
    }

    /**
     * Checks if content contains HTML tags.
     *
     * @param content content to check
     * @return true if HTML detected
     */
    private boolean containsHTML(String content) {
        return content.matches(".*<[^>]+>.*");
    }

    /**
     * Checks if content contains Markdown syntax.
     *
     * @param content content to check
     * @return true if Markdown detected
     */
    private boolean containsMarkdown(String content) {
        return content.matches(".*[*_`\\[\\]#-].*");
    }

    /**
     * Creates a new builder for GenerationGuardrails.
     *
     * @return builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return "GenerationGuardrails{" +
                "maxLength=" + maxLength +
                ", minLength=" + minLength +
                ", forbiddenPatterns=" + forbiddenPatterns.size() +
                ", requiredPatterns=" + requiredPatterns.size() +
                ", requiresPolicyCheck=" + requiresPolicyCheck +
                ", allowsHTML=" + allowsHTML +
                ", allowsMarkdown=" + allowsMarkdown +
                ", allowedLanguages=" + allowedLanguages.size() +
                '}';
    }

    /**
     * Result of guardrail validation.
     */
    public static class GuardrailValidationResult {
        private final boolean valid;
        private final List<String> violations;

        private GuardrailValidationResult(boolean valid, List<String> violations) {
            this.valid = valid;
            this.violations = Collections.unmodifiableList(violations);
        }

        /**
         * Creates valid result.
         *
         * @return valid result
         */
        public static GuardrailValidationResult valid() {
            return new GuardrailValidationResult(true, Collections.emptyList());
        }

        /**
         * Creates invalid result.
         *
         * @param violations list of violation messages
         * @return invalid result
         */
        public static GuardrailValidationResult invalid(List<String> violations) {
            return new GuardrailValidationResult(false, violations);
        }

        public boolean isValid() {
            return valid;
        }

        public List<String> getViolations() {
            return violations;
        }

        @Override
        public String toString() {
            return valid ? "VALID" : "INVALID: " + String.join(", ", violations);
        }
    }

    /**
     * Builder for GenerationGuardrails construction.
     */
    public static class Builder {
        private int maxLength = 2000;
        private int minLength = 10;
        private Set<String> forbiddenPatterns = new HashSet<>();
        private Set<String> requiredPatterns = new HashSet<>();
        private boolean requiresPolicyCheck = true;
        private boolean allowsHTML = false;
        private boolean allowsMarkdown = true;
        private Set<String> allowedLanguages = new HashSet<>(Arrays.asList("en", "es", "fr", "de", "it", "ja", "zh"));

        /**
         * Sets maximum length.
         *
         * @param maxLength max length (1 to 50,000)
         * @return this builder
         */
        public Builder maxLength(int maxLength) {
            this.maxLength = maxLength;
            return this;
        }

        /**
         * Sets minimum length.
         *
         * @param minLength min length (>= 1)
         * @return this builder
         */
        public Builder minLength(int minLength) {
            this.minLength = minLength;
            return this;
        }

        /**
         * Adds forbidden pattern.
         *
         * @param pattern pattern to forbid (case-insensitive)
         * @return this builder
         */
        public Builder forbiddenPattern(String pattern) {
            Objects.requireNonNull(pattern, "pattern cannot be null");
            forbiddenPatterns.add(pattern);
            return this;
        }

        /**
         * Adds forbidden patterns.
         *
         * @param patterns patterns to forbid
         * @return this builder
         */
        public Builder forbiddenPatterns(String... patterns) {
            Objects.requireNonNull(patterns, "patterns cannot be null");
            forbiddenPatterns.addAll(Arrays.asList(patterns));
            return this;
        }

        /**
         * Adds required pattern.
         *
         * @param pattern pattern that must appear
         * @return this builder
         */
        public Builder requiredPattern(String pattern) {
            Objects.requireNonNull(pattern, "pattern cannot be null");
            requiredPatterns.add(pattern);
            return this;
        }

        /**
         * Sets whether policy check required.
         *
         * @param required true if policy validation required
         * @return this builder
         */
        public Builder requiresPolicyCheck(boolean required) {
            this.requiresPolicyCheck = required;
            return this;
        }

        /**
         * Sets whether HTML allowed.
         *
         * @param allowed true if HTML permitted
         * @return this builder
         */
        public Builder allowsHTML(boolean allowed) {
            this.allowsHTML = allowed;
            return this;
        }

        /**
         * Sets whether Markdown allowed.
         *
         * @param allowed true if Markdown permitted
         * @return this builder
         */
        public Builder allowsMarkdown(boolean allowed) {
            this.allowsMarkdown = allowed;
            return this;
        }

        /**
         * Sets allowed languages.
         *
         * @param languages set of language codes
         * @return this builder
         */
        public Builder allowedLanguages(Set<String> languages) {
            Objects.requireNonNull(languages, "languages cannot be null");
            this.allowedLanguages = new HashSet<>(languages);
            return this;
        }

        /**
         * Builds GenerationGuardrails.
         *
         * @return configured guardrails
         */
        public GenerationGuardrails build() {
            return new GenerationGuardrails(
                    maxLength, minLength,
                    forbiddenPatterns, requiredPatterns,
                    requiresPolicyCheck,
                    allowsHTML, allowsMarkdown,
                    allowedLanguages
            );
        }
    }
}
