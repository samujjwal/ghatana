/*
 * Copyright (c) 2026 Ghatana Technologies // GH-90000
 * YAPPC Services Platform — SecretFieldMapper Tests
 */
package com.ghatana.yappc.services.security;

import com.ghatana.yappc.infrastructure.security.EncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link SecretFieldMapper}.
 *
 * <p>Uses a real {@link EncryptionService} with a predictable 32-byte key so that
 * round-trip properties can be verified without mocking the cipher.
 */
@DisplayName("SecretFieldMapper [GH-90000]")
class SecretFieldMapperTest {

    /** 32-byte all-zero key — acceptable for testing; never use in production. */
    private static final byte[] TEST_KEY = new byte[32];

    private SecretFieldMapper mapper;

    @BeforeEach
    void setUp() { // GH-90000
        mapper = new SecretFieldMapper(new EncryptionService(TEST_KEY)); // GH-90000
    }

    // ── encrypt ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("encrypt: produces enc:: prefix [GH-90000]")
    void encrypt_addsPrefix() { // GH-90000
        String result = mapper.encrypt("my-secret-api-key", "lifecycle.config.apiKey"); // GH-90000

        assertThat(result).startsWith("enc:: [GH-90000]");
    }

    @Test
    @DisplayName("encrypt: different calls produce different ciphertexts (random IV) [GH-90000]")
    void encrypt_randomIv_differentCiphertexts() { // GH-90000
        String first  = mapper.encrypt("same-value", "field"); // GH-90000
        String second = mapper.encrypt("same-value", "field"); // GH-90000

        assertThat(first).isNotEqualTo(second); // GH-90000
    }

    @Test
    @DisplayName("encrypt: null input returns null [GH-90000]")
    void encrypt_null_returnsNull() { // GH-90000
        assertThat(mapper.encrypt(null, "lifecycle.apiKey")).isNull(); // GH-90000
    }

    @Test
    @DisplayName("encrypt: already-encrypted value is returned unchanged (idempotent) [GH-90000]")
    void encrypt_alreadyEncrypted_isIdempotent() { // GH-90000
        String firstPass = mapper.encrypt("plaintext", "field"); // GH-90000
        String secondPass = mapper.encrypt(firstPass, "field"); // GH-90000

        assertThat(secondPass).isEqualTo(firstPass); // GH-90000
    }

    @Test
    @DisplayName("encrypt: empty string is encrypted normally [GH-90000]")
    void encrypt_emptyString_isEncrypted() { // GH-90000
        String result = mapper.encrypt("", "field"); // GH-90000

        assertThat(result).startsWith("enc:: [GH-90000]");
    }

    // ── decrypt ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("decrypt: round-trip returns original plaintext [GH-90000]")
    void decrypt_roundTrip() { // GH-90000
        String original  = "my-secret-api-key";
        String encrypted = mapper.encrypt(original, "field"); // GH-90000
        String decrypted = mapper.decrypt(encrypted, "field"); // GH-90000

        assertThat(decrypted).isEqualTo(original); // GH-90000
    }

    @Test
    @DisplayName("decrypt: null input returns null [GH-90000]")
    void decrypt_null_returnsNull() { // GH-90000
        assertThat(mapper.decrypt(null, "field")).isNull(); // GH-90000
    }

    @Test
    @DisplayName("decrypt: plaintext without enc:: prefix is returned as-is (graceful migration) [GH-90000]")
    void decrypt_plaintext_returnedAsIs() { // GH-90000
        String result = mapper.decrypt("not-yet-migrated-value", "field"); // GH-90000

        assertThat(result).isEqualTo("not-yet-migrated-value [GH-90000]");
    }

    @Test
    @DisplayName("decrypt: empty string without prefix is returned as-is [GH-90000]")
    void decrypt_emptyNonEncrypted_returnedAsIs() { // GH-90000
        assertThat(mapper.decrypt("", "field")).isEqualTo(" [GH-90000]");
    }

    // ── isEncrypted ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("isEncrypted: returns true for enc:: prefixed values [GH-90000]")
    void isEncrypted_true_forEncPrefix() { // GH-90000
        String encrypted = mapper.encrypt("value", "field"); // GH-90000

        assertThat(SecretFieldMapper.isEncrypted(encrypted)).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("isEncrypted: returns false for plaintext [GH-90000]")
    void isEncrypted_false_forPlaintext() { // GH-90000
        assertThat(SecretFieldMapper.isEncrypted("plain-api-key [GH-90000]")).isFalse();
    }

    @Test
    @DisplayName("isEncrypted: returns false for null [GH-90000]")
    void isEncrypted_false_forNull() { // GH-90000
        assertThat(SecretFieldMapper.isEncrypted(null)).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("isEncrypted: returns false for empty string [GH-90000]")
    void isEncrypted_false_forEmpty() { // GH-90000
        assertThat(SecretFieldMapper.isEncrypted(" [GH-90000]")).isFalse();
    }

    // ── Constructor validation ────────────────────────────────────────────────

    @Test
    @DisplayName("null encryptionService throws NullPointerException [GH-90000]")
    void constructor_nullService_throws() { // GH-90000
        assertThatThrownBy(() -> new SecretFieldMapper(null)) // GH-90000
                .isInstanceOf(NullPointerException.class) // GH-90000
                .hasMessageContaining("encryptionService [GH-90000]");
    }

    // ── Lifecycle / approval / agent-config field scenarios ──────────────────

    @Test
    @DisplayName("encrypt+decrypt: lifecycle apiKey round-trip [GH-90000]")
    void lifecycleApiKey_roundTrip() { // GH-90000
        String apiKey = "sk-lifecycle-api-key-123";
        String stored = mapper.encrypt(apiKey, "lifecycle.config.apiKey"); // GH-90000
        String loaded = mapper.decrypt(stored, "lifecycle.config.apiKey"); // GH-90000

        assertThat(loaded).isEqualTo(apiKey); // GH-90000
    }

    @Test
    @DisplayName("encrypt+decrypt: approval attachment secret round-trip [GH-90000]")
    void approvalSecret_roundTrip() { // GH-90000
        String secret = "approval-signing-secret-xyz";
        String stored = mapper.encrypt(secret, "approval.signingSecret"); // GH-90000
        String loaded = mapper.decrypt(stored, "approval.signingSecret"); // GH-90000

        assertThat(loaded).isEqualTo(secret); // GH-90000
    }

    @Test
    @DisplayName("encrypt+decrypt: agent config third-party credential round-trip [GH-90000]")
    void agentCredential_roundTrip() { // GH-90000
        String credential = "bearer eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJhZ2VudC0xIn0";
        String stored = mapper.encrypt(credential, "agent.config.bearerToken"); // GH-90000
        String loaded = mapper.decrypt(stored, "agent.config.bearerToken"); // GH-90000

        assertThat(loaded).isEqualTo(credential); // GH-90000
    }
}
