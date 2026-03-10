/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC Core Agents
 */
package com.ghatana.yappc.agent;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.AgentConfig;
import com.ghatana.agent.AgentDescriptor;
import com.ghatana.agent.AgentResult;
import com.ghatana.agent.HealthStatus;
import com.ghatana.agent.TypedAgent;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Agent that coordinates human-in-the-loop approval gates within the YAPPC
 * lifecycle pipeline.
 *
 * <h2>Contract</h2>
 * <ul>
 *   <li>Input: {@link ApprovalRequest} — describes the gate requiring human sign-off</li>
 *   <li>Output: {@link ApprovalDecision} — the final APPROVED or REJECTED decision</li>
 * </ul>
 *
 * <h2>Behaviour</h2>
 * <ol>
 *   <li>The agent receives an {@link ApprovalRequest} from an upstream pipeline operator.</li>
 *   <li>It delegates creation of the request to an {@link ApprovalGateway} — an
 *       interface that can be backed by the lifecycle service's
 *       {@code HumanApprovalService} or a remote REST call.</li>
 *   <li>The agent polls the gateway for a decision until the request is either decided
 *       or its {@code expiresAt} deadline is exceeded.</li>
 *   <li>It returns an {@link AgentResult} whose output is the final {@link ApprovalDecision}.</li>
 * </ol>
 *
 * <h2>Polling Strategy</h2>
 * <p>The agent uses an ActiveJ-compatible, promise-chained polling loop with
 * configurable intervals. All waiting is non-blocking and no threads are held.
 *
 * @doc.type class
 * @doc.purpose TypedAgent that coordinates human-in-the-loop lifecycle approval gates
 * @doc.layer product
 * @doc.pattern Coordinator, TypedAgent
 * @doc.gaa.lifecycle act
 */
public class HumanInTheLoopCoordinatorAgent implements TypedAgent<ApprovalRequest, ApprovalDecision> {

    private static final Logger log = LoggerFactory.getLogger(HumanInTheLoopCoordinatorAgent.class);

    private static final String AGENT_ID   = "human-in-the-loop-coordinator";
    private static final String AGENT_NAME = "Human-In-The-Loop Coordinator";
    private static final String AGENT_ROLE = "coordinator";

    /**
     * Gateway for submitting and polling approval requests.
     * Implementations can be backed by the in-process {@code HumanApprovalService}
     * or an HTTP client pointing at the lifecycle service REST API.
     */
    public interface ApprovalGateway {

        /**
         * Submits a new approval request and returns the created request ID.
         *
         * @param request the request to submit
         * @return Promise of the created request's ID
         */
        Promise<String> submit(ApprovalRequest request);

        /**
         * Polls for a decision on a previously submitted request.
         *
         * @param tenantId  tenant context
         * @param requestId the ID returned by {@link #submit}
         * @return Promise of the decision, or {@code null} when still pending
         */
        Promise<ApprovalDecision> poll(String tenantId, String requestId);
    }

    /** Controls the polling behaviour. */
    public record PollingConfig(
            long intervalMs,
            int maxAttempts
    ) {
        /** Default: 5-second interval, up to 720 polls (one hour). */
        public static PollingConfig defaultConfig() {
            return new PollingConfig(5_000L, 720);
        }
    }

    // ─── Fields ───────────────────────────────────────────────────────────────

    private final AgentDescriptor descriptor;
    private final ApprovalGateway  gateway;
    private final PollingConfig    pollingConfig;

    /**
     * @param gateway       the gateway used to submit and poll requests
     * @param pollingConfig polling interval and max-attempt parameters
     */
    public HumanInTheLoopCoordinatorAgent(ApprovalGateway gateway, PollingConfig pollingConfig) {
        this.gateway       = Objects.requireNonNull(gateway, "gateway must not be null");
        this.pollingConfig = Objects.requireNonNull(pollingConfig, "pollingConfig must not be null");
        this.descriptor    = AgentDescriptor.builder()
                .agentId(AGENT_ID)
                .name(AGENT_NAME)
                .description(AGENT_ROLE)
                .capabilities(Set.of("human_approval", "gate_coordination"))
                .build();
    }

    /** Convenience constructor using the default polling config. */
    public HumanInTheLoopCoordinatorAgent(ApprovalGateway gateway) {
        this(gateway, PollingConfig.defaultConfig());
    }

    // ─── TypedAgent contract ──────────────────────────────────────────────────

    @Override
    public AgentDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public @NotNull Promise<Void> initialize(@NotNull AgentConfig config) {
        return Promise.complete();
    }

    @Override
    public @NotNull Promise<Void> shutdown() {
        return Promise.complete();
    }

    @Override
    public @NotNull Promise<HealthStatus> healthCheck() {
        return Promise.of(HealthStatus.HEALTHY);
    }

    /**
     * Submits the approval request and polls for a decision.
     *
     * <p>Returns a {@link AgentResult} with:
     * <ul>
     *   <li>An {@link ApprovalDecision} with {@code approved=true} when approved.</li>
     *   <li>An {@link ApprovalDecision} with {@code approved=false} when rejected.</li>
     *   <li>A failed {@link AgentResult} when the polling limit is exceeded (expired).</li>
     * </ul>
     */
    @Override
    public Promise<AgentResult<ApprovalDecision>> process(AgentContext context, ApprovalRequest input) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(input,   "input must not be null");

        long startMs = System.currentTimeMillis();
        log.info("[agent={}] Submitting approval request for project={} type={}",
                AGENT_ID, input.projectId(), input.approvalType());

        return gateway.submit(input)
                .then(requestId -> pollForDecision(input, requestId, context, startMs, 0));
    }

    // ─── Polling loop ─────────────────────────────────────────────────────────

    private Promise<AgentResult<ApprovalDecision>> pollForDecision(
            ApprovalRequest original,
            String requestId,
            AgentContext context,
            long startMs,
            int attempt) {

        if (attempt >= pollingConfig.maxAttempts()) {
            log.warn("[agent={}] Approval timed out after {} attempts for request={}",
                    AGENT_ID, attempt, requestId);
            long elapsed = System.currentTimeMillis() - startMs;
            return Promise.of(AgentResult.failure(
                    new IllegalStateException("Approval request timed out: " + requestId),
                    AGENT_ID,
                    java.time.Duration.ofMillis(elapsed)));
        }

        return gateway.poll(original.tenantId(), requestId)
                .then(decision -> {
                    if (decision != null) {
                        long elapsed = System.currentTimeMillis() - startMs;
                        log.info("[agent={}] Approval {} for request={} by={} elapsed={}ms",
                                AGENT_ID,
                                decision.approved() ? "APPROVED" : "REJECTED",
                                requestId,
                                decision.decidedBy(),
                                elapsed);
                        return Promise.of(AgentResult.success(decision, AGENT_ID,
                                java.time.Duration.ofMillis(elapsed)));
                    }

                    // Still pending — schedule next poll (non-blocking, but simplified here)
                    // In a real ActiveJ context, wrap in Eventloop.scheduleBackground.
                    // For testing, a recursive promise chain works.
                    return Promises.delay(pollingConfig.intervalMs())
                            .then(v -> pollForDecision(original, requestId, context, startMs, attempt + 1));
                });
    }

    // ─── Factory helpers ──────────────────────────────────────────────────────

    /**
     * Creates an agent backed by an in-process {@link DirectApprovalGateway}.
     * Used when the lifecycle service's {@code HumanApprovalService} is available in-process.
     *
     * @param submitFn  function to submit a request, returning its ID
     * @param pollFn    function to poll for a decision; returns {@code null} if still pending
     */
    public static HumanInTheLoopCoordinatorAgent of(
            Function<ApprovalRequest, Promise<String>> submitFn,
            java.util.function.BiFunction<String, String, Promise<ApprovalDecision>> pollFn) {

        ApprovalGateway gateway = new ApprovalGateway() {
            @Override
            public Promise<String> submit(ApprovalRequest request) {
                return submitFn.apply(request);
            }

            @Override
            public Promise<ApprovalDecision> poll(String tenantId, String requestId) {
                return pollFn.apply(tenantId, requestId);
            }
        };
        return new HumanInTheLoopCoordinatorAgent(gateway);
    }

    /**
     * Inner gateway implementation that delegates directly to lambda functions.
     * Here for documentation completeness.
     */
    private record DirectApprovalGateway(
            Function<ApprovalRequest, Promise<String>> submitFn,
            java.util.function.BiFunction<String, String, Promise<ApprovalDecision>> pollFn
    ) implements ApprovalGateway {

        @Override
        public Promise<String> submit(ApprovalRequest request) {
            return submitFn.apply(request);
        }

        @Override
        public Promise<ApprovalDecision> poll(String tenantId, String requestId) {
            return pollFn.apply(tenantId, requestId);
        }
    }
}
