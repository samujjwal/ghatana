package com.ghatana.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for PiiRedactor utility class.
 *
 * @doc.type class
 * @doc.purpose Test PII redaction functionality
 * @doc.layer test
 * @doc.pattern Test
 */
@DisplayName("PII Redactor Tests")
class PiiRedactorTest {

    @Test
    @DisplayName("Should redact email addresses")
    void shouldRedactEmailAddresses() {
        String input = "Contact user@example.com for support";
        String result = PiiRedactor.redactEmails(input);
        assertThat(result).isEqualTo("Contact ***@***.*** for support");
    }

    @Test
    @DisplayName("Should redact multiple email addresses")
    void shouldRedactMultipleEmailAddresses() {
        String input = "Email john@example.com and jane@example.org";
        String result = PiiRedactor.redactEmails(input);
        assertThat(result).isEqualTo("Email ***@***.*** and ***@***.***");
    }

    @Test
    @DisplayName("Should redact phone numbers")
    void shouldRedactPhoneNumbers() {
        String input = "Call 555-123-4567 for support";
        String result = PiiRedactor.redactPhones(input);
        assertThat(result).isEqualTo("Call **** for support");
    }

    @Test
    @DisplayName("Should redact phone numbers in various formats")
    void shouldRedactPhoneNumbersInVariousFormats() {
        String input = "Phone: (555) 123-4567 or 555.123.4567";
        String result = PiiRedactor.redactPhones(input);
        assertThat(result).isEqualTo("Phone: **** or ****");
    }

    @Test
    @DisplayName("Should redact credit card numbers preserving last 4 digits")
    void shouldRedactCreditCardsPreservingLast4Digits() {
        String input = "Card: 4111-1111-1111-1111";
        String result = PiiRedactor.redactCreditCards(input);
        assertThat(result).isEqualTo("Card: ****-1111");
    }

    @Test
    @DisplayName("Should redact credit cards without separators")
    void shouldRedactCreditCardsWithoutSeparators() {
        String input = "Card 4111111111111111";
        String result = PiiRedactor.redactCreditCards(input);
        assertThat(result).isEqualTo("Card ****-1111");
    }

    @Test
    @DisplayName("Should redact SSN numbers")
    void shouldRedactSSNNumbers() {
        String input = "SSN: 123-45-6789";
        String result = PiiRedactor.redactSSNs(input);
        assertThat(result).isEqualTo("SSN: ****");
    }

    @Test
    @DisplayName("Should redact SSN without separators")
    void shouldRedactSSNWithoutSeparators() {
        String input = "SSN: 123456789";
        String result = PiiRedactor.redactSSNs(input);
        assertThat(result).isEqualTo("SSN: ****");
    }

    @Test
    @DisplayName("Should redact IP addresses")
    void shouldRedactIPAddresses() {
        String input = "From 192.168.1.1";
        String result = PiiRedactor.redactIPs(input);
        assertThat(result).isEqualTo("From ***.***.***.***");
    }

    @Test
    @DisplayName("Should redact API keys and passwords")
    void shouldRedactApiKeysAndPasswords() {
        String input = "api?key=secret123&token=abc456";
        String result = PiiRedactor.redactApiKeys(input);
        assertThat(result).isEqualTo("api?key=****&token=****");
    }

    @Test
    @DisplayName("Should redact passwords with equals sign")
    void shouldRedactPasswordsWithEqualsSign() {
        String input = "password=mySecret123";
        String result = PiiRedactor.redactApiKeys(input);
        assertThat(result).isEqualTo("password=****");
    }

    @Test
    @DisplayName("Should redact UUIDs")
    void shouldRedactUUIDs() {
        String input = "ID: 550e8400-e29b-41d4-a716-446655440000";
        String result = PiiRedactor.redactUUIDs(input);
        assertThat(result).isEqualTo("ID: ****");
    }

    @Test
    @DisplayName("Should redact all PII types with single call")
    void shouldRedactAllPIITypesWithSingleCall() {
        String input = "Contact john@example.com at 555-123-4567, card 4111111111111111, SSN 123-45-6789";
        String result = PiiRedactor.redact(input);
        assertThat(result).contains("***@***.***");
        assertThat(result).contains("****");
        assertThat(result).doesNotContain("john@example.com");
        assertThat(result).doesNotContain("555-123-4567");
        assertThat(result).doesNotContain("4111111111111111");
        assertThat(result).doesNotContain("123-45-6789");
    }

    @Test
    @DisplayName("Should return null when input is null")
    void shouldReturnNullWhenInputIsNull() {
        String result = PiiRedactor.redact(null);
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should return empty string when input is empty")
    void shouldReturnEmptyStringWhenInputIsEmpty() {
        String result = PiiRedactor.redact("");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should detect PII in string")
    void shouldDetectPIIInString() {
        assertThat(PiiRedactor.containsPii("john@example.com")).isTrue();
        assertThat(PiiRedactor.containsPii("555-123-4567")).isTrue();
        assertThat(PiiRedactor.containsPii("4111111111111111")).isTrue();
        assertThat(PiiRedactor.containsPii("123-45-6789")).isTrue();
        assertThat(PiiRedactor.containsPii("192.168.1.1")).isTrue();
        assertThat(PiiRedactor.containsPii("key=secret")).isTrue();
    }

    @Test
    @DisplayName("Should not detect PII in clean string")
    void shouldNotDetectPIIInCleanString() {
        assertThat(PiiRedactor.containsPii("Hello world")).isFalse();
        assertThat(PiiRedactor.containsPii("No PII here")).isFalse();
    }

    @Test
    @DisplayName("Should return false when checking null for PII")
    void shouldReturnFalseWhenCheckingNullForPII() {
        assertThat(PiiRedactor.containsPii(null)).isFalse();
    }
}
