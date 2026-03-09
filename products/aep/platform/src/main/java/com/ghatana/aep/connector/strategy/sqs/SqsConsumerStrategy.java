package com.ghatana.aep.connector.strategy.sqs;

import com.ghatana.aep.connector.strategy.QueueConsumerStrategy;
import com.ghatana.aep.connector.strategy.QueueMessage;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AWS SQS consumer strategy using ActiveJ Promise integration.
 * 
 * <p>This implementation:
 * <ul>
 *   <li>Uses long polling for efficient message retrieval</li>
 *   <li>Manages receipt handles for message acknowledgment</li>
 *   <li>Supports visibility timeout for at-least-once delivery</li>
 *   <li>Configurable batch size (max 10 for SQS)</li>
 * </ul>
 * 
 * @doc.type class
 * @doc.purpose AWS SQS consumer implementation
 * @doc.layer infrastructure
 * @doc.pattern Strategy
 */
public class SqsConsumerStrategy implements QueueConsumerStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(SqsConsumerStrategy.class);
    private static final int SQS_MAX_BATCH_SIZE = 10; // SQS limit
    
    private final SqsConfig config;
    private final Eventloop eventloop;
    private final ExecutorService executor;
    private final AtomicReference<ConsumerStatus> status;
    private final Map<String, String> receiptHandles;
    
    private volatile SqsClient sqsClient;
    
    /**
     * Creates a new SqsConsumerStrategy.
     * 
     * @param config Consumer configuration
     * @param eventloop ActiveJ eventloop for async operations
     */
    public SqsConsumerStrategy(SqsConfig config, Eventloop eventloop) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop cannot be null");
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "sqs-consumer-" + config.getQueueName());
            t.setDaemon(true);
            return t;
        });
        this.status = new AtomicReference<>(ConsumerStatus.NOT_STARTED);
        this.receiptHandles = new ConcurrentHashMap<>();
    }
    
    @Override
    public Promise<Void> start() {
        if (!status.compareAndSet(ConsumerStatus.NOT_STARTED, ConsumerStatus.STARTING)) {
            return Promise.ofException(
                new IllegalStateException("Consumer already started or starting")
            );
        }
        
        return Promise.ofBlocking(executor, () -> {
            logger.info("Starting SQS consumer. queue={}, region={}", 
                config.getQueueName(), config.getRegion());
            
            sqsClient = SqsClient.builder()
                .region(Region.of(config.getRegion()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
            
            // Verify queue exists
            GetQueueUrlRequest getQueueUrlRequest = GetQueueUrlRequest.builder()
                .queueName(config.getQueueName())
                .build();
            
            try {
                sqsClient.getQueueUrl(getQueueUrlRequest);
            } catch (QueueDoesNotExistException e) {
                throw new IllegalStateException("SQS queue does not exist: " + config.getQueueName(), e);
            }
            
            status.set(ConsumerStatus.RUNNING);
            logger.info("SQS consumer started successfully");
            
            return null;
        });
    }
    
    @Override
    public Promise<List<QueueMessage>> poll() {
        if (status.get() != ConsumerStatus.RUNNING) {
            return Promise.ofException(
                new IllegalStateException("Consumer not running. Status: " + status.get())
            );
        }
        
        return Promise.ofBlocking(executor, () -> {
            int batchSize = Math.min(config.getBatchSize(), SQS_MAX_BATCH_SIZE);
            
            ReceiveMessageRequest request = ReceiveMessageRequest.builder()
                .queueUrl(config.getQueueUrl())
                .maxNumberOfMessages(batchSize)
                .waitTimeSeconds(config.getWaitTimeSeconds()) // Long polling
                .visibilityTimeout(config.getVisibilityTimeout())
                .messageAttributeNames("All")
                .attributeNames(QueueAttributeName.ALL)
                .build();
            
            ReceiveMessageResponse response = sqsClient.receiveMessage(request);
            
            List<QueueMessage> messages = new ArrayList<>();
            
            for (Message message : response.messages()) {
                String messageId = message.messageId();
                receiptHandles.put(messageId, message.receiptHandle());
                
                Map<String, String> metadata = new HashMap<>();
                metadata.put("messageId", message.messageId());
                metadata.put("receiptHandle", message.receiptHandle());
                metadata.put("md5OfBody", message.md5OfBody());
                
                // Add message attributes
                message.messageAttributes().forEach((key, attr) -> 
                    metadata.put("attr." + key, attr.stringValue())
                );
                
                // Add system attributes
                message.attributes().forEach((key, value) -> 
                    metadata.put("sys." + key.toString(), value)
                );
                
                messages.add(new QueueMessage(messageId, message.body(), metadata));
            }
            
            if (!messages.isEmpty()) {
                logger.debug("Polled {} messages from SQS", messages.size());
            }
            
            return messages;
        });
    }
    
    @Override
    public Promise<Void> acknowledge(String messageId) {
        return Promise.ofBlocking(executor, () -> {
            String receiptHandle = receiptHandles.remove(messageId);
            if (receiptHandle != null) {
                DeleteMessageRequest request = DeleteMessageRequest.builder()
                    .queueUrl(config.getQueueUrl())
                    .receiptHandle(receiptHandle)
                    .build();
                
                sqsClient.deleteMessage(request);
                logger.debug("Acknowledged (deleted) SQS message: {}", messageId);
            }
            return null;
        });
    }
    
    @Override
    public Promise<Void> nack(String messageId) {
        return Promise.ofBlocking(executor, () -> {
            String receiptHandle = receiptHandles.remove(messageId);
            if (receiptHandle != null) {
                // Change visibility timeout to 0 to make message immediately visible again
                ChangeMessageVisibilityRequest request = ChangeMessageVisibilityRequest.builder()
                    .queueUrl(config.getQueueUrl())
                    .receiptHandle(receiptHandle)
                    .visibilityTimeout(0)
                    .build();
                
                sqsClient.changeMessageVisibility(request);
                logger.warn("Nacked SQS message (immediate requeue): {}", messageId);
            }
            return null;
        });
    }
    
    @Override
    public Promise<Void> stop() {
        ConsumerStatus currentStatus = status.get();
        if (currentStatus == ConsumerStatus.STOPPED || currentStatus == ConsumerStatus.STOPPING) {
            return Promise.complete();
        }
        
        status.set(ConsumerStatus.STOPPING);
        
        return Promise.ofBlocking(executor, () -> {
            logger.info("Stopping SQS consumer");
            
            try {
                if (sqsClient != null) {
                    sqsClient.close();
                }
                
                executor.shutdown();
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } finally {
                status.set(ConsumerStatus.STOPPED);
                logger.info("SQS consumer stopped");
            }
            
            return null;
        });
    }
    
    @Override
    public ConsumerStatus getStatus() {
        return status.get();
    }
}
