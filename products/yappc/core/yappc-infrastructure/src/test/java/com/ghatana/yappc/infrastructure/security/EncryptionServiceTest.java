package com.ghatana.yappc.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link EncryptionService}.
 *
 * <p>Covers AES-256-GCM round-trip, IV randomness, key validation, corrupt-data
 * handling, and environment-key loading errors. No I/O or async code; no
 * {@code EventloopTestBase} needed.
 */
@DisplayName("EncryptionService")
class EncryptionServiceTest {

    private EncryptionService encryptionService;

    @BeforeEach
    void setUp() { // GH-90000
        // Generate a fresh 32-byte key for each test
        String base64Key = EncryptionService.generateKey(); // GH-90000
        byte[] keyBytes = Base64.getDecoder().decode(base64Key); // GH-90000
        encryptionService = new EncryptionService(keyBytes); // GH-90000
    }

    @Nested
    @DisplayName("encrypt and decrypt")
    class EncryptDecrypt {

        @Test
        @DisplayName("round-trip produces original plaintext")
        void roundTripReturnsOriginalPlaintext() { // GH-90000
            String plaintext = "super-secret-value";

            String ciphertext = encryptionService.encrypt(plaintext); // GH-90000
            String decrypted  = encryptionService.decrypt(ciphertext); // GH-90000

            assertThat(decrypted).isEqualTo(plaintext); // GH-90000
        }

        @Test
        @DisplayName("encrypting the same plaintext twice produces different ciphertexts (random IV)")
        void encryptingTwiceProducesDifferentCiphertexts() { // GH-90000
            String plaintext  = "idempotency-test";
            String ciphertext1 = encryptionService.encrypt(plaintext); // GH-90000
            String ciphertext2 = encryptionService.encrypt(plaintext); // GH-90000

            assertThat(ciphertext1).isNotEqualTo(ciphertext2); // GH-90000
        }

        @Test
        @DisplayName("encrypts empty string without error")
        void encryptsEmptyString() { // GH-90000
            String ciphertext = encryptionService.encrypt("");
            String decrypted  = encryptionService.decrypt(ciphertext); // GH-90000

            assertThat(decrypted).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("encrypts unicode plaintext correctly")
        void encryptsUnicodePlaintext() { // GH-90000
            String plaintext = "日本語テスト — αβγ — emoji 🔑🔒";

            String decrypted = encryptionService.decrypt(encryptionService.encrypt(plaintext)); // GH-90000

            assertThat(decrypted).isEqualTo(plaintext); // GH-90000
        }

        @Test
        @DisplayName("ciphertext is valid Base64")
        void ciphertextIsBase64() { // GH-90000
            String ciphertext = encryptionService.encrypt("test-data");

            assertThat(ciphertext).matches("^[A-Za-z0-9+/]+=*$");
        }

        @Test
        @DisplayName("encrypt throws on null plaintext")
        void encryptThrowsOnNull() { // GH-90000
            assertThatThrownBy(() -> encryptionService.encrypt(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("decrypt throws on null ciphertext")
        void decryptThrowsOnNull() { // GH-90000
            assertThatThrownBy(() -> encryptionService.decrypt(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("decrypt throws EncryptionException on corrupt ciphertext")
        void decryptThrowsOnCorruptCiphertext() { // GH-90000
            assertThatThrownBy(() -> encryptionService.decrypt("not-valid-b64!!!!"))
                    .isInstanceOf(EncryptionService.EncryptionException.class); // GH-90000
        }

        @Test
        @DisplayName("decrypt throws EncryptionException when ciphertext is too short")
        void decryptThrowsWhenCiphertextTooShort() { // GH-90000
            // 8 bytes encoded — shorter than the 12-byte IV minimum
            String tooShort = Base64.getEncoder().encodeToString(new byte[8]); // GH-90000
            assertThatThrownBy(() -> encryptionService.decrypt(tooShort)) // GH-90000
                    .isInstanceOf(EncryptionService.EncryptionException.class); // GH-90000
        }

        @Test
        @DisplayName("decrypt throws when ciphertext was encrypted with a different key")
        void decryptFailsWithWrongKey() { // GH-90000
            // Encrypt with a different service instance
            String anotherKey = EncryptionService.generateKey(); // GH-90000
            EncryptionService other = new EncryptionService(Base64.getDecoder().decode(anotherKey)); // GH-90000
            String ciphertext = other.encrypt("original");

            assertThatThrownBy(() -> encryptionService.decrypt(ciphertext)) // GH-90000
                    .isInstanceOf(EncryptionService.EncryptionException.class); // GH-90000
        }
    }

    @Nested
    @DisplayName("constructor validation")
    class ConstructorValidation {

        @Test
        @DisplayName("throws when key is less than 32 bytes")
        void throwsOnShortKey() { // GH-90000
            assertThatThrownBy(() -> new EncryptionService(new byte[16])) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("32 bytes");
        }

        @Test
        @DisplayName("throws when key is more than 32 bytes")
        void throwsOnLongKey() { // GH-90000
            assertThatThrownBy(() -> new EncryptionService(new byte[64])) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("32 bytes");
        }

        @Test
        @DisplayName("throws on null key bytes")
        void throwsOnNullKey() { // GH-90000
            assertThatThrownBy(() -> new EncryptionService(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }

    @Nested
    @DisplayName("generateKey")
    class GenerateKey {

        @Test
        @DisplayName("returns a valid Base64 string")
        void returnsValidBase64() { // GH-90000
            String key = EncryptionService.generateKey(); // GH-90000
            assertThat(key).isNotBlank(); // GH-90000
            assertThat(key).matches("^[A-Za-z0-9+/]+=*$");
        }

        @Test
        @DisplayName("returns a 256-bit (32-byte) key")
        void returns256BitKey() { // GH-90000
            String key    = EncryptionService.generateKey(); // GH-90000
            byte[] decoded = Base64.getDecoder().decode(key); // GH-90000
            assertThat(decoded).hasSize(32); // GH-90000
        }

        @Test
        @DisplayName("each call returns a different key")
        void eachCallReturnsDifferentKey() { // GH-90000
            String key1 = EncryptionService.generateKey(); // GH-90000
            String key2 = EncryptionService.generateKey(); // GH-90000
            assertThat(key1).isNotEqualTo(key2); // GH-90000
        }
    }

    @Nested
    @DisplayName("fromEnvironment")
    class FromEnvironment {

        @Test
        @DisplayName("throws IllegalStateException when env var is absent")
        void throwsWhenEnvVarAbsent() { // GH-90000
            // YAPPC_ENCRYPTION_KEY is not set in test JVM by default
            // If it is set in CI, this test will still verify the key is valid
            // so we verify the exception only when the var is actually absent.
            String envKey = System.getenv("YAPPC_ENCRYPTION_KEY");
            if (envKey != null && !envKey.isBlank()) { // GH-90000
                // env var present — fromEnvironment should succeed
                EncryptionService svc = EncryptionService.fromEnvironment(); // GH-90000
                assertThat(svc).isNotNull(); // GH-90000
            } else {
                assertThatThrownBy(EncryptionService::fromEnvironment) // GH-90000
                        .isInstanceOf(IllegalStateException.class) // GH-90000
                        .hasMessageContaining("YAPPC_ENCRYPTION_KEY");
            }
        }
    }

    @Nested
    @DisplayName("fromConfiguredSources")
    class FromConfiguredSources {

        @Test
        @DisplayName("prefers secret provider key over legacy env key")
        void prefersSecretProviderOverLegacyEnvKey() { // GH-90000
            String providerKey = EncryptionService.generateKey(); // GH-90000
            String legacyKey = EncryptionService.generateKey(); // GH-90000

            EncryptionService.SecretProvider provider = secretName -> Optional.of(providerKey); // GH-90000
            EncryptionService service = EncryptionService.fromConfiguredSources( // GH-90000
                    provider,
                    "yappc/encryption-key",
                    legacyKey,
                    true);

            String ciphertext = service.encrypt("provider-first");
            assertThat(service.decrypt(ciphertext)).isEqualTo("provider-first");
        }

        @Test
        @DisplayName("uses legacy env key when enabled and secret provider has no key")
        void usesLegacyEnvFallbackWhenEnabled() { // GH-90000
            String legacyKey = EncryptionService.generateKey(); // GH-90000

            EncryptionService service = EncryptionService.fromConfiguredSources( // GH-90000
                    secretName -> Optional.empty(), // GH-90000
                    "yappc/encryption-key",
                    legacyKey,
                    true);

            String ciphertext = service.encrypt("legacy-fallback");
            assertThat(service.decrypt(ciphertext)).isEqualTo("legacy-fallback");
        }

        @Test
        @DisplayName("returns empty when no source is configured")
        void returnsEmptyWhenNoSourceConfigured() { // GH-90000
            Optional<EncryptionService> service = EncryptionService.tryFromConfiguredSources( // GH-90000
                    secretName -> Optional.empty(), // GH-90000
                    "yappc/encryption-key",
                    null,
                    false);

            assertThat(service).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("throws when secret value is not valid Base64")
        void throwsOnInvalidBase64Secret() { // GH-90000
            assertThatThrownBy(() -> EncryptionService.fromConfiguredSources( // GH-90000
                    secretName -> Optional.of("not-base64!!"),
                    "yappc/encryption-key",
                    null,
                    false))
                    .isInstanceOf(IllegalStateException.class) // GH-90000
                    .hasMessageContaining("Invalid Base64");
        }
    }
}
