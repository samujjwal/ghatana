package com.ghatana.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive PII redaction validation tests.
 * Tests edge cases, performance, and complex scenarios for PII detection and redaction.
 *
 * @doc.type test
 * @doc.purpose Comprehensive PII redaction validation
 * @doc.layer core
 * @doc.pattern Test
 */
@DisplayName("Comprehensive PII Redaction Validation Tests")
class PiiRedactorValidationTest {

    // =========================================================================
    // EDGE CASES
    // =========================================================================

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("should handle mixed case email")
        void shouldHandleMixedCaseEmail() { // GH-90000
            String input = "Contact John.Doe@Example.COM for support";
            String result = PiiRedactor.redactEmails(input); // GH-90000
            assertThat(result).isEqualTo("Contact ***@***.*** for support");
        }

        @Test
        @DisplayName("should handle email with subdomains")
        void shouldHandleEmailWithSubdomains() { // GH-90000
            String input = "user@mail.subdomain.example.com";
            String result = PiiRedactor.redactEmails(input); // GH-90000
            assertThat(result).isEqualTo("***@***.***");
        }

        @Test
        @DisplayName("should handle email with plus addressing")
        void shouldHandleEmailWithPlusAddressing() { // GH-90000
            String input = "user+tag@example.com";
            String result = PiiRedactor.redactEmails(input); // GH-90000
            assertThat(result).isEqualTo("***@***.***");
        }

        @Test
        @DisplayName("should handle international phone numbers")
        void shouldHandleInternationalPhoneNumbers() { // GH-90000
            String input = "Call +1-555-123-4567 or +44 20 7946 0958";
            String result = PiiRedactor.redactPhones(input); // GH-90000
            assertThat(result).contains("****");
        }

        @Test
        @DisplayName("should handle credit cards with varying spacing")
        void shouldHandleCreditCardsWithVaryingSpacing() { // GH-90000
            String input = "Card: 4111 1111 1111 1111 or 4111-1111-1111-1111";
            String result = PiiRedactor.redactCreditCards(input); // GH-90000
            assertThat(result).contains("****-1111");
        }

        @Test
        @DisplayName("should handle SSN with dots instead of dashes")
        void shouldHandleSSNWithDots() { // GH-90000
            String input = "SSN: 123.45.6789";
            String result = PiiRedactor.redactSSNs(input); // GH-90000
            assertThat(result).isEqualTo("SSN: ****");
        }

        @Test
        @DisplayName("should handle IP address in URL")
        void shouldHandleIPInURL() { // GH-90000
            String input = "http://192.168.1.1:8080/api";
            String result = PiiRedactor.redactIPs(input); // GH-90000
            assertThat(result).contains("***.***.***.***");
        }

        @Test
        @DisplayName("should handle API key in JSON")
        void shouldHandleAPIKeyInJSON() { // GH-90000
            String input = "{\"apiKey\": \"secret123\", \"token\": \"abc456\"}";
            String result = PiiRedactor.redactApiKeys(input); // GH-90000
            assertThat(result).contains("apiKey=****");
            assertThat(result).contains("token=****");
        }

        @Test
        @DisplayName("should handle Bearer token")
        void shouldHandleBearerToken() { // GH-90000
            String input = "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9";
            String result = PiiRedactor.redactApiKeys(input); // GH-90000
            assertThat(result).contains("token=****");
        }

        @Test
        @DisplayName("should handle password in URL")
        void shouldHandlePasswordInURL() { // GH-90000
            String input = "jdbc:postgresql://user:password@localhost:5432/db";
            String result = PiiRedactor.redactApiKeys(input); // GH-90000
            assertThat(result).contains("password=****");
        }
    }

    // =========================================================================
    // COMPLEX SCENARIOS
    // =========================================================================

    @Nested
    @DisplayName("Complex scenarios")
    class ComplexScenarios {

        @Test
        @DisplayName("should redact multiple PII types in single string")
        void shouldRedactMultiplePIIInSingleString() { // GH-90000
            String input = "User john@example.com called 555-123-4567 from IP 192.168.1.1 with card 4111111111111111";
            String result = PiiRedactor.redact(input); // GH-90000
            assertThat(result).doesNotContain("john@example.com");
            assertThat(result).doesNotContain("555-123-4567");
            assertThat(result).doesNotContain("192.168.1.1");
            assertThat(result).doesNotContain("4111111111111111");
        }

        @Test
        @DisplayName("should preserve non-PII content")
        void shouldPreserveNonPIIContent() { // GH-90000
            String input = "Contact support at support@example.com or call 555-HELP-NOW";
            String result = PiiRedactor.redact(input); // GH-90000
            assertThat(result).contains("support");
            assertThat(result).contains("HELP-NOW");
        }

        @Test
        @DisplayName("should handle overlapping patterns")
        void shouldHandleOverlappingPatterns() { // GH-90000
            String input = "Card number 4111111111111111 is not a phone 555-123-4567";
            String result = PiiRedactor.redact(input); // GH-90000
            assertThat(result).contains("****-1111");
            assertThat(result).contains("****");
        }

        @Test
        @DisplayName("should handle empty values")
        void shouldHandleEmptyValues() { // GH-90000
            assertThat(PiiRedactor.redact("")).isEmpty();
            assertThat(PiiRedactor.redactEmails("")).isEmpty();
            assertThat(PiiRedactor.redactPhones("")).isEmpty();
        }

        @Test
        @DisplayName("should handle whitespace only")
        void shouldHandleWhitespaceOnly() { // GH-90000
            String input = "   ";
            String result = PiiRedactor.redact(input); // GH-90000
            assertThat(result).isEqualTo("   ");
        }

        @Test
        @DisplayName("should handle very long strings")
        void shouldHandleVeryLongStrings() { // GH-90000
            StringBuilder sb = new StringBuilder(); // GH-90000
            for (int i = 0; i < 1000; i++) { // GH-90000
                sb.append("Contact user@example.com ");
            }
            String input = sb.toString(); // GH-90000
            String result = PiiRedactor.redact(input); // GH-90000
            assertThat(result).doesNotContain("user@example.com");
            assertThat(result).contains("***@***.***");
        }
    }

    // =========================================================================
    // DETECTION VALIDATION
    // =========================================================================

    @Nested
    @DisplayName("PII detection validation")
    class PIIDetectionValidation {

        @Test
        @DisplayName("should detect email in various contexts")
        void shouldDetectEmailInVariousContexts() { // GH-90000
            assertThat(PiiRedactor.containsPii("Email: user@example.com")).isTrue();
            assertThat(PiiRedactor.containsPii("Send to user@example.com")).isTrue();
            assertThat(PiiRedactor.containsPii("user@example.com is the contact")).isTrue();
        }

        @Test
        @DisplayName("should detect phone in various formats")
        void shouldDetectPhoneInVariousFormats() { // GH-90000
            assertThat(PiiRedactor.containsPii("555-123-4567")).isTrue();
            assertThat(PiiRedactor.containsPii("(555) 123-4567")).isTrue();
            assertThat(PiiRedactor.containsPii("555.123.4567")).isTrue();
            assertThat(PiiRedactor.containsPii("5551234567")).isFalse(); // No separator
        }

        @Test
        @DisplayName("should detect credit card numbers")
        void shouldDetectCreditCardNumbers() { // GH-90000
            assertThat(PiiRedactor.containsPii("4111111111111111")).isTrue();
            assertThat(PiiRedactor.containsPii("4111-1111-1111-1111")).isTrue();
            assertThat(PiiRedactor.containsPii("4111 1111 1111 1111")).isTrue();
        }

        @Test
        @DisplayName("should detect SSN patterns")
        void shouldDetectSSNPatterns() { // GH-90000
            assertThat(PiiRedactor.containsPii("123-45-6789")).isTrue();
            assertThat(PiiRedactor.containsPii("123.45.6789")).isTrue();
            assertThat(PiiRedactor.containsPii("123456789")).isTrue();
        }

        @Test
        @DisplayName("should detect IP addresses")
        void shouldDetectIPAddresses() { // GH-90000
            assertThat(PiiRedactor.containsPii("192.168.1.1")).isTrue();
            assertThat(PiiRedactor.containsPii("10.0.0.1")).isTrue();
            assertThat(PiiRedactor.containsPii("172.16.0.1")).isTrue();
        }

        @Test
        @DisplayName("should detect API keys")
        void shouldDetectAPIKeys() { // GH-90000
            assertThat(PiiRedactor.containsPii("key=secret")).isTrue();
            assertThat(PiiRedactor.containsPii("token=abc123")).isTrue();
            assertThat(PiiRedactor.containsPii("password=mypass")).isTrue();
            assertThat(PiiRedactor.containsPii("api_key=xyz")).isTrue();
        }

        @Test
        @DisplayName("should detect UUIDs")
        void shouldDetectUUIDs() { // GH-90000
            assertThat(PiiRedactor.containsPii("550e8400-e29b-41d4-a716-446655440000")).isTrue();
            assertThat(PiiRedactor.containsPii("00000000-0000-0000-0000-000000000000")).isTrue();
        }

        @Test
        @DisplayName("should not detect false positives")
        void shouldNotDetectFalsePositives() { // GH-90000
            assertThat(PiiRedactor.containsPii("12345")).isFalse(); // Too short for CC
            assertThat(PiiRedactor.containsPii("user@")).isFalse(); // Incomplete email
            assertThat(PiiRedactor.containsPii("example.com")).isFalse(); // Domain only
            assertThat(PiiRedactor.containsPii("192.168")).isFalse(); // Incomplete IP
        }
    }

    // =========================================================================
    // SPECIFIC REDACTION METHODS
    // =========================================================================

    @Nested
    @DisplayName("Specific redaction methods")
    class SpecificRedactionMethods {

        @Test
        @DisplayName("should only redact emails when using specific method")
        void shouldOnlyRedactEmailsWithSpecificMethod() { // GH-90000
            String input = "Contact user@example.com at 555-123-4567";
            String result = PiiRedactor.redactEmails(input); // GH-90000
            assertThat(result).contains("***@***.***");
            assertThat(result).contains("555-123-4567"); // Phone not redacted
        }

        @Test
        @DisplayName("should only redact phones when using specific method")
        void shouldOnlyRedactPhonesWithSpecificMethod() { // GH-90000
            String input = "Contact user@example.com at 555-123-4567";
            String result = PiiRedactor.redactPhones(input); // GH-90000
            assertThat(result).contains("user@example.com"); // Email not redacted
            assertThat(result).contains("****");
        }

        @Test
        @DisplayName("should only redact credit cards when using specific method")
        void shouldOnlyRedactCreditCardsWithSpecificMethod() { // GH-90000
            String input = "Card 4111111111111111 email user@example.com";
            String result = PiiRedactor.redactCreditCards(input); // GH-90000
            assertThat(result).contains("****-1111");
            assertThat(result).contains("user@example.com"); // Email not redacted
        }

        @Test
        @DisplayName("should only redact SSNs when using specific method")
        void shouldOnlyRedactSSNsWithSpecificMethod() { // GH-90000
            String input = "SSN 123-45-6789 phone 555-123-4567";
            String result = PiiRedactor.redactSSNs(input); // GH-90000
            assertThat(result).contains("****");
            assertThat(result).contains("555-123-4567"); // Phone not redacted
        }

        @Test
        @DisplayName("should only redact IPs when using specific method")
        void shouldOnlyRedactIPsWithSpecificMethod() { // GH-90000
            String input = "From 192.168.1.1 email user@example.com";
            String result = PiiRedactor.redactIPs(input); // GH-90000
            assertThat(result).contains("***.***.***.***");
            assertThat(result).contains("user@example.com"); // Email not redacted
        }

        @Test
        @DisplayName("should only redact API keys when using specific method")
        void shouldOnlyRedactAPIKeysWithSpecificMethod() { // GH-90000
            String input = "key=secret email user@example.com";
            String result = PiiRedactor.redactApiKeys(input); // GH-90000
            assertThat(result).contains("key=****");
            assertThat(result).contains("user@example.com"); // Email not redacted
        }

        @Test
        @DisplayName("should only redact UUIDs when using specific method")
        void shouldOnlyRedactUUIDsWithSpecificMethod() { // GH-90000
            String input = "ID 550e8400-e29b-41d4-a716-446655440000 email user@example.com";
            String result = PiiRedactor.redactUUIDs(input); // GH-90000
            assertThat(result).contains("****");
            assertThat(result).contains("user@example.com"); // Email not redacted
        }
    }

    // =========================================================================
    // NULL AND EMPTY HANDLING
    // =========================================================================

    @Nested
    @DisplayName("Null and empty handling")
    class NullAndEmptyHandling {

        @Test
        @DisplayName("should handle null for all methods")
        void shouldHandleNullForAllMethods() { // GH-90000
            assertThat(PiiRedactor.redact(null)).isNull(); // GH-90000
            assertThat(PiiRedactor.redactEmails(null)).isNull(); // GH-90000
            assertThat(PiiRedactor.redactPhones(null)).isNull(); // GH-90000
            assertThat(PiiRedactor.redactCreditCards(null)).isNull(); // GH-90000
            assertThat(PiiRedactor.redactSSNs(null)).isNull(); // GH-90000
            assertThat(PiiRedactor.redactIPs(null)).isNull(); // GH-90000
            assertThat(PiiRedactor.redactApiKeys(null)).isNull(); // GH-90000
            assertThat(PiiRedactor.redactUUIDs(null)).isNull(); // GH-90000
        }

        @Test
        @DisplayName("should handle empty string for all methods")
        void shouldHandleEmptyStringForAllMethods() { // GH-90000
            assertThat(PiiRedactor.redact("")).isEmpty();
            assertThat(PiiRedactor.redactEmails("")).isEmpty();
            assertThat(PiiRedactor.redactPhones("")).isEmpty();
            assertThat(PiiRedactor.redactCreditCards("")).isEmpty();
            assertThat(PiiRedactor.redactSSNs("")).isEmpty();
            assertThat(PiiRedactor.redactIPs("")).isEmpty();
            assertThat(PiiRedactor.redactApiKeys("")).isEmpty();
            assertThat(PiiRedactor.redactUUIDs("")).isEmpty();
        }

        @Test
        @DisplayName("should handle string without PII")
        void shouldHandleStringWithoutPII() { // GH-90000
            String input = "Hello world, this is a test string";
            String result = PiiRedactor.redact(input); // GH-90000
            assertThat(result).isEqualTo(input); // GH-90000
        }
    }

    // =========================================================================
    // SPECIAL CHARACTERS AND UNICODE
    // =========================================================================

    @Nested
    @DisplayName("Special characters and Unicode")
    class SpecialCharactersAndUnicode {

        @Test
        @DisplayName("should handle emails with special characters in local part")
        void shouldHandleEmailsWithSpecialChars() { // GH-90000
            String input = "user.name+tag@example.com";
            String result = PiiRedactor.redactEmails(input); // GH-90000
            assertThat(result).isEqualTo("***@***.***");
        }

        @Test
        @DisplayName("should handle strings with newlines")
        void shouldHandleStringsWithNewlines() { // GH-90000
            String input = "Contact user@example.com\nfor support";
            String result = PiiRedactor.redact(input); // GH-90000
            assertThat(result).contains("***@***.***");
            assertThat(result).contains("\n");
        }

        @Test
        @DisplayName("should handle strings with tabs")
        void shouldHandleStringsWithTabs() { // GH-90000
            String input = "Email:\tuser@example.com";
            String result = PiiRedactor.redact(input); // GH-90000
            assertThat(result).contains("***@***.***");
        }

        @Test
        @DisplayName("should handle strings with multiple spaces")
        void shouldHandleStringsWithMultipleSpaces() { // GH-90000
            String input = "Contact   user@example.com   for  support";
            String result = PiiRedactor.redact(input); // GH-90000
            assertThat(result).contains("***@***.***");
        }
    }
}
