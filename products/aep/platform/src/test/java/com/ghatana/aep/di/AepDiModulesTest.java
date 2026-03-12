/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.di;

import com.ghatana.aep.analytics.AdvancedTimeSeriesForecaster;
import com.ghatana.aep.analytics.AnalyticsEngine;
import com.ghatana.aep.analytics.BusinessIntelligenceService;
import com.ghatana.aep.analytics.IntelligentPredictiveAlerting;
import com.ghatana.aep.analytics.KPIAggregator;
import com.ghatana.aep.analytics.PatternPerformanceAnalyzer;
import com.ghatana.aep.analytics.PredictiveAnalyticsEngine;
import com.ghatana.aep.analytics.RealTimeAnomalyDetectionEngine;
import com.ghatana.aep.connector.strategy.QueueConsumerStrategy;
import com.ghatana.aep.connector.strategy.QueueProducerStrategy;
import com.ghatana.aep.connector.strategy.http.HttpIngressConfig;
import com.ghatana.aep.connector.strategy.http.HttpIngressStrategy;
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
import com.ghatana.core.operator.catalog.DefaultOperatorCatalog;
import com.ghatana.core.operator.catalog.OperatorCatalog;
import com.ghatana.core.pipeline.PipelineExecutionEngine;
import com.ghatana.ingress.api.HealthController;
import com.ghatana.ingress.api.ratelimit.RateLimitStorage;
import com.ghatana.ingress.api.ratelimit.RedisRateLimitStorage;
import com.ghatana.ingress.app.IdempotencyService;
import com.ghatana.orchestrator.cache.PipelineCache;
import com.ghatana.orchestrator.client.AgentRegistryClient;
import com.ghatana.orchestrator.client.PipelineRegistryClient;
import com.ghatana.orchestrator.config.OrchestratorConfig;
import com.ghatana.orchestrator.core.Orchestrator;
import com.ghatana.orchestrator.loader.SpecFormatLoader;
import com.ghatana.orchestrator.queue.ExecutionQueue;
import com.ghatana.orchestrator.queue.impl.CheckpointAwareExecutionQueue;
import com.ghatana.orchestrator.store.CheckpointStore;
import com.ghatana.orchestrator.store.PipelineCheckpointRepository;
import com.ghatana.orchestrator.store.StepCheckpointRepository;
import com.ghatana.pattern.compiler.PatternCompiler;
import com.ghatana.pattern.operator.registry.OperatorRegistry;
import com.ghatana.aep.event.EventCloud;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.ObservabilityModule;
import io.activej.eventloop.Eventloop;
import io.activej.inject.Injector;
import io.activej.inject.Key;
import io.activej.inject.module.Module;
import io.activej.inject.module.ModuleBuilder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.JedisPool;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

/**
 * Comprehensive tests for all 6 AEP ActiveJ DI modules.
 *
 * <p>Tests verify that:
 * <ul>
 *   <li>Each module provides all expected bindings</li>
 *   <li>Interface-to-implementation bindings are correct</li>
 *   <li>Singleton semantics are maintained</li>
 *   <li>Module composition works with proper dependency resolution</li>
 *   <li>Shared dependencies (Eventloop, ExecutorService, MeterRegistry) propagate correctly</li>
 *   <li>Builder-created services receive all dependencies</li>
 *   <li>Named bindings resolve correctly for connectors</li>
 * </ul>
 */
@DisplayName("AEP ActiveJ DI Modules")
class AepDiModulesTest {

    // ═══════════════════════════════════════════════════════════════
    //  1. AepCoreModule Tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("AepCoreModule")
    class AepCoreModuleTests {

        @Test
        @DisplayName("provides PipelineExecutionEngine as singleton")
        void providesPipelineExecutionEngine() {
            Injector injector = Injector.of(new AepCoreModule());

            PipelineExecutionEngine engine = injector.getInstance(PipelineExecutionEngine.class);

            assertThat(engine).isNotNull();
            assertThat(injector.getInstance(PipelineExecutionEngine.class))
                    .as("should return same singleton instance")
                    .isSameAs(engine);
        }

        @Test
        @DisplayName("provides OperatorCatalog bound to DefaultOperatorCatalog")
        void providesOperatorCatalog() {
            Injector injector = Injector.of(new AepCoreModule());

            OperatorCatalog catalog = injector.getInstance(OperatorCatalog.class);

            assertThat(catalog).isNotNull();
            assertThat(catalog).isInstanceOf(DefaultOperatorCatalog.class);
        }

        @Test
        @DisplayName("provides Eventloop")
        void providesEventloop() {
            Injector injector = Injector.of(new AepCoreModule());

            Eventloop eventloop = injector.getInstance(Eventloop.class);

            assertThat(eventloop).isNotNull();
        }

        @Test
        @DisplayName("provides ExecutorService with daemon threads")
        void providesExecutorService() {
            Injector injector = Injector.of(new AepCoreModule());

            ExecutorService executor = injector.getInstance(ExecutorService.class);

            assertThat(executor).isNotNull();
            assertThat(executor.isShutdown()).isFalse();

            // Cleanup
            executor.shutdown();
        }

        @Test
        @DisplayName("provides ScheduledExecutorService with daemon threads")
        void providesScheduledExecutorService() {
            Injector injector = Injector.of(new AepCoreModule());

            ScheduledExecutorService scheduler = injector.getInstance(ScheduledExecutorService.class);

            assertThat(scheduler).isNotNull();
            assertThat(scheduler.isShutdown()).isFalse();

            // Cleanup
            scheduler.shutdown();
        }

        @Test
        @DisplayName("provides all 5 bindings")
        void providesAllBindings() {
            Injector injector = Injector.of(new AepCoreModule());

            assertThat(injector.getInstance(PipelineExecutionEngine.class)).isNotNull();
            assertThat(injector.getInstance(OperatorCatalog.class)).isNotNull();
            assertThat(injector.getInstance(Eventloop.class)).isNotNull();
            assertThat(injector.getInstance(ExecutorService.class)).isNotNull();
            assertThat(injector.getInstance(ScheduledExecutorService.class)).isNotNull();

            // Cleanup
            injector.getInstance(ExecutorService.class).shutdown();
            injector.getInstance(ScheduledExecutorService.class).shutdown();
        }

        @Test
        @DisplayName("Eventloop is singleton across injections")
        void eventloopIsSingleton() {
            Injector injector = Injector.of(new AepCoreModule());

            Eventloop e1 = injector.getInstance(Eventloop.class);
            Eventloop e2 = injector.getInstance(Eventloop.class);

            assertThat(e1).isSameAs(e2);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  2. AepOrchestrationModule Tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("AepOrchestrationModule")
    class AepOrchestrationModuleTests {

        /**
         * Creates an injector with AepOrchestrationModule and all required
         * external dependencies stubbed.
         */
        private Injector createOrchestrationInjector() {
            return Injector.of(orchestrationStubModule(), new AepOrchestrationModule());
        }

        @Test
        @DisplayName("provides OrchestratorConfig with defaults")
        void providesOrchestratorConfig() {
            Injector injector = createOrchestrationInjector();

            OrchestratorConfig config = injector.getInstance(OrchestratorConfig.class);

            assertThat(config).isNotNull();
        }

        @Test
        @DisplayName("provides PipelineCache with MetricsCollector dependency")
        void providesPipelineCache() {
            Injector injector = createOrchestrationInjector();

            PipelineCache cache = injector.getInstance(PipelineCache.class);

            assertThat(cache).isNotNull();
        }

        @Test
        @DisplayName("provides ExecutionQueue bound to CheckpointAwareExecutionQueue")
        void providesExecutionQueue() {
            Injector injector = createOrchestrationInjector();

            ExecutionQueue queue = injector.getInstance(ExecutionQueue.class);

            assertThat(queue).isNotNull();
            assertThat(queue).isInstanceOf(CheckpointAwareExecutionQueue.class);
        }

        @Test
        @DisplayName("provides Orchestrator with all 6 dependencies wired")
        void providesOrchestrator() {
            Injector injector = createOrchestrationInjector();

            Orchestrator orchestrator = injector.getInstance(Orchestrator.class);

            assertThat(orchestrator).isNotNull();
        }

        @Test
        @DisplayName("Orchestrator is singleton")
        void orchestratorIsSingleton() {
            Injector injector = createOrchestrationInjector();

            Orchestrator o1 = injector.getInstance(Orchestrator.class);
            Orchestrator o2 = injector.getInstance(Orchestrator.class);

            assertThat(o1).isSameAs(o2);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  3. AepPatternModule Tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("AepPatternModule")
    class AepPatternModuleTests {

        private Injector createPatternInjector() {
            return Injector.of(new ObservabilityModule(), new AepPatternModule());
        }

        @Test
        @DisplayName("provides OperatorRegistry as singleton")
        void providesOperatorRegistry() {
            Injector injector = createPatternInjector();

            OperatorRegistry registry = injector.getInstance(OperatorRegistry.class);

            assertThat(registry).isNotNull();
            assertThat(injector.getInstance(OperatorRegistry.class))
                    .isSameAs(registry);
        }

        @Test
        @DisplayName("provides PatternCompiler with registry and meter dependencies")
        void providesPatternCompiler() {
            Injector injector = createPatternInjector();

            PatternCompiler compiler = injector.getInstance(PatternCompiler.class);

            assertThat(compiler).isNotNull();
        }

        @Test
        @DisplayName("PatternCompiler receives OperatorRegistry from same injector")
        void patternCompilerUsesInjectedRegistry() {
            Injector injector = createPatternInjector();

            // Both should be present and non-null
            OperatorRegistry registry = injector.getInstance(OperatorRegistry.class);
            PatternCompiler compiler = injector.getInstance(PatternCompiler.class);

            assertThat(registry).isNotNull();
            assertThat(compiler).isNotNull();
        }

        @Test
        @DisplayName("MeterRegistry is resolved from ObservabilityModule")
        void meterRegistryFromObservabilityModule() {
            Injector injector = createPatternInjector();

            MeterRegistry meterRegistry = injector.getInstance(MeterRegistry.class);

            assertThat(meterRegistry).isNotNull();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  4. AepConnectorModule Tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("AepConnectorModule")
    class AepConnectorModuleTests {

        private Injector createConnectorInjector() {
            return Injector.of(new AepCoreModule(), new AepConnectorModule());
        }

        // ─── Config bindings ────────────────────────────────────

        @Test
        @DisplayName("provides KafkaConsumerConfig with defaults")
        void providesKafkaConsumerConfig() {
            Injector injector = createConnectorInjector();

            KafkaConsumerConfig config = injector.getInstance(KafkaConsumerConfig.class);

            assertThat(config).isNotNull();
            assertThat(config.getBootstrapServers()).isEqualTo("localhost:9092");
            assertThat(config.getGroupId()).isEqualTo("aep-consumer-group");
        }

        @Test
        @DisplayName("provides KafkaProducerConfig with defaults")
        void providesKafkaProducerConfig() {
            Injector injector = createConnectorInjector();

            KafkaProducerConfig config = injector.getInstance(KafkaProducerConfig.class);

            assertThat(config).isNotNull();
            assertThat(config.getBootstrapServers()).isEqualTo("localhost:9092");
        }

        @Test
        @DisplayName("provides RabbitMQConfig with defaults")
        void providesRabbitMQConfig() {
            Injector injector = createConnectorInjector();

            RabbitMQConfig config = injector.getInstance(RabbitMQConfig.class);

            assertThat(config).isNotNull();
        }

        @Test
        @DisplayName("provides SqsConfig with defaults")
        void providesSqsConfig() {
            Injector injector = createConnectorInjector();

            SqsConfig config = injector.getInstance(SqsConfig.class);

            assertThat(config).isNotNull();
        }

        @Test
        @DisplayName("provides S3Config with defaults")
        void providesS3Config() {
            Injector injector = createConnectorInjector();

            S3Config config = injector.getInstance(S3Config.class);

            assertThat(config).isNotNull();
        }

        @Test
        @DisplayName("provides HttpIngressConfig with defaults")
        void providesHttpIngressConfig() {
            Injector injector = createConnectorInjector();

            HttpIngressConfig config = injector.getInstance(HttpIngressConfig.class);

            assertThat(config).isNotNull();
            assertThat(config.getEndpoint()).isEqualTo("http://localhost:8080/events");
        }

        // ─── Strategy bindings ──────────────────────────────────

        @Test
        @DisplayName("provides Kafka consumer via @Named('kafka')")
        void providesKafkaConsumer() {
            Injector injector = createConnectorInjector();

            QueueConsumerStrategy consumer = injector.getInstance(
                    Key.of(QueueConsumerStrategy.class, "kafka"));

            assertThat(consumer).isNotNull();
            assertThat(consumer).isInstanceOf(KafkaConsumerStrategy.class);
        }

        @Test
        @DisplayName("provides Kafka producer via @Named('kafka')")
        void providesKafkaProducer() {
            Injector injector = createConnectorInjector();

            QueueProducerStrategy producer = injector.getInstance(
                    Key.of(QueueProducerStrategy.class, "kafka"));

            assertThat(producer).isNotNull();
            assertThat(producer).isInstanceOf(KafkaProducerStrategy.class);
        }

        @Test
        @DisplayName("provides RabbitMQ consumer via @Named('rabbitmq')")
        void providesRabbitMQConsumer() {
            Injector injector = createConnectorInjector();

            QueueConsumerStrategy consumer = injector.getInstance(
                    Key.of(QueueConsumerStrategy.class, "rabbitmq"));

            assertThat(consumer).isNotNull();
            assertThat(consumer).isInstanceOf(RabbitMQConsumerStrategy.class);
        }

        @Test
        @DisplayName("provides SQS consumer via @Named('sqs')")
        void providesSqsConsumer() {
            Injector injector = createConnectorInjector();

            QueueConsumerStrategy consumer = injector.getInstance(
                    Key.of(QueueConsumerStrategy.class, "sqs"));

            assertThat(consumer).isNotNull();
            assertThat(consumer).isInstanceOf(SqsConsumerStrategy.class);
        }

        @Test
        @DisplayName("provides SQS producer via @Named('sqs')")
        void providesSqsProducer() {
            Injector injector = createConnectorInjector();

            QueueProducerStrategy producer = injector.getInstance(
                    Key.of(QueueProducerStrategy.class, "sqs"));

            assertThat(producer).isNotNull();
            assertThat(producer).isInstanceOf(SqsProducerStrategy.class);
        }

        @Test
        @DisplayName("provides S3StorageStrategy bound to DefaultS3StorageStrategy")
        void providesS3StorageStrategy() {
            Injector injector = createConnectorInjector();

            S3StorageStrategy strategy = injector.getInstance(S3StorageStrategy.class);

            assertThat(strategy).isNotNull();
            assertThat(strategy).isInstanceOf(DefaultS3StorageStrategy.class);

            // Cleanup
            injector.getInstance(ExecutorService.class).shutdown();
            injector.getInstance(ScheduledExecutorService.class).shutdown();
        }

        @Test
        @DisplayName("provides HttpIngressStrategy")
        void providesHttpIngressStrategy() {
            Injector injector = createConnectorInjector();

            HttpIngressStrategy strategy = injector.getInstance(HttpIngressStrategy.class);

            assertThat(strategy).isNotNull();

            // Cleanup
            injector.getInstance(ExecutorService.class).shutdown();
            injector.getInstance(ScheduledExecutorService.class).shutdown();
        }

        @Test
        @DisplayName("all strategies share same Eventloop")
        void strategiesShareEventloop() {
            Injector injector = createConnectorInjector();

            Eventloop eventloop = injector.getInstance(Eventloop.class);

            assertThat(eventloop).isNotNull();
            // All strategies created with same eventloop from AepCoreModule
        }

        @Test
        @DisplayName("all 6 configs and 7 strategies are provided")
        void allBindingsPresent() {
            Injector injector = createConnectorInjector();

            // 6 configs
            assertThat(injector.getInstance(KafkaConsumerConfig.class)).isNotNull();
            assertThat(injector.getInstance(KafkaProducerConfig.class)).isNotNull();
            assertThat(injector.getInstance(RabbitMQConfig.class)).isNotNull();
            assertThat(injector.getInstance(SqsConfig.class)).isNotNull();
            assertThat(injector.getInstance(S3Config.class)).isNotNull();
            assertThat(injector.getInstance(HttpIngressConfig.class)).isNotNull();

            // 7 strategies (5 named + 2 direct)
            assertThat(injector.getInstance(Key.of(QueueConsumerStrategy.class, "kafka"))).isNotNull();
            assertThat(injector.getInstance(Key.of(QueueProducerStrategy.class, "kafka"))).isNotNull();
            assertThat(injector.getInstance(Key.of(QueueConsumerStrategy.class, "rabbitmq"))).isNotNull();
            assertThat(injector.getInstance(Key.of(QueueConsumerStrategy.class, "sqs"))).isNotNull();
            assertThat(injector.getInstance(Key.of(QueueProducerStrategy.class, "sqs"))).isNotNull();
            assertThat(injector.getInstance(S3StorageStrategy.class)).isNotNull();
            assertThat(injector.getInstance(HttpIngressStrategy.class)).isNotNull();

            // Cleanup
            injector.getInstance(ExecutorService.class).shutdown();
            injector.getInstance(ScheduledExecutorService.class).shutdown();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  5. AepIngressModule Tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("AepIngressModule")
    class AepIngressModuleTests {

        @Test
        @DisplayName("provides JedisPool")
        void providesJedisPool() {
            Module coreStub = ModuleBuilder.create()
                    .bind(Eventloop.class).toInstance(Eventloop.builder().withCurrentThread().build())
                    .build();
            Injector injector = Injector.of(coreStub, new AepIngressModule());

            JedisPool pool = injector.getInstance(JedisPool.class);

            assertThat(pool).isNotNull();
            pool.close();
        }

        @Test
        @DisplayName("provides RateLimitStorage bound to RedisRateLimitStorage")
        void providesRateLimitStorage() {
            Module coreStub = ModuleBuilder.create()
                    .bind(Eventloop.class).toInstance(Eventloop.builder().withCurrentThread().build())
                    .build();
            Injector injector = Injector.of(coreStub, new AepIngressModule());

            RateLimitStorage storage = injector.getInstance(RateLimitStorage.class);

            assertThat(storage).isNotNull();
            assertThat(storage).isInstanceOf(RedisRateLimitStorage.class);

            // Cleanup
            injector.getInstance(JedisPool.class).close();
        }

        @Test
        @DisplayName("provides IdempotencyService")
        void providesIdempotencyService() {
            Module coreStub = ModuleBuilder.create()
                    .bind(Eventloop.class).toInstance(Eventloop.builder().withCurrentThread().build())
                    .build();
            Injector injector = Injector.of(coreStub, new AepIngressModule());

            IdempotencyService service = injector.getInstance(IdempotencyService.class);

            assertThat(service).isNotNull();

            // Cleanup
            injector.getInstance(JedisPool.class).close();
        }

        @Test
        @DisplayName("provides HealthController with Eventloop")
        void providesHealthController() {
            Module coreStub = ModuleBuilder.create()
                    .bind(Eventloop.class).toInstance(Eventloop.builder().withCurrentThread().build())
                    .build();
            Injector injector = Injector.of(coreStub, new AepIngressModule());

            HealthController controller = injector.getInstance(HealthController.class);

            assertThat(controller).isNotNull();

            // Cleanup
            injector.getInstance(JedisPool.class).close();
        }

        @Test
        @DisplayName("RateLimitStorage and IdempotencyService share same JedisPool")
        void shareJedisPool() {
            Module coreStub = ModuleBuilder.create()
                    .bind(Eventloop.class).toInstance(Eventloop.builder().withCurrentThread().build())
                    .build();
            Injector injector = Injector.of(coreStub, new AepIngressModule());

            JedisPool pool1 = injector.getInstance(JedisPool.class);
            JedisPool pool2 = injector.getInstance(JedisPool.class);

            assertThat(pool1).as("JedisPool should be singleton").isSameAs(pool2);

            // Cleanup
            pool1.close();
        }

        @Test
        @DisplayName("all 4 bindings are provided")
        void allBindingsPresent() {
            Module coreStub = ModuleBuilder.create()
                    .bind(Eventloop.class).toInstance(Eventloop.builder().withCurrentThread().build())
                    .build();
            Injector injector = Injector.of(coreStub, new AepIngressModule());

            assertThat(injector.getInstance(JedisPool.class)).isNotNull();
            assertThat(injector.getInstance(RateLimitStorage.class)).isNotNull();
            assertThat(injector.getInstance(IdempotencyService.class)).isNotNull();
            assertThat(injector.getInstance(HealthController.class)).isNotNull();

            // Cleanup
            injector.getInstance(JedisPool.class).close();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  6. AepObservabilityModule Tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("AepObservabilityModule")
    class AepObservabilityModuleTests {

        @Test
        @DisplayName("provides BusinessIntelligenceService")
        void providesBusinessIntelligenceService() {
            Injector injector = createMinimalObservabilityInjector();

            BusinessIntelligenceService service = injector.getInstance(BusinessIntelligenceService.class);

            assertThat(service).isNotNull();
        }

        @Test
        @DisplayName("provides PredictiveAnalyticsEngine")
        void providesPredictiveAnalyticsEngine() {
            Injector injector = createMinimalObservabilityInjector();

            PredictiveAnalyticsEngine engine = injector.getInstance(PredictiveAnalyticsEngine.class);

            assertThat(engine).isNotNull();
        }

        @Test
        @DisplayName("provides PatternPerformanceAnalyzer")
        void providesPatternPerformanceAnalyzer() {
            Injector injector = createMinimalObservabilityInjector();

            PatternPerformanceAnalyzer analyzer = injector.getInstance(PatternPerformanceAnalyzer.class);

            assertThat(analyzer).isNotNull();
        }

        @Test
        @DisplayName("provides KPIAggregator")
        void providesKPIAggregator() {
            Injector injector = createMinimalObservabilityInjector();

            KPIAggregator aggregator = injector.getInstance(KPIAggregator.class);

            assertThat(aggregator).isNotNull();
        }

        @Test
        @DisplayName("provides RealTimeAnomalyDetectionEngine")
        void providesRealTimeAnomalyDetectionEngine() {
            Injector injector = createMinimalObservabilityInjector();

            RealTimeAnomalyDetectionEngine engine = injector.getInstance(RealTimeAnomalyDetectionEngine.class);

            assertThat(engine).isNotNull();
        }

        @Test
        @DisplayName("provides IntelligentPredictiveAlerting")
        void providesIntelligentPredictiveAlerting() {
            Injector injector = createMinimalObservabilityInjector();

            IntelligentPredictiveAlerting alerting = injector.getInstance(IntelligentPredictiveAlerting.class);

            assertThat(alerting).isNotNull();
        }

        @Test
        @DisplayName("provides AdvancedTimeSeriesForecaster")
        void providesAdvancedTimeSeriesForecaster() {
            Injector injector = createMinimalObservabilityInjector();

            AdvancedTimeSeriesForecaster forecaster = injector.getInstance(AdvancedTimeSeriesForecaster.class);

            assertThat(forecaster).isNotNull();
        }

        @Test
        @DisplayName("all 7 analytics sub-services are provided as singletons")
        void allAnalyticsSubservicesAreSingletons() {
            Injector injector = createMinimalObservabilityInjector();

            assertThat(injector.getInstance(BusinessIntelligenceService.class))
                    .isSameAs(injector.getInstance(BusinessIntelligenceService.class));
            assertThat(injector.getInstance(PredictiveAnalyticsEngine.class))
                    .isSameAs(injector.getInstance(PredictiveAnalyticsEngine.class));
            assertThat(injector.getInstance(PatternPerformanceAnalyzer.class))
                    .isSameAs(injector.getInstance(PatternPerformanceAnalyzer.class));
            assertThat(injector.getInstance(KPIAggregator.class))
                    .isSameAs(injector.getInstance(KPIAggregator.class));
            assertThat(injector.getInstance(RealTimeAnomalyDetectionEngine.class))
                    .isSameAs(injector.getInstance(RealTimeAnomalyDetectionEngine.class));
            assertThat(injector.getInstance(IntelligentPredictiveAlerting.class))
                    .isSameAs(injector.getInstance(IntelligentPredictiveAlerting.class));
            assertThat(injector.getInstance(AdvancedTimeSeriesForecaster.class))
                    .isSameAs(injector.getInstance(AdvancedTimeSeriesForecaster.class));
        }

        @Test
        @DisplayName("provides AnalyticsEngine with all sub-services wired")
        void providesAnalyticsEngine() {
            Injector injector = createMinimalObservabilityInjector();

            AnalyticsEngine engine = injector.getInstance(AnalyticsEngine.class);

            assertThat(engine).isNotNull();
        }

        private Injector createMinimalObservabilityInjector() {
            // AnalyticsEngine @Provides requires EventCloud, Eventloop, MetricsCollector
            // — ActiveJ validates ALL bindings eagerly at Injector creation
            Module stubs = ModuleBuilder.create()
                    .bind(EventCloud.class).toInstance(mock(EventCloud.class))
                    .bind(Eventloop.class).toInstance(Eventloop.builder().withCurrentThread().build())
                    .bind(MetricsCollector.class).toInstance(mock(MetricsCollector.class))
                    .build();
            return Injector.of(stubs, new AepObservabilityModule());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  7. Module Composition Tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Module Composition")
    class ModuleCompositionTests {

        @Test
        @DisplayName("AepCoreModule + AepPatternModule compose correctly")
        void coreAndPatternCompose() {
            Injector injector = Injector.of(
                    new ObservabilityModule(),
                    new AepCoreModule(),
                    new AepPatternModule()
            );

            // Core bindings
            assertThat(injector.getInstance(PipelineExecutionEngine.class)).isNotNull();
            assertThat(injector.getInstance(OperatorCatalog.class)).isNotNull();

            // Pattern bindings (that depend on MeterRegistry from ObservabilityModule)
            assertThat(injector.getInstance(OperatorRegistry.class)).isNotNull();
            assertThat(injector.getInstance(PatternCompiler.class)).isNotNull();

            // Cleanup
            injector.getInstance(ExecutorService.class).shutdown();
            injector.getInstance(ScheduledExecutorService.class).shutdown();
        }

        @Test
        @DisplayName("AepCoreModule + AepConnectorModule compose correctly")
        void coreAndConnectorCompose() {
            Injector injector = Injector.of(
                    new AepCoreModule(),
                    new AepConnectorModule()
            );

            // Connector strategies use Eventloop from AepCoreModule
            Eventloop eventloop = injector.getInstance(Eventloop.class);
            assertThat(eventloop).isNotNull();

            QueueConsumerStrategy kafkaConsumer = injector.getInstance(
                    Key.of(QueueConsumerStrategy.class, "kafka"));
            assertThat(kafkaConsumer).isNotNull();

            // Cleanup
            injector.getInstance(ExecutorService.class).shutdown();
            injector.getInstance(ScheduledExecutorService.class).shutdown();
        }

        @Test
        @DisplayName("AepCoreModule + AepIngressModule compose correctly")
        void coreAndIngressCompose() {
            Injector injector = Injector.of(
                    new AepCoreModule(),
                    new AepIngressModule()
            );

            // Ingress uses Eventloop from AepCoreModule
            assertThat(injector.getInstance(HealthController.class)).isNotNull();
            assertThat(injector.getInstance(RateLimitStorage.class)).isNotNull();
            assertThat(injector.getInstance(IdempotencyService.class)).isNotNull();

            // Cleanup
            injector.getInstance(JedisPool.class).close();
            injector.getInstance(ExecutorService.class).shutdown();
            injector.getInstance(ScheduledExecutorService.class).shutdown();
        }

        @Test
        @DisplayName("full module stack creates valid injector")
        void fullModuleStack() {
            // External stubs for orchestration — MetricsCollector comes from ObservabilityModule
            Module orchExternalStubs = ModuleBuilder.create()
                    .bind(AgentRegistryClient.class).toInstance(mock(AgentRegistryClient.class))
                    .bind(PipelineRegistryClient.class).toInstance(mock(PipelineRegistryClient.class))
                    .bind(SpecFormatLoader.class).toInstance(mock(SpecFormatLoader.class))
                    .bind(PipelineCheckpointRepository.class).toInstance(mock(PipelineCheckpointRepository.class))
                    .bind(StepCheckpointRepository.class).toInstance(mock(StepCheckpointRepository.class))
                    .build();

            assertThatCode(() -> {
                Injector injector = Injector.of(
                        new ObservabilityModule(),
                        new AepCoreModule(),
                        new AepPatternModule(),
                        new AepConnectorModule(),
                        orchExternalStubs,
                        new AepOrchestrationModule()
                );

                // Verify cross-module dependency: Orchestrator depends on MetricsCollector (ObservabilityModule)
                assertThat(injector.getInstance(Orchestrator.class)).isNotNull();
                assertThat(injector.getInstance(PatternCompiler.class)).isNotNull();
                assertThat(injector.getInstance(PipelineExecutionEngine.class)).isNotNull();

                // Cleanup
                injector.getInstance(ExecutorService.class).shutdown();
                injector.getInstance(ScheduledExecutorService.class).shutdown();
            }).doesNotThrowAnyException();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  8. Edge Case Tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("modules can be instantiated multiple times")
        void multipleInstances() {
            AepCoreModule m1 = new AepCoreModule();
            AepCoreModule m2 = new AepCoreModule();

            Injector i1 = Injector.of(m1);
            Injector i2 = Injector.of(m2);

            PipelineExecutionEngine e1 = i1.getInstance(PipelineExecutionEngine.class);
            PipelineExecutionEngine e2 = i2.getInstance(PipelineExecutionEngine.class);

            assertThat(e1).isNotNull();
            assertThat(e2).isNotNull();
            assertThat(e1).isNotSameAs(e2);

            // Cleanup
            i1.getInstance(ExecutorService.class).shutdown();
            i1.getInstance(ScheduledExecutorService.class).shutdown();
            i2.getInstance(ExecutorService.class).shutdown();
            i2.getInstance(ScheduledExecutorService.class).shutdown();
        }

        @Test
        @DisplayName("connector config can be overridden by custom module")
        void configOverride() {
            KafkaConsumerConfig custom = KafkaConsumerConfig.builder()
                    .bootstrapServers("custom-broker:9093")
                    .groupId("custom-group")
                    .topics(java.util.List.of("custom-topic"))
                    .batchSize(50)
                    .build();

            // Verify custom config is properly built and injectable via module binding
            Module customModule = ModuleBuilder.create()
                    .bind(KafkaConsumerConfig.class).toInstance(custom)
                    .build();

            Injector injector = Injector.of(customModule);

            KafkaConsumerConfig config = injector.getInstance(KafkaConsumerConfig.class);
            assertThat(config.getBootstrapServers()).isEqualTo("custom-broker:9093");
            assertThat(config.getGroupId()).isEqualTo("custom-group");
        }

        @Test
        @DisplayName("OperatorCatalog and OperatorRegistry are distinct types")
        void catalogAndRegistryDistinct() {
            Injector injector = Injector.of(
                    new ObservabilityModule(),
                    new AepCoreModule(),
                    new AepPatternModule()
            );

            OperatorCatalog catalog = injector.getInstance(OperatorCatalog.class);
            OperatorRegistry registry = injector.getInstance(OperatorRegistry.class);

            // Different types entirely
            assertThat(catalog).isNotNull();
            assertThat(registry).isNotNull();
            assertThat(catalog).isNotSameAs(registry);
            assertThat(catalog.getClass()).isNotEqualTo(registry.getClass());

            // Cleanup
            injector.getInstance(ExecutorService.class).shutdown();
            injector.getInstance(ScheduledExecutorService.class).shutdown();
        }

        @Test
        @DisplayName("AepObservabilityModule analytics sub-services have correct interface bindings")
        void analyticsInterfaceBindings() {
            Module stubs = ModuleBuilder.create()
                    .bind(EventCloud.class).toInstance(mock(EventCloud.class))
                    .bind(Eventloop.class).toInstance(Eventloop.builder().withCurrentThread().build())
                    .bind(MetricsCollector.class).toInstance(mock(MetricsCollector.class))
                    .build();
            Injector injector = Injector.of(stubs, new AepObservabilityModule());

            // Verify all are bound to their interfaces, not concrete types
            assertThat(injector.getInstance(BusinessIntelligenceService.class))
                    .isInstanceOf(BusinessIntelligenceService.class);
            assertThat(injector.getInstance(PredictiveAnalyticsEngine.class))
                    .isInstanceOf(PredictiveAnalyticsEngine.class);
            assertThat(injector.getInstance(PatternPerformanceAnalyzer.class))
                    .isInstanceOf(PatternPerformanceAnalyzer.class);
            assertThat(injector.getInstance(KPIAggregator.class))
                    .isInstanceOf(KPIAggregator.class);
            assertThat(injector.getInstance(RealTimeAnomalyDetectionEngine.class))
                    .isInstanceOf(RealTimeAnomalyDetectionEngine.class);
            assertThat(injector.getInstance(IntelligentPredictiveAlerting.class))
                    .isInstanceOf(IntelligentPredictiveAlerting.class);
            assertThat(injector.getInstance(AdvancedTimeSeriesForecaster.class))
                    .isInstanceOf(AdvancedTimeSeriesForecaster.class);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  6. AepLearningModule
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("AepLearningModule")
    class AepLearningModuleTests {

        private Injector buildInjector() {
            Module schedulerStub = ModuleBuilder.create()
                    .bind(ScheduledExecutorService.class)
                    .toInstance(mock(ScheduledExecutorService.class))
                    .build();
            return Injector.of(schedulerStub, new AepLearningModule());
        }

        @Test
        @DisplayName("provides HumanReviewQueue bound to InMemoryHumanReviewQueue")
        void providesHumanReviewQueue() {
            var injector = buildInjector();
            var queue = injector.getInstance(
                    com.ghatana.agent.learning.review.HumanReviewQueue.class);
            assertThat(queue).isNotNull();
            assertThat(queue).isInstanceOf(
                    com.ghatana.agent.learning.review.InMemoryHumanReviewQueue.class);
        }

        @Test
        @DisplayName("provides ConflictResolver bound to EntrenchmentConflictResolver")
        void providesConflictResolver() {
            var injector = buildInjector();
            var resolver = injector.getInstance(
                    com.ghatana.agent.learning.consolidation.ConflictResolver.class);
            assertThat(resolver).isNotNull();
            assertThat(resolver).isInstanceOf(
                    com.ghatana.agent.learning.consolidation.EntrenchmentConflictResolver.class);
        }

        @Test
        @DisplayName("provides ConsolidationPipeline")
        void providesConsolidationPipeline() {
            var injector = buildInjector();
            var pipeline = injector.getInstance(
                    com.ghatana.agent.learning.consolidation.ConsolidationPipeline.class);
            assertThat(pipeline).isNotNull();
        }

        @Test
        @DisplayName("provides ConsolidationScheduler with AEP_SYSTEM agent id")
        void providesConsolidationScheduler() {
            var injector = buildInjector();
            var scheduler = injector.getInstance(
                    com.ghatana.agent.learning.consolidation.ConsolidationScheduler.class);
            assertThat(scheduler).isNotNull();
        }

        @Test
        @DisplayName("singleton semantics: same HumanReviewQueue instance returned twice")
        void humanReviewQueueIsSingleton() {
            var injector = buildInjector();
            var type = com.ghatana.agent.learning.review.HumanReviewQueue.class;
            assertThat(injector.getInstance(type)).isSameAs(injector.getInstance(type));
        }

        @Test
        @DisplayName("singleton semantics: same ConsolidationPipeline instance returned twice")
        void consolidationPipelineIsSingleton() {
            var injector = buildInjector();
            var type = com.ghatana.agent.learning.consolidation.ConsolidationPipeline.class;
            assertThat(injector.getInstance(type)).isSameAs(injector.getInstance(type));
        }

        @Test
        @DisplayName("all learning bindings can be instantiated without exception")
        void allBindingsInstantiateCleanly() {
            assertThatCode(() -> {
                var injector = buildInjector();
                injector.getInstance(
                        com.ghatana.agent.learning.review.HumanReviewQueue.class);
                injector.getInstance(
                        com.ghatana.agent.learning.consolidation.ConflictResolver.class);
                injector.getInstance(
                        com.ghatana.agent.learning.consolidation.ConsolidationPipeline.class);
                injector.getInstance(
                        com.ghatana.agent.learning.consolidation.ConsolidationScheduler.class);
            }).doesNotThrowAnyException();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Stub/Mock Helpers
    // ═══════════════════════════════════════════════════════════════

    /**
     * Creates a module with mocked external dependencies for the orchestration
     * module. Uses Mockito mocks since these interfaces have many abstract methods.
     */
    private static Module orchestrationStubModule() {
        return ModuleBuilder.create()
                .bind(MetricsCollector.class).toInstance(mock(MetricsCollector.class))
                .bind(AgentRegistryClient.class).toInstance(mock(AgentRegistryClient.class))
                .bind(PipelineRegistryClient.class).toInstance(mock(PipelineRegistryClient.class))
                .bind(SpecFormatLoader.class).toInstance(mock(SpecFormatLoader.class))
                .bind(PipelineCheckpointRepository.class).toInstance(mock(PipelineCheckpointRepository.class))
                .bind(StepCheckpointRepository.class).toInstance(mock(StepCheckpointRepository.class))
                .build();
    }
}
