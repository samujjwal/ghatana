package com.ghatana.digitalmarketing.domain.lead;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("Lead domain entity")
class LeadTest {

    private Lead newLead() {
        return Lead.builder()
            .id("lead-1")
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .campaignId("camp-1")
            .email("alice@example.com")
            .firstName("Alice")
            .lastName("Smith")
            .source("landing-page")
            .status(LeadStatus.NEW)
            .capturedAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    }

    @Test
    @DisplayName("rejects null required fields")
    void shouldRejectNulls() {
        assertThatNullPointerException()
            .isThrownBy(() -> Lead.builder()
                .workspaceId(DmWorkspaceId.of("ws-1"))
                .campaignId("camp-1")
                .email("x@x.com")
                .status(LeadStatus.NEW)
                .capturedAt(Instant.now())
                .updatedAt(Instant.now())
                .build());
    }

    @Test
    @DisplayName("rejects blank email")
    void shouldRejectBlankEmail() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> Lead.builder()
                .id("l-1")
                .workspaceId(DmWorkspaceId.of("ws-1"))
                .campaignId("camp-1")
                .email("")
                .status(LeadStatus.NEW)
                .capturedAt(Instant.now())
                .updatedAt(Instant.now())
                .build());
    }

    @Test
    @DisplayName("qualify transitions NEW to QUALIFIED")
    void shouldQualify() {
        Lead qualified = newLead().qualify();
        assertThat(qualified.getStatus()).isEqualTo(LeadStatus.QUALIFIED);
    }

    @Test
    @DisplayName("qualify throws when not NEW")
    void shouldRejectQualifyFromNonNew() {
        Lead qualified = newLead().qualify();
        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(qualified::qualify);
    }

    @Test
    @DisplayName("convert transitions QUALIFIED to CONVERTED")
    void shouldConvert() {
        Lead converted = newLead().qualify().convert();
        assertThat(converted.getStatus()).isEqualTo(LeadStatus.CONVERTED);
    }

    @Test
    @DisplayName("convert throws when not QUALIFIED")
    void shouldRejectConvertFromNew() {
        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(() -> newLead().convert());
    }

    @Test
    @DisplayName("disqualify transitions NEW to DISQUALIFIED")
    void shouldDisqualifyFromNew() {
        Lead disqualified = newLead().disqualify();
        assertThat(disqualified.getStatus()).isEqualTo(LeadStatus.DISQUALIFIED);
    }

    @Test
    @DisplayName("disqualify transitions QUALIFIED to DISQUALIFIED")
    void shouldDisqualifyFromQualified() {
        Lead disqualified = newLead().qualify().disqualify();
        assertThat(disqualified.getStatus()).isEqualTo(LeadStatus.DISQUALIFIED);
    }

    @Test
    @DisplayName("disqualify throws when CONVERTED or already DISQUALIFIED")
    void shouldRejectDisqualifyFromTerminalStates() {
        Lead converted = newLead().qualify().convert();
        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(converted::disqualify);

        Lead disqualified = newLead().disqualify();
        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(disqualified::disqualify);
    }

    @Test
    @DisplayName("equals based on id and workspaceId")
    void shouldUseIdAndWorkspaceForEquality() {
        Lead a = newLead();
        Lead b = newLead();
        // same id: equal
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        // self-reference
        assertThat(a).isEqualTo(a);
        // null and wrong type
        assertThat(a).isNotEqualTo(null);
        assertThat(a).isNotEqualTo("string");
    }
}
