package com.ghatana.digitalmarketing.domain.contact;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

@DisplayName("Contact domain entity")
class ContactTest {

    private Contact validContact() {
        Instant now = Instant.now();
        return Contact.builder()
            .id("contact-1")
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .email("alice@example.com")
            .displayName("Alice")
            .consentStatus(ConsentStatus.UNKNOWN)
            .consentPurpose("")
            .suppressed(false)
            .createdAt(now)
            .updatedAt(now)
            .createdBy("user-1")
            .build();
    }

    @Test
    @DisplayName("builder rejects blank id and blank email")
    void shouldRejectBlankIdentityFields() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> Contact.builder()
                .id(" ")
                .workspaceId(DmWorkspaceId.of("ws-1"))
                .email("a@example.com")
                .consentStatus(ConsentStatus.UNKNOWN)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .createdBy("user-1")
                .build());

        assertThatIllegalArgumentException()
            .isThrownBy(() -> Contact.builder()
                .id("contact-1")
                .workspaceId(DmWorkspaceId.of("ws-1"))
                .email(" ")
                .consentStatus(ConsentStatus.UNKNOWN)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .createdBy("user-1")
                .build());
    }

    @Test
    @DisplayName("grant consent makes contact marketing eligible")
    void shouldGrantConsentAndBecomeEligible() {
        Contact granted = validContact().grantConsent("marketing-email", Instant.now());

        assertThat(granted.getConsentStatus()).isEqualTo(ConsentStatus.GRANTED);
        assertThat(granted.getConsentPurpose()).isEqualTo("marketing-email");
        assertThat(granted.isMarketingEligible()).isTrue();
    }

    @Test
    @DisplayName("withdraw consent and suppression block marketing eligibility")
    void shouldWithdrawAndSuppress() {
        Contact withdrawn = validContact().grantConsent("marketing-email", Instant.now()).withdrawConsent();
        Contact suppressed = validContact().suppress();

        assertThat(withdrawn.getConsentStatus()).isEqualTo(ConsentStatus.WITHDRAWN);
        assertThat(withdrawn.isSuppressed()).isTrue();
        assertThat(withdrawn.isMarketingEligible()).isFalse();

        assertThat(suppressed.isSuppressed()).isTrue();
        assertThat(suppressed.isMarketingEligible()).isFalse();
    }

    @Test
    @DisplayName("exposes fields and supports toBuilder roundtrip")
    void shouldExposeFieldsAndRoundTrip() {
        Contact c = validContact();
        Contact copy = c.toBuilder().build();

        assertThat(c.getId()).isEqualTo("contact-1");
        assertThat(c.getWorkspaceId()).isEqualTo(DmWorkspaceId.of("ws-1"));
        assertThat(c.getEmail()).isEqualTo("alice@example.com");
        assertThat(c.getDisplayName()).isEqualTo("Alice");
        assertThat(c.getConsentStatus()).isEqualTo(ConsentStatus.UNKNOWN);
        assertThat(c.getConsentPurpose()).isEmpty();
        assertThat(c.getConsentRecordedAt()).isNull();
        assertThat(c.getCreatedAt()).isNotNull();
        assertThat(c.getUpdatedAt()).isNotNull();
        assertThat(c.getCreatedBy()).isEqualTo("user-1");
        assertThat(c).isEqualTo(copy);
        assertThat(c.hashCode()).isEqualTo(copy.hashCode());
        assertThat(c.toString()).contains("contact-1");
        // self-reference
        assertThat(c).isEqualTo(c);
        // null and wrong type
        assertThat(c).isNotEqualTo(null);
        assertThat(c).isNotEqualTo("string");
    }
}
