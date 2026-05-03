package com.ghatana.digitalmarketing.application.ai;

import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.transparency.AiActionLogEntry;
import com.ghatana.digitalmarketing.application.transparency.AiActionLogRepository;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Service for governed agent workflows (DMOS-P1-019).
 *
 * <p>Wraps the agent orchestration port with governance features:
 * - AI action logging for audit trail
 * - Confidence/risk/evidence tracking
 * - Approval routing for risky outputs
 * - Deterministic fallback</p>
 *
 * @doc.type class
 * @doc.purpose Governed agent workflows with logging and approval routing (DMOS-P1-019)
 * @doc.layer application
 * @doc.pattern Service
 */
public final class GovernedAgentWorkflowService {

    private static final Logger logger = LoggerFactory.getLogger(GovernedAgentWorkflowService.class);
    private static final double HIGH_RISK_THRESHOLD = 0.7;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final DmAgentOrchestrationPort agentPort;
    private final AiActionLogRepository aiActionLogRepository;

    public GovernedAgentWorkflowService(DmAgentOrchestrationPort agentPort, AiActionLogRepository aiActionLogRepository) {
        this.agentPort = agentPort;
        this.aiActionLogRepository = aiActionLogRepository;
    }

    /**
     * Executes a governed agent workflow with logging and approval routing.
     *
     * @param agentType the type of agent to invoke
     * @param prompt the prompt to send to the agent
     * @param model the model to use
     * @param parameters additional parameters
     * @param tenantId the tenant ID
     * @param workspaceId the workspace ID
     * @param principalId the principal ID
     * @return the governed workflow result
     */
    public Promise<GovernedWorkflowResult> executeGovernedWorkflow(
        DmAgentOrchestrationPort.AgentType agentType,
        String prompt,
        String model,
        Map<String, Object> parameters,
        DmTenantId tenantId,
        DmWorkspaceId workspaceId,
        String principalId
    ) {
        return agentPort.invokeAgent(agentType, prompt, model, parameters, DEFAULT_TIMEOUT)
            .then(response -> {
                // Log AI action for audit trail (DMOS-P1-019)
                AiActionLogEntry logEntry = createLogEntry(
                    agentType,
                    prompt,
                    model,
                    response,
                    tenantId,
                    workspaceId,
                    principalId
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
                        determineApprovalRequired(response),
                        savedLog.actionId()
                    ));
            });
    }

    /**
     * Determines if approval is required based on confidence and risk level.
     */
    private boolean determineApprovalRequired(DmAgentOrchestrationPort.AgentResponse response) {
        if (!response.success()) {
            return false; // Failed responses don't need approval
        }
        // High-risk outputs require approval (DMOS-P1-019)
        return response.confidence() < HIGH_RISK_THRESHOLD;
    }

    /**
     * Creates an AI action log entry for audit trail.
     */
    private AiActionLogEntry createLogEntry(
        DmAgentOrchestrationPort.AgentType agentType,
        String prompt,
        String model,
        DmAgentOrchestrationPort.AgentResponse response,
        DmTenantId tenantId,
        DmWorkspaceId workspaceId,
        String principalId
    ) {
        String correlationId = "corr-" + java.util.UUID.randomUUID().toString();
        List<String> evidenceLinks = response.evidenceLocation() != null 
            ? List.of(response.evidenceLocation()) 
            : List.of();
        
        return new AiActionLogEntry(
            java.util.UUID.randomUUID().toString(),
            workspaceId.getValue(),
            correlationId,
            com.ghatana.digitalmarketing.domain.transparency.AiActionType.ACTION_EXECUTED,
            com.ghatana.digitalmarketing.domain.transparency.AiActionStatus.EXECUTED,
            principalId,
            response.success(),
            response.confidence(),
            evidenceLinks,
            List.of(),
            "Agent execution: " + agentType.name(),
            response.output(),
            null,
            Instant.now(),
            0L
        );
    }

    /**
     * Result of a governed agent workflow (DMOS-P1-019).
     */
    public record GovernedWorkflowResult(
        String output,
        String model,
        double confidence,
        String evidenceLocation,
        Duration duration,
        boolean success,
        String errorMessage,
        boolean approvalRequired,
        String logEntryId
    ) {}
}
