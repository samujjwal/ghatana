package com.ghatana.datacloud.infrastructure.encryption;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link SimpleEncryptionService}.
 *
 * @doc.type test
 * @doc.purpose Validate AES-GCM encryption correctness, security properties, and error handling
 * @doc.layer infrastructure
 */
@DisplayName("SimpleEncryptionService Tests")
class SimpleEncryptionServiceTest {

    private String validKey;
    private SimpleEncryptionService service;

    @BeforeEach
    void setUp() {
        validKey = SimpleEncryptionService.generateKey();
        service = new SimpleEncryptionService(validKey);
    }

    // =========================================================================
    // CONSTRUCTION
    // =========================================================================

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("should create service with valid key")
        void shouldCreateWithValidKey() {
            assertThatCode(() -> new SimpleEncryptionService(validKey)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should throw NullPointerException for null key")
        void shouldThrowForNullKey() {
            assertThatThrownBy(() -> new SimpleEncryptionService(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should throw IllegalArgumentException for key with wrong length")
        void shouldThrowForWrongKeyLength() {
            // 16-byte key (AES-128), not 32-byte (AES-256)
            byte[] shortKey = new byte[16];
            String shortBase64 = Base64.getEncoder().encodeToString(shortKey);
            assertThatThrownBy(() -> new SimpleEncryptionService(shortBase64))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid key length");
        }

        @Test
        @DisplayName("should throw IllegalArgumentException for invalid base64 key")
        void shouldThrowForInvalidBase64Key() {
            assertThatThrownBy(() -> new SimpleEncryptionService("not-valid-base64!!!"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // =========================================================================
    // KEY GENERATION
    // =========================================================================

    @Nested
    @DisplayName("Key Generation")
    class KeyGeneration {

        @Test
        @DisplayName("should generate a valid base64-encoded 32-byte key")
        void shouldGenerateValidKey() {
            String key = SimpleEncryptionService.generateKey();
            byte[] keyBytes = Base64.getDecoder().decode(key);
            assertThat(keyBytes).hasSize(32); // 256 bits
        }

        @Test
        @DisplayName("should generate unique keys on each call")
        void shouldGenerateUniqueKeys() {
            String key1 = SimpleEncryptionService.generateKey();
            String key2 = SimpleEncryptionService.generateKey();
            assertThat(key1).isNotEqualTo(key2);
        }

        @Test
        @DisplayName("generated key should be usable with service constructor")
        void generatedKeyShouldBeUsable() {
            String key = SimpleEncryptionService.generateKey();
            assertThatCode(() -> new SimpleEncryptionService(key)).doesNotThrowAnyException();
        }
    }

    // =========================================================================
    // ENCRYPT / DECRYPT ROUND TRIPS
    // =========================================================================

    @Nested
    @DisplayName("Encrypt and Decrypt")
    class EncryptDecrypt {

        @Test
        @DisplayName("should encrypt and decrypt plaintext correctly")
        void shouldEncryptAndDecrypt() {
            byte[] plaintext = "Hello, Data-Cloud!".getBytes(StandardCharsets.UTF_8);
            byte[] encrypted = service.encrypt(plaintext);
            byte[] decrypted = service.decrypt(encrypted);
            assertThat(decrypted).isEqualTo(plaintext);
        }

        @Test
        @DisplayName("should encrypt empty byte array")
        void shouldEncryptEmptyArray() {
            byte[] plaintext = new byte[0];
            byte[] encrypted = service.encrypt(plaintext);
            byte[] decrypted = service.decrypt(encrypted);
            assertThat(decrypted).isEqualTo(plaintext);
        }

        @ParameterizedTest
        @DisplayName("should encrypt and decrypt various payloads")
        @ValueSource(strings = {"a", "short", "medium-length payload for testing", "1234567890"})
        void shouldEncryptVariousPayloads(String text) {
            byte[] plaintext = text.getBytes(StandardCharsets.UTF_8);
            byte[] encrypted = service.encrypt(plaintext);
            byte[] decrypted = service.decrypt(encrypted);
            assertThat(new String(decrypted, StandardCharsets.UTF_8)).isEqualTo(text);
        }

        @Test
        @DisplayName("should encrypt large payload correctly")
        void shouldEncryptLargePayload() {
            byte[] bigPayload = new byte[1_000_000];
            new java.util.Random(42L).nextBytes(bigPayload);
            byte[] encrypted = service.encrypt(bigPayload);
            byte[] decrypted = service.decrypt(encrypted);
            assertThat(decrypted).isEqualTo(bigPayload);
        }

        @Test
        @DisplayName("encrypted output should differ from plaintext")
        void encryptedShouldDifferFromPlaintext() {
            byte[] plaintext = "sensitive data".getBytes(StandardCharsets.UTF_8);
            byte[] encrypted = service.encrypt(plaintext);
            assertThat(encrypted).isNotEqualTo(plaintext);
        }

        @Test
        @DisplayName("same plaintext encrypted twice should produce different ciphertexts (IV randomness)")
        void sameTextShouldProduceDifferentCiphertexts() {
            byte[] plaintext = "same data".getBytes(StandardCharsets.UTF_8);
            byte[] enc1 = service.encrypt(plaintext);
            byte[] enc2 = service.encrypt(plaintext);
            assertThat(enc1).isNotEqualTo(enc2); // Different IV each time
        }

        @Test
        @DisplayName("should throw NullPointerException when encrypting null")
        void shouldThrowForNullEncryptInput() {
            assertThatThrownBy(() -> service.encrypt(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should throw NullPointerException when decrypting null")
        void shouldThrowForNullDecryptInput() {
            assertThatThrownBy(() -> service.decrypt(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // =========================================================================
    // TAMPER DETECTION (AES-GCM authentication tag)
    // =========================================================================

    @Nested
    @DisplayName("Tamper Detection")
    class TamperDetection {

        @Test
        @DisplayName("should throw EncryptionException when ciphertext is too short")
        void shouldRejectTooShortCiphertext() {
            byte[] tooShort = new byte[5];
            assertThatThrownBy(() -> service.decrypt(tooShort))
                    .isInstanceOf(SimpleEncryptionService.EncryptionException.class);
        }

        @Test
        @DisplayName("should throw EncryptionException when ciphertext is tampered")
        void shouldDetectTamperedCiphertext() {
            byte[] plaintext = "important data".getBytes(StandardCharsets.UTF_8);
            byte[] encrypted = service.encrypt(plaintext);
            // Flip a bit in the ciphertext (after the IV)
            encrypted[15] ^= 0xFF;
            assertThatThrownBy(() -> service.decrypt(encrypted))
                    .isInstanceOf(SimpleEncryptionService.EncryptionException.class);
        }

        @Test
        @DisplayName("should throw EncryptionException when decrypting with a different key")
        void shouldRejectDifferentKey() {
            byte[] plaintext = "secret".getBytes(StandardCharsets.UTF_8);
            byte[] encrypted = service.encrypt(plaintext);

            String otherKey = SimpleEncryptionService.generateKey();
            SimpleEncryptionService otherService = new SimpleEncryptionService(otherKey);
            assertThatThrownBy(() -> otherService.decrypt(encrypted))
                    .isInstanceOf(SimpleEncryptionService.EncryptionException.class);
        }
    }

    // =========================================================================
    // fromEnvironment FACTORY
    // =========================================================================

    @Nested
    @DisplayName("fromEnvironment factory")
    class FromEnvironment {

        @Test
        @DisplayName("should create service when env var is absent (ephemeral key fallback)")
        void shouldCreateServiceWithEphemeralKeyWhenEnvVarAbsent() {
            // Non-existent env var → ephemeral key path
            assertThatCode(() -> SimpleEncryptionService.fromEnvironment("__NONEXISTENT_TEST_VAR_XYZ__"))
                    .doesNotThrowAnyException();
        }
    }
}
