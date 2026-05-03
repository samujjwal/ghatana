package com.ghatana.digitalmarketing.application.privacy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for ContactEncryptionService (DMOS-P1-014).
 *
 * @doc.type test
 * @doc.purpose Verify encryption and decryption of sensitive contact data
 * @doc.layer application
 */
@DisplayName("ContactEncryptionService")
class ContactEncryptionServiceTest {

    @Test
    @DisplayName("encrypt produces ciphertext that can be decrypted")
    void encrypt_decryptsSuccessfully() {
        ContactEncryptionService service = new ContactEncryptionService();
        String plainText = "test@example.com";
        String cipherText = service.encrypt(plainText);
        String decrypted = service.decrypt(cipherText);
        assertThat(decrypted).isEqualTo(plainText);
    }

    @Test
    @DisplayName("encrypt throws on null plain text")
    void encrypt_throwsOnNullPlainText() {
        ContactEncryptionService service = new ContactEncryptionService();
        assertThatThrownBy(() -> service.encrypt(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("decrypt throws on null cipher text")
    void decrypt_throwsOnNullCipherText() {
        ContactEncryptionService service = new ContactEncryptionService();
        assertThatThrownBy(() -> service.decrypt(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("decrypt throws on invalid cipher text")
    void decrypt_throwsOnInvalidCipherText() {
        ContactEncryptionService service = new ContactEncryptionService();
        assertThatThrownBy(() -> service.decrypt("invalid-ciphertext"))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("encrypt produces different ciphertext for same plain text (due to random IV)")
    void encrypt_producesDifferentCiphertext() {
        ContactEncryptionService service = new ContactEncryptionService();
        String plainText = "test@example.com";
        String cipherText1 = service.encrypt(plainText);
        String cipherText2 = service.encrypt(plainText);
        assertThat(cipherText1).isNotEqualTo(cipherText2);
    }

    @Test
    @DisplayName("decrypt recovers plain text from different ciphertexts")
    void decrypt_recoversPlainText() {
        ContactEncryptionService service = new ContactEncryptionService();
        String plainText = "test@example.com";
        String cipherText1 = service.encrypt(plainText);
        String cipherText2 = service.encrypt(plainText);
        assertThat(service.decrypt(cipherText1)).isEqualTo(plainText);
        assertThat(service.decrypt(cipherText2)).isEqualTo(plainText);
    }
}
