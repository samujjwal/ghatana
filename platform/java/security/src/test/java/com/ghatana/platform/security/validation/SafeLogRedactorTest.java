package com.ghatana.platform.security.validation;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SafeLogRedactor")
class SafeLogRedactorTest {

    @Test
    @DisplayName("redacts free-text PII using the shared core redactor")
    void redactsFreeTextPii() {
        String redacted = SafeLogRedactor.redactText(
            "Contact patient@example.com with token=abc123 and card 4111-1111-1111-1111"
        );

        assertThat(redacted).contains("***@***.***");
        assertThat(redacted).contains("token=****");
        assertThat(redacted).contains("****-1111");
    }

    @Test
    @DisplayName("redacts structured metadata by sensitive key and string value")
    void redactsStructuredMetadata() {
        Map<String, Object> redacted = SafeLogRedactor.redactMetadata(Map.of(
            "tenantId", "tenant-1",
            "email", "patient@example.com",
            "accessToken", "super-secret-token",
            "nested", Map.of(
                "password", "cleartext",
                "note", "Call 415-555-1212"
            )
        ));

        assertThat(redacted).containsEntry("tenantId", "tenant-1");
        assertThat(redacted).containsEntry("email", "***@***.***");
        assertThat(redacted).containsEntry("accessToken", "****");
        assertThat(redacted.get("nested")).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) redacted.get("nested");
        assertThat(nested).containsEntry("password", "****");
        assertThat(nested).containsEntry("note", "Call ****");
    }

    @Test
    @DisplayName("rejects blank structured metadata keys")
    void rejectsBlankMetadataKeys() {
        assertThatThrownBy(() -> SafeLogRedactor.redactMetadata(Map.of("", "value")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("metadata keys must not be blank");
    }

    @Test
    @DisplayName("detects sensitive text without redacting first")
    void detectsSensitiveText() {
        assertThat(SafeLogRedactor.containsSensitiveText("ssn 123-45-6789")).isTrue();
        assertThat(SafeLogRedactor.containsSensitiveText("tenant tenant-1")).isFalse();
    }
}
