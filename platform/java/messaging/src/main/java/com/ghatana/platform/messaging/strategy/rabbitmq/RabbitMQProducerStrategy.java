/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.messaging.strategy.rabbitmq;

import com.ghatana.platform.messaging.AbstractResilientConnector;
import com.ghatana.platform.messaging.strategy.QueueMessage;
import com.ghatana.platform.messaging.strategy.QueueProducerStrategy;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.MessageProperties;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * RabbitMQ producer strategy — publishes messages to a queue with persistence
 * (delivery mode 2) using the official AMQP client library.
 *
 * <p>Each message is published with a unique {@code correlationId} returned as
 * the message ID. The queue is declared durable on connect so the producer can
 * run before consumers and messages survive broker restarts.
 *
 * @doc.type class
 * @doc.purpose RabbitMQ producer strategy with persistent AMQP delivery
 * @doc.layer infrastructure
 * @doc.pattern Strategy
 */
public class RabbitMQProducerStrategy extends AbstractResilientConnector implements QueueProducerStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(RabbitMQProducerStrategy.class);

    private final RabbitMQConfig config;
    private volatile Connection connection;
    private volatile Channel channel;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public RabbitMQProducerStrategy(RabbitMQConfig config) {
        super(config.retryConfig());
        this.config = config;
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
                    // Reuse pre-provisioned queues (e.g., DLQ args in integration tests)
                    // and only declare when absent.
                    ensureQueueExists(channel, config.queueName());
                    LOG.info("RabbitMQProducerStrategy started — queue={}", config.queueName());
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
                LOG.warn("Error during RabbitMQ producer shutdown: {}", e.getMessage());
            }
            LOG.info("RabbitMQProducerStrategy stopped");
            return null;
        });
    }

    @Override
    public boolean send(QueueMessage message) {
        try {
            if (!running.get() || channel == null || !channel.isOpen()) {
                LOG.error("Cannot send — channel not open");
                return false;
            }
            byte[] body = message.getBody().getBytes(StandardCharsets.UTF_8);
            var props = MessageProperties.PERSISTENT_TEXT_PLAIN.builder()
                    .correlationId(message.getId())
                    .messageId(UUID.randomUUID().toString())
                    .build();
            channel.basicPublish("", config.queueName(), props, body);
            LOG.debug("Published message to queue={} key={}", config.queueName(), message.getId());
            return true;
        } catch (Exception e) {
            LOG.error("Failed to publish message key={}: {}", message.getId(), e.getMessage(), e);
            return false;
        }
    }

    @Override
    public Promise<String> send(String key, String payload) {
        return Promise.ofBlocking(ioExecutor, () -> {
            if (!running.get() || channel == null || !channel.isOpen()) {
                throw new IllegalStateException("Producer channel not open");
            }
            String messageId = UUID.randomUUID().toString();
            byte[] body = payload.getBytes(StandardCharsets.UTF_8);
            var props = MessageProperties.PERSISTENT_TEXT_PLAIN.builder()
                    .correlationId(key)
                    .messageId(messageId)
                    .build();
            channel.basicPublish("", config.queueName(), props, body);
            LOG.debug("Published keyed message — queue={} key={} msgId={}", config.queueName(), key, messageId);
            return messageId;
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
            // Queue does not exist (or is inaccessible) — create the default durable queue.
            channel.queueDeclare(queueName, true, false, false, null);
        }
    }
}
