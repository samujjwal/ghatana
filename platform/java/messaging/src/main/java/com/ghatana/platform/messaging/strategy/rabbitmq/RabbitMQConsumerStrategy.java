/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.messaging.strategy.rabbitmq;

import com.ghatana.platform.messaging.AbstractResilientConnector;
import com.ghatana.platform.messaging.strategy.QueueConsumerStrategy;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Delivery;
import io.activej.promise.Promise;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * RabbitMQ consumer strategy — subscribes to a queue with manual acknowledgement
 * (at-least-once) using the official AMQP client library.
 *
 * <p>Successful handler execution triggers {@code basicAck}; exceptions trigger
 * {@code basicNack} with {@code requeue=true} so the message is re-enqueued.
 *
 * @doc.type class
 * @doc.purpose RabbitMQ consumer strategy with real AMQP I/O
 * @doc.layer infrastructure
 * @doc.pattern Strategy
 */
public class RabbitMQConsumerStrategy extends AbstractResilientConnector implements QueueConsumerStrategy {

    private static final String DELIVERY_ATTEMPT_HEADER = "x-ghatana-delivery-attempt";

    private final RabbitMQConfig config;
    private volatile Consumer<String> messageHandler;
    private volatile Connection connection;
    private volatile Channel channel;
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * Construct with a message handler that receives each message body as a string.
     *
     * @param config         RabbitMQ configuration
     * @param messageHandler handler called with each message body
     */
    public RabbitMQConsumerStrategy(RabbitMQConfig config, Consumer<String> messageHandler) {
        super(config.retryConfig());
        this.config = config;
        this.messageHandler = messageHandler;
    }

    /** Construct with a no-op handler. */
    public RabbitMQConsumerStrategy(RabbitMQConfig config) {
        this(config, body -> {});
    }

    @Override
    public void setMessageHandler(Consumer<String> handler) {
        this.messageHandler = handler != null ? handler : body -> {};
    }

    @Override
    public Promise<Void> start() {
        return Promise.ofBlocking(ioExecutor, () -> {
            if (running.compareAndSet(false, true)) {
                try {
                    ConnectionFactory factory = new ConnectionFactory();
                    factory.setHost(config.rabbitHost());
                    factory.setPort(config.rabbitPort());
                    factory.setUsername(config.username());
                    factory.setPassword(config.password());
                    factory.setVirtualHost(config.virtualHost());
                    if (config.isTlsEnabled()) {
                        factory.useSslProtocol();
                    }

                    connection = factory.newConnection();
                    channel = connection.createChannel();
                    // Reuse pre-provisioned queues (e.g., DLQ args) and declare only when absent.
                    ensureQueueExists(channel, config.queueName());
                    channel.basicQos(1); // one message at a time for fair dispatch

                    channel.basicConsume(config.queueName(), false,
                        (consumerTag, delivery) -> {
                            long tag = delivery.getEnvelope().getDeliveryTag();
                            String body = new String(delivery.getBody(), StandardCharsets.UTF_8);
                            try {
                                messageHandler.accept(body);
                                channel.basicAck(tag, false);
                            } catch (Exception e) {
                                log.error("Error processing RabbitMQ message tag={}: {}", tag, e.getMessage(), e);
                                handleProcessingFailure(delivery, tag, body);
                            }
                        },
                        consumerTag -> log.warn("RabbitMQ consumer cancelled: {}", consumerTag)
                    );
                    log.info("RabbitMQConsumerStrategy started — queue={}", config.queueName());
                } catch (Exception e) {
                    running.set(false);
                    throw e;
                }
            }
            return null;
        });
    }

    @Override
    public Promise<Void> stop() {
        return Promise.ofBlocking(ioExecutor, () -> {
            running.set(false);
            try {
                Channel ch = channel;
                if (ch != null && ch.isOpen()) ch.close();
                Connection conn = connection;
                if (conn != null && conn.isOpen()) conn.close();
            } catch (Exception e) {
                log.warn("Error during RabbitMQ shutdown: {}", e.getMessage());
            }
            log.info("RabbitMQConsumerStrategy stopped");
            return null;
        });
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    private static void ensureQueueExists(Channel channel, String queueName) throws Exception {
        try {
            channel.queueDeclarePassive(queueName);
        } catch (IOException passiveDeclareFailure) {
            channel.queueDeclare(queueName, true, false, false, null);
        }
    }

    private void handleProcessingFailure(Delivery delivery, long tag, String body) throws IOException {
        int deliveryAttempt = resolveDeliveryAttempt(delivery);
        if (deliveryAttempt >= config.maxDeliveryAttempts()) {
            log.warn("Rejecting RabbitMQ message without requeue after {} attempts; queue={}",
                deliveryAttempt, config.queueName());
            channel.basicReject(tag, false);
            return;
        }

        try {
            republishForRetry(delivery, body, deliveryAttempt + 1);
            channel.basicAck(tag, false);
        } catch (IOException retryFailure) {
            log.error("Failed to republish RabbitMQ message for retry; falling back to broker requeue", retryFailure);
            channel.basicNack(tag, false, true);
        }
    }

    private int resolveDeliveryAttempt(Delivery delivery) {
        Map<String, Object> headers = delivery.getProperties().getHeaders();
        if (headers == null) {
            return 1;
        }

        Object rawAttempt = headers.get(DELIVERY_ATTEMPT_HEADER);
        if (rawAttempt instanceof Number number) {
            return Math.max(1, number.intValue());
        }
        if (rawAttempt instanceof String text) {
            try {
                return Math.max(1, Integer.parseInt(text));
            } catch (NumberFormatException ignored) {
                return 1;
            }
        }
        return 1;
    }

    private void republishForRetry(Delivery delivery, String body, int nextDeliveryAttempt) throws IOException {
        Map<String, Object> retryHeaders = new HashMap<>();
        Map<String, Object> existingHeaders = delivery.getProperties().getHeaders();
        if (existingHeaders != null) {
            retryHeaders.putAll(existingHeaders);
        }
        retryHeaders.put(DELIVERY_ATTEMPT_HEADER, nextDeliveryAttempt);

        AMQP.BasicProperties properties = delivery.getProperties();
        AMQP.BasicProperties retryProperties = new AMQP.BasicProperties.Builder()
            .contentType(properties.getContentType())
            .contentEncoding(properties.getContentEncoding())
            .headers(retryHeaders)
            .deliveryMode(properties.getDeliveryMode())
            .priority(properties.getPriority())
            .correlationId(properties.getCorrelationId())
            .replyTo(properties.getReplyTo())
            .expiration(properties.getExpiration())
            .messageId(properties.getMessageId())
            .timestamp(properties.getTimestamp())
            .type(properties.getType())
            .userId(properties.getUserId())
            .appId(properties.getAppId())
            .clusterId(properties.getClusterId())
            .build();

        String routingKey = delivery.getEnvelope().getRoutingKey() != null
            ? delivery.getEnvelope().getRoutingKey()
            : config.queueName();
        channel.basicPublish("", routingKey, retryProperties, body.getBytes(StandardCharsets.UTF_8));
    }
}
