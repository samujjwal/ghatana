/**
 * P1-043: Exact changed-flow API integration suite.
 *
 * @doc.type class
 * @doc.purpose API integration tests for changed-flow workflows (P1-043)
 * @doc.layer test
 */
package com.ghatana.digitalmarketing.integration;

import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.domain.campaign.Campaign;
import com.ghatana.digitalmarketing.domain.campaign.CampaignStatus;
import com.ghatana.digitalmarketing.domain.approval.ApprovalStatus;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("P1-043: Changed-Flow API Integration Tests")
public class ChangedFlowApiIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("dmos_test")
        .withUsername("test")
        .withPassword("test");

    private Eventloop eventloop;
    private TestApplicationContext appContext;
    private DmOperationContext testCtx;

    @BeforeEach
    void setUp() {
        eventloop = Eventloop.create();
        appContext = new TestApplicationContext(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        testCtx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("test-tenant"))
            .workspaceId(DmWorkspaceId.of("test-workspace"))
            .actor(ActorRef.user("test-reviewer"))
            .correlationId(DmCorrelationId.generate())
            .idempotencyKey(DmIdempotencyKey.generate())
            .build();
    }

    @Test
    @DisplayName("P1-043: Campaign modification triggers approval workflow")
    void shouldTriggerApprovalWorkflowOnCampaignModification() {
        String campaignId = createApprovedCampaign().map(Campaign::getId).await(Duration.ofSeconds(10));
        var modifyCommand = new com.ghatana.digitalmarketing.application.campaign.ModifyCampaignCommand(
            campaignId, "Modified Name", "Updated desc", "Testing workflow");
        Campaign modified = appContext.campaignService().modify(testCtx, modifyCommand).await(Duration.ofSeconds(10));
        assertThat(modified.getStatus()).isEqualTo(CampaignStatus.MODIFICATION_PENDING);
        var approvals = appContext.approvalService().listPendingForEntity(testCtx, "campaign", campaignId).await(Duration.ofSeconds(5));
        assertThat(approvals).hasSize(1);
    }

    @Test
    @DisplayName("P1-043: Approve modification via API")
    void shouldApproveModificationViaApi() {
        String campaignId = createCampaignWithPendingModification().map(Campaign::getId).await(Duration.ofSeconds(10));
        var approvals = appContext.approvalService().listPendingForEntity(testCtx, "campaign", campaignId).await(Duration.ofSeconds(5));
        var approveCmd = new com.ghatana.digitalmarketing.application.approval.ApproveCommand(approvals.get(0).getId(), "Approved");
        appContext.approvalService().approve(testCtx, approveCmd).await(Duration.ofSeconds(10));
        var campaign = appContext.campaignService().findById(testCtx, campaignId).await(Duration.ofSeconds(5)).orElseThrow();
        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.APPROVED);
    }

    @Test
    @DisplayName("P1-043: Reject modification restores previous state")
    void shouldRestorePreviousStateOnRejection() {
        String campaignId = createCampaignWithPendingModification().map(Campaign::getId).await(Duration.ofSeconds(10));
        String originalName = appContext.campaignService().findById(testCtx, campaignId).await(Duration.ofSeconds(5)).orElseThrow().getName();
        var approvals = appContext.approvalService().listPendingForEntity(testCtx, "campaign", campaignId).await(Duration.ofSeconds(5));
        var rejectCmd = new com.ghatana.digitalmarketing.application.approval.RejectCommand(approvals.get(0).getId(), "Rejected");
        appContext.approvalService().reject(testCtx, rejectCmd).await(Duration.ofSeconds(10));
        var campaign = appContext.campaignService().findById(testCtx, campaignId).await(Duration.ofSeconds(5)).orElseThrow();
        assertThat(campaign.getName()).isEqualTo(originalName);
    }

    @Test
    @DisplayName("P1-043: Modification reason is required")
    void shouldRequireModificationReason() {
        String campaignId = createApprovedCampaign().map(Campaign::getId).await(Duration.ofSeconds(10));
        var modifyCmd = new com.ghatana.digitalmarketing.application.campaign.ModifyCampaignCommand(campaignId, "New Name", null, null);
        var result = appContext.campaignService().modify(testCtx, modifyCmd).await(Duration.ofSeconds(10));
        assertThat(result.isFailure()).isTrue();
        assertThat(result.getError()).contains("modification reason");
    }

    private Promise<Campaign> createApprovedCampaign() {
        var cmd = new com.ghatana.digitalmarketing.application.campaign.CreateCampaignCommand(UUID.randomUUID().toString(), "Test", "Desc");
        return appContext.campaignService().create(testCtx, cmd)
            .then(c -> appContext.campaignService().submitForApproval(testCtx, c.getId())
                .then(() -> appContext.approvalService().listPendingForEntity(testCtx, "campaign", c.getId())
                    .then(a -> a.isEmpty() ? Promise.complete() : appContext.approvalService().approve(testCtx,
                        new com.ghatana.digitalmarketing.application.approval.ApproveCommand(a.get(0).getId(), "Auto-approve"))))
                .then(() -> appContext.campaignService().findById(testCtx, c.getId()))
                .then(opt -> opt.map(Promise::of).orElse(Promise.ofException(new IllegalStateException()))));
    }

    private Promise<Campaign> createCampaignWithPendingModification() {
        return createApprovedCampaign().then(c -> {
            var cmd = new com.ghatana.digitalmarketing.application.campaign.ModifyCampaignCommand(c.getId(), "Modified", "Desc", "Reason");
            return appContext.campaignService().modify(testCtx, cmd);
        });
    }
}
