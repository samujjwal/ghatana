package com.ghatana.digitalmarketing.domain.content;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GoogleAdCopySection")
class GoogleAdCopySectionTest {

    @Test
    @DisplayName("All required ad copy sections are present")
    void shouldContainAllRequiredSections() {
        assertThat(GoogleAdCopySection.values()).contains(
            GoogleAdCopySection.HEADLINES,
            GoogleAdCopySection.DESCRIPTIONS,
            GoogleAdCopySection.KEYWORD_THEMES,
            GoogleAdCopySection.NEGATIVE_KEYWORDS,
            GoogleAdCopySection.CALL_TO_ACTION,
            GoogleAdCopySection.COMPLIANCE_NOTES
        );
    }

    @Test
    @DisplayName("Enum has exactly 6 sections")
    void shouldHaveExactlySixSections() {
        assertThat(GoogleAdCopySection.values()).hasSize(6);
    }

    @Test
    @DisplayName("valueOf returns correct section by name")
    void shouldResolveByName() {
        assertThat(GoogleAdCopySection.valueOf("HEADLINES")).isEqualTo(GoogleAdCopySection.HEADLINES);
        assertThat(GoogleAdCopySection.valueOf("COMPLIANCE_NOTES")).isEqualTo(GoogleAdCopySection.COMPLIANCE_NOTES);
    }
}
