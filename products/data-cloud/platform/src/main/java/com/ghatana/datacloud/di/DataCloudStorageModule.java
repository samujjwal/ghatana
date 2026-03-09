/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.di;

import com.ghatana.datacloud.plugins.iceberg.CoolTierStoragePlugin;
import com.ghatana.datacloud.plugins.iceberg.IcebergStorageConfig;
import com.ghatana.datacloud.plugins.redis.RedisHotTierPlugin;
import com.ghatana.datacloud.plugins.redis.RedisStorageConfig;
import com.ghatana.datacloud.plugins.s3archive.ColdTierArchivePlugin;
import com.ghatana.datacloud.plugins.s3archive.S3ArchiveConfig;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;

/**
 * ActiveJ DI module for data-cloud tiered storage plugins.
 *
 * <p>Provides the three-tier storage hierarchy with sensible defaults:
 * <ul>
 *   <li><b>Hot tier</b> — {@link RedisHotTierPlugin} for sub-millisecond access
 *       to recent/active data (Redis-backed)</li>
 *   <li><b>Cool tier</b> — {@link CoolTierStoragePlugin} for queryable historical
 *       data (Apache Iceberg on object storage)</li>
 *   <li><b>Cold tier</b> — {@link ColdTierArchivePlugin} for long-term archival
 *       (S3/Glacier with restore capabilities)</li>
 * </ul>
 *
 * <p>Each tier has its own configuration and lifecycle. Override individual
 * config bindings to customize connection parameters, paths, or retention.
 *
 * <p><b>Dependencies:</b> None — all configs have sensible defaults for
 * local development. Production deployments should override configs.
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * Injector injector = Injector.of(new DataCloudStorageModule());
 * RedisHotTierPlugin hotTier = injector.getInstance(RedisHotTierPlugin.class);
 * CoolTierStoragePlugin coolTier = injector.getInstance(CoolTierStoragePlugin.class);
 * ColdTierArchivePlugin coldTier = injector.getInstance(ColdTierArchivePlugin.class);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose ActiveJ DI module for tiered storage plugins
 * @doc.layer product
 * @doc.pattern Module, Strategy
 * @see RedisHotTierPlugin
 * @see CoolTierStoragePlugin
 * @see ColdTierArchivePlugin
 */
public class DataCloudStorageModule extends AbstractModule {

    // ═══════════════════════════════════════════════════════════════
    //  Hot Tier — Redis
    // ═══════════════════════════════════════════════════════════════

    /**
     * Provides the Redis hot-tier storage configuration.
     *
     * <p>Defaults: {@code localhost:6379}, connection pool size 16,
     * TTL 1 hour, max memory 256 MB.
     *
     * @return Redis storage config
     */
    @Provides
    RedisStorageConfig redisStorageConfig() {
        return RedisStorageConfig.builder()
                .host("localhost")
                .port(6379)
                .build();
    }

    /**
     * Provides the Redis hot-tier storage plugin.
     *
     * <p>Manages the hot tier — highest-frequency, lowest-latency storage
     * for recently ingested or frequently accessed data. Internally creates
     * a JedisPool and Disruptor for async I/O.
     *
     * @param config Redis storage configuration
     * @return Redis hot-tier plugin
     */
    @Provides
    RedisHotTierPlugin redisHotTierPlugin(RedisStorageConfig config) {
        return new RedisHotTierPlugin(config);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Cool Tier — Apache Iceberg
    // ═══════════════════════════════════════════════════════════════

    /**
     * Provides the Iceberg cool-tier storage configuration.
     *
     * <p>Defaults: Hadoop catalog, local warehouse, Parquet format.
     *
     * @return Iceberg storage config
     */
    @Provides
    IcebergStorageConfig icebergStorageConfig() {
        return IcebergStorageConfig.builder()
                .build();
    }

    /**
     * Provides the Apache Iceberg cool-tier storage plugin.
     *
     * <p>Manages the cool tier — queryable historical data stored in
     * columnar format (Parquet/ORC) with time-travel and schema evolution.
     *
     * @param config Iceberg storage configuration
     * @return cool-tier storage plugin
     */
    @Provides
    CoolTierStoragePlugin coolTierStoragePlugin(IcebergStorageConfig config) {
        return new CoolTierStoragePlugin(config);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Cold Tier — S3/Glacier Archive
    // ═══════════════════════════════════════════════════════════════

    /**
     * Provides the S3 cold-tier archive configuration.
     *
     * <p>Defaults: bucket {@code "dc-archive"}, region {@code "us-east-1"},
     * no encryption. Override for production.
     *
     * @return S3 archive config
     */
    @Provides
    S3ArchiveConfig s3ArchiveConfig() {
        return S3ArchiveConfig.builder()
                .bucketName("dc-archive")
                .region("us-east-1")
                .build();
    }

    /**
     * Provides the S3/Glacier cold-tier archive plugin.
     *
     * <p>Manages the cold tier — long-term archival with Glacier tiering
     * for compliance and cost optimization. Supports restore-on-demand.
     *
     * @param config S3 archive configuration
     * @return cold-tier archive plugin
     */
    @Provides
    ColdTierArchivePlugin coldTierArchivePlugin(S3ArchiveConfig config) {
        return new ColdTierArchivePlugin(config);
    }
}
