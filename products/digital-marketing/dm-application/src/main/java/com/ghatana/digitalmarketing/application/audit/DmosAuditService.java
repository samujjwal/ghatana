package com.ghatana.digitalmarketing.application.audit;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * P1-028: Structured audit event service for all critical DMOS actions.
 *
 * <p>Ensures every critical action has an audit entry with:
 * <ul>
 *   <li>Actor (who performed the action)</li>
 *   <li>Tenant (organization scope)</li>
 *   <li>Workspace (project scope)</li>
 *   <li>Entity (what was affected)</li>
 *   <li>Action (what was done)</li>
 *   <li>Correlation ID (request tracing)</li>
 *   <li>Timestamp (when it occurred)</li>
 *   <li>Metadata (additional context)</li>
 * </ul>
 *
 * <p>Critical actions covered:</p>
 * <ul>
 *   <li>Create/launch/pause campaign</li>
 *   <li>Generate strategy/budget</li>
 *   <li>Submit/approve/reject approval requests</li>
 *   <li>External writes (Google Ads, connectors)</li>
 *   <li>PII operations</li>
 *   <li>AI actions</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Structured audit events for critical actions (P1-028)
 * @doc.layer product
 * @doc.pattern ApplicationService, Audit
 */
public final class DmosAuditService {

    private static final Logger LOG = LoggerFactory.getLogger(DmosAuditService.class);

    private final DigitalMarketingKernelAdapter kernelAdapter;

    public DmosAuditService(DigitalMarketingKernelAdapter kernelAdapter) {
        this.kernelAdapter = Objects.requireNonNull(kernelAdapter, "kernelAdapter must not be null");
    }

    /**
     * Records a campaign creation audit event.
     */
    public Promise<Void> recordCampaignCreated(DmOperationContext ctx, String campaignId, CampaignType type) {
        return record(ctx, AuditAction.CAMPAIGN_CREATED, "campaign", campaignId,
            Map.of("campaignType", type.name()));
    }

    /**
     * Records a campaign launch audit event.
     */
    public Promise<Void> recordCampaignLaunched(DmOperationContext ctx, String campaignId, String launchMethod) {
        return record(ctx, AuditAction.CAMPAIGN_LAUNCHED, "campaign", campaignId,
            Map.of("launchMethod", launchMethod));
    }

    /**
     * Records a campaign pause audit event.
     */
    public Promise<Void> recordCampaignPaused(DmOperationContext ctx, String campaignId, String reason) {
        return record(ctx, AuditAction.CAMPAIGN_PAUSED, "campaign", campaignId,
            Map.of("reason", reason));
    }

    /**
     * Records a strategy generation audit event.
     */
    public Promise<Void> recordStrategyGenerated(DmOperationContext ctx, String strategyId, String modelVersion,
                                                  Map<String, Object> provenance) {
        return record(ctx, AuditAction.STRATEGY_GENERATED, "strategy", strategyId,
            Map.of(
                "modelVersion", modelVersion,
                "provenance", provenance
            ));
    }

    /**
     * Records a budget recommendation audit event.
     */
    public Promise<Void> recordBudgetRecommended(DmOperationContext ctx, String budgetId, String modelVersion,
                                                  Map<String, Object> provenance) {
        return record(ctx, AuditAction.BUDGET_RECOMMENDED, "budget", budgetId,
            Map.of(
                "modelVersion", modelVersion,
                "provenance", provenance
            ));
    }

    /**
     * Records an approval submission audit event.
     */
    public Promise<Void> recordApprovalSubmitted(DmOperationContext ctx, String requestId, String entityType,
                                                  String entityId) {
        return record(ctx, AuditAction.APPROVAL_SUBMITTED, "approval-request", requestId,
            Map.of(
                "entityType", entityType,
                "entityId", entityId
            ));
    }

    /**
     * Records an approval decision audit event.
     */
    public Promise<Void> recordApprovalDecided(DmOperationContext ctx, String requestId, String decision,
                                                String approverId, String reason) {
        return record(ctx,
            "APPROVED".equals(decision) ? AuditAction.APPROVAL_APPROVED : AuditAction.APPROVAL_REJECTED,
            "approval-request", requestId,
            Map.of(
                "decision", decision,
                "approverId", approverId,
                "reason", reason
            ));
    }

    /**
     * Records an external write (connector) audit event.
     */
    public Promise<Void> recordExternalWrite(DmOperationContext ctx, String connectorType, String operation,
                                              String externalEntityId, boolean success, String details) {
        AuditAction action = success ? AuditAction.EXTERNAL_WRITE_SUCCESS : AuditAction.EXTERNAL_WRITE_FAILED;
        return record(ctx, action, "connector", connectorType + ":" + externalEntityId,
            Map.of(
                "operation", operation,
                "success", success,
                "details", details
            ));
    }

    /**
     * Records a PII operation audit event.
     */
    public Promise<Void> recordPiiOperation(DmOperationContext ctx, String operation, String dataSubjectId,
                                             String dataType, String result) {
        return record(ctx, AuditAction.PII_OPERATION, "pii", dataSubjectId,
            Map.of(
                "operation", operation,
                "dataType", dataType,
                "result", result
            ));
    }

    /**
     * Records an AI action audit event.
     */
    public Promise<Void> recordAiAction(DmOperationContext ctx, String actionType, String modelId,
                                       Map<String, Object> inputs, Map<String, Object> outputs) {
        return record(ctx, AuditAction.AI_ACTION, "ai-action", UUID.randomUUID().toString(),
            Map.of(
                "actionType", actionType,
                "modelId", modelId,
                "inputSummary", summarizeInputs(inputs),
                "outputSummary", summarizeOutputs(outputs)
            ));
    }

    /**
     * Records a kill switch activation audit event.
     */
    public Promise<Void> recordKillSwitchActivated(DmOperationContext ctx, String scope, String scopeId,
                                                    String reason) {
        return record(ctx, AuditAction.KILL_SWITCH_ACTIVATED, "kill-switch", scope + ":" + scopeId,
            Map.of(
                "scope", scope,
                "scopeId", scopeId,
                "reason", reason
            ));
    }

    /**
     * Records a rollback action audit event.
     */
    public Promise<Void> recordRollbackExecuted(DmOperationContext ctx, String rollbackActionId,
                                                 String originalCommandId, String result) {
        return record(ctx, AuditAction.ROLLBACK_EXECUTED, "rollback", rollbackActionId,
            Map.of(
                "originalCommandId", originalCommandId,
                "result", result
            ));
    }

    /**
     * Generic audit record method.
     */
    public Promise<Void> record(DmOperationContext ctx, AuditAction action, String entityType,
                                   String entityId, Map<String, Object> metadata) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(entityType, "entityType must not be null");
        Objects.requireNonNull(entityId, "entityId must not be null");

        String auditId = UUID.randomUUID().toString();
        String correlationId = ctx.getCorrelationId() != null
            ? ctx.getCorrelationId().getValue()
            : "unknown";

        LOG.info("[DMOS-AUDIT] {}: actor={}, tenant={}, workspace={}, entity={}:{}, correlationId={}",
            action, ctx.getActor().getPrincipalId(), ctx.getTenantId().getValue(),
            ctx.getWorkspaceId().getValue(), entityType, entityId, correlationId);

        Map<String, Object> auditData = Map.ofEntries(
            Map.entry("auditId", auditId),
            Map.entry("action", action.name()),
            Map.entry("actor", ctx.getActor().getPrincipalId()),
            Map.entry("actorType", (Object) "USER"),
            Map.entry("tenantId", ctx.getTenantId().getValue()),
            Map.entry("workspaceId", ctx.getWorkspaceId().getValue()),
            Map.entry("entityType", entityType),
            Map.entry("entityId", entityId),
            Map.entry("correlationId", correlationId),
            Map.entry("timestamp", Instant.now().toString()),
            Map.entry("metadata", metadata)
        );

        return kernelAdapter.recordAudit(ctx, entityType, action.name().toLowerCase(), auditData)
            .toVoid()
            .whenException(e -> {
                LOG.error("[DMOS-AUDIT] Failed to record audit event: {}", action, e);
            });
    }

    private Map<String, Object> summarizeInputs(Map<String, Object> inputs) {
        // Redact sensitive data from inputs
        return Map.of(
            "keys", inputs.keySet(),
            "hasSensitiveData", inputs.containsKey("password") || inputs.containsKey("token")
        );
    }

    private Map<String, Object> summarizeOutputs(Map<String, Object> outputs) {
        return Map.of(
            "keys", outputs.keySet(),
            "size", outputs.size()
        );
    }

    /**
     * Audit action types for critical DMOS operations.
     */
    public enum AuditAction {
        // Campaign actions
        CAMPAIGN_CREATED,
        CAMPAIGN_LAUNCHED,
        CAMPAIGN_PAUSED,
        CAMPAIGN_UPDATED,
        CAMPAIGN_DELETED,

        // Strategy actions
        STRATEGY_GENERATED,
        STRATEGY_SUBMITTED,

        // Budget actions
        BUDGET_RECOMMENDED,
        BUDGET_SUBMITTED,

        // Approval actions
        APPROVAL_SUBMITTED,
        APPROVAL_APPROVED,
        APPROVAL_REJECTED,

        // External write actions
        EXTERNAL_WRITE_SUCCESS,
        EXTERNAL_WRITE_FAILED,

        // PII actions
        PII_OPERATION,

        // AI actions
        AI_ACTION,

        // Governance actions
        KILL_SWITCH_ACTIVATED,
        KILL_SWITCH_DEACTIVATED,
        ROLLBACK_EXECUTED
    }

    /**
     * Campaign types for audit classification.
     */
    public enum CampaignType {
        EMAIL,
        SOCIAL,
        PAID_SEARCH,
        PUSH,
        SMS,
        OMNICHANNEL
    }
}
