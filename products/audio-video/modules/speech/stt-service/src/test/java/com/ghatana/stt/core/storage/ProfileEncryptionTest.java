package com.ghatana.stt.core.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for ProfileEncryption.
 *
 * @doc.type test
 * @doc.purpose Test AES-GCM encryption/decryption
 * @doc.layer core
 */
@DisplayName("ProfileEncryption Tests")
class ProfileEncryptionTest {

    private ProfileEncryption encryption;

    @BeforeEach
    void setUp() throws Exception {
        encryption = new ProfileEncryption();
    }

    @Test
    @DisplayName("Should encrypt and decrypt string successfully")
    void shouldEncryptAndDecryptString() throws Exception {
        // GIVEN
        String plaintext = "Hello, World! This is a test profile data.";

        // WHEN
        String encrypted = encryption.encryptString(plaintext);
        String decrypted = encryption.decryptString(encrypted);

        // THEN
        assertThat(encrypted).isNotEqualTo(plaintext);
        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("Should encrypt and decrypt binary data successfully")
    void shouldEncryptAndDecryptBinary() throws Exception {
        // GIVEN
        byte[] plaintext = "Binary data test".getBytes("UTF-8");

        // WHEN
        byte[] encrypted = encryption.encrypt(plaintext);
        byte[] decrypted = encryption.decrypt(encrypted);

        // THEN
        assertThat(encrypted).isNotEqualTo(plaintext);
        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("Should produce different ciphertext for same plaintext (different IV)")
    void shouldProduceDifferentCiphertextEachTime() throws Exception {
        // GIVEN
        String plaintext = "Same plaintext";

        // WHEN
        String encrypted1 = encryption.encryptString(plaintext);
        String encrypted2 = encryption.encryptString(plaintext);

        // THEN
        assertThat(encrypted1).isNotEqualTo(encrypted2); // Different IVs
        assertThat(encryption.decryptString(encrypted1)).isEqualTo(plaintext);
        assertThat(encryption.decryptString(encrypted2)).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("Should fail to decrypt with wrong key")
    void shouldFailWithWrongKey() throws Exception {
        // GIVEN
        String plaintext = "Secret data";
        String encrypted = encryption.encryptString(plaintext);

        // Create new encryption with different key
        ProfileEncryption differentEncryption = new ProfileEncryption();

        // WHEN/THEN
        assertThatThrownBy(() -> differentEncryption.decryptString(encrypted))
            .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Should encrypt empty string")
    void shouldEncryptEmptyString() throws Exception {
        // GIVEN
        String plaintext = "";

        // WHEN
        String encrypted = encryption.encryptString(plaintext);
        String decrypted = encryption.decryptString(encrypted);

        // THEN
        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("Should encrypt long data")
    void shouldEncryptLongData() throws Exception {
        // GIVEN
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            sb.append("Data line ").append(i).append("\n");
        }
        String plaintext = sb.toString();

        // WHEN
        String encrypted = encryption.encryptString(plaintext);
        String decrypted = encryption.decryptString(encrypted);

        // THEN
        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("Should derive key from password consistently")
    void shouldDeriveKeyFromPassword() throws Exception {
        // GIVEN
        String password = "TestPassword123!";
        byte[] salt = new byte[16];
        for (int i = 0; i < salt.length; i++) {
            salt[i] = (byte) i;
        }

        // WHEN
        ProfileEncryption enc1 = ProfileEncryption.fromPassword(password, salt);
        ProfileEncryption enc2 = ProfileEncryption.fromPassword(password, salt);

        String plaintext = "Test data";
        String encrypted = enc1.encryptString(plaintext);

        // THEN - Same password+salt should decrypt
        assertThat(enc2.decryptString(encrypted)).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("Should fail with different password")
    void shouldFailWithDifferentPassword() throws Exception {
        // GIVEN
        byte[] salt = new byte[16];
        ProfileEncryption enc1 = ProfileEncryption.fromPassword("password1", salt);
        ProfileEncryption enc2 = ProfileEncryption.fromPassword("password2", salt);

        String plaintext = "Test data";
        String encrypted = enc1.encryptString(plaintext);

        // WHEN/THEN
        assertThatThrownBy(() -> enc2.decryptString(encrypted))
            .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Should handle Unicode characters")
    void shouldHandleUnicode() throws Exception {
        // GIVEN
        String plaintext = "Hello 世界 🌍 Привет مرحبا";

        // WHEN
        String encrypted = encryption.encryptString(plaintext);
        String decrypted = encryption.decryptString(encrypted);

        // THEN
        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("Should export and import key")
    void shouldExportAndImportKey() throws Exception {
        // GIVEN
        String plaintext = "Test data";
        String encrypted = encryption.encryptString(plaintext);

        // Export key
        String encodedKey = encryption.getEncodedKey();

        // Create new encryption with same key
        ProfileEncryption newEncryption = new ProfileEncryption(encodedKey);

        // WHEN
        String decrypted = newEncryption.decryptString(encrypted);

        // THEN
        assertThat(decrypted).isEqualTo(plaintext);
    }
}

