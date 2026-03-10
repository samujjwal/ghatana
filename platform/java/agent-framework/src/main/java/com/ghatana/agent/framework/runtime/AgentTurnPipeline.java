package com.ghatana.agent.framework.runtime;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.resilience.ResiliencePolicy;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Encapsulates the standard GAA agent turn lifecycle as a discrete, reusable pipeline.
 *
 * <p>Phases: PERCEIVE → REASON → ACT → CAPTURE → REFLECT
 *
 * <p>This class decouples the lifecycle orchestration from {@link BaseAgent}, making
 * it testable, composable, and overridable without subclassing the agent itself.
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * AgentTurnPipeline<String, String> pipeline = AgentTurnPipeline.of(myAgent);
 * Promise<String> result = pipeline.execute("input", context);
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

    private final String agentId;
    private final PhaseHandler<TInput, TInput> perceive;
    private final PhaseHandler<TInput, TOutput> reason;
    private final PhaseHandler<TOutput, TOutput> act;
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
            @NotNull PhaseHandler<TInput, TInput> perceive,
            @NotNull PhaseHandler<TInput, TOutput> reason,
            @NotNull PhaseHandler<TOutput, TOutput> act,
            @NotNull CaptureHandler<TInput, TOutput> capture,
            @NotNull CaptureHandler<TInput, TOutput> reflect) {
        this.agentId = Objects.requireNonNull(agentId);
        this.perceive = Objects.requireNonNull(perceive);
        this.reason = Objects.requireNonNull(reason);
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
            (input, ctx) -> Promise.of(agent.perceive(input, ctx)),
            (input, ctx) -> agent.getOutputGenerator().generate(input, ctx),
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
     * Executes the full lifecycle pipeline.
     *
     * @param input   Turn input
     * @param context Execution context
     * @return Promise of the output
     */
    @NotNull
    public Promise<TOutput> execute(@NotNull TInput input, @NotNull AgentContext context) {
        Objects.requireNonNull(input, "input cannot be null");
        Objects.requireNonNull(context, "context cannot be null");

        context.getLogger().info("Pipeline for agent {} starting turn {}", agentId, context.getTurnId());
        context.addTraceTag("agent.id", agentId);

        long startTime = System.currentTimeMillis();

        return Promise.complete()
            // Phase 1: PERCEIVE
            .then(() -> timed("perceive", context, () -> perceive.execute(input, context)))
            // Phase 2: REASON
            .then(perceived -> timed("reason", context, () -> reason.execute(perceived, context)))
            // Phase 3: ACT
            .then(output -> timed("act", context, () -> act.execute(output, context)))
            // Phase 4: CAPTURE
            .then(output -> {
                TOutput captured = output;
                return timed("capture", context, () -> capture.execute(input, output, context))
                    .map(ignored -> captured);
            })
            // Phase 5: REFLECT (fire and forget)
            .whenResult(output -> {
                reflect.execute(input, output, context)
                    .whenComplete((ignored, error) -> {
                        if (error != null) {
                            context.getLogger().error("REFLECT phase failed (non-blocking)", error);
                        }
                    });
            })
            // Final metrics
            .whenComplete((output, error) -> {
                long duration = System.currentTimeMillis() - startTime;
                context.recordMetric("agent.turn.duration", duration);
                context.recordMetric(
                    error == null ? "agent.turn.success" : "agent.turn.failure", 1);
            });
    }

    /**
     * Wraps a phase with timing metrics.
     */
    private <R> Promise<R> timed(String phase, AgentContext context, java.util.function.Supplier<Promise<R>> supplier) {
        long start = System.currentTimeMillis();
        context.getLogger().debug("Phase: {}", phase.toUpperCase());
        return supplier.get()
            .whenComplete((r, e) -> {
                context.recordMetric("agent.phase." + phase + ".duration", System.currentTimeMillis() - start);
                if (e != null) {
                    context.getLogger().error("{} phase failed", phase.toUpperCase(), e);
                }
            });
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
        private PhaseHandler<I, I> perceive = (input, ctx) -> Promise.of(input);
        private PhaseHandler<I, O> reason;
        private PhaseHandler<O, O> act = (output, ctx) -> Promise.of(output);
        private CaptureHandler<I, O> capture = (in, out, ctx) -> Promise.complete();
        private CaptureHandler<I, O> reflect = (in, out, ctx) -> Promise.complete();

        private Builder(String agentId) {
            this.agentId = agentId;
        }

        public Builder<I, O> perceive(@NotNull PhaseHandler<I, I> handler) { this.perceive = handler; return this; }
        public Builder<I, O> reason(@NotNull PhaseHandler<I, O> handler) { this.reason = handler; return this; }
        public Builder<I, O> act(@NotNull PhaseHandler<O, O> handler) { this.act = handler; return this; }
        public Builder<I, O> capture(@NotNull CaptureHandler<I, O> handler) { this.capture = handler; return this; }
        public Builder<I, O> reflect(@NotNull CaptureHandler<I, O> handler) { this.reflect = handler; return this; }

        @NotNull
        public AgentTurnPipeline<I, O> build() {
            Objects.requireNonNull(reason, "reason phase handler is required");
            return new AgentTurnPipeline<>(agentId, perceive, reason, act, capture, reflect);
        }
    }
}
