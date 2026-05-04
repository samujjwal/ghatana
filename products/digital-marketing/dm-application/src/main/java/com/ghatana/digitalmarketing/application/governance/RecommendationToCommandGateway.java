package com.ghatana.digitalmarketing.application.governance;

import com.ghatana.digitalmarketing.application.command.DmCommandService;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.command.DmCommand;
import com.ghatana.digitalmarketing.domain.command.DmCommandType;
import com.ghatana.digitalmarketing.domain.transparency.AiActionLogEntry;
import com.ghatana.digitalmarketing.application.transparency.AiActionLogRepository;
import com.ghatana.plugin.compliance.CompliancePlugin;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

import static com.ghatana.digitalmarketing.pack.DmComplianceRuleSetIds.DM_CONNECTOR_EXECUTION_SAFETY;

/**
 * Gateway for converting AI recommendations to governed commands (DMOS-P1-020).
 *
 * <p>Ensures that AI recommendations cannot directly mutate external systems.
 * All side-effecting recommendations must go through this gateway to become governed commands
 * with validation, risk classification, policy checking, approval routing, and audit logging.</p>
 *
 * @doc.type class
 * @doc.purpose Gateway for recommendation-to-command conversion with governance (DMOS-P1-020)
 * @doc.layer application
 * @doc.pattern Gateway
 */
public final class RecommendationToCommandGateway {

    private static final Logger logger = LoggerFactory.getLogger(RecommendationToCommandGateway.class);
    private static final double HIGH_RISK_THRESHOLD = 0.7;

    private final DmCommandService commandService;
    private final AiActionLogRepository aiActionLogRepository;
    private final CompliancePlugin compliancePlugin;

    public RecommendationToCommandGateway(
            DmCommandService commandService,
            AiActionLogRepository aiActionLogRepository,
            CompliancePlugin compliancePlugin) {
        this.commandService = Objects.requireNonNull(commandService, "commandService must not be null");
        this.aiActionLogRepository = Objects.requireNonNull(aiActionLogRepository, "aiActionLogRepository must not be null");
        this.compliancePlugin = Objects.requireNonNull(compliancePlugin, "compliancePlugin must not be null");
    }

    /**
     * Converts an AI recommendation to a governed command (DMOS-P1-020).
     *
     * @param recommendation the AI recommendation
     * @param tenantId the tenant ID
     * @param workspaceId the workspace ID
     * @param principalId the principal ID
     * @return the gateway result
     */
    public Promise<GatewayResult> convertToCommand(
        Recommendation recommendation,
        DmTenantId tenantId,
        DmWorkspaceId workspaceId,
        String principalId
    ) {
        // Step 1: Validate recommendation (DMOS-P1-020)
        ValidationResult validation = validateRecommendation(recommendation);
        if (!validation.valid()) {
            logger.warn("Recommendation validation failed: {}", validation.reason());
            return Promise.of(new GatewayResult(
                null,
                GatewayStatus.BLOCKED,
                validation.reason(),
                null,
                null
            ));
        }

        // Step 2: Classify risk (DMOS-P1-020)
        RiskLevel riskLevel = classifyRisk(recommendation);

        // Step 3: Check policy/compliance (DMOS-P1-020) — async via compliance plugin
        return checkPolicy(recommendation, riskLevel)
            .then(policyCheck -> {
                if (!policyCheck.compliant()) {
                    logger.warn("Policy check failed: {}", policyCheck.reason());
                    return Promise.of(new GatewayResult(
                        null,
                        GatewayStatus.BLOCKED,
                        policyCheck.reason(),
                        null,
                        null
                    ));
                }

                // Step 4: Determine approval requirement (DMOS-P1-020)
                boolean approvalRequired = isApprovalRequired(riskLevel, recommendation);

                // Step 5: Create command only if no approval required or if pre-approved (DMOS-P1-020)
                if (!approvalRequired) {
                    return createCommand(recommendation, tenantId, workspaceId, principalId, riskLevel);
                } else {
                    // Log recommendation as pending approval
                    AiActionLogEntry logEntry = createLogEntry(recommendation, riskLevel, "PROPOSED", tenantId, workspaceId, principalId);
                    return aiActionLogRepository.save(logEntry)
                        .then(saved -> Promise.of(new GatewayResult(
                            null,
                            GatewayStatus.REQUIRES_APPROVAL,
                            "Recommendation requires approval due to " + riskLevel,
                            null,
                            saved.actionId()
                        )));
                }
            });
    }

    /**
     * Processes an approved recommendation by creating the command (DMOS-P1-020).
     */
    public Promise<GatewayResult> processApprovedRecommendation(
        String logEntryId,
        DmTenantId tenantId,
        DmWorkspaceId workspaceId,
        String principalId
    ) {
        return aiActionLogRepository.findById(workspaceId.getValue(), logEntryId)
            .then(optEntry -> {
                if (optEntry.isEmpty()) {
                    logger.warn("Approved recommendation log entry not found: {} in workspace {}",
                        logEntryId, workspaceId.getValue());
                    return Promise.of(new GatewayResult(
                        null,
                        GatewayStatus.BLOCKED,
                        "Approved recommendation log entry not found: " + logEntryId,
                        null,
                        logEntryId
                    ));
                }
                AiActionLogEntry entry = optEntry.get();
                Recommendation recommendation = reconstructRecommendation(entry);
                RiskLevel riskLevel = classifyRisk(recommendation);
                return createCommand(recommendation, tenantId, workspaceId, principalId, riskLevel)
                    .map(result -> new GatewayResult(
                        result.command(),
                        GatewayStatus.PROCESSED,
                        "Recommendation processed from approval and command created",
                        result.commandId(),
                        logEntryId
                    ));
            });
    }

    /**
     * Validates the recommendation (DMOS-P1-020).
     */
    private ValidationResult validateRecommendation(Recommendation recommendation) {
        if (recommendation == null) {
            return new ValidationResult(false, "Recommendation is null");
        }
        if (recommendation.targetType() == null) {
            return new ValidationResult(false, "Target type is required");
        }
        if (recommendation.targetId() == null || recommendation.targetId().isBlank()) {
            return new ValidationResult(false, "Target ID is required");
        }
        return new ValidationResult(true, null);
    }

    /**
     * Classifies the risk level of the recommendation (DMOS-P1-020).
     */
    private RiskLevel classifyRisk(Recommendation recommendation) {
        if (recommendation.confidence() < HIGH_RISK_THRESHOLD) {
            return RiskLevel.HIGH;
        }
        if (recommendation.confidence() < 0.85) {
            return RiskLevel.MEDIUM;
        }
        return RiskLevel.LOW;
    }

    /**
     * Checks policy/compliance (DMOS-P1-020).
     */
    private Promise<PolicyCheckResult> checkPolicy(Recommendation recommendation, RiskLevel riskLevel) {
        CompliancePlugin.ComplianceContext complianceCtx = new CompliancePlugin.ComplianceContext(
            recommendation.targetId(),
            recommendation.targetType(),
            Map.of(
                "confidence", recommendation.confidence(),
                "riskLevel", riskLevel.name(),
                "agentType", recommendation.agentType(),
                "model", recommendation.model()
            ),
            "recommendation-gateway",
            Instant.now()
        );
        return compliancePlugin.evaluate(DM_CONNECTOR_EXECUTION_SAFETY, complianceCtx)
            .map(result -> {
                if (!result.compliant()) {
                    String reason = "Policy check failed for " + recommendation.targetType()
                        + ": " + result.violations();
                    logger.warn("[DMOS] {}", reason);
                    return new PolicyCheckResult(false, reason);
                }
                return new PolicyCheckResult(true, null);
            });
    }

    /**
     * Determines if approval is required (DMOS-P1-020).
     */
    private boolean isApprovalRequired(RiskLevel riskLevel, Recommendation recommendation) {
        return riskLevel == RiskLevel.HIGH;
    }

    /**
     * Creates a command from the recommendation (DMOS-P1-020).
     */
    private Promise<GatewayResult> createCommand(
        Recommendation recommendation,
        DmTenantId tenantId,
        DmWorkspaceId workspaceId,
        String principalId,
        RiskLevel riskLevel
    ) {
        DmCommandType commandType = resolveCommandType(recommendation.targetType());
        String payload = buildCommandPayload(recommendation);

        DmOperationContext ctx = DmOperationContext.builder()
            .tenantId(tenantId)
            .workspaceId(workspaceId)
            .actor(ActorRef.user(principalId))
            .correlationId(DmCorrelationId.generate())
            .idempotencyKey(DmIdempotencyKey.forCommand(commandType.name(), recommendation.targetId()))
            .build();

        DmCommandService.IssueCommandRequest issueRequest =
            new DmCommandService.IssueCommandRequest(commandType, payload);

        return commandService.issue(ctx, issueRequest)
            .then(command -> {
                AiActionLogEntry logEntry = createLogEntry(
                    recommendation, riskLevel, "EXECUTED", tenantId, workspaceId, principalId);
                return aiActionLogRepository.save(logEntry)
                    .then(saved -> Promise.of(new GatewayResult(
                        command,
                        GatewayStatus.CREATED,
                        "Command created from recommendation: " + command.getId(),
                        command.getId(),
                        saved.actionId()
                    )));
            });
    }

    private DmCommandType resolveCommandType(String targetType) {
        String upper = targetType.toUpperCase();
        if (upper.contains("CAMPAIGN")) {
            return DmCommandType.CAMPAIGN_CREATE;
        }
        if (upper.contains("BUDGET")) {
            return DmCommandType.BUDGET_ADJUST;
        }
        if (upper.contains("LANDING_PAGE") || upper.contains("LANDINGPAGE")) {
            return DmCommandType.LANDING_PAGE_PUBLISH;
        }
        if (upper.contains("EMAIL")) {
            return DmCommandType.EMAIL_SEND;
        }
        if (upper.contains("GOOGLE_ADS") || upper.contains("GOOGLEADS")) {
            return DmCommandType.GOOGLE_ADS_CAMPAIGN_CREATE;
        }
        if (upper.contains("CONNECTOR")) {
            return DmCommandType.CONNECTOR_OAUTH_CONNECT;
        }
        return DmCommandType.AUDIT_RECORD;
    }

    private String buildCommandPayload(Recommendation recommendation) {
        return "{"
            + "\"targetType\":\"" + recommendation.targetType() + "\","
            + "\"targetId\":\"" + recommendation.targetId() + "\","
            + "\"confidence\":" + recommendation.confidence() + ","
            + "\"output\":\"" + recommendation.output().replace("\"", "\\\"") + "\","
            + "\"agentType\":\"" + recommendation.agentType() + "\""
            + "}";
    }

    private Recommendation reconstructRecommendation(AiActionLogEntry entry) {
        return new Recommendation(
            "approved-recommendation",
            entry.relatedEntityId() != null ? inferTargetType(entry.summary()) : "UNKNOWN",
            entry.relatedEntityId() != null ? entry.relatedEntityId() : entry.actionId(),
            entry.summary(),
            "unknown",
            entry.details(),
            entry.confidence() != null ? entry.confidence() : 0.0,
            null,
            0L
        );
    }

    private String inferTargetType(String summary) {
        String upper = summary.toUpperCase();
        if (upper.contains("CAMPAIGN")) return "CAMPAIGN";
        if (upper.contains("BUDGET")) return "BUDGET";
        if (upper.contains("STRATEGY")) return "STRATEGY";
        if (upper.contains("LANDING")) return "LANDING_PAGE";
        if (upper.contains("EMAIL")) return "EMAIL";
        if (upper.contains("GOOGLE ADS")) return "GOOGLE_ADS";
        return "UNKNOWN";
    }

    /**
     * Creates an AI action log entry (DMOS-P1-020).
     */
    private AiActionLogEntry createLogEntry(
        Recommendation recommendation,
        RiskLevel riskLevel,
        String status,
        DmTenantId tenantId,
        DmWorkspaceId workspaceId,
        String principalId
    ) {
        return new AiActionLogEntry(
            java.util.UUID.randomUUID().toString(),
            workspaceId.getValue(),
            "corr-" + java.util.UUID.randomUUID().toString(),
            com.ghatana.digitalmarketing.domain.transparency.AiActionType.RECOMMENDATION_GENERATED,
            com.ghatana.digitalmarketing.domain.transparency.AiActionStatus.valueOf(status),
            principalId,
            true,
            recommendation.confidence(),
            java.util.List.of(),
            java.util.List.of(),
            "AI recommendation: " + recommendation.targetType(),
            recommendation.output(),
            recommendation.targetId(),
            Instant.now(),
            0L
        );
    }

    /**
     * AI recommendation (DMOS-P1-020).
     */
    public record Recommendation(
        String agentType,
        String targetType,
        String targetId,
        String prompt,
        String model,
        String output,
        double confidence,
        String evidenceLocation,
        long durationMs
    ) {}

    /**
     * Gateway result (DMOS-P1-020).
     */
    public record GatewayResult(
        DmCommand command,
        GatewayStatus status,
        String reason,
        String commandId,
        String logEntryId
    ) {}

    /**
     * Gateway status (DMOS-P1-020).
     */
    public enum GatewayStatus {
        CREATED,
        REQUIRES_APPROVAL,
        BLOCKED,
        PROCESSED
    }

    /**
     * Validation result (DMOS-P1-020).
     */
    private record ValidationResult(boolean valid, String reason) {}

    /**
     * Risk level (DMOS-P1-020).
     */
    private enum RiskLevel {
        LOW,
        MEDIUM,
        HIGH
    }

    /**
     * Policy check result (DMOS-P1-020).
     */
    private record PolicyCheckResult(boolean compliant, String reason) {}
}
