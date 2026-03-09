package com.ghatana.aep.connector.strategy.s3;

import com.ghatana.aep.connector.strategy.QueueMessage;
import io.activej.promise.Promise;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Default S3 storage strategy implementation.
 * Supports both writing events to S3 and polling S3 for events.
 * 
 * @doc.type class
 * @doc.purpose S3 event storage implementation
 * @doc.layer infrastructure
 * @doc.pattern Strategy
 */
public class DefaultS3StorageStrategy implements S3StorageStrategy {
    
    private final S3Config config;
    private final Executor executor;
    private final ScheduledExecutorService scheduler;
    private final KeyGenerator keyGenerator;
    
    private final AtomicReference<S3Client> s3ClientRef = new AtomicReference<>();
    private final AtomicReference<StorageStatus> statusRef = new AtomicReference<>(StorageStatus.CREATED);
    private final List<Consumer<QueueMessage>> handlers = new CopyOnWriteArrayList<>();
    private final Set<String> processedKeys = ConcurrentHashMap.newKeySet();
    private volatile ScheduledFuture<?> pollTask;
    
    public DefaultS3StorageStrategy(S3Config config, Executor executor, ScheduledExecutorService scheduler) {
        this(config, executor, scheduler, new DefaultKeyGenerator());
    }
    
    public DefaultS3StorageStrategy(
            S3Config config, 
            Executor executor, 
            ScheduledExecutorService scheduler,
            KeyGenerator keyGenerator) {
        this.config = config;
        this.executor = executor;
        this.scheduler = scheduler;
        this.keyGenerator = keyGenerator;
    }
    
    /**
     * Interface for generating S3 keys for messages.
     */
    @FunctionalInterface
    public interface KeyGenerator {
        String generate(QueueMessage message);
    }
    
    /**
     * Default key generator using date partitioning.
     */
    public static class DefaultKeyGenerator implements KeyGenerator {
        private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd");
        
        @Override
        public String generate(QueueMessage message) {
            String date = DATE_FORMAT.format(java.time.LocalDate.now());
            String id = message.messageId() != null ? message.messageId() : UUID.randomUUID().toString();
            return date + "/" + id + ".json";
        }
    }
    
    @Override
    public Promise<Void> start() {
        return Promise.ofBlocking(executor, () -> {
            statusRef.set(StorageStatus.STARTING);
            
            S3Client client = S3Client.builder()
                .region(Region.of(config.getRegion()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
            
            s3ClientRef.set(client);
            statusRef.set(StorageStatus.RUNNING);
            return null;
        });
    }
    
    @Override
    public Promise<Void> stop() {
        return Promise.ofBlocking(executor, () -> {
            stopPollingInternal();
            
            S3Client client = s3ClientRef.getAndSet(null);
            if (client != null) {
                client.close();
            }
            
            statusRef.set(StorageStatus.STOPPED);
            return null;
        });
    }
    
    @Override
    public Promise<WriteResult> write(QueueMessage message) {
        return Promise.ofBlocking(executor, () -> {
            S3Client client = s3ClientRef.get();
            if (client == null) {
                throw new IllegalStateException("S3 client not started");
            }
            
            try {
                String key = buildKey(keyGenerator.generate(message));
                
                PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(config.getBucketName())
                    .key(key)
                    .contentType("application/json")
                    .metadata(message.metadata() != null ? message.metadata() : Map.of())
                    .build();
                
                client.putObject(request, RequestBody.fromString(message.payload()));
                
                return new WriteResult(message.messageId(), key, true, null);
                
            } catch (Exception e) {
                return new WriteResult(message.messageId(), null, false, e.getMessage());
            }
        });
    }
    
    @Override
    public Promise<List<WriteResult>> writeBatch(List<QueueMessage> messages) {
        return Promise.ofBlocking(executor, () -> {
            List<WriteResult> results = new ArrayList<>();
            for (QueueMessage message : messages) {
                try {
                    WriteResult result = write(message).getResult();
                    results.add(result);
                } catch (Exception e) {
                    results.add(new WriteResult(message.messageId(), null, false, e.getMessage()));
                }
            }
            return results;
        });
    }
    
    @Override
    public Promise<List<QueueMessage>> read(int maxMessages) {
        return Promise.ofBlocking(executor, () -> {
            S3Client client = s3ClientRef.get();
            if (client == null) {
                throw new IllegalStateException("S3 client not started");
            }
            
            List<QueueMessage> messages = new ArrayList<>();
            
            ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                .bucket(config.getBucketName())
                .prefix(config.getPrefix())
                .maxKeys(Math.min(maxMessages, config.getMaxKeysPerRequest()))
                .build();
            
            ListObjectsV2Response listResponse = client.listObjectsV2(listRequest);
            
            for (S3Object s3Object : listResponse.contents()) {
                if (messages.size() >= maxMessages) {
                    break;
                }
                
                String key = s3Object.key();
                if (processedKeys.contains(key)) {
                    continue;
                }
                
                try {
                    GetObjectRequest getRequest = GetObjectRequest.builder()
                        .bucket(config.getBucketName())
                        .key(key)
                        .build();
                    
                    byte[] content = client.getObject(getRequest, ResponseTransformer.toBytes()).asByteArray();
                    String payload = new String(content, StandardCharsets.UTF_8);
                    
                    Map<String, String> metadata = new HashMap<>();
                    metadata.put("s3.key", key);
                    metadata.put("s3.bucket", config.getBucketName());
                    metadata.put("s3.lastModified", s3Object.lastModified().toString());
                    metadata.put("timestamp", Instant.now().toString());
                    
                    QueueMessage message = new QueueMessage(
                        key, // Use S3 key as message ID
                        payload,
                        metadata
                    );
                    
                    messages.add(message);
                    processedKeys.add(key);
                    
                    // Delete after read if configured
                    if (config.isDeleteAfterRead()) {
                        deleteInternal(client, key);
                    }
                    
                } catch (Exception e) {
                    // Log and continue
                }
            }
            
            return messages;
        });
    }
    
    @Override
    public void onMessage(Consumer<QueueMessage> handler) {
        handlers.add(handler);
    }
    
    @Override
    public Promise<Void> startPolling() {
        return Promise.ofBlocking(executor, () -> {
            if (pollTask != null) {
                return null; // Already polling
            }
            
            pollTask = scheduler.scheduleWithFixedDelay(
                this::pollAndDispatch,
                0,
                config.getPollInterval().toMillis(),
                TimeUnit.MILLISECONDS
            );
            
            statusRef.set(StorageStatus.POLLING);
            return null;
        });
    }
    
    @Override
    public Promise<Void> stopPolling() {
        return Promise.ofBlocking(executor, () -> {
            stopPollingInternal();
            return null;
        });
    }
    
    private void stopPollingInternal() {
        if (pollTask != null) {
            pollTask.cancel(false);
            pollTask = null;
        }
        if (statusRef.get() == StorageStatus.POLLING) {
            statusRef.set(StorageStatus.RUNNING);
        }
    }
    
    private void pollAndDispatch() {
        try {
            List<QueueMessage> messages = read(config.getMaxKeysPerRequest()).getResult();
            
            for (QueueMessage message : messages) {
                for (Consumer<QueueMessage> handler : handlers) {
                    try {
                        handler.accept(message);
                    } catch (Exception e) {
                        // Log and continue
                    }
                }
            }
        } catch (Exception e) {
            // Log error
        }
    }
    
    @Override
    public Promise<Void> delete(String key) {
        return Promise.ofBlocking(executor, () -> {
            S3Client client = s3ClientRef.get();
            if (client == null) {
                throw new IllegalStateException("S3 client not started");
            }
            
            deleteInternal(client, key);
            processedKeys.remove(key);
            return null;
        });
    }
    
    private void deleteInternal(S3Client client, String key) {
        DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
            .bucket(config.getBucketName())
            .key(key)
            .build();
        
        client.deleteObject(deleteRequest);
    }
    
    @Override
    public StorageStatus getStatus() {
        return statusRef.get();
    }
    
    private String buildKey(String generatedKey) {
        if (config.getPrefix() == null || config.getPrefix().isEmpty()) {
            return generatedKey;
        }
        return config.getPrefix() + "/" + generatedKey;
    }
}
