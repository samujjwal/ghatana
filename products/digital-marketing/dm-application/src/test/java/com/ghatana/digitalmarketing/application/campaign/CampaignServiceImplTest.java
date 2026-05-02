package com.ghatana.digitalmarketing.application.campaign;

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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CampaignServiceImpl}.
 */
@DisplayName("CampaignServiceImpl")
@ExtendWith(MockitoExtension.class)
class CampaignServiceImplTest extends EventloopTestBase {

    @Mock private DigitalMarketingKernelAdapter kernelAdapter;
    @Mock private CampaignRepository repository;
    @Mock private CompliancePlugin compliancePlugin;

    private CampaignServiceImpl service;

    private DmOperationContext ctx;

    @BeforeEach
    void setUp() {
        service = new CampaignServiceImpl(kernelAdapter, repository, compliancePlugin);

        ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-42"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .idempotencyKey(DmIdempotencyKey.of("idk-1"))
            .build();

        // Lenient: not used by every test
        lenient().when(kernelAdapter.isAuthorized(any(), any(), any()))
            .thenReturn(Promise.of(true));
        lenient().when(kernelAdapter.recordAudit(any(), any(), any(), any()))
            .thenReturn(Promise.of("audit-1"));
    }

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("constructor throws on null kernelAdapter")
    void shouldThrowOnNullKernelAdapter() {
        assertThatNullPointerException()
            .isThrownBy(() -> new CampaignServiceImpl(null, repository, compliancePlugin));
    }

    @Test
    @DisplayName("constructor throws on null repository")
    void shouldThrowOnNullRepository() {
        assertThatNullPointerException()
            .isThrownBy(() -> new CampaignServiceImpl(kernelAdapter, null, compliancePlugin));
    }

    @Test
    @DisplayName("constructor throws on null compliancePlugin")
    void shouldThrowOnNullCompliancePlugin() {
        assertThatNullPointerException()
            .isThrownBy(() -> new CampaignServiceImpl(kernelAdapter, repository, null));
    }

    // -----------------------------------------------------------------------
    // createCampaign
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("createCampaign() creates and returns a DRAFT campaign")
    void shouldCreateCampaignInDraft() {
        when(repository.save(any())).thenAnswer(inv -> Promise.of(inv.getArgument(0)));

        Campaign created = runPromise(() -> service.createCampaign(ctx,
            new CampaignService.CreateCampaignCommand("Q4 Acquisition", CampaignType.EMAIL)));

        assertThat(created.getName()).isEqualTo("Q4 Acquisition");
        assertThat(created.getStatus()).isEqualTo(CampaignStatus.DRAFT);
        assertThat(created.getType()).isEqualTo(CampaignType.EMAIL);
        assertThat(created.getWorkspaceId()).isEqualTo(DmWorkspaceId.of("ws-1"));
        assertThat(created.getCreatedBy()).isEqualTo("user-42");
        assertThat(created.getId()).isNotBlank();
    }

    @Test
    @DisplayName("createCampaign() rejects when not authorized")
    void shouldDenyCreateWhenNotAuthorized() {
        when(kernelAdapter.isAuthorized(any(), eq("campaigns/*"), eq("write")))
            .thenReturn(Promise.of(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.createCampaign(ctx,
                new CampaignService.CreateCampaignCommand("Test", CampaignType.EMAIL))));
    }

    @Test
    @DisplayName("createCampaign() records an audit event")
    void shouldRecordAuditOnCreate() {
        when(repository.save(any())).thenAnswer(inv -> Promise.of(inv.getArgument(0)));

        runPromise(() -> service.createCampaign(ctx,
            new CampaignService.CreateCampaignCommand("Promo", CampaignType.SOCIAL)));

        verify(kernelAdapter).recordAudit(any(), any(), eq("create"), any());
    }

    // -----------------------------------------------------------------------
    // launchCampaign
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("launchCampaign() transitions DRAFT campaign to LAUNCHED")
    void shouldLaunchDraftCampaign() {
        Campaign draft = buildCampaign(CampaignStatus.DRAFT);
        when(repository.findById(any(), eq("campaign-1"))).thenReturn(Promise.of(Optional.of(draft)));
        when(repository.save(any())).thenAnswer(inv -> Promise.of(inv.getArgument(0)));
        stubPassingCompliance();

        Campaign launched = runPromise(() -> service.launchCampaign(ctx, "campaign-1"));

        assertThat(launched.getStatus()).isEqualTo(CampaignStatus.LAUNCHED);
    }

    @Test
    @DisplayName("launchCampaign() records audit on launch")
    void shouldRecordAuditOnLaunch() {
        Campaign draft = buildCampaign(CampaignStatus.DRAFT);
        when(repository.findById(any(), eq("campaign-1"))).thenReturn(Promise.of(Optional.of(draft)));
        when(repository.save(any())).thenAnswer(inv -> Promise.of(inv.getArgument(0)));
        stubPassingCompliance();

        runPromise(() -> service.launchCampaign(ctx, "campaign-1"));

        verify(kernelAdapter).recordAudit(any(), eq("campaign-1"), eq("launch"), any());
    }

    @Test
    @DisplayName("launchCampaign() throws NoSuchElementException when campaign not found")
    void shouldThrowWhenLaunchingMissingCampaign() {
        when(repository.findById(any(), any())).thenReturn(Promise.of(Optional.empty()));

        assertThatExceptionOfType(NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() -> service.launchCampaign(ctx, "missing")));
    }

    @Test
    @DisplayName("launchCampaign() rejects when not authorized")
    void shouldDenyLaunchWhenNotAuthorized() {
        when(kernelAdapter.isAuthorized(any(), contains("campaign-1"), eq("launch")))
            .thenReturn(Promise.of(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.launchCampaign(ctx, "campaign-1")));
    }

    @Test
    @DisplayName("launchCampaign() throws ComplianceViolationException when preflight fails")
    void shouldThrowOnCompliancePreflight() {
        Campaign draft = buildCampaign(CampaignStatus.DRAFT);
        when(repository.findById(any(), eq("campaign-1"))).thenReturn(Promise.of(Optional.of(draft)));
        CompliancePlugin.ComplianceResult failingResult = new CompliancePlugin.ComplianceResult(
            false,
            List.of(new CompliancePlugin.ComplianceViolation(
                "CP-001", "Missing approved content", CompliancePlugin.ComplianceRule.Severity.HIGH, Map.of()
            )),
            "DM_CAMPAIGN_PREFLIGHT",
            Instant.now()
        );
        when(compliancePlugin.evaluate(any(), any())).thenReturn(Promise.of(failingResult));

        assertThatExceptionOfType(CampaignComplianceViolationException.class)
            .isThrownBy(() -> runPromise(() -> service.launchCampaign(ctx, "campaign-1")));
    }

    // -----------------------------------------------------------------------
    // pauseCampaign
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("pauseCampaign() transitions LAUNCHED campaign to PAUSED")
    void shouldPauseLaunchedCampaign() {
        Campaign launched = buildCampaign(CampaignStatus.LAUNCHED);
        when(repository.findById(any(), eq("campaign-1"))).thenReturn(Promise.of(Optional.of(launched)));
        when(repository.save(any())).thenAnswer(inv -> Promise.of(inv.getArgument(0)));

        Campaign paused = runPromise(() -> service.pauseCampaign(ctx, "campaign-1"));

        assertThat(paused.getStatus()).isEqualTo(CampaignStatus.PAUSED);
    }

    @Test
    @DisplayName("pauseCampaign() rejects when not authorized")
    void shouldDenyPauseWhenNotAuthorized() {
        when(kernelAdapter.isAuthorized(any(), contains("campaign-1"), eq("pause")))
            .thenReturn(Promise.of(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.pauseCampaign(ctx, "campaign-1")));
    }

    // -----------------------------------------------------------------------
    // getCampaign
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("getCampaign() returns campaign by ID")
    void shouldGetCampaignById() {
        Campaign campaign = buildCampaign(CampaignStatus.DRAFT);
        when(repository.findById(any(), eq("campaign-1"))).thenReturn(Promise.of(Optional.of(campaign)));

        Campaign found = runPromise(() -> service.getCampaign(ctx, "campaign-1"));
        assertThat(found.getId()).isEqualTo("campaign-1");
    }

    @Test
    @DisplayName("getCampaign() throws NoSuchElementException when campaign not found")
    void shouldThrowWhenCampaignNotFound() {
        when(repository.findById(any(), any())).thenReturn(Promise.of(Optional.empty()));

        assertThatExceptionOfType(NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() -> service.getCampaign(ctx, "missing")));
    }

    @Test
    @DisplayName("getCampaign() rejects when not authorized")
    void shouldDenyGetWhenNotAuthorized() {
        when(kernelAdapter.isAuthorized(any(), contains("campaign-1"), eq("read")))
            .thenReturn(Promise.of(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.getCampaign(ctx, "campaign-1")));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Campaign buildCampaign(CampaignStatus status) {
        Instant now = Instant.now();
        Campaign draft = Campaign.builder()
            .id("campaign-1")
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .name("Test Campaign")
            .type(CampaignType.EMAIL)
            .status(CampaignStatus.DRAFT)
            .createdAt(now)
            .updatedAt(now)
            .createdBy("user-42")
            .build();
        if (status == CampaignStatus.LAUNCHED) return draft.launch();
        if (status == CampaignStatus.PAUSED)   return draft.launch().pause();
        if (status == CampaignStatus.COMPLETED) return draft.launch().complete();
        return draft;
    }

    private void stubPassingCompliance() {
        CompliancePlugin.ComplianceResult passing = new CompliancePlugin.ComplianceResult(
            true, List.of(), "DM_CAMPAIGN_PREFLIGHT", Instant.now()
        );
        when(compliancePlugin.evaluate(any(), any())).thenReturn(Promise.of(passing));
    }
}
