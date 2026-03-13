/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.di;

import com.ghatana.agent.catalog.CatalogRegistry;
import com.ghatana.agent.dispatch.AgentDispatcher;
import com.ghatana.agent.dispatch.CatalogAgentDispatcher;
import com.ghatana.agent.dispatch.tier.DefaultLlmExecutionPlan;
import com.ghatana.agent.dispatch.tier.DefaultServiceOrchestrationPlan;
import com.ghatana.agent.dispatch.tier.LlmExecutionPlan;
import com.ghatana.agent.dispatch.tier.LlmProvider;
import com.ghatana.agent.dispatch.tier.ServiceOrchestrationPlan;
import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionService;
import com.ghatana.ai.llm.LLMConfiguration;
import com.ghatana.ai.llm.OllamaCompletionService;
import com.ghatana.ai.llm.ToolAwareAnthropicCompletionService;
import com.ghatana.ai.llm.ToolAwareOpenAICompletionService;
import com.ghatana.orchestrator.cache.PipelineCache;
import com.ghatana.orchestrator.config.OrchestratorConfig;
import com.ghatana.orchestrator.core.Orchestrator;
import com.ghatana.orchestrator.client.AgentRegistryClient;
import com.ghatana.orchestrator.client.PipelineRegistryClient;
import com.ghatana.orchestrator.loader.SpecFormatLoader;
import com.ghatana.orchestrator.queue.ExecutionQueue;
import com.ghatana.orchestrator.queue.impl.CheckpointAwareExecutionQueue;
import com.ghatana.orchestrator.store.CheckpointStore;
import com.ghatana.orchestrator.store.PipelineCheckpointRepository;
import com.ghatana.orchestrator.store.PostgresqlCheckpointStore;
import com.ghatana.orchestrator.store.StepCheckpointRepository;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;

import java.util.LinkedHashMap;
import java.util.Map;
import java.time.Duration;

/**
 * ActiveJ DI module for AEP orchestration components.
 *
 * <p>Provides the orchestration layer — pipeline coordination, checkpointing,
 * execution queues, and agent step running:
 * <ul>
 *   <li>{@link Orchestrator} — central pipeline lifecycle manager</li>
 *   <li>{@link CheckpointStore} — execution state persistence</li>
 *   <li>{@link ExecutionQueue} — checkpoint-aware execution queue</li>
 *   <li>{@link OrchestratorConfig} — orchestrator configuration</li>
 *   <li>{@link PipelineCache} — in-memory pipeline cache with TTL</li>
 * </ul>
 *
 * <p><b>Dependencies:</b> Requires bindings from {@link AepCoreModule}
 * (for {@code Eventloop}), and from the observability layer
 * (for {@link MetricsCollector}).
 *
 * <p>Also provides the agent dispatch stack:
 * <ul>
 *   <li>{@link CatalogRegistry} — ServiceLoader-discovered agent catalog registry</li>
 *   <li>{@link LlmExecutionPlan} — Tier-L LLM execution (stub until AEP-P7)</li>
 *   <li>{@link ServiceOrchestrationPlan} — Tier-S delegation plan</li>
 *   <li>{@link CatalogAgentDispatcher} / {@link AgentDispatcher} — three-tier
 *       catalog-backed dispatcher</li>
 * </ul>
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * Injector injector = Injector.of(
 *     new AepCoreModule(),
 *     new AepObservabilityModule(),
 *     new AepOrchestrationModule()
 * );
 * Orchestrator orchestrator = injector.getInstance(Orchestrator.class);
 * AgentDispatcher dispatcher = injector.getInstance(AgentDispatcher.class);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose ActiveJ DI module for orchestration, checkpointing, execution, and agent dispatch
 * @doc.layer product
 * @doc.pattern Module
 * @see Orchestrator
 * @see CheckpointStore
 * @see ExecutionQueue
 * @see CatalogAgentDispatcher
 * @see AgentDispatcher
 */
public class AepOrchestrationModule extends AbstractModule {

    private static final Duration DEFAULT_CACHE_TTL = Duration.ofMinutes(10);

    /**
     * Provides the orchestrator configuration with production defaults.
     *
     * @return default orchestrator config
     */
    @Provides
    OrchestratorConfig orchestratorConfig() {
        return new OrchestratorConfig();
    }

    /**
     * Provides the pipeline cache with a configurable TTL.
     *
     * <p>The cache stores recently-loaded pipeline definitions to avoid
     * repeated repository lookups. TTL defaults to 10 minutes.
     *
     * @param metrics metrics collector for cache hit/miss tracking
     * @return pipeline cache instance
     */
    @Provides
    PipelineCache pipelineCache(MetricsCollector metrics) {
        return new PipelineCache(DEFAULT_CACHE_TTL, metrics);
    }

    /**
     * Provides the checkpoint store bound to its interface.
     *
     * <p>Uses {@link PostgresqlCheckpointStore} for durable execution state.
     * Requires JPA repository bindings for
     * {@link PipelineCheckpointRepository} and {@link StepCheckpointRepository},
     * which should be provided by a persistence module.
     *
     * @param pipelineRepository pipeline checkpoint JPA repository
     * @param stepRepository     step checkpoint JPA repository
     * @return checkpoint store
     */
    @Provides
    CheckpointStore checkpointStore(PipelineCheckpointRepository pipelineRepository,
                                    StepCheckpointRepository stepRepository) {
        return new PostgresqlCheckpointStore(pipelineRepository, stepRepository);
    }

    /**
     * Provides the execution queue bound to its interface.
     *
     * <p>Uses {@link CheckpointAwareExecutionQueue} which deduplicates
     * executions via the checkpoint store (idempotency guarantee).
     *
     * @param checkpointStore the checkpoint store for duplicate detection
     * @return execution queue
     */
    @Provides
    ExecutionQueue executionQueue(CheckpointStore checkpointStore) {
        return new CheckpointAwareExecutionQueue(checkpointStore);
    }

    /**
     * Provides the orchestrator — the central coordination component.
     *
     * <p>Wires together the pipeline cache, registry clients, config,
     * metrics, and spec loader. The orchestrator manages pipeline loading,
     * caching, refresh cycles, and execution dispatching.
     *
     * @param pipelineCache        cached pipeline store
     * @param agentRegistryClient  client for agent registry
     * @param pipelineRegistryClient client for pipeline registry
     * @param config               orchestrator configuration
     * @param metrics              metrics collector
     * @param specFormatLoader     pipeline spec format loader
     * @return orchestrator instance
     */
    @Provides
    Orchestrator orchestrator(
            PipelineCache pipelineCache,
            AgentRegistryClient agentRegistryClient,
            PipelineRegistryClient pipelineRegistryClient,
            OrchestratorConfig config,
            MetricsCollector metrics,
            SpecFormatLoader specFormatLoader) {
        return new Orchestrator(
                pipelineCache,
                agentRegistryClient,
                pipelineRegistryClient,
                config,
                metrics,
                specFormatLoader);
    }

    // ==================== Agent Dispatch Stack (AEP-P2) ====================

    /**
     * Provides the catalog registry populated via ServiceLoader discovery.
     *
     * <p>Discovers all {@link com.ghatana.agent.catalog.AgentCatalog} implementations
     * on the classpath and merges them into a single queryable registry.
     *
     * @return merged catalog registry
     */
    @Provides
    CatalogRegistry catalogRegistry() {
        return CatalogRegistry.discover();
    }

    /**
     * Provides an env-driven {@link LlmProvider} backed by real completion services.
     *
     * <p>Reads the following environment variables to discover available providers:
     * <ul>
     *   <li>{@code AEP_ANTHROPIC_API_KEY} — enables the Anthropic provider</li>
     *   <li>{@code AEP_OPENAI_API_KEY} — enables the OpenAI provider</li>
     *   <li>{@code AEP_OLLAMA_HOST} — enables the Ollama provider (e.g. {@code http://localhost:11434})</li>
     * </ul>
     *
     * <p>Throws {@link IllegalStateException} at startup if no provider is configured.
     * The {@code provider} argument in {@link LlmProvider#invoke} is matched
     * case-insensitively against registered provider names.
     *
     * @param metrics metrics collector for LLM call instrumentation
     * @return multi-provider LLM provider
     * @throws IllegalStateException if no LLM provider env var is set
     */
    @Provides
    LlmProvider llmProvider(MetricsCollector metrics) {
        Map<String, CompletionService> services = new LinkedHashMap<>();

        String anthropicKey = System.getenv("AEP_ANTHROPIC_API_KEY");
        if (anthropicKey != null && !anthropicKey.isBlank()) {
            String model = System.getenv().getOrDefault("AEP_ANTHROPIC_MODEL", "claude-3-5-sonnet-20241022");
            LLMConfiguration cfg = LLMConfiguration.builder()
                    .apiKey(anthropicKey)
                    .modelName(model)
                    .build();
            services.put("anthropic", new ToolAwareAnthropicCompletionService(cfg, null, metrics));
        }

        String openaiKey = System.getenv("AEP_OPENAI_API_KEY");
        if (openaiKey != null && !openaiKey.isBlank()) {
            String model = System.getenv().getOrDefault("AEP_OPENAI_MODEL", "gpt-4o-mini");
            LLMConfiguration cfg = LLMConfiguration.builder()
                    .apiKey(openaiKey)
                    .modelName(model)
                    .build();
            services.put("openai", new ToolAwareOpenAICompletionService(cfg, null, metrics));
        }

        String ollamaHost = System.getenv("AEP_OLLAMA_HOST");
        if (ollamaHost != null && !ollamaHost.isBlank()) {
            String model = System.getenv().getOrDefault("AEP_OLLAMA_MODEL", "llama3");
            LLMConfiguration cfg = LLMConfiguration.builder()
                    .apiKey("ollama")
                    .baseUrl(ollamaHost)
                    .modelName(model)
                    .build();
            services.put("ollama", new OllamaCompletionService(cfg, null, metrics));
        }

        if (services.isEmpty()) {
            throw new IllegalStateException(
                    "No LLM provider configured for AEP. Set at least one of: "
                    + "AEP_ANTHROPIC_API_KEY, AEP_OPENAI_API_KEY, AEP_OLLAMA_HOST");
        }

        // Default provider is the first one registered.
        String defaultProvider = services.keySet().iterator().next();

        return (provider, model, prompt, temperature, maxTokens) -> {
            String key = provider != null ? provider.toLowerCase() : defaultProvider;
            CompletionService service = services.getOrDefault(key, services.get(defaultProvider));
            CompletionRequest req = CompletionRequest.builder()
                    .prompt(prompt)
                    .model(model)
                    .temperature(temperature)
                    .maxTokens(maxTokens)
                    .build();
            return service.complete(req).map(result -> (Object) result.getText());
        };
    }

    /**
     * Provides the Tier-L LLM execution plan using the configured {@link LlmProvider}.
     *
     * @param llmProvider LLM provider SPI implementation
     * @return default LLM execution plan
     */
    @Provides
    LlmExecutionPlan llmExecutionPlan(LlmProvider llmProvider) {
        return new DefaultLlmExecutionPlan(llmProvider);
    }

    /**
     * Provides the Tier-S service orchestration plan.
     *
     * <p>{@link DefaultServiceOrchestrationPlan} reads the delegation chain from
     * catalog entries and dispatches recursively via the parent dispatcher.
     *
     * @return default service orchestration plan
     */
    @Provides
    ServiceOrchestrationPlan serviceOrchestrationPlan() {
        return new DefaultServiceOrchestrationPlan();
    }

    /**
     * Provides the {@link CatalogAgentDispatcher} — the primary agent dispatch component.
     *
     * <p>Implements a three-tier resolution strategy:
     * <ol>
     *   <li>Tier-J: registered Java {@link com.ghatana.agent.TypedAgent} beans</li>
     *   <li>Tier-S: catalog entry with delegation chain → {@link ServiceOrchestrationPlan}</li>
     *   <li>Tier-L: catalog entry with LLM generator → {@link LlmExecutionPlan}</li>
     * </ol>
     *
     * @param catalogRegistry     aggregated agent catalog
     * @param llmExecutionPlan    Tier-L execution plan
     * @param serviceOrchestrationPlan Tier-S execution plan
     * @return catalog-backed agent dispatcher
     */
    @Provides
    CatalogAgentDispatcher catalogAgentDispatcher(
            CatalogRegistry catalogRegistry,
            LlmExecutionPlan llmExecutionPlan,
            ServiceOrchestrationPlan serviceOrchestrationPlan) {
        return new CatalogAgentDispatcher(catalogRegistry, llmExecutionPlan, serviceOrchestrationPlan);
    }

    /**
     * Provides {@link AgentDispatcher} bound to {@link CatalogAgentDispatcher}.
     *
     * @param dispatcher the concrete dispatcher implementation
     * @return agent dispatcher interface binding
     */
    @Provides
    AgentDispatcher agentDispatcher(CatalogAgentDispatcher dispatcher) {
        return dispatcher;
    }
}
