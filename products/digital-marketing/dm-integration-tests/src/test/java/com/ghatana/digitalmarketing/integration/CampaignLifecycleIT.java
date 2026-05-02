package com.ghatana.digitalmarketing.integration;

import com.ghatana.digitalmarketing.application.campaign.CampaignRepository;
import com.ghatana.digitalmarketing.application.campaign.CampaignComplianceViolationException;
import com.ghatana.digitalmarketing.application.campaign.CampaignService;
import com.ghatana.digitalmarketing.application.campaign.CampaignServiceImpl;
import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.campaign.Campaign;
import com.ghatana.digitalmarketing.domain.campaign.CampaignStatus;
import com.ghatana.digitalmarketing.domain.campaign.CampaignType;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.plugin.compliance.CompliancePlugin;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for the DMOS Campaign lifecycle.
 *
 * <p>Uses an in-memory {@link CampaignRepository} and mock kernel + compliance plugins
 * to verify that the application service correctly orchestrates domain transitions.</p>
 */
@DisplayName("Campaign Lifecycle Integration")
@ExtendWith(MockitoExtension.class)
class CampaignLifecycleIT extends EventloopTestBase {

    @Mock private DigitalMarketingKernelAdapter kernelAdapter;
    @Mock private CompliancePlugin compliancePlugin;

    private CampaignService campaignService;
    private DmOperationContext writeCtx;
    private DmOperationContext readCtx;

    @BeforeEach
    void setUp() {
        CampaignRepository inMemoryRepo = new InMemoryCampaignRepository();
        campaignService = new CampaignServiceImpl(kernelAdapter, inMemoryRepo, compliancePlugin);

        writeCtx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-42"))
            .correlationId(DmCorrelationId.generate())
            .idempotencyKey(DmIdempotencyKey.generate())
            .build();

        readCtx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-42"))
            .correlationId(DmCorrelationId.generate())
            .build();

        // Default kernel stub: always authorized, audit succeeds
        lenient().when(kernelAdapter.isAuthorized(any(), any(), any()))
            .thenReturn(Promise.of(true));
        lenient().when(kernelAdapter.recordAudit(any(), any(), any(), any()))
            .thenReturn(Promise.of("audit-entry-1"));
    }

    // -----------------------------------------------------------------------
    // Full lifecycle: DRAFT → LAUNCHED → PAUSED → LAUNCHED → COMPLETED
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("full lifecycle: create → launch → pause → resume → get")
    void shouldExecuteFullLifecycle() {
        stubPassingCompliance();

        // Create
        Campaign created = runPromise(() -> campaignService.createCampaign(writeCtx,
            new CampaignService.CreateCampaignCommand("Q4 Acquisition", CampaignType.EMAIL)));
        assertThat(created.getStatus()).isEqualTo(CampaignStatus.DRAFT);
        String id = created.getId();

        // Launch
        Campaign launched = runPromise(() -> campaignService.launchCampaign(writeCtx, id));
        assertThat(launched.getStatus()).isEqualTo(CampaignStatus.LAUNCHED);

        // Pause
        Campaign paused = runPromise(() -> campaignService.pauseCampaign(writeCtx, id));
        assertThat(paused.getStatus()).isEqualTo(CampaignStatus.PAUSED);

        // Resume (launch again)
        Campaign resumed = runPromise(() -> campaignService.launchCampaign(writeCtx, id));
        assertThat(resumed.getStatus()).isEqualTo(CampaignStatus.LAUNCHED);

        // Get — persisted state is LAUNCHED
        Campaign fetched = runPromise(() -> campaignService.getCampaign(readCtx, id));
        assertThat(fetched.getStatus()).isEqualTo(CampaignStatus.LAUNCHED);
    }

    // -----------------------------------------------------------------------
    // Authorization enforcement
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("create is denied when not authorized")
    void shouldDenyCreateWhenNotAuthorized() {
        when(kernelAdapter.isAuthorized(any(), eq("campaigns/*"), eq("write")))
            .thenReturn(Promise.of(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> campaignService.createCampaign(writeCtx,
                new CampaignService.CreateCampaignCommand("Blocked", CampaignType.SOCIAL))));
    }

    @Test
    @DisplayName("launch is denied when not authorized")
    void shouldDenyLaunchWhenNotAuthorized() {
        stubPassingCompliance();
        // Create with normal auth
        Campaign created = runPromise(() -> campaignService.createCampaign(writeCtx,
            new CampaignService.CreateCampaignCommand("Campaign", CampaignType.EMAIL)));

        // Then revoke launch authorization
        when(kernelAdapter.isAuthorized(any(), contains(created.getId()), eq("launch")))
            .thenReturn(Promise.of(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> campaignService.launchCampaign(writeCtx, created.getId())));
    }

    // -----------------------------------------------------------------------
    // Compliance enforcement
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("launch is blocked when compliance preflight fails")
    void shouldBlockLaunchOnComplianceFailure() {
        Campaign created = runPromise(() -> campaignService.createCampaign(writeCtx,
            new CampaignService.CreateCampaignCommand("Non-Compliant", CampaignType.EMAIL)));

        CompliancePlugin.ComplianceResult failResult = new CompliancePlugin.ComplianceResult(
            false,
            List.of(new CompliancePlugin.ComplianceViolation(
                "CP-001", "No approved content", CompliancePlugin.ComplianceRule.Severity.HIGH, Map.of()
            )),
            "DM_CAMPAIGN_PREFLIGHT",
            Instant.now()
        );
        when(compliancePlugin.evaluate(any(), any())).thenReturn(Promise.of(failResult));

        assertThatExceptionOfType(CampaignComplianceViolationException.class)
            .isThrownBy(() -> runPromise(() -> campaignService.launchCampaign(writeCtx, created.getId())));
    }

    // -----------------------------------------------------------------------
    // Audit recording
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("create and launch each record distinct audit events")
    void shouldRecordAuditForCreateAndLaunch() {
        stubPassingCompliance();

        Campaign created = runPromise(() -> campaignService.createCampaign(writeCtx,
            new CampaignService.CreateCampaignCommand("Audited", CampaignType.EMAIL)));
        runPromise(() -> campaignService.launchCampaign(writeCtx, created.getId()));

        verify(kernelAdapter).recordAudit(any(), any(), eq("create"), any());
        verify(kernelAdapter).recordAudit(any(), any(), eq("launch"), any());
    }

    // -----------------------------------------------------------------------
    // Persistence scoping
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("campaigns in different workspaces are isolated")
    void shouldIsolateCampaignsByWorkspace() {
        stubPassingCompliance();
        DmOperationContext wsACtx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-A"))
            .actor(ActorRef.user("user-1"))
            .correlationId(DmCorrelationId.generate())
            .idempotencyKey(DmIdempotencyKey.generate())
            .build();

        DmOperationContext wsBCtx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-B"))
            .actor(ActorRef.user("user-2"))
            .correlationId(DmCorrelationId.generate())
            .idempotencyKey(DmIdempotencyKey.generate())
            .build();

        Campaign inA = runPromise(() -> campaignService.createCampaign(wsACtx,
            new CampaignService.CreateCampaignCommand("Campaign A", CampaignType.EMAIL)));

        // Looking up the campaign from workspace B should not find it
        DmOperationContext wsBReadCtx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-B"))
            .actor(ActorRef.user("user-2"))
            .correlationId(DmCorrelationId.generate())
            .build();

        assertThatExceptionOfType(NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() -> campaignService.getCampaign(wsBReadCtx, inA.getId())));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private void stubPassingCompliance() {
        lenient().when(compliancePlugin.evaluate(any(), any()))
            .thenReturn(Promise.of(new CompliancePlugin.ComplianceResult(
                true, List.of(), "DM_CAMPAIGN_PREFLIGHT", Instant.now()
            )));
    }

    /**
     * Simple, non-thread-safe in-memory campaign store for integration tests.
     * Scopes campaigns by workspaceId + campaignId.
     */
    private static final class InMemoryCampaignRepository implements CampaignRepository {

        /** Key: workspaceId + ":" + campaignId */
        private final ConcurrentHashMap<String, Campaign> store = new ConcurrentHashMap<>();

        @Override
        public Promise<Campaign> save(Campaign campaign) {
            String key = key(campaign.getWorkspaceId().getValue(), campaign.getId());
            store.put(key, campaign);
            return Promise.of(campaign);
        }

        @Override
        public Promise<Optional<Campaign>> findById(DmWorkspaceId workspaceId, String campaignId) {
            return Promise.of(Optional.ofNullable(store.get(key(workspaceId.getValue(), campaignId))));
        }

        private static String key(String wsId, String campaignId) {
            return wsId + ":" + campaignId;
        }
    }
}
