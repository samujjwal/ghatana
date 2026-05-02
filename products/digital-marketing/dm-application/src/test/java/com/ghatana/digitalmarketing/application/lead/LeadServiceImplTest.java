package com.ghatana.digitalmarketing.application.lead;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.application.suppression.SuppressionRepository;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.lead.Lead;
import com.ghatana.digitalmarketing.domain.lead.LeadStatus;
import com.ghatana.digitalmarketing.domain.suppression.SuppressionEntry;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("LeadServiceImpl")
class LeadServiceImplTest extends EventloopTestBase {

    private RecordingKernelAdapter kernelAdapter;
    private InMemoryLeadRepository repository;
    private InMemorySuppressionRepository suppressionRepository;
    private LeadServiceImpl service;
    private DmOperationContext ctx;

    @BeforeEach
    void setUp() {
        kernelAdapter = new RecordingKernelAdapter();
        repository    = new InMemoryLeadRepository();
        suppressionRepository = new InMemorySuppressionRepository();
        service = new LeadServiceImpl(kernelAdapter, repository, suppressionRepository);

        ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-42"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .idempotencyKey(DmIdempotencyKey.of("idk-1"))
            .build();

        kernelAdapter.setDefaultAuthorization(true);
    }

    @Test
    @DisplayName("constructor throws on null dependencies")
    void shouldRejectNullDependencies() {
        assertThatNullPointerException()
            .isThrownBy(() -> new LeadServiceImpl(null, repository, suppressionRepository));
        assertThatNullPointerException()
            .isThrownBy(() -> new LeadServiceImpl(kernelAdapter, null, suppressionRepository));
        assertThatNullPointerException()
            .isThrownBy(() -> new LeadServiceImpl(kernelAdapter, repository, null));
    }

    @Test
    @DisplayName("captureLead creates NEW lead and records audit")
    void shouldCaptureLead() {
        Lead lead = runPromise(() -> service.captureLead(
            ctx,
            new LeadService.CaptureLeadCommand("camp-1", "alice@example.com", "Alice", "Smith", null, "landing-page")));

        assertThat(lead.getId()).isNotBlank();
        assertThat(lead.getEmail()).isEqualTo("alice@example.com");
        assertThat(lead.getStatus()).isEqualTo(LeadStatus.NEW);
        assertThat(lead.getCampaignId()).isEqualTo("camp-1");
        assertThat(kernelAdapter.auditActions()).contains("lead-captured");
    }

    @Test
    @DisplayName("CaptureLeadCommand rejects blank email or campaignId")
    void shouldRejectBlankEmailOrCampaign() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new LeadService.CaptureLeadCommand("camp-1", "", null, null, null, null));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new LeadService.CaptureLeadCommand("", "email@x.com", null, null, null, null));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new LeadService.CaptureLeadCommand("camp-1", null, null, null, null, null));
    }

    @Test
    @DisplayName("captureLead rejects unauthorized actor")
    void shouldDenyCaptureWhenNotAuthorized() {
        kernelAdapter.setAuthorization("leads/*", "write", false);

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.captureLead(
                ctx,
                new LeadService.CaptureLeadCommand("camp-1", "bob@x.com", null, null, null, null))));
    }

    @Test
    @DisplayName("captureLead rejects duplicate email in same campaign")
    void shouldRejectDuplicateLead() {
        runPromise(() -> service.captureLead(
            ctx,
            new LeadService.CaptureLeadCommand("camp-1", "dup@x.com", null, null, null, null)));

        assertThatIllegalArgumentException()
            .isThrownBy(() -> runPromise(() -> service.captureLead(
                ctx,
                new LeadService.CaptureLeadCommand("camp-1", "dup@x.com", null, null, null, null))));
    }

    @Test
    @DisplayName("captureLead rejects suppressed email")
    void shouldRejectSuppressedLead() {
        suppressionRepository.setSuppressed(ctx.getWorkspaceId(), "suppressed@x.com");

        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> runPromise(() -> service.captureLead(
                ctx,
                new LeadService.CaptureLeadCommand("camp-1", "suppressed@x.com", null, null, null, null))));
    }

    @Test
    @DisplayName("qualifyLead transitions NEW lead to QUALIFIED")
    void shouldQualifyLead() {
        Lead captured = runPromise(() -> service.captureLead(
            ctx,
            new LeadService.CaptureLeadCommand("camp-1", "carol@x.com", null, null, null, null)));

        Lead qualified = runPromise(() -> service.qualifyLead(ctx, captured.getId()));

        assertThat(qualified.getStatus()).isEqualTo(LeadStatus.QUALIFIED);
        assertThat(kernelAdapter.auditActions()).contains("lead-qualified");
    }

    @Test
    @DisplayName("qualifyLead throws on missing lead")
    void shouldThrowWhenQualifyingMissingLead() {
        assertThatExceptionOfType(NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() -> service.qualifyLead(ctx, "missing")));
    }

    @Test
    @DisplayName("convertLead transitions QUALIFIED lead to CONVERTED")
    void shouldConvertLead() {
        Lead captured = runPromise(() -> service.captureLead(
            ctx,
            new LeadService.CaptureLeadCommand("camp-1", "dave@x.com", null, null, null, null)));
        runPromise(() -> service.qualifyLead(ctx, captured.getId()));

        Lead converted = runPromise(() -> service.convertLead(ctx, captured.getId()));

        assertThat(converted.getStatus()).isEqualTo(LeadStatus.CONVERTED);
        assertThat(kernelAdapter.auditActions()).contains("lead-converted");
    }

    @Test
    @DisplayName("convertLead throws if lead is still NEW")
    void shouldRejectConvertFromNew() {
        Lead captured = runPromise(() -> service.captureLead(
            ctx,
            new LeadService.CaptureLeadCommand("camp-1", "eve@x.com", null, null, null, null)));

        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(() -> runPromise(() -> service.convertLead(ctx, captured.getId())));
    }

    @Test
    @DisplayName("disqualifyLead sets DISQUALIFIED status")
    void shouldDisqualifyLead() {
        Lead captured = runPromise(() -> service.captureLead(
            ctx,
            new LeadService.CaptureLeadCommand("camp-1", "frank@x.com", null, null, null, null)));

        Lead disqualified = runPromise(() -> service.disqualifyLead(ctx, captured.getId()));

        assertThat(disqualified.getStatus()).isEqualTo(LeadStatus.DISQUALIFIED);
        assertThat(kernelAdapter.auditActions()).contains("lead-disqualified");
    }

    @Test
    @DisplayName("getLead returns existing lead")
    void shouldGetLead() {
        Lead captured = runPromise(() -> service.captureLead(
            ctx,
            new LeadService.CaptureLeadCommand("camp-1", "grace@x.com", null, null, null, null)));

        Lead found = runPromise(() -> service.getLead(ctx, captured.getId()));
        assertThat(found.getId()).isEqualTo(captured.getId());
    }

    @Test
    @DisplayName("getLead enforces read authorization")
    void shouldDenyGetWhenNotAuthorized() {
        Lead captured = runPromise(() -> service.captureLead(
            ctx,
            new LeadService.CaptureLeadCommand("camp-1", "henry@x.com", null, null, null, null)));

        kernelAdapter.setAuthorization("leads/" + captured.getId(), "read", false);

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.getLead(ctx, captured.getId())));
    }

    // -----------------------------------------------------------------------
    // Test doubles
    // -----------------------------------------------------------------------

    private static final class InMemoryLeadRepository implements LeadRepository {
        private final ConcurrentHashMap<String, Lead> store = new ConcurrentHashMap<>();

        @Override
        public Promise<Lead> save(Lead lead) {
            store.put(lead.getWorkspaceId().getValue() + ":" + lead.getId(), lead);
            return Promise.of(lead);
        }

        @Override
        public Promise<Optional<Lead>> findById(DmWorkspaceId workspaceId, String leadId) {
            return Promise.of(Optional.ofNullable(
                store.get(workspaceId.getValue() + ":" + leadId)));
        }

        @Override
        public Promise<List<Lead>> findByCampaign(DmWorkspaceId workspaceId, String campaignId) {
            List<Lead> result = store.values().stream()
                .filter(l -> l.getWorkspaceId().equals(workspaceId) && l.getCampaignId().equals(campaignId))
                .toList();
            return Promise.of(result);
        }

        @Override
        public Promise<Boolean> existsByEmail(DmWorkspaceId workspaceId, String campaignId, String email) {
            boolean exists = store.values().stream()
                .anyMatch(l -> l.getWorkspaceId().equals(workspaceId)
                    && l.getCampaignId().equals(campaignId)
                    && l.getEmail().equals(email));
            return Promise.of(exists);
        }
    }

    private static final class InMemorySuppressionRepository implements SuppressionRepository {
        private final ConcurrentHashMap<String, SuppressionEntry> store = new ConcurrentHashMap<>();

        void setSuppressed(DmWorkspaceId workspaceId, String email) {
            SuppressionEntry entry = SuppressionEntry.builder()
                .id("sup-" + email)
                .workspaceId(workspaceId)
                .email(email)
                .reason("test")
                .active(true)
                .createdAt(java.time.Instant.now())
                .updatedAt(java.time.Instant.now())
                .createdBy("test")
                .build();
            store.put(workspaceId.getValue() + ":" + email, entry);
        }

        @Override
        public Promise<SuppressionEntry> save(SuppressionEntry entry) {
            store.put(entry.getWorkspaceId().getValue() + ":" + entry.getEmail(), entry);
            return Promise.of(entry);
        }

        @Override
        public Promise<Optional<SuppressionEntry>> findActiveByEmail(DmWorkspaceId workspaceId, String email) {
            SuppressionEntry found = store.get(workspaceId.getValue() + ":" + email);
            if (found != null && found.isActive()) {
                return Promise.of(Optional.of(found));
            }
            return Promise.of(Optional.empty());
        }
    }

    private static final class RecordingKernelAdapter implements DigitalMarketingKernelAdapter {
        private final ConcurrentHashMap<String, Boolean> decisionMap = new ConcurrentHashMap<>();
        private volatile boolean defaultAuthorization = true;
        private final List<String> auditActions = new ArrayList<>();

        void setDefaultAuthorization(boolean allowed) {
            defaultAuthorization = allowed;
        }

        void setAuthorization(String resource, String action, boolean allowed) {
            decisionMap.put(resource + "|" + action, allowed);
        }

        List<String> auditActions() {
            return auditActions;
        }

        @Override
        public void start() { }

        @Override
        public void stop() { }

        @Override
        public Promise<Boolean> isAuthorized(DmOperationContext context, String resource, String action) {
            return Promise.of(decisionMap.getOrDefault(resource + "|" + action, defaultAuthorization));
        }

        @Override
        public Promise<Boolean> verifyConsent(DmOperationContext context, String subjectId, String purpose) {
            return Promise.of(true);
        }

        @Override
        public Promise<String> requestApproval(
                DmOperationContext context,
                String operationType,
                String subjectId,
                String description) {
            return Promise.of("approval-1");
        }

        @Override
        public Promise<String> recordAudit(
                DmOperationContext context,
                String entityId,
                String action,
                Map<String, Object> attributes) {
            auditActions.add(action);
            return Promise.of("audit-1");
        }
    }
}
