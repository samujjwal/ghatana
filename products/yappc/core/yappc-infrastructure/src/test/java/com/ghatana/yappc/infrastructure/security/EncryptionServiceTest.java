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
@DisplayName("EncryptionService [GH-90000]")
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
    @DisplayName("encrypt and decrypt [GH-90000]")
    class EncryptDecrypt {

        @Test
        @DisplayName("round-trip produces original plaintext [GH-90000]")
        void roundTripReturnsOriginalPlaintext() { // GH-90000
            String plaintext = "super-secret-value";

            String ciphertext = encryptionService.encrypt(plaintext); // GH-90000
            String decrypted  = encryptionService.decrypt(ciphertext); // GH-90000

            assertThat(decrypted).isEqualTo(plaintext); // GH-90000
        }

        @Test
        @DisplayName("encrypting the same plaintext twice produces different ciphertexts (random IV) [GH-90000]")
        void encryptingTwiceProducesDifferentCiphertexts() { // GH-90000
            String plaintext  = "idempotency-test";
            String ciphertext1 = encryptionService.encrypt(plaintext); // GH-90000
            String ciphertext2 = encryptionService.encrypt(plaintext); // GH-90000

            assertThat(ciphertext1).isNotEqualTo(ciphertext2); // GH-90000
        }

        @Test
        @DisplayName("encrypts empty string without error [GH-90000]")
        void encryptsEmptyString() { // GH-90000
            String ciphertext = encryptionService.encrypt(" [GH-90000]");
            String decrypted  = encryptionService.decrypt(ciphertext); // GH-90000

            assertThat(decrypted).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("encrypts unicode plaintext correctly [GH-90000]")
        void encryptsUnicodePlaintext() { // GH-90000
            String plaintext = "日本語テスト — αβγ — emoji 🔑🔒";

            String decrypted = encryptionService.decrypt(encryptionService.encrypt(plaintext)); // GH-90000

            assertThat(decrypted).isEqualTo(plaintext); // GH-90000
        }

        @Test
        @DisplayName("ciphertext is valid Base64 [GH-90000]")
        void ciphertextIsBase64() { // GH-90000
            String ciphertext = encryptionService.encrypt("test-data [GH-90000]");

            assertThat(ciphertext).matches("^[A-Za-z0-9+/]+=*$ [GH-90000]");
        }

        @Test
        @DisplayName("encrypt throws on null plaintext [GH-90000]")
        void encryptThrowsOnNull() { // GH-90000
            assertThatThrownBy(() -> encryptionService.encrypt(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("decrypt throws on null ciphertext [GH-90000]")
        void decryptThrowsOnNull() { // GH-90000
            assertThatThrownBy(() -> encryptionService.decrypt(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("decrypt throws EncryptionException on corrupt ciphertext [GH-90000]")
        void decryptThrowsOnCorruptCiphertext() { // GH-90000
            assertThatThrownBy(() -> encryptionService.decrypt("not-valid-b64!!!! [GH-90000]"))
                    .isInstanceOf(EncryptionService.EncryptionException.class); // GH-90000
        }

        @Test
        @DisplayName("decrypt throws EncryptionException when ciphertext is too short [GH-90000]")
        void decryptThrowsWhenCiphertextTooShort() { // GH-90000
            // 8 bytes encoded — shorter than the 12-byte IV minimum
            String tooShort = Base64.getEncoder().encodeToString(new byte[8]); // GH-90000
            assertThatThrownBy(() -> encryptionService.decrypt(tooShort)) // GH-90000
                    .isInstanceOf(EncryptionService.EncryptionException.class); // GH-90000
        }

        @Test
        @DisplayName("decrypt throws when ciphertext was encrypted with a different key [GH-90000]")
        void decryptFailsWithWrongKey() { // GH-90000
            // Encrypt with a different service instance
            String anotherKey = EncryptionService.generateKey(); // GH-90000
            EncryptionService other = new EncryptionService(Base64.getDecoder().decode(anotherKey)); // GH-90000
            String ciphertext = other.encrypt("original [GH-90000]");

            assertThatThrownBy(() -> encryptionService.decrypt(ciphertext)) // GH-90000
                    .isInstanceOf(EncryptionService.EncryptionException.class); // GH-90000
        }
    }

    @Nested
    @DisplayName("constructor validation [GH-90000]")
    class ConstructorValidation {

        @Test
        @DisplayName("throws when key is less than 32 bytes [GH-90000]")
        void throwsOnShortKey() { // GH-90000
            assertThatThrownBy(() -> new EncryptionService(new byte[16])) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("32 bytes [GH-90000]");
        }

        @Test
        @DisplayName("throws when key is more than 32 bytes [GH-90000]")
        void throwsOnLongKey() { // GH-90000
            assertThatThrownBy(() -> new EncryptionService(new byte[64])) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("32 bytes [GH-90000]");
        }

        @Test
        @DisplayName("throws on null key bytes [GH-90000]")
        void throwsOnNullKey() { // GH-90000
            assertThatThrownBy(() -> new EncryptionService(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }

    @Nested
    @DisplayName("generateKey [GH-90000]")
    class GenerateKey {

        @Test
        @DisplayName("returns a valid Base64 string [GH-90000]")
        void returnsValidBase64() { // GH-90000
            String key = EncryptionService.generateKey(); // GH-90000
            assertThat(key).isNotBlank(); // GH-90000
            assertThat(key).matches("^[A-Za-z0-9+/]+=*$ [GH-90000]");
        }

        @Test
        @DisplayName("returns a 256-bit (32-byte) key [GH-90000]")
        void returns256BitKey() { // GH-90000
            String key    = EncryptionService.generateKey(); // GH-90000
            byte[] decoded = Base64.getDecoder().decode(key); // GH-90000
            assertThat(decoded).hasSize(32); // GH-90000
        }

        @Test
        @DisplayName("each call returns a different key [GH-90000]")
        void eachCallReturnsDifferentKey() { // GH-90000
            String key1 = EncryptionService.generateKey(); // GH-90000
            String key2 = EncryptionService.generateKey(); // GH-90000
            assertThat(key1).isNotEqualTo(key2); // GH-90000
        }
    }

    @Nested
    @DisplayName("fromEnvironment [GH-90000]")
    class FromEnvironment {

        @Test
        @DisplayName("throws IllegalStateException when env var is absent [GH-90000]")
        void throwsWhenEnvVarAbsent() { // GH-90000
            // YAPPC_ENCRYPTION_KEY is not set in test JVM by default
            // If it is set in CI, this test will still verify the key is valid
            // so we verify the exception only when the var is actually absent.
            String envKey = System.getenv("YAPPC_ENCRYPTION_KEY [GH-90000]");
            if (envKey != null && !envKey.isBlank()) { // GH-90000
                // env var present — fromEnvironment should succeed
                EncryptionService svc = EncryptionService.fromEnvironment(); // GH-90000
                assertThat(svc).isNotNull(); // GH-90000
            } else {
                assertThatThrownBy(EncryptionService::fromEnvironment) // GH-90000
                        .isInstanceOf(IllegalStateException.class) // GH-90000
                        .hasMessageContaining("YAPPC_ENCRYPTION_KEY [GH-90000]");
            }
        }
    }

    @Nested
    @DisplayName("fromConfiguredSources [GH-90000]")
    class FromConfiguredSources {

        @Test
        @DisplayName("prefers secret provider key over legacy env key [GH-90000]")
        void prefersSecretProviderOverLegacyEnvKey() { // GH-90000
            String providerKey = EncryptionService.generateKey(); // GH-90000
            String legacyKey = EncryptionService.generateKey(); // GH-90000

            EncryptionService.SecretProvider provider = secretName -> Optional.of(providerKey); // GH-90000
            EncryptionService service = EncryptionService.fromConfiguredSources( // GH-90000
                    provider,
                    "yappc/encryption-key",
                    legacyKey,
                    true);

            String ciphertext = service.encrypt("provider-first [GH-90000]");
            assertThat(service.decrypt(ciphertext)).isEqualTo("provider-first [GH-90000]");
        }

        @Test
        @DisplayName("uses legacy env key when enabled and secret provider has no key [GH-90000]")
        void usesLegacyEnvFallbackWhenEnabled() { // GH-90000
            String legacyKey = EncryptionService.generateKey(); // GH-90000

            EncryptionService service = EncryptionService.fromConfiguredSources( // GH-90000
                    secretName -> Optional.empty(), // GH-90000
                    "yappc/encryption-key",
                    legacyKey,
                    true);

            String ciphertext = service.encrypt("legacy-fallback [GH-90000]");
            assertThat(service.decrypt(ciphertext)).isEqualTo("legacy-fallback [GH-90000]");
        }

        @Test
        @DisplayName("returns empty when no source is configured [GH-90000]")
        void returnsEmptyWhenNoSourceConfigured() { // GH-90000
            Optional<EncryptionService> service = EncryptionService.tryFromConfiguredSources( // GH-90000
                    secretName -> Optional.empty(), // GH-90000
                    "yappc/encryption-key",
                    null,
                    false);

            assertThat(service).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("throws when secret value is not valid Base64 [GH-90000]")
        void throwsOnInvalidBase64Secret() { // GH-90000
            assertThatThrownBy(() -> EncryptionService.fromConfiguredSources( // GH-90000
                    secretName -> Optional.of("not-base64!! [GH-90000]"),
                    "yappc/encryption-key",
                    null,
                    false))
                    .isInstanceOf(IllegalStateException.class) // GH-90000
                    .hasMessageContaining("Invalid Base64 [GH-90000]");
        }
    }
}
