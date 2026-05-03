package com.ghatana.digitalmarketing.domain.privacy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for ContactPoint PII-safe model (DMOS-P1-014).
 *
 * @doc.type test
 * @doc.purpose Verify contact point normalization, hashing, and suppression matching
 * @doc.layer domain
 */
@DisplayName("ContactPoint")
class ContactPointTest {

    @Test
    @DisplayName("fromEmail normalizes email to lowercase and trims")
    void fromEmail_normalizesEmail() {
        ContactPoint contact = ContactPoint.fromEmail("  Test@Example.COM  ");
        assertThat(contact.normalizedValue()).isEqualTo("test@example.com");
        assertThat(contact.type()).isEqualTo(ContactPointType.EMAIL);
    }

    @Test
    @DisplayName("fromEmail throws on blank email")
    void fromEmail_throwsOnBlankEmail() {
        assertThatThrownBy(() -> ContactPoint.fromEmail(""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Email must not be blank");
    }

    @Test
    @DisplayName("fromEmail throws on null email")
    void fromEmail_throwsOnNullEmail() {
        assertThatThrownBy(() -> ContactPoint.fromEmail(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Email must not be blank");
    }

    @Test
    @DisplayName("fromEmail produces consistent hash for same email")
    void fromEmail_producesConsistentHash() {
        ContactPoint contact1 = ContactPoint.fromEmail("test@example.com");
        ContactPoint contact2 = ContactPoint.fromEmail("test@example.com");
        assertThat(contact1.contactPointHash()).isEqualTo(contact2.contactPointHash());
    }

    @Test
    @DisplayName("fromEmail produces different hashes for different emails")
    void fromEmail_producesDifferentHashes() {
        ContactPoint contact1 = ContactPoint.fromEmail("test@example.com");
        ContactPoint contact2 = ContactPoint.fromEmail("other@example.com");
        assertThat(contact1.contactPointHash()).isNotEqualTo(contact2.contactPointHash());
    }

    @Test
    @DisplayName("fromPhone normalizes phone to digits only")
    void fromPhone_normalizesPhone() {
        ContactPoint contact = ContactPoint.fromPhone("+1 (555) 123-4567");
        assertThat(contact.normalizedValue()).isEqualTo("+15551234567");
        assertThat(contact.type()).isEqualTo(ContactPointType.PHONE);
    }

    @Test
    @DisplayName("fromPhone throws on blank phone")
    void fromPhone_throwsOnBlankPhone() {
        assertThatThrownBy(() -> ContactPoint.fromPhone(""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Phone must not be blank");
    }

    @Test
    @DisplayName("isSuppressed returns true when hash matches")
    void isSuppressed_returnsTrueWhenHashMatches() {
        ContactPoint contact = ContactPoint.fromEmail("test@example.com");
        assertThat(contact.isSuppressed(contact.contactPointHash())).isTrue();
    }

    @Test
    @DisplayName("isSuppressed returns false when hash does not match")
    void isSuppressed_returnsFalseWhenHashDoesNotMatch() {
        ContactPoint contact = ContactPoint.fromEmail("test@example.com");
        assertThat(contact.isSuppressed("some-other-hash")).isFalse();
    }
}
