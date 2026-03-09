package com.ghatana.tts.core.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for TTS ProfileEncryption.
 *
 * @doc.type test
 * @doc.purpose Test AES-GCM encryption/decryption for TTS profiles
 * @doc.layer core
 */
@DisplayName("TTS ProfileEncryption Tests")
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
        String plaintext = "TTS Profile: User preferences and voice settings";

        // WHEN
        String encrypted = encryption.encryptString(plaintext);
        String decrypted = encryption.decryptString(encrypted);

        // THEN
        assertThat(encrypted).isNotEqualTo(plaintext);
        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("Should produce different ciphertext each time")
    void shouldProduceDifferentCiphertextEachTime() throws Exception {
        // GIVEN
        String plaintext = "Same voice settings";

        // WHEN
        String encrypted1 = encryption.encryptString(plaintext);
        String encrypted2 = encryption.encryptString(plaintext);

        // THEN
        assertThat(encrypted1).isNotEqualTo(encrypted2);
        assertThat(encryption.decryptString(encrypted1)).isEqualTo(plaintext);
        assertThat(encryption.decryptString(encrypted2)).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("Should fail with wrong key")
    void shouldFailWithWrongKey() throws Exception {
        // GIVEN
        String plaintext = "Secret voice profile";
        String encrypted = encryption.encryptString(plaintext);
        ProfileEncryption differentKey = new ProfileEncryption();

        // WHEN/THEN
        assertThatThrownBy(() -> differentKey.decryptString(encrypted))
            .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Should handle large voice profile data")
    void shouldHandleLargeData() throws Exception {
        // GIVEN
        StringBuilder largeData = new StringBuilder();
        for (int i = 0; i < 5000; i++) {
            largeData.append("Voice sample metadata line ").append(i).append("\n");
        }
        String plaintext = largeData.toString();

        // WHEN
        String encrypted = encryption.encryptString(plaintext);
        String decrypted = encryption.decryptString(encrypted);

        // THEN
        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("Should derive key from password")
    void shouldDeriveKeyFromPassword() throws Exception {
        // GIVEN
        String password = "VoicePassword2025!";
        byte[] salt = new byte[16];

        // WHEN
        ProfileEncryption enc1 = ProfileEncryption.fromPassword(password, salt);
        ProfileEncryption enc2 = ProfileEncryption.fromPassword(password, salt);

        String plaintext = "Voice data";
        String encrypted = enc1.encryptString(plaintext);

        // THEN
        assertThat(enc2.decryptString(encrypted)).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("Should export and reimport key")
    void shouldExportAndReimportKey() throws Exception {
        // GIVEN
        String plaintext = "Voice settings";
        String encrypted = encryption.encryptString(plaintext);
        String key = encryption.getEncodedKey();

        // WHEN
        ProfileEncryption newEncryption = new ProfileEncryption(key);
        String decrypted = newEncryption.decryptString(encrypted);

        // THEN
        assertThat(decrypted).isEqualTo(plaintext);
    }
}

