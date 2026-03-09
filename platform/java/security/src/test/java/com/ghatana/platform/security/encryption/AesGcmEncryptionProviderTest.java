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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void setUpProvider() {
        provider = AesGcmEncryptionProvider.withNewKey(256, "test-key-1");
    }

    @Nested
    @DisplayName("encrypt/decrypt round-trip")
    class RoundTrip {

        @Test
        @DisplayName("should encrypt and decrypt text data")
        void shouldRoundTripText() {
            byte[] plaintext = "Hello, World!".getBytes(StandardCharsets.UTF_8);

            byte[] encrypted = runPromise(() -> provider.encrypt(plaintext));
            byte[] decrypted = runPromise(() -> provider.decrypt(encrypted));

            assertThat(decrypted).isEqualTo(plaintext);
            assertThat(new String(decrypted, StandardCharsets.UTF_8)).isEqualTo("Hello, World!");
        }

        @Test
        @DisplayName("should encrypt and decrypt empty data")
        void shouldRoundTripEmpty() {
            byte[] plaintext = new byte[0];

            byte[] encrypted = runPromise(() -> provider.encrypt(plaintext));
            byte[] decrypted = runPromise(() -> provider.decrypt(encrypted));

            assertThat(decrypted).isEqualTo(plaintext);
        }

        @Test
        @DisplayName("should encrypt and decrypt large data")
        void shouldRoundTripLargeData() {
            byte[] plaintext = new byte[1024 * 1024]; // 1 MB
            Arrays.fill(plaintext, (byte) 0xAB);

            byte[] encrypted = runPromise(() -> provider.encrypt(plaintext));
            byte[] decrypted = runPromise(() -> provider.decrypt(encrypted));

            assertThat(decrypted).isEqualTo(plaintext);
        }

        @Test
        @DisplayName("should produce different ciphertext for same plaintext (random IV)")
        void shouldUseDifferentIVs() {
            byte[] plaintext = "deterministic?".getBytes(StandardCharsets.UTF_8);

            byte[] encrypted1 = runPromise(() -> provider.encrypt(plaintext));
            byte[] encrypted2 = runPromise(() -> provider.encrypt(plaintext));

            assertThat(encrypted1).isNotEqualTo(encrypted2);
        }
    }

    @Nested
    @DisplayName("decrypt failures")
    class DecryptFailures {

        @Test
        @DisplayName("should fail to decrypt tampered ciphertext")
        void shouldFailOnTamperedData() {
            byte[] plaintext = "secret".getBytes(StandardCharsets.UTF_8);
            byte[] encrypted = runPromise(() -> provider.encrypt(plaintext));

            // Tamper with the last byte
            encrypted[encrypted.length - 1] ^= (byte) 0xFF;

            byte[] tampered = encrypted;
            runPromise(() -> provider.decrypt(tampered)
                    .then(
                            result -> Promise.<byte[]>ofException(new RuntimeException("Should have failed")),
                            e -> {
                                assertThat(e).isNotNull();
                                return Promise.of((byte[]) null);
                            }));
            clearFatalError();
        }

        @Test
        @DisplayName("should fail to decrypt data with different key")
        void shouldFailWithDifferentKey() {
            byte[] plaintext = "secret".getBytes(StandardCharsets.UTF_8);
            byte[] encrypted = runPromise(() -> provider.encrypt(plaintext));

            AesGcmEncryptionProvider otherProvider = AesGcmEncryptionProvider.withNewKey(256, "other-key");

            runPromise(() -> otherProvider.decrypt(encrypted)
                    .then(
                            result -> Promise.<byte[]>ofException(new RuntimeException("Should have failed")),
                            e -> {
                                assertThat(e).isNotNull();
                                return Promise.of((byte[]) null);
                            }));
            clearFatalError();
        }
    }

    @Nested
    @DisplayName("factory methods")
    class FactoryMethods {

        @Test
        @DisplayName("should generate provider with 128-bit key")
        void shouldGenerate128BitKey() {
            AesGcmEncryptionProvider p = AesGcmEncryptionProvider.withNewKey(128, "k-128");
            assertThat(p.getAlgorithm()).isEqualTo("AES/GCM/NoPadding");
            assertThat(p.getKeyId()).isEqualTo("k-128");
        }

        @Test
        @DisplayName("should generate provider with 256-bit key")
        void shouldGenerate256BitKey() {
            AesGcmEncryptionProvider p = AesGcmEncryptionProvider.withNewKey(256, "k-256");
            assertThat(p.getAlgorithm()).isEqualTo("AES/GCM/NoPadding");
            assertThat(p.getKeyId()).isEqualTo("k-256");
        }
    }
}
