/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.connector.strategy.kafka;

import com.ghatana.aep.connector.AbstractResilientConnector;
import com.ghatana.aep.connector.strategy.QueueMessage;
import com.ghatana.aep.connector.strategy.QueueProducerStrategy;
import io.activej.promise.Promise;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Kafka producer strategy — sends messages to a Kafka topic using the official
 * Kafka client with idempotent delivery, at-least-once guarantees, and
 * exponential-backoff retry inherited from {@link AbstractResilientConnector}.
 *
 * @doc.type class
 * @doc.purpose Kafka producer strategy with real I/O and retry resilience
 * @doc.layer infrastructure
 * @doc.pattern Strategy
 */
public class KafkaProducerStrategy extends AbstractResilientConnector implements QueueProducerStrategy {

    private final KafkaProducerConfig config;
    private final AtomicReference<KafkaProducer<String, String>> producer = new AtomicReference<>();
    private volatile boolean running = false;

    public KafkaProducerStrategy(KafkaProducerConfig config) {
        super(config.retryConfig());
        this.config = config;
    }

    @Override
    public boolean send(QueueMessage message) {
        KafkaProducer<String, String> p = producer.get();
        if (p == null || !running) {
            throw new IllegalStateException("KafkaProducerStrategy is not started");
        }
        try {
            return withRetry("kafka.send", () -> {
                ProducerRecord<String, String> record =
                    new ProducerRecord<>(config.topic(), message.getId(), message.getBody());
                // Propagate message headers into Kafka record headers
                message.getHeaders().forEach((k, v) ->
                    record.headers().add(k, v.getBytes(StandardCharsets.UTF_8)));
                p.send(record).get(); // block until broker ACK
                log.debug("Sent message key={} to topic={}", message.getId(), config.topic());
                return true;
            });
        } catch (Exception e) {
            log.error("Failed to send message to Kafka topic={}: {}", config.topic(), e.getMessage(), e);
            return false;
        }
    }

    @Override
    public Promise<Void> start() {
        return Promise.ofBlocking(ioExecutor, () -> {
            Properties props = new Properties();
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.bootstrapServers());
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            props.put(ProducerConfig.BATCH_SIZE_CONFIG, config.batchSize());
            props.put(ProducerConfig.ACKS_CONFIG, "all");
            props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
            props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1);
            if (config.isTlsEnabled()) {
                props.put("security.protocol", "SSL");
            }
            producer.set(new KafkaProducer<>(props));
            running = true;
            log.info("KafkaProducerStrategy started — topic={}", config.topic());
            return null;
        });
    }

    @Override
    public Promise<Void> stop() {
        return Promise.ofBlocking(ioExecutor, () -> {
            running = false;
            KafkaProducer<String, String> p = producer.getAndSet(null);
            if (p != null) {
                p.flush();
                p.close();
            }
            log.info("KafkaProducerStrategy stopped — topic={}", config.topic());
            return null;
        });
    }

    @Override
    public Promise<Void> flush() {
        return Promise.ofBlocking(ioExecutor, () -> {
            KafkaProducer<String, String> p = producer.get();
            if (p != null) {
                p.flush();
            }
            return null;
        });
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
