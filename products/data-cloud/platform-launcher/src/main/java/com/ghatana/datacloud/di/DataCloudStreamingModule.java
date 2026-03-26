/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.di;

import com.ghatana.datacloud.infrastructure.state.redis.RedisStateAdapter;
import com.ghatana.datacloud.plugins.kafka.EventSerializer;
import com.ghatana.datacloud.plugins.kafka.KafkaStreamingConfig;
import com.ghatana.datacloud.plugins.kafka.KafkaStreamingPlugin;
import io.activej.eventloop.Eventloop;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * ActiveJ DI module for data-cloud streaming infrastructure.
 *
 * <p>Provides Kafka and Redis streaming components for real-time
 * event processing:
 * <ul>
 *   <li>{@link KafkaStreamingPlugin} — Kafka-based event streaming with
 *       consumer groups, exactly-once semantics, and batch processing</li>
 *   <li>{@link EventSerializer} — event serialization/deserialization</li>
 *   <li>{@link RedisStateAdapter} — Redis-backed state management for
 *       stream processing (checkpoints, windowing, aggregations)</li>
 * </ul>
 *
 * <p><b>Dependencies:</b> Requires {@link Eventloop} and {@link MeterRegistry}
 * from the platform modules.
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * Injector injector = Injector.of(
 *     new ObservabilityModule(),   // provides MeterRegistry
 *     new AepCoreModule(),         // provides Eventloop
 *     new DataCloudStreamingModule()
 * );
 * KafkaStreamingPlugin kafka = injector.getInstance(KafkaStreamingPlugin.class);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose ActiveJ DI module for data-cloud streaming infrastructure
 * @doc.layer product
 * @doc.pattern Module
 * @see KafkaStreamingPlugin
 * @see RedisStateAdapter
 */
public class DataCloudStreamingModule extends AbstractModule {

    private static final String DEFAULT_REDIS_HOST = "localhost";
    private static final int DEFAULT_REDIS_PORT = 6379;
    private static final String DEFAULT_KEY_PREFIX = "dc:";

    /**
     * Provides the Kafka streaming configuration with production defaults.
     *
     * <p>Defaults: {@code localhost:9092}, exactly-once disabled (for local dev),
     * PLAINTEXT security. Override for production Kafka clusters.
     *
     * @return Kafka streaming config
     */
    @Provides
    KafkaStreamingConfig kafkaStreamingConfig() {
        return KafkaStreamingConfig.defaults();
    }

    /**
     * Provides the Kafka streaming plugin.
     *
     * <p>Full-featured Kafka integration with consumer groups, batch processing,
     * and ActiveJ eventloop integration for non-blocking I/O. Internally creates
     * KafkaProducer, KafkaConsumer, AdminClient, and ExecutorService.
     *
     * @param config       Kafka streaming configuration
     * @param eventloop    ActiveJ event loop for async integration
     * @param meterRegistry metrics registry for Kafka operation tracking
     * @return Kafka streaming plugin
     */
    @Provides
    KafkaStreamingPlugin kafkaStreamingPlugin(KafkaStreamingConfig config,
                                               Eventloop eventloop,
                                               MeterRegistry meterRegistry) {
        return new KafkaStreamingPlugin(config, eventloop, meterRegistry);
    }

    /**
     * Provides the event serializer.
     *
     * <p>Handles serialization and deserialization of data-cloud events
     * to/from byte arrays for Kafka and Redis wire format.
     *
     * @return event serializer
     */
    @Provides
    EventSerializer eventSerializer() {
        return new EventSerializer();
    }

    /**
     * Provides the Redis state adapter for stream processing state.
     *
     * <p>Manages stream processing state: consumer offsets, window aggregations,
     * exactly-once checkpoints, and deduplication. Connects to {@code localhost:6379}
     * with key prefix {@code dc:} by default.
     *
     * @return Redis state adapter
     */
    @Provides
    RedisStateAdapter redisStateAdapter() {
        return new RedisStateAdapter(DEFAULT_REDIS_HOST, DEFAULT_REDIS_PORT, DEFAULT_KEY_PREFIX);
    }
}
