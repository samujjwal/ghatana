/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.di;

import com.ghatana.datacloud.infrastructure.state.redis.RedisStateAdapter;
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
    // ═══════════════════════════════════════════════════════════════
    // Kafka streaming plugin (commented out - plugin not available)
    // ═══════════════════════════════════════════════════════════════
    /*
    @Provides
    KafkaStreamingConfig kafkaStreamingConfig() {
        return KafkaStreamingConfig.defaults();
    }

    @Provides
    KafkaStreamingPlugin kafkaStreamingPlugin(KafkaStreamingConfig config,
                                               Eventloop eventloop,
                                               MeterRegistry meterRegistry) {
        return new KafkaStreamingPlugin(config, eventloop, meterRegistry);
    }

    @Provides
    EventSerializer eventSerializer() {
        return new EventSerializer();
    }
    */

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
