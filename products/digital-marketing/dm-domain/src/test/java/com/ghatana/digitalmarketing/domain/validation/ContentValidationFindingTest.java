package com.ghatana.digitalmarketing.domain.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ContentValidationFinding domain tests")
class ContentValidationFindingTest {

    @Test
    @DisplayName("constructs successfully with all fields")
    void constructsSuccessfully() {
        ContentValidationFinding finding = new ContentValidationFinding(
            ValidationSeverity.WARN,
            "RULE_CODE",
            "block-1",
            "reason text",
            "required action",
            "REVIEWER");

        assertThat(finding.severity()).isEqualTo(ValidationSeverity.WARN);
        assertThat(finding.ruleCode()).isEqualTo("RULE_CODE");
        assertThat(finding.affectedBlockId()).isEqualTo("block-1");
        assertThat(finding.reason()).isEqualTo("reason text");
        assertThat(finding.requiredAction()).isEqualTo("required action");
        assertThat(finding.approverRole()).isEqualTo("REVIEWER");
    }

    @Test
    @DisplayName("affectedBlockId and approverRole may be null")
    void optionalFieldsCanBeNull() {
        ContentValidationFinding finding = new ContentValidationFinding(
            ValidationSeverity.INFO,
            "RULE",
            null,
            "reason",
            "action",
            null);

        assertThat(finding.affectedBlockId()).isNull();
        assertThat(finding.approverRole()).isNull();
    }

    @Test
    @DisplayName("rejects null severity")
    void rejectsNullSeverity() {
        assertThatThrownBy(() -> new ContentValidationFinding(null, "CODE", null, "r", "a", null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("rejects blank ruleCode")
    void rejectsBlankRuleCode() {
        assertThatThrownBy(() -> new ContentValidationFinding(
            ValidationSeverity.WARN, " ", null, "r", "a", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ruleCode");
    }

    @Test
    @DisplayName("rejects blank reason")
    void rejectsBlankReason() {
        assertThatThrownBy(() -> new ContentValidationFinding(
            ValidationSeverity.WARN, "CODE", null, "  ", "a", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("reason");
    }

    @Test
    @DisplayName("rejects blank requiredAction")
    void rejectsBlankRequiredAction() {
        assertThatThrownBy(() -> new ContentValidationFinding(
            ValidationSeverity.WARN, "CODE", null, "r", " ", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("requiredAction");
    }
}
