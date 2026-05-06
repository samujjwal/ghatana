package com.ghatana.digitalmarketing.application.audit;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for P1-028: Structured audit events for critical actions.
 */
@DisplayName("P1-028: DmosAuditService Tests")
class DmosAuditServiceTest {

    private CapturingKernelAdapter adapter;
    private DmosAuditService auditService;
    private DmOperationContext testCtx;

    @BeforeEach
    void setUp() {
        adapter = new CapturingKernelAdapter();
        auditService = new DmosAuditService(adapter);

        testCtx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("test-tenant"))
            .workspaceId(DmWorkspaceId.of("test-workspace"))
            .actor(ActorRef.user("test-user"))
            .correlationId(DmCorrelationId.of("test-correlation-123"))
            .build();
    }

    @Test
    @DisplayName("P1-028: Should record campaign creation with all required fields")
    void shouldRecordCampaignCreated() {
        auditService.recordCampaignCreated(testCtx, "campaign-123", DmosAuditService.CampaignType.EMAIL);

        assertThat(adapter.auditCalls()).hasSize(1);
        AuditCall call = adapter.lastAuditCall();
        assertThat(call.entityType()).isEqualTo("campaign");
        assertThat(call.action()).isEqualTo("campaign_created");

        Map<String, Object> auditData = call.data();
        assertThat(auditData).containsKeys("auditId", "action", "actor", "tenantId", "workspaceId",
            "entityType", "entityId", "correlationId", "timestamp", "metadata");
        assertThat(auditData.get("action")).isEqualTo("CAMPAIGN_CREATED");
        assertThat(auditData.get("actor")).isEqualTo("test-user");
        assertThat(auditData.get("entityId")).isEqualTo("campaign-123");
    }

    @Test
    @DisplayName("P1-028: Should record campaign launch with launch method")
    void shouldRecordCampaignLaunched() {
        auditService.recordCampaignLaunched(testCtx, "campaign-123", "MANUAL");

        Map<String, Object> metadata = getLastMetadata();
        assertThat(metadata).containsEntry("launchMethod", "MANUAL");
    }

    @Test
    @DisplayName("P1-028: Should record campaign pause with reason")
    void shouldRecordCampaignPaused() {
        auditService.recordCampaignPaused(testCtx, "campaign-123", "Budget reallocation");

        Map<String, Object> metadata = getLastMetadata();
        assertThat(metadata).containsEntry("reason", "Budget reallocation");
    }

    @Test
    @DisplayName("P1-028: Should record strategy generation with model provenance")
    void shouldRecordStrategyGenerated() {
        Map<String, Object> provenance = Map.of("model", "gpt-4", "temperature", 0.7, "inputTokens", 1500);

        auditService.recordStrategyGenerated(testCtx, "strategy-123", "v2.1.0", provenance);

        Map<String, Object> metadata = getLastMetadata();
        assertThat(metadata).containsKeys("modelVersion", "provenance");
        assertThat(metadata.get("modelVersion")).isEqualTo("v2.1.0");
    }

    @Test
    @DisplayName("P1-028: Should record budget recommendation with model provenance")
    void shouldRecordBudgetRecommended() {
        Map<String, Object> provenance = Map.of("model", "budget-optimizer-v1", "historicalData", true);

        auditService.recordBudgetRecommended(testCtx, "budget-123", "v1.2.0", provenance);

        Map<String, Object> metadata = getLastMetadata();
        assertThat(metadata.get("modelVersion")).isEqualTo("v1.2.0");
    }

    @Test
    @DisplayName("P1-028: Should record approval submission")
    void shouldRecordApprovalSubmitted() {
        auditService.recordApprovalSubmitted(testCtx, "approval-123", "STRATEGY", "strategy-456");

        Map<String, Object> metadata = getLastMetadata();
        assertThat(metadata).containsEntry("entityType", "STRATEGY");
        assertThat(metadata).containsEntry("entityId", "strategy-456");
    }

    @Test
    @DisplayName("P1-028: Should record approval decision with approver and reason")
    void shouldRecordApprovalDecided() {
        auditService.recordApprovalDecided(testCtx, "approval-123", "APPROVED", "approver-1", "Looks good");

        AuditCall call = adapter.lastAuditCall();
        assertThat(call.action()).isEqualTo("approval_approved");

        Map<String, Object> metadata = getLastMetadata();
        assertThat(metadata).containsEntry("decision", "APPROVED");
        assertThat(metadata).containsEntry("approverId", "approver-1");
        assertThat(metadata).containsEntry("reason", "Looks good");
    }

    @Test
    @DisplayName("P1-028: Should record rejection correctly")
    void shouldRecordRejection() {
        auditService.recordApprovalDecided(testCtx, "approval-123", "REJECTED", "approver-1", "Budget too high");

        assertThat(adapter.lastAuditCall().action()).isEqualTo("approval_rejected");
    }

    @Test
    @DisplayName("P1-028: Should record external write success")
    void shouldRecordExternalWriteSuccess() {
        auditService.recordExternalWrite(testCtx, "GOOGLE_ADS", "CAMPAIGN_CREATE",
            "external-123", true, "Campaign created successfully");

        AuditCall call = adapter.lastAuditCall();
        assertThat(call.action()).isEqualTo("external_write_success");

        Map<String, Object> metadata = getLastMetadata();
        assertThat(metadata).containsEntry("operation", "CAMPAIGN_CREATE");
        assertThat(metadata).containsEntry("success", true);
    }

    @Test
    @DisplayName("P1-028: Should record external write failure")
    void shouldRecordExternalWriteFailure() {
        auditService.recordExternalWrite(testCtx, "GOOGLE_ADS", "CAMPAIGN_CREATE",
            "external-123", false, "API error: rate limited");

        assertThat(adapter.lastAuditCall().action()).isEqualTo("external_write_failed");
    }

    @Test
    @DisplayName("P1-028: Should record PII operation")
    void shouldRecordPiiOperation() {
        auditService.recordPiiOperation(testCtx, "DATA_EXPORT", "user-123", "EMAIL", "SUCCESS");

        AuditCall call = adapter.lastAuditCall();
        assertThat(call.action()).isEqualTo("pii_operation");

        Map<String, Object> metadata = getLastMetadata();
        assertThat(metadata).containsEntry("operation", "DATA_EXPORT");
        assertThat(metadata).containsEntry("dataType", "EMAIL");
    }

    @Test
    @DisplayName("P1-028: Should record AI action")
    void shouldRecordAiAction() {
        Map<String, Object> inputs = Map.of("prompt", "Generate strategy", "budget", 50000);
        Map<String, Object> outputs = Map.of("strategy", "Content marketing focus", "channels", 4);

        auditService.recordAiAction(testCtx, "STRATEGY_GENERATION", "model-v1", inputs, outputs);

        AuditCall call = adapter.lastAuditCall();
        assertThat(call.action()).isEqualTo("ai_action");

        Map<String, Object> metadata = getLastMetadata();
        assertThat(metadata).containsEntry("actionType", "STRATEGY_GENERATION");
        assertThat(metadata).containsEntry("modelId", "model-v1");
    }

    @Test
    @DisplayName("P1-028: Should record kill switch activation")
    void shouldRecordKillSwitchActivated() {
        auditService.recordKillSwitchActivated(testCtx, "GOOGLE_ADS", "tenant-123", "Emergency stop");

        AuditCall call = adapter.lastAuditCall();
        assertThat(call.action()).isEqualTo("kill_switch_activated");

        Map<String, Object> metadata = getLastMetadata();
        assertThat(metadata).containsEntry("scope", "GOOGLE_ADS");
        assertThat(metadata).containsEntry("reason", "Emergency stop");
    }

    @Test
    @DisplayName("P1-028: Should record rollback execution")
    void shouldRecordRollbackExecuted() {
        auditService.recordRollbackExecuted(testCtx, "rollback-123", "command-456", "SUCCESS");

        AuditCall call = adapter.lastAuditCall();
        assertThat(call.action()).isEqualTo("rollback_executed");

        Map<String, Object> metadata = getLastMetadata();
        assertThat(metadata).containsEntry("originalCommandId", "command-456");
        assertThat(metadata).containsEntry("result", "SUCCESS");
    }

    @Test
    @DisplayName("P1-028: Should include correlation ID in all audit events")
    void shouldIncludeCorrelationId() {
        auditService.recordCampaignCreated(testCtx, "campaign-123", DmosAuditService.CampaignType.EMAIL);

        assertThat(adapter.lastAuditCall().data()).containsEntry("correlationId", "test-correlation-123");
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> getLastMetadata() {
        return (Map<String, Object>) adapter.lastAuditCall().data().get("metadata");
    }

    // ─── In-memory doubles ────────────────────────────────────────────────────

    record AuditCall(DmOperationContext ctx, String entityType, String action, Map<String, Object> data) {}

    private static final class CapturingKernelAdapter implements DigitalMarketingKernelAdapter {
        private final List<AuditCall> calls = new ArrayList<>();

        @Override public void start() {}
        @Override public void stop() {}

        @Override
        public Promise<Boolean> isAuthorized(DmOperationContext context, String resource, String action) {
            return Promise.of(true);
        }

        @Override
        public Promise<Boolean> verifyConsent(DmOperationContext context, String subjectId, String purpose) {
            return Promise.of(true);
        }

        @Override
        public Promise<String> requestApproval(DmOperationContext context, String operationType,
                                                String subjectId, String description) {
            return Promise.of("approval-request-id");
        }

        @Override
        public Promise<String> recordAudit(DmOperationContext context, String entityType,
                                           String action, Map<String, Object> attributes) {
            calls.add(new AuditCall(context, entityType, action, attributes));
            return Promise.of("audit-" + calls.size());
        }

        List<AuditCall> auditCalls() { return calls; }

        AuditCall lastAuditCall() {
            assertThat(calls).as("Expected at least one audit call").isNotEmpty();
            return calls.getLast();
        }
    }
}
