/*
 * Copyright (c) 2026 Ghatana Technologies
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
@DisplayName("SecretFieldMapper")
class SecretFieldMapperTest {

    /** 32-byte all-zero key — acceptable for testing; never use in production. */
    private static final byte[] TEST_KEY = new byte[32];

    private SecretFieldMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new SecretFieldMapper(new EncryptionService(TEST_KEY));
    }

    // ── encrypt ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("encrypt: produces enc:: prefix")
    void encrypt_addsPrefix() {
        String result = mapper.encrypt("my-secret-api-key", "lifecycle.config.apiKey");

        assertThat(result).startsWith("enc::");
    }

    @Test
    @DisplayName("encrypt: different calls produce different ciphertexts (random IV)")
    void encrypt_randomIv_differentCiphertexts() {
        String first  = mapper.encrypt("same-value", "field");
        String second = mapper.encrypt("same-value", "field");

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("encrypt: null input returns null")
    void encrypt_null_returnsNull() {
        assertThat(mapper.encrypt(null, "lifecycle.apiKey")).isNull();
    }

    @Test
    @DisplayName("encrypt: already-encrypted value is returned unchanged (idempotent)")
    void encrypt_alreadyEncrypted_isIdempotent() {
        String firstPass = mapper.encrypt("plaintext", "field");
        String secondPass = mapper.encrypt(firstPass, "field");

        assertThat(secondPass).isEqualTo(firstPass);
    }

    @Test
    @DisplayName("encrypt: empty string is encrypted normally")
    void encrypt_emptyString_isEncrypted() {
        String result = mapper.encrypt("", "field");

        assertThat(result).startsWith("enc::");
    }

    // ── decrypt ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("decrypt: round-trip returns original plaintext")
    void decrypt_roundTrip() {
        String original  = "my-secret-api-key";
        String encrypted = mapper.encrypt(original, "field");
        String decrypted = mapper.decrypt(encrypted, "field");

        assertThat(decrypted).isEqualTo(original);
    }

    @Test
    @DisplayName("decrypt: null input returns null")
    void decrypt_null_returnsNull() {
        assertThat(mapper.decrypt(null, "field")).isNull();
    }

    @Test
    @DisplayName("decrypt: plaintext without enc:: prefix is returned as-is (graceful migration)")
    void decrypt_plaintext_returnedAsIs() {
        String result = mapper.decrypt("not-yet-migrated-value", "field");

        assertThat(result).isEqualTo("not-yet-migrated-value");
    }

    @Test
    @DisplayName("decrypt: empty string without prefix is returned as-is")
    void decrypt_emptyNonEncrypted_returnedAsIs() {
        assertThat(mapper.decrypt("", "field")).isEqualTo("");
    }

    // ── isEncrypted ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("isEncrypted: returns true for enc:: prefixed values")
    void isEncrypted_true_forEncPrefix() {
        String encrypted = mapper.encrypt("value", "field");

        assertThat(SecretFieldMapper.isEncrypted(encrypted)).isTrue();
    }

    @Test
    @DisplayName("isEncrypted: returns false for plaintext")
    void isEncrypted_false_forPlaintext() {
        assertThat(SecretFieldMapper.isEncrypted("plain-api-key")).isFalse();
    }

    @Test
    @DisplayName("isEncrypted: returns false for null")
    void isEncrypted_false_forNull() {
        assertThat(SecretFieldMapper.isEncrypted(null)).isFalse();
    }

    @Test
    @DisplayName("isEncrypted: returns false for empty string")
    void isEncrypted_false_forEmpty() {
        assertThat(SecretFieldMapper.isEncrypted("")).isFalse();
    }

    // ── Constructor validation ────────────────────────────────────────────────

    @Test
    @DisplayName("null encryptionService throws NullPointerException")
    void constructor_nullService_throws() {
        assertThatThrownBy(() -> new SecretFieldMapper(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("encryptionService");
    }

    // ── Lifecycle / approval / agent-config field scenarios ──────────────────

    @Test
    @DisplayName("encrypt+decrypt: lifecycle apiKey round-trip")
    void lifecycleApiKey_roundTrip() {
        String apiKey = "sk-lifecycle-api-key-123";
        String stored = mapper.encrypt(apiKey, "lifecycle.config.apiKey");
        String loaded = mapper.decrypt(stored, "lifecycle.config.apiKey");

        assertThat(loaded).isEqualTo(apiKey);
    }

    @Test
    @DisplayName("encrypt+decrypt: approval attachment secret round-trip")
    void approvalSecret_roundTrip() {
        String secret = "approval-signing-secret-xyz";
        String stored = mapper.encrypt(secret, "approval.signingSecret");
        String loaded = mapper.decrypt(stored, "approval.signingSecret");

        assertThat(loaded).isEqualTo(secret);
    }

    @Test
    @DisplayName("encrypt+decrypt: agent config third-party credential round-trip")
    void agentCredential_roundTrip() {
        String credential = "bearer eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJhZ2VudC0xIn0";
        String stored = mapper.encrypt(credential, "agent.config.bearerToken");
        String loaded = mapper.decrypt(stored, "agent.config.bearerToken");

        assertThat(loaded).isEqualTo(credential);
    }
}
