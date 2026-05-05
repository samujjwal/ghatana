package com.ghatana.digitalmarketing.integration;

import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.domain.approval.ApprovalRequest;
import com.ghatana.digitalmarketing.domain.approval.ApprovalStatus;
import com.ghatana.digitalmarketing.domain.budget.BudgetRecommendation;
import com.ghatana.digitalmarketing.domain.budget.ChannelAllocation;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * P1-034: Budget lifecycle E2E test.
 *
 * <p>Tests the complete budget journey:</p>
 * <ol>
 *   <li>Generate budget recommendation</li>
 *   <li>Display budget values and channel allocations</li>
 *   <li>Submit for approval</li>
 *   <li>Approve/Deny path</li>
 *   <li>Verify values persist correctly</li>
 *   <li>Verify AI action log</li>
 *   <li>Verify audit trail</li>
 * </ol>
 *
 * @doc.type test
 * @doc.purpose E2E test for budget lifecycle (P1-034)
 * @doc.layer test
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("P1-034: Budget Lifecycle E2E")
public class BudgetLifecycleE2E {

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
            .actor(ActorRef.user("test-user"))
            .correlationId(DmCorrelationId.generate())
            .idempotencyKey(DmIdempotencyKey.generate())
            .build();
    }

    @Test
    @DisplayName("P1-034: Complete budget recommendation → approve workflow")
    void shouldCompleteBudgetLifecycle() {
        // Phase 1: Generate budget recommendation
        String budgetId = generateBudgetRecommendation()
            .map(BudgetRecommendation::getId)
            .await(Duration.ofSeconds(10));

        assertThat(budgetId).isNotNull();

        // Phase 2: Verify budget values and allocations
        BudgetRecommendation budget = appContext.budgetService().findById(testCtx, budgetId)
            .await(Duration.ofSeconds(5))
            .orElseThrow();

        assertThat(budget.getTotalBudget()).isPositive();
        assertThat(budget.getChannelAllocations()).isNotEmpty();

        // Verify allocations sum to total
        BigDecimal totalAllocated = budget.getChannelAllocations().stream()
            .map(ChannelAllocation::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(totalAllocated).isEqualByComparingTo(budget.getTotalBudget());

        // Phase 3: Submit for approval
        String approvalRequestId = submitForApproval(budgetId)
            .map(ApprovalRequest::getId)
            .await(Duration.ofSeconds(10));

        assertThat(approvalRequestId).isNotNull();

        // Phase 4: Verify approval queue entry
        ApprovalRequest approvalRequest = appContext.approvalService().findById(testCtx, approvalRequestId)
            .await(Duration.ofSeconds(5))
            .orElseThrow();

        assertThat(approvalRequest.getStatus()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(approvalRequest.getSubjectId()).isEqualTo(budgetId);

        // Phase 5: Approve the budget
        ApprovalRequest approved = approveRequest(approvalRequestId, "Budget approved for Q1 campaign")
            .await(Duration.ofSeconds(10));

        assertThat(approved.getStatus()).isEqualTo(ApprovalStatus.APPROVED);

        // Phase 6: Verify budget is now ACTIVE
        BudgetRecommendation activeBudget = appContext.budgetService().findById(testCtx, budgetId)
            .await(Duration.ofSeconds(5))
            .orElseThrow();

        assertThat(activeBudget.getStatus()).isEqualTo(com.ghatana.digitalmarketing.domain.budget.BudgetStatus.ACTIVE);

        // Phase 7: Verify AI action log entry with model provenance (P1-029)
        var aiActions = appContext.aiActionLogService().listByWorkspace(testCtx, testCtx.getWorkspaceId(), 10)
            .await(Duration.ofSeconds(5));

        var budgetAction = aiActions.stream()
            .filter(action -> action.getAction().equals("BUDGET_RECOMMENDED"))
            .filter(action -> action.getEntityId().equals(budgetId))
            .findFirst();

        assertThat(budgetAction).isPresent();
        assertThat(budgetAction.get().getModelVersion()).isNotNull();
        assertThat(budgetAction.get().getInputEvidence()).isNotNull();

        // Phase 8: Verify audit trail
        var auditEvents = appContext.auditService().listForEntity(testCtx, "budget", budgetId)
            .await(Duration.ofSeconds(5));

        assertThat(auditEvents).extracting("action")
            .contains("BUDGET_RECOMMENDED", "APPROVAL_SUBMITTED", "APPROVAL_APPROVED");
    }

    @Test
    @DisplayName("P1-034: Budget channel allocation persistence")
    void shouldPersistChannelAllocations() {
        // Generate budget
        BudgetRecommendation budget = generateBudgetRecommendation()
            .await(Duration.ofSeconds(10));

        // Verify each channel allocation has expected fields
        for (ChannelAllocation allocation : budget.getChannelAllocations()) {
            assertThat(allocation.getChannel()).isNotBlank();
            assertThat(allocation.getAmount()).isPositive();
            assertThat(allocation.getPercentage()).isPositive();
        }

        // Re-fetch and verify persistence
        BudgetRecommendation persisted = appContext.budgetService().findById(testCtx, budget.getId())
            .await(Duration.ofSeconds(5))
            .orElseThrow();

        assertThat(persisted.getChannelAllocations())
            .extracting(ChannelAllocation::getChannel, ChannelAllocation::getAmount)
            .containsExactlyInAnyOrderElementsOf(
                budget.getChannelAllocations().stream()
                    .map(a -> tuple(a.getChannel(), a.getAmount()))
                    .toList()
            );
    }

    @Test
    @DisplayName("P1-034: Budget rejection with proper status transition")
    void shouldHandleBudgetRejection() {
        // Generate and submit budget
        String budgetId = generateBudgetRecommendation()
            .map(BudgetRecommendation::getId)
            .await(Duration.ofSeconds(10));

        String approvalRequestId = submitForApproval(budgetId)
            .map(ApprovalRequest::getId)
            .await(Duration.ofSeconds(10));

        // Reject the budget
        ApprovalRequest rejected = rejectRequest(approvalRequestId, "Exceeds available funds")
            .await(Duration.ofSeconds(10));

        assertThat(rejected.getStatus()).isEqualTo(ApprovalStatus.REJECTED);

        // Verify budget is back to DRAFT
        BudgetRecommendation budget = appContext.budgetService().findById(testCtx, budgetId)
            .await(Duration.ofSeconds(5))
            .orElseThrow();

        assertThat(budget.getStatus()).isEqualTo(com.ghatana.digitalmarketing.domain.budget.BudgetStatus.DRAFT);
    }

    private Promise<BudgetRecommendation> generateBudgetRecommendation() {
        var command = new com.ghatana.digitalmarketing.application.budget.GenerateBudgetCommand(
            UUID.randomUUID().toString(),
            "Q1 Marketing Budget",
            LocalDate.now(),
            LocalDate.now().plusMonths(3),
            new BigDecimal("50000"),
            java.util.List.of("SOCIAL", "SEARCH", "DISPLAY", "EMAIL"),
            Map.of(
                "targetLeads", 1000,
                "targetConversions", 100,
                "historicalCpa", 50.0
            )
        );

        return appContext.budgetService().generate(testCtx, command);
    }

    private Promise<ApprovalRequest> submitForApproval(String budgetId) {
        var command = new com.ghatana.digitalmarketing.application.approval.SubmitForApprovalCommand(
            budgetId,
            "BUDGET",
            "Please review this budget recommendation"
        );

        return appContext.approvalService().submit(testCtx, command);
    }

    private Promise<ApprovalRequest> approveRequest(String requestId, String reason) {
        var command = new com.ghatana.digitalmarketing.application.approval.ApproveRequestCommand(
            requestId,
            reason
        );

        return appContext.approvalService().approve(testCtx, command);
    }

    private Promise<ApprovalRequest> rejectRequest(String requestId, String reason) {
        var command = new com.ghatana.digitalmarketing.application.approval.RejectRequestCommand(
            requestId,
            reason
        );

        return appContext.approvalService().reject(testCtx, command);
    }
}
