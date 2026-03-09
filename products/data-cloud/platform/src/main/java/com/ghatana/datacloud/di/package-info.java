/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

/**
 * ActiveJ DI modules for the data-cloud platform.
 *
 * <p>This package provides 5 modular ActiveJ DI modules that together compose
 * the complete data-cloud dependency injection graph:
 *
 * <pre>{@code
 * ┌─────────────────────────────────────────────────────────────────┐
 * │                   Data-Cloud DI Architecture                    │
 * ├─────────────────────────────────────────────────────────────────┤
 * │                                                                 │
 * │  DataCloudConfigModule       (10 bindings)                      │
 * │  ├── ConfigLoader, ConfigValidator                              │
 * │  ├── 4 Config Compilers                                         │
 * │  ├── ConfigRegistry                                             │
 * │  └── ConfigReloadManager, ConfigMetrics, GracefulReloadMgr      │
 * │       ↕ requires: Eventloop, ExecutorService, MetricsCollector  │
 * │                                                                 │
 * │  DataCloudCoreModule         (3 bindings)                       │
 * │  ├── DataCloudConfig (defaults)                                 │
 * │  ├── DataCloudClient (in-memory stores)                         │
 * │  └── StoragePluginRegistry (singleton)                          │
 * │       ↕ self-contained                                          │
 * │                                                                 │
 * │  DataCloudStorageModule      (6 bindings)                       │
 * │  ├── Hot:  RedisStorageConfig  → RedisHotTierPlugin             │
 * │  ├── Cool: IcebergStorageConfig → CoolTierStoragePlugin         │
 * │  └── Cold: S3ArchiveConfig → ColdTierArchivePlugin              │
 * │       ↕ self-contained (configs have defaults)                  │
 * │                                                                 │
 * │  DataCloudStreamingModule    (4 bindings)                       │
 * │  ├── KafkaStreamingConfig → KafkaStreamingPlugin                │
 * │  ├── EventSerializer                                            │
 * │  └── RedisStateAdapter                                          │
 * │       ↕ requires: Eventloop, MeterRegistry                      │
 * │                                                                 │
 * │  DataCloudBrainModule        (8 bindings)                       │
 * │  ├── BrainConfig, ReflexEngine, PatternCatalog                  │
 * │  ├── MemoryTierRouter, GlobalWorkspace                          │
 * │  ├── SalienceScorer, AttentionManager                           │
 * │  └── DataCloudBrain (composite facade)                          │
 * │       ↕ requires: AnomalyDetectionCapability, PredictionCap,    │
 * │         LearningSignalStore, MetricsCollector                   │
 * └─────────────────────────────────────────────────────────────────┘
 *
 * Quick start:
 *   Injector injector = Injector.of(
 *       new ObservabilityModule(),        // platform
 *       new AepCoreModule(),              // Eventloop, ExecutorService
 *       new DataCloudCoreModule(),
 *       new DataCloudConfigModule(),
 *       new DataCloudStorageModule(),
 *       new DataCloudStreamingModule(),
 *       aiCapabilityStubs,               // AI SPIs
 *       new DataCloudBrainModule()
 *   );
 * }</pre>
 *
 * @doc.type package
 * @doc.purpose Data-cloud ActiveJ DI module architecture
 * @doc.layer product
 */
package com.ghatana.datacloud.di;
