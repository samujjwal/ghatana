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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    private EventCloudSecurityManager securityManager;

    @BeforeEach
    void setUp() {
        keyManager = mock(KeyManager.class);
        EncryptionService encryptionService = mock(EncryptionService.class);
        EncryptedStorageService storageService = mock(EncryptedStorageService.class);
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
}