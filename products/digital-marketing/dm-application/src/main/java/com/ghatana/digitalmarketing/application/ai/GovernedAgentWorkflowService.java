package com.ghatana.digitalmarketing.application.ai;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.domain.ai.AiProvenance;
import com.ghatana.digitalmarketing.domain.transparency.AiActionLogEntry;
import com.ghatana.digitalmarketing.application.transparency.AiActionLogRepository;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * P0-007: Service for governed agent workflows with end-to-end AI governance.
 *
 * <p>Wraps the agent orchestration port with comprehensive governance features:
 * <ul>
 *   <li>Pre-execution policy checks on prompts and parameters</li>
 *   <li>AI action logging for audit trail with provenance</li>
 *   <li>Confidence/risk/evidence tracking</li>
 *   <li>Post-execution policy checks on outputs</li>
 *   <li>Approval routing for risky outputs</li>
 *   <li>Deterministic fallback on failures</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Governed agent workflows with end-to-end AI governance (P0-007)
 * @doc.layer application
 * @doc.pattern Service, Governance
 */
public final class GovernedAgentWorkflowService {

    private static final Logger logger = LoggerFactory.getLogger(GovernedAgentWorkflowService.class);
    private static final double HIGH_RISK_THRESHOLD = 0.7;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final DmAgentOrchestrationPort agentPort;
    private final AiActionLogRepository aiActionLogRepository;
    private final AiPolicyCheckService policyCheckService;

    public GovernedAgentWorkflowService(
            DmAgentOrchestrationPort agentPort,
            AiActionLogRepository aiActionLogRepository,
            AiPolicyCheckService policyCheckService) {
        this.agentPort = Objects.requireNonNull(agentPort, "agentPort must not be null");
        this.aiActionLogRepository = Objects.requireNonNull(aiActionLogRepository, "aiActionLogRepository must not be null");
        this.policyCheckService = Objects.requireNonNull(policyCheckService, "policyCheckService must not be null");
    }

    /**
     * P0-007: Executes a governed agent workflow with end-to-end policy checks and logging.
     *
     * <p>Governance flow:
     * <ol>
     *   <li>Pre-execution policy check on prompt and parameters</li>
     *   <li>Execute agent via orchestration port</li>
     *   <li>Post-execution policy check on output</li>
     *   <li>Evidence validation</li>
     *   <li>Unsafe claim detection</li>
     *   <li>Determine approval requirement</li>
     *   <li>Log AI action for audit trail with provenance</li>
     * </ol>
     *
     * @param ctx operation context for authorization and audit
     * @param agentType the type of agent to invoke
     * @param prompt the prompt to send to the agent
     * @param model the model to use
     * @param parameters additional parameters
     * @return the governed workflow result with policy check results
     */
    public Promise<GovernedWorkflowResult> executeGovernedWorkflow(
        DmOperationContext ctx,
        DmAgentOrchestrationPort.AgentType agentType,
        String prompt,
        String model,
        Map<String, Object> parameters
    ) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(prompt, "prompt must not be null");
        Objects.requireNonNull(model, "model must not be null");

        DmOperationContext policyCtx = ctx;

        // P0-007: Pre-execution policy check
        AiProvenance preExecutionProvenance = AiProvenance.builder()
            .provenanceId(UUID.randomUUID().toString())
            .modelProvider("dmos-agent-orchestration")
            .modelName(model)
            .modelVersion(model)
            .promptVersion("v1.0")
            .confidenceScore(1.0) // Pre-execution assumes high confidence
            .invokedAt(Instant.now())
            .invokedBy(ctx.getActor().getPrincipalId())
            .correlationId(ctx.getCorrelationId().getValue())
            .build();

        AiPolicyCheckService.AiOutputType outputType = mapAgentTypeToOutputType(agentType);

        return policyCheckService.checkPolicy(policyCtx, preExecutionProvenance, prompt, outputType)
            .then(preCheckResult -> {
                if (!preCheckResult.passed()) {
                    logger.warn("[DMOS-AI-GOVERNANCE] Pre-execution policy check failed: {}", preCheckResult.failureReason());
                    return Promise.of(new GovernedWorkflowResult(
                        null, model, 0.0, null, Duration.ZERO, false,
                        "Pre-execution policy check failed: " + preCheckResult.failureReason(),
                        AiPolicyCheckService.ApprovalRequirement.BLOCKED, null, preCheckResult, null, null
                    ));
                }

                // Execute agent
                return agentPort.invokeAgent(agentType, prompt, model, parameters, DEFAULT_TIMEOUT)
                    .then(response -> {
                        // P0-007: Create post-execution provenance
                        AiProvenance postExecutionProvenance = AiProvenance.builder()
                            .provenanceId(UUID.randomUUID().toString())
                            .modelProvider("dmos-agent-orchestration")
                            .modelName(response.model())
                            .modelVersion(response.model())
                            .promptVersion("v1.0")
                            .confidenceScore(response.confidence())
                            .invokedAt(Instant.now())
                            .invokedBy(ctx.getActor().getPrincipalId())
                            .correlationId(ctx.getCorrelationId().getValue())
                            .build();

                        // P0-007: Post-execution policy checks
                        List<String> evidenceLinks = response.evidenceLocation() != null
                            ? List.of(response.evidenceLocation())
                            : List.of();

                        return policyCheckService.checkPolicy(policyCtx, postExecutionProvenance, response.output(), outputType)
                            .then(policyResult -> policyCheckService.validateEvidence(policyCtx, evidenceLinks, outputType)
                                .then(evidenceResult -> policyCheckService.checkUnsafeClaims(policyCtx, response.output())
                                    .then(unsafeClaimResult -> {
                                        AiPolicyCheckService.ApprovalRequirement approvalRequirement =
                                            policyCheckService.determineApprovalRequirement(
                                                policyResult, evidenceResult, unsafeClaimResult);

                                        AiActionLogEntry logEntry = createLogEntry(
                                            agentType, prompt, model, response, ctx, policyResult, evidenceResult, unsafeClaimResult
                                        );

                                        return aiActionLogRepository.save(logEntry)
                                            .map(savedLog -> new GovernedWorkflowResult(
                                                response.output(),
                                                response.model(),
                                                response.confidence(),
                                                response.evidenceLocation(),
                                                response.duration(),
                                                response.success(),
                                                response.errorMessage(),
                                                approvalRequirement,
                                                savedLog.actionId(),
                                                policyResult,
                                                evidenceResult,
                                                unsafeClaimResult
                                            ));
                                    })));
                    });
            });
    }

    /**
     * P0-007: Maps agent type to AI output type for policy checks.
     */
    private AiPolicyCheckService.AiOutputType mapAgentTypeToOutputType(DmAgentOrchestrationPort.AgentType agentType) {
        return switch (agentType) {
            case STRATEGY_GENERATOR, PROPOSAL_SOW_GENERATOR -> AiPolicyCheckService.AiOutputType.STRATEGY;
            case AD_COPY_GENERATOR, LANDING_PAGE_GENERATOR, EMAIL_FOLLOW_UP_GENERATOR -> AiPolicyCheckService.AiOutputType.CONTENT;
            case REPORT_NARRATIVE_GENERATOR -> AiPolicyCheckService.AiOutputType.ANALYTICS;
            case RECOMMENDATION_ENGINE -> AiPolicyCheckService.AiOutputType.RECOMMENDATION;
        };
    }

    /**
     * P0-007: Creates an AI action log entry for audit trail with policy check results.
     */
    private AiActionLogEntry createLogEntry(
        DmAgentOrchestrationPort.AgentType agentType,
        String prompt,
        String model,
        DmAgentOrchestrationPort.AgentResponse response,
        DmOperationContext ctx,
        AiPolicyCheckService.PolicyCheckResult policyResult,
        AiPolicyCheckService.EvidenceValidationResult evidenceResult,
        AiPolicyCheckService.UnsafeClaimCheckResult unsafeClaimResult
    ) {
        String correlationId = ctx.getCorrelationId().getValue();
        List<String> evidenceLinks = response.evidenceLocation() != null
            ? List.of(response.evidenceLocation())
            : List.of();
        
        // P0-007: Include policy check results in the log entry
        List<String> policyChecks = new java.util.ArrayList<>();
        policyChecks.add("Policy check passed: " + policyResult.passed());
        policyChecks.add("Evidence sufficient: " + evidenceResult.sufficient());
        policyChecks.add("Unsafe claims detected: " + unsafeClaimResult.hasUnsafeClaims());
        if (!policyResult.warnings().isEmpty()) {
            policyChecks.addAll(policyResult.warnings());
        }
        
        String details = response.output();
        if (details == null || details.isBlank()) {
            details = response.errorMessage() != null && !response.errorMessage().isBlank()
                ? "Agent execution failed: " + response.errorMessage()
                : "Agent execution completed with no output.";
        }
        
        return new AiActionLogEntry(
            UUID.randomUUID().toString(),
            ctx.getWorkspaceId().getValue(),
            correlationId,
            com.ghatana.digitalmarketing.domain.transparency.AiActionType.ACTION_EXECUTED,
            com.ghatana.digitalmarketing.domain.transparency.AiActionStatus.EXECUTED,
            ctx.getActor().getPrincipalId(),
            response.success(),
            "dmos-agent-orchestration",
            model,
            false,
            response.confidence(),
            evidenceLinks,
            policyChecks,
            "Agent execution: " + agentType.name(),
            details,
            null,
            Instant.now(),
            0L
        );
    }

    /**
     * P0-007: Result of a governed agent workflow with comprehensive policy check results.
     */
    public record GovernedWorkflowResult(
        String output,
        String model,
        double confidence,
        String evidenceLocation,
        Duration duration,
        boolean success,
        String errorMessage,
        AiPolicyCheckService.ApprovalRequirement approvalRequirement,
        String logEntryId,
        AiPolicyCheckService.PolicyCheckResult policyCheckResult,
        AiPolicyCheckService.EvidenceValidationResult evidenceValidationResult,
        AiPolicyCheckService.UnsafeClaimCheckResult unsafeClaimCheckResult
    ) {}
}
