package com.ghatana.platform.security.encryption;

import com.ghatana.platform.security.encryption.impl.AesGcmEncryptionProvider;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link EncryptionService}.
 * Uses {@link EventloopTestBase} for async encryption operations.
 *
 * @doc.type class
 * @doc.purpose EncryptionService async round-trip tests
 * @doc.layer core
 * @doc.pattern Unit Test
 */
@DisplayName("EncryptionService")
class EncryptionServiceTest extends EventloopTestBase {

    private EncryptionService encryptionService;
    private AesGcmEncryptionProvider provider;

    @BeforeEach
    void setUp() {
        provider = AesGcmEncryptionProvider.withNewKey(256, "svc-test-key");
        encryptionService = new EncryptionService(provider, eventloop());
    }

    @Nested
    @DisplayName("encryptAsync / decryptAsync")
    class AsyncRoundTrip {

        @Test
        @DisplayName("should encrypt and decrypt text data")
        void shouldRoundTripText() {
            byte[] plaintext = "Sensitive data for encryption".getBytes(StandardCharsets.UTF_8);

            byte[] encrypted = runPromise(() -> encryptionService.encryptAsync(plaintext));
            byte[] decrypted = runPromise(() -> encryptionService.decryptAsync(encrypted));

            assertThat(new String(decrypted, StandardCharsets.UTF_8))
                    .isEqualTo("Sensitive data for encryption");
        }

        @Test
        @DisplayName("should encrypt and decrypt empty data")
        void shouldRoundTripEmptyData() {
            byte[] plaintext = new byte[0];

            byte[] encrypted = runPromise(() -> encryptionService.encryptAsync(plaintext));
            byte[] decrypted = runPromise(() -> encryptionService.decryptAsync(encrypted));

            assertThat(decrypted).isEqualTo(plaintext);
        }

        @Test
        @DisplayName("encrypted output should differ from plaintext")
        void shouldDifferFromPlaintext() {
            byte[] plaintext = "Hello World".getBytes(StandardCharsets.UTF_8);

            byte[] encrypted = runPromise(() -> encryptionService.encryptAsync(plaintext));

            assertThat(encrypted).isNotEqualTo(plaintext);
            assertThat(encrypted.length).isGreaterThan(plaintext.length);
        }
    }

    @Nested
    @DisplayName("getEncryptionProvider")
    class GetProvider {

        @Test
        @DisplayName("should return the configured provider")
        void shouldReturnProvider() {
            assertThat(encryptionService.getEncryptionProvider()).isSameAs(provider);
        }
    }
}
