package com.ghatana.digitalmarketing.domain.identity;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

@DisplayName("ContactIdentityProfile")
class ContactIdentityProfileTest {

    @Test
    @DisplayName("builds identity profile with attributes")
    void shouldBuildIdentityProfile() {
        Instant now = Instant.now();
        ContactIdentityProfile profile = ContactIdentityProfile.builder()
            .contactId("contact-1")
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .phoneNumber("+15551230000")
            .preferredLocale("en-US")
            .externalIdentityId("crm-991")
            .attributes(Map.of("source", "form"))
            .updatedAt(now)
            .updatedBy("user-1")
            .build();

        assertThat(profile.getContactId()).isEqualTo("contact-1");
        assertThat(profile.getWorkspaceId()).isEqualTo(DmWorkspaceId.of("ws-1"));
        assertThat(profile.getPhoneNumber()).isEqualTo("+15551230000");
        assertThat(profile.getPreferredLocale()).isEqualTo("en-US");
        assertThat(profile.getExternalIdentityId()).isEqualTo("crm-991");
        assertThat(profile.getAttributes()).containsEntry("source", "form");
        assertThat(profile.getUpdatedAt()).isEqualTo(now);
        assertThat(profile.getUpdatedBy()).isEqualTo("user-1");
    }

    @Test
    @DisplayName("rejects blank contact id")
    void shouldRejectBlankContactId() {
        assertThatIllegalArgumentException().isThrownBy(() -> ContactIdentityProfile.builder()
            .contactId(" ")
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .updatedAt(Instant.now())
            .updatedBy("user-1")
            .build());

        assertThatCode(() -> ContactIdentityProfile.builder()
            .contactId("contact-1")
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .phoneNumber("+1555")
            .preferredLocale("en-US")
            .externalIdentityId("crm-1")
            .attributes(Map.of())
            .updatedAt(Instant.now())
            .updatedBy("user-1")
            .build()
            .toString())
            .doesNotThrowAnyException();
    }
}
