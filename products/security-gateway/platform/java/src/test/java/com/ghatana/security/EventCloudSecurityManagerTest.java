package com.ghatana.security;

import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.security.encryption.EncryptionService;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.security.keys.KeyManager;
import com.ghatana.security.storage.EncryptedStorageService;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Async tests for {@link EventCloudSecurityManager}.
 *
 * @doc.type class
 * @doc.purpose Verify key rotation composes promises without blocking on getResult
 * @doc.layer product
 * @doc.pattern Test, Unit
 */
@DisplayName("EventCloudSecurityManager Tests")
class EventCloudSecurityManagerTest extends EventloopTestBase {

    private KeyManager keyManager;
    private EncryptedStorageService storageService;
    private EventCloudSecurityManager securityManager;

    @BeforeEach
    void setUp() {
        keyManager = mock(KeyManager.class);
        EncryptionService encryptionService = mock(EncryptionService.class);
        storageService = mock(EncryptedStorageService.class);
        MetricsCollector metricsCollector = mock(MetricsCollector.class);

        securityManager = new EventCloudSecurityManager(
                keyManager,
                encryptionService,
                storageService,
                eventloop(),
                metricsCollector);
    }

    @Test
    @DisplayName("rotateEncryptionKey should resolve when key manager rotation succeeds")
    void rotateEncryptionKeyShouldResolveWhenRotationSucceeds() {
        when(keyManager.rotateKey()).thenReturn(Promise.of("key-v2"));

        runPromise(() -> securityManager.rotateEncryptionKey());

        verify(keyManager).rotateKey();
    }

    @Test
    @DisplayName("rotateEncryptionKey should surface key manager failures")
    void rotateEncryptionKeyShouldSurfaceFailures() {
        when(keyManager.rotateKey()).thenReturn(Promise.ofException(new IllegalStateException("rotation failed")));

        assertThatThrownBy(() -> runPromise(() -> securityManager.rotateEncryptionKey()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Key rotation failed");
    }

    @Test
    @DisplayName("retrieveMetadata should return null when the key is not stored")
    void retrieveMetadataShouldReturnNullWhenMissing() {
        when(storageService.retrieve("missing-key")).thenReturn(Promise.of((byte[]) null));

        String value = runPromise(() -> securityManager.retrieveMetadata("missing-key"));

        assertThat(value).isNull();
        verify(storageService).retrieve("missing-key");
    }

    @Test
    @DisplayName("storeMetadata should encode text and delegate to encrypted storage")
    void storeMetadataShouldDelegateToStorageService() {
        when(storageService.store(eq("tenant-key"), argThat(bytes ->
                java.util.Arrays.equals(bytes, "secret".getBytes(StandardCharsets.UTF_8)))))
                .thenReturn(Promise.complete());

        runPromise(() -> securityManager.storeMetadata("tenant-key", "secret"));

        verify(storageService).store(eq("tenant-key"), argThat(bytes ->
                java.util.Arrays.equals(bytes, "secret".getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    @DisplayName("storeMetadata should wrap storage failures")
    void storeMetadataShouldWrapStorageFailures() {
        when(storageService.store(eq("tenant-key"), argThat(bytes ->
                java.util.Arrays.equals(bytes, "secret".getBytes(StandardCharsets.UTF_8)))))
                .thenReturn(Promise.ofException(new IllegalStateException("disk full")));

        assertThatThrownBy(() -> runPromise(() -> securityManager.storeMetadata("tenant-key", "secret")))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Failed to store metadata");
    }
}