package com.ghatana.security.storage;

import com.ghatana.platform.security.encryption.EncryptionService;
import com.ghatana.security.keys.KeyManager;
import io.activej.eventloop.Eventloop;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Dependency injection module for storage services.
 * Provides EncryptedStorageService and related components.
 
 *
 * @doc.type class
 * @doc.purpose Storage module
 * @doc.layer core
 * @doc.pattern Component
*/
public class StorageModule extends AbstractModule {
    private static final Logger logger = LoggerFactory.getLogger(StorageModule.class);
    
    @Override
    protected void configure() {
        logger.info("Configuring StorageModule");
    }
    
    @Provides
    EncryptedStorageService encryptedStorageService(
            KeyManager keyManager,
            EncryptionService encryptionService,
            Eventloop eventloop) {
        
        // Create a dedicated executor for storage operations
        Executor executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "security-storage");
            thread.setDaemon(true);
            return thread;
        });
        
        logger.info("Initializing EncryptedStorageService with {} encryption", 
            encryptionService.getEncryptionProvider().getClass().getSimpleName());
            
        return new EncryptedStorageService(keyManager, encryptionService, executor);
    }
}
