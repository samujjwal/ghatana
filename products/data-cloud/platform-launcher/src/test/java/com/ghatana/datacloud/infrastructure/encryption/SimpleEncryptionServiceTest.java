package com.ghatana.datacloud.infrastructure.encryption;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

class SimpleEncryptionServiceTest {

    String key;
    SimpleEncryptionService service;

    @BeforeEach
    void setUp() {
        key = SimpleEncryptionService.generateKey();
        service = new SimpleEncryptionService(key);
    }

    // ── round-trip ───────────────────────────────────────────────────────────

    @Test
    void encrypt_decrypt_roundTrip_shortBytes() {
        byte[] plaintext = "hello world".getBytes(StandardCharsets.UTF_8);
        byte[] decrypted = service.decrypt(service.encrypt(plaintext));
        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void encrypt_decrypt_roundTrip_longBytes() {
        byte[] plaintext = "a".repeat(10_000).getBytes(StandardCharsets.UTF_8);
        assertThat(service.decrypt(service.encrypt(plaintext))).isEqualTo(plaintext);
    }

    @Test
    void encrypt_decrypt_roundTrip_unicodeBytes() {
        byte[] plaintext = "こんにちは世界 🌍 тест".getBytes(StandardCharsets.UTF_8);
        assertThat(service.decrypt(service.encrypt(plaintext))).isEqualTo(plaintext);
    }

    @Test
    void encrypt_decrypt_roundTrip_emptyBytes() {
        byte[] plaintext = new byte[0];
        assertThat(service.decrypt(service.encrypt(plaintext))).isEqualTo(plaintext);
    }

    // ── ciphertext properties ────────────────────────────────────────────────

    @Test
    void encrypt_producesDifferentCiphertextForSameInput() {
        // GCM uses random IV so same plaintext → different ciphertext each time
        byte[] input = "same input".getBytes(StandardCharsets.UTF_8);
        byte[] ct1 = service.encrypt(input);
        byte[] ct2 = service.encrypt(input);
        assertThat(ct1).isNotEqualTo(ct2);
    }

    @Test
    void encrypt_outputLongerThanInput() {
        byte[] input = "test data".getBytes(StandardCharsets.UTF_8);
        byte[] ciphertext = service.encrypt(input);
        // IV (12) + GCM tag (16) overhead
        assertThat(ciphertext.length).isGreaterThan(input.length);
    }

    @Test
    void encrypt_doesNotContainPlaintextLiteral() {
        byte[] input = "secret password".getBytes(StandardCharsets.UTF_8);
        byte[] ciphertext = service.encrypt(input);
        // Ciphertext bytes should differ from plaintext bytes
        assertThat(ciphertext).isNotEqualTo(input);
    }

    // ── invalid / tampered data ──────────────────────────────────────────────

    @Test
    void decrypt_tamperedData_throwsEncryptionException() {
        byte[] plaintext = "original data".getBytes(StandardCharsets.UTF_8);
        byte[] ciphertext = service.encrypt(plaintext);
        // Flip a byte in the auth tag area (last 16 bytes)
        ciphertext[ciphertext.length - 1] ^= 0xFF;
        assertThatThrownBy(() -> service.decrypt(ciphertext))
                .isInstanceOf(SimpleEncryptionService.EncryptionException.class);
    }

    @Test
    void decrypt_tooShortData_throwsEncryptionException() {
        // Less than 12-byte IV minimum
        byte[] tooShort = new byte[5];
        assertThatThrownBy(() -> service.decrypt(tooShort))
                .isInstanceOf(SimpleEncryptionService.EncryptionException.class);
    }

    @Test
    void decrypt_null_throwsNPE() {
        assertThatNullPointerException().isThrownBy(() -> service.decrypt(null));
    }

    @Test
    void encrypt_null_throwsNPE() {
        assertThatNullPointerException().isThrownBy(() -> service.encrypt(null));
    }

    @Test
    void decrypt_truncatedCiphertext_throwsEncryptionException() {
        byte[] ciphertext = service.encrypt("some data".getBytes(StandardCharsets.UTF_8));
        byte[] truncated = new byte[ciphertext.length / 2];
        System.arraycopy(ciphertext, 0, truncated, 0, truncated.length);
        assertThatThrownBy(() -> service.decrypt(truncated))
                .isInstanceOf(SimpleEncryptionService.EncryptionException.class);
    }

    // ── key validation ───────────────────────────────────────────────────────

    @Test
    void constructor_nullKey_throwsNPE() {
        assertThatNullPointerException().isThrownBy(() -> new SimpleEncryptionService(null));
    }

    @Test
    void constructor_wrongKeyLength_throwsIllegalArgument() {
        // 16 bytes base64 → only 128-bit key, not 256-bit
        String shortKey = java.util.Base64.getEncoder().encodeToString(new byte[16]);
        assertThatIllegalArgumentException().isThrownBy(() -> new SimpleEncryptionService(shortKey));
    }

    @Test
    void constructor_invalidBase64_throwsIllegalArgument() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SimpleEncryptionService("not-valid-base64!!!"));
    }

    // ── cross-key isolation ──────────────────────────────────────────────────

    @Test
    void twoInstances_withDifferentKeys_cannotCrossDecrypt() {
        SimpleEncryptionService service2 = new SimpleEncryptionService(
                SimpleEncryptionService.generateKey());
        byte[] ciphertext = service.encrypt("cross-key test".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> service2.decrypt(ciphertext))
                .isInstanceOf(SimpleEncryptionService.EncryptionException.class);
    }

    // ── generateKey ──────────────────────────────────────────────────────────

    @Test
    void generateKey_producesValidBase64_256bitKey() {
        String generatedKey = SimpleEncryptionService.generateKey();
        byte[] keyBytes = java.util.Base64.getDecoder().decode(generatedKey);
        assertThat(keyBytes).hasSize(32); // 256 bits
    }

    @Test
    void generateKey_producesDifferentKeysEachTime() {
        String k1 = SimpleEncryptionService.generateKey();
        String k2 = SimpleEncryptionService.generateKey();
        assertThat(k1).isNotEqualTo(k2);
    }
}
