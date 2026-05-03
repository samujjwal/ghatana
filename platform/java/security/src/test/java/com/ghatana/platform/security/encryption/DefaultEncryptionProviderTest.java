package com.ghatana.platform.security.encryption.impl;

import com.ghatana.platform.security.encryption.EncryptionService;
import com.ghatana.platform.security.encryption.EncryptionProvider;
import com.ghatana.platform.security.port.EncryptionPort;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests for DefaultEncryptionProvider.
 *
 * @doc.type test
 * @doc.purpose DefaultEncryptionProvider unit tests
 * @doc.layer core
 */
@DisplayName("DefaultEncryptionProvider Tests")
class DefaultEncryptionProviderTest {

    private final Eventloop eventloop = Eventloop.create().withCurrentThread();

    @Test
    @DisplayName("Should encrypt and decrypt string correctly")
    void shouldEncryptAndDecryptStringCorrectly() {
        EncryptionProvider mockProvider = Mockito.mock(EncryptionProvider.class);
        EncryptionService mockService = Mockito.mock(EncryptionService.class);
        
        byte[] encryptedData = "encrypted".getBytes();
        when(mockService.getEncryptionProvider()).thenReturn(mockProvider);
        when(mockProvider.getAlgorithm()).thenReturn("AES/GCM/NoPadding");
        when(mockProvider.getKeyId()).thenReturn("key-1");
        when(mockService.encryptAsync(any())).thenReturn(Promise.of(encryptedData));
        when(mockService.decryptAsync(any())).thenReturn(Promise.of("plaintext".getBytes()));

        DefaultEncryptionProvider provider = new DefaultEncryptionProvider(mockService);
        String plaintext = "plaintext";
        String ciphertext = runPromise(() -> provider.encrypt(plaintext));
        String decrypted = runPromise(() -> provider.decrypt(ciphertext));

        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("Should encrypt and decrypt bytes correctly")
    void shouldEncryptAndDecryptBytesCorrectly() {
        EncryptionProvider mockProvider = Mockito.mock(EncryptionProvider.class);
        EncryptionService mockService = Mockito.mock(EncryptionService.class);
        
        byte[] encryptedData = "encrypted".getBytes();
        when(mockService.getEncryptionProvider()).thenReturn(mockProvider);
        when(mockProvider.getAlgorithm()).thenReturn("AES/GCM/NoPadding");
        when(mockProvider.getKeyId()).thenReturn("key-1");
        when(mockService.encryptAsync(any())).thenReturn(Promise.of(encryptedData));
        when(mockService.decryptAsync(any())).thenReturn(Promise.of("plaintext".getBytes()));

        DefaultEncryptionProvider provider = new DefaultEncryptionProvider(mockService);
        byte[] plaintext = "plaintext".getBytes();
        byte[] ciphertext = runPromise(() -> provider.encryptBytes(plaintext));
        byte[] decrypted = runPromise(() -> provider.decryptBytes(ciphertext));

        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("Should return correct algorithm name")
    void shouldReturnCorrectAlgorithmName() {
        EncryptionProvider mockProvider = Mockito.mock(EncryptionProvider.class);
        EncryptionService mockService = Mockito.mock(EncryptionService.class);
        
        when(mockService.getEncryptionProvider()).thenReturn(mockProvider);
        when(mockProvider.getAlgorithm()).thenReturn("AES/GCM/NoPadding");
        when(mockProvider.getKeyId()).thenReturn("key-1");

        DefaultEncryptionProvider provider = new DefaultEncryptionProvider(mockService);
        
        assertThat(provider.getAlgorithm()).isEqualTo("AES/GCM/NoPadding");
    }

    @Test
    @DisplayName("Should return correct key ID")
    void shouldReturnCorrectKeyId() {
        EncryptionProvider mockProvider = Mockito.mock(EncryptionProvider.class);
        EncryptionService mockService = Mockito.mock(EncryptionService.class);
        
        when(mockService.getEncryptionProvider()).thenReturn(mockProvider);
        when(mockProvider.getAlgorithm()).thenReturn("AES/GCM/NoPadding");
        when(mockProvider.getKeyId()).thenReturn("key-1");

        DefaultEncryptionProvider provider = new DefaultEncryptionProvider(mockService);
        
        assertThat(provider.getKeyId()).isEqualTo("key-1");
    }

    private <T> T runPromise(io.activej.promise.Promise<T> promise) {
        try {
            return promise.block();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
