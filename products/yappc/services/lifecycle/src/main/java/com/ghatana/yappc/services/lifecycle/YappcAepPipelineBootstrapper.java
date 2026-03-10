/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC Lifecycle Service
 */
package com.ghatana.yappc.services.lifecycle;

import com.ghatana.platform.domain.domain.event.GEvent;
import com.ghatana.platform.workflow.operator.OperatorConfig;
import com.ghatana.platform.workflow.pipeline.Pipeline;
import com.ghatana.yappc.services.lifecycle.dlq.DlqPublisher;
import com.ghatana.yappc.services.lifecycle.operators.AgentDispatchOperator;
import com.ghatana.yappc.services.lifecycle.operators.GateOrchestratorOperator;
import com.ghatana.yappc.services.lifecycle.operators.LifecycleStatePublisherOperator;
import com.ghatana.yappc.services.lifecycle.operators.PhaseTransitionValidatorOperator;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;

/**
 * Builds and starts the YAPPC lifecycle management AEP operator pipeline at service startup.
 *
 * <p><b>Pipeline: {@code lifecycle-management-v1}</b>
 * <pre>
 * lifecycle.phase.transition.requested
 *   │
 *   ▼
 * [phase-transition-validator]  ─── validates transition rule + entry gate
 *   │ lifecycle.phase.transition.validated
 *   ▼
 * [gate-orchestrator]           ─── runs policy evaluation; routes to human approval if needed
 *   │ lifecycle.gate.passed  OR  lifecycle.approval.requested
 *   ▼
 * [agent-dispatch]              ─── reads stage agent assignments; emits dispatch events
 *   │ lifecycle.agent.dispatched (one per agent)
 *   ▼
 * [lifecycle-state-publisher]   ─── emits lifecycle.phase.advanced to AEP stream
 * </pre>
 *
 * <p><b>Usage</b><br>
 * Call {@link #start()} once at service startup (e.g., from
 * {@link io.activej.boot.ServiceGraphModule} or {@link LifecycleServiceModule}).
 * The returned {@link Pipeline} can be used to route incoming events.
 *
 * @doc.type class
 * @doc.purpose Builds and starts the YAPPC lifecycle management AEP operator pipeline
 * @doc.layer product
 * @doc.pattern Service, Bootstrapper
 * @doc.gaa.lifecycle perceive
 */
public class YappcAepPipelineBootstrapper {

    private static final Logger log = LoggerFactory.getLogger(YappcAepPipelineBootstrapper.class);

    /** Identifier used to register the pipeline with the AEP registry. */
    public static final String PIPELINE_ID      = "lifecycle-management-v1";
    public static final String PIPELINE_VERSION = "1.0.0";

    // Node IDs within the pipeline graph
    private static final String NODE_VALIDATOR  = "phase-transition-validator";
    private static final String NODE_GATE       = "gate-orchestrator";
    private static final String NODE_DISPATCH   = "agent-dispatch";
    private static final String NODE_PUBLISHER  = "lifecycle-state-publisher";

    private final PhaseTransitionValidatorOperator validatorOperator;
    private final GateOrchestratorOperator         gateOperator;
    private final AgentDispatchOperator            dispatchOperator;
    private final LifecycleStatePublisherOperator  publisherOperator;
    private final DlqPublisher                    dlqPublisher;

    private volatile Pipeline lifecycle;

    /**
     * Creates a {@code YappcAepPipelineBootstrapper} with the 4 lifecycle operators.
     *
     * @param validatorOperator validates phase transition rules and gate criteria
     * @param gateOperator      orchestrates policy + human-approval gate logic
     * @param dispatchOperator  emits agent dispatch events per stage assignment
     * @param publisherOperator publishes the final lifecycle.phase.advanced event
     * @param dlqPublisher      publishes failed events to the dead-letter queue
     */
    public YappcAepPipelineBootstrapper(
            PhaseTransitionValidatorOperator validatorOperator,
            GateOrchestratorOperator         gateOperator,
            AgentDispatchOperator            dispatchOperator,
            LifecycleStatePublisherOperator  publisherOperator,
            DlqPublisher                    dlqPublisher) {
        this.validatorOperator = Objects.requireNonNull(validatorOperator, "validatorOperator");
        this.gateOperator      = Objects.requireNonNull(gateOperator, "gateOperator");
        this.dispatchOperator  = Objects.requireNonNull(dispatchOperator, "dispatchOperator");
        this.publisherOperator = Objects.requireNonNull(publisherOperator, "publisherOperator");
        this.dlqPublisher      = Objects.requireNonNull(dlqPublisher, "dlqPublisher");
    }

    /**
     * Builds the sequential lifecycle pipeline and initializes all operators.
     *
     * <p>Idempotent — subsequent calls return the already-started pipeline.
     *
     * @return Promise completing with the started {@link Pipeline}
     */
    public Promise<Pipeline> start() {
        if (lifecycle != null) {
            return Promise.of(lifecycle);
        }

        log.info("Building YAPPC lifecycle pipeline '{}'", PIPELINE_ID);

        // Build linear DAG: validator → gate → dispatch → publisher
        Pipeline pipeline = Pipeline.builder(PIPELINE_ID, PIPELINE_VERSION)
            .addNode(NODE_VALIDATOR, validatorOperator)
            .addNode(NODE_GATE,      gateOperator)
            .addNode(NODE_DISPATCH,  dispatchOperator)
            .addNode(NODE_PUBLISHER, publisherOperator)
            .edge(NODE_VALIDATOR, NODE_GATE)
            .edge(NODE_GATE,      NODE_DISPATCH)
            .edge(NODE_DISPATCH,  NODE_PUBLISHER)
            .build();

        // Initialize all operators with empty default config
        OperatorConfig config = OperatorConfig.empty();

        return pipeline.initialize(config)
            .map(v -> {
                this.lifecycle = pipeline;
                log.info("YAPPC lifecycle pipeline '{}' started — {} operators active",
                        PIPELINE_ID, 4);
                return pipeline;
            })
            .then(
                p -> Promise.of(p),
                e -> {
                    log.error("Failed to start lifecycle pipeline '{}': {}", PIPELINE_ID, e.getMessage(), e);
                    return Promise.ofException(e);
                });
    }

    /**
     * Routes an incoming event through the lifecycle pipeline, publishing failures to the DLQ.
     *
     * <p>If the pipeline throws or returns an error result the failure is forwarded to the
     * {@link DlqPublisher} before the exception is re-propagated to the caller.
     *
     * @param tenantId      tenant owning the event
     * @param eventType     event-type string (e.g. {@code lifecycle.phase.transition.requested})
     * @param eventPayload  key-value payload of the event
     * @param correlationId optional correlation id for tracing (may be {@code null})
     * @return Promise completing when the pipeline finishes processing
     */
    public Promise<Void> routeEvent(
            String tenantId,
            String eventType,
            Map<String, Object> eventPayload,
            String correlationId) {
        if (lifecycle == null) {
            return Promise.ofException(
                new IllegalStateException("Pipeline not started — call start() first"));
        }
        // Build event from parameters
        GEvent.GEventBuilder eventBuilder = GEvent.builder()
            .type(eventType)
            .typeTenantVersion(tenantId, eventType, "1.0");
        eventPayload.forEach((key, value) -> eventBuilder.addPayload(key, value));
        if (correlationId != null) {
            eventBuilder.addPayload("correlationId", correlationId);
        }

        return lifecycle.execute(eventBuilder.build())
            .then(
                result -> Promise.complete(),
                e -> {
                    log.error("Pipeline '{}' threw for event '{}': {}", PIPELINE_ID, eventType, e.getMessage(), e);
                    return dlqPublisher.publish(
                            tenantId, PIPELINE_ID, "unknown",
                            eventType, eventPayload,
                            e.getMessage(), correlationId)
                        .then(() -> Promise.<Void>ofException(e));
                });
    }

    /**
     * Returns the running pipeline, or {@code null} if not yet started.
     *
     * @return pipeline or {@code null}
     */
    public Pipeline getPipeline() {
        return lifecycle;
    }

    /**
     * Shuts down the pipeline gracefully (stops all operators).
     *
     * @return Promise completing when all operators have stopped
     */
    public Promise<Void> stop() {
        if (lifecycle == null) {
            return Promise.complete();
        }
        log.info("Shutting down YAPPC lifecycle pipeline '{}'", PIPELINE_ID);
        return lifecycle.shutdown();
    }
}
