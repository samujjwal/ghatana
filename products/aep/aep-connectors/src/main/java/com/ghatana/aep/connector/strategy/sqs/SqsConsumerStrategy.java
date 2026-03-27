/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.connector.strategy.sqs;

import com.ghatana.aep.connector.AbstractResilientConnector;
import com.ghatana.aep.connector.strategy.QueueConsumerStrategy;
import io.activej.promise.Promise;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * SQS consumer strategy — long-polls an AWS SQS queue, processing each message
 * with explicit delete-after-process (at-least-once) semantics.
 *
 * <p>If the message handler throws, the message is not deleted and becomes
 * visible again after the queue's visibility timeout, enabling natural retry.
 *
 * @doc.type class
 * @doc.purpose SQS consumer strategy with real at-least-once consumption
 * @doc.layer infrastructure
 * @doc.pattern Strategy
 */
public class SqsConsumerStrategy extends AbstractResilientConnector implements QueueConsumerStrategy {

    private final SqsConfig config;
    private final Consumer<String> messageHandler;
    private volatile SqsClient sqsClient;
    private volatile Thread pollingThread;
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * Construct with a handler that receives each SQS message body.
     *
     * @param config         SQS configuration
     * @param messageHandler handler called for each received message body
     */
    public SqsConsumerStrategy(SqsConfig config, Consumer<String> messageHandler) {
        super(config.retryConfig());
        this.config = config;
        this.messageHandler = messageHandler;
    }

    /** Construct with a no-op handler. */
    public SqsConsumerStrategy(SqsConfig config) {
        this(config, body -> {});
    }

    @Override
    public Promise<Void> start() {
        return Promise.ofBlocking(ioExecutor, () -> {
            if (running.compareAndSet(false, true)) {
                var builder = SqsClient.builder().region(Region.of(config.region()));
                if (config.accessKey() != null && config.secretKey() != null) {
                    builder.credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(config.accessKey(), config.secretKey())
                    ));
                }
                sqsClient = builder.build();

                pollingThread = Thread.ofVirtual().name("sqs-consumer").start(() -> {
                    while (running.get()) {
                        try {
                            ReceiveMessageRequest request = ReceiveMessageRequest.builder()
                                .queueUrl(config.queueUrl())
                                .maxNumberOfMessages(config.maxMessages())
                                .waitTimeSeconds(config.waitTimeSeconds())
                                .build();
                            List<Message> messages = sqsClient.receiveMessage(request).messages();
                            for (Message msg : messages) {
                                try {
                                    messageHandler.accept(msg.body());
                                    // Delete only after successful processing
                                    sqsClient.deleteMessage(DeleteMessageRequest.builder()
                                        .queueUrl(config.queueUrl())
                                        .receiptHandle(msg.receiptHandle())
                                        .build());
                                } catch (Exception e) {
                                    log.error("Error processing SQS message, will retry: {}", e.getMessage(), e);
                                    // Intentionally not deleting — SQS will re-enqueue after visibility timeout
                                }
                            }
                        } catch (Exception e) {
                            if (running.get()) {
                                log.error("SQS polling error: {}", e.getMessage(), e);
                            }
                            try {
                                Thread.sleep(1_000);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    }
                });
                log.info("SqsConsumerStrategy started — queue={}", config.queueUrl());
            }
            return null;
        });
    }

    @Override
    public Promise<Void> stop() {
        return Promise.ofBlocking(ioExecutor, () -> {
            running.set(false);
            Thread t = pollingThread;
            if (t != null) t.interrupt();
            SqsClient c = sqsClient;
            if (c != null) {
                sqsClient = null;
                c.close();
            }
            log.info("SqsConsumerStrategy stopped");
            return null;
        });
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }
}
