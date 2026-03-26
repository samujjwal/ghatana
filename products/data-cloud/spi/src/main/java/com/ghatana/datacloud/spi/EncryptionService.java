package com.ghatana.datacloud.spi;

/**
 * SPI-level encryption service contract exposed to plugins.
 * Implementations in infrastructure can adapt to this interface.
 
 *
 * @doc.type interface
 * @doc.purpose Encryption service
 * @doc.layer platform
 * @doc.pattern Service
*/
public interface EncryptionService {
    byte[] encrypt(byte[] plaintext);
    byte[] decrypt(byte[] ciphertext);

    static EncryptionService noop() {
        return new EncryptionService() {
            @Override public byte[] encrypt(byte[] plaintext) { return plaintext; }
            @Override public byte[] decrypt(byte[] ciphertext) { return ciphertext; }
        };
    }
}

