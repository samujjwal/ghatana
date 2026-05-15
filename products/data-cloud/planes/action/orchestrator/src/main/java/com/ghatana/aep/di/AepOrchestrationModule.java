/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.di;

import com.ghatana.agent.catalog.CatalogRegistry;
import com.ghatana.agent.dispatch.AgentDispatcher;
import com.ghatana.agent.dispatch.CatalogAgentDispatcher;
import com.ghatana.agent.runtime.safety.GovernedAgentDispatcher;
import com.ghatana.agent.runtime.mode.MasteryAwareModeSelector;
import com.ghatana.agent.runtime.mode.ModeSelectionPolicy;
import com.ghatana.agent.runtime.mode.TaskClassifier;
import com.ghatana.agent.mastery.MasteryRegistry;
import com.ghatana.agent.mastery.MasteryQuery;
import com.ghatana.agent.mastery.MasteryDecision;
import com.ghatana.agent.mastery.VersionScope;
import com.ghatana.agent.runtime.mode.TaskClassification;
import com.ghatana.agent.runtime.mode.TaskRiskLevel;
import com.ghatana.agent.runtime.mode.TaskNovelty;
import com.ghatana.agent.runtime.mode.ExecutionMode;
import com.ghatana.agent.mastery.MasteryState;
import com.ghatana.agent.mastery.ApplicabilityScope;
import com.ghatana.agent.mastery.MasteryScore;
import com.ghatana.agent.context.version.VersionContext;
import com.ghatana.agent.memory.MemoryRetriever;
import com.ghatana.agent.promotion.DefaultPromotionEngine;
import com.ghatana.agent.promotion.PromotionEngine;
import com.ghatana.agent.learning.DefaultLearningDeltaEvaluator;
import com.ghatana.agent.learning.LearningDeltaEvaluator;
import com.ghatana.agent.learning.LearningDeltaRepository;
import com.ghatana.agent.obsolescence.ObsolescenceDetector;
import com.ghatana.agent.obsolescence.DefaultObsolescenceDetector;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import com.ghatana.agent.dispatch.tier.DefaultLlmExecutionPlan;
import com.ghatana.agent.dispatch.tier.DefaultServiceOrchestrationPlan;
import com.ghatana.agent.dispatch.tier.LlmExecutionPlan;
import com.ghatana.agent.dispatch.tier.LlmProvider;
import com.ghatana.agent.dispatch.tier.ServiceOrchestrationPlan;
import com.ghatana.agent.runtime.safety.InvariantMonitor;
import com.ghatana.agent.runtime.safety.DefaultInvariantMonitor;
import com.ghatana.agent.audit.AgentTraceLedger;
import com.ghatana.agent.audit.DataCloudAgentTraceLedger;
import com.ghatana.agent.runtime.mode.DefaultModeSelectionPolicy;
import com.ghatana.datacloud.agent.learning.delta.DataCloudLearningDeltaRepository;
import com.ghatana.datacloud.entity.EntityRepository;
import com.ghatana.datacloud.spi.EventLogStoreAdapters;
import com.ghatana.datacloud.spi.provider.InMemoryEventLogStoreProvider;
import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionService;
import com.ghatana.ai.llm.LLMConfiguration;
import com.ghatana.ai.llm.OllamaCompletionService;
import com.ghatana.ai.llm.ToolAwareAnthropicCompletionService;
import com.ghatana.ai.llm.ToolAwareOpenAICompletionService;
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
import com.ghatana.orchestrator.store.PostgresqlCheckpointStore;
import com.ghatana.orchestrator.store.StepCheckpointRepository;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.domain.eventstore.EventLogStore;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ServiceLoader;

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
    CheckpointStore checkpointStore(
            PipelineCheckpointRepository pipelineRepository, StepCheckpointRepository stepRepository) {
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
                pipelineCache, agentRegistryClient, pipelineRegistryClient, config, metrics, specFormatLoader);
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
        return createLlmProvider(System.getenv(), metrics);
    }

    static LlmProvider createLlmProvider(Map<String, String> env, MetricsCollector metrics) {
        Map<String, CompletionService> services = new LinkedHashMap<>();

        String anthropicKey = normalizedValue(env, "AEP_ANTHROPIC_API_KEY");
        if (anthropicKey != null) {
            String model = readModel(env, "AEP_ANTHROPIC_MODEL", "claude-3-5-sonnet-20241022");
            LLMConfiguration cfg = LLMConfiguration.builder()
                    .apiKey(anthropicKey)
                    .modelName(model)
                    .build();
            services.put("anthropic", new ToolAwareAnthropicCompletionService(cfg, null, metrics));
        }

        String openaiKey = normalizedValue(env, "AEP_OPENAI_API_KEY");
        if (openaiKey != null) {
            String model = readModel(env, "AEP_OPENAI_MODEL", "gpt-4o-mini");
            LLMConfiguration cfg = LLMConfiguration.builder()
                    .apiKey(openaiKey)
                    .modelName(model)
                    .build();
            services.put("openai", new ToolAwareOpenAICompletionService(cfg, null, metrics));
        }

        String ollamaHost = normalizedValue(env, "AEP_OLLAMA_HOST");
        if (ollamaHost != null) {
            String model = readModel(env, "AEP_OLLAMA_MODEL", "llama3");
            LLMConfiguration cfg = LLMConfiguration.builder()
                    .apiKey("ollama")
                    .baseUrl(validateAbsoluteHttpUrl("AEP_OLLAMA_HOST", ollamaHost))
                    .modelName(model)
                    .build();
            services.put("ollama", new OllamaCompletionService(cfg, null, metrics));
        }

        if (services.isEmpty()) {
            throw new IllegalStateException("No LLM provider configured for AEP. Set at least one of: "
                    + "AEP_ANTHROPIC_API_KEY, AEP_OPENAI_API_KEY, AEP_OLLAMA_HOST");
        }

        // Default provider is the first one registered.
        String defaultProvider = services.keySet().iterator().next();

        return (provider, model, prompt, temperature, maxTokens, context) -> {
            String key = provider != null ? provider.toLowerCase() : defaultProvider;
            CompletionService service = services.getOrDefault(key, services.get(defaultProvider));
            CompletionRequest req = CompletionRequest.builder()
                    .prompt(prompt)
                    .model(model)
                    .temperature(temperature)
                    .maxTokens(maxTokens)
                    .metadata(Map.of(
                    "tenantId", context.getTenantId(),
                    "traceId", context.getTraceId() != null ? context.getTraceId() : context.getTurnId(),
                    "agentId", context.getAgentId()))
                    .build();
            return service.complete(req);
        };
    }

    private static String readModel(Map<String, String> env, String key, String defaultValue) {
        String configured = env.get(key);
        if (configured == null) {
            return defaultValue;
        }

        String normalized = configured.trim();
        if (normalized.isEmpty()) {
            throw new IllegalStateException(key + " must not be blank when its provider is configured");
        }
        return normalized;
    }

    private static String normalizedValue(Map<String, String> env, String key) {
        String value = env.get(key);
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static String validateAbsoluteHttpUrl(String key, String value) {
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                throw new IllegalStateException(key + " must be an absolute http(s) URL");
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new IllegalStateException(key + " must include a host");
            }
            return uri.toString();
        } catch (URISyntaxException ex) {
            throw new IllegalStateException(key + " must be a valid absolute URL", ex);
        }
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
     * Provides a fail-closed mastery registry for the orchestrator.
     * When no real mastery registry is configured, this blocks execution by default
     * to prevent unsafe autonomous execution without mastery tracking.
     */
    @Provides
    MasteryRegistry masteryRegistry() {
        return new MasteryRegistry() {
            @Override
            @NotNull
            @SuppressWarnings("deprecation")
            public Promise<Optional<com.ghatana.agent.mastery.MasteryItem>> findBySkill(
                    @NotNull String skillId,
                    @NotNull com.ghatana.agent.environment.EnvironmentFingerprint env) {
                return Promise.of(Optional.empty());
            }

            @Override
            @NotNull
            public Promise<List<com.ghatana.agent.mastery.MasteryItem>> query(@NotNull MasteryQuery query) {
                return Promise.of(List.of());
            }

            @Override
            @NotNull
            public Promise<Optional<MasteryDecision>> queryMastery(@NotNull MasteryQuery query) {
                return Promise.of(Optional.empty());
            }

            @Override
            @NotNull
            public Promise<MasteryDecision> decide(@NotNull MasteryQuery query) {
                // Return a fail-closed decision that blocks execution when no mastery registry is configured
                // This prevents unsafe autonomous execution without mastery tracking
                return Promise.of(MasteryDecision.block(
                        "no-mastery",
                        query.skillId(),
                        com.ghatana.agent.mastery.MasteryState.UNKNOWN,
                        com.ghatana.agent.mastery.MasteryScore.zero(),
                        com.ghatana.agent.mastery.VersionScope.empty(),
                        "No mastery registry configured - blocking execution by default. Configure a real MasteryRegistry or provide explicit approval for this skill."
                ));
            }

            @Override
            @NotNull
            public Promise<com.ghatana.agent.mastery.MasteryItem> save(@NotNull com.ghatana.agent.mastery.MasteryItem item) {
                return Promise.of(item);
            }

            @Override
            @NotNull
            public Promise<com.ghatana.agent.mastery.MasteryTransitionResult> transition(@NotNull com.ghatana.agent.mastery.MasteryTransition transition) {
                return Promise.of(com.ghatana.agent.mastery.MasteryTransitionResult.success(
                        transition.masteryId(),
                        transition.fromState(),
                        transition.toState(),
                        transition.transitionId()
                ));
            }

            @Override
            @NotNull
            @Deprecated
            public Promise<List<com.ghatana.agent.mastery.MasteryItem>> findStale(@NotNull Instant now) {
                return Promise.of(List.of());
            }

            @Override
            @NotNull
            public Promise<List<com.ghatana.agent.mastery.MasteryItem>> findStale(@NotNull String tenantId, @NotNull Instant now) {
                return Promise.of(List.of());
            }

            @Override
            @NotNull
            public Promise<Optional<com.ghatana.agent.mastery.MasteryItem>> getById(@NotNull String tenantId, @NotNull String masteryId) {
                return Promise.of(Optional.empty());
            }

            @Override
            @NotNull
            public Promise<Optional<com.ghatana.agent.mastery.MasteryItem>> findBest(@NotNull MasteryQuery query) {
                return Promise.of(Optional.empty());
            }
        };
    }

    /**
     * Provides a cautious task classifier for the orchestrator.
     * When no real task classifier is configured, this treats all tasks as
     * UNKNOWN novelty and MEDIUM risk to prevent unsafe assumptions.
     */
    @Provides
    TaskClassifier taskClassifier() {
        return new TaskClassifier() {
            @Override
            @NotNull
            public Promise<TaskClassification> classify(@NotNull String taskDescription, @NotNull String context) {
                return Promise.of(new TaskClassification(TaskRiskLevel.MEDIUM, TaskNovelty.UNKNOWN, Map.of(
                        "reason", "No task classifier configured - using cautious defaults"
                )));
            }

            @Override
            @NotNull
            public Promise<TaskClassification> classify(
                    @NotNull String taskDescription,
                    @NotNull String context,
                    @NotNull java.util.Map<String, String> metadata) {
                java.util.Map<String, String> enrichedMetadata = new java.util.LinkedHashMap<>(metadata);
                enrichedMetadata.put("reason", "No task classifier configured - using cautious defaults");
                return Promise.of(new TaskClassification(TaskRiskLevel.MEDIUM, TaskNovelty.UNKNOWN, enrichedMetadata));
            }
        };
    }

    /**
     * Provides the default mode selection policy for the orchestrator.
     * This policy maps mastery state and version applicability to safe execution strategies,
     * defaulting to human-gated for unknown states to prevent unsafe autonomous execution.
     */
    @Provides
    ModeSelectionPolicy modeSelectionPolicy() {
        return new DefaultModeSelectionPolicy();
    }

    /**
     * Provides a mastery-aware mode selector for the orchestrator.
     */
    @Provides
    MasteryAwareModeSelector masteryAwareModeSelector(
            MasteryRegistry masteryRegistry,
            TaskClassifier taskClassifier,
            ModeSelectionPolicy modeSelectionPolicy) {
        return new MasteryAwareModeSelector(masteryRegistry, taskClassifier, modeSelectionPolicy);
    }

    /**
     * Provides the invariant monitor singleton.
     *
     * @return the invariant monitor instance
     */
    @Provides
    InvariantMonitor invariantMonitor() {
        return new DefaultInvariantMonitor();
    }

    /**
     * Provides the Data Cloud event log used by the agent trace ledger.
     *
     * <p>Production deployments supply a Data Cloud {@code EventLogStore} provider.
     * Local development falls back to the registered in-memory Data Cloud SPI provider
     * while still using the same Data Cloud event-log contract.
     *
     * @return platform event log store for agent trace evidence
     */
    @Provides
    EventLogStore agentTraceEventLogStore() {
        com.ghatana.datacloud.spi.EventLogStore dataCloudStore =
                ServiceLoader.load(com.ghatana.datacloud.spi.EventLogStore.class)
                        .findFirst()
                        .orElseGet(InMemoryEventLogStoreProvider::new);
        return EventLogStoreAdapters.toPlatformStore(dataCloudStore);
    }

    /**
     * Provides the agent trace ledger implementation.
     *
     * @param eventLogStore Data Cloud event-log store for trace evidence
     * @return Data Cloud-backed hash-chained trace ledger
     */
    @Provides
    AgentTraceLedger agentTraceLedger(EventLogStore eventLogStore) {
        return new DataCloudAgentTraceLedger(eventLogStore);
    }

    /**
     * Provides a governed agent dispatcher wrapping the catalog dispatcher.
     *
     * <p>This uses real implementations for governance components to ensure
     * safe dispatch with proper invariant monitoring and trace recording.
     *
     * @param catalogAgentDispatcher the base catalog dispatcher to wrap
     * @param invariantMonitor the invariant monitor for pre-dispatch checks
     * @param traceLedger the trace ledger for evidence recording
     * @param masteryAwareModeSelector the mode selector
     * @return governed agent dispatcher
     */
    @Provides
    GovernedAgentDispatcher governedAgentDispatcher(
            CatalogAgentDispatcher catalogAgentDispatcher,
            InvariantMonitor invariantMonitor,
            AgentTraceLedger traceLedger,
            MasteryAwareModeSelector masteryAwareModeSelector) {
        return new GovernedAgentDispatcher(
                catalogAgentDispatcher,
                invariantMonitor,
                traceLedger,
                masteryAwareModeSelector
        );
    }

    /**
     * Provides a no-op memory retriever for the orchestrator.
     */
    @Provides
    MemoryRetriever memoryRetriever() {
        return new MemoryRetriever() {
            @Override
            @NotNull
            public Promise<List<Object>> retrieve(@NotNull String query, @NotNull String context) {
                return Promise.of(List.of());
            }

            @Override
            @NotNull
            public Promise<List<Object>> retrieve(
                    @NotNull String query,
                    @NotNull String context,
                    @NotNull java.util.Map<String, String> options) {
                return Promise.of(List.of());
            }
        };
    }

    /**
     * Provides a durable learning delta repository for the orchestrator.
     * Uses DataCloudLearningDeltaRepository backed by EntityRepository for persistent storage.
     *
     * @param entityRepository Data Cloud entity repository for durable persistence
     * @return durable learning delta repository
     */
    @Provides
    LearningDeltaRepository learningDeltaRepository(EntityRepository entityRepository) {
        return new DataCloudLearningDeltaRepository(entityRepository);
    }

    /**
     * Provides a no-op promotion engine for the orchestrator.
     */
    @Provides
    PromotionEngine promotionEngine(MasteryRegistry masteryRegistry,
                                   LearningDeltaRepository deltaRepository) {
        return new DefaultPromotionEngine(masteryRegistry, deltaRepository);
    }

    /**
     * Provides a learning delta evaluator for the orchestrator.
     */
    @Provides
    LearningDeltaEvaluator learningDeltaEvaluator() {
        return new DefaultLearningDeltaEvaluator();
    }

    /**
     * Provides an obsolescence detector for the orchestrator.
     */
    @Provides
    ObsolescenceDetector obsolescenceDetector(MasteryRegistry masteryRegistry) {
        return new DefaultObsolescenceDetector(masteryRegistry);
    }

    /**
     * Provides {@link AgentDispatcher} bound to {@link GovernedAgentDispatcher}.
     * This ensures all consumers of AgentDispatcher get the governed version.
     *
     * @param governedDispatcher the governed dispatcher
     * @return agent dispatcher interface
     */
    @Provides
    AgentDispatcher agentDispatcher(GovernedAgentDispatcher governedDispatcher) {
        return governedDispatcher;
    }
}
