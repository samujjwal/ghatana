package com.ghatana.platform.security.encryption;

import com.ghatana.platform.security.encryption.impl.AesGcmEncryptionProvider;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.activej.promise.Promise;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link AesGcmEncryptionProvider} and encryption round-trips.
 *
 * @doc.type class
 * @doc.purpose AES-GCM encryption/decryption round-trip tests
 * @doc.layer core
 * @doc.pattern Unit Test
 */
@DisplayName("AesGcmEncryptionProvider")
class AesGcmEncryptionProviderTest extends EventloopTestBase {

    private AesGcmEncryptionProvider provider;

    @BeforeEach
    void setUpProvider() { // GH-90000
        provider = AesGcmEncryptionProvider.withNewKey(256, "test-key-1"); // GH-90000
    }

    @Nested
    @DisplayName("encrypt/decrypt round-trip")
    class RoundTrip {

        @Test
        @DisplayName("should encrypt and decrypt text data")
        void shouldRoundTripText() { // GH-90000
            byte[] plaintext = "Hello, World!".getBytes(StandardCharsets.UTF_8); // GH-90000

            byte[] encrypted = runPromise(() -> provider.encrypt(plaintext)); // GH-90000
            byte[] decrypted = runPromise(() -> provider.decrypt(encrypted)); // GH-90000

            assertThat(decrypted).isEqualTo(plaintext); // GH-90000
            assertThat(new String(decrypted, StandardCharsets.UTF_8)).isEqualTo("Hello, World!");
        }

        @Test
        @DisplayName("should encrypt and decrypt empty data")
        void shouldRoundTripEmpty() { // GH-90000
            byte[] plaintext = new byte[0];

            byte[] encrypted = runPromise(() -> provider.encrypt(plaintext)); // GH-90000
            byte[] decrypted = runPromise(() -> provider.decrypt(encrypted)); // GH-90000

            assertThat(decrypted).isEqualTo(plaintext); // GH-90000
        }

        @Test
        @DisplayName("should encrypt and decrypt large data")
        void shouldRoundTripLargeData() { // GH-90000
            byte[] plaintext = new byte[1024 * 1024]; // 1 MB
            Arrays.fill(plaintext, (byte) 0xAB); // GH-90000

            byte[] encrypted = runPromise(() -> provider.encrypt(plaintext)); // GH-90000
            byte[] decrypted = runPromise(() -> provider.decrypt(encrypted)); // GH-90000

            assertThat(decrypted).isEqualTo(plaintext); // GH-90000
        }

        @Test
        @DisplayName("should produce different ciphertext for same plaintext (random IV)")
        void shouldUseDifferentIVs() { // GH-90000
            byte[] plaintext = "deterministic?".getBytes(StandardCharsets.UTF_8); // GH-90000

            byte[] encrypted1 = runPromise(() -> provider.encrypt(plaintext)); // GH-90000
            byte[] encrypted2 = runPromise(() -> provider.encrypt(plaintext)); // GH-90000

            assertThat(encrypted1).isNotEqualTo(encrypted2); // GH-90000
        }
    }

    @Nested
    @DisplayName("decrypt failures")
    class DecryptFailures {

        @Test
        @DisplayName("should fail to decrypt tampered ciphertext")
        void shouldFailOnTamperedData() { // GH-90000
            byte[] plaintext = "secret".getBytes(StandardCharsets.UTF_8); // GH-90000
            byte[] encrypted = runPromise(() -> provider.encrypt(plaintext)); // GH-90000

            // Tamper with the last byte
            encrypted[encrypted.length - 1] ^= (byte) 0xFF; // GH-90000

            byte[] tampered = encrypted;
            runPromise(() -> provider.decrypt(tampered) // GH-90000
                    .then( // GH-90000
                            result -> Promise.<byte[]>ofException(new RuntimeException("Should have failed")),
                            e -> {
                                assertThat(e).isNotNull(); // GH-90000
                                return Promise.of((byte[]) null); // GH-90000
                            }));
            clearFatalError(); // GH-90000
        }

        @Test
        @DisplayName("should fail to decrypt data with different key")
        void shouldFailWithDifferentKey() { // GH-90000
            byte[] plaintext = "secret".getBytes(StandardCharsets.UTF_8); // GH-90000
            byte[] encrypted = runPromise(() -> provider.encrypt(plaintext)); // GH-90000

            AesGcmEncryptionProvider otherProvider = AesGcmEncryptionProvider.withNewKey(256, "other-key"); // GH-90000

            runPromise(() -> otherProvider.decrypt(encrypted) // GH-90000
                    .then( // GH-90000
                            result -> Promise.<byte[]>ofException(new RuntimeException("Should have failed")),
                            e -> {
                                assertThat(e).isNotNull(); // GH-90000
                                return Promise.of((byte[]) null); // GH-90000
                            }));
            clearFatalError(); // GH-90000
        }
    }

    @Nested
    @DisplayName("factory methods")
    class FactoryMethods {

        @Test
        @DisplayName("should generate provider with 128-bit key")
        void shouldGenerate128BitKey() { // GH-90000
            AesGcmEncryptionProvider p = AesGcmEncryptionProvider.withNewKey(128, "k-128"); // GH-90000
            assertThat(p.getAlgorithm()).isEqualTo("AES/GCM/NoPadding");
            assertThat(p.getKeyId()).isEqualTo("k-128");
        }

        @Test
        @DisplayName("should generate provider with 256-bit key")
        void shouldGenerate256BitKey() { // GH-90000
            AesGcmEncryptionProvider p = AesGcmEncryptionProvider.withNewKey(256, "k-256"); // GH-90000
            assertThat(p.getAlgorithm()).isEqualTo("AES/GCM/NoPadding");
            assertThat(p.getKeyId()).isEqualTo("k-256");
        }
    }
}
