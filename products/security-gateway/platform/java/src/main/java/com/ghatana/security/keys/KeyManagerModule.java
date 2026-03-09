package com.ghatana.security.keys;

import com.ghatana.security.config.KeyConfig;
import com.ghatana.security.config.KeyManagementProperties;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dependency injection module for key management services.
 * Provides KeyManager implementation and related components.
 
 *
 * @doc.type class
 * @doc.purpose Key manager module
 * @doc.layer core
 * @doc.pattern Component
*/
public class KeyManagerModule extends AbstractModule {
    private static final Logger logger = LoggerFactory.getLogger(KeyManagerModule.class);
    
    @Override
    protected void configure() {
        logger.info("Configuring KeyManagerModule");
        bind(KeyManager.class).to(DefaultKeyManager.class);
    }
    
    @Provides
    DefaultKeyManager keyManager(KeyManagementProperties keyProps) {
        try {
            // Create a map to hold keys
            Map<String, SecretKey> keys = new ConcurrentHashMap<>();
            String currentKeyId = null;
            
            // Generate initial keys based on configuration
            if (keyProps.isGenerateInitialKey()) {
                int keyCount = keyProps.getInitialKeyCount();
                int keySize = keyProps.getKeySize();
                String keyAlgorithm = keyProps.getKeyAlgorithm();
                
                logger.info("Generating {} initial {} keys with size {} bits", 
                    keyCount, keyAlgorithm, keySize);
                
                KeyGenerator keyGen = KeyGenerator.getInstance(keyAlgorithm);
                keyGen.init(keySize, new SecureRandom());
                
                // Generate the specified number of keys
                for (int i = 0; i < keyCount; i++) {
                    String keyId = "key-" + UUID.randomUUID().toString();
                    SecretKey key = keyGen.generateKey();
                    keys.put(keyId, key);
                    
                    // First key is the current one
                    if (i == 0) {
                        currentKeyId = keyId;
                    }
                    
                    logger.debug("Generated key: {} (algorithm: {}, format: {})", 
                        keyId, key.getAlgorithm(), key.getFormat());
                }
                
                if (currentKeyId == null) {
                    throw new IllegalStateException("Failed to generate initial keys");
                }
                
                logger.info("Initialized KeyManager with {} keys, current key: {}", 
                    keys.size(), currentKeyId);
            } else {
                // Use provided keys from configuration
                Map<String, KeyConfig> configuredKeys = keyProps.getConfiguredKeys();
                if (configuredKeys != null && !configuredKeys.isEmpty()) {
                    // In a real implementation, we would load the actual keys from a secure store
                    // For now, we'll just log a warning and generate random keys
                    logger.warn("Loading configured keys is not implemented. Using random keys instead.");
                    
                    KeyGenerator keyGen = KeyGenerator.getInstance("AES");
                    keyGen.init(256, new SecureRandom());
                    
                    for (Map.Entry<String, KeyConfig> entry : configuredKeys.entrySet()) {
                        String keyId = entry.getKey();
                        KeyConfig keyConfig = entry.getValue();
                        
                        // In a real implementation, we would use the keyConfig to load the key
                        // For now, we'll generate a new random key
                        SecretKey key = keyGen.generateKey();
                        keys.put(keyId, key);
                        
                        // First key is the current one
                        if (currentKeyId == null) {
                            currentKeyId = keyId;
                        }
                        
                        logger.debug("Added configured key: {}", keyId);
                    }
                } else {
                    throw new IllegalStateException("No keys configured and key generation is disabled");
                }
            }
            
            return new DefaultKeyManager(
                keys, 
                currentKeyId,
                KeyGenerator.getInstance("AES"),
                "AES",
                null // executorService
            );
            
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to initialize KeyManager: " + e.getMessage(), e);
        }
    }
}
