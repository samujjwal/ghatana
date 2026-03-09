package com.ghatana.products.collection.domain.generation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for GenerationGuardrails.
 *
 * Tests validate:
 * - Length constraints (min/max)
 * - Forbidden pattern detection
 * - Required pattern checking
 * - HTML/Markdown content filtering
 * - Guardrail validation with violation reporting
 * - Builder pattern construction
 * - Immutability of configuration
 *
 * @see GenerationGuardrails
 */
@DisplayName("GenerationGuardrails Tests")
class GenerationGuardrailsTest {

    private GenerationGuardrails.Builder builder;

    @BeforeEach
    void setUp() {
        // GIVEN: Builder with defaults
        builder = GenerationGuardrails.builder();
    }

    /**
     * Verifies guardrails with default configuration.
     *
     * GIVEN: Builder created with defaults
     * WHEN: build() called
     * THEN: Guardrails have sensible defaults
     */
    @Test
    @DisplayName("Should create guardrails with default configuration")
    void shouldCreateGuardrailsWithDefaultConfiguration() {
        // WHEN: Build with defaults
        GenerationGuardrails guardrails = builder.build();

        // THEN: Defaults applied
        assertThat(guardrails.getMaxLength())
                .as("Default max length should be 2000")
                .isEqualTo(2000);
        assertThat(guardrails.getMinLength())
                .as("Default min length should be 10")
                .isEqualTo(10);
        assertThat(guardrails.isRequiresPolicyCheck())
                .as("Policy check should be required by default")
                .isTrue();
        assertThat(guardrails.isAllowsMarkdown())
                .as("Markdown should be allowed by default")
                .isTrue();
        assertThat(guardrails.isAllowsHTML())
                .as("HTML should not be allowed by default")
                .isFalse();
    }

    /**
     * Verifies length validation accepts content within limits.
     *
     * GIVEN: Content within min and max length
     * WHEN: validate() called
     * THEN: Validation passes
     */
    @Test
    @DisplayName("Should accept content within length limits")
    void shouldAcceptContentWithinLengthLimits() {
        // GIVEN: Guardrails with length limits
        GenerationGuardrails guardrails = builder
                .minLength(50)
                .maxLength(200)
                .build();

        // WHEN: Validate content within range
        GenerationGuardrails.GuardrailValidationResult result =
                guardrails.validate("This is valid content with sufficient length for validation purposes.");

        // THEN: Valid
        assertThat(result.isValid())
                .as("Content within length limits should be valid")
                .isTrue();
        assertThat(result.getViolations())
                .as("Should have no violations")
                .isEmpty();
    }

    /**
     * Verifies length validation rejects too short content.
     *
     * GIVEN: Content shorter than minLength
     * WHEN: validate() called
     * THEN: Validation fails with length violation
     */
    @Test
    @DisplayName("Should reject content below minimum length")
    void shouldRejectContentBelowMinimumLength() {
        // GIVEN: Guardrails requiring min 50 chars
        GenerationGuardrails guardrails = builder
                .minLength(50)
                .build();

        // WHEN: Validate short content
        GenerationGuardrails.GuardrailValidationResult result =
                guardrails.validate("Too short");

        // THEN: Invalid with violation
        assertThat(result.isValid())
                .as("Short content should fail validation")
                .isFalse();
        assertThat(result.getViolations())
                .as("Should have length violation")
                .hasSize(1)
                .anySatisfy(v -> assertThat(v).contains("minimum length"));
    }

    /**
     * Verifies length validation rejects too long content.
     *
     * GIVEN: Content longer than maxLength
     * WHEN: validate() called
     * THEN: Validation fails with length violation
     */
    @Test
    @DisplayName("Should reject content exceeding maximum length")
    void shouldRejectContentExceedingMaximumLength() {
        // GIVEN: Guardrails with max 100 chars
        GenerationGuardrails guardrails = builder
                .maxLength(100)
                .build();

        // WHEN: Validate long content
        String longContent = "x".repeat(101);
        GenerationGuardrails.GuardrailValidationResult result =
                guardrails.validate(longContent);

        // THEN: Invalid with violation
        assertThat(result.isValid())
                .as("Long content should fail validation")
                .isFalse();
        assertThat(result.getViolations())
                .as("Should have length violation")
                .hasSize(1)
                .anySatisfy(v -> assertThat(v).contains("exceeds maximum length"));
    }

    /**
     * Verifies forbidden patterns are case-insensitive.
     *
     * GIVEN: Forbidden pattern "hate"
     * WHEN: Content contains "HATE" or "Hate"
     * THEN: Validation fails (case-insensitive match)
     */
    @Test
    @DisplayName("Should detect forbidden patterns case-insensitively")
    void shouldDetectForbiddenPatternsCaseInsensitively() {
        // GIVEN: Forbidden pattern "hate"
        GenerationGuardrails guardrails = builder
                .forbiddenPattern("hate")
                .build();

        // WHEN: Validate content with uppercase version
        GenerationGuardrails.GuardrailValidationResult result =
                guardrails.validate("This content contains HATE speech.");

        // THEN: Detected (case-insensitive)
        assertThat(result.isValid())
                .as("Should detect forbidden pattern case-insensitively")
                .isFalse();
        assertThat(result.getViolations())
                .anySatisfy(v -> assertThat(v).contains("Forbidden pattern found"));
    }

    /**
     * Verifies multiple forbidden patterns checked.
     *
     * GIVEN: Multiple forbidden patterns
     * WHEN: Content contains one or more
     * THEN: All violations reported
     */
    @Test
    @DisplayName("Should report multiple forbidden pattern violations")
    void shouldReportMultipleForbiddenPatternViolations() {
        // GIVEN: Multiple forbidden patterns
        GenerationGuardrails guardrails = builder
                .forbiddenPatterns("violence", "drugs", "weapons")
                .build();

        // WHEN: Content contains multiple
        GenerationGuardrails.GuardrailValidationResult result =
                guardrails.validate("violence and weapons are concerning.");

        // THEN: Both violations reported
        assertThat(result.isValid()).isFalse();
        assertThat(result.getViolations())
                .as("Should report both violations")
                .hasSize(2);
    }

    /**
     * Verifies required patterns are enforced.
     *
     * GIVEN: Required pattern "disclaimer"
     * WHEN: Content lacks pattern
     * THEN: Validation fails
     */
    @Test
    @DisplayName("Should enforce required patterns")
    void shouldEnforceRequiredPatterns() {
        // GIVEN: Required pattern
        GenerationGuardrails guardrails = builder
                .requiredPattern("disclaimer")
                .build();

        // WHEN: Content lacks pattern
        GenerationGuardrails.GuardrailValidationResult result =
                guardrails.validate("This is regular content without the required text.");

        // THEN: Validation fails
        assertThat(result.isValid())
                .as("Should fail when required pattern missing")
                .isFalse();
        assertThat(result.getViolations())
                .anySatisfy(v -> assertThat(v).contains("Required pattern not found"));
    }

    /**
     * Verifies required patterns case-insensitive matching.
     *
     * GIVEN: Required pattern "disclaimer"
     * WHEN: Content contains "DISCLAIMER"
     * THEN: Validation passes
     */
    @Test
    @DisplayName("Should match required patterns case-insensitively")
    void shouldMatchRequiredPatternsCaseInsensitively() {
        // GIVEN: Required pattern
        GenerationGuardrails guardrails = builder
                .requiredPattern("disclaimer")
                .build();

        // WHEN: Content has uppercase version
        GenerationGuardrails.GuardrailValidationResult result =
                guardrails.validate("Content here. DISCLAIMER: This is example text.");

        // THEN: Valid (case-insensitive)
        assertThat(result.isValid())
                .as("Should match case-insensitively")
                .isTrue();
    }

    /**
     * Verifies HTML content rejection when not allowed.
     *
     * GIVEN: allowsHTML = false (default)
     * WHEN: Content contains HTML tags
     * THEN: Validation fails
     */
    @Test
    @DisplayName("Should reject HTML content when not allowed")
    void shouldRejectHTMLContentWhenNotAllowed() {
        // GIVEN: HTML not allowed
        GenerationGuardrails guardrails = builder
                .allowsHTML(false)
                .build();

        // WHEN: Validate HTML content
        GenerationGuardrails.GuardrailValidationResult result =
                guardrails.validate("Text with <strong>HTML</strong> tags.");

        // THEN: Invalid
        assertThat(result.isValid())
                .as("HTML should be rejected when not allowed")
                .isFalse();
        assertThat(result.getViolations())
                .anySatisfy(v -> assertThat(v).contains("HTML content not allowed"));
    }

    /**
     * Verifies HTML content acceptance when allowed.
     *
     * GIVEN: allowsHTML = true
     * WHEN: Content contains HTML
     * THEN: Validation passes
     */
    @Test
    @DisplayName("Should accept HTML content when allowed")
    void shouldAcceptHTMLContentWhenAllowed() {
        // GIVEN: HTML allowed
        GenerationGuardrails guardrails = builder
                .allowsHTML(true)
                .build();

        // WHEN: Validate HTML content
        GenerationGuardrails.GuardrailValidationResult result =
                guardrails.validate("Text with <strong>HTML</strong> tags.");

        // THEN: Valid
        assertThat(result.isValid())
                .as("HTML should be accepted when allowed")
                .isTrue();
    }

    /**
     * Verifies Markdown content rejection when not allowed.
     *
     * GIVEN: allowsMarkdown = false
     * WHEN: Content contains Markdown
     * THEN: Validation fails
     */
    @Test
    @DisplayName("Should reject Markdown content when not allowed")
    void shouldRejectMarkdownContentWhenNotAllowed() {
        // GIVEN: Markdown not allowed
        GenerationGuardrails guardrails = builder
                .allowsMarkdown(false)
                .build();

        // WHEN: Validate Markdown content
        GenerationGuardrails.GuardrailValidationResult result =
                guardrails.validate("Text with **bold** and _italic_ formatting.");

        // THEN: Invalid
        assertThat(result.isValid())
                .as("Markdown should be rejected when not allowed")
                .isFalse();
        assertThat(result.getViolations())
                .anySatisfy(v -> assertThat(v).contains("Markdown content not allowed"));
    }

    /**
     * Verifies exceedsLength() method.
     *
     * GIVEN: Content and length limit
     * WHEN: exceedsLength() called
     * THEN: Correct boolean returned
     */
    @Test
    @DisplayName("Should correctly identify if content exceeds length")
    void shouldCorrectlyIdentifyIfContentExceedsLength() {
        // GIVEN: Guardrails
        GenerationGuardrails guardrails = builder.build();

        // WHEN: Check various lengths
        // THEN: Correct results
        assertThat(guardrails.exceedsLength("short", 10))
                .as("Short text should not exceed limit")
                .isFalse();
        assertThat(guardrails.exceedsLength("x".repeat(11), 10))
                .as("Long text should exceed limit")
                .isTrue();
        assertThat(guardrails.exceedsLength("exactly10c", 10))
                .as("Content equal to limit should not exceed")
                .isFalse();
    }

    /**
     * Verifies invalid maxLength rejected during construction.
     *
     * GIVEN: maxLength > 50,000
     * WHEN: build() called
     * THEN: IllegalArgumentException thrown
     */
    @Test
    @DisplayName("Should reject maxLength exceeding 50000")
    void shouldRejectMaxLengthExceeding50000() {
        // WHEN/THEN: Build with invalid max length
        assertThatThrownBy(() -> builder.maxLength(50001).build())
                .as("Should reject maxLength > 50,000")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("50000");
    }

    /**
     * Verifies invalid minLength relative to maxLength rejected.
     *
     * GIVEN: minLength > maxLength
     * WHEN: build() called
     * THEN: IllegalArgumentException thrown
     */
    @Test
    @DisplayName("Should reject minLength greater than maxLength")
    void shouldRejectMinLengthGreaterThanMaxLength() {
        // WHEN/THEN: Build with invalid lengths
        assertThatThrownBy(() -> builder
                .minLength(200)
                .maxLength(100)
                .build())
                .as("Should reject minLength > maxLength")
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Verifies null content rejected during validation.
     *
     * GIVEN: Content is null
     * WHEN: validate() called
     * THEN: NullPointerException thrown
     */
    @Test
    @DisplayName("Should require non-null content for validation")
    void shouldRequireNonNullContentForValidation() {
        // GIVEN: Guardrails
        GenerationGuardrails guardrails = builder.build();

        // WHEN/THEN: Null content rejected
        assertThatThrownBy(() -> guardrails.validate(null))
                .as("Should reject null content")
                .isInstanceOf(NullPointerException.class);
    }

    /**
     * Verifies forbidden patterns immutable after construction.
     *
     * GIVEN: Guardrails built with patterns
     * WHEN: getForbiddenPatterns() returns set and caller modifies
     * THEN: Guardrails patterns unchanged
     */
    @Test
    @DisplayName("Should return immutable forbidden patterns")
    void shouldReturnImmutableForbiddenPatterns() {
        // GIVEN: Guardrails with patterns
        GenerationGuardrails guardrails = builder
                .forbiddenPattern("pattern1")
                .build();

        // WHEN: Get patterns and try to modify
        Set<String> patterns = guardrails.getForbiddenPatterns();

        // THEN: Modification fails
        assertThatThrownBy(() -> patterns.add("pattern2"))
                .as("Forbidden patterns should be unmodifiable")
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /**
     * Verifies multiple violations reported together.
     *
     * GIVEN: Content violates multiple guardrails
     * WHEN: validate() called
     * THEN: All violations reported
     */
    @Test
    @DisplayName("Should report multiple violations together")
    void shouldReportMultipleViolationsTogether() {
        // GIVEN: Guardrails with multiple constraints
        GenerationGuardrails guardrails = builder
                .minLength(50)
                .maxLength(100)
                .forbiddenPattern("forbidden")
                .requiredPattern("required")
                .build();

        // WHEN: Content violates multiple
        GenerationGuardrails.GuardrailValidationResult result =
                guardrails.validate("Too short. Has forbidden word.");

        // THEN: All violations reported
        assertThat(result.isValid()).isFalse();
        assertThat(result.getViolations())
                .as("Should report all violations")
                .hasSize(3)  // too short, has forbidden, missing required
                .anySatisfy(v -> assertThat(v).contains("minimum length"))
                .anySatisfy(v -> assertThat(v).contains("Forbidden"))
                .anySatisfy(v -> assertThat(v).contains("Required"));
    }

    /**
     * Verifies plain text without special chars passes all checks.
     *
     * GIVEN: Plain text content
     * WHEN: Default guardrails validate it
     * THEN: Validation passes
     */
    @Test
    @DisplayName("Should accept plain text content")
    void shouldAcceptPlainTextContent() {
        // GIVEN: Default guardrails
        GenerationGuardrails guardrails = builder.build();

        // WHEN: Validate plain text
        GenerationGuardrails.GuardrailValidationResult result =
                guardrails.validate("This is plain text content with sufficient length for validation purposes and guardrail checking requirements.");

        // THEN: Valid
        assertThat(result.isValid())
                .as("Plain text should pass validation")
                .isTrue();
        assertThat(result.getViolations())
                .as("Should have no violations")
                .isEmpty();
    }

    /**
     * Verifies allowed languages set is immutable.
     *
     * GIVEN: Guardrails with allowed languages
     * WHEN: getAllowedLanguages() returns set and caller modifies
     * THEN: Guardrails languages unchanged
     */
    @Test
    @DisplayName("Should return immutable allowed languages set")
    void shouldReturnImmutableAllowedLanguagesSet() {
        // GIVEN: Guardrails with languages
        GenerationGuardrails guardrails = builder
                .allowedLanguages(new HashSet<>(Arrays.asList("en", "es")))
                .build();

        // WHEN: Get languages and try to modify
        Set<String> languages = guardrails.getAllowedLanguages();

        // THEN: Modification fails
        assertThatThrownBy(() -> languages.add("fr"))
                .as("Allowed languages should be unmodifiable")
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /**
     * Verifies policy check requirement.
     *
     * GIVEN: Guardrails with policy check requirement
     * WHEN: isRequiresPolicyCheck() called
     * THEN: Returns configured value
     */
    @Test
    @DisplayName("Should track policy check requirement")
    void shouldTrackPolicyCheckRequirement() {
        // GIVEN: Policy check required
        GenerationGuardrails required = builder
                .requiresPolicyCheck(true)
                .build();

        // GIVEN: Policy check not required
        GenerationGuardrails notRequired = builder
                .requiresPolicyCheck(false)
                .build();

        // THEN: Correct values
        assertThat(required.isRequiresPolicyCheck()).isTrue();
        assertThat(notRequired.isRequiresPolicyCheck()).isFalse();
    }
}
