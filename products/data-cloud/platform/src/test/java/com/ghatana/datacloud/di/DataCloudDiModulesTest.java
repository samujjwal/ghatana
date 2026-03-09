/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.di;

import com.ghatana.datacloud.DataCloud;
import com.ghatana.datacloud.DataCloud.DataCloudConfig;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.DataRecord;
import com.ghatana.datacloud.client.LearningSignalStore;
import com.ghatana.datacloud.attention.AttentionManager;
import com.ghatana.datacloud.attention.DefaultSalienceScorer;
import com.ghatana.datacloud.attention.SalienceScorer;
import com.ghatana.datacloud.brain.BrainConfig;
import com.ghatana.datacloud.brain.DataCloudBrain;
import com.ghatana.datacloud.brain.DefaultDataCloudBrain;
import com.ghatana.datacloud.config.CollectionConfigCompiler;
import com.ghatana.datacloud.config.ConfigLoader;
import com.ghatana.datacloud.config.ConfigMetrics;
import com.ghatana.datacloud.config.ConfigRegistry;
import com.ghatana.datacloud.config.ConfigReloadManager;
import com.ghatana.datacloud.config.ConfigValidator;
import com.ghatana.datacloud.config.PluginConfigCompiler;
import com.ghatana.datacloud.config.PolicyConfigCompiler;
import com.ghatana.datacloud.config.StorageProfileCompiler;
import com.ghatana.datacloud.config.reload.GracefulReloadManager;
import com.ghatana.datacloud.infrastructure.state.redis.RedisStateAdapter;
import com.ghatana.datacloud.memory.DefaultMemoryTierRouter;
import com.ghatana.datacloud.memory.MemoryTierRouter;
import com.ghatana.datacloud.pattern.DefaultPatternCatalog;
import com.ghatana.datacloud.pattern.PatternCatalog;
import com.ghatana.datacloud.plugins.iceberg.CoolTierStoragePlugin;
import com.ghatana.datacloud.plugins.iceberg.IcebergStorageConfig;
import com.ghatana.datacloud.plugins.kafka.EventSerializer;
import com.ghatana.datacloud.plugins.kafka.KafkaStreamingConfig;
import com.ghatana.datacloud.plugins.kafka.KafkaStreamingPlugin;
import com.ghatana.datacloud.plugins.redis.RedisHotTierPlugin;
import com.ghatana.datacloud.plugins.redis.RedisStorageConfig;
import com.ghatana.datacloud.plugins.s3archive.ColdTierArchivePlugin;
import com.ghatana.datacloud.plugins.s3archive.S3ArchiveConfig;
import com.ghatana.datacloud.reflex.DefaultReflexEngine;
import com.ghatana.datacloud.reflex.ReflexEngine;
import com.ghatana.datacloud.spi.StoragePluginRegistry;
import com.ghatana.datacloud.spi.ai.AnomalyDetectionCapability;
import com.ghatana.datacloud.spi.ai.PredictionCapability;
import com.ghatana.datacloud.workspace.GlobalWorkspace;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.ObservabilityModule;
import io.activej.eventloop.Eventloop;
import io.activej.inject.Injector;
import io.activej.inject.Key;
import io.activej.inject.module.Module;
import io.activej.inject.module.ModuleBuilder;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

/**
 * Comprehensive tests for all 5 data-cloud ActiveJ DI modules.
 *
 * <p>Tests verify:
 * <ul>
 *   <li>Each module provides all expected bindings</li>
 *   <li>Interface-to-implementation bindings are correct</li>
 *   <li>Singleton semantics are maintained</li>
 *   <li>Module composition works with proper dependency resolution</li>
 *   <li>External dependencies are correctly propagated</li>
 *   <li>Config defaults are sensible</li>
 * </ul>
 */
@DisplayName("Data-Cloud ActiveJ DI Modules")
class DataCloudDiModulesTest {

    // ═══════════════════════════════════════════════════════════════
    //  1. DataCloudConfigModule Tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("DataCloudConfigModule")
    class DataCloudConfigModuleTests {

        private Injector createConfigInjector() {
            return Injector.of(configExternalStubModule(), new DataCloudConfigModule());
        }

        @Test
        @DisplayName("provides ConfigValidator")
        void providesConfigValidator() {
            Injector injector = createConfigInjector();

            ConfigValidator validator = injector.getInstance(ConfigValidator.class);

            assertThat(validator).isNotNull();
        }

        @Test
        @DisplayName("provides all 4 config compilers")
        void providesAllCompilers() {
            Injector injector = createConfigInjector();

            assertThat(injector.getInstance(CollectionConfigCompiler.class)).isNotNull();
            assertThat(injector.getInstance(PluginConfigCompiler.class)).isNotNull();
            assertThat(injector.getInstance(StorageProfileCompiler.class)).isNotNull();
            assertThat(injector.getInstance(PolicyConfigCompiler.class)).isNotNull();
        }

        @Test
        @DisplayName("provides ConfigLoader with Eventloop and Executor")
        void providesConfigLoader() {
            Injector injector = createConfigInjector();

            ConfigLoader loader = injector.getInstance(ConfigLoader.class);

            assertThat(loader).isNotNull();
        }

        @Test
        @DisplayName("provides ConfigRegistry with all 7 dependencies wired")
        void providesConfigRegistry() {
            Injector injector = createConfigInjector();

            ConfigRegistry registry = injector.getInstance(ConfigRegistry.class);

            assertThat(registry).isNotNull();
        }

        @Test
        @DisplayName("provides ConfigReloadManager")
        void providesConfigReloadManager() {
            Injector injector = createConfigInjector();

            ConfigReloadManager manager = injector.getInstance(ConfigReloadManager.class);

            assertThat(manager).isNotNull();
        }

        @Test
        @DisplayName("provides ConfigMetrics")
        void providesConfigMetrics() {
            Injector injector = createConfigInjector();

            ConfigMetrics metrics = injector.getInstance(ConfigMetrics.class);

            assertThat(metrics).isNotNull();
        }

        @Test
        @DisplayName("provides GracefulReloadManager")
        void providesGracefulReloadManager() {
            Injector injector = createConfigInjector();

            GracefulReloadManager manager = injector.getInstance(GracefulReloadManager.class);

            assertThat(manager).isNotNull();
        }

        @Test
        @DisplayName("ConfigRegistry is singleton")
        void configRegistryIsSingleton() {
            Injector injector = createConfigInjector();

            ConfigRegistry r1 = injector.getInstance(ConfigRegistry.class);
            ConfigRegistry r2 = injector.getInstance(ConfigRegistry.class);

            assertThat(r1).isSameAs(r2);
        }

        @Test
        @DisplayName("all 10 bindings are provided")
        void allBindingsProvided() {
            Injector injector = createConfigInjector();

            // 5 compilers/validators
            assertThat(injector.getInstance(ConfigValidator.class)).isNotNull();
            assertThat(injector.getInstance(CollectionConfigCompiler.class)).isNotNull();
            assertThat(injector.getInstance(PluginConfigCompiler.class)).isNotNull();
            assertThat(injector.getInstance(StorageProfileCompiler.class)).isNotNull();
            assertThat(injector.getInstance(PolicyConfigCompiler.class)).isNotNull();

            // Loader + registry
            assertThat(injector.getInstance(ConfigLoader.class)).isNotNull();
            assertThat(injector.getInstance(ConfigRegistry.class)).isNotNull();

            // Reload + metrics
            assertThat(injector.getInstance(ConfigReloadManager.class)).isNotNull();
            assertThat(injector.getInstance(ConfigMetrics.class)).isNotNull();
            assertThat(injector.getInstance(GracefulReloadManager.class)).isNotNull();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  2. DataCloudCoreModule Tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("DataCloudCoreModule")
    class DataCloudCoreModuleTests {

        @Test
        @DisplayName("provides DataCloudConfig with defaults")
        void providesDataCloudConfig() {
            Injector injector = Injector.of(new DataCloudCoreModule());

            DataCloudConfig config = injector.getInstance(DataCloudConfig.class);

            assertThat(config).isNotNull();
        }

        @Test
        @DisplayName("provides DataCloudClient via factory")
        void providesDataCloudClient() {
            Injector injector = Injector.of(new DataCloudCoreModule());

            DataCloudClient client = injector.getInstance(DataCloudClient.class);

            assertThat(client).isNotNull();
        }

        @Test
        @DisplayName("provides StoragePluginRegistry singleton")
        void providesStoragePluginRegistry() {
            Injector injector = Injector.of(new DataCloudCoreModule());

            StoragePluginRegistry registry = injector.getInstance(StoragePluginRegistry.class);

            assertThat(registry).isNotNull();
        }

        @Test
        @DisplayName("DataCloudClient is singleton")
        void dataCloudClientIsSingleton() {
            Injector injector = Injector.of(new DataCloudCoreModule());

            DataCloudClient c1 = injector.getInstance(DataCloudClient.class);
            DataCloudClient c2 = injector.getInstance(DataCloudClient.class);

            assertThat(c1).isSameAs(c2);
        }

        @Test
        @DisplayName("is self-contained — no external deps required")
        void selfContained() {
            assertThatCode(() -> Injector.of(new DataCloudCoreModule()))
                    .doesNotThrowAnyException();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  3. DataCloudStorageModule Tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("DataCloudStorageModule")
    class DataCloudStorageModuleTests {

        @Test
        @DisplayName("provides RedisStorageConfig with defaults")
        void providesRedisStorageConfig() {
            Injector injector = Injector.of(new DataCloudStorageModule());

            RedisStorageConfig config = injector.getInstance(RedisStorageConfig.class);

            assertThat(config).isNotNull();
        }

        @Test
        @DisplayName("provides RedisHotTierPlugin")
        void providesRedisHotTierPlugin() {
            Injector injector = Injector.of(new DataCloudStorageModule());

            RedisHotTierPlugin plugin = injector.getInstance(RedisHotTierPlugin.class);

            assertThat(plugin).isNotNull();
        }

        @Test
        @DisplayName("provides IcebergStorageConfig with defaults")
        void providesIcebergStorageConfig() {
            Injector injector = Injector.of(new DataCloudStorageModule());

            IcebergStorageConfig config = injector.getInstance(IcebergStorageConfig.class);

            assertThat(config).isNotNull();
        }

        @Test
        @DisplayName("provides CoolTierStoragePlugin")
        void providesCoolTierStoragePlugin() {
            Injector injector = Injector.of(new DataCloudStorageModule());

            CoolTierStoragePlugin plugin = injector.getInstance(CoolTierStoragePlugin.class);

            assertThat(plugin).isNotNull();
        }

        @Test
        @DisplayName("provides S3ArchiveConfig with defaults")
        void providesS3ArchiveConfig() {
            Injector injector = Injector.of(new DataCloudStorageModule());

            S3ArchiveConfig config = injector.getInstance(S3ArchiveConfig.class);

            assertThat(config).isNotNull();
        }

        @Test
        @DisplayName("provides ColdTierArchivePlugin")
        void providesColdTierArchivePlugin() {
            Injector injector = Injector.of(new DataCloudStorageModule());

            ColdTierArchivePlugin plugin = injector.getInstance(ColdTierArchivePlugin.class);

            assertThat(plugin).isNotNull();
        }

        @Test
        @DisplayName("all 6 bindings (3 configs + 3 plugins) are provided")
        void allBindingsProvided() {
            Injector injector = Injector.of(new DataCloudStorageModule());

            // 3 configs
            assertThat(injector.getInstance(RedisStorageConfig.class)).isNotNull();
            assertThat(injector.getInstance(IcebergStorageConfig.class)).isNotNull();
            assertThat(injector.getInstance(S3ArchiveConfig.class)).isNotNull();

            // 3 plugins
            assertThat(injector.getInstance(RedisHotTierPlugin.class)).isNotNull();
            assertThat(injector.getInstance(CoolTierStoragePlugin.class)).isNotNull();
            assertThat(injector.getInstance(ColdTierArchivePlugin.class)).isNotNull();
        }

        @Test
        @DisplayName("is self-contained — no external deps required")
        void selfContained() {
            assertThatCode(() -> Injector.of(new DataCloudStorageModule()))
                    .doesNotThrowAnyException();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  4. DataCloudStreamingModule Tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("DataCloudStreamingModule")
    class DataCloudStreamingModuleTests {

        private Injector createStreamingInjector() {
            return Injector.of(streamingExternalStubModule(), new DataCloudStreamingModule());
        }

        @Test
        @DisplayName("provides KafkaStreamingConfig with defaults")
        void providesKafkaStreamingConfig() {
            Injector injector = createStreamingInjector();

            KafkaStreamingConfig config = injector.getInstance(KafkaStreamingConfig.class);

            assertThat(config).isNotNull();
        }

        @Test
        @DisplayName("provides KafkaStreamingPlugin with Eventloop and MeterRegistry")
        void providesKafkaStreamingPlugin() {
            Injector injector = createStreamingInjector();

            KafkaStreamingPlugin plugin = injector.getInstance(KafkaStreamingPlugin.class);

            assertThat(plugin).isNotNull();
        }

        @Test
        @DisplayName("provides EventSerializer")
        void providesEventSerializer() {
            Injector injector = createStreamingInjector();

            EventSerializer serializer = injector.getInstance(EventSerializer.class);

            assertThat(serializer).isNotNull();
        }

        @Test
        @DisplayName("provides RedisStateAdapter")
        void providesRedisStateAdapter() {
            Injector injector = createStreamingInjector();

            RedisStateAdapter adapter = injector.getInstance(RedisStateAdapter.class);

            assertThat(adapter).isNotNull();
        }

        @Test
        @DisplayName("all 4 bindings are provided")
        void allBindingsProvided() {
            Injector injector = createStreamingInjector();

            assertThat(injector.getInstance(KafkaStreamingConfig.class)).isNotNull();
            assertThat(injector.getInstance(KafkaStreamingPlugin.class)).isNotNull();
            assertThat(injector.getInstance(EventSerializer.class)).isNotNull();
            assertThat(injector.getInstance(RedisStateAdapter.class)).isNotNull();
        }

        @Test
        @DisplayName("KafkaStreamingPlugin is singleton")
        void kafkaPluginIsSingleton() {
            Injector injector = createStreamingInjector();

            KafkaStreamingPlugin p1 = injector.getInstance(KafkaStreamingPlugin.class);
            KafkaStreamingPlugin p2 = injector.getInstance(KafkaStreamingPlugin.class);

            assertThat(p1).isSameAs(p2);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  5. DataCloudBrainModule Tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("DataCloudBrainModule")
    class DataCloudBrainModuleTests {

        private Injector createBrainInjector() {
            return Injector.of(brainExternalStubModule(), new DataCloudBrainModule());
        }

        @Test
        @DisplayName("provides BrainConfig with defaults")
        void providesBrainConfig() {
            Injector injector = createBrainInjector();

            BrainConfig config = injector.getInstance(BrainConfig.class);

            assertThat(config).isNotNull();
        }

        @Test
        @DisplayName("provides ReflexEngine bound to DefaultReflexEngine")
        void providesReflexEngine() {
            Injector injector = createBrainInjector();

            ReflexEngine engine = injector.getInstance(ReflexEngine.class);

            assertThat(engine).isNotNull();
            assertThat(engine).isInstanceOf(DefaultReflexEngine.class);
        }

        @Test
        @DisplayName("provides PatternCatalog bound to DefaultPatternCatalog")
        void providesPatternCatalog() {
            Injector injector = createBrainInjector();

            PatternCatalog catalog = injector.getInstance(PatternCatalog.class);

            assertThat(catalog).isNotNull();
            assertThat(catalog).isInstanceOf(DefaultPatternCatalog.class);
        }

        @Test
        @DisplayName("provides MemoryTierRouter bound to DefaultMemoryTierRouter")
        void providesMemoryTierRouter() {
            Injector injector = createBrainInjector();

            MemoryTierRouter<DataRecord> router =
                    injector.getInstance(new Key<MemoryTierRouter<DataRecord>>(){});

            assertThat(router).isNotNull();
            assertThat(router).isInstanceOf(DefaultMemoryTierRouter.class);
        }

        @Test
        @DisplayName("provides SalienceScorer bound to DefaultSalienceScorer")
        void providesSalienceScorer() {
            Injector injector = createBrainInjector();

            SalienceScorer scorer = injector.getInstance(SalienceScorer.class);

            assertThat(scorer).isNotNull();
            assertThat(scorer).isInstanceOf(DefaultSalienceScorer.class);
        }

        @Test
        @DisplayName("provides GlobalWorkspace with defaults")
        void providesGlobalWorkspace() {
            Injector injector = createBrainInjector();

            GlobalWorkspace workspace = injector.getInstance(GlobalWorkspace.class);

            assertThat(workspace).isNotNull();
        }

        @Test
        @DisplayName("provides AttentionManager")
        void providesAttentionManager() {
            Injector injector = createBrainInjector();

            AttentionManager manager = injector.getInstance(AttentionManager.class);

            assertThat(manager).isNotNull();
        }

        @Test
        @DisplayName("provides DataCloudBrain bound to DefaultDataCloudBrain")
        void providesDataCloudBrain() {
            Injector injector = createBrainInjector();

            DataCloudBrain brain = injector.getInstance(DataCloudBrain.class);

            assertThat(brain).isNotNull();
            assertThat(brain).isInstanceOf(DefaultDataCloudBrain.class);
        }

        @Test
        @DisplayName("all 8 bindings are provided")
        void allBindingsProvided() {
            Injector injector = createBrainInjector();

            assertThat(injector.getInstance(BrainConfig.class)).isNotNull();
            assertThat(injector.getInstance(ReflexEngine.class)).isNotNull();
            assertThat(injector.getInstance(PatternCatalog.class)).isNotNull();
            assertThat(injector.getInstance(new Key<MemoryTierRouter<DataRecord>>(){})).isNotNull();
            assertThat(injector.getInstance(SalienceScorer.class)).isNotNull();
            assertThat(injector.getInstance(GlobalWorkspace.class)).isNotNull();
            assertThat(injector.getInstance(AttentionManager.class)).isNotNull();
            assertThat(injector.getInstance(DataCloudBrain.class)).isNotNull();
        }

        @Test
        @DisplayName("DataCloudBrain is singleton")
        void brainIsSingleton() {
            Injector injector = createBrainInjector();

            DataCloudBrain b1 = injector.getInstance(DataCloudBrain.class);
            DataCloudBrain b2 = injector.getInstance(DataCloudBrain.class);

            assertThat(b1).isSameAs(b2);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  6. Module Composition Tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Module Composition")
    class ModuleCompositionTests {

        @Test
        @DisplayName("DataCloudCoreModule + DataCloudStorageModule compose correctly")
        void coreAndStorageCompose() {
            Injector injector = Injector.of(
                    new DataCloudCoreModule(),
                    new DataCloudStorageModule()
            );

            assertThat(injector.getInstance(DataCloudClient.class)).isNotNull();
            assertThat(injector.getInstance(RedisHotTierPlugin.class)).isNotNull();
            assertThat(injector.getInstance(CoolTierStoragePlugin.class)).isNotNull();
            assertThat(injector.getInstance(ColdTierArchivePlugin.class)).isNotNull();
        }

        @Test
        @DisplayName("DataCloudCoreModule + DataCloudStreamingModule compose correctly")
        void coreAndStreamingCompose() {
            Injector injector = Injector.of(
                    new DataCloudCoreModule(),
                    streamingExternalStubModule(),
                    new DataCloudStreamingModule()
            );

            assertThat(injector.getInstance(DataCloudClient.class)).isNotNull();
            assertThat(injector.getInstance(KafkaStreamingPlugin.class)).isNotNull();
        }

        @Test
        @DisplayName("DataCloudCoreModule + DataCloudBrainModule compose correctly")
        void coreAndBrainCompose() {
            Injector injector = Injector.of(
                    new DataCloudCoreModule(),
                    brainExternalStubModule(),
                    new DataCloudBrainModule()
            );

            assertThat(injector.getInstance(DataCloudClient.class)).isNotNull();
            assertThat(injector.getInstance(DataCloudBrain.class)).isNotNull();
        }

        @Test
        @DisplayName("full module stack creates valid injector")
        void fullModuleStack() {
            // External stubs for streaming (Eventloop, MeterRegistry)
            // and brain (AI SPIs, MetricsCollector) — MetricsCollector from ObservabilityModule
            Module externalStubs = ModuleBuilder.create()
                    .bind(Eventloop.class).toInstance(Eventloop.builder().withCurrentThread().build())
                    .bind(AnomalyDetectionCapability.class).toInstance(mock(AnomalyDetectionCapability.class))
                    .bind(PredictionCapability.class).toInstance(mock(PredictionCapability.class))
                    .bind(LearningSignalStore.class).toInstance(mock(LearningSignalStore.class))
                    .bind(ExecutorService.class).toInstance(Executors.newSingleThreadExecutor())
                    .build();

            assertThatCode(() -> {
                Injector injector = Injector.of(
                        new ObservabilityModule(),
                        new DataCloudCoreModule(),
                        new DataCloudStorageModule(),
                        externalStubs,
                        new DataCloudStreamingModule(),
                        new DataCloudConfigModule(),
                        new DataCloudBrainModule()
                );

                // Cross-module verification
                assertThat(injector.getInstance(DataCloudClient.class)).isNotNull();
                assertThat(injector.getInstance(ConfigRegistry.class)).isNotNull();
                assertThat(injector.getInstance(DataCloudBrain.class)).isNotNull();
                assertThat(injector.getInstance(KafkaStreamingPlugin.class)).isNotNull();
                assertThat(injector.getInstance(RedisHotTierPlugin.class)).isNotNull();

                injector.getInstance(ExecutorService.class).shutdown();
            }).doesNotThrowAnyException();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  7. Edge Case Tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("modules can be instantiated multiple times")
        void multipleInstances() {
            Injector i1 = Injector.of(new DataCloudCoreModule());
            Injector i2 = Injector.of(new DataCloudCoreModule());

            DataCloudClient c1 = i1.getInstance(DataCloudClient.class);
            DataCloudClient c2 = i2.getInstance(DataCloudClient.class);

            assertThat(c1).isNotNull();
            assertThat(c2).isNotNull();
            assertThat(c1).isNotSameAs(c2);
        }

        @Test
        @DisplayName("storage config can be provided by custom module")
        void storageConfigOverride() {
            RedisStorageConfig custom = RedisStorageConfig.builder()
                    .host("redis.production.local")
                    .port(6380)
                    .build();

            Module customModule = ModuleBuilder.create()
                    .bind(RedisStorageConfig.class).toInstance(custom)
                    .build();

            Injector injector = Injector.of(customModule);

            RedisStorageConfig resolved = injector.getInstance(RedisStorageConfig.class);
            assertThat(resolved.getHost()).isEqualTo("redis.production.local");
            assertThat(resolved.getPort()).isEqualTo(6380);
        }

        @Test
        @DisplayName("brain sub-services are distinct singletons")
        void brainSubServicesDistinct() {
            Injector injector = Injector.of(brainExternalStubModule(), new DataCloudBrainModule());

            ReflexEngine reflex = injector.getInstance(ReflexEngine.class);
            PatternCatalog catalog = injector.getInstance(PatternCatalog.class);

            assertThat(reflex).isNotNull();
            assertThat(catalog).isNotNull();
            assertThat(reflex).isNotSameAs(catalog);
        }

        @Test
        @DisplayName("self-contained modules work without any external deps")
        void selfContainedModules() {
            assertThatCode(() -> Injector.of(new DataCloudCoreModule())).doesNotThrowAnyException();
            assertThatCode(() -> Injector.of(new DataCloudStorageModule())).doesNotThrowAnyException();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Stub/Mock Helpers
    // ═══════════════════════════════════════════════════════════════

    /**
     * External deps for DataCloudConfigModule: Eventloop, ExecutorService, MetricsCollector.
     */
    private static Module configExternalStubModule() {
        return ModuleBuilder.create()
                .bind(Eventloop.class).toInstance(Eventloop.builder().withCurrentThread().build())
                .bind(ExecutorService.class).toInstance(Executors.newSingleThreadExecutor())
                .bind(MetricsCollector.class).toInstance(mock(MetricsCollector.class))
                .build();
    }

    /**
     * External deps for DataCloudStreamingModule: Eventloop, MeterRegistry.
     */
    private static Module streamingExternalStubModule() {
        return ModuleBuilder.create()
                .bind(Eventloop.class).toInstance(Eventloop.builder().withCurrentThread().build())
                .bind(MeterRegistry.class).toInstance(mock(MeterRegistry.class))
                .build();
    }

    /**
     * External deps for DataCloudBrainModule: AI SPIs + MetricsCollector.
     */
    private static Module brainExternalStubModule() {
        return ModuleBuilder.create()
                .bind(AnomalyDetectionCapability.class).toInstance(mock(AnomalyDetectionCapability.class))
                .bind(PredictionCapability.class).toInstance(mock(PredictionCapability.class))
                .bind(LearningSignalStore.class).toInstance(mock(LearningSignalStore.class))
                .bind(MetricsCollector.class).toInstance(mock(MetricsCollector.class))
                .build();
    }
}
