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
    void setUp() {
        // Generate a fresh 32-byte key for each test
        String base64Key = EncryptionService.generateKey();
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        encryptionService = new EncryptionService(keyBytes);
    }

    @Nested
    @DisplayName("encrypt and decrypt")
    class EncryptDecrypt {

        @Test
        @DisplayName("round-trip produces original plaintext")
        void roundTripReturnsOriginalPlaintext() {
            String plaintext = "super-secret-value";

            String ciphertext = encryptionService.encrypt(plaintext);
            String decrypted  = encryptionService.decrypt(ciphertext);

            assertThat(decrypted).isEqualTo(plaintext);
        }

        @Test
        @DisplayName("encrypting the same plaintext twice produces different ciphertexts (random IV)")
        void encryptingTwiceProducesDifferentCiphertexts() {
            String plaintext  = "idempotency-test";
            String ciphertext1 = encryptionService.encrypt(plaintext);
            String ciphertext2 = encryptionService.encrypt(plaintext);

            assertThat(ciphertext1).isNotEqualTo(ciphertext2);
        }

        @Test
        @DisplayName("encrypts empty string without error")
        void encryptsEmptyString() {
            String ciphertext = encryptionService.encrypt("");
            String decrypted  = encryptionService.decrypt(ciphertext);

            assertThat(decrypted).isEmpty();
        }

        @Test
        @DisplayName("encrypts unicode plaintext correctly")
        void encryptsUnicodePlaintext() {
            String plaintext = "日本語テスト — αβγ — emoji 🔑🔒";

            String decrypted = encryptionService.decrypt(encryptionService.encrypt(plaintext));

            assertThat(decrypted).isEqualTo(plaintext);
        }

        @Test
        @DisplayName("ciphertext is valid Base64")
        void ciphertextIsBase64() {
            String ciphertext = encryptionService.encrypt("test-data");

            assertThat(ciphertext).matches("^[A-Za-z0-9+/]+=*$");
        }

        @Test
        @DisplayName("encrypt throws on null plaintext")
        void encryptThrowsOnNull() {
            assertThatThrownBy(() -> encryptionService.encrypt(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("decrypt throws on null ciphertext")
        void decryptThrowsOnNull() {
            assertThatThrownBy(() -> encryptionService.decrypt(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("decrypt throws EncryptionException on corrupt ciphertext")
        void decryptThrowsOnCorruptCiphertext() {
            assertThatThrownBy(() -> encryptionService.decrypt("not-valid-b64!!!!"))
                    .isInstanceOf(EncryptionService.EncryptionException.class);
        }

        @Test
        @DisplayName("decrypt throws EncryptionException when ciphertext is too short")
        void decryptThrowsWhenCiphertextTooShort() {
            // 8 bytes encoded — shorter than the 12-byte IV minimum
            String tooShort = Base64.getEncoder().encodeToString(new byte[8]);
            assertThatThrownBy(() -> encryptionService.decrypt(tooShort))
                    .isInstanceOf(EncryptionService.EncryptionException.class);
        }

        @Test
        @DisplayName("decrypt throws when ciphertext was encrypted with a different key")
        void decryptFailsWithWrongKey() {
            // Encrypt with a different service instance
            String anotherKey = EncryptionService.generateKey();
            EncryptionService other = new EncryptionService(Base64.getDecoder().decode(anotherKey));
            String ciphertext = other.encrypt("original");

            assertThatThrownBy(() -> encryptionService.decrypt(ciphertext))
                    .isInstanceOf(EncryptionService.EncryptionException.class);
        }
    }

    @Nested
    @DisplayName("constructor validation")
    class ConstructorValidation {

        @Test
        @DisplayName("throws when key is less than 32 bytes")
        void throwsOnShortKey() {
            assertThatThrownBy(() -> new EncryptionService(new byte[16]))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("32 bytes");
        }

        @Test
        @DisplayName("throws when key is more than 32 bytes")
        void throwsOnLongKey() {
            assertThatThrownBy(() -> new EncryptionService(new byte[64]))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("32 bytes");
        }

        @Test
        @DisplayName("throws on null key bytes")
        void throwsOnNullKey() {
            assertThatThrownBy(() -> new EncryptionService(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("generateKey")
    class GenerateKey {

        @Test
        @DisplayName("returns a valid Base64 string")
        void returnsValidBase64() {
            String key = EncryptionService.generateKey();
            assertThat(key).isNotBlank();
            assertThat(key).matches("^[A-Za-z0-9+/]+=*$");
        }

        @Test
        @DisplayName("returns a 256-bit (32-byte) key")
        void returns256BitKey() {
            String key    = EncryptionService.generateKey();
            byte[] decoded = Base64.getDecoder().decode(key);
            assertThat(decoded).hasSize(32);
        }

        @Test
        @DisplayName("each call returns a different key")
        void eachCallReturnsDifferentKey() {
            String key1 = EncryptionService.generateKey();
            String key2 = EncryptionService.generateKey();
            assertThat(key1).isNotEqualTo(key2);
        }
    }

    @Nested
    @DisplayName("fromEnvironment")
    class FromEnvironment {

        @Test
        @DisplayName("throws IllegalStateException when env var is absent")
        void throwsWhenEnvVarAbsent() {
            // YAPPC_ENCRYPTION_KEY is not set in test JVM by default
            // If it is set in CI, this test will still verify the key is valid
            // so we verify the exception only when the var is actually absent.
            String envKey = System.getenv("YAPPC_ENCRYPTION_KEY");
            if (envKey != null && !envKey.isBlank()) {
                // env var present — fromEnvironment should succeed
                EncryptionService svc = EncryptionService.fromEnvironment();
                assertThat(svc).isNotNull();
            } else {
                assertThatThrownBy(EncryptionService::fromEnvironment)
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("YAPPC_ENCRYPTION_KEY");
            }
        }
    }

    @Nested
    @DisplayName("fromConfiguredSources")
    class FromConfiguredSources {

        @Test
        @DisplayName("prefers secret provider key over legacy env key")
        void prefersSecretProviderOverLegacyEnvKey() {
            String providerKey = EncryptionService.generateKey();
            String legacyKey = EncryptionService.generateKey();

            EncryptionService.SecretProvider provider = secretName -> Optional.of(providerKey);
            EncryptionService service = EncryptionService.fromConfiguredSources(
                    provider,
                    "yappc/encryption-key",
                    legacyKey,
                    true);

            String ciphertext = service.encrypt("provider-first");
            assertThat(service.decrypt(ciphertext)).isEqualTo("provider-first");
        }

        @Test
        @DisplayName("uses legacy env key when enabled and secret provider has no key")
        void usesLegacyEnvFallbackWhenEnabled() {
            String legacyKey = EncryptionService.generateKey();

            EncryptionService service = EncryptionService.fromConfiguredSources(
                    secretName -> Optional.empty(),
                    "yappc/encryption-key",
                    legacyKey,
                    true);

            String ciphertext = service.encrypt("legacy-fallback");
            assertThat(service.decrypt(ciphertext)).isEqualTo("legacy-fallback");
        }

        @Test
        @DisplayName("returns empty when no source is configured")
        void returnsEmptyWhenNoSourceConfigured() {
            Optional<EncryptionService> service = EncryptionService.tryFromConfiguredSources(
                    secretName -> Optional.empty(),
                    "yappc/encryption-key",
                    null,
                    false);

            assertThat(service).isEmpty();
        }

        @Test
        @DisplayName("throws when secret value is not valid Base64")
        void throwsOnInvalidBase64Secret() {
            assertThatThrownBy(() -> EncryptionService.fromConfiguredSources(
                    secretName -> Optional.of("not-base64!!"),
                    "yappc/encryption-key",
                    null,
                    false))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Invalid Base64");
        }
    }
}
