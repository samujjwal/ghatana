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
@DisplayName("EncryptionService [GH-90000]")
class EncryptionServiceTest extends EventloopTestBase {

    private EncryptionService encryptionService;
    private AesGcmEncryptionProvider provider;

    @BeforeEach
    void setUp() { // GH-90000
        provider = AesGcmEncryptionProvider.withNewKey(256, "svc-test-key"); // GH-90000
        encryptionService = new EncryptionService(provider, eventloop()); // GH-90000
    }

    @Nested
    @DisplayName("encryptAsync / decryptAsync [GH-90000]")
    class AsyncRoundTrip {

        @Test
        @DisplayName("should encrypt and decrypt text data [GH-90000]")
        void shouldRoundTripText() { // GH-90000
            byte[] plaintext = "Sensitive data for encryption".getBytes(StandardCharsets.UTF_8); // GH-90000

            byte[] encrypted = runPromise(() -> encryptionService.encryptAsync(plaintext)); // GH-90000
            byte[] decrypted = runPromise(() -> encryptionService.decryptAsync(encrypted)); // GH-90000

            assertThat(new String(decrypted, StandardCharsets.UTF_8)) // GH-90000
                    .isEqualTo("Sensitive data for encryption [GH-90000]");
        }

        @Test
        @DisplayName("should encrypt and decrypt empty data [GH-90000]")
        void shouldRoundTripEmptyData() { // GH-90000
            byte[] plaintext = new byte[0];

            byte[] encrypted = runPromise(() -> encryptionService.encryptAsync(plaintext)); // GH-90000
            byte[] decrypted = runPromise(() -> encryptionService.decryptAsync(encrypted)); // GH-90000

            assertThat(decrypted).isEqualTo(plaintext); // GH-90000
        }

        @Test
        @DisplayName("encrypted output should differ from plaintext [GH-90000]")
        void shouldDifferFromPlaintext() { // GH-90000
            byte[] plaintext = "Hello World".getBytes(StandardCharsets.UTF_8); // GH-90000

            byte[] encrypted = runPromise(() -> encryptionService.encryptAsync(plaintext)); // GH-90000

            assertThat(encrypted).isNotEqualTo(plaintext); // GH-90000
            assertThat(encrypted.length).isGreaterThan(plaintext.length); // GH-90000
        }
    }

    @Nested
    @DisplayName("getEncryptionProvider [GH-90000]")
    class GetProvider {

        @Test
        @DisplayName("should return the configured provider [GH-90000]")
        void shouldReturnProvider() { // GH-90000
            assertThat(encryptionService.getEncryptionProvider()).isSameAs(provider); // GH-90000
        }
    }
}
