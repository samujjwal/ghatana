package com.ghatana.datacloud.infrastructure.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FieldLevelEncryptionServiceTest {

    private final FieldLevelEncryptionService service = new FieldLevelEncryptionService();
    private final byte[] key = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    @Test
    void encryptsAndDecryptsOnlySensitiveFields() {
        Map<String, Object> payload = Map.of(
            "name", "Alice",
            "ssn", "123-45-6789",
            "email", "alice@example.com");

        Map<String, Object> encrypted = service.encryptSensitiveFields(payload, Set.of("ssn"), key);

        assertThat(encrypted.get("name")).isEqualTo("Alice");
        assertThat(encrypted.get("ssn").toString())
            .startsWith(FieldLevelEncryptionService.ENCRYPTED_PREFIX)
            .doesNotContain("123-45-6789");

        Map<String, Object> decrypted = service.decryptSensitiveFields(encrypted, Set.of("ssn"), key);
        assertThat(decrypted).containsEntry("ssn", "123-45-6789");
    }

    @Test
    void doesNotEncryptAlreadyEncryptedValuesTwice() {
        Map<String, Object> encrypted = service.encryptSensitiveFields(
            Map.of("secret", "value"),
            Set.of("secret"),
            key);

        Map<String, Object> encryptedAgain = service.encryptSensitiveFields(encrypted, Set.of("secret"), key);

        assertThat(encryptedAgain).containsEntry("secret", encrypted.get("secret"));
    }

    @Test
    void redactsSensitiveFieldsForAuditExport() {
        Map<String, Object> redacted = service.redactSensitiveFields(
            Map.of("token", "secret-token", "status", "active"),
            Set.of("token"));

        assertThat(redacted)
            .containsEntry("token", FieldLevelEncryptionService.REDACTED_VALUE)
            .containsEntry("status", "active");
    }

    @Test
    void rejectsInvalidKeySizes() {
        assertThatThrownBy(() -> service.encryptSensitiveFields(
            Map.of("secret", "value"),
            Set.of("secret"),
            "short".getBytes(StandardCharsets.UTF_8)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("AES key");
    }
}
