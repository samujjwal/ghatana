/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.connector.strategy.rabbitmq;

import com.ghatana.aep.connector.AbstractResilientConnector;
import com.ghatana.aep.connector.strategy.QueueConsumerStrategy;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import io.activej.promise.Promise;

import java.nio.charset.StandardCharsets;
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

    private final RabbitMQConfig config;
    private final Consumer<String> messageHandler;
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
                    // Durable queue, non-exclusive, non-auto-delete
                    channel.queueDeclare(config.queueName(), true, false, false, null);
                    channel.basicQos(1); // one message at a time for fair dispatch

                    channel.basicConsume(config.queueName(), false,
                        (consumerTag, delivery) -> {
                            long tag = delivery.getEnvelope().getDeliveryTag();
                            try {
                                String body = new String(delivery.getBody(), StandardCharsets.UTF_8);
                                messageHandler.accept(body);
                                channel.basicAck(tag, false);
                            } catch (Exception e) {
                                log.error("Error processing RabbitMQ message tag={}: {}", tag, e.getMessage(), e);
                                // Nack with requeue so the message is retried
                                channel.basicNack(tag, false, true);
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
}
