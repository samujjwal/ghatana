package com.ghatana.digitalmarketing.api.security;

import com.ghatana.digitalmarketing.api.DmosApprovalServlet;
import com.ghatana.digitalmarketing.api.DmosBudgetRecommendationServlet;
import com.ghatana.digitalmarketing.api.DmosCampaignServlet;
import com.ghatana.digitalmarketing.api.DmosStrategyServlet;
import com.ghatana.digitalmarketing.application.approval.ApprovalWorkflowService;
import com.ghatana.digitalmarketing.application.budget.BudgetRecommendationService;
import com.ghatana.digitalmarketing.application.campaign.CampaignService;
import com.ghatana.digitalmarketing.application.strategy.StrategyGeneratorService;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.budget.BudgetRecommendation;
import com.ghatana.digitalmarketing.domain.campaign.Campaign;
import com.ghatana.digitalmarketing.domain.strategy.MarketingStrategy;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.plugin.approval.ApprovalRecord;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1-016: Cross-tenant isolation verification for all DMOS API routes.
 *
 * <p>Verifies that every servlet correctly propagates {@code SecurityException}
 * thrown by services to a 403 Forbidden response. This guarantees that when the
 * authorization layer (kernel bridge) rejects a cross-tenant or cross-workspace
 * request, no data leaks back to the caller.</p>
 *
 * <p>Threat model: attacker sends a request targeting workspace {@code ws-victim}
 * with their own tenant ID in {@code X-Tenant-ID}. The kernel bridge's
 * {@code isAuthorized} call detects the mismatch and throws
 * {@code SecurityException}. These tests verify that the 403 response is always
 * returned, with no resource data in the body.</p>
 *
 * @doc.type test
 * @doc.purpose Cross-tenant isolation boundary verification (P1-016)
 * @doc.layer test
 * @doc.pattern SecurityTest
 */
@DisplayName("P1-016: Cross-Tenant Isolation")
class CrossTenantIsolationTest extends EventloopTestBase {

    // Attacker controls this tenant and sends a forged request to the victim workspace
    private static final String ATTACKER_TENANT = "tenant-attacker";
    private static final String VICTIM_WORKSPACE = "ws-victim";
    private static final String AUTHORIZATION_HEADER = "Bearer attacker-token";

    private Eventloop eventloop;

    @BeforeEach
    void setUp() {
        eventloop = Eventloop.create();
    }

    // -----------------------------------------------------------------------
    // Campaign servlet
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("CampaignServlet cross-tenant isolation")
    class CampaignServletIsolation {

        private AsyncServlet servlet;

        @BeforeEach
        void setUp() {
            CampaignService crossTenantRejectingService = new CrossTenantRejectingCampaignService();
            servlet = new DmosCampaignServlet(crossTenantRejectingService, eventloop).getServlet();
        }


        @Test
        @DisplayName("GET /campaigns returns 403 when tenant does not own workspace")
        void listCampaigns_crossTenant_returns403() {
            HttpRequest request = get("/v1/workspaces/" + VICTIM_WORKSPACE + "/campaigns");
            HttpResponse response = runPromise(() -> servlet.serve(request));
            assertTenantRejection(response);
        }

        @Test
        @DisplayName("POST /campaigns returns 403 when tenant does not own workspace")
        void createCampaign_crossTenant_returns403() {
            HttpRequest request = post("/v1/workspaces/" + VICTIM_WORKSPACE + "/campaigns",
                "{\"name\":\"Stolen\",\"type\":\"EMAIL\"}");
            HttpResponse response = runPromise(() -> servlet.serve(request));
            assertTenantRejection(response);
        }

        @Test
        @DisplayName("POST /campaigns/:id/launch returns 403 when tenant does not own workspace")
        void launchCampaign_crossTenant_returns403() {
            HttpRequest request = post("/v1/workspaces/" + VICTIM_WORKSPACE + "/campaigns/cmp-1/launch", "");
            HttpResponse response = runPromise(() -> servlet.serve(request));
            assertTenantRejection(response);
        }

        @Test
        @DisplayName("GET /campaigns/:id returns 403 when tenant does not own workspace")
        void getCampaign_crossTenant_returns403() {
            HttpRequest request = get("/v1/workspaces/" + VICTIM_WORKSPACE + "/campaigns/cmp-1");
            HttpResponse response = runPromise(() -> servlet.serve(request));
            assertTenantRejection(response);
        }
    }

    // -----------------------------------------------------------------------
    // Strategy servlet
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("StrategyServlet cross-tenant isolation")
    class StrategyServletIsolation {

        private AsyncServlet servlet;

        @BeforeEach
        void setUp() {
            StrategyGeneratorService crossTenantRejectingService = new CrossTenantRejectingStrategyService();
            servlet = new DmosStrategyServlet(crossTenantRejectingService, eventloop).getServlet();
        }

        @Test
        @DisplayName("GET /strategy returns 403 when tenant does not own workspace")
        void getStrategy_crossTenant_returns403() {
            HttpRequest request = get("/v1/workspaces/" + VICTIM_WORKSPACE + "/strategy");
            HttpResponse response = runPromise(() -> servlet.serve(request));
            assertTenantRejection(response);
        }

        @Test
        @DisplayName("POST /strategy returns 403 when tenant does not own workspace")
        void generateStrategy_crossTenant_returns403() {
            HttpRequest request = post("/v1/workspaces/" + VICTIM_WORKSPACE + "/strategy",
                                """
                                {
                                    "intakeCompletionPct": 80,
                                    "serviceArea": "metro-area",
                                    "monthlyBudget": 5000,
                                    "auditFindingCount": 3,
                                    "trackingGapsDetected": false,
                                    "keywordOpportunityCount": 12,
                                    "topCompetitorCount": 4,
                                    "primaryOffer": "managed-service"
                                }
                                """);
            HttpResponse response = runPromise(() -> servlet.serve(request));
            assertTenantRejection(response);
        }
    }

    // -----------------------------------------------------------------------
    // Budget servlet
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("BudgetServlet cross-tenant isolation")
    class BudgetServletIsolation {

        private AsyncServlet servlet;

        @BeforeEach
        void setUp() {
            BudgetRecommendationService crossTenantRejectingService = new CrossTenantRejectingBudgetService();
            servlet = new DmosBudgetRecommendationServlet(crossTenantRejectingService, eventloop).getServlet();
        }

        @Test
        @DisplayName("GET /budget-recommendation returns 403 when tenant does not own workspace")
        void getBudget_crossTenant_returns403() {
            HttpRequest request = get("/v1/workspaces/" + VICTIM_WORKSPACE + "/budget-recommendation");
            HttpResponse response = runPromise(() -> servlet.serve(request));
            assertTenantRejection(response);
        }

        @Test
        @DisplayName("POST /budget-recommendation returns 403 when tenant does not own workspace")
        void generateBudget_crossTenant_returns403() {
            HttpRequest request = post("/v1/workspaces/" + VICTIM_WORKSPACE + "/budget-recommendation",
                "{\"strategyId\":\"str-1\"}");
            HttpResponse response = runPromise(() -> servlet.serve(request));
            assertTenantRejection(response);
        }
    }

    // -----------------------------------------------------------------------
    // Approval servlet
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("ApprovalServlet cross-tenant isolation")
    class ApprovalServletIsolation {

        private AsyncServlet servlet;

        @BeforeEach
        void setUp() {
            ApprovalWorkflowService crossTenantRejectingService = new CrossTenantRejectingApprovalService();
            servlet = new DmosApprovalServlet(crossTenantRejectingService, eventloop).routes();
        }

        @Test
        @DisplayName("GET /approvals/pending returns 403 when tenant does not own workspace")
        void listApprovals_crossTenant_returns403() {
            HttpRequest request = get("/v1/workspaces/" + VICTIM_WORKSPACE + "/approvals/pending");
            HttpResponse response = runPromise(() -> servlet.serve(request));
            assertTenantRejection(response);
        }

        @Test
        @DisplayName("POST /approvals returns 403 when tenant does not own workspace")
        void submitApproval_crossTenant_returns403() {
            HttpRequest request = post("/v1/workspaces/" + VICTIM_WORKSPACE + "/approvals",
                                """
                                {
                                    "targetType": "CAMPAIGN_LAUNCH",
                                    "targetId": "cmp-1",
                                    "description": "Cross-tenant attack simulation",
                                    "riskLevel": 2,
                                    "requiredApproverRole": "reviewer",
                                    "validationResultId": "vr-attack"
                                }
                                """);
            HttpResponse response = runPromise(() -> servlet.serve(request));
            assertTenantRejection(response);
        }
    }

    // -----------------------------------------------------------------------
    // Helper assertions
    // -----------------------------------------------------------------------

    /**
     * Verifies the response is a 403 with no data belonging to the victim workspace.
     *
     * <p>Critically the response body must not include any workspace or tenant IDs
     * that would confirm resource existence (no oracle). The only safe response
     * is a generic 403 with a static message.</p>
     */
    private static void assertTenantRejection(HttpResponse response) {
        assertThat(response.getCode())
            .as("Cross-tenant request must be rejected with 403 Forbidden")
            .isEqualTo(403);

        String body = response.getBody().getString(java.nio.charset.StandardCharsets.UTF_8);

        // Body must not leak victim workspace or tenant data
        assertThat(body)
            .as("403 response must not leak victim workspace ID")
            .doesNotContain(VICTIM_WORKSPACE);
    }

    // -----------------------------------------------------------------------
    // Request builders
    // -----------------------------------------------------------------------

    private static HttpRequest get(String path) {
        return HttpRequest.get("http://localhost" + path)
            .withHeader(HttpHeaders.of("X-Tenant-ID"), ATTACKER_TENANT)
            .withHeader(HttpHeaders.AUTHORIZATION, AUTHORIZATION_HEADER)
            .build();
    }

    private static HttpRequest post(String path, String body) {
        return HttpRequest.post("http://localhost" + path)
            .withHeader(HttpHeaders.of("X-Tenant-ID"), ATTACKER_TENANT)
            .withHeader(HttpHeaders.AUTHORIZATION, AUTHORIZATION_HEADER)
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-test-123")
            .withBody(body.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            .build();
    }

    // -----------------------------------------------------------------------
    // Cross-tenant-rejecting fake services
    // Simulate the kernel bridge throwing SecurityException on mismatched access
    // -----------------------------------------------------------------------

    private static SecurityException crossTenantException() {
        return new SecurityException(
            "Access denied: tenant " + ATTACKER_TENANT + " does not own workspace " + VICTIM_WORKSPACE);
    }

    private static final class CrossTenantRejectingCampaignService implements CampaignService {
        @Override
        public Promise<Campaign> createCampaign(
                DmOperationContext ctx,
                CampaignService.CreateCampaignCommand command) {
            return Promise.ofException(crossTenantException());
        }

        @Override
        public Promise<Campaign> launchCampaign(
                DmOperationContext ctx, String campaignId) {
            return Promise.ofException(crossTenantException());
        }

        @Override
        public Promise<Campaign> pauseCampaign(
                DmOperationContext ctx, String campaignId) {
            return Promise.ofException(crossTenantException());
        }

        @Override
        public Promise<Campaign> completeCampaign(DmOperationContext ctx, String campaignId) {
            return Promise.ofException(crossTenantException());
        }

        @Override
        public Promise<Campaign> archiveCampaign(DmOperationContext ctx, String campaignId) {
            return Promise.ofException(crossTenantException());
        }

        @Override
        public Promise<Campaign> rollbackCampaign(DmOperationContext ctx, String campaignId) {
            return Promise.ofException(crossTenantException());
        }

        @Override
        public Promise<Campaign> duplicateCampaign(DmOperationContext ctx, String campaignId, String newName) {
            return Promise.ofException(crossTenantException());
        }

        @Override
        public Promise<Campaign> getCampaign(
                DmOperationContext ctx, String campaignId) {
            return Promise.ofException(crossTenantException());
        }

        @Override
        public Promise<CampaignService.CampaignListResult> listCampaigns(
                DmOperationContext ctx, int limit, int offset) {
            return Promise.ofException(crossTenantException());
        }
    }

    private static final class CrossTenantRejectingStrategyService implements StrategyGeneratorService {
        @Override
        public Promise<MarketingStrategy> generateStrategy(
                DmOperationContext ctx,
                StrategyGeneratorService.GenerateStrategyCommand command) {
            return Promise.ofException(crossTenantException());
        }

        @Override
        public Promise<MarketingStrategy> getLatestStrategy(DmOperationContext ctx) {
            return Promise.ofException(crossTenantException());
        }

        @Override
        public Promise<MarketingStrategy> submitForApproval(DmOperationContext ctx, String strategyId) {
            return Promise.ofException(crossTenantException());
        }

        @Override
        public Promise<MarketingStrategy> approveStrategy(DmOperationContext ctx, String strategyId) {
            return Promise.ofException(crossTenantException());
        }
    }

    private static final class CrossTenantRejectingBudgetService implements BudgetRecommendationService {
        @Override
        public Promise<BudgetRecommendation> recommendBudget(
                DmOperationContext ctx,
                BudgetRecommendationService.GenerateBudgetCommand command) {
            return Promise.ofException(crossTenantException());
        }

        @Override
        public Promise<BudgetRecommendation> getLatestRecommendation(DmOperationContext ctx) {
            return Promise.ofException(crossTenantException());
        }

        @Override
        public Promise<BudgetRecommendation> submitForApproval(
                DmOperationContext ctx, String recommendationId) {
            return Promise.ofException(crossTenantException());
        }

        @Override
        public Promise<BudgetRecommendation> approveRecommendation(
                DmOperationContext ctx, String recommendationId) {
            return Promise.ofException(crossTenantException());
        }
    }

    private static final class CrossTenantRejectingApprovalService implements ApprovalWorkflowService {
        @Override
        public Promise<ApprovalRecord> submitForApproval(
                DmOperationContext ctx,
                ApprovalWorkflowService.SubmitForApprovalCommand command) {
            return Promise.ofException(crossTenantException());
        }

        @Override
        public Promise<ApprovalRecord> recordDecision(
                DmOperationContext ctx,
                ApprovalWorkflowService.RecordApprovalDecisionCommand command) {
            return Promise.ofException(crossTenantException());
        }

        @Override
        public Promise<Optional<ApprovalRecord>> getApprovalStatus(
                DmOperationContext ctx, String requestId) {
            return Promise.ofException(crossTenantException());
        }

        @Override
        public Promise<List<ApprovalRecord>> listPendingApprovals(
                DmOperationContext ctx, String subjectId) {
            return Promise.ofException(crossTenantException());
        }

        @Override
        public Promise<List<ApprovalRecord>> listPendingApprovalsForWorkspace(
                DmOperationContext ctx, String workspaceId) {
            return Promise.ofException(crossTenantException());
        }

        @Override
        public Promise<Optional<com.ghatana.digitalmarketing.domain.approval.ApprovalSnapshot>> getSnapshot(
                DmOperationContext ctx, String requestId) {
            return Promise.ofException(crossTenantException());
        }
    }
}
