/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Lifecycle Service
 */
package com.ghatana.yappc.services.lifecycle;

import com.ghatana.platform.domain.domain.event.GEvent;
import com.ghatana.platform.workflow.operator.OperatorConfig;
import com.ghatana.platform.workflow.pipeline.Pipeline;
import com.ghatana.yappc.services.lifecycle.dlq.DlqPublisher;
import com.ghatana.yappc.services.lifecycle.operators.AgentDispatchValidatorOperator;
import com.ghatana.yappc.services.lifecycle.operators.AgentExecutorOperator;
import com.ghatana.yappc.services.lifecycle.operators.BackpressureOperator;
import com.ghatana.yappc.services.lifecycle.operators.MetricsCollectorOperator;
import com.ghatana.yappc.services.lifecycle.operators.ResultAggregatorOperator;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;

/**
 * Builds and starts the YAPPC agent orchestration AEP operator pipeline at service startup.
 *
 * <p><b>Pipeline: {@code agent-orchestration-v1}</b>
 * <pre>
 * agent.dispatch.requested
 *   │
 *   ▼
 * [agent-dispatch-validator]   ─── validates required fields (agentId, fromStage, toStage)
 *   │ agent.dispatch.validated
 *   ▼
 * [backpressure-handler]       ─── bounded queue (2048, DROP_OLDEST) for rate spikes
 *   │ agent.dispatch.validated (rate-limited)
 *   ▼
 * [agent-executor]             ─── executes agent; emits agent.result.produced
 *   │ agent.result.produced
 *   ▼
 * [result-aggregator]          ─── aggregates by correlation_id; emits workflow.step.completed
 *   │ workflow.step.completed
 *   ▼
 * [metrics-collector]          ─── side-effect: emits agent.metrics.updated
 * </pre>
 *
 * <p><b>Usage</b><br>
 * Call {@link #start()} once at service startup (e.g., from
 * {@link io.activej.boot.ServiceGraphModule} or {@link LifecycleServiceModule}).
 * The returned {@link Promise}{@code <}{@link Pipeline}{@code >} resolves when the
 * pipeline is ready to route agent dispatch events.
 *
 * @doc.type class
 * @doc.purpose Builds and starts the YAPPC agent-orchestration-v1 AEP operator pipeline
 * @doc.layer product
 * @doc.pattern Service, Bootstrapper
 * @doc.gaa.lifecycle perceive
 */
public class YappcAgentOrchestrationBootstrapper {

    private static final Logger log = LoggerFactory.getLogger(YappcAgentOrchestrationBootstrapper.class);

    /** Identifier used to register the pipeline with the AEP registry. */
    public static final String PIPELINE_ID      = "agent-orchestration-v1";
    public static final String PIPELINE_VERSION = "1.0.0";

    // Node IDs within the pipeline graph
    private static final String NODE_VALIDATOR    = "agent-dispatch-validator";
    private static final String NODE_BACKPRESSURE = "backpressure-handler";
    private static final String NODE_EXECUTOR     = "agent-executor";
    private static final String NODE_AGGREGATOR   = "result-aggregator";
    private static final String NODE_METRICS      = "metrics-collector";

    private final AgentDispatchValidatorOperator validatorOperator;
    private final BackpressureOperator           backpressureOperator;
    private final AgentExecutorOperator          executorOperator;
    private final ResultAggregatorOperator       aggregatorOperator;
    private final MetricsCollectorOperator       metricsOperator;
    private final DlqPublisher                  dlqPublisher;

    private volatile Pipeline orchestration;

    /**
     * Creates a {@code YappcAgentOrchestrationBootstrapper} with the 5 agent
     * orchestration operators.
     *
     * @param validatorOperator   validates agent dispatch events
     * @param backpressureOperator bounded FIFO buffer for rate spikes
     * @param executorOperator    executes agents and emits result events
     * @param aggregatorOperator  aggregates results by correlation_id
     * @param metricsOperator     collects and emits agent execution metrics
     * @param dlqPublisher        publishes failed events to the dead-letter queue
     */
    public YappcAgentOrchestrationBootstrapper(
            AgentDispatchValidatorOperator validatorOperator,
            BackpressureOperator           backpressureOperator,
            AgentExecutorOperator          executorOperator,
            ResultAggregatorOperator       aggregatorOperator,
            MetricsCollectorOperator       metricsOperator,
            DlqPublisher                  dlqPublisher) {
        this.validatorOperator    = Objects.requireNonNull(validatorOperator,    "validatorOperator");
        this.backpressureOperator = Objects.requireNonNull(backpressureOperator, "backpressureOperator");
        this.executorOperator     = Objects.requireNonNull(executorOperator,     "executorOperator");
        this.aggregatorOperator   = Objects.requireNonNull(aggregatorOperator,   "aggregatorOperator");
        this.metricsOperator      = Objects.requireNonNull(metricsOperator,      "metricsOperator");
        this.dlqPublisher         = Objects.requireNonNull(dlqPublisher,         "dlqPublisher");
    }

    /**
     * Builds the sequential agent orchestration pipeline and initializes all operators.
     *
     * <p>Idempotent — subsequent calls return the already-started pipeline.
     *
     * @return Promise completing with the started {@link Pipeline}
     */
    public Promise<Pipeline> start() {
        if (orchestration != null) {
            return Promise.of(orchestration);
        }

        log.info("Building YAPPC agent orchestration pipeline '{}'", PIPELINE_ID);

        // Build linear DAG: validator → backpressure → executor → aggregator → metrics
        Pipeline pipeline = Pipeline.builder(PIPELINE_ID, PIPELINE_VERSION)
            .addNode(NODE_VALIDATOR,    validatorOperator)
            .addNode(NODE_BACKPRESSURE, backpressureOperator)
            .addNode(NODE_EXECUTOR,     executorOperator)
            .addNode(NODE_AGGREGATOR,   aggregatorOperator)
            .addNode(NODE_METRICS,      metricsOperator)
            .edge(NODE_VALIDATOR,    NODE_BACKPRESSURE)
            .edge(NODE_BACKPRESSURE, NODE_EXECUTOR)
            .edge(NODE_EXECUTOR,     NODE_AGGREGATOR)
            .edge(NODE_AGGREGATOR,   NODE_METRICS)
            .build();

        // Initialize all operators with empty default config
        OperatorConfig config = OperatorConfig.empty();

        return pipeline.initialize(config)
            .map(v -> {
                this.orchestration = pipeline;
                log.info("YAPPC agent orchestration pipeline '{}' started — {} operators active",
                        PIPELINE_ID, 5);
                return pipeline;
            })
            .then(
                p -> Promise.of(p),
                e -> {
                    log.error("Failed to start agent orchestration pipeline '{}': {}",
                            PIPELINE_ID, e.getMessage(), e);
                    return Promise.ofException(e);
                });
    }

    /**
     * Routes an incoming agent dispatch event through the orchestration pipeline,
     * publishing failures to the DLQ.
     *
     * @param tenantId      tenant owning the event
     * @param eventType     event-type string (e.g. {@code agent.dispatch.requested})
     * @param eventPayload  key-value payload of the event
     * @param correlationId optional correlation id for tracing (may be {@code null})
     * @return Promise completing when the pipeline finishes processing
     */
    public Promise<Void> routeEvent(
            String tenantId,
            String eventType,
            Map<String, Object> eventPayload,
            String correlationId) {
        if (orchestration == null) {
            return Promise.ofException(
                new IllegalStateException("Pipeline not started — call start() first"));
        }

        GEvent.GEventBuilder eventBuilder = GEvent.builder()
            .type(eventType)
            .typeTenantVersion(tenantId, eventType, "1.0");
        eventPayload.forEach((key, value) -> eventBuilder.addPayload(key, value));
        if (correlationId != null) {
            eventBuilder.addPayload("correlationId", correlationId);
        }

        return orchestration.execute(eventBuilder.build())
            .then(
                result -> Promise.complete(),
                e -> {
                    log.error("Agent orchestration pipeline '{}' threw for event '{}': {}",
                            PIPELINE_ID, eventType, e.getMessage(), e);
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
        return orchestration;
    }

    /**
     * Shuts down the pipeline gracefully (stops all operators).
     *
     * @return Promise completing when all operators have stopped
     */
    public Promise<Void> stop() {
        if (orchestration == null) {
            return Promise.complete();
        }
        log.info("Stopping YAPPC agent orchestration pipeline '{}'", PIPELINE_ID);
        return orchestration.shutdown().map(v -> {
            this.orchestration = null;
            log.info("YAPPC agent orchestration pipeline '{}' stopped", PIPELINE_ID);
            return null;
        });
    }
}
