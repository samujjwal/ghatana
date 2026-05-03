package com.ghatana.digitalmarketing.domain.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ContentValidationResult domain tests")
class ContentValidationResultTest {

    private static final String VERSION_ID = "ver-test-1";
    private static final String VALIDATED_BY = "user-alice";

    private static ContentValidationFinding failFinding() {
        return new ContentValidationFinding(
            ValidationSeverity.FAIL, "RULE_X", "block-1", "reason", "action", "REVIEWER");
    }

    private static ContentValidationFinding warnFinding() {
        return new ContentValidationFinding(
            ValidationSeverity.WARN, "RULE_Y", null, "reason", "action", "REVIEWER");
    }

    @Test
    @DisplayName("PASS outcome when no findings")
    void passOutcomeWhenNoFindings() {
        ContentValidationResult result = new ContentValidationResult(
            VERSION_ID,
            ContentValidationResult.ValidationOutcome.PASS,
            List.of(),
            Instant.now(),
            VALIDATED_BY);

        assertThat(result.isPassed()).isTrue();
        assertThat(result.hasFails()).isFalse();
        assertThat(result.hasWarnings()).isFalse();
    }

    @Test
    @DisplayName("FAIL outcome when FAIL finding present")
    void failOutcomeDetected() {
        ContentValidationResult result = new ContentValidationResult(
            VERSION_ID,
            ContentValidationResult.ValidationOutcome.FAIL,
            List.of(failFinding()),
            Instant.now(),
            VALIDATED_BY);

        assertThat(result.isPassed()).isFalse();
        assertThat(result.hasFails()).isTrue();
        assertThat(result.hasWarnings()).isTrue();
    }

    @Test
    @DisplayName("WARN outcome when only WARN finding present")
    void warnOutcomeDetected() {
        ContentValidationResult result = new ContentValidationResult(
            VERSION_ID,
            ContentValidationResult.ValidationOutcome.WARN,
            List.of(warnFinding()),
            Instant.now(),
            VALIDATED_BY);

        assertThat(result.isPassed()).isTrue();
        assertThat(result.hasFails()).isFalse();
        assertThat(result.hasWarnings()).isTrue();
    }

    @Test
    @DisplayName("findings list is immutable after construction")
    void findingsImmutable() {
        List<ContentValidationFinding> mutable = new java.util.ArrayList<>();
        mutable.add(warnFinding());
        ContentValidationResult result = new ContentValidationResult(
            VERSION_ID,
            ContentValidationResult.ValidationOutcome.WARN,
            mutable,
            Instant.now(),
            VALIDATED_BY);

        assertThatThrownBy(() -> result.findings().add(warnFinding()))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("rejects null versionId")
    void rejectsNullVersionId() {
        assertThatThrownBy(() -> new ContentValidationResult(
            null,
            ContentValidationResult.ValidationOutcome.PASS,
            List.of(),
            Instant.now(),
            VALIDATED_BY))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("rejects blank validatedBy")
    void rejectsBlankValidatedBy() {
        assertThatThrownBy(() -> new ContentValidationResult(
            VERSION_ID,
            ContentValidationResult.ValidationOutcome.PASS,
            List.of(),
            Instant.now(),
            "   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("validatedBy");
    }
}
