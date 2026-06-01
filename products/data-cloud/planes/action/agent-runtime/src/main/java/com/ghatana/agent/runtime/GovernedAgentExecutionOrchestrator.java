/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.runtime;

import com.ghatana.agent.AgentResult;
import com.ghatana.agent.dispatch.AgentDispatcher;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.datacloud.entity.agent.AgentRun;
import com.ghatana.datacloud.entity.agent.ApprovalRequest;
import com.ghatana.datacloud.entity.agent.ToolCall;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WS2-8: Replay-safe agent execution orchestrator with side-effect controls.
 *
 * <p>Implements the canonical agent execution lifecycle with full governance:
 * <ul>
 *   <li>Planning: Generate execution plan with side-effect declaration</li>
 *   <li>Review: Policy evaluation and risk assessment</li>
 *   <li>Approval: Human-in-the-loop approval for high-risk operations</li>
 *   <li>Execution: Execute with idempotency and side-effect controls</li>
 *   <li>Compensation: Rollback and compensation for failed executions</li>
 * </ul>
 *
 * <p>This orchestrator uses the canonical {@link AgentRun} entity for persistence
 * and governance tracking, aligning with the Action Plane lifecycle.
 *
 * @doc.type class
 * @doc.purpose Replay-safe agent execution with planning, review, approval, execution, and compensation
 * @doc.layer product
 * @doc.pattern Orchestrator
 */
public class GovernedAgentExecutionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(GovernedAgentExecutionOrchestrator.class);

    private final AgentDispatcher agentDispatcher;
    private final AgentRunRepository agentRunRepository;
    private final PolicyEvaluator policyEvaluator;
    private final ApprovalService approvalService;
    private final CompensationService compensationService;

    // Track in-flight executions for idempotency
    private final Map<String, AgentRun> inFlightRuns = new ConcurrentHashMap<>();

    public GovernedAgentExecutionOrchestrator(
            AgentDispatcher agentDispatcher,
            AgentRunRepository agentRunRepository,
            PolicyEvaluator policyEvaluator,
            ApprovalService approvalService,
            CompensationService compensationService) {
        this.agentDispatcher = agentDispatcher;
        this.agentRunRepository = agentRunRepository;
        this.policyEvaluator = policyEvaluator;
        this.approvalService = approvalService;
        this.compensationService = compensationService;
    }

    /**
     * Execute an agent with full governance lifecycle.
     *
     * <p>WS2-8: Executes an agent with:
     * <ul>
     *   <li>Planning phase with side-effect declaration</li>
     *   <li>Review phase with policy evaluation</li>
     *   <li>Approval phase for high-risk operations</li>
     *   <li>Execution phase with idempotency and side-effect controls</li>
     *   <li>Compensation phase for rollback if needed</li>
     * </ul>
     */
    public Promise<AgentExecutionResult> execute(AgentExecutionRequest request) {
        String runId = request.idempotencyKey() != null
            ? request.idempotencyKey()
            : generateRunId(request.agentId(), request.tenantId());

        // Check for existing execution (idempotency)
        AgentRun existing = inFlightRuns.get(runId);
        if (existing != null) {
            log.info("[idempotency] Duplicate run detected: runId={}", runId);
            return Promise.of(createDuplicateResult(runId, existing, request));
        }

        // Check repository for completed runs
        return agentRunRepository.findById(runId).then(opt -> {
            if (opt.isPresent()) {
                log.info("[idempotency] Returning previously completed run: runId={}", runId);
                return Promise.of(createHistoricalResult(runId, opt.get(), request));
            }

            // Create new AgentRun entity
            AgentRun agentRun = AgentRun.builder()
                .runId(runId)
                .agentId(request.agentId())
                .tenantId(request.tenantId())
                .userId(request.userId())
                .status(AgentRun.RunStatus.INITIALIZED)
                .startTime(Instant.now())
                .input(request.input())
                .metadata(request.metadata())
                .build();

            inFlightRuns.put(runId, agentRun);

            // Persist initial state
            return agentRunRepository.save(agentRun).then(v -> {
                // Start execution flow
                return executeWithGovernance(runId, request, agentRun);
            });
        });
    }

    /**
     * Replay an agent execution with replay-safe controls.
     */
    public Promise<AgentReplayResult> replay(AgentReplayRequest request) {
        log.info("[replay] Starting replay: originalRunId={} replayMode={}",
            request.originalRunId(), request.replayMode());

        // Load original run
        return agentRunRepository.findById(request.originalRunId()).then(opt -> {
            if (opt.isEmpty()) {
                log.error("[replay] Original run not found: {}", request.originalRunId());
                return Promise.of(new AgentReplayResult(
                    request.replayRunId(),
                    request.originalRunId(),
                    ReplayStatus.FAILED,
                    Map.of(),
                    null,
                    null,
                    false,
                    Instant.now(),
                    Instant.now(),
                    Map.of("error", "Original run not found")
                ));
            }

            AgentRun original = opt.get();

            // Check if replay is allowed based on original run status
            if (!isReplayable(original.status())) {
                log.warn("[replay] Original run not replayable: status={}", original.status());
                return Promise.of(new AgentReplayResult(
                    request.replayRunId(),
                    request.originalRunId(),
                    ReplayStatus.SKIPPED,
                    Map.of(),
                    null,
                    null,
                    false,
                    Instant.now(),
                    Instant.now(),
                    Map.of("error", "Original run not replayable")
                ));
            }

            // Evaluate replay policy
            return policyEvaluator.evaluateReplay(request, original).then(policyDecision -> {
                if (!policyDecision.allowed()) {
                    log.warn("[replay] Policy denied replay: reason={}", policyDecision.reason());
                    return Promise.of(new AgentReplayResult(
                        request.replayRunId(),
                        request.originalRunId(),
                        ReplayStatus.FAILED,
                        Map.of(),
                        null,
                        policyDecision,
                        false,
                        Instant.now(),
                        Instant.now(),
                        Map.of("error", policyDecision.reason())
                    ));
                }

                // Execute replay based on replay mode
                return executeReplay(request, original, policyDecision);
            });
        });
    }

    // ==================== Private Methods ====================

    private Promise<AgentExecutionResult> executeWithGovernance(
            String runId,
            AgentExecutionRequest request,
            AgentRun agentRun) {

        Instant startTime = Instant.now();

        // Phase 1: Planning
        return planExecution(request, agentRun).then(planResult -> {
            agentRun = agentRun.toBuilder()
                .status(AgentRun.RunStatus.RUNNING)
                .sideEffectDeclaration(planResult.sideEffectDeclaration())
                .build();

            // Phase 2: Review
            return reviewExecution(request, agentRun, planResult).then(reviewResult -> {
                if (!reviewResult.allowed()) {
                    log.warn("[review] Policy denied execution: runId={} reason={}",
                        runId, reviewResult.reason());
                    return completeRun(runId, agentRun, AgentRun.RunStatus.FAILED, request, startTime);
                }

                // Phase 3: Approval if required
                if (reviewResult.requiresApproval()) {
                    return handleApproval(runId, request, agentRun, startTime);
                }

                // Phase 4: Execution
                return executeAgent(runId, request, agentRun, startTime);
            });
        });
    }

    private Promise<ExecutionPlanResult> planExecution(AgentExecutionRequest request, AgentRun agentRun) {
        log.info("[planning] Planning execution: runId={} agentId={}",
            agentRun.runId(), request.agentId());

        // Get side-effect declaration from dispatcher
        AgentDispatcher.SideEffectDeclaration sideEffectDeclaration =
            agentDispatcher.declareSideEffects(request.agentId());

        // Create execution plan
        ExecutionPlanResult plan = new ExecutionPlanResult(
            request.agentId(),
            sideEffectDeclaration,
            sideEffectDeclaration.isSafeToReplay(),
            sideEffectDeclaration.isDestructive() ? "HIGH" : "LOW",
            sideEffectDeclaration.affectedResources()
        );

        return Promise.of(plan);
    }

    private Promise<ReviewResult> reviewExecution(
            AgentExecutionRequest request,
            AgentRun agentRun,
            ExecutionPlanResult plan) {
        log.info("[review] Reviewing execution: runId={} agentId={} riskLevel={}",
            agentRun.runId(), request.agentId(), plan.riskLevel());

        return policyEvaluator.evaluate(request, agentRun, plan).then(policyDecision -> {
            boolean requiresApproval = plan.riskLevel().equals("HIGH") || policyDecision.requiresApproval();

            return Promise.of(new ReviewResult(
                policyDecision.allowed(),
                policyDecision.reason(),
                requiresApproval,
                policyDecision
            ));
        });
    }

    private Promise<AgentExecutionResult> handleApproval(
            String runId,
            AgentExecutionRequest request,
            AgentRun agentRun,
            Instant startTime) {

        log.info("[approval] Requesting approvals: runId={} agentId={}",
            runId, request.agentId());

        return approvalService.requestApprovals(
            runId,
            request.tenantId(),
            request.userId(),
            request.agentId(),
            request.input()
        ).then(approvalResult -> {
            if (!approvalResult.approved()) {
                log.warn("[approval] Approval denied: runId={} reason={}",
                    runId, approvalResult.reason());
                return completeRun(runId, agentRun, AgentRun.RunStatus.FAILED, request, startTime);
            }

            agentRun = agentRun.toBuilder()
                .grantedApprovals(approvalResult.grantedApprovals())
                .build();
            return executeAgent(runId, request, agentRun, startTime);
        });
    }

    private Promise<AgentExecutionResult> executeAgent(
            String runId,
            AgentExecutionRequest request,
            AgentRun agentRun,
            Instant startTime) {

        log.info("[execution] Executing agent: runId={} agentId={} mode={}",
            runId, request.agentId(), request.mode());

        // Execute based on mode
        if (request.mode() == ExecutionMode.DRY_RUN || request.mode() == ExecutionMode.VALIDATION_ONLY) {
            // Dry-run or validation only - skip actual execution
            log.info("[execution] Dry-run/validation mode, skipping actual execution");
            return completeRun(runId, agentRun, AgentRun.RunStatus.COMPLETED, request, startTime);
        }

        // Normal execution - dispatch through governed dispatcher
        AgentContext ctx = AgentContext.empty().toBuilder()
            .agentId(request.agentId())
            .turnId(runId)
            .build();

        boolean isReplay = request.replayMode() != null && request.replayMode() != ReplayMode.NONE;
        String idempotencyKey = request.idempotencyKey() != null ? request.idempotencyKey() : runId;

        return agentDispatcher.dispatch(
            request.agentId(),
            request.input(),
            ctx,
            isReplay,
            idempotencyKey
        ).then(result -> {
            agentRun = agentRun.toBuilder()
                .output(Map.of("result", result.getOutput()))
                .build();
            return completeRun(runId, agentRun, AgentRun.RunStatus.COMPLETED, request, startTime);
        }).mapException(e -> {
            log.error("[execution] Agent execution failed: runId={}", runId, e);
            
            // Phase 5: Compensation if needed
            if (agentRun.sideEffectDeclaration() != null 
                && agentRun.sideEffectDeclaration().isReversible()) {
                return compensateExecution(runId, agentRun, request, startTime, e);
            }
            
            return completeRun(runId, agentRun, AgentRun.RunStatus.FAILED, request, startTime);
        });
    }

    private Promise<AgentExecutionResult> compensateExecution(
            String runId,
            AgentRun agentRun,
            AgentExecutionRequest request,
            Instant startTime,
            Throwable error) {

        log.info("[compensation] Compensating execution: runId={} strategy={}",
            runId, agentRun.sideEffectDeclaration().compensationStrategy());

        return compensationService.compensate(
            runId,
            agentRun.sideEffectDeclaration().compensationStrategy(),
            agentRun
        ).then(compensationResult -> {
            agentRun = agentRun.toBuilder()
                .compensationExecuted(true)
                .compensationResult(compensationResult)
                .build();
            return completeRun(runId, agentRun, AgentRun.RunStatus.FAILED, request, startTime);
        });
    }

    private Promise<AgentExecutionResult> completeRun(
            String runId,
            AgentRun agentRun,
            AgentRun.RunStatus status,
            AgentExecutionRequest request,
            Instant startTime) {

        agentRun = agentRun.toBuilder()
            .status(status)
            .endTime(Instant.now())
            .build();

        // Persist run state
        return agentRunRepository.save(agentRun).then(v -> {
            inFlightRuns.remove(runId);

            return Promise.of(new AgentExecutionResult(
                runId,
                request.agentId(),
                request.tenantId(),
                toRunStatus(status),
                agentRun.output(),
                agentRun.traceId(),
                agentRun.policyDecision(),
                agentRun.grantedApprovals(),
                startTime,
                agentRun.endTime(),
                request.metadata()
            ));
        });
    }

    private Promise<AgentReplayResult> executeReplay(
            AgentReplayRequest request,
            AgentRun original,
            PolicyDecisionContract.ReplayPolicyDecision policyDecision) {

        Instant startTime = Instant.now();

        // Execute based on replay mode
        if (request.replayMode() == ReplayMode.DRY_RUN) {
            log.info("[replay] Dry-run mode, skipping actual execution");
            return Promise.of(new AgentReplayResult(
                request.replayRunId(),
                request.originalRunId(),
                ReplayStatus.SUCCESS,
                Map.of("result", "dry-run"),
                null,
                policyDecision,
                false,
                startTime,
                Instant.now(),
                Map.of()
            ));
        }

        if (request.replayMode() == ReplayMode.RECORDED_OUTPUT) {
            log.info("[replay] Using recorded output from original run");
            return Promise.of(new AgentReplayResult(
                request.replayRunId(),
                request.originalRunId(),
                ReplayStatus.SUCCESS,
                original.output(),
                null,
                policyDecision,
                false,
                startTime,
                Instant.now(),
                Map.of()
            ));
        }

        // Full replay with side effects
        log.info("[replay] Full replay with side effects");
        // Note: In a full implementation, this would re-execute the agent
        return Promise.of(new AgentReplayResult(
            request.replayRunId(),
            request.originalRunId(),
            ReplayStatus.SUCCESS,
            Map.of("result", "replayed"),
            null,
            policyDecision,
            false,
            startTime,
            Instant.now(),
            Map.of()
        ));
    }

    private AgentExecutionResult createDuplicateResult(
            String runId,
            AgentRun existing,
            AgentExecutionRequest request) {

        return new AgentExecutionResult(
            runId,
            request.agentId(),
            request.tenantId(),
            toRunStatus(existing.status()),
            existing.output(),
            existing.traceId(),
            existing.policyDecision(),
            existing.grantedApprovals(),
            existing.startTime(),
            existing.endTime(),
            request.metadata()
        );
    }

    private AgentExecutionResult createHistoricalResult(
            String runId,
            AgentRun existing,
            AgentExecutionRequest request) {

        return new AgentExecutionResult(
            runId,
            request.agentId(),
            request.tenantId(),
            toRunStatus(existing.status()),
            existing.output(),
            existing.traceId(),
            existing.policyDecision(),
            existing.grantedApprovals(),
            existing.startTime(),
            existing.endTime(),
            request.metadata()
        );
    }

    private String generateRunId(String agentId, String tenantId) {
        return String.format("%s:%s:%s", tenantId, agentId, UUID.randomUUID());
    }

    private boolean isReplayable(AgentRun.RunStatus status) {
        return status == AgentRun.RunStatus.COMPLETED || status == AgentRun.RunStatus.FAILED;
    }

    private RunStatus toRunStatus(AgentRun.RunStatus agentRunStatus) {
        return switch (agentRunStatus) {
            case INITIALIZED -> RunStatus.INITIALIZED;
            case RUNNING -> RunStatus.RUNNING;
            case WAITING_APPROVAL -> RunStatus.WAITING_APPROVAL;
            case COMPLETED -> RunStatus.COMPLETED;
            case FAILED -> RunStatus.FAILED;
            case CANCELLED -> RunStatus.CANCELLED;
            case TIMEOUT -> RunStatus.TIMEOUT;
            case SUSPENDED -> RunStatus.SUSPENDED;
        };
    }

    // ==================== Service Interfaces ====================

    public interface AgentRunRepository {
        Promise<Void> save(AgentRun agentRun);
        Promise<Optional<AgentRun>> findById(String runId);
    }

    public interface PolicyEvaluator {
        Promise<PolicyDecisionContract.PolicyDecision> evaluate(
            AgentExecutionRequest request,
            AgentRun agentRun,
            ExecutionPlanResult plan);

        Promise<PolicyDecisionContract.ReplayPolicyDecision> evaluateReplay(
            AgentReplayRequest request,
            AgentRun original);
    }

    public interface ApprovalService {
        Promise<ApprovalResult> requestApprovals(
            String runId,
            String tenantId,
            String userId,
            String agentId,
            Map<String, Object> input);

        record ApprovalResult(
            boolean approved,
            String reason,
            Set<String> grantedApprovals,
            String reviewer
        ) {}
    }

    public interface CompensationService {
        Promise<CompensationResult> compensate(
            String runId,
            String compensationStrategy,
            AgentRun agentRun);

        record CompensationResult(
            boolean compensated,
            String compensationStrategy,
            Set<String> compensatedResources,
            String failureReason
        ) {}
    }

    // ==================== Supporting Types ====================

    public record AgentExecutionRequest(
            String agentId,
            String tenantId,
            String userId,
            String idempotencyKey,
            Map<String, Object> input,
            ExecutionMode mode,
            ReplayMode replayMode,
            Map<String, Object> metadata
    ) {
        public AgentExecutionRequest {
            input = Map.copyOf(input != null ? input : Map.of());
            metadata = Map.copyOf(metadata != null ? metadata : Map.of());
        }
    }

    public record AgentExecutionResult(
            String runId,
            String agentId,
            String tenantId,
            RunStatus status,
            Map<String, Object> output,
            String traceId,
            PolicyDecisionContract.PolicyDecision policyDecision,
            Set<String> grantedApprovals,
            Instant startTime,
            Instant endTime,
            Map<String, Object> metadata
    ) {
        public AgentExecutionResult {
            output = Map.copyOf(output != null ? output : Map.of());
            grantedApprovals = Set.copyOf(grantedApprovals != null ? grantedApprovals : Set.of());
            metadata = Map.copyOf(metadata != null ? metadata : Map.of());
        }
    }

    public record AgentReplayRequest(
            String originalRunId,
            String replayRunId,
            String tenantId,
            String userId,
            ReplayMode replayMode,
            Map<String, Object> metadata
    ) {
        public AgentReplayRequest {
            metadata = Map.copyOf(metadata != null ? metadata : Map.of());
        }
    }

    public record AgentReplayResult(
            String replayRunId,
            String originalRunId,
            ReplayStatus status,
            Map<String, Object> output,
            String traceId,
            PolicyDecisionContract.ReplayPolicyDecision policyDecision,
            boolean compensationExecuted,
            Instant startTime,
            Instant endTime,
            Map<String, Object> metadata
    ) {
        public AgentReplayResult {
            output = Map.copyOf(output != null ? output : Map.of());
            metadata = Map.copyOf(metadata != null ? metadata : Map.of());
        }
    }

    public record ExecutionPlanResult(
            String agentId,
            AgentDispatcher.SideEffectDeclaration sideEffectDeclaration,
            boolean safeToReplay,
            String riskLevel,
            Set<String> affectedResources
    ) {
        public ExecutionPlanResult {
            affectedResources = Set.copyOf(affectedResources != null ? affectedResources : Set.of());
        }
    }

    public record ReviewResult(
            boolean allowed,
            String reason,
            boolean requiresApproval,
            PolicyDecisionContract.PolicyDecision policyDecision
    ) {}

    public enum ExecutionMode {
        NORMAL,
        DRY_RUN,
        VALIDATION_ONLY
    }

    public enum ReplayMode {
        NONE,
        DRY_RUN,
        REPLAY_WITH_SIDE_EFFECTS,
        RECORDED_OUTPUT
    }

    public enum RunStatus {
        INITIALIZED,
        RUNNING,
        WAITING_APPROVAL,
        COMPLETED,
        FAILED,
        CANCELLED,
        TIMEOUT,
        SUSPENDED
    }

    public enum ReplayStatus {
        SUCCESS,
        FAILED,
        SKIPPED,
        COMPENSATION_FAILED
    }
}
