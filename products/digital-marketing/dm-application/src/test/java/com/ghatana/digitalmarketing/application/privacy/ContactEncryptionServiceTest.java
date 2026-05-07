package com.ghatana.digitalmarketing.application.privacy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for ContactEncryptionService (DMOS-P1-014, P1-015).
 *
 * @doc.type test
 * @doc.purpose Verify encryption/decryption of sensitive contact data and fail-fast key behaviour
 * @doc.layer application
 */
@DisplayName("ContactEncryptionService")
class ContactEncryptionServiceTest {

    private static final String TEST_KEY = "test-encryption-key-32chars-long!";

    private ContactEncryptionService service;

    @BeforeEach
    void setUp() {
        service = new ContactEncryptionService(TEST_KEY);
    }

    @Test
    @DisplayName("encrypt produces ciphertext that can be decrypted")
    void encryptDecryptsSuccessfully() {
        String plainText = "test@example.com";
        String cipherText = service.encrypt(plainText);
        assertThat(service.decrypt(cipherText)).isEqualTo(plainText);
    }

    @Test
    @DisplayName("encrypt produces different ciphertext for same plain text")
    void encryptProducesDifferentCiphertexts() {
        String plainText = "test@example.com";
        String cipherText1 = service.encrypt(plainText);
        String cipherText2 = service.encrypt(plainText);
        assertThat(cipherText1).isNotEqualTo(cipherText2);
        assertThat(service.decrypt(cipherText1)).isEqualTo(plainText);
        assertThat(service.decrypt(cipherText2)).isEqualTo(plainText);
    }

    @Test
    @DisplayName("encrypt throws NullPointerException for null input")
    void encryptThrowsOnNull() {
        assertThatThrownBy(() -> service.encrypt(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("decrypt throws NullPointerException for null cipher text")
    void decryptThrowsOnNull() {
        assertThatThrownBy(() -> service.decrypt(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("decrypt throws on tampered ciphertext")
    void decryptThrowsOnTamperedInput() {
        String cipherText = service.encrypt("sensitive-data");
        String tampered = cipherText.substring(0, cipherText.length() - 4) + "XXXX";
        assertThatThrownBy(() -> service.decrypt(tampered))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("P1-015: no-arg constructor throws when env var is absent")
    void noArgConstructorThrowsWhenEnvAbsent() {
        if (System.getenv("DMOS_CONTACT_ENCRYPTION_KEY") == null) {
            assertThatThrownBy(ContactEncryptionService::new)
                .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    @DisplayName("constructor throws when rawKey is shorter than 32 characters")
    void constructorThrowsOnShortKey() {
        assertThatThrownBy(() -> new ContactEncryptionService("short"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
