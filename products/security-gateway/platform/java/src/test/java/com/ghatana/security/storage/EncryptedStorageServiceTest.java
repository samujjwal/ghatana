package com.ghatana.security.storage;

import com.ghatana.platform.security.encryption.EncryptionProvider;
import com.ghatana.platform.security.encryption.EncryptionService;
import com.ghatana.platform.security.encryption.impl.AesGcmEncryptionProvider;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.security.keys.KeyManager;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Async tests for {@link EncryptedStorageService}.
 *
 * @doc.type class
 * @doc.purpose Verify encrypted storage service avoids synchronous promise result access
 * @doc.layer product
 * @doc.pattern Test, Unit
 */
@DisplayName("EncryptedStorageService Tests")
class EncryptedStorageServiceTest extends EventloopTestBase {

    private KeyManager keyManager;
    private EncryptedStorageService storageService;

    @BeforeEach
    void setUp() {
        keyManager = mock(KeyManager.class);
        EncryptionService encryptionService = new EncryptionService(
                AesGcmEncryptionProvider.withNewKey(128, "test-key"),
                eventloop());
        storageService = new EncryptedStorageService(keyManager, encryptionService, Runnable::run);
    }

    @Test
    @DisplayName("storeSecurely should persist data through async encryption")
    void storeSecurelyShouldPersistDataThroughAsyncEncryption() {
        byte[] plaintext = "secret-value".getBytes(StandardCharsets.UTF_8);

        runPromise(() -> storageService.storeSecurely("audit", plaintext));
        byte[] decrypted = runPromise(() -> storageService.retrieve("audit"));

        assertThat(new String(decrypted, StandardCharsets.UTF_8)).isEqualTo("secret-value");
    }

    @Test
    @DisplayName("storeSecurely should surface encryption failures as failed promises")
    void storeSecurelyShouldSurfaceEncryptionFailures() {
        EncryptionProvider failingProvider = new EncryptionProvider() {
            @Override
            public Promise<byte[]> encrypt(byte[] data) {
                return Promise.ofException(new IllegalStateException("boom"));
            }

            @Override
            public Promise<byte[]> decrypt(byte[] encryptedData) {
                return Promise.of(encryptedData);
            }

            @Override
            public String getAlgorithm() {
                return "test";
            }

            @Override
            public String getKeyId() {
                return "test-key";
            }
        };

        EncryptedStorageService failingStorageService = new EncryptedStorageService(
                keyManager,
                new EncryptionService(failingProvider, eventloop()),
                Runnable::run);

        assertThatThrownBy(() -> runPromise(() ->
                failingStorageService.storeSecurely("audit", "payload".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to store data securely");
    }
}