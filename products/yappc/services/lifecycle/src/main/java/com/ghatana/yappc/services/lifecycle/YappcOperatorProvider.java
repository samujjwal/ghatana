/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.services.lifecycle;

import com.ghatana.core.operator.OperatorConfig;
import com.ghatana.core.operator.OperatorId;
import com.ghatana.core.operator.OperatorState;
import com.ghatana.core.operator.OperatorType;
import com.ghatana.core.operator.OperatorResult;
import com.ghatana.core.operator.UnifiedOperator;
import com.ghatana.core.operator.spi.OperatorProvider;
import com.ghatana.governance.PolicyEngine;
import com.ghatana.platform.domain.domain.event.Event;
import com.ghatana.yappc.services.lifecycle.operators.AgentDispatchOperator;
import com.ghatana.yappc.services.lifecycle.operators.AgentDispatchValidatorOperator;
import com.ghatana.yappc.services.lifecycle.operators.AgentExecutorOperator;
import com.ghatana.yappc.services.lifecycle.operators.BackpressureOperator;
import com.ghatana.yappc.services.lifecycle.operators.GateOrchestratorOperator;
import com.ghatana.yappc.services.lifecycle.operators.LifecycleStatePublisherOperator;
import com.ghatana.yappc.services.lifecycle.operators.MetricsCollectorOperator;
import com.ghatana.yappc.services.lifecycle.operators.PhaseTransitionValidatorOperator;
import com.ghatana.yappc.services.lifecycle.operators.ResultAggregatorOperator;
import com.ghatana.yappc.agent.YappcAgentSystem;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AEP {@link OperatorProvider} SPI implementation that exposes all YAPPC lifecycle
 * operators to the AEP {@code OperatorCatalog} and {@code PipelineMaterializer}.
 *
 * <h2>Type Bridge</h2>
 * YAPPC operators extend {@code com.ghatana.platform.workflow.operator.UnifiedOperator}
 * while the AEP type system expects {@code com.ghatana.core.operator.UnifiedOperator}.
 * The inner {@link PlatformOperatorBridge} class translates between these two parallel
 * type hierarchies at runtime, converting {@code OperatorId}, {@code OperatorType},
 * {@code OperatorState}, {@code OperatorResult}, and {@code OperatorConfig} objects in
 * both directions. Both hierarchies share the same {@code Event} type so event data
 * passes through without conversion.
 *
 * <h2>Instantiation Model</h2>
 * <p>Operators are created in two ways:
 * <ol>
 *   <li><b>On-demand (simple operators)</b>: Operators with no external dependencies
 *       are instantiated directly in {@link #createOperator(OperatorId, OperatorConfig)}.</li>
 *   <li><b>Registry-based (complex operators)</b>: Operators that need domain services
 *       (e.g. {@link PolicyEngine}, {@link AepEventBridge}) must be pre-registered by
 *       calling {@link #configure(PolicyEngine, HumanApprovalService, AepEventBridge)}
 *       before any catalog initialisation. If a complex operator is requested before
 *       configuration an {@link IllegalStateException} is thrown immediately.</li>
 * </ol>
 *
 * <h2>ServiceLoader Discovery</h2>
 * <p>This class is registered in
 * {@code META-INF/services/com.ghatana.core.operator.spi.OperatorProvider}. When
 * {@code OperatorProviderRegistry.discoverProviders()} runs, it discovers and registers
 * this provider automatically. Callers may also register it manually via
 * {@code providerRegistry.registerProvider(new YappcOperatorProvider())}.
 *
 * @doc.type class
 * @doc.purpose AEP SPI provider: exposes YAPPC lifecycle operators to the AEP catalog
 * @doc.layer product
 * @doc.pattern Service, Adapter, Bridge
 * @doc.gaa.lifecycle perceive, act
 * @see PlatformOperatorBridge
 */
public class YappcOperatorProvider implements OperatorProvider {

    private static final Logger log = LoggerFactory.getLogger(YappcOperatorProvider.class);

    /** Provider ID used in the AEP operator provider registry. */
    public static final String PROVIDER_ID = "yappc-lifecycle";

    // -----------------------------------------------------------------------
    // Static configuration — populated before catalog initialisation
    // -----------------------------------------------------------------------

    /** Configured policy engine; required only by GateOrchestratorOperator. */
    private static final AtomicReference<PolicyEngine> POLICY_ENGINE = new AtomicReference<>();

    /** Configured human approval service; required only by GateOrchestratorOperator. */
    private static final AtomicReference<HumanApprovalService> HUMAN_APPROVAL = new AtomicReference<>();

    /** Configured AEP event bridge; required only by LifecycleStatePublisherOperator. */
    private static final AtomicReference<AepEventBridge> AEP_EVENT_BRIDGE = new AtomicReference<>();

    /** Configured YAPPC agent system; required by AgentExecutorOperator for real dispatch. */
    private static final AtomicReference<YappcAgentSystem> YAPPC_AGENT_SYSTEM = new AtomicReference<>();

    /**
     * Pre-registered operator instances grouped by operator ID string, populated by
     * {@link #configure(PolicyEngine, HumanApprovalService, AepEventBridge)} or
     * {@link #registerPrebuilt}.
     */
    private static final Map<String, UnifiedOperator> PREBUILT_REGISTRY = new ConcurrentHashMap<>();

    // -----------------------------------------------------------------------
    // Static API
    // -----------------------------------------------------------------------

    /**
     * Configures the external dependencies required by the complex YAPPC operators.
     *
     * <p>This method MUST be called once during application startup, before
     * {@code OperatorProviderRegistry.discoverProviders()}, to ensure the provider can
     * serve all nine YAPPC operator types.
     *
     * @param policyEngine       policy evaluation engine for gate checks (not null)
     * @param humanApprovalSvc   human approval workflow service (not null)
     * @param aepEventBridge     bridge to AEP event publishing (not null)
     */
    public static void configure(
            PolicyEngine policyEngine,
            HumanApprovalService humanApprovalSvc,
            AepEventBridge aepEventBridge) {
        Objects.requireNonNull(policyEngine,     "policyEngine");
        Objects.requireNonNull(humanApprovalSvc, "humanApprovalSvc");
        Objects.requireNonNull(aepEventBridge,   "aepEventBridge");

        POLICY_ENGINE.set(policyEngine);
        HUMAN_APPROVAL.set(humanApprovalSvc);
        AEP_EVENT_BRIDGE.set(aepEventBridge);

        log.info("YappcOperatorProvider configured with PolicyEngine={}, HumanApprovalService={}, AepEventBridge={}",
                policyEngine.getClass().getSimpleName(),
                humanApprovalSvc.getClass().getSimpleName(),
                aepEventBridge.getClass().getSimpleName());
    }

    /**
     * Configures the {@link YappcAgentSystem} used by {@link AgentExecutorOperator} for
     * real agent dispatch. Call this at startup after the agent system is initialized.
     *
     * @param agentSystem initialized YAPPC agent system (not null)
     */
    public static void configureAgentSystem(YappcAgentSystem agentSystem) {
        Objects.requireNonNull(agentSystem, "agentSystem");
        YAPPC_AGENT_SYSTEM.set(agentSystem);
        log.info("YappcOperatorProvider: YappcAgentSystem configured for agent-executor dispatch");
    }

    /**
     * Registers a pre-built, fully configured YAPPC operator wrapped in a bridge.
     *
     * <p>Use this when you have an already-constructed lifecycle operator instance (e.g.
     * one managed by the YAPPC lifecycle DI context) and want it used for pipeline
     * materialisation without the SPI recreating it.
     *
     * @param platformOperator pre-built YAPPC lifecycle operator (not null)
     */
    public static void registerPrebuilt(
            com.ghatana.platform.workflow.operator.UnifiedOperator platformOperator) {
        Objects.requireNonNull(platformOperator, "platformOperator");
        OperatorId coreId = toCoreId(platformOperator.getId());
        UnifiedOperator bridge = new PlatformOperatorBridge(platformOperator);
        UnifiedOperator existing = PREBUILT_REGISTRY.put(coreId.toString(), bridge);
        if (existing != null) {
            log.warn("YappcOperatorProvider: replaced existing registration for operatorId={}", coreId);
        } else {
            log.info("YappcOperatorProvider: pre-registered operatorId={}", coreId);
        }
    }

    // -----------------------------------------------------------------------
    // OperatorProvider contract
    // -----------------------------------------------------------------------

    /** No-arg constructor required for {@link java.util.ServiceLoader} discovery. */
    public YappcOperatorProvider() {}

    @Override
    public String getProviderId() {
        return PROVIDER_ID;
    }

    @Override
    public String getProviderName() {
        return "YAPPC Lifecycle Operators";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public int priority() {
        return 500; // Higher priority than default 1000 — YAPPC owns these IDs
    }

    @Override
    public Set<OperatorId> getOperatorIds() {
        return Set.of(
            OperatorId.of("yappc", "stream", "phase-transition-validator",  "1.0.0"),
            OperatorId.of("yappc", "stream", "gate-orchestrator",           "1.0.0"),
            OperatorId.of("yappc", "stream", "lifecycle-state-publisher",   "1.0.0"),
            OperatorId.of("yappc", "stream", "agent-dispatch-validator",    "1.0.0"),
            OperatorId.of("yappc", "stream", "agent-executor",              "1.0.0"),
            OperatorId.of("yappc", "stream", "result-aggregator",           "1.0.0"),
            OperatorId.of("yappc", "stream", "metrics-collector",           "1.0.0"),
            OperatorId.of("yappc", "stream", "backpressure-handler",        "1.0.0"),
            OperatorId.of("yappc", "stream", "agent-dispatch",              "1.0.0")
        );
    }

    @Override
    public Set<OperatorType> getOperatorTypes() {
        return Set.of(OperatorType.STREAM);
    }

    /**
     * Creates (or retrieves a pre-registered) YAPPC lifecycle operator wrapped in a
     * type bridge that implements the AEP {@link UnifiedOperator} contract.
     *
     * @param operatorId AEP-typed operator ID; must match one of {@link #getOperatorIds()}
     * @param config     operator configuration (properties forwarded to wrapped operator)
     * @return bridge-wrapped YAPPC operator, never null
     * @throws IllegalArgumentException if the ID is not supported by this provider
     * @throws IllegalStateException    if a complex operator is requested before
     *                                  {@link #configure} has been called
     */
    @Override
    public UnifiedOperator createOperator(OperatorId operatorId, OperatorConfig config) {
        Objects.requireNonNull(operatorId, "operatorId");
        Objects.requireNonNull(config,     "config");

        String key = operatorId.toString();

        // 1. Pre-registered instances take priority (fully configured by DI context)
        UnifiedOperator prebuilt = PREBUILT_REGISTRY.get(key);
        if (prebuilt != null) {
            log.debug("YappcOperatorProvider: serving pre-registered operator {}", key);
            return prebuilt;
        }

        // 2. Create on demand, converting the AEP OperatorConfig to a platform one
        com.ghatana.platform.workflow.operator.OperatorConfig platformConfig = toPlatformConfig(config);

        String name = operatorId.getName();
        com.ghatana.platform.workflow.operator.UnifiedOperator platformOp = switch (name) {

            case "phase-transition-validator" ->
                new PhaseTransitionValidatorOperator(
                    new TransitionConfigLoader(),
                    new StageConfigLoader(),
                    new GateEvaluator());

            case "gate-orchestrator" -> {
                PolicyEngine pe          = requireConfigured(POLICY_ENGINE, "PolicyEngine");
                HumanApprovalService has = requireConfigured(HUMAN_APPROVAL, "HumanApprovalService");
                yield new GateOrchestratorOperator(pe, has);
            }

            case "lifecycle-state-publisher" -> {
                AepEventBridge bridge = requireConfigured(AEP_EVENT_BRIDGE, "AepEventBridge");
                yield new LifecycleStatePublisherOperator(bridge);
            }

            case "agent-dispatch-validator" -> new AgentDispatchValidatorOperator();

            case "agent-executor"           -> new AgentExecutorOperator(YAPPC_AGENT_SYSTEM.get());

            case "result-aggregator"        -> new ResultAggregatorOperator();

            case "metrics-collector"        -> new MetricsCollectorOperator();

            case "backpressure-handler"     -> new BackpressureOperator();

            case "agent-dispatch"           -> new AgentDispatchOperator(new StageConfigLoader());

            default -> throw new IllegalArgumentException(
                "YappcOperatorProvider does not support operatorId=" + operatorId);
        };

        log.debug("YappcOperatorProvider: created operator {}", key);
        return new PlatformOperatorBridge(platformOp);
    }

    // -----------------------------------------------------------------------
    // Helper utilities
    // -----------------------------------------------------------------------

    private static <T> T requireConfigured(AtomicReference<T> ref, String name) {
        T value = ref.get();
        if (value == null) {
            throw new IllegalStateException(
                "YappcOperatorProvider: " + name + " is not configured. "
                + "Call YappcOperatorProvider.configure(...) at application startup "
                + "before any operator catalog initialisation.");
        }
        return value;
    }

    // -----------------------------------------------------------------------
    // Type bridge utilities (static — used by PlatformOperatorBridge as well)
    // -----------------------------------------------------------------------

    static com.ghatana.platform.workflow.operator.OperatorConfig toPlatformConfig(OperatorConfig coreConfig) {
        com.ghatana.platform.workflow.operator.OperatorConfig.Builder b =
            com.ghatana.platform.workflow.operator.OperatorConfig.builder();
        coreConfig.getProperties().forEach(b::withProperty);
        return b.build();
    }

    static OperatorConfig toCoreConfig(com.ghatana.platform.workflow.operator.OperatorConfig platformConfig) {
        return OperatorConfig.builder().withProperties(platformConfig.getProperties()).build();
    }

    static OperatorId toCoreId(com.ghatana.platform.types.identity.OperatorId platformId) {
        return OperatorId.of(
            platformId.getNamespace(),
            platformId.getType(),
            platformId.getName(),
            platformId.getVersion());
    }

    static OperatorType toCoreType(com.ghatana.platform.workflow.operator.OperatorType platformType) {
        try {
            return OperatorType.valueOf(platformType.name());
        } catch (IllegalArgumentException e) {
            return OperatorType.STREAM; // graceful fallback for custom platform types
        }
    }

    static OperatorState toCoreState(com.ghatana.platform.workflow.operator.OperatorState platformState) {
        try {
            return OperatorState.valueOf(platformState.name());
        } catch (IllegalArgumentException e) {
            return OperatorState.CREATED; // graceful fallback
        }
    }

    static OperatorResult toCoreResult(com.ghatana.platform.workflow.operator.OperatorResult r) {
        OperatorResult.Builder b = OperatorResult.builder();
        if (r.isSuccess()) {
            b.success();
        } else {
            b.failed(r.getErrorMessage());
        }
        b.addEvents(r.getOutputEvents());
        b.processingTime(r.getProcessingTimeNanos());
        return b.build();
    }

    // -----------------------------------------------------------------------
    // Inner class: Bridge adapter
    // -----------------------------------------------------------------------

    /**
     * Wraps a {@code com.ghatana.platform.workflow.operator.UnifiedOperator} (YAPPC
     * platform type) so it presents itself as a {@code com.ghatana.core.operator.UnifiedOperator}
     * (AEP type), converting all value-object arguments and return types on the fly.
     *
     * <p>Both operator interfaces share the same {@code Event} type
     * ({@code com.ghatana.platform.domain.domain.event.Event}), so event data passes
     * through without boxing or copying.
     *
     * @doc.type class
     * @doc.purpose Bridge adapter: com.ghatana.platform.workflow → com.ghatana.core operator types
     * @doc.layer product
     * @doc.pattern Adapter, Bridge
     */
    static final class PlatformOperatorBridge implements UnifiedOperator {

        private final com.ghatana.platform.workflow.operator.UnifiedOperator delegate;
        /** Cached AEP-typed operator ID derived from the wrapped operator's platform ID. */
        private final OperatorId coreId;

        PlatformOperatorBridge(com.ghatana.platform.workflow.operator.UnifiedOperator delegate) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
            this.coreId   = toCoreId(delegate.getId());
        }

        // ── Identity ───────────────────────────────────────────────────────

        @Override
        public OperatorId getId() {
            return coreId;
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public OperatorType getType() {
            return toCoreType(delegate.getType());
        }

        @Override
        public String getVersion() {
            return delegate.getVersion();
        }

        @Override
        public String getDescription() {
            return delegate.getDescription();
        }

        @Override
        public List<String> getCapabilities() {
            return delegate.getCapabilities();
        }

        // ── Execution ──────────────────────────────────────────────────────

        @Override
        public Promise<OperatorResult> process(Event event) {
            return delegate.process(event).map(YappcOperatorProvider::toCoreResult);
        }

        // ── Lifecycle ──────────────────────────────────────────────────────

        @Override
        public Promise<Void> initialize(OperatorConfig config) {
            return delegate.initialize(toPlatformConfig(config));
        }

        @Override
        public Promise<Void> start() {
            return delegate.start();
        }

        @Override
        public Promise<Void> stop() {
            return delegate.stop();
        }

        @Override
        public boolean isHealthy() {
            return delegate.isHealthy();
        }

        @Override
        public OperatorState getState() {
            return toCoreState(delegate.getState());
        }

        // ── Observability ──────────────────────────────────────────────────

        @Override
        public Map<String, Object> getMetrics() {
            return delegate.getMetrics();
        }

        @Override
        public Map<String, Object> getInternalState() {
            return delegate.getInternalState();
        }

        @Override
        public OperatorConfig getConfig() {
            return toCoreConfig(delegate.getConfig());
        }

        @Override
        public Map<String, String> getMetadata() {
            return delegate.getMetadata();
        }

        @Override
        public boolean isStateful() {
            return delegate.isStateful();
        }

        // ── Operator-as-agent representation ──────────────────────────────

        @Override
        public Event toEvent() {
            return delegate.toEvent();
        }

        @Override
        public String toString() {
            return "PlatformOperatorBridge{delegateId=" + coreId + "}";
        }
    }
}
