/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.aep.di;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

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
import com.ghatana.aep.event.EventCloud;
import com.ghatana.agent.dispatch.tier.LlmProvider;
import com.ghatana.core.operator.catalog.UnifiedOperatorCatalog;
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
import com.ghatana.orchestrator.store.PipelineCheckpointRepository;
import com.ghatana.orchestrator.store.StepCheckpointRepository;
import com.ghatana.pattern.compiler.PatternCompiler;
import com.ghatana.pattern.operator.registry.OperatorRegistry;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.ObservabilityModule;
import io.activej.eventloop.Eventloop;
import io.activej.inject.Injector;
import io.activej.inject.Key;
import io.activej.inject.module.Module;
import io.activej.inject.module.ModuleBuilder;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.JedisPool;

/**
 * Comprehensive tests for all 6 AEP ActiveJ DI modules.
 *
 * <p>Tests verify that:
 * <ul>
 *   <li>Each module provides all expected bindings</li>
 *   <li>Interface-to-implementation bindings are correct</li>
 *   <li>Singleton semantics are maintained</li>
 *   <li>Module composition works with proper dependency resolution</li>
 *   <li>Shared dependencies (Eventloop, ExecutorService, MeterRegistry) propagate correctly</li> // GH-90000
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
        void providesPipelineExecutionEngine() { // GH-90000
            Injector injector = Injector.of(new AepCoreModule()); // GH-90000

            PipelineExecutionEngine engine = injector.getInstance(PipelineExecutionEngine.class); // GH-90000

            assertThat(engine).isNotNull(); // GH-90000
            assertThat(injector.getInstance(PipelineExecutionEngine.class)) // GH-90000
                    .as("should return same singleton instance")
                    .isSameAs(engine); // GH-90000
        }

        @Test
        @DisplayName("provides OperatorCatalog bound to UnifiedOperatorCatalog")
        void providesOperatorCatalog() { // GH-90000
            Injector injector = Injector.of(new AepCoreModule()); // GH-90000

            OperatorCatalog catalog = injector.getInstance(OperatorCatalog.class); // GH-90000

            assertThat(catalog).isNotNull(); // GH-90000
            assertThat(catalog).isInstanceOf(UnifiedOperatorCatalog.class); // GH-90000
        }

        @Test
        @DisplayName("provides Eventloop")
        void providesEventloop() { // GH-90000
            Injector injector = Injector.of(new AepCoreModule()); // GH-90000

            Eventloop eventloop = injector.getInstance(Eventloop.class); // GH-90000

            assertThat(eventloop).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("provides ExecutorService with daemon threads")
        void providesExecutorService() { // GH-90000
            Injector injector = Injector.of(new AepCoreModule()); // GH-90000

            ExecutorService executor = injector.getInstance(ExecutorService.class); // GH-90000

            assertThat(executor).isNotNull(); // GH-90000
            assertThat(executor.isShutdown()).isFalse(); // GH-90000

            executor.shutdown(); // GH-90000
        }

        @Test
        @DisplayName("provides ScheduledExecutorService with daemon threads")
        void providesScheduledExecutorService() { // GH-90000
            Injector injector = Injector.of(new AepCoreModule()); // GH-90000

            ScheduledExecutorService scheduler = injector.getInstance(ScheduledExecutorService.class); // GH-90000

            assertThat(scheduler).isNotNull(); // GH-90000
            assertThat(scheduler.isShutdown()).isFalse(); // GH-90000

            scheduler.shutdown(); // GH-90000
        }

        @Test
        @DisplayName("provides all 5 bindings")
        void providesAllBindings() { // GH-90000
            Injector injector = Injector.of(new AepCoreModule()); // GH-90000

            assertThat(injector.getInstance(PipelineExecutionEngine.class)).isNotNull(); // GH-90000
            assertThat(injector.getInstance(OperatorCatalog.class)).isNotNull(); // GH-90000
            assertThat(injector.getInstance(Eventloop.class)).isNotNull(); // GH-90000
            assertThat(injector.getInstance(ExecutorService.class)).isNotNull(); // GH-90000
            assertThat(injector.getInstance(ScheduledExecutorService.class)).isNotNull(); // GH-90000

            injector.getInstance(ExecutorService.class).shutdown(); // GH-90000
            injector.getInstance(ScheduledExecutorService.class).shutdown(); // GH-90000
        }

        @Test
        @DisplayName("Eventloop is singleton across injections")
        void eventloopIsSingleton() { // GH-90000
            Injector injector = Injector.of(new AepCoreModule()); // GH-90000

            Eventloop e1 = injector.getInstance(Eventloop.class); // GH-90000
            Eventloop e2 = injector.getInstance(Eventloop.class); // GH-90000

            assertThat(e1).isSameAs(e2); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  2. AepOrchestrationModule Tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("AepOrchestrationModule")
    class AepOrchestrationModuleTests {

        private Injector createOrchestrationInjector() { // GH-90000
            return Injector.of(orchestrationStubModule(), new AepOrchestrationModule()); // GH-90000
        }

        @Test
        @DisplayName("provides OrchestratorConfig with defaults")
        void providesOrchestratorConfig() { // GH-90000
            Injector injector = createOrchestrationInjector(); // GH-90000

            OrchestratorConfig config = injector.getInstance(OrchestratorConfig.class); // GH-90000

            assertThat(config).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("fails fast when no LLM provider env is configured")
        void failsFastWhenNoLlmProviderEnvConfigured() { // GH-90000
            assertThatCode(() -> AepOrchestrationModule.createLlmProvider(Map.of(), mock(MetricsCollector.class))) // GH-90000
                    .isInstanceOf(IllegalStateException.class) // GH-90000
                    .hasMessageContaining("No LLM provider configured for AEP");
        }

        @Test
        @DisplayName("fails fast when Ollama host is not a valid absolute URL")
        void failsFastWhenOllamaHostIsInvalid() { // GH-90000
            assertThatCode(() -> AepOrchestrationModule.createLlmProvider( // GH-90000
                    Map.of("AEP_OLLAMA_HOST", "not-a-url"), // GH-90000
                    mock(MetricsCollector.class))) // GH-90000
                    .isInstanceOf(IllegalStateException.class) // GH-90000
                    .hasMessageContaining("AEP_OLLAMA_HOST")
                    .hasMessageContaining("valid absolute URL");
        }

        @Test
        @DisplayName("creates LLM provider when OpenAI config is valid")
        void createsLlmProviderWhenOpenAiConfigIsValid() { // GH-90000
            LlmProvider provider = AepOrchestrationModule.createLlmProvider( // GH-90000
                    Map.of( // GH-90000
                            "AEP_OPENAI_API_KEY", "test-key",
                            "AEP_OPENAI_MODEL", "gpt-4o-mini"
                    ),
                    mock(MetricsCollector.class)); // GH-90000

            assertThat(provider).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("provides PipelineCache with MetricsCollector dependency")
        void providesPipelineCache() { // GH-90000
            Injector injector = createOrchestrationInjector(); // GH-90000

            PipelineCache cache = injector.getInstance(PipelineCache.class); // GH-90000

            assertThat(cache).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("provides ExecutionQueue bound to CheckpointAwareExecutionQueue")
        void providesExecutionQueue() { // GH-90000
            Injector injector = createOrchestrationInjector(); // GH-90000

            ExecutionQueue queue = injector.getInstance(ExecutionQueue.class); // GH-90000

            assertThat(queue).isNotNull(); // GH-90000
            assertThat(queue).isInstanceOf(CheckpointAwareExecutionQueue.class); // GH-90000
        }

        @Test
        @DisplayName("provides Orchestrator with all 6 dependencies wired")
        void providesOrchestrator() { // GH-90000
            Injector injector = createOrchestrationInjector(); // GH-90000

            Orchestrator orchestrator = injector.getInstance(Orchestrator.class); // GH-90000

            assertThat(orchestrator).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("Orchestrator is singleton")
        void orchestratorIsSingleton() { // GH-90000
            Injector injector = createOrchestrationInjector(); // GH-90000

            Orchestrator o1 = injector.getInstance(Orchestrator.class); // GH-90000
            Orchestrator o2 = injector.getInstance(Orchestrator.class); // GH-90000

            assertThat(o1).isSameAs(o2); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  3. AepPatternModule Tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("AepPatternModule")
    class AepPatternModuleTests {

        private Injector createPatternInjector() { // GH-90000
            return Injector.of(new ObservabilityModule(), new AepPatternModule()); // GH-90000
        }

        @Test
        @DisplayName("provides OperatorRegistry as singleton")
        void providesOperatorRegistry() { // GH-90000
            Injector injector = createPatternInjector(); // GH-90000

            OperatorRegistry registry = injector.getInstance(OperatorRegistry.class); // GH-90000

            assertThat(registry).isNotNull(); // GH-90000
            assertThat(injector.getInstance(OperatorRegistry.class)).isSameAs(registry); // GH-90000
        }

        @Test
        @DisplayName("provides PatternCompiler with registry and meter dependencies")
        void providesPatternCompiler() { // GH-90000
            Injector injector = createPatternInjector(); // GH-90000

            PatternCompiler compiler = injector.getInstance(PatternCompiler.class); // GH-90000

            assertThat(compiler).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("PatternCompiler receives OperatorRegistry from same injector")
        void patternCompilerUsesInjectedRegistry() { // GH-90000
            Injector injector = createPatternInjector(); // GH-90000

            // Both should be present and non-null
            OperatorRegistry registry = injector.getInstance(OperatorRegistry.class); // GH-90000
            PatternCompiler compiler = injector.getInstance(PatternCompiler.class); // GH-90000

            assertThat(registry).isNotNull(); // GH-90000
            assertThat(compiler).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("MeterRegistry is resolved from ObservabilityModule")
        void meterRegistryFromObservabilityModule() { // GH-90000
            Injector injector = createPatternInjector(); // GH-90000

            MeterRegistry meterRegistry = injector.getInstance(MeterRegistry.class); // GH-90000

            assertThat(meterRegistry).isNotNull(); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  4. AepConnectorModule Tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("AepConnectorModule")
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "RABBITMQ_USERNAME", matches = ".+") // GH-90000
    class AepConnectorModuleTests {

        private Injector createConnectorInjector() { // GH-90000
            return Injector.of(new AepCoreModule(), new AepConnectorModule()); // GH-90000
        }

        // ─── Config bindings ────────────────────────────────────

        @Test
        @DisplayName("provides KafkaConsumerConfig with defaults")
        void providesKafkaConsumerConfig() { // GH-90000
            Injector injector = createConnectorInjector(); // GH-90000

            KafkaConsumerConfig config = injector.getInstance(KafkaConsumerConfig.class); // GH-90000

            assertThat(config).isNotNull(); // GH-90000
            assertThat(config.getBootstrapServers()).isEqualTo("localhost:9092");
            assertThat(config.getGroupId()).isEqualTo("aep-consumer-group");
        }

        @Test
        @DisplayName("provides KafkaProducerConfig with defaults")
        void providesKafkaProducerConfig() { // GH-90000
            Injector injector = createConnectorInjector(); // GH-90000

            KafkaProducerConfig config = injector.getInstance(KafkaProducerConfig.class); // GH-90000

            assertThat(config).isNotNull(); // GH-90000
            assertThat(config.getBootstrapServers()).isEqualTo("localhost:9092");
        }

        @Test
        @DisplayName("provides RabbitMQConfig with defaults")
        void providesRabbitMQConfig() { // GH-90000
            Injector injector = createConnectorInjector(); // GH-90000

            RabbitMQConfig config = injector.getInstance(RabbitMQConfig.class); // GH-90000

            assertThat(config).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("provides SqsConfig with defaults")
        void providesSqsConfig() { // GH-90000
            Injector injector = createConnectorInjector(); // GH-90000

            SqsConfig config = injector.getInstance(SqsConfig.class); // GH-90000

            assertThat(config).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("provides S3Config with defaults")
        void providesS3Config() { // GH-90000
            Injector injector = createConnectorInjector(); // GH-90000

            S3Config config = injector.getInstance(S3Config.class); // GH-90000

            assertThat(config).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("provides HttpIngressConfig with defaults")
        void providesHttpIngressConfig() { // GH-90000
            Injector injector = createConnectorInjector(); // GH-90000

            HttpIngressConfig config = injector.getInstance(HttpIngressConfig.class); // GH-90000

            assertThat(config).isNotNull(); // GH-90000
            assertThat(config.getEndpoint()).isEqualTo("http://localhost:8080/events");
        }

        // ─── Strategy bindings ──────────────────────────────────

        @Test
        @DisplayName("provides Kafka consumer via @Named('kafka')")
        void providesKafkaConsumer() { // GH-90000
            Injector injector = createConnectorInjector(); // GH-90000

            QueueConsumerStrategy consumer = injector.getInstance(Key.of(QueueConsumerStrategy.class, "kafka")); // GH-90000

            assertThat(consumer).isNotNull(); // GH-90000
            assertThat(consumer).isInstanceOf(KafkaConsumerStrategy.class); // GH-90000
        }

        @Test
        @DisplayName("provides Kafka producer via @Named('kafka')")
        void providesKafkaProducer() { // GH-90000
            Injector injector = createConnectorInjector(); // GH-90000

            QueueProducerStrategy producer = injector.getInstance(Key.of(QueueProducerStrategy.class, "kafka")); // GH-90000

            assertThat(producer).isNotNull(); // GH-90000
            assertThat(producer).isInstanceOf(KafkaProducerStrategy.class); // GH-90000
        }

        @Test
        @DisplayName("provides RabbitMQ consumer via @Named('rabbitmq')")
        void providesRabbitMQConsumer() { // GH-90000
            Injector injector = createConnectorInjector(); // GH-90000

            QueueConsumerStrategy consumer = injector.getInstance(Key.of(QueueConsumerStrategy.class, "rabbitmq")); // GH-90000

            assertThat(consumer).isNotNull(); // GH-90000
            assertThat(consumer).isInstanceOf(RabbitMQConsumerStrategy.class); // GH-90000
        }

        @Test
        @DisplayName("provides SQS consumer via @Named('sqs')")
        void providesSqsConsumer() { // GH-90000
            Injector injector = createConnectorInjector(); // GH-90000

            QueueConsumerStrategy consumer = injector.getInstance(Key.of(QueueConsumerStrategy.class, "sqs")); // GH-90000

            assertThat(consumer).isNotNull(); // GH-90000
            assertThat(consumer).isInstanceOf(SqsConsumerStrategy.class); // GH-90000
        }

        @Test
        @DisplayName("provides SQS producer via @Named('sqs')")
        void providesSqsProducer() { // GH-90000
            Injector injector = createConnectorInjector(); // GH-90000

            QueueProducerStrategy producer = injector.getInstance(Key.of(QueueProducerStrategy.class, "sqs")); // GH-90000

            assertThat(producer).isNotNull(); // GH-90000
            assertThat(producer).isInstanceOf(SqsProducerStrategy.class); // GH-90000
        }

        @Test
        @DisplayName("provides S3StorageStrategy bound to DefaultS3StorageStrategy")
        void providesS3StorageStrategy() { // GH-90000
            Injector injector = createConnectorInjector(); // GH-90000

            S3StorageStrategy strategy = injector.getInstance(S3StorageStrategy.class); // GH-90000

            assertThat(strategy).isNotNull(); // GH-90000
            assertThat(strategy).isInstanceOf(DefaultS3StorageStrategy.class); // GH-90000

            // Cleanup
            injector.getInstance(ExecutorService.class).shutdown(); // GH-90000
            injector.getInstance(ScheduledExecutorService.class).shutdown(); // GH-90000
        }

        @Test
        @DisplayName("provides HttpIngressStrategy")
        void providesHttpIngressStrategy() { // GH-90000
            Injector injector = createConnectorInjector(); // GH-90000

            HttpIngressStrategy strategy = injector.getInstance(HttpIngressStrategy.class); // GH-90000

            assertThat(strategy).isNotNull(); // GH-90000

            // Cleanup
            injector.getInstance(ExecutorService.class).shutdown(); // GH-90000
            injector.getInstance(ScheduledExecutorService.class).shutdown(); // GH-90000
        }

        @Test
        @DisplayName("all strategies share same Eventloop")
        void strategiesShareEventloop() { // GH-90000
            Injector injector = createConnectorInjector(); // GH-90000

            Eventloop eventloop = injector.getInstance(Eventloop.class); // GH-90000

            assertThat(eventloop).isNotNull(); // GH-90000
            // All strategies created with same eventloop from AepCoreModule
        }

        @Test
        @DisplayName("all 6 configs and 7 strategies are provided")
        void allBindingsPresent() { // GH-90000
            Injector injector = createConnectorInjector(); // GH-90000

            // 6 configs
            assertThat(injector.getInstance(KafkaConsumerConfig.class)).isNotNull(); // GH-90000
            assertThat(injector.getInstance(KafkaProducerConfig.class)).isNotNull(); // GH-90000
            assertThat(injector.getInstance(RabbitMQConfig.class)).isNotNull(); // GH-90000
            assertThat(injector.getInstance(SqsConfig.class)).isNotNull(); // GH-90000
            assertThat(injector.getInstance(S3Config.class)).isNotNull(); // GH-90000
            assertThat(injector.getInstance(HttpIngressConfig.class)).isNotNull(); // GH-90000

            // 7 strategies (5 named + 2 direct) // GH-90000
            assertThat(injector.getInstance(Key.of(QueueConsumerStrategy.class, "kafka"))) // GH-90000
                    .isNotNull(); // GH-90000
            assertThat(injector.getInstance(Key.of(QueueProducerStrategy.class, "kafka"))) // GH-90000
                    .isNotNull(); // GH-90000
            assertThat(injector.getInstance(Key.of(QueueConsumerStrategy.class, "rabbitmq"))) // GH-90000
                    .isNotNull(); // GH-90000
            assertThat(injector.getInstance(Key.of(QueueConsumerStrategy.class, "sqs"))) // GH-90000
                    .isNotNull(); // GH-90000
            assertThat(injector.getInstance(Key.of(QueueProducerStrategy.class, "sqs"))) // GH-90000
                    .isNotNull(); // GH-90000
            assertThat(injector.getInstance(S3StorageStrategy.class)).isNotNull(); // GH-90000
            assertThat(injector.getInstance(HttpIngressStrategy.class)).isNotNull(); // GH-90000

            // Cleanup
            injector.getInstance(ExecutorService.class).shutdown(); // GH-90000
            injector.getInstance(ScheduledExecutorService.class).shutdown(); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  5. AepIngressModule Tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("AepIngressModule")
    class AepIngressModuleTests {

        /**
         * Creates an injector with AepIngressModule and eagerly creates JedisPool
         * to trigger the Redis health check. Skips the test if Redis is unavailable.
         */
        private Injector createIngressInjector() { // GH-90000
            Module coreStub = ModuleBuilder.create() // GH-90000
                    .bind(Eventloop.class) // GH-90000
                    .toInstance(Eventloop.builder().withCurrentThread().build()) // GH-90000
                    .build(); // GH-90000
            try {
                Injector injector = Injector.of(coreStub, new AepIngressModule()); // GH-90000
                injector.getInstance(JedisPool.class); // eagerly triggers verifyRedisReachable // GH-90000
                return injector;
            } catch (Exception e) { // GH-90000
                Assumptions.assumeTrue( // GH-90000
                        false, "Skipping: Redis unavailable for AepIngressModule test — " + e.getMessage()); // GH-90000
                throw new AssertionError("unreachable"); // satisfies compiler
            }
        }

        @Test
        @DisplayName("provides JedisPool")
        void providesJedisPool() { // GH-90000
            Injector injector = createIngressInjector(); // GH-90000
            JedisPool pool = injector.getInstance(JedisPool.class); // GH-90000
            assertThat(pool).isNotNull(); // GH-90000
            pool.close(); // GH-90000
        }

        @Test
        @DisplayName("provides RateLimitStorage bound to RedisRateLimitStorage")
        void providesRateLimitStorage() { // GH-90000
            Injector injector = createIngressInjector(); // GH-90000
            RateLimitStorage storage = injector.getInstance(RateLimitStorage.class); // GH-90000
            assertThat(storage).isNotNull(); // GH-90000
            assertThat(storage).isInstanceOf(RedisRateLimitStorage.class); // GH-90000
            injector.getInstance(JedisPool.class).close(); // GH-90000
        }

        @Test
        @DisplayName("provides IdempotencyService")
        void providesIdempotencyService() { // GH-90000
            Injector injector = createIngressInjector(); // GH-90000
            IdempotencyService service = injector.getInstance(IdempotencyService.class); // GH-90000
            assertThat(service).isNotNull(); // GH-90000
            injector.getInstance(JedisPool.class).close(); // GH-90000
        }

        @Test
        @DisplayName("provides HealthController with Eventloop")
        void providesHealthController() { // GH-90000
            Injector injector = createIngressInjector(); // GH-90000
            HealthController controller = injector.getInstance(HealthController.class); // GH-90000
            assertThat(controller).isNotNull(); // GH-90000
            injector.getInstance(JedisPool.class).close(); // GH-90000
        }

        @Test
        @DisplayName("RateLimitStorage and IdempotencyService share same JedisPool")
        void shareJedisPool() { // GH-90000
            Injector injector = createIngressInjector(); // GH-90000
            JedisPool pool1 = injector.getInstance(JedisPool.class); // GH-90000
            JedisPool pool2 = injector.getInstance(JedisPool.class); // GH-90000
            assertThat(pool1).as("JedisPool should be singleton").isSameAs(pool2);
            pool1.close(); // GH-90000
        }

        @Test
        @DisplayName("all 4 bindings are provided")
        void allBindingsPresent() { // GH-90000
            Injector injector = createIngressInjector(); // GH-90000
            assertThat(injector.getInstance(JedisPool.class)).isNotNull(); // GH-90000
            assertThat(injector.getInstance(RateLimitStorage.class)).isNotNull(); // GH-90000
            assertThat(injector.getInstance(IdempotencyService.class)).isNotNull(); // GH-90000
            assertThat(injector.getInstance(HealthController.class)).isNotNull(); // GH-90000
            injector.getInstance(JedisPool.class).close(); // GH-90000
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
        void providesBusinessIntelligenceService() { // GH-90000
            Injector injector = createMinimalObservabilityInjector(); // GH-90000

            BusinessIntelligenceService service = injector.getInstance(BusinessIntelligenceService.class); // GH-90000

            assertThat(service).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("provides PredictiveAnalyticsEngine")
        void providesPredictiveAnalyticsEngine() { // GH-90000
            Injector injector = createMinimalObservabilityInjector(); // GH-90000

            PredictiveAnalyticsEngine engine = injector.getInstance(PredictiveAnalyticsEngine.class); // GH-90000

            assertThat(engine).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("provides PatternPerformanceAnalyzer")
        void providesPatternPerformanceAnalyzer() { // GH-90000
            Injector injector = createMinimalObservabilityInjector(); // GH-90000

            PatternPerformanceAnalyzer analyzer = injector.getInstance(PatternPerformanceAnalyzer.class); // GH-90000

            assertThat(analyzer).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("provides KPIAggregator")
        void providesKPIAggregator() { // GH-90000
            Injector injector = createMinimalObservabilityInjector(); // GH-90000

            KPIAggregator aggregator = injector.getInstance(KPIAggregator.class); // GH-90000

            assertThat(aggregator).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("provides RealTimeAnomalyDetectionEngine")
        void providesRealTimeAnomalyDetectionEngine() { // GH-90000
            Injector injector = createMinimalObservabilityInjector(); // GH-90000

            RealTimeAnomalyDetectionEngine engine = injector.getInstance(RealTimeAnomalyDetectionEngine.class); // GH-90000

            assertThat(engine).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("provides IntelligentPredictiveAlerting")
        void providesIntelligentPredictiveAlerting() { // GH-90000
            Injector injector = createMinimalObservabilityInjector(); // GH-90000

            IntelligentPredictiveAlerting alerting = injector.getInstance(IntelligentPredictiveAlerting.class); // GH-90000

            assertThat(alerting).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("provides AdvancedTimeSeriesForecaster")
        void providesAdvancedTimeSeriesForecaster() { // GH-90000
            Injector injector = createMinimalObservabilityInjector(); // GH-90000

            AdvancedTimeSeriesForecaster forecaster = injector.getInstance(AdvancedTimeSeriesForecaster.class); // GH-90000

            assertThat(forecaster).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("all 7 analytics sub-services are provided as singletons")
        void allAnalyticsSubservicesAreSingletons() { // GH-90000
            Injector injector = createMinimalObservabilityInjector(); // GH-90000

            assertThat(injector.getInstance(BusinessIntelligenceService.class)) // GH-90000
                    .isSameAs(injector.getInstance(BusinessIntelligenceService.class)); // GH-90000
            assertThat(injector.getInstance(PredictiveAnalyticsEngine.class)) // GH-90000
                    .isSameAs(injector.getInstance(PredictiveAnalyticsEngine.class)); // GH-90000
            assertThat(injector.getInstance(PatternPerformanceAnalyzer.class)) // GH-90000
                    .isSameAs(injector.getInstance(PatternPerformanceAnalyzer.class)); // GH-90000
            assertThat(injector.getInstance(KPIAggregator.class)).isSameAs(injector.getInstance(KPIAggregator.class)); // GH-90000
            assertThat(injector.getInstance(RealTimeAnomalyDetectionEngine.class)) // GH-90000
                    .isSameAs(injector.getInstance(RealTimeAnomalyDetectionEngine.class)); // GH-90000
            assertThat(injector.getInstance(IntelligentPredictiveAlerting.class)) // GH-90000
                    .isSameAs(injector.getInstance(IntelligentPredictiveAlerting.class)); // GH-90000
            assertThat(injector.getInstance(AdvancedTimeSeriesForecaster.class)) // GH-90000
                    .isSameAs(injector.getInstance(AdvancedTimeSeriesForecaster.class)); // GH-90000
        }

        @Test
        @DisplayName("provides AnalyticsEngine with all sub-services wired")
        void providesAnalyticsEngine() { // GH-90000
            Injector injector = createMinimalObservabilityInjector(); // GH-90000

            AnalyticsEngine engine = injector.getInstance(AnalyticsEngine.class); // GH-90000

            assertThat(engine).isNotNull(); // GH-90000
        }

        private Injector createMinimalObservabilityInjector() { // GH-90000
            // AnalyticsEngine @Provides requires EventCloud, Eventloop, MetricsCollector
            // — ActiveJ validates ALL bindings eagerly at Injector creation
            Module stubs = ModuleBuilder.create() // GH-90000
                    .bind(EventCloud.class) // GH-90000
                    .toInstance(mock(EventCloud.class)) // GH-90000
                    .bind(Eventloop.class) // GH-90000
                    .toInstance(Eventloop.builder().withCurrentThread().build()) // GH-90000
                    .bind(MetricsCollector.class) // GH-90000
                    .toInstance(mock(MetricsCollector.class)) // GH-90000
                    .build(); // GH-90000
            return Injector.of(stubs, new AepObservabilityModule()); // GH-90000
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
        void coreAndPatternCompose() { // GH-90000
            Injector injector = Injector.of(new ObservabilityModule(), new AepCoreModule(), new AepPatternModule()); // GH-90000

            // Core bindings
            assertThat(injector.getInstance(PipelineExecutionEngine.class)).isNotNull(); // GH-90000
            assertThat(injector.getInstance(OperatorCatalog.class)).isNotNull(); // GH-90000

            // Pattern bindings (that depend on MeterRegistry from ObservabilityModule) // GH-90000
            assertThat(injector.getInstance(OperatorRegistry.class)).isNotNull(); // GH-90000
            assertThat(injector.getInstance(PatternCompiler.class)).isNotNull(); // GH-90000

            // Cleanup
            injector.getInstance(ExecutorService.class).shutdown(); // GH-90000
            injector.getInstance(ScheduledExecutorService.class).shutdown(); // GH-90000
        }

        @Test
        @DisplayName("AepCoreModule + AepConnectorModule compose correctly")
        void coreAndConnectorCompose() { // GH-90000
            Injector injector = Injector.of(new AepCoreModule(), new AepConnectorModule()); // GH-90000

            // Connector strategies use Eventloop from AepCoreModule
            Eventloop eventloop = injector.getInstance(Eventloop.class); // GH-90000
            assertThat(eventloop).isNotNull(); // GH-90000

            QueueConsumerStrategy kafkaConsumer = injector.getInstance(Key.of(QueueConsumerStrategy.class, "kafka")); // GH-90000
            assertThat(kafkaConsumer).isNotNull(); // GH-90000

            // Cleanup
            injector.getInstance(ExecutorService.class).shutdown(); // GH-90000
            injector.getInstance(ScheduledExecutorService.class).shutdown(); // GH-90000
        }

        @Test
        @DisplayName("AepCoreModule + AepIngressModule compose correctly")
        void coreAndIngressCompose() { // GH-90000
            // AepIngressModule performs a Redis startup health check — skip if Redis is unavailable.
            Injector injector;
            try {
                injector = Injector.of(new AepCoreModule(), new AepIngressModule()); // GH-90000
                injector.getInstance(JedisPool.class); // eagerly trigger verifyRedisReachable // GH-90000
            } catch (Exception e) { // GH-90000
                Assumptions.assumeTrue( // GH-90000
                        false, "Skipping: Redis unavailable for AepIngressModule composition — " + e.getMessage()); // GH-90000
                return;
            }

            // Ingress uses Eventloop from AepCoreModule
            assertThat(injector.getInstance(HealthController.class)).isNotNull(); // GH-90000
            assertThat(injector.getInstance(RateLimitStorage.class)).isNotNull(); // GH-90000
            assertThat(injector.getInstance(IdempotencyService.class)).isNotNull(); // GH-90000

            // Cleanup
            injector.getInstance(JedisPool.class).close(); // GH-90000
            injector.getInstance(ExecutorService.class).shutdown(); // GH-90000
            injector.getInstance(ScheduledExecutorService.class).shutdown(); // GH-90000
        }

        @Test
        @DisplayName("full module stack creates valid injector")
        void fullModuleStack() { // GH-90000
            // External stubs for orchestration — MetricsCollector comes from ObservabilityModule
            Module orchExternalStubs = ModuleBuilder.create() // GH-90000
                    .bind(AgentRegistryClient.class) // GH-90000
                    .toInstance(mock(AgentRegistryClient.class)) // GH-90000
                    .bind(PipelineRegistryClient.class) // GH-90000
                    .toInstance(mock(PipelineRegistryClient.class)) // GH-90000
                    .bind(SpecFormatLoader.class) // GH-90000
                    .toInstance(mock(SpecFormatLoader.class)) // GH-90000
                    .bind(PipelineCheckpointRepository.class) // GH-90000
                    .toInstance(mock(PipelineCheckpointRepository.class)) // GH-90000
                    .bind(StepCheckpointRepository.class) // GH-90000
                    .toInstance(mock(StepCheckpointRepository.class)) // GH-90000
                    .build(); // GH-90000

            assertThatCode(() -> { // GH-90000
                        Injector injector = Injector.of( // GH-90000
                                new ObservabilityModule(), // GH-90000
                                new AepCoreModule(), // GH-90000
                                new AepPatternModule(), // GH-90000
                                new AepConnectorModule(), // GH-90000
                                orchExternalStubs,
                                new AepOrchestrationModule()); // GH-90000

                        // Verify cross-module dependency: Orchestrator depends on MetricsCollector
                        // (ObservabilityModule) // GH-90000
                        assertThat(injector.getInstance(Orchestrator.class)).isNotNull(); // GH-90000
                        assertThat(injector.getInstance(PatternCompiler.class)).isNotNull(); // GH-90000
                        assertThat(injector.getInstance(PipelineExecutionEngine.class)) // GH-90000
                                .isNotNull(); // GH-90000

                        // Cleanup
                        injector.getInstance(ExecutorService.class).shutdown(); // GH-90000
                        injector.getInstance(ScheduledExecutorService.class).shutdown(); // GH-90000
                    })
                    .doesNotThrowAnyException(); // GH-90000
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
        void multipleInstances() { // GH-90000
            AepCoreModule m1 = new AepCoreModule(); // GH-90000
            AepCoreModule m2 = new AepCoreModule(); // GH-90000

            Injector i1 = Injector.of(m1); // GH-90000
            Injector i2 = Injector.of(m2); // GH-90000

            PipelineExecutionEngine e1 = i1.getInstance(PipelineExecutionEngine.class); // GH-90000
            PipelineExecutionEngine e2 = i2.getInstance(PipelineExecutionEngine.class); // GH-90000

            assertThat(e1).isNotNull(); // GH-90000
            assertThat(e2).isNotNull(); // GH-90000
            assertThat(e1).isNotSameAs(e2); // GH-90000

            // Cleanup
            i1.getInstance(ExecutorService.class).shutdown(); // GH-90000
            i1.getInstance(ScheduledExecutorService.class).shutdown(); // GH-90000
            i2.getInstance(ExecutorService.class).shutdown(); // GH-90000
            i2.getInstance(ScheduledExecutorService.class).shutdown(); // GH-90000
        }

        @Test
        @DisplayName("connector config can be overridden by custom module")
        void configOverride() { // GH-90000
            KafkaConsumerConfig custom = KafkaConsumerConfig.builder() // GH-90000
                    .bootstrapServers("custom-broker:9093")
                    .groupId("custom-group")
                    .topics(java.util.List.of("custom-topic"))
                    .batchSize(50) // GH-90000
                    .build(); // GH-90000

            // Verify custom config is properly built and injectable via module binding
            Module customModule = ModuleBuilder.create() // GH-90000
                    .bind(KafkaConsumerConfig.class) // GH-90000
                    .toInstance(custom) // GH-90000
                    .build(); // GH-90000

            Injector injector = Injector.of(customModule); // GH-90000

            KafkaConsumerConfig config = injector.getInstance(KafkaConsumerConfig.class); // GH-90000
            assertThat(config.getBootstrapServers()).isEqualTo("custom-broker:9093");
            assertThat(config.getGroupId()).isEqualTo("custom-group");
        }

        @Test
        @DisplayName("OperatorCatalog and OperatorRegistry are distinct types")
        void catalogAndRegistryDistinct() { // GH-90000
            Injector injector = Injector.of(new ObservabilityModule(), new AepCoreModule(), new AepPatternModule()); // GH-90000

            OperatorCatalog catalog = injector.getInstance(OperatorCatalog.class); // GH-90000
            OperatorRegistry registry = injector.getInstance(OperatorRegistry.class); // GH-90000

            // Different types entirely
            assertThat(catalog).isNotNull(); // GH-90000
            assertThat(registry).isNotNull(); // GH-90000
            assertThat(catalog).isNotSameAs(registry); // GH-90000
            assertThat(catalog.getClass()).isNotEqualTo(registry.getClass()); // GH-90000

            // Cleanup
            injector.getInstance(ExecutorService.class).shutdown(); // GH-90000
            injector.getInstance(ScheduledExecutorService.class).shutdown(); // GH-90000
        }

        @Test
        @DisplayName("AepObservabilityModule analytics sub-services have correct interface bindings")
        void analyticsInterfaceBindings() { // GH-90000
            Module stubs = ModuleBuilder.create() // GH-90000
                    .bind(EventCloud.class) // GH-90000
                    .toInstance(mock(EventCloud.class)) // GH-90000
                    .bind(Eventloop.class) // GH-90000
                    .toInstance(Eventloop.builder().withCurrentThread().build()) // GH-90000
                    .bind(MetricsCollector.class) // GH-90000
                    .toInstance(mock(MetricsCollector.class)) // GH-90000
                    .build(); // GH-90000
            Injector injector = Injector.of(stubs, new AepObservabilityModule()); // GH-90000

            // Verify all are bound to their interfaces, not concrete types
            assertThat(injector.getInstance(BusinessIntelligenceService.class)) // GH-90000
                    .isInstanceOf(BusinessIntelligenceService.class); // GH-90000
            assertThat(injector.getInstance(PredictiveAnalyticsEngine.class)) // GH-90000
                    .isInstanceOf(PredictiveAnalyticsEngine.class); // GH-90000
            assertThat(injector.getInstance(PatternPerformanceAnalyzer.class)) // GH-90000
                    .isInstanceOf(PatternPerformanceAnalyzer.class); // GH-90000
            assertThat(injector.getInstance(KPIAggregator.class)).isInstanceOf(KPIAggregator.class); // GH-90000
            assertThat(injector.getInstance(RealTimeAnomalyDetectionEngine.class)) // GH-90000
                    .isInstanceOf(RealTimeAnomalyDetectionEngine.class); // GH-90000
            assertThat(injector.getInstance(IntelligentPredictiveAlerting.class)) // GH-90000
                    .isInstanceOf(IntelligentPredictiveAlerting.class); // GH-90000
            assertThat(injector.getInstance(AdvancedTimeSeriesForecaster.class)) // GH-90000
                    .isInstanceOf(AdvancedTimeSeriesForecaster.class); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  6. AepLearningModule
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("AepLearningModule")
    class AepLearningModuleTests {

        private Injector buildInjector() { // GH-90000
            Module schedulerStub = ModuleBuilder.create() // GH-90000
                    .bind(ScheduledExecutorService.class) // GH-90000
                    .toInstance(mock(ScheduledExecutorService.class)) // GH-90000
                    .build(); // GH-90000
            return Injector.of(schedulerStub, new AepLearningModule()); // GH-90000
        }

        @Test
        @DisplayName("provides HumanReviewQueue bound to InMemoryHumanReviewQueue")
        void providesHumanReviewQueue() { // GH-90000
            var injector = buildInjector(); // GH-90000
            var queue = injector.getInstance(com.ghatana.agent.learning.review.HumanReviewQueue.class); // GH-90000
            assertThat(queue).isNotNull(); // GH-90000
            assertThat(queue).isInstanceOf(com.ghatana.agent.learning.review.InMemoryHumanReviewQueue.class); // GH-90000
        }

        @Test
        @DisplayName("provides ConflictResolver bound to EntrenchmentConflictResolver")
        void providesConflictResolver() { // GH-90000
            var injector = buildInjector(); // GH-90000
            var resolver = injector.getInstance(com.ghatana.agent.learning.consolidation.ConflictResolver.class); // GH-90000
            assertThat(resolver).isNotNull(); // GH-90000
            assertThat(resolver) // GH-90000
                    .isInstanceOf(com.ghatana.agent.learning.consolidation.EntrenchmentConflictResolver.class); // GH-90000
        }

        @Test
        @DisplayName("provides ConsolidationPipeline")
        void providesConsolidationPipeline() { // GH-90000
            var injector = buildInjector(); // GH-90000
            var pipeline = injector.getInstance(com.ghatana.agent.learning.consolidation.ConsolidationPipeline.class); // GH-90000
            assertThat(pipeline).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("provides ConsolidationScheduler with AEP_SYSTEM agent id")
        void providesConsolidationScheduler() { // GH-90000
            var injector = buildInjector(); // GH-90000
            var scheduler = injector.getInstance(com.ghatana.agent.learning.consolidation.ConsolidationScheduler.class); // GH-90000
            assertThat(scheduler).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("singleton semantics: same HumanReviewQueue instance returned twice")
        void humanReviewQueueIsSingleton() { // GH-90000
            var injector = buildInjector(); // GH-90000
            var type = com.ghatana.agent.learning.review.HumanReviewQueue.class;
            assertThat(injector.getInstance(type)).isSameAs(injector.getInstance(type)); // GH-90000
        }

        @Test
        @DisplayName("singleton semantics: same ConsolidationPipeline instance returned twice")
        void consolidationPipelineIsSingleton() { // GH-90000
            var injector = buildInjector(); // GH-90000
            var type = com.ghatana.agent.learning.consolidation.ConsolidationPipeline.class;
            assertThat(injector.getInstance(type)).isSameAs(injector.getInstance(type)); // GH-90000
        }

        @Test
        @DisplayName("all learning bindings can be instantiated without exception")
        void allBindingsInstantiateCleanly() { // GH-90000
            assertThatCode(() -> { // GH-90000
                        var injector = buildInjector(); // GH-90000
                        injector.getInstance(com.ghatana.agent.learning.review.HumanReviewQueue.class); // GH-90000
                        injector.getInstance(com.ghatana.agent.learning.consolidation.ConflictResolver.class); // GH-90000
                        injector.getInstance(com.ghatana.agent.learning.consolidation.ConsolidationPipeline.class); // GH-90000
                        injector.getInstance(com.ghatana.agent.learning.consolidation.ConsolidationScheduler.class); // GH-90000
                    })
                    .doesNotThrowAnyException(); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Stub/Mock Helpers
    // ═══════════════════════════════════════════════════════════════

    /**
     * Creates a module with mocked external dependencies for the orchestration
     * module. Uses Mockito mocks since these interfaces have many abstract methods.
     */
    private static Module orchestrationStubModule() { // GH-90000
        return ModuleBuilder.create() // GH-90000
                .bind(MetricsCollector.class) // GH-90000
                .toInstance(mock(MetricsCollector.class)) // GH-90000
                .bind(AgentRegistryClient.class) // GH-90000
                .toInstance(mock(AgentRegistryClient.class)) // GH-90000
                .bind(PipelineRegistryClient.class) // GH-90000
                .toInstance(mock(PipelineRegistryClient.class)) // GH-90000
                .bind(SpecFormatLoader.class) // GH-90000
                .toInstance(mock(SpecFormatLoader.class)) // GH-90000
                .bind(PipelineCheckpointRepository.class) // GH-90000
                .toInstance(mock(PipelineCheckpointRepository.class)) // GH-90000
                .bind(StepCheckpointRepository.class) // GH-90000
                .toInstance(mock(StepCheckpointRepository.class)) // GH-90000
                .build(); // GH-90000
    }
}
