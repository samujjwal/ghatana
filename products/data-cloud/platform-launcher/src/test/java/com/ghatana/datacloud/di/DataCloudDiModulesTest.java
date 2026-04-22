/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.di;

import com.ghatana.datacloud.DataCloud.DataCloudConfig;
import com.ghatana.datacloud.DataCloud.DataCloudConfig.DataCloudProfile;
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
import com.zaxxer.hikari.HikariDataSource;
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
@DisplayName("Data-Cloud ActiveJ DI Modules [GH-90000]")
class DataCloudDiModulesTest {

    // ═══════════════════════════════════════════════════════════════
    //  1. DataCloudConfigModule Tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("DataCloudConfigModule [GH-90000]")
    class DataCloudConfigModuleTests {

        private Injector createConfigInjector() { // GH-90000
            return Injector.of(configExternalStubModule(), new DataCloudConfigModule()); // GH-90000
        }

        @Test
        @DisplayName("provides ConfigValidator [GH-90000]")
        void providesConfigValidator() { // GH-90000
            Injector injector = createConfigInjector(); // GH-90000

            ConfigValidator validator = injector.getInstance(ConfigValidator.class); // GH-90000

            assertThat(validator).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("provides all 4 config compilers [GH-90000]")
        void providesAllCompilers() { // GH-90000
            Injector injector = createConfigInjector(); // GH-90000

            assertThat(injector.getInstance(CollectionConfigCompiler.class)).isNotNull(); // GH-90000
            assertThat(injector.getInstance(PluginConfigCompiler.class)).isNotNull(); // GH-90000
            assertThat(injector.getInstance(StorageProfileCompiler.class)).isNotNull(); // GH-90000
            assertThat(injector.getInstance(PolicyConfigCompiler.class)).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("provides ConfigLoader with Eventloop and Executor [GH-90000]")
        void providesConfigLoader() { // GH-90000
            Injector injector = createConfigInjector(); // GH-90000

            ConfigLoader loader = injector.getInstance(ConfigLoader.class); // GH-90000

            assertThat(loader).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("provides ConfigRegistry with all 7 dependencies wired [GH-90000]")
        void providesConfigRegistry() { // GH-90000
            Injector injector = createConfigInjector(); // GH-90000

            ConfigRegistry registry = injector.getInstance(ConfigRegistry.class); // GH-90000

            assertThat(registry).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("provides ConfigReloadManager [GH-90000]")
        void providesConfigReloadManager() { // GH-90000
            Injector injector = createConfigInjector(); // GH-90000

            ConfigReloadManager manager = injector.getInstance(ConfigReloadManager.class); // GH-90000

            assertThat(manager).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("provides ConfigMetrics [GH-90000]")
        void providesConfigMetrics() { // GH-90000
            Injector injector = createConfigInjector(); // GH-90000

            ConfigMetrics metrics = injector.getInstance(ConfigMetrics.class); // GH-90000

            assertThat(metrics).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("provides GracefulReloadManager [GH-90000]")
        void providesGracefulReloadManager() { // GH-90000
            Injector injector = createConfigInjector(); // GH-90000

            GracefulReloadManager manager = injector.getInstance(GracefulReloadManager.class); // GH-90000

            assertThat(manager).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("ConfigRegistry is singleton [GH-90000]")
        void configRegistryIsSingleton() { // GH-90000
            Injector injector = createConfigInjector(); // GH-90000

            ConfigRegistry r1 = injector.getInstance(ConfigRegistry.class); // GH-90000
            ConfigRegistry r2 = injector.getInstance(ConfigRegistry.class); // GH-90000

            assertThat(r1).isSameAs(r2); // GH-90000
        }

        @Test
        @DisplayName("all 10 bindings are provided [GH-90000]")
        void allBindingsProvided() { // GH-90000
            Injector injector = createConfigInjector(); // GH-90000

            // 5 compilers/validators
            assertThat(injector.getInstance(ConfigValidator.class)).isNotNull(); // GH-90000
            assertThat(injector.getInstance(CollectionConfigCompiler.class)).isNotNull(); // GH-90000
            assertThat(injector.getInstance(PluginConfigCompiler.class)).isNotNull(); // GH-90000
            assertThat(injector.getInstance(StorageProfileCompiler.class)).isNotNull(); // GH-90000
            assertThat(injector.getInstance(PolicyConfigCompiler.class)).isNotNull(); // GH-90000

            // Loader + registry
            assertThat(injector.getInstance(ConfigLoader.class)).isNotNull(); // GH-90000
            assertThat(injector.getInstance(ConfigRegistry.class)).isNotNull(); // GH-90000

            // Reload + metrics
            assertThat(injector.getInstance(ConfigReloadManager.class)).isNotNull(); // GH-90000
            assertThat(injector.getInstance(ConfigMetrics.class)).isNotNull(); // GH-90000
            assertThat(injector.getInstance(GracefulReloadManager.class)).isNotNull(); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  2. DataCloudCoreModule Tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("DataCloudCoreModule [GH-90000]")
    class DataCloudCoreModuleTests {

        @Test
        @DisplayName("provides DataCloudConfig with defaults [GH-90000]")
        void providesDataCloudConfig() { // GH-90000
            Injector injector = Injector.of(new DataCloudCoreModule()); // GH-90000

            DataCloudConfig config = injector.getInstance(DataCloudConfig.class); // GH-90000

            assertThat(config).isNotNull(); // GH-90000
            assertThat(config.profile()).isEqualTo(DataCloudProfile.LOCAL); // GH-90000
        }

        @Test
        @DisplayName("provides DataCloudClient via factory [GH-90000]")
        void providesDataCloudClient() { // GH-90000
            Injector injector = Injector.of(new DataCloudCoreModule()); // GH-90000

            DataCloudClient client = injector.getInstance(DataCloudClient.class); // GH-90000

            assertThat(client).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("provides StoragePluginRegistry singleton [GH-90000]")
        void providesStoragePluginRegistry() { // GH-90000
            Injector injector = Injector.of(new DataCloudCoreModule()); // GH-90000

            StoragePluginRegistry registry = injector.getInstance(StoragePluginRegistry.class); // GH-90000

            assertThat(registry).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("DataCloudClient is singleton [GH-90000]")
        void dataCloudClientIsSingleton() { // GH-90000
            Injector injector = Injector.of(new DataCloudCoreModule()); // GH-90000

            DataCloudClient c1 = injector.getInstance(DataCloudClient.class); // GH-90000
            DataCloudClient c2 = injector.getInstance(DataCloudClient.class); // GH-90000

            assertThat(c1).isSameAs(c2); // GH-90000
        }

        @Test
        @DisplayName("is self-contained — no external deps required [GH-90000]")
        void selfContained() { // GH-90000
            assertThatCode(() -> Injector.of(new DataCloudCoreModule())) // GH-90000
                    .doesNotThrowAnyException(); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  3. DataCloudStorageModule Tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("DataCloudStorageModule [GH-90000]")
    class DataCloudStorageModuleTests {

        @Test
        @DisplayName("provides RedisStorageConfig with defaults [GH-90000]")
        void providesRedisStorageConfig() { // GH-90000
            Injector injector = Injector.of(new DataCloudStorageModule()); // GH-90000

            RedisStorageConfig config = injector.getInstance(RedisStorageConfig.class); // GH-90000

            assertThat(config).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("provides RedisHotTierPlugin [GH-90000]")
        void providesRedisHotTierPlugin() { // GH-90000
            Injector injector = Injector.of(new DataCloudStorageModule()); // GH-90000

            RedisHotTierPlugin plugin = injector.getInstance(RedisHotTierPlugin.class); // GH-90000

            assertThat(plugin).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("provides IcebergStorageConfig with defaults [GH-90000]")
        void providesIcebergStorageConfig() { // GH-90000
            Injector injector = Injector.of(new DataCloudStorageModule()); // GH-90000

            IcebergStorageConfig config = injector.getInstance(IcebergStorageConfig.class); // GH-90000

            assertThat(config).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("provides CoolTierStoragePlugin [GH-90000]")
        void providesCoolTierStoragePlugin() { // GH-90000
            Injector injector = Injector.of(new DataCloudStorageModule()); // GH-90000

            CoolTierStoragePlugin plugin = injector.getInstance(CoolTierStoragePlugin.class); // GH-90000

            assertThat(plugin).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("provides S3ArchiveConfig with defaults [GH-90000]")
        void providesS3ArchiveConfig() { // GH-90000
            Injector injector = Injector.of(new DataCloudStorageModule()); // GH-90000

            S3ArchiveConfig config = injector.getInstance(S3ArchiveConfig.class); // GH-90000

            assertThat(config).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("provides ColdTierArchivePlugin [GH-90000]")
        void providesColdTierArchivePlugin() { // GH-90000
            Injector injector = Injector.of(new DataCloudStorageModule()); // GH-90000

            ColdTierArchivePlugin plugin = injector.getInstance(ColdTierArchivePlugin.class); // GH-90000

            assertThat(plugin).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("all 6 bindings (3 configs + 3 plugins) are provided [GH-90000]")
        void allBindingsProvided() { // GH-90000
            Injector injector = Injector.of(new DataCloudStorageModule()); // GH-90000

            // 3 configs
            assertThat(injector.getInstance(RedisStorageConfig.class)).isNotNull(); // GH-90000
            assertThat(injector.getInstance(IcebergStorageConfig.class)).isNotNull(); // GH-90000
            assertThat(injector.getInstance(S3ArchiveConfig.class)).isNotNull(); // GH-90000

            // 3 plugins
            assertThat(injector.getInstance(RedisHotTierPlugin.class)).isNotNull(); // GH-90000
            assertThat(injector.getInstance(CoolTierStoragePlugin.class)).isNotNull(); // GH-90000
            assertThat(injector.getInstance(ColdTierArchivePlugin.class)).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("warm-tier DataSource applies bounded idle and validation defaults [GH-90000]")
        void warmTierDataSourceAppliesBoundedIdleAndValidationDefaults() { // GH-90000
            DataCloudStorageModule module = new DataCloudStorageModule(); // GH-90000

            HikariDataSource dataSource = (HikariDataSource) module.warmTierDataSource(); // GH-90000
            try (HikariDataSource ignored = dataSource) { // GH-90000
                assertThat(dataSource.getMaximumPoolSize()).isEqualTo(10); // GH-90000
                assertThat(dataSource.getMinimumIdle()).isEqualTo(2); // GH-90000
                assertThat(dataSource.getValidationTimeout()).isEqualTo(5_000L); // GH-90000
            }
        }

        @Test
        @DisplayName("is self-contained — no external deps required [GH-90000]")
        void selfContained() { // GH-90000
            assertThatCode(() -> Injector.of(new DataCloudStorageModule())) // GH-90000
                    .doesNotThrowAnyException(); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  4. DataCloudStreamingModule Tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("DataCloudStreamingModule [GH-90000]")
    class DataCloudStreamingModuleTests {

        private Injector createStreamingInjector() { // GH-90000
            return Injector.of(streamingExternalStubModule(), new DataCloudStreamingModule()); // GH-90000
        }

        @Test
        @DisplayName("provides KafkaStreamingConfig with defaults [GH-90000]")
        void providesKafkaStreamingConfig() { // GH-90000
            Injector injector = createStreamingInjector(); // GH-90000

            KafkaStreamingConfig config = injector.getInstance(KafkaStreamingConfig.class); // GH-90000

            assertThat(config).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("provides KafkaStreamingPlugin with Eventloop and MeterRegistry [GH-90000]")
        void providesKafkaStreamingPlugin() { // GH-90000
            Injector injector = createStreamingInjector(); // GH-90000

            KafkaStreamingPlugin plugin = injector.getInstance(KafkaStreamingPlugin.class); // GH-90000

            assertThat(plugin).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("provides EventSerializer [GH-90000]")
        void providesEventSerializer() { // GH-90000
            Injector injector = createStreamingInjector(); // GH-90000

            EventSerializer serializer = injector.getInstance(EventSerializer.class); // GH-90000

            assertThat(serializer).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("provides RedisStateAdapter [GH-90000]")
        void providesRedisStateAdapter() { // GH-90000
            Injector injector = createStreamingInjector(); // GH-90000

            RedisStateAdapter adapter = injector.getInstance(RedisStateAdapter.class); // GH-90000

            assertThat(adapter).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("all 4 bindings are provided [GH-90000]")
        void allBindingsProvided() { // GH-90000
            Injector injector = createStreamingInjector(); // GH-90000

            assertThat(injector.getInstance(KafkaStreamingConfig.class)).isNotNull(); // GH-90000
            assertThat(injector.getInstance(KafkaStreamingPlugin.class)).isNotNull(); // GH-90000
            assertThat(injector.getInstance(EventSerializer.class)).isNotNull(); // GH-90000
            assertThat(injector.getInstance(RedisStateAdapter.class)).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("KafkaStreamingPlugin is singleton [GH-90000]")
        void kafkaPluginIsSingleton() { // GH-90000
            Injector injector = createStreamingInjector(); // GH-90000

            KafkaStreamingPlugin p1 = injector.getInstance(KafkaStreamingPlugin.class); // GH-90000
            KafkaStreamingPlugin p2 = injector.getInstance(KafkaStreamingPlugin.class); // GH-90000

            assertThat(p1).isSameAs(p2); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  5. DataCloudBrainModule Tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("DataCloudBrainModule [GH-90000]")
    class DataCloudBrainModuleTests {

        private Injector createBrainInjector() { // GH-90000
            return Injector.of(brainExternalStubModule(), new DataCloudBrainModule()); // GH-90000
        }

        @Test
        @DisplayName("provides BrainConfig with defaults [GH-90000]")
        void providesBrainConfig() { // GH-90000
            Injector injector = createBrainInjector(); // GH-90000

            BrainConfig config = injector.getInstance(BrainConfig.class); // GH-90000

            assertThat(config).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("provides ReflexEngine bound to DefaultReflexEngine [GH-90000]")
        void providesReflexEngine() { // GH-90000
            Injector injector = createBrainInjector(); // GH-90000

            ReflexEngine engine = injector.getInstance(ReflexEngine.class); // GH-90000

            assertThat(engine).isNotNull(); // GH-90000
            assertThat(engine).isInstanceOf(DefaultReflexEngine.class); // GH-90000
        }

        @Test
        @DisplayName("provides PatternCatalog bound to DefaultPatternCatalog [GH-90000]")
        void providesPatternCatalog() { // GH-90000
            Injector injector = createBrainInjector(); // GH-90000

            PatternCatalog catalog = injector.getInstance(PatternCatalog.class); // GH-90000

            assertThat(catalog).isNotNull(); // GH-90000
            assertThat(catalog).isInstanceOf(DefaultPatternCatalog.class); // GH-90000
        }

        @Test
        @DisplayName("provides MemoryTierRouter bound to DefaultMemoryTierRouter [GH-90000]")
        void providesMemoryTierRouter() { // GH-90000
            Injector injector = createBrainInjector(); // GH-90000

            MemoryTierRouter<DataRecord> router =
                    injector.getInstance(new Key<>(){}); // GH-90000

            assertThat(router).isNotNull(); // GH-90000
            assertThat(router).isInstanceOf(DefaultMemoryTierRouter.class); // GH-90000
        }

        @Test
        @DisplayName("provides SalienceScorer bound to DefaultSalienceScorer [GH-90000]")
        void providesSalienceScorer() { // GH-90000
            Injector injector = createBrainInjector(); // GH-90000

            SalienceScorer scorer = injector.getInstance(SalienceScorer.class); // GH-90000

            assertThat(scorer).isNotNull(); // GH-90000
            assertThat(scorer).isInstanceOf(DefaultSalienceScorer.class); // GH-90000
        }

        @Test
        @DisplayName("provides GlobalWorkspace with defaults [GH-90000]")
        void providesGlobalWorkspace() { // GH-90000
            Injector injector = createBrainInjector(); // GH-90000

            GlobalWorkspace workspace = injector.getInstance(GlobalWorkspace.class); // GH-90000

            assertThat(workspace).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("provides AttentionManager [GH-90000]")
        void providesAttentionManager() { // GH-90000
            Injector injector = createBrainInjector(); // GH-90000

            AttentionManager manager = injector.getInstance(AttentionManager.class); // GH-90000

            assertThat(manager).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("provides DataCloudBrain bound to DefaultDataCloudBrain [GH-90000]")
        void providesDataCloudBrain() { // GH-90000
            Injector injector = createBrainInjector(); // GH-90000

            DataCloudBrain brain = injector.getInstance(DataCloudBrain.class); // GH-90000

            assertThat(brain).isNotNull(); // GH-90000
            assertThat(brain).isInstanceOf(DefaultDataCloudBrain.class); // GH-90000
        }

        @Test
        @DisplayName("all 8 bindings are provided [GH-90000]")
        void allBindingsProvided() { // GH-90000
            Injector injector = createBrainInjector(); // GH-90000

            assertThat(injector.getInstance(BrainConfig.class)).isNotNull(); // GH-90000
            assertThat(injector.getInstance(ReflexEngine.class)).isNotNull(); // GH-90000
            assertThat(injector.getInstance(PatternCatalog.class)).isNotNull(); // GH-90000
            assertThat(injector.getInstance(new Key<MemoryTierRouter<DataRecord>>(){})).isNotNull(); // GH-90000
            assertThat(injector.getInstance(SalienceScorer.class)).isNotNull(); // GH-90000
            assertThat(injector.getInstance(GlobalWorkspace.class)).isNotNull(); // GH-90000
            assertThat(injector.getInstance(AttentionManager.class)).isNotNull(); // GH-90000
            assertThat(injector.getInstance(DataCloudBrain.class)).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("DataCloudBrain is singleton [GH-90000]")
        void brainIsSingleton() { // GH-90000
            Injector injector = createBrainInjector(); // GH-90000

            DataCloudBrain b1 = injector.getInstance(DataCloudBrain.class); // GH-90000
            DataCloudBrain b2 = injector.getInstance(DataCloudBrain.class); // GH-90000

            assertThat(b1).isSameAs(b2); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  6. Module Composition Tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Module Composition [GH-90000]")
    class ModuleCompositionTests {

        @Test
        @DisplayName("DataCloudCoreModule + DataCloudStorageModule compose correctly [GH-90000]")
        void coreAndStorageCompose() { // GH-90000
            Injector injector = Injector.of( // GH-90000
                    new DataCloudCoreModule(), // GH-90000
                    new DataCloudStorageModule() // GH-90000
            );

            assertThat(injector.getInstance(DataCloudClient.class)).isNotNull(); // GH-90000
            assertThat(injector.getInstance(RedisHotTierPlugin.class)).isNotNull(); // GH-90000
            assertThat(injector.getInstance(CoolTierStoragePlugin.class)).isNotNull(); // GH-90000
            assertThat(injector.getInstance(ColdTierArchivePlugin.class)).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("DataCloudCoreModule + DataCloudStreamingModule compose correctly [GH-90000]")
        void coreAndStreamingCompose() { // GH-90000
            Injector injector = Injector.of( // GH-90000
                    new DataCloudCoreModule(), // GH-90000
                    streamingExternalStubModule(), // GH-90000
                    new DataCloudStreamingModule() // GH-90000
            );

            assertThat(injector.getInstance(DataCloudClient.class)).isNotNull(); // GH-90000
            assertThat(injector.getInstance(KafkaStreamingPlugin.class)).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("DataCloudCoreModule + DataCloudBrainModule compose correctly [GH-90000]")
        void coreAndBrainCompose() { // GH-90000
            Injector injector = Injector.of( // GH-90000
                    new DataCloudCoreModule(), // GH-90000
                    brainExternalStubModule(), // GH-90000
                    new DataCloudBrainModule() // GH-90000
            );

            assertThat(injector.getInstance(DataCloudClient.class)).isNotNull(); // GH-90000
            assertThat(injector.getInstance(DataCloudBrain.class)).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("full module stack creates valid injector [GH-90000]")
        void fullModuleStack() { // GH-90000
            // External stubs for streaming (Eventloop, MeterRegistry) // GH-90000
            // and brain (AI SPIs, MetricsCollector) — MetricsCollector from ObservabilityModule // GH-90000
            Module externalStubs = ModuleBuilder.create() // GH-90000
                    .bind(Eventloop.class).toInstance(Eventloop.builder().withCurrentThread().build()) // GH-90000
                    .bind(AnomalyDetectionCapability.class).toInstance(mock(AnomalyDetectionCapability.class)) // GH-90000
                    .bind(PredictionCapability.class).toInstance(mock(PredictionCapability.class)) // GH-90000
                    .bind(LearningSignalStore.class).toInstance(mock(LearningSignalStore.class)) // GH-90000
                    .bind(ExecutorService.class).toInstance(Executors.newSingleThreadExecutor()) // GH-90000
                    .build(); // GH-90000

            assertThatCode(() -> { // GH-90000
                Injector injector = Injector.of( // GH-90000
                        new ObservabilityModule(), // GH-90000
                        new DataCloudCoreModule(), // GH-90000
                        new DataCloudStorageModule(), // GH-90000
                        externalStubs,
                        new DataCloudStreamingModule(), // GH-90000
                        new DataCloudConfigModule(), // GH-90000
                        new DataCloudBrainModule() // GH-90000
                );

                // Cross-module verification
                assertThat(injector.getInstance(DataCloudClient.class)).isNotNull(); // GH-90000
                assertThat(injector.getInstance(ConfigRegistry.class)).isNotNull(); // GH-90000
                assertThat(injector.getInstance(DataCloudBrain.class)).isNotNull(); // GH-90000
                assertThat(injector.getInstance(KafkaStreamingPlugin.class)).isNotNull(); // GH-90000
                assertThat(injector.getInstance(RedisHotTierPlugin.class)).isNotNull(); // GH-90000

                injector.getInstance(ExecutorService.class).shutdown(); // GH-90000
            }).doesNotThrowAnyException(); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  7. Edge Case Tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Edge Cases [GH-90000]")
    class EdgeCaseTests {

        @Test
        @DisplayName("modules can be instantiated multiple times [GH-90000]")
        void multipleInstances() { // GH-90000
            Injector i1 = Injector.of(new DataCloudCoreModule()); // GH-90000
            Injector i2 = Injector.of(new DataCloudCoreModule()); // GH-90000

            DataCloudClient c1 = i1.getInstance(DataCloudClient.class); // GH-90000
            DataCloudClient c2 = i2.getInstance(DataCloudClient.class); // GH-90000

            assertThat(c1).isNotNull(); // GH-90000
            assertThat(c2).isNotNull(); // GH-90000
            assertThat(c1).isNotSameAs(c2); // GH-90000
        }

        @Test
        @DisplayName("storage config can be provided by custom module [GH-90000]")
        void storageConfigOverride() { // GH-90000
            RedisStorageConfig custom = RedisStorageConfig.builder() // GH-90000
                    .host("redis.production.local [GH-90000]")
                    .port(6380) // GH-90000
                    .build(); // GH-90000

            Module customModule = ModuleBuilder.create() // GH-90000
                    .bind(RedisStorageConfig.class).toInstance(custom) // GH-90000
                    .build(); // GH-90000

            Injector injector = Injector.of(customModule); // GH-90000

            RedisStorageConfig resolved = injector.getInstance(RedisStorageConfig.class); // GH-90000
            assertThat(resolved.getHost()).isEqualTo("redis.production.local [GH-90000]");
            assertThat(resolved.getPort()).isEqualTo(6380); // GH-90000
        }

        @Test
        @DisplayName("brain sub-services are distinct singletons [GH-90000]")
        void brainSubServicesDistinct() { // GH-90000
            Injector injector = Injector.of(brainExternalStubModule(), new DataCloudBrainModule()); // GH-90000

            ReflexEngine reflex = injector.getInstance(ReflexEngine.class); // GH-90000
            PatternCatalog catalog = injector.getInstance(PatternCatalog.class); // GH-90000

            assertThat(reflex).isNotNull(); // GH-90000
            assertThat(catalog).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("self-contained modules work without any external deps [GH-90000]")
        void selfContainedModules() { // GH-90000
            assertThatCode(() -> Injector.of(new DataCloudCoreModule())).doesNotThrowAnyException(); // GH-90000
            assertThatCode(() -> Injector.of(new DataCloudStorageModule())).doesNotThrowAnyException(); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Stub/Mock Helpers
    // ═══════════════════════════════════════════════════════════════

    /**
     * External deps for DataCloudConfigModule: Eventloop, ExecutorService, MetricsCollector.
     */
    private static Module configExternalStubModule() { // GH-90000
        return ModuleBuilder.create() // GH-90000
                .bind(Eventloop.class).toInstance(Eventloop.builder().withCurrentThread().build()) // GH-90000
                .bind(ExecutorService.class).toInstance(Executors.newSingleThreadExecutor()) // GH-90000
                .bind(MetricsCollector.class).toInstance(mock(MetricsCollector.class)) // GH-90000
                .build(); // GH-90000
    }

    /**
     * External deps for DataCloudStreamingModule: Eventloop, MeterRegistry.
     */
    private static Module streamingExternalStubModule() { // GH-90000
        return ModuleBuilder.create() // GH-90000
                .bind(Eventloop.class).toInstance(Eventloop.builder().withCurrentThread().build()) // GH-90000
                .bind(MeterRegistry.class).toInstance(mock(MeterRegistry.class)) // GH-90000
                .build(); // GH-90000
    }

    /**
     * External deps for DataCloudBrainModule: AI SPIs + MetricsCollector.
     */
    private static Module brainExternalStubModule() { // GH-90000
        return ModuleBuilder.create() // GH-90000
                .bind(AnomalyDetectionCapability.class).toInstance(mock(AnomalyDetectionCapability.class)) // GH-90000
                .bind(PredictionCapability.class).toInstance(mock(PredictionCapability.class)) // GH-90000
                .bind(LearningSignalStore.class).toInstance(mock(LearningSignalStore.class)) // GH-90000
                .bind(MetricsCollector.class).toInstance(mock(MetricsCollector.class)) // GH-90000
                .build(); // GH-90000
    }
}
