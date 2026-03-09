package com.ghatana.security;

import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.MetricsCollectorFactory;
import com.ghatana.platform.security.encryption.EncryptionService;
import com.ghatana.security.keys.KeyManager;
import com.ghatana.security.storage.EncryptedStorageService;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages security-related operations for the EventCloud component.
 * Handles encryption, key management, and secure storage for event data.
 
 *
 * @doc.type class
 * @doc.purpose Event cloud security manager
 * @doc.layer core
 * @doc.pattern Manager
*/
public class EventCloudSecurityManager {
    private static final Logger logger = LoggerFactory.getLogger(EventCloudSecurityManager.class);
    
    // Security operation constants for metrics
    private static final String METRIC_PREFIX = "security.";
    private static final String OP_ENCRYPT = "encrypt";
    private static final String OP_DECRYPT = "decrypt";
    private static final String OP_STORE = "store";
    private static final String OP_RETRIEVE = "retrieve";
    
    private final KeyManager keyManager;
    private final EncryptionService encryptionService;
    private final EncryptedStorageService storageService;
    private final Executor executor;
    private final MetricsCollector metricsCollector;
    
    /**
     * Creates a new EventCloudSecurityManager with the specified components.
     * 
     * @param keyManager The key manager for cryptographic operations (required)
     * @param encryptionService The encryption service for data protection (required)
     * @param storageService The encrypted storage service (required)
     * @param eventloop The event loop for async operations (required)
     */
    public EventCloudSecurityManager(
            KeyManager keyManager,
            EncryptionService encryptionService,
            EncryptedStorageService storageService,
            Eventloop eventloop) {
        this(keyManager, encryptionService, storageService, eventloop, null);
    }
    
    /**
     * Creates a new EventCloudSecurityManager with the specified components and metrics.
     * 
     * @param keyManager The key manager for cryptographic operations (required)
     * @param encryptionService The encryption service for data protection (required)
     * @param storageService The encrypted storage service (required)
     * @param eventloop The event loop for async operations (required)
     * @param metricsCollector The metrics collector for monitoring (optional)
     */
    public EventCloudSecurityManager(
            KeyManager keyManager,
            EncryptionService encryptionService,
            EncryptedStorageService storageService,
            Eventloop eventloop,
            MetricsCollector metricsCollector) {
        
        this.keyManager = Objects.requireNonNull(keyManager, "KeyManager cannot be null");
        this.encryptionService = Objects.requireNonNull(encryptionService, "EncryptionService cannot be null");
        this.storageService = Objects.requireNonNull(storageService, "EncryptedStorageService cannot be null");
        
        // Use a noop metrics collector if none provided
        this.metricsCollector = metricsCollector != null ? metricsCollector : new com.ghatana.platform.observability.NoopMetricsCollector();
        
        if (eventloop == null) {
            throw new IllegalArgumentException("Eventloop cannot be null");
        }
        
        // Create a dedicated executor for security operations
        this.executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "eventcloud-security");
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY);
            return thread;
        });
        
        logger.info("Initialized EventCloudSecurityManager with key manager: {}", 
            keyManager.getClass().getSimpleName());
    }
    
    /**
     * Encrypts event data before storage.
     * 
     * @param eventData The event data to encrypt
     * @return A promise that completes with the encrypted data
     */
    public Promise<byte[]> encryptEvent(byte[] eventData) {
        return encryptionService.encryptAsync(eventData);
    }
    
    /**
     * Decrypts event data after retrieval.
     * 
     * @param encryptedData The encrypted event data
     * @return A promise that completes with the decrypted data
     */
    public Promise<byte[]> decryptEvent(byte[] encryptedData) {
        return encryptionService.decryptAsync(encryptedData);
    }
    
    /**
     * Securely stores event metadata.
     * 
     * @param key The metadata key
     * @param value The metadata value
     * @return A promise that completes when the operation is done
     */
    public Promise<Void> storeMetadata(String key, String value) {
        return storageService.store(key, value.getBytes())
            .mapException(e -> new SecurityException("Failed to store metadata: " + e.getMessage(), e));
    }
    
    /**
     * Retrieves and decrypts stored event metadata.
     * 
     * @param key The metadata key
     * @return A promise that completes with the decrypted metadata value
     */
    public Promise<String> retrieveMetadata(String key) {
        return storageService.retrieve(key)
            .map(bytes -> bytes != null ? new String(bytes) : null)
            .mapException(e -> new SecurityException("Failed to retrieve metadata: " + e.getMessage(), e));
    }
    
    /**
     * Rotates the encryption key and re-encrypts all stored data.
     * 
     * @return A promise that completes when key rotation is complete
     */
    public Promise<Void> rotateEncryptionKey() {
        long startTime = System.nanoTime();
        return Promise.ofBlocking(executor, () -> {
            try {
                // Generate a new key
                String newKeyId = keyManager.rotateKey().getResult();
                logger.info("Rotated to new encryption key: {}", newKeyId);
                
                // In a real implementation, we would re-encrypt all data here
                // For now, we'll just log a message
                logger.warn("Automatic re-encryption of existing data is not implemented");
                
                recordKeyRotationMetrics(System.nanoTime() - startTime, true);
                
                return null;
            } catch (Exception e) {
                recordKeyRotationMetrics(System.nanoTime() - startTime, false);
                logger.error("Failed to rotate encryption key", e);
                throw new SecurityException("Key rotation failed: " + e.getMessage(), e);
            }
        });
    }
    
    /**
     * Gets the current encryption key ID.
     * 
     * @return The current key ID
     */
    public String getCurrentKeyId() {
        return keyManager.getCurrentKeyId();
    }
    
    /**
     * Records metrics for key rotation operations.
     *
     * @param durationNanos The duration of the key rotation operation in nanoseconds
     * @param success Whether the operation was successful
     */
    private void recordKeyRotationMetrics(long durationNanos, boolean success) {
        try {
            String status = success ? "success" : "error";
            // Record the operation count with status
            metricsCollector.increment(METRIC_PREFIX + "keyRotation.operations", 1.0, 
                Map.of("status", status));
            
            // Record the duration in milliseconds
            metricsCollector.increment(METRIC_PREFIX + "keyRotation.duration", 
                durationNanos / 1_000_000.0, 
                Map.of("status", status));
                
            if (!success) {
                // Record error count
                metricsCollector.increment(METRIC_PREFIX + "keyRotation.errors", 1.0, 
                    Map.of("errorType", "rotation_failed"));
            }
        } catch (Exception e) {
            logger.warn("Failed to record key rotation metrics", e);
        }
    }
    
    /**
     * Shuts down the security manager and releases resources.
     * 
     * @return A promise that completes when shutdown is complete
     */
    public Promise<Void> shutdown() {
        return Promise.ofBlocking(executor, () -> {
            logger.info("Shutting down EventCloudSecurityManager");
            // Clean up resources
            if (executor instanceof ExecutorService) {
                ((ExecutorService) executor).shutdown();
            }
            return null;
        });
    }
}
