package com.ghatana.aep.connector.strategy.rabbitmq;

import com.ghatana.aep.connector.strategy.QueueConsumerStrategy;
import com.ghatana.aep.connector.strategy.QueueMessage;
import com.rabbitmq.client.*;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * RabbitMQ consumer strategy using ActiveJ Promise integration.
 * 
 * <p>This implementation:
 * <ul>
 *   <li>Uses manual acknowledgment for at-least-once delivery</li>
 *   <li>Supports basic.get for synchronous polling</li>
 *   <li>Handles connection recovery automatically</li>
 *   <li>Configurable prefetch count (QoS)</li>
 * </ul>
 * 
 * @doc.type class
 * @doc.purpose RabbitMQ consumer implementation
 * @doc.layer infrastructure
 * @doc.pattern Strategy
 */
public class RabbitMQConsumerStrategy implements QueueConsumerStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(RabbitMQConsumerStrategy.class);
    
    private final RabbitMQConfig config;
    private final Eventloop eventloop;
    private final ExecutorService executor;
    private final AtomicReference<ConsumerStatus> status;
    private final Map<String, Long> deliveryTags;
    
    private volatile Connection connection;
    private volatile Channel channel;
    
    /**
     * Creates a new RabbitMQConsumerStrategy.
     * 
     * @param config Consumer configuration
     * @param eventloop ActiveJ eventloop for async operations
     */
    public RabbitMQConsumerStrategy(RabbitMQConfig config, Eventloop eventloop) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop cannot be null");
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "rabbitmq-consumer-" + config.getQueueName());
            t.setDaemon(true);
            return t;
        });
        this.status = new AtomicReference<>(ConsumerStatus.NOT_STARTED);
        this.deliveryTags = new ConcurrentHashMap<>();
    }
    
    @Override
    public Promise<Void> start() {
        if (!status.compareAndSet(ConsumerStatus.NOT_STARTED, ConsumerStatus.STARTING)) {
            return Promise.ofException(
                new IllegalStateException("Consumer already started or starting")
            );
        }
        
        return Promise.ofBlocking(executor, () -> {
            logger.info("Starting RabbitMQ consumer. queue={}", config.getQueueName());
            
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(config.getHost());
            factory.setPort(config.getPort());
            factory.setUsername(config.getUsername());
            factory.setPassword(config.getPassword());
            factory.setVirtualHost(config.getVirtualHost());
            
            // Enable automatic recovery
            factory.setAutomaticRecoveryEnabled(true);
            factory.setNetworkRecoveryInterval(5000);
            
            connection = factory.newConnection();
            channel = connection.createChannel();
            
            // Declare queue (idempotent)
            channel.queueDeclare(
                config.getQueueName(),
                config.isDurable(),
                config.isExclusive(),
                config.isAutoDelete(),
                null
            );
            
            // Set QoS (prefetch count)
            channel.basicQos(config.getPrefetchCount());
            
            status.set(ConsumerStatus.RUNNING);
            logger.info("RabbitMQ consumer started successfully");
            
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
            List<QueueMessage> messages = new ArrayList<>();
            
            for (int i = 0; i < config.getBatchSize(); i++) {
                GetResponse response = channel.basicGet(config.getQueueName(), false); // noAck=false
                
                if (response == null) {
                    break; // No more messages
                }
                
                String messageId = UUID.randomUUID().toString();
                deliveryTags.put(messageId, response.getEnvelope().getDeliveryTag());
                
                Map<String, String> metadata = new HashMap<>();
                metadata.put("exchange", response.getEnvelope().getExchange());
                metadata.put("routingKey", response.getEnvelope().getRoutingKey());
                metadata.put("deliveryTag", String.valueOf(response.getEnvelope().getDeliveryTag()));
                metadata.put("redelivered", String.valueOf(response.getEnvelope().isRedeliver()));
                
                // Add message properties as metadata
                AMQP.BasicProperties props = response.getProps();
                if (props != null) {
                    if (props.getMessageId() != null) {
                        metadata.put("messageId", props.getMessageId());
                    }
                    if (props.getCorrelationId() != null) {
                        metadata.put("correlationId", props.getCorrelationId());
                    }
                    if (props.getContentType() != null) {
                        metadata.put("contentType", props.getContentType());
                    }
                    if (props.getHeaders() != null) {
                        props.getHeaders().forEach((k, v) -> 
                            metadata.put("header." + k, String.valueOf(v))
                        );
                    }
                }
                
                messages.add(new QueueMessage(
                    messageId,
                    new String(response.getBody()),
                    metadata
                ));
            }
            
            if (!messages.isEmpty()) {
                logger.debug("Polled {} messages from RabbitMQ", messages.size());
            }
            
            return messages;
        });
    }
    
    @Override
    public Promise<Void> acknowledge(String messageId) {
        return Promise.ofBlocking(executor, () -> {
            Long deliveryTag = deliveryTags.remove(messageId);
            if (deliveryTag != null) {
                channel.basicAck(deliveryTag, false); // multiple=false
                logger.debug("Acknowledged message: {}", messageId);
            }
            return null;
        });
    }
    
    @Override
    public Promise<Void> nack(String messageId) {
        return Promise.ofBlocking(executor, () -> {
            Long deliveryTag = deliveryTags.remove(messageId);
            if (deliveryTag != null) {
                channel.basicNack(
                    deliveryTag,
                    false, // multiple=false
                    true   // requeue=true
                );
                logger.warn("Nacked message (will requeue): {}", messageId);
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
            logger.info("Stopping RabbitMQ consumer");
            
            try {
                if (channel != null && channel.isOpen()) {
                    channel.close();
                }
                if (connection != null && connection.isOpen()) {
                    connection.close();
                }
                
                executor.shutdown();
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (Exception e) {
                logger.error("Error stopping RabbitMQ consumer", e);
            } finally {
                status.set(ConsumerStatus.STOPPED);
                logger.info("RabbitMQ consumer stopped");
            }
            
            return null;
        });
    }
    
    @Override
    public ConsumerStatus getStatus() {
        return status.get();
    }
}
