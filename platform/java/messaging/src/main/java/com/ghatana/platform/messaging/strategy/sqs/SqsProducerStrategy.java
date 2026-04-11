/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.connector.strategy.sqs;

import com.ghatana.aep.connector.AbstractResilientConnector;
import com.ghatana.aep.connector.strategy.QueueMessage;
import com.ghatana.aep.connector.strategy.QueueProducerStrategy;
import io.activej.promise.Promise;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.net.URI;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SQS producer strategy — sends messages to an AWS SQS queue using the AWS SDK v2,
 * with exponential-backoff retry inherited from {@link AbstractResilientConnector}.
 *
 * @doc.type class
 * @doc.purpose SQS producer strategy with real AWS SQS I/O
 * @doc.layer infrastructure
 * @doc.pattern Strategy
 */
public class SqsProducerStrategy extends AbstractResilientConnector implements QueueProducerStrategy {

    private final SqsConfig config;
    private volatile SqsClient sqsClient;
    private volatile boolean running = false;

    public SqsProducerStrategy(SqsConfig config) {
        super(config.retryConfig());
        this.config = config;
    }

    @Override
    public boolean send(QueueMessage message) {
        if (!running || sqsClient == null) {
            throw new IllegalStateException("SqsProducerStrategy is not started");
        }
        try {
            return withRetry("sqs.send", () -> {
                Map<String, MessageAttributeValue> attrs = message.getHeaders().entrySet().stream()
                    .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> MessageAttributeValue.builder()
                            .dataType("String")
                            .stringValue(e.getValue())
                            .build()
                    ));
                SendMessageRequest request = SendMessageRequest.builder()
                    .queueUrl(config.queueUrl())
                    .messageBody(message.getBody())
                    .messageAttributes(attrs)
                    .build();
                sqsClient.sendMessage(request);
                log.debug("Sent SQS message key={} to queue={}", message.getId(), config.queueUrl());
                return true;
            });
        } catch (Exception e) {
            log.error("Failed to send message to SQS queue={}: {}", config.queueUrl(), e.getMessage(), e);
            return false;
        }
    }

    @Override
    public Promise<Void> start() {
        return Promise.ofBlocking(ioExecutor, () -> {
            var builder = SqsClient.builder().region(Region.of(config.region()));
            if (config.endpointOverride() != null && !config.endpointOverride().isBlank()) {
                builder.endpointOverride(URI.create(config.endpointOverride()));
            }
            if (config.accessKey() != null && config.secretKey() != null) {
                builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(config.accessKey(), config.secretKey())
                ));
            }
            sqsClient = builder.build();
            running = true;
            log.info("SqsProducerStrategy started — queue={}", config.queueUrl());
            return null;
        });
    }

    @Override
    public Promise<Void> stop() {
        return Promise.ofBlocking(ioExecutor, () -> {
            running = false;
            SqsClient c = sqsClient;
            if (c != null) {
                sqsClient = null;
                c.close();
            }
            log.info("SqsProducerStrategy stopped");
            return null;
        });
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
