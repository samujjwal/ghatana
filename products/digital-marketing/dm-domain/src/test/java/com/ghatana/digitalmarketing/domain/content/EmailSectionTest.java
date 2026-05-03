package com.ghatana.digitalmarketing.domain.content;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EmailSection")
class EmailSectionTest {

    @Test
    @DisplayName("has all expected values")
    void shouldHaveAllExpectedValues() {
        assertThat(EmailSection.values())
            .containsExactlyInAnyOrder(
                EmailSection.SUBJECT_LINE,
                EmailSection.GREETING,
                EmailSection.BODY_COPY,
                EmailSection.CALL_TO_ACTION,
                EmailSection.UNSUBSCRIBE_NOTICE,
                EmailSection.COMPLIANCE_NOTES
            );
    }

    @Test
    @DisplayName("has exactly 6 values")
    void shouldHaveExactlySixValues() {
        assertThat(EmailSection.values()).hasSize(6);
    }

    @Test
    @DisplayName("valueOf resolves all section names")
    void shouldResolveByName() {
        assertThat(EmailSection.valueOf("SUBJECT_LINE")).isEqualTo(EmailSection.SUBJECT_LINE);
        assertThat(EmailSection.valueOf("GREETING")).isEqualTo(EmailSection.GREETING);
        assertThat(EmailSection.valueOf("BODY_COPY")).isEqualTo(EmailSection.BODY_COPY);
        assertThat(EmailSection.valueOf("CALL_TO_ACTION")).isEqualTo(EmailSection.CALL_TO_ACTION);
        assertThat(EmailSection.valueOf("UNSUBSCRIBE_NOTICE")).isEqualTo(EmailSection.UNSUBSCRIBE_NOTICE);
        assertThat(EmailSection.valueOf("COMPLIANCE_NOTES")).isEqualTo(EmailSection.COMPLIANCE_NOTES);
    }
}
