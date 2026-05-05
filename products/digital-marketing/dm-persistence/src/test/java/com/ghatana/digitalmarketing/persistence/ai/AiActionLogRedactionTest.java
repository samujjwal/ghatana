package com.ghatana.digitalmarketing.persistence.ai;

import com.ghatana.digitalmarketing.domain.ai.AiActionLog;
import com.ghatana.digitalmarketing.domain.ai.SensitiveDataRedactor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1-014: Sensitive redaction tests for AI action log.
 *
 * <p>Comprehensive tests for PII and sensitive data redaction:
 * <ul>
 *   <li>Email address redaction</li>
 *   <li>Phone number masking</li>
 *   <li>Credit card number masking</li>
 *   <li>SSN/Tax ID masking</li>
 *   <li>API key and token redaction</li>
 *   <li>Password redaction</li>
 *   <li>Nested object redaction</li>
 *   <li>Permission-based redaction</li>
 * </ul>
 */
@DisplayName("P1-014: AI Action Log Sensitive Data Redaction Tests")
class AiActionLogRedactionTest {

    private SensitiveDataRedactor redactor;

    @BeforeEach
    void setUp() {
        redactor = new SensitiveDataRedactor();
    }

    @Test
    @DisplayName("P1-014: Email addresses are redacted in AI prompts")
    void shouldRedactEmailAddresses() {
        // Given
        String prompt = "Contact john.doe@example.com or jane.smith@company.co.uk for details";

        // When
        String redacted = redactor.redact(prompt);

        // Then
        assertThat(redacted).doesNotContain("john.doe@example.com");
        assertThat(redacted).doesNotContain("jane.smith@company.co.uk");
        assertThat(redacted).contains("[EMAIL REDACTED]");
        assertThat(redacted).doesNotMatch("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b");
    }

    @Test
    @DisplayName("P1-014: Phone numbers are masked in AI prompts")
    void shouldMaskPhoneNumbers() {
        // Given
        String prompt = "Call us at (555) 123-4567 or 555-987-6543 or +1-555-555-5555";

        // When
        String redacted = redactor.redact(prompt);

        // Then
        assertThat(redacted).doesNotContain("(555) 123-4567");
        assertThat(redacted).doesNotContain("555-987-6543");
        assertThat(redacted).doesNotContain("+1-555-555-5555");
        assertThat(redacted).contains("[PHONE REDACTED]");
    }

    @Test
    @DisplayName("P1-014: Credit card numbers are masked")
    void shouldMaskCreditCardNumbers() {
        // Given
        String prompt = "Payment card: 4532-1234-5678-9012 or 4532123456789012";

        // When
        String redacted = redactor.redact(prompt);

        // Then
        assertThat(redacted).doesNotContain("4532-1234-5678-9012");
        assertThat(redacted).doesNotContain("4532123456789012");
        assertThat(redacted).contains("[CREDIT_CARD REDACTED]");
    }

    @Test
    @DisplayName("P1-014: SSN/Tax ID numbers are masked")
    void shouldMaskSsnAndTaxIds() {
        // Given
        String prompt = "SSN: 123-45-6789 or Tax ID: 98-7654321";

        // When
        String redacted = redactor.redact(prompt);

        // Then
        assertThat(redacted).doesNotContain("123-45-6789");
        assertThat(redacted).doesNotContain("98-7654321");
        assertThat(redacted).contains("[SSN REDACTED]");
    }

    @Test
    @DisplayName("P1-014: API keys and tokens are fully redacted")
    void shouldRedactApiKeysAndTokens() {
        // Given
        String prompt = "API Key: sk-1234567890abcdef or Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9";

        // When
        String redacted = redactor.redact(prompt);

        // Then
        assertThat(redacted).doesNotContain("sk-1234567890abcdef");
        assertThat(redacted).doesNotContain("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9");
        assertThat(redacted).contains("[API_KEY REDACTED]");
        assertThat(redacted).contains("[TOKEN REDACTED]");
    }

    @Test
    @DisplayName("P1-014: Passwords are fully redacted")
    void shouldRedactPasswords() {
        // Given
        String prompt = "Password: MySecret123! or pwd: anotherPassword456";

        // When
        String redacted = redactor.redact(prompt);

        // Then
        assertThat(redacted).doesNotContain("MySecret123!");
        assertThat(redacted).doesNotContain("anotherPassword456");
        assertThat(redacted).contains("[PASSWORD REDACTED]");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Contact support@company.com for help",
        "Email: user123@gmail.com",
        "Reach out to admin@sub.domain.org"
    })
    @DisplayName("P1-014: Various email formats are redacted")
    void shouldRedactVariousEmailFormats(String input) {
        // When
        String redacted = redactor.redact(input);

        // Then
        assertThat(redacted).contains("[EMAIL REDACTED]");
        assertThat(redacted).doesNotMatch("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    }

    @Test
    @DisplayName("P1-014: Nested JSON objects have sensitive data redacted")
    void shouldRedactSensitiveDataInNestedObjects() {
        // Given
        Map<String, Object> data = Map.of(
            "user", Map.of(
                "name", "John Doe",
                "email", "john.doe@example.com",
                "phone", "555-123-4567"
            ),
            "payment", Map.of(
                "cardNumber", "4532-1234-5678-9012",
                "expiry", "12/25"
            )
        );

        // When
        Map<String, Object> redacted = redactor.redactMap(data);

        // Then
        assertThat(redacted)
            .extractingByKey("user")
            .asInstanceOf(map(String.class, Object.class))
            .containsEntry("name", "John Doe")
            .containsEntry("email", "[EMAIL REDACTED]")
            .containsEntry("phone", "[PHONE REDACTED]");

        assertThat(redacted)
            .extractingByKey("payment")
            .asInstanceOf(map(String.class, Object.class))
            .containsEntry("cardNumber", "[CREDIT_CARD REDACTED]")
            .containsEntry("expiry", "12/25");
    }

    @Test
    @DisplayName("P1-014: AI action log entry has sensitive data redacted before storage")
    void shouldRedactBeforeStorage() {
        // Given
        AiActionLog logEntry = AiActionLog.builder()
            .id("log-123")
            .action("STRATEGY_GENERATED")
            .prompt("Generate strategy for user john.doe@example.com with budget $50000")
            .response("Strategy created. Contact support at 555-HELP for questions.")
            .build();

        // When
        AiActionLog redactedLog = redactor.redactLogEntry(logEntry);

        // Then
        assertThat(redactedLog.getPrompt())
            .contains("[EMAIL REDACTED]")
            .doesNotContain("john.doe@example.com");

        assertThat(redactedLog.getResponse())
            .contains("[PHONE REDACTED]")
            .doesNotContain("555-HELP");

        // Non-sensitive data preserved
        assertThat(redactedLog.getPrompt()).contains("$50000");
    }

    @Test
    @DisplayName("P1-014: Permission-based redaction allows admin full access")
    void shouldRespectPermissionBasedRedaction() {
        // Given
        String content = "User: john.doe@example.com, Phone: 555-123-4567";
        Set<String> userPermissions = Set.of("ai.log.read.full");

        // When - Admin with full permission
        String adminView = redactor.redactWithPermissions(content, userPermissions);

        // Then
        assertThat(adminView)
            .contains("john.doe@example.com")
            .contains("555-123-4567");

        // When - Regular user without full permission
        Set<String> limitedPermissions = Set.of("ai.log.read.redacted");
        String userView = redactor.redactWithPermissions(content, limitedPermissions);

        // Then
        assertThat(userView)
            .contains("[EMAIL REDACTED]")
            .contains("[PHONE REDACTED]");
    }

    @Test
    @DisplayName("P1-014: Lists of sensitive data are all redacted")
    void shouldRedactListsOfSensitiveData() {
        // Given
        List<String> emails = List.of(
            "user1@example.com",
            "user2@company.com",
            "user3@domain.org"
        );

        // When
        List<String> redacted = redactor.redactList(emails);

        // Then
        assertThat(redacted).allMatch(s -> s.equals("[EMAIL REDACTED]"));
    }

    @Test
    @DisplayName("P1-014: Mixed content has only sensitive parts redacted")
    void shouldRedactMixedContent() {
        // Given
        String content = "Meeting with john@example.com about campaign strategy. " +
            "Budget: $10000. Contact: 555-123-4567. Deadline: 2026-01-15";

        // When
        String redacted = redactor.redact(content);

        // Then
        assertThat(redacted)
            .contains("[EMAIL REDACTED]")
            .contains("[PHONE REDACTED]")
            .contains("$10000")
            .contains("campaign strategy")
            .contains("2026-01-15");
    }

    @Test
    @DisplayName("P1-014: Redaction preserves text structure and length hints")
    void shouldPreserveStructureAndHints() {
        // Given
        String content = "Email: john.doe@example.com and another: jane@company.com";

        // When
        String redacted = redactor.redactWithHints(content);

        // Then
        assertThat(redacted).contains("[EMAIL REDACTED x2]");
    }

    @Test
    @DisplayName("P1-014: No false positives on non-sensitive data")
    void shouldNotRedactNonSensitiveData() {
        // Given - these look like but are not sensitive
        String content = "Version 1.2.3.4 of the API. Campaign ID: ABC-123-XYZ. " +
            "Timestamp: 2026-01-15T10:30:00Z. Amount: $1234.56";

        // When
        String redacted = redactor.redact(content);

        // Then
        assertThat(redacted).isEqualTo(content); // No changes
    }

    @Test
    @DisplayName("P1-014: Empty and null content handled gracefully")
    void shouldHandleEmptyAndNullContent() {
        assertThat(redactor.redact("")).isEqualTo("");
        assertThat(redactor.redact(null)).isEqualTo("");
    }

    @Test
    @DisplayName("P1-014: Already redacted content not double-redacted")
    void shouldNotDoubleRedact() {
        // Given
        String content = "Contact [EMAIL REDACTED] for help";

        // When
        String redacted = redactor.redact(content);

        // Then
        assertThat(redacted).isEqualTo(content);
        assertThat(redacted).contains("[EMAIL REDACTED]");
        assertThat(redacted).doesNotContain("[EMAIL REDACTED] [EMAIL REDACTED]");
    }

    @Test
    @DisplayName("P1-014: Redaction maintains audit trail")
    void shouldMaintainAuditTrail() {
        // Given
        String original = "Contact john.doe@example.com";

        // When
        SensitiveDataRedactor.RedactionResult result = redactor.redactWithAudit(original);

        // Then
        assertThat(result.redactedContent()).contains("[EMAIL REDACTED]");
        assertThat(result.redactions()).hasSize(1);
        assertThat(result.redactions().get(0).type()).isEqualTo("EMAIL");
        assertThat(result.redactions().get(0).originalLength()).isEqualTo("john.doe@example.com".length());
    }

    // Helper method for type assertion
    private static <K, V> org.assertj.core.api.InstanceOfAssertFactory<Map<K, V>, org.assertj.core.api.MapAssert<K, V>> map(Class<K> keyClass, Class<V> valueClass) {
        return new org.assertj.core.api.InstanceOfAssertFactory<>(Map.class, org.assertj.core.api.Assertions::assertThat);
    }
}
