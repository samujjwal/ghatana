package com.ghatana.security.config;

import io.activej.config.Config;

import java.util.Objects;

/**
 * Configuration properties for encryption settings.
 * 
 * <p>This class holds configuration options for various encryption mechanisms
 * used throughout the application, including data-at-rest and data-in-transit encryption.</p>
 * 
 * <p>Example configuration (in application.yaml or application.properties):</p>
 * <pre>
 * security:
 *   encryption:
 *     enabled: true
 *     algorithm: AES
 *     key-size: 256
 *     key-alias: my-encryption-key
 *     key-version: 1
 *     iv-length: 16
 *     key-derivation:
 *       algorithm: PBKDF2WithHmacSHA256
 *       iterations: 100000
 *       salt-length: 16
 *     provider: SunJCE
 * </pre>
 
 *
 * @doc.type class
 * @doc.purpose Encryption properties
 * @doc.layer core
 * @doc.pattern Component
*/
public class EncryptionProperties {
    private final boolean enabled;
    private final String algorithm;
    private final int keySize;
    private final String keyAlias;
    private final int keyVersion;
    private final int ivLength;
    private final KeyDerivationProperties keyDerivation;
    private final String provider;
    
    /**
     * Creates a new EncryptionProperties instance from a Config object.
     * 
     * @param config The configuration source
     * @throws NullPointerException if config is null
     */
    public EncryptionProperties(Config config) {
        Objects.requireNonNull(config, "Config cannot be null");
        
        this.enabled = Boolean.parseBoolean(config.get("enabled", "true"));
        this.algorithm = config.get("algorithm", "AES");
        this.keySize = Integer.parseInt(config.get("key-size", "256"));
        this.keyAlias = config.get("key-alias", "default-encryption-key");
        this.keyVersion = Integer.parseInt(config.get("key-version", "1"));
        this.ivLength = Integer.parseInt(config.get("iv-length", "16"));
        this.keyDerivation = new KeyDerivationProperties(config.getChild("key-derivation"));
        this.provider = config.get("provider", "");
    }
    
    /**
     * Checks if encryption is enabled.
     * 
     * @return true if encryption is enabled, false otherwise
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Gets the encryption algorithm to use.
     * 
     * @return The encryption algorithm (e.g., AES, RSA)
     */
    public String getAlgorithm() {
        return algorithm;
    }
    
    /**
     * Gets the key size in bits.
     * 
     * @return The key size in bits
     */
    public int getKeySize() {
        return keySize;
    }
    
    /**
     * Gets the alias of the encryption key to use.
     * 
     * @return The key alias
     */
    public String getKeyAlias() {
        return keyAlias;
    }
    
    /**
     * Gets the version of the encryption key to use.
     * 
     * @return The key version
     */
    public int getKeyVersion() {
        return keyVersion;
    }
    
    /**
     * Gets the length of the initialization vector (IV) in bytes.
     * 
     * @return The IV length in bytes
     */
    public int getIvLength() {
        return ivLength;
    }
    
    /**
     * Gets the key derivation configuration.
     * 
     * @return Key derivation properties
     */
    public KeyDerivationProperties getKeyDerivation() {
        return keyDerivation;
    }
    
    /**
     * Gets the security provider to use for encryption operations.
     * 
     * @return The security provider name, or empty string for default provider
     */
    public String getProvider() {
        return provider;
    }
    
    @Override
    public String toString() {
        return "EncryptionProperties{" +
                "enabled=" + enabled +
                ", algorithm='" + algorithm + '\'' +
                ", keySize=" + keySize +
                ", keyAlias='" + keyAlias + '\'' +
                ", keyVersion=" + keyVersion +
                ", ivLength=" + ivLength +
                ", keyDerivation=" + keyDerivation +
                ", provider='" + provider + '\'' +
                '}';
    }
    
    /**
     * Key derivation function (KDF) configuration.
     */
    public static class KeyDerivationProperties {
        private final String algorithm;
        private final int iterations;
        private final int saltLength;
        
        public KeyDerivationProperties(Config config) {
            this.algorithm = config.get("algorithm", "PBKDF2WithHmacSHA256");
            this.iterations = Integer.parseInt(config.get("iterations", "100000"));
            this.saltLength = Integer.parseInt(config.get("salt-length", "16"));
        }
        
        /**
         * Gets the key derivation algorithm.
         * 
         * @return The algorithm name (e.g., PBKDF2WithHmacSHA256)
         */
        public String getAlgorithm() {
            return algorithm;
        }
        
        /**
         * Gets the number of iterations for the key derivation function.
         * 
         * @return The number of iterations
         */
        public int getIterations() {
            return iterations;
        }
        
        /**
         * Gets the length of the salt in bytes.
         * 
         * @return The salt length in bytes
         */
        public int getSaltLength() {
            return saltLength;
        }
        
        @Override
        public String toString() {
            return "KeyDerivationProperties{" +
                    "algorithm='" + algorithm + '\'' +
                    ", iterations=" + iterations +
                    ", saltLength=" + saltLength +
                    '}';
        }
    }
}
