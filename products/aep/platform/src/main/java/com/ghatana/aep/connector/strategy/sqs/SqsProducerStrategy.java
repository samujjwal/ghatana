package com.ghatana.aep.connector.strategy.sqs;

import com.ghatana.aep.connector.strategy.QueueMessage;
import com.ghatana.aep.connector.strategy.QueueProducerStrategy;
import io.activej.promise.Promise;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AWS SQS producer strategy implementation.
 * 
 * @doc.type class
 * @doc.purpose AWS SQS message producer
 * @doc.layer infrastructure
 * @doc.pattern Strategy
 */
public class SqsProducerStrategy implements QueueProducerStrategy {
    
    private final SqsConfig config;
    private final Executor executor;
    private final AtomicReference<SqsClient> sqsClientRef = new AtomicReference<>();
    private final AtomicReference<ProducerStatus> statusRef = new AtomicReference<>(ProducerStatus.NOT_STARTED);
    private final Map<String, String> resolvedQueueUrls = new ConcurrentHashMap<>();
    
    public SqsProducerStrategy(SqsConfig config, Executor executor) {
        this.config = config;
        this.executor = executor;
    }
    
    @Override
    public Promise<Void> start() {
        return Promise.ofBlocking(executor, () -> {
            SqsClient client = SqsClient.builder()
                .region(Region.of(config.getRegion()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
            
            sqsClientRef.set(client);
            
            // Resolve queue URL if not provided
            if (config.getQueueUrl() == null) {
                String queueUrl = client.getQueueUrl(r -> r.queueName(config.getQueueName())).queueUrl();
                resolvedQueueUrls.put(config.getQueueName(), queueUrl);
            } else {
                resolvedQueueUrls.put(config.getQueueName(), config.getQueueUrl());
            }
            
            statusRef.set(ProducerStatus.RUNNING);
            return null;
        });
    }
    
    @Override
    public Promise<Void> stop() {
        return Promise.ofBlocking(executor, () -> {
            SqsClient client = sqsClientRef.getAndSet(null);
            if (client != null) {
                client.close();
            }
            statusRef.set(ProducerStatus.STOPPED);
            return null;
        });
    }
    
    public Promise<String> send(QueueMessage message) {
        return Promise.ofBlocking(executor, () -> {
            SqsClient client = sqsClientRef.get();
            if (client == null) {
                throw new IllegalStateException("Producer not started");
            }
            
            String queueUrl = resolveQueueUrl(config.getQueueName());
            
            SendMessageRequest.Builder requestBuilder = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(message.payload());
            
            // Add message attributes from metadata
            if (message.metadata() != null && !message.metadata().isEmpty()) {
                requestBuilder.messageAttributes(
                    message.metadata().entrySet().stream()
                        .collect(java.util.stream.Collectors.toMap(
                            Map.Entry::getKey,
                            e -> software.amazon.awssdk.services.sqs.model.MessageAttributeValue.builder()
                                .dataType("String")
                                .stringValue(e.getValue())
                                .build()
                        ))
                );
            }
            
            SendMessageResponse response = client.sendMessage(requestBuilder.build());
            return response.messageId();
        });
    }

    @Override
    public Promise<String> send(String key, String value) {
        return send(new QueueMessage(key, value, Map.of()));
    }

    @Override
    public Promise<String> send(String key, String value, Map<String, String> headers) {
        // Use key as message group ID or just ignore if standard SQS?
        // QueueMessage key usage depends on impl.
        return send(new QueueMessage(key, value, headers));
    }
    
    @Override
    public Promise<List<String>> sendBatch(List<QueueMessage> messages) {
        return Promise.ofBlocking(executor, () -> {
            SqsClient client = sqsClientRef.get();
            if (client == null) {
                throw new IllegalStateException("Producer not started");
            }
            
            String queueUrl = resolveQueueUrl(config.getQueueName());
            List<String> allMessageIds = new ArrayList<>();
            
            // SQS batch max is 10 messages
            List<List<QueueMessage>> batches = partition(messages, 10);
            
            for (List<QueueMessage> batch : batches) {
                List<SendMessageBatchRequestEntry> entries = new ArrayList<>();
                int index = 0;
                for (QueueMessage msg : batch) {
                    String id = msg.messageId() != null ? msg.messageId() : 
                        UUID.randomUUID().toString() + "-" + index++;
                    
                    SendMessageBatchRequestEntry.Builder entryBuilder = SendMessageBatchRequestEntry.builder()
                        .id(id)
                        .messageBody(msg.payload());
                    
                    if (msg.metadata() != null && !msg.metadata().isEmpty()) {
                        entryBuilder.messageAttributes(
                            msg.metadata().entrySet().stream()
                                .collect(java.util.stream.Collectors.toMap(
                                    Map.Entry::getKey,
                                    e -> software.amazon.awssdk.services.sqs.model.MessageAttributeValue.builder()
                                        .dataType("String")
                                        .stringValue(e.getValue())
                                        .build()
                                ))
                        );
                    }
                    
                    entries.add(entryBuilder.build());
                }
                
                SendMessageBatchRequest batchRequest = SendMessageBatchRequest.builder()
                    .queueUrl(queueUrl)
                    .entries(entries)
                    .build();
                
                SendMessageBatchResponse response = client.sendMessageBatch(batchRequest);

                if (response.hasSuccessful()) {
                    response.successful().forEach(entry -> allMessageIds.add(entry.messageId()));
                }
                
                // Handle failed messages if any
                if (response.hasFailed() && !response.failed().isEmpty()) {
                    // In production, implement retry logic or dead letter handling
                    throw new RuntimeException("Failed to send " + response.failed().size() + " messages");
                }
            }
            
            return allMessageIds;
        });
    }

    @Override
    public Promise<Void> flush() {
        return Promise.complete();
    }
    
    @Override
    public ProducerStatus getStatus() {
        return statusRef.get();
    }
    
    private String resolveQueueUrl(String queueName) {
        return resolvedQueueUrls.computeIfAbsent(queueName, name -> {
            SqsClient client = sqsClientRef.get();
            return client.getQueueUrl(r -> r.queueName(name)).queueUrl();
        });
    }
    
    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }
}
