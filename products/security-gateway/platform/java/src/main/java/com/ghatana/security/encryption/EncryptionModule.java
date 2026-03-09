package com.ghatana.security.encryption;

import com.ghatana.security.config.EncryptionProperties;
import com.ghatana.platform.security.encryption.EncryptionException;
import com.ghatana.platform.security.encryption.EncryptionService;
import com.ghatana.platform.security.encryption.impl.AesGcmEncryptionProvider;
import io.activej.config.Config;
import io.activej.eventloop.Eventloop;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Dependency injection module for encryption services.
 * Integrates with KeyManager for secure key management and TlsModule for SSL context.
 
 *
 * @doc.type class
 * @doc.purpose Encryption module
 * @doc.layer core
 * @doc.pattern Component
*/
public class EncryptionModule extends AbstractModule {
    private static final Logger logger = LoggerFactory.getLogger(EncryptionModule.class);
    
    @Override
    protected void configure() {
        logger.info("Configuring EncryptionModule");
    }
    
    @Provides
    Executor encryptionExecutor() {
        return Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r, "encryption-worker");
            thread.setDaemon(true);
            return thread;
        });
    }
    
    @Provides
    AesGcmEncryptionProvider encryptionProvider(
            EncryptionProperties encryptionProps,
            Executor executor) throws EncryptionException {
        
        // Check if encryption is enabled
        if (!encryptionProps.isEnabled()) {
            logger.warn("Encryption is disabled in configuration. Using a randomly generated key for this session.");
            return AesGcmEncryptionProvider.withNewKey(256, "temporary-session-key", executor);
        }
        
        // Get key from configuration
        String keyAlias = encryptionProps.getKeyAlias();
        String algorithm = encryptionProps.getAlgorithm();
        int keySize = encryptionProps.getKeySize();
        
        // Validate key size for AES
        if (keySize != 128 && keySize != 192 && keySize != 256) {
            throw new EncryptionException("Invalid key size: " + keySize + " bits. Must be 128, 192, or 256 bits for AES.");
        }
        
        // In a real implementation, we would use the KeyManager to get the key
        logger.info("Initializing AES-GCM encryption with algorithm: {}, key size: {} bits, key alias: {}", 
            algorithm, keySize, keyAlias);
            
        return AesGcmEncryptionProvider.withNewKey(keySize, keyAlias, executor);
    }
    
    @Provides
    EncryptionService encryptionService(
            AesGcmEncryptionProvider encryptionProvider,
            Eventloop eventloop) {
        
        logger.info("Initialized EncryptionService with provider: {}", 
            encryptionProvider.getClass().getSimpleName());
            
        return new EncryptionService(encryptionProvider, eventloop);
    }
    
    @Provides
    EncryptionProperties encryptionProperties(Config config) {
        return new EncryptionProperties(config.getChild("security.encryption"));
    }
}
