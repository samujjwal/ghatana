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
@DisplayName("SimpleEncryptionService Tests [GH-90000]")
class SimpleEncryptionServiceTest {

    private String validKey;
    private SimpleEncryptionService service;

    @BeforeEach
    void setUp() { // GH-90000
        validKey = SimpleEncryptionService.generateKey(); // GH-90000
        service = new SimpleEncryptionService(validKey); // GH-90000
    }

    // =========================================================================
    // CONSTRUCTION
    // =========================================================================

    @Nested
    @DisplayName("Construction [GH-90000]")
    class Construction {

        @Test
        @DisplayName("should create service with valid key [GH-90000]")
        void shouldCreateWithValidKey() { // GH-90000
            assertThatCode(() -> new SimpleEncryptionService(validKey)).doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("should throw NullPointerException for null key [GH-90000]")
        void shouldThrowForNullKey() { // GH-90000
            assertThatThrownBy(() -> new SimpleEncryptionService(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("should throw IllegalArgumentException for key with wrong length [GH-90000]")
        void shouldThrowForWrongKeyLength() { // GH-90000
            // 16-byte key (AES-128), not 32-byte (AES-256) // GH-90000
            byte[] shortKey = new byte[16];
            String shortBase64 = Base64.getEncoder().encodeToString(shortKey); // GH-90000
            assertThatThrownBy(() -> new SimpleEncryptionService(shortBase64)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("Invalid key length [GH-90000]");
        }

        @Test
        @DisplayName("should throw IllegalArgumentException for invalid base64 key [GH-90000]")
        void shouldThrowForInvalidBase64Key() { // GH-90000
            assertThatThrownBy(() -> new SimpleEncryptionService("not-valid-base64!!! [GH-90000]"))
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }
    }

    // =========================================================================
    // KEY GENERATION
    // =========================================================================

    @Nested
    @DisplayName("Key Generation [GH-90000]")
    class KeyGeneration {

        @Test
        @DisplayName("should generate a valid base64-encoded 32-byte key [GH-90000]")
        void shouldGenerateValidKey() { // GH-90000
            String key = SimpleEncryptionService.generateKey(); // GH-90000
            byte[] keyBytes = Base64.getDecoder().decode(key); // GH-90000
            assertThat(keyBytes).hasSize(32); // 256 bits // GH-90000
        }

        @Test
        @DisplayName("should generate unique keys on each call [GH-90000]")
        void shouldGenerateUniqueKeys() { // GH-90000
            String key1 = SimpleEncryptionService.generateKey(); // GH-90000
            String key2 = SimpleEncryptionService.generateKey(); // GH-90000
            assertThat(key1).isNotEqualTo(key2); // GH-90000
        }

        @Test
        @DisplayName("generated key should be usable with service constructor [GH-90000]")
        void generatedKeyShouldBeUsable() { // GH-90000
            String key = SimpleEncryptionService.generateKey(); // GH-90000
            assertThatCode(() -> new SimpleEncryptionService(key)).doesNotThrowAnyException(); // GH-90000
        }
    }

    // =========================================================================
    // ENCRYPT / DECRYPT ROUND TRIPS
    // =========================================================================

    @Nested
    @DisplayName("Encrypt and Decrypt [GH-90000]")
    class EncryptDecrypt {

        @Test
        @DisplayName("should encrypt and decrypt plaintext correctly [GH-90000]")
        void shouldEncryptAndDecrypt() { // GH-90000
            byte[] plaintext = "Hello, Data-Cloud!".getBytes(StandardCharsets.UTF_8); // GH-90000
            byte[] encrypted = service.encrypt(plaintext); // GH-90000
            byte[] decrypted = service.decrypt(encrypted); // GH-90000
            assertThat(decrypted).isEqualTo(plaintext); // GH-90000
        }

        @Test
        @DisplayName("should encrypt empty byte array [GH-90000]")
        void shouldEncryptEmptyArray() { // GH-90000
            byte[] plaintext = new byte[0];
            byte[] encrypted = service.encrypt(plaintext); // GH-90000
            byte[] decrypted = service.decrypt(encrypted); // GH-90000
            assertThat(decrypted).isEqualTo(plaintext); // GH-90000
        }

        @ParameterizedTest
        @DisplayName("should encrypt and decrypt various payloads [GH-90000]")
        @ValueSource(strings = {"a", "short", "medium-length payload for testing", "1234567890"}) // GH-90000
        void shouldEncryptVariousPayloads(String text) { // GH-90000
            byte[] plaintext = text.getBytes(StandardCharsets.UTF_8); // GH-90000
            byte[] encrypted = service.encrypt(plaintext); // GH-90000
            byte[] decrypted = service.decrypt(encrypted); // GH-90000
            assertThat(new String(decrypted, StandardCharsets.UTF_8)).isEqualTo(text); // GH-90000
        }

        @Test
        @DisplayName("should encrypt large payload correctly [GH-90000]")
        void shouldEncryptLargePayload() { // GH-90000
            byte[] bigPayload = new byte[1_000_000];
            new java.util.Random(42L).nextBytes(bigPayload); // GH-90000
            byte[] encrypted = service.encrypt(bigPayload); // GH-90000
            byte[] decrypted = service.decrypt(encrypted); // GH-90000
            assertThat(decrypted).isEqualTo(bigPayload); // GH-90000
        }

        @Test
        @DisplayName("encrypted output should differ from plaintext [GH-90000]")
        void encryptedShouldDifferFromPlaintext() { // GH-90000
            byte[] plaintext = "sensitive data".getBytes(StandardCharsets.UTF_8); // GH-90000
            byte[] encrypted = service.encrypt(plaintext); // GH-90000
            assertThat(encrypted).isNotEqualTo(plaintext); // GH-90000
        }

        @Test
        @DisplayName("same plaintext encrypted twice should produce different ciphertexts (IV randomness) [GH-90000]")
        void sameTextShouldProduceDifferentCiphertexts() { // GH-90000
            byte[] plaintext = "same data".getBytes(StandardCharsets.UTF_8); // GH-90000
            byte[] enc1 = service.encrypt(plaintext); // GH-90000
            byte[] enc2 = service.encrypt(plaintext); // GH-90000
            assertThat(enc1).isNotEqualTo(enc2); // Different IV each time // GH-90000
        }

        @Test
        @DisplayName("should throw NullPointerException when encrypting null [GH-90000]")
        void shouldThrowForNullEncryptInput() { // GH-90000
            assertThatThrownBy(() -> service.encrypt(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("should throw NullPointerException when decrypting null [GH-90000]")
        void shouldThrowForNullDecryptInput() { // GH-90000
            assertThatThrownBy(() -> service.decrypt(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }

    // =========================================================================
    // TAMPER DETECTION (AES-GCM authentication tag) // GH-90000
    // =========================================================================

    @Nested
    @DisplayName("Tamper Detection [GH-90000]")
    class TamperDetection {

        @Test
        @DisplayName("should throw EncryptionException when ciphertext is too short [GH-90000]")
        void shouldRejectTooShortCiphertext() { // GH-90000
            byte[] tooShort = new byte[5];
            assertThatThrownBy(() -> service.decrypt(tooShort)) // GH-90000
                    .isInstanceOf(SimpleEncryptionService.EncryptionException.class); // GH-90000
        }

        @Test
        @DisplayName("should throw EncryptionException when ciphertext is tampered [GH-90000]")
        void shouldDetectTamperedCiphertext() { // GH-90000
            byte[] plaintext = "important data".getBytes(StandardCharsets.UTF_8); // GH-90000
            byte[] encrypted = service.encrypt(plaintext); // GH-90000
            // Flip a bit in the ciphertext (after the IV) // GH-90000
            encrypted[15] ^= 0xFF;
            assertThatThrownBy(() -> service.decrypt(encrypted)) // GH-90000
                    .isInstanceOf(SimpleEncryptionService.EncryptionException.class); // GH-90000
        }

        @Test
        @DisplayName("should throw EncryptionException when decrypting with a different key [GH-90000]")
        void shouldRejectDifferentKey() { // GH-90000
            byte[] plaintext = "secret".getBytes(StandardCharsets.UTF_8); // GH-90000
            byte[] encrypted = service.encrypt(plaintext); // GH-90000

            String otherKey = SimpleEncryptionService.generateKey(); // GH-90000
            SimpleEncryptionService otherService = new SimpleEncryptionService(otherKey); // GH-90000
            assertThatThrownBy(() -> otherService.decrypt(encrypted)) // GH-90000
                    .isInstanceOf(SimpleEncryptionService.EncryptionException.class); // GH-90000
        }
    }

    // =========================================================================
    // fromEnvironment FACTORY
    // =========================================================================

    @Nested
    @DisplayName("fromEnvironment factory [GH-90000]")
    class FromEnvironment {

        @Test
        @DisplayName("should create service when env var is absent (ephemeral key fallback) [GH-90000]")
        void shouldCreateServiceWithEphemeralKeyWhenEnvVarAbsent() { // GH-90000
            // Non-existent env var → ephemeral key path
            assertThatCode(() -> SimpleEncryptionService.fromEnvironment("__NONEXISTENT_TEST_VAR_XYZ__ [GH-90000]"))
                    .doesNotThrowAnyException(); // GH-90000
        }
    }
}
