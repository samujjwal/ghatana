/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.di;

import com.ghatana.aep.connector.strategy.QueueConsumerStrategy;
import com.ghatana.aep.connector.strategy.QueueProducerStrategy;
import com.ghatana.aep.connector.strategy.http.HttpIngressConfig;
import com.ghatana.aep.connector.strategy.http.HttpIngressStrategy;
import com.ghatana.aep.connector.strategy.http.HttpPollingIngressStrategy;
import com.ghatana.aep.connector.strategy.kafka.KafkaConsumerConfig;
import com.ghatana.aep.connector.strategy.kafka.KafkaConsumerStrategy;
import com.ghatana.aep.connector.strategy.kafka.KafkaProducerConfig;
import com.ghatana.aep.connector.strategy.kafka.KafkaProducerStrategy;
import com.ghatana.aep.connector.strategy.rabbitmq.RabbitMQConfig;
import com.ghatana.aep.connector.strategy.rabbitmq.RabbitMQConsumerStrategy;
import com.ghatana.aep.connector.strategy.s3.DefaultS3StorageStrategy;
import com.ghatana.aep.connector.strategy.s3.S3Config;
import com.ghatana.aep.connector.strategy.s3.S3StorageStrategy;
import com.ghatana.aep.connector.strategy.sqs.SqsConfig;
import com.ghatana.aep.connector.strategy.sqs.SqsConsumerStrategy;
import com.ghatana.aep.connector.strategy.sqs.SqsProducerStrategy;
import io.activej.eventloop.Eventloop;
import io.activej.inject.annotation.Named;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * ActiveJ DI module for AEP connector strategies.
 *
 * <p>Provides message queue and storage connector strategies for all supported
 * infrastructure backends:
 * <ul>
 *   <li><b>Kafka</b> — consumer and producer using ActiveJ eventloop integration</li>
 *   <li><b>RabbitMQ</b> — consumer with AMQP protocol support</li>
 *   <li><b>SQS</b> — consumer and producer for AWS SQS</li>
 *   <li><b>S3</b> — storage strategy with multipart upload support</li>
 *   <li><b>HTTP</b> — polling ingress strategy</li>
 * </ul>
 *
 * <p>Each connector is provided with a {@code @Named} qualifier to allow
 * injection of specific strategy types. The module also provides a
 * consolidated map of all consumer and producer strategies accessible
 * by name.
 *
 * <p><b>Dependencies:</b> Requires bindings from {@link AepCoreModule}
 * (for {@link Eventloop}, {@link ExecutorService}, {@link ScheduledExecutorService}).
 * Connector configs (e.g., {@link KafkaConsumerConfig}) are provided with
 * sensible defaults that can be overridden.
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * Injector injector = Injector.of(
 *     new AepCoreModule(),
 *     new AepConnectorModule()
 * );
 * // Named injection
 * QueueConsumerStrategy kafka = injector.getInstance(
 *     Key.of(QueueConsumerStrategy.class, "kafka"));
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose ActiveJ DI module for all AEP connector strategies
 * @doc.layer product
 * @doc.pattern Module, Strategy
 * @see QueueConsumerStrategy
 * @see QueueProducerStrategy
 * @see S3StorageStrategy
 */
public class AepConnectorModule extends AbstractModule {

    // ═══════════════════════════════════════════════════════════════
    //  Default Configs
    // ═══════════════════════════════════════════════════════════════

    /**
     * Provides default Kafka consumer configuration.
     *
     * <p>Connects to {@code localhost:9092} with consumer group {@code aep-consumer-group}
     * and subscribes to the {@code events} topic. Override by providing your own
     * {@link KafkaConsumerConfig} binding.
     *
     * @return default Kafka consumer config
     */
    @Provides
    KafkaConsumerConfig kafkaConsumerConfig() {
        return KafkaConsumerConfig.builder()
                .bootstrapServers("localhost:9092")
                .groupId("aep-consumer-group")
                .topics(List.of("events"))
                .batchSize(100)
                .build();
    }

    /**
     * Provides default Kafka producer configuration.
     *
     * @return default Kafka producer config
     */
    @Provides
    KafkaProducerConfig kafkaProducerConfig() {
        return KafkaProducerConfig.builder()
                .bootstrapServers("localhost:9092")
                .topic("events-out")
                .build();
    }

    /**
     * Provides default RabbitMQ configuration.
     *
     * @return default RabbitMQ config
     */
    @Provides
    RabbitMQConfig rabbitMQConfig() {
        return RabbitMQConfig.builder()
                .host("localhost")
                .port(5672)
                .queueName("aep-events")
                .build();
    }

    /**
     * Provides default SQS configuration.
     *
     * @return default SQS config
     */
    @Provides
    SqsConfig sqsConfig() {
        return SqsConfig.builder()
                .region("us-east-1")
                .queueName("aep-events")
                .queueUrl("https://sqs.us-east-1.amazonaws.com/000000000000/aep-events")
                .build();
    }

    /**
     * Provides default S3 configuration.
     *
     * @return default S3 config
     */
    @Provides
    S3Config s3Config() {
        return S3Config.builder()
                .region("us-east-1")
                .bucketName("aep-storage")
                .build();
    }

    /**
     * Provides default HTTP ingress configuration.
     *
     * @return default HTTP ingress config
     */
    @Provides
    HttpIngressConfig httpIngressConfig() {
        return HttpIngressConfig.builder()
                .endpoint("http://localhost:8080/events")
                .build();
    }

    // ═══════════════════════════════════════════════════════════════
    //  Connector Strategies
    // ═══════════════════════════════════════════════════════════════

    /**
     * Provides the Kafka consumer strategy.
     *
     * @param config    Kafka consumer config
     * @param eventloop ActiveJ event loop for async integration
     * @return named Kafka consumer
     */
    @Provides
    @Named("kafka")
    QueueConsumerStrategy kafkaConsumer(KafkaConsumerConfig config, Eventloop eventloop) {
        return new KafkaConsumerStrategy(config, eventloop);
    }

    /**
     * Provides the Kafka producer strategy.
     *
     * @param config    Kafka producer config
     * @param eventloop ActiveJ event loop
     * @return named Kafka producer
     */
    @Provides
    @Named("kafka")
    QueueProducerStrategy kafkaProducer(KafkaProducerConfig config, Eventloop eventloop) {
        return new KafkaProducerStrategy(config, eventloop);
    }

    /**
     * Provides the RabbitMQ consumer strategy.
     *
     * @param config    RabbitMQ config
     * @param eventloop ActiveJ event loop
     * @return named RabbitMQ consumer
     */
    @Provides
    @Named("rabbitmq")
    QueueConsumerStrategy rabbitMQConsumer(RabbitMQConfig config, Eventloop eventloop) {
        return new RabbitMQConsumerStrategy(config, eventloop);
    }

    /**
     * Provides the SQS consumer strategy.
     *
     * @param config    SQS config
     * @param eventloop ActiveJ event loop
     * @return named SQS consumer
     */
    @Provides
    @Named("sqs")
    QueueConsumerStrategy sqsConsumer(SqsConfig config, Eventloop eventloop) {
        return new SqsConsumerStrategy(config, eventloop);
    }

    /**
     * Provides the SQS producer strategy.
     *
     * @param config   SQS config
     * @param executor executor for async SQS API calls
     * @return named SQS producer
     */
    @Provides
    @Named("sqs")
    QueueProducerStrategy sqsProducer(SqsConfig config, ExecutorService executor) {
        return new SqsProducerStrategy(config, executor);
    }

    /**
     * Provides the S3 storage strategy.
     *
     * @param config    S3 config
     * @param executor  executor for blocking S3 operations
     * @param scheduler scheduler for multipart upload timeouts
     * @return S3 storage strategy
     */
    @Provides
    S3StorageStrategy s3StorageStrategy(S3Config config, ExecutorService executor,
                                        ScheduledExecutorService scheduler) {
        return new DefaultS3StorageStrategy(config, executor, scheduler);
    }

    /**
     * Provides the HTTP polling ingress strategy.
     *
     * @param config    HTTP ingress config
     * @param executor  executor for HTTP calls
     * @param scheduler scheduler for polling intervals
     * @return HTTP ingress strategy
     */
    @Provides
    HttpIngressStrategy httpIngressStrategy(HttpIngressConfig config, ExecutorService executor,
                                            ScheduledExecutorService scheduler) {
        return new HttpPollingIngressStrategy(config, Duration.ofSeconds(30), null, executor, scheduler);
    }
}
