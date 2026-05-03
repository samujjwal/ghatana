package com.ghatana.platform.security.encryption.impl;

import com.ghatana.platform.security.encryption.EncryptionProvider;
import com.ghatana.platform.security.encryption.EncryptionService;
import com.ghatana.platform.security.port.EncryptionPort;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Default implementation of EncryptionPort that delegates to EncryptionService.
 * Handles base64 encoding/decoding for string-based operations.
 *
 * @doc.type class
 * @doc.purpose Default encryption provider implementation
 * @doc.layer core
 * @doc.pattern Provider
 */
public class DefaultEncryptionProvider implements EncryptionPort {
    private static final Logger logger = LoggerFactory.getLogger(DefaultEncryptionProvider.class);

    private final EncryptionService encryptionService;

    /**
     * Creates a new DefaultEncryptionProvider with the specified encryption service.
     *
     * @param encryptionService The encryption service to delegate to
     */
    public DefaultEncryptionProvider(EncryptionService encryptionService) {
        this.encryptionService = encryptionService;
        logger.info("Initialized DefaultEncryptionProvider");
    }

    @Override
    public Promise<String> encrypt(String plaintext) {
        return encryptionService.encryptAsync(plaintext.getBytes(StandardCharsets.UTF_8))
            .map(encryptedBytes -> Base64.getEncoder().encodeToString(encryptedBytes))
            .mapException(e -> {
                logger.error("Failed to encrypt string", e);
                throw new com.ghatana.platform.security.encryption.EncryptionException("Failed to encrypt string", e);
            });
    }

    @Override
    public Promise<String> decrypt(String ciphertext) {
        return encryptionService.decryptAsync(Base64.getDecoder().decode(ciphertext))
            .map(decryptedBytes -> new String(decryptedBytes, StandardCharsets.UTF_8))
            .mapException(e -> {
                logger.error("Failed to decrypt string", e);
                throw new com.ghatana.platform.security.encryption.EncryptionException("Failed to decrypt string", e);
            });
    }

    @Override
    public Promise<byte[]> encryptBytes(byte[] data) {
        return encryptionService.encryptAsync(data)
            .mapException(e -> {
                logger.error("Failed to encrypt bytes", e);
                throw new com.ghatana.platform.security.encryption.EncryptionException("Failed to encrypt bytes", e);
            });
    }

    @Override
    public Promise<byte[]> decryptBytes(byte[] encryptedData) {
        return encryptionService.decryptAsync(encryptedData)
            .mapException(e -> {
                logger.error("Failed to decrypt bytes", e);
                throw new com.ghatana.platform.security.encryption.EncryptionException("Failed to decrypt bytes", e);
            });
    }

    @Override
    public String getAlgorithm() {
        return encryptionService.getEncryptionProvider().getAlgorithm();
    }

    @Override
    public String getKeyId() {
        return encryptionService.getEncryptionProvider().getKeyId();
    }
}
