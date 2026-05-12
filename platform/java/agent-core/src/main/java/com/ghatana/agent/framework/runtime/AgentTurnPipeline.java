package com.ghatana.agent.framework.runtime;

import com.ghatana.agent.AgentResult;
import com.ghatana.agent.AgentResultStatus;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.resilience.ResiliencePolicy;
import com.ghatana.agent.lifecycle.AgentLifecyclePhase;
import com.ghatana.agent.lifecycle.AgentPhaseTrace;
import com.ghatana.agent.runtime.mode.ExecutionMode;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Encapsulates the standard GAA agent turn lifecycle as a discrete, reusable pipeline.
 *
 * <p>Operational phases: ADMIT → PERCEIVE → REASON → VERIFY → ACT → CAPTURE → REFLECT → COMPLETE
 *
 * <p>This class decouples the lifecycle orchestration from {@link BaseAgent}, making
 * it testable, composable, and overridable without subclassing the agent itself.
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * AgentTurnPipeline<String, String> pipeline = AgentTurnPipeline.of(myAgent);
 * Promise<AgentResult<String>> result = pipeline.executeResult("input", context);
 * }</pre>
 *
 * @param <TInput>  Turn input type
 * @param <TOutput> Turn output type
 *
 * @doc.type class
 * @doc.purpose Reusable agent turn lifecycle pipeline
 * @doc.layer framework
 * @doc.pattern Pipeline, Strategy
 * @doc.gaa.lifecycle perceive|reason|act|capture|reflect
 */
public class AgentTurnPipeline<TInput, TOutput> {

    private static final Logger log = LoggerFactory.getLogger(AgentTurnPipeline.class);

    /**
     * Functional interface for each lifecycle phase.
     */
    @FunctionalInterface
    public interface PhaseHandler<I, O> {
        @NotNull Promise<O> execute(@NotNull I input, @NotNull AgentContext context);
    }

    /**
     * Functional interface for pre-ADMIT enrichment hook.
     *
     * <p>This hook runs before the ADMIT phase and is used for:
     * <ul>
     *   <li>Task classification (risk level, novelty)</li>
     *   <li>Environment snapshot (version context, dependencies)</li>
     *   <li>Mastery decision (which skills to use)</li>
     *   <li>Mode selection (online/offline, strict/lenient)</li>
     * </ul>
     */
    @FunctionalInterface
    public interface PreAdmitEnrichmentHandler<I> {
        @NotNull Promise<Void> execute(@NotNull I input, @NotNull AgentContext context);
    }

    private final String agentId;
    @Nullable
    private ExecutionMode executionMode;
    @Nullable
    private PreAdmitEnrichmentHandler<TInput> preAdmitEnrichment;
    private final PhaseHandler<TInput, TInput> perceive;
    private final PhaseHandler<TInput, AgentResult<TOutput>> reason;
    private final PhaseHandler<TOutput, TOutput> act;
    private final PhaseHandler<AgentResult<TOutput>, AgentResult<TOutput>> verify;
    private final CaptureHandler<TInput, TOutput> capture;
    private final CaptureHandler<TInput, TOutput> reflect;

    /**
     * Functional interface for capture/reflect phases that receive both input and output.
     */
    @FunctionalInterface
    public interface CaptureHandler<I, O> {
        @NotNull Promise<Void> execute(@NotNull I input, @NotNull O output, @NotNull AgentContext context);
    }

    private AgentTurnPipeline(
            @NotNull String agentId,
            @Nullable PreAdmitEnrichmentHandler<TInput> preAdmitEnrichment,
            @NotNull PhaseHandler<TInput, TInput> perceive,
            @NotNull PhaseHandler<TInput, AgentResult<TOutput>> reason,
            @NotNull PhaseHandler<AgentResult<TOutput>, AgentResult<TOutput>> verify,
            @NotNull PhaseHandler<TOutput, TOutput> act,
            @NotNull CaptureHandler<TInput, TOutput> capture,
            @NotNull CaptureHandler<TInput, TOutput> reflect) {
        this.agentId = Objects.requireNonNull(agentId);
        this.executionMode = null;
        this.preAdmitEnrichment = preAdmitEnrichment;
        this.perceive = Objects.requireNonNull(perceive);
        this.reason = Objects.requireNonNull(reason);
        this.verify = Objects.requireNonNull(verify);
        this.act = Objects.requireNonNull(act);
        this.capture = Objects.requireNonNull(capture);
        this.reflect = Objects.requireNonNull(reflect);
    }

    /**
     * Creates a pipeline from a {@link BaseAgent} by extracting its lifecycle methods.
     *
     * @param agent The agent whose lifecycle phases to use
     * @return A new pipeline
     */
    @NotNull
    public static <I, O> AgentTurnPipeline<I, O> of(@NotNull BaseAgent<I, O> agent) {
        return new AgentTurnPipeline<>(
            agent.getAgentId(),
            null, // no pre-ADMIT enrichment by default
            (input, ctx) -> Promise.of(agent.perceive(input, ctx)),
            (input, ctx) -> {
                Instant start = Instant.now();
                return agent.getOutputGenerator().generate(input, ctx)
                        .map(output -> AgentResult.success(
                                output,
                                agent.getAgentId(),
                                Duration.between(start, Instant.now())));
            },
            (result, ctx) -> Promise.of(result),
            agent::act,
            agent::capture,
            agent::reflect
        );
    }

    /**
     * Creates a builder for constructing a custom pipeline.
     *
     * @param agentId Agent identifier for tracing/metrics
     * @return A new builder
     */
    @NotNull
    public static <I, O> Builder<I, O> builder(@NotNull String agentId) {
        return new Builder<>(agentId);
    }

    /**
     * Executes the full lifecycle pipeline and returns the raw output.
     *
     * <p>Use {@link #executeResult(Object, AgentContext)} for governed runtime
     * calls that need trace and policy metadata.
     *
     * @param input   Turn input
     * @param context Execution context
     * @return Promise of the output
     */
    @NotNull
    public Promise<TOutput> execute(@NotNull TInput input, @NotNull AgentContext context) {
        return executeResult(input, context).map(AgentResult::getOutput);
    }

    /**
     * Executes the full governed lifecycle pipeline.
     *
     * @param input   Turn input
     * @param context Execution context
     * @return Promise of the enriched result envelope
     */
    @NotNull
    public Promise<AgentResult<TOutput>> executeResult(@NotNull TInput input, @NotNull AgentContext context) {
        Objects.requireNonNull(input, "input cannot be null");
        Objects.requireNonNull(context, "context cannot be null");

        context.getLogger().info("Pipeline for agent {} starting turn {}", agentId, context.getTurnId());
        context.addTraceTag("agent.id", agentId);

        Instant startedAt = Instant.now();
        List<AgentPhaseTrace> phaseTraces = new ArrayList<>();

        return Promise.complete()
            .then(() -> {
                // Pre-ADMIT enrichment hook for task classification, environment snapshot, mastery decision, mode selection
                if (preAdmitEnrichment != null) {
                    return preAdmitEnrichment.execute(input, context);
                }
                return Promise.complete();
            })
            .then(() -> timed(AgentLifecyclePhase.ADMIT, context, phaseTraces, () -> Promise.of(input)))
            .then(ignored -> timed(AgentLifecyclePhase.PERCEIVE, context, phaseTraces,
                    () -> perceive.execute(input, context)))
            .then(perceived -> timed(AgentLifecyclePhase.REASON, context, phaseTraces,
                    () -> reason.execute(perceived, context)))
            .then(result -> timed(AgentLifecyclePhase.VERIFY, context, phaseTraces,
                    () -> verify.execute(result, context)))
            .then(result -> {
                if (result.isFailed() || result.getStatus() == AgentResultStatus.DENIED) {
                    return Promise.of(result);
                }
                TOutput output = result.getOutput();
                if (output == null) {
                    return Promise.of(result);
                }
                return timed(AgentLifecyclePhase.ACT, context, phaseTraces, () -> act.execute(output, context))
                        .map(actedOutput -> result.toBuilder().output(actedOutput).build());
            })
            .then(result -> {
                TOutput captured = result.getOutput();
                if (captured == null) {
                    return Promise.of(result);
                }
                return timed(AgentLifecyclePhase.CAPTURE, context, phaseTraces,
                        () -> capture.execute(input, captured, context))
                    .map(ignored -> result);
            })
            .whenResult(result -> {
                TOutput output = result.getOutput();
                if (output == null) {
                    return;
                }
                timed(AgentLifecyclePhase.REFLECT, context, phaseTraces,
                        () -> reflect.execute(input, output, context))
                    .whenComplete((ignored, error) -> {
                        if (error != null) {
                            context.getLogger().error("REFLECT phase failed (non-blocking)", error);
                        }
                    });
            })
            .then(result -> timed(AgentLifecyclePhase.COMPLETE, context, phaseTraces,
                    () -> Promise.of(enrichResult(result, context, startedAt, phaseTraces))))
            .whenComplete((result, error) -> {
                long duration = Duration.between(startedAt, Instant.now()).toMillis();
                context.recordMetric("agent.turn.duration", duration);
                context.recordMetric(
                    error == null ? "agent.turn.success" : "agent.turn.failure", 1);
            });
    }

    /**
     * Wraps a phase with timing metrics.
     */
    private <R> Promise<R> timed(
            AgentLifecyclePhase phase,
            AgentContext context,
            List<AgentPhaseTrace> phaseTraces,
            java.util.function.Supplier<Promise<R>> supplier) {
        Instant startedAt = Instant.now();
        context.getLogger().debug("Phase: {}", phase);
        return supplier.get()
            .whenComplete((r, e) -> {
                Instant endedAt = Instant.now();
                context.recordMetric("agent.phase." + phase.name().toLowerCase() + ".duration",
                        Duration.between(startedAt, endedAt).toMillis());
                phaseTraces.add(new AgentPhaseTrace(
                        phaseTraceId(context, phase),
                        phase,
                        startedAt,
                        endedAt,
                        e == null ? "SUCCESS" : "FAILED",
                        e == null ? null : e.getMessage(),
                        Map.of()));
                if (e != null) {
                    context.getLogger().error("{} phase failed", phase, e);
                }
            });
    }

    private AgentResult<TOutput> enrichResult(
            AgentResult<TOutput> result,
            AgentContext context,
            Instant startedAt,
            List<AgentPhaseTrace> phaseTraces) {
        List<String> phaseTraceRefs = phaseTraces.stream()
                .map(AgentPhaseTrace::phaseTraceId)
                .toList();
        Object releaseId = context.getConfig("agentReleaseId");
        Object specDigest = context.getConfig("specDigest");
        String traceId = String.valueOf(context.getConfig("__traceId"));
        if (traceId == null || "null".equals(traceId)) {
            traceId = UUID.randomUUID().toString();
        }
        return result.toBuilder()
                .agentId(result.getAgentId() != null ? result.getAgentId() : agentId)
                .agentReleaseId(releaseId != null ? releaseId.toString() : result.getAgentReleaseId())
                .specDigest(specDigest != null ? specDigest.toString() : result.getSpecDigest())
                .traceId(result.getTraceId() != null ? result.getTraceId() : traceId)
                .turnId(result.getTurnId() != null ? result.getTurnId() : context.getTurnId())
                .startedAt(result.getStartedAt() != null ? result.getStartedAt() : startedAt)
                .processingTime(result.getProcessingTime() != null
                        ? result.getProcessingTime()
                        : Duration.between(startedAt, Instant.now()))
                .phaseTraceRefs(phaseTraceRefs)
                .build();
    }

    private String phaseTraceId(AgentContext context, AgentLifecyclePhase phase) {
        return agentId + ":" + context.getTurnId() + ":" + phase.name().toLowerCase();
    }

    // ── Resilience-wrapped execution ─────────────────────────────────────

    /**
     * Executes the pipeline with the given {@link ResiliencePolicy} applied.
     *
     * <p>Policy semantics:
     * <ul>
     *   <li><b>Timeout</b>: Each attempt is bounded by {@link ResiliencePolicy#timeout()}.</li>
     *   <li><b>Retry</b>: On failure or timeout, the pipeline is re-executed up to
     *       {@link ResiliencePolicy#maxAttempts()} times (sequentially, no delay).</li>
     * </ul>
     *
     * <p>If all attempts are exhausted the last exception is propagated.
     *
     * @param input   Turn input
     * @param context Execution context
     * @param policy  Resilience policy to enforce
     * @return Promise of the output, resilience-wrapped
     */
    @NotNull
    public Promise<TOutput> executeWithPolicy(
            @NotNull TInput input,
            @NotNull AgentContext context,
            @NotNull ResiliencePolicy policy) {
        Objects.requireNonNull(policy, "policy cannot be null");

        Promise<TOutput> first = Promises.timeout(policy.timeout(), execute(input, context));

        if (!policy.isRetrying()) {
            return first;
        }

        // Chain sequential retries (k = 1..maxAttempts-1)
        Promise<TOutput> chain = first;
        for (int k = 1; k < policy.maxAttempts(); k++) {
            final int attempt = k + 1;
            chain = chain.then(
                result -> Promise.of(result),
                ex -> {
                    log.warn("Pipeline for agent {} attempt {}/{} failed: {}; retrying",
                            agentId, attempt, policy.maxAttempts(), ex.getMessage());
                    return Promises.timeout(policy.timeout(), execute(input, context));
                }
            );
        }
        return chain;
    }

    // ── Builder ─────────────────────────────────────────────────────────

    /**
     * Builder for custom pipelines.
     */
    public static class Builder<I, O> {
        private final String agentId;
        @Nullable
        private ExecutionMode executionMode;
        @Nullable
        private PreAdmitEnrichmentHandler<I> preAdmitEnrichment;
        private PhaseHandler<I, I> perceive = (input, ctx) -> Promise.of(input);
        private PhaseHandler<I, AgentResult<O>> reason;
        private PhaseHandler<AgentResult<O>, AgentResult<O>> verify = (result, ctx) -> Promise.of(result);
        private PhaseHandler<O, O> act = (output, ctx) -> Promise.of(output);
        private CaptureHandler<I, O> capture = (in, out, ctx) -> Promise.complete();
        private CaptureHandler<I, O> reflect = (in, out, ctx) -> Promise.complete();

        private Builder(String agentId) {
            this.agentId = agentId;
        }

        public Builder<I, O> perceive(@NotNull PhaseHandler<I, I> handler) { this.perceive = handler; return this; }
        public Builder<I, O> reason(@NotNull PhaseHandler<I, O> handler) {
            this.reason = (input, ctx) -> {
                Instant start = Instant.now();
                return handler.execute(input, ctx)
                        .map(output -> AgentResult.success(
                                output,
                                agentId,
                                Duration.between(start, Instant.now())));
            };
            return this;
        }
        public Builder<I, O> reasonResult(@NotNull PhaseHandler<I, AgentResult<O>> handler) { this.reason = handler; return this; }
        public Builder<I, O> verify(@NotNull PhaseHandler<AgentResult<O>, AgentResult<O>> handler) { this.verify = handler; return this; }
        public Builder<I, O> act(@NotNull PhaseHandler<O, O> handler) { this.act = handler; return this; }
        public Builder<I, O> capture(@NotNull CaptureHandler<I, O> handler) { this.capture = handler; return this; }
        public Builder<I, O> reflect(@NotNull CaptureHandler<I, O> handler) { this.reflect = handler; return this; }
        public Builder<I, O> withExecutionMode(@Nullable ExecutionMode mode) { this.executionMode = mode; return this; }
        public Builder<I, O> withPreAdmitEnrichment(@NotNull PreAdmitEnrichmentHandler<I> handler) { this.preAdmitEnrichment = handler; return this; }

        @NotNull
        public AgentTurnPipeline<I, O> build() {
            Objects.requireNonNull(reason, "reason phase handler is required");
            AgentTurnPipeline<I, O> pipeline = new AgentTurnPipeline<>(agentId, preAdmitEnrichment, perceive, reason, verify, act, capture, reflect);
            if (executionMode != null) {
                pipeline.executionMode = executionMode;
            }
            return pipeline;
        }
    }
}
