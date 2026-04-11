/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.messaging.strategy.kafka;

import com.ghatana.platform.messaging.AbstractResilientConnector;
import com.ghatana.platform.messaging.strategy.QueueConsumerStrategy;
import io.activej.promise.Promise;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Kafka consumer strategy — polls a Kafka topic with explicit commit-after-process
 * semantics (at-least-once) using a virtual-thread poll loop.
 *
 * <p>Supply a {@code messageHandler} to process each record; the consumer only commits
 * after the handler returns without throwing.
 *
 * @doc.type class
 * @doc.purpose Kafka consumer strategy with real at-least-once consumption
 * @doc.layer infrastructure
 * @doc.pattern Strategy
 */
public class KafkaConsumerStrategy extends AbstractResilientConnector implements QueueConsumerStrategy {

    private final KafkaConsumerConfig config;
    private final Consumer<String> messageHandler;
    private volatile Thread pollingThread;
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * Construct with a message handler that receives each record value.
     *
     * @param config         Kafka consumer configuration
     * @param messageHandler handler called for each consumed message body
     */
    public KafkaConsumerStrategy(KafkaConsumerConfig config, Consumer<String> messageHandler) {
        super(config.retryConfig());
        this.config = config;
        this.messageHandler = messageHandler;
    }

    /** Construct with a no-op handler (for config-validation-only purposes). */
    public KafkaConsumerStrategy(KafkaConsumerConfig config) {
        this(config, payload -> {});
    }

    @Override
    public Promise<Void> start() {
        return Promise.ofBlocking(ioExecutor, () -> {
            if (running.compareAndSet(false, true)) {
                Properties props = new Properties();
                props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.bootstrapServers());
                props.put(ConsumerConfig.GROUP_ID_CONFIG, config.groupId());
                props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
                props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
                props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
                props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
                if (config.isTlsEnabled()) {
                    props.put("security.protocol", "SSL");
                }

                pollingThread = Thread.ofVirtual().name("kafka-consumer-" + config.topic()).start(() -> {
                    try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
                        consumer.subscribe(List.of(config.topic()));
                        while (running.get()) {
                            ConsumerRecords<String, String> records =
                                consumer.poll(Duration.ofMillis(config.pollTimeoutMs()));
                            records.forEach(record -> {
                                try {
                                    messageHandler.accept(record.value());
                                    consumer.commitSync();
                                } catch (Exception e) {
                                    log.error("Error processing Kafka record offset={}: {}",
                                        record.offset(), e.getMessage(), e);
                                    // Do NOT commit — message will be re-delivered
                                }
                            });
                        }
                    } catch (Exception e) {
                        if (running.get()) {
                            log.error("Kafka consumer fatal error for topic={}: {}", config.topic(), e.getMessage(), e);
                        }
                    }
                });
                log.info("KafkaConsumerStrategy started — topic={} group={}", config.topic(), config.groupId());
            }
            return null;
        });
    }

    @Override
    public Promise<Void> stop() {
        return Promise.ofBlocking(ioExecutor, () -> {
            running.set(false);
            Thread t = pollingThread;
            if (t != null) {
                t.interrupt();
            }
            log.info("KafkaConsumerStrategy stopped — topic={}", config.topic());
            return null;
        });
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }
}
