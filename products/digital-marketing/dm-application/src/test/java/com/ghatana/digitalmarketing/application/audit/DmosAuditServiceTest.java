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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for P1-028: Structured audit events for critical actions.
 */
@DisplayName("P1-028: DmosAuditService Tests")
class DmosAuditServiceTest {

    @Mock
    private DigitalMarketingKernelAdapter kernelAdapter;

    private DmosAuditService auditService;
    private DmOperationContext testCtx;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        auditService = new DmosAuditService(kernelAdapter);

        testCtx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("test-tenant"))
            .workspaceId(DmWorkspaceId.of("test-workspace"))
            .actor(ActorRef.user("test-user"))
            .correlationId(DmCorrelationId.of("test-correlation-123"))
            .build();

        when(kernelAdapter.recordAudit(any(), any(), any(), any())).thenReturn(Promise.of(true));
    }

    @Test
    @DisplayName("P1-028: Should record campaign creation with all required fields")
    void shouldRecordCampaignCreated() {
        // When
        auditService.recordCampaignCreated(testCtx, "campaign-123", DmosAuditService.CampaignType.EMAIL)
            .await();

        // Then
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(kernelAdapter).recordAudit(eq(testCtx), eq("campaign"), eq("campaign_created"), captor.capture());

        Map<String, Object> auditData = captor.getValue();
        assertThat(auditData).containsKeys("auditId", "action", "actor", "tenantId", "workspaceId",
            "entityType", "entityId", "correlationId", "timestamp", "metadata");
        assertThat(auditData.get("action")).isEqualTo("CAMPAIGN_CREATED");
        assertThat(auditData.get("actor")).isEqualTo("test-user");
        assertThat(auditData.get("entityId")).isEqualTo("campaign-123");
    }

    @Test
    @DisplayName("P1-028: Should record campaign launch with launch method")
    void shouldRecordCampaignLaunched() {
        // When
        auditService.recordCampaignLaunched(testCtx, "campaign-123", "MANUAL")
            .await();

        // Then
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(kernelAdapter).recordAudit(any(), any(), any(), captor.capture());

        Map<String, Object> metadata = (Map<String, Object>) captor.getValue().get("metadata");
        assertThat(metadata).containsEntry("launchMethod", "MANUAL");
    }

    @Test
    @DisplayName("P1-028: Should record campaign pause with reason")
    void shouldRecordCampaignPaused() {
        // When
        auditService.recordCampaignPaused(testCtx, "campaign-123", "Budget reallocation")
            .await();

        // Then
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(kernelAdapter).recordAudit(any(), any(), any(), captor.capture());

        Map<String, Object> metadata = (Map<String, Object>) captor.getValue().get("metadata");
        assertThat(metadata).containsEntry("reason", "Budget reallocation");
    }

    @Test
    @DisplayName("P1-028: Should record strategy generation with model provenance")
    void shouldRecordStrategyGenerated() {
        // Given
        Map<String, Object> provenance = Map.of(
            "model", "gpt-4",
            "temperature", 0.7,
            "inputTokens", 1500
        );

        // When
        auditService.recordStrategyGenerated(testCtx, "strategy-123", "v2.1.0", provenance)
            .await();

        // Then
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(kernelAdapter).recordAudit(any(), any(), any(), captor.capture());

        Map<String, Object> metadata = (Map<String, Object>) captor.getValue().get("metadata");
        assertThat(metadata).containsKeys("modelVersion", "provenance");
        assertThat(metadata.get("modelVersion")).isEqualTo("v2.1.0");
    }

    @Test
    @DisplayName("P1-028: Should record budget recommendation with model provenance")
    void shouldRecordBudgetRecommended() {
        // Given
        Map<String, Object> provenance = Map.of(
            "model", "budget-optimizer-v1",
            "historicalData", true
        );

        // When
        auditService.recordBudgetRecommended(testCtx, "budget-123", "v1.2.0", provenance)
            .await();

        // Then
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(kernelAdapter).recordAudit(any(), any(), any(), captor.capture());

        Map<String, Object> metadata = (Map<String, Object>) captor.getValue().get("metadata");
        assertThat(metadata.get("modelVersion")).isEqualTo("v1.2.0");
    }

    @Test
    @DisplayName("P1-028: Should record approval submission")
    void shouldRecordApprovalSubmitted() {
        // When
        auditService.recordApprovalSubmitted(testCtx, "approval-123", "STRATEGY", "strategy-456")
            .await();

        // Then
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(kernelAdapter).recordAudit(any(), any(), any(), captor.capture());

        Map<String, Object> metadata = (Map<String, Object>) captor.getValue().get("metadata");
        assertThat(metadata).containsEntry("entityType", "STRATEGY");
        assertThat(metadata).containsEntry("entityId", "strategy-456");
    }

    @Test
    @DisplayName("P1-028: Should record approval decision with approver and reason")
    void shouldRecordApprovalDecided() {
        // When - approved
        auditService.recordApprovalDecided(testCtx, "approval-123", "APPROVED", "approver-1", "Looks good")
            .await();

        // Then
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(kernelAdapter).recordAudit(any(), any(), eq("approval_approved"), captor.capture());

        Map<String, Object> metadata = (Map<String, Object>) captor.getValue().get("metadata");
        assertThat(metadata).containsEntry("decision", "APPROVED");
        assertThat(metadata).containsEntry("approverId", "approver-1");
        assertThat(metadata).containsEntry("reason", "Looks good");
    }

    @Test
    @DisplayName("P1-028: Should record rejection correctly")
    void shouldRecordRejection() {
        // When
        auditService.recordApprovalDecided(testCtx, "approval-123", "REJECTED", "approver-1", "Budget too high")
            .await();

        // Then
        verify(kernelAdapter).recordAudit(any(), any(), eq("approval_rejected"), any());
    }

    @Test
    @DisplayName("P1-028: Should record external write success")
    void shouldRecordExternalWriteSuccess() {
        // When
        auditService.recordExternalWrite(testCtx, "GOOGLE_ADS", "CAMPAIGN_CREATE",
            "external-123", true, "Campaign created successfully")
            .await();

        // Then
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(kernelAdapter).recordAudit(any(), any(), eq("external_write_success"), captor.capture());

        Map<String, Object> metadata = (Map<String, Object>) captor.getValue().get("metadata");
        assertThat(metadata).containsEntry("operation", "CAMPAIGN_CREATE");
        assertThat(metadata).containsEntry("success", true);
    }

    @Test
    @DisplayName("P1-028: Should record external write failure")
    void shouldRecordExternalWriteFailure() {
        // When
        auditService.recordExternalWrite(testCtx, "GOOGLE_ADS", "CAMPAIGN_CREATE",
            "external-123", false, "API error: rate limited")
            .await();

        // Then
        verify(kernelAdapter).recordAudit(any(), any(), eq("external_write_failed"), any());
    }

    @Test
    @DisplayName("P1-028: Should record PII operation")
    void shouldRecordPiiOperation() {
        // When
        auditService.recordPiiOperation(testCtx, "DATA_EXPORT", "user-123", "EMAIL", "SUCCESS")
            .await();

        // Then
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(kernelAdapter).recordAudit(any(), any(), eq("pii_operation"), captor.capture());

        Map<String, Object> metadata = (Map<String, Object>) captor.getValue().get("metadata");
        assertThat(metadata).containsEntry("operation", "DATA_EXPORT");
        assertThat(metadata).containsEntry("dataType", "EMAIL");
    }

    @Test
    @DisplayName("P1-028: Should record AI action")
    void shouldRecordAiAction() {
        // Given
        Map<String, Object> inputs = Map.of("prompt", "Generate strategy", "budget", 50000);
        Map<String, Object> outputs = Map.of("strategy", "Content marketing focus", "channels", 4);

        // When
        auditService.recordAiAction(testCtx, "STRATEGY_GENERATION", "model-v1", inputs, outputs)
            .await();

        // Then
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(kernelAdapter).recordAudit(any(), any(), eq("ai_action"), captor.capture());

        Map<String, Object> metadata = (Map<String, Object>) captor.getValue().get("metadata");
        assertThat(metadata).containsEntry("actionType", "STRATEGY_GENERATION");
        assertThat(metadata).containsEntry("modelId", "model-v1");
    }

    @Test
    @DisplayName("P1-028: Should record kill switch activation")
    void shouldRecordKillSwitchActivated() {
        // When
        auditService.recordKillSwitchActivated(testCtx, "GOOGLE_ADS", "tenant-123", "Emergency stop")
            .await();

        // Then
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(kernelAdapter).recordAudit(any(), any(), eq("kill_switch_activated"), captor.capture());

        Map<String, Object> metadata = (Map<String, Object>) captor.getValue().get("metadata");
        assertThat(metadata).containsEntry("scope", "GOOGLE_ADS");
        assertThat(metadata).containsEntry("reason", "Emergency stop");
    }

    @Test
    @DisplayName("P1-028: Should record rollback execution")
    void shouldRecordRollbackExecuted() {
        // When
        auditService.recordRollbackExecuted(testCtx, "rollback-123", "command-456", "SUCCESS")
            .await();

        // Then
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(kernelAdapter).recordAudit(any(), any(), eq("rollback_executed"), captor.capture());

        Map<String, Object> metadata = (Map<String, Object>) captor.getValue().get("metadata");
        assertThat(metadata).containsEntry("originalCommandId", "command-456");
        assertThat(metadata).containsEntry("result", "SUCCESS");
    }

    @Test
    @DisplayName("P1-028: Should include correlation ID in all audit events")
    void shouldIncludeCorrelationId() {
        // When
        auditService.recordCampaignCreated(testCtx, "campaign-123", DmosAuditService.CampaignType.EMAIL)
            .await();

        // Then
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(kernelAdapter).recordAudit(any(), any(), any(), captor.capture());

        assertThat(captor.getValue()).containsEntry("correlationId", "test-correlation-123");
    }
}
