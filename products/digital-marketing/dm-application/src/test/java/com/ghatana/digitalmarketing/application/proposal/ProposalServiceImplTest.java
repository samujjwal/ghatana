package com.ghatana.digitalmarketing.application.proposal;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.proposal.Proposal;
import com.ghatana.digitalmarketing.domain.proposal.ProposalStatus;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ProposalServiceImpl")
class ProposalServiceImplTest extends EventloopTestBase {

    private RecordingKernelAdapter kernelAdapter;
    private InMemoryProposalRepository repository;
    private ProposalServiceImpl service;
    private DmOperationContext ctx;

    @BeforeEach
    void setUp() {
        kernelAdapter = new RecordingKernelAdapter();
        repository = new InMemoryProposalRepository();
        service = new ProposalServiceImpl(kernelAdapter, repository);
        ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("owner-1"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .idempotencyKey(DmIdempotencyKey.of("idk-1"))
            .build();
    }

    private static ProposalService.GenerateProposalCommand validCommand() {
        return new ProposalService.GenerateProposalCommand(
            "strat-1", "tmpl-1", "v1.0", "Standard assumptions");
    }

    @Test
    @DisplayName("generates proposal in DRAFT status with deliverables and pricing options")
    void shouldGenerateProposal() {
        Proposal p = runPromise(() -> service.generateProposal(ctx, validCommand()));

        assertThat(p.getStatus()).isEqualTo(ProposalStatus.DRAFT);
        assertThat(p.getWorkspaceId()).isEqualTo(DmWorkspaceId.of("ws-1"));
        assertThat(p.getStrategyId()).isEqualTo("strat-1");
        assertThat(p.getTemplateId()).isEqualTo("tmpl-1");
        assertThat(p.getTemplateVersion()).isEqualTo("v1.0");
        assertThat(p.getDeliverables()).isNotEmpty();
        assertThat(p.getPricingOptions()).isNotEmpty();
        assertThat(p.getModelVersion()).isEqualTo(ProposalServiceImpl.MODEL_VERSION);
        assertThat(p.getGeneratedBy()).isEqualTo("owner-1");
        assertThat(p.getGeneratedAt()).isNotNull();
        assertThat(p.getApprovedBy()).isNull();
        assertThat(p.getReviewedAt()).isNull();
        assertThat(kernelAdapter.lastAuditAction).isEqualTo("proposal-generated");
    }

    @Test
    @DisplayName("uses blank assumptions fallback when empty string provided")
    void shouldUseFallbackAssumptionsWhenBlank() {
        ProposalService.GenerateProposalCommand cmd =
            new ProposalService.GenerateProposalCommand("strat-1", "tmpl-1", "v1.0", "");
        Proposal p = runPromise(() -> service.generateProposal(ctx, cmd));
        assertThat(p.getAssumptions()).isNotBlank();
    }

    @Test
    @DisplayName("generateProposal denied returns SecurityException")
    void shouldDenyUnauthorizedGenerate() {
        kernelAdapter.denyAll = true;
        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.generateProposal(ctx, validCommand())));
    }

    @Test
    @DisplayName("null ctx throws NullPointerException on generate")
    void nullCtxThrowsOnGenerate() {
        assertThatThrownBy(() -> runPromise(() -> service.generateProposal(null, validCommand())))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("null command throws NullPointerException on generate")
    void nullCommandThrowsOnGenerate() {
        assertThatThrownBy(() -> runPromise(() -> service.generateProposal(ctx, null)))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("getProposal returns the latest saved proposal")
    void shouldGetLatestProposal() {
        runPromise(() -> service.generateProposal(ctx, validCommand()));
        Proposal p = runPromise(() -> service.getProposal(ctx));
        assertThat(p.getStrategyId()).isEqualTo("strat-1");
    }

    @Test
    @DisplayName("getProposal when none saved throws NoSuchElementException")
    void shouldThrowWhenNoneExists() {
        assertThatExceptionOfType(NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() -> service.getProposal(ctx)));
    }

    @Test
    @DisplayName("getProposal denied returns SecurityException")
    void shouldDenyUnauthorizedGet() {
        kernelAdapter.denyAll = true;
        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.getProposal(ctx)));
    }

    @Test
    @DisplayName("submitForReview transitions DRAFT to PENDING_REVIEW")
    void shouldSubmitForReview() {
        Proposal draft = runPromise(() -> service.generateProposal(ctx, validCommand()));
        Proposal submitted = runPromise(() -> service.submitForReview(ctx, draft.getProposalId()));

        assertThat(submitted.getStatus()).isEqualTo(ProposalStatus.PENDING_REVIEW);
        assertThat(kernelAdapter.lastAuditAction).isEqualTo("proposal-submitted");
    }

    @Test
    @DisplayName("submitForReview for missing proposal throws NoSuchElementException")
    void shouldThrowOnMissingProposalSubmit() {
        assertThatExceptionOfType(NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() -> service.submitForReview(ctx, "nonexistent")));
    }

    @Test
    @DisplayName("submitForReview denied returns SecurityException")
    void shouldDenyUnauthorizedSubmit() {
        Proposal draft = runPromise(() -> service.generateProposal(ctx, validCommand()));
        kernelAdapter.denyAll = true;
        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.submitForReview(ctx, draft.getProposalId())));
    }

    @Test
    @DisplayName("submitForReview from non-DRAFT status throws IllegalStateException")
    void shouldThrowOnDoubleSubmit() {
        Proposal draft = runPromise(() -> service.generateProposal(ctx, validCommand()));
        runPromise(() -> service.submitForReview(ctx, draft.getProposalId()));
        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(() -> runPromise(() -> service.submitForReview(ctx, draft.getProposalId())));
    }

    @Test
    @DisplayName("approveProposal transitions PENDING_REVIEW to APPROVED with approver")
    void shouldApproveProposal() {
        Proposal draft = runPromise(() -> service.generateProposal(ctx, validCommand()));
        runPromise(() -> service.submitForReview(ctx, draft.getProposalId()));
        Proposal approved = runPromise(() -> service.approveProposal(ctx, draft.getProposalId()));

        assertThat(approved.getStatus()).isEqualTo(ProposalStatus.APPROVED);
        assertThat(approved.getApprovedBy()).isEqualTo("owner-1");
        assertThat(approved.getReviewedAt()).isNotNull();
        assertThat(kernelAdapter.lastAuditAction).isEqualTo("proposal-approved");
    }

    @Test
    @DisplayName("approveProposal for missing proposal throws NoSuchElementException")
    void shouldThrowOnMissingProposalApprove() {
        assertThatExceptionOfType(NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() -> service.approveProposal(ctx, "nonexistent")));
    }

    @Test
    @DisplayName("approveProposal denied returns SecurityException")
    void shouldDenyUnauthorizedApprove() {
        Proposal draft = runPromise(() -> service.generateProposal(ctx, validCommand()));
        runPromise(() -> service.submitForReview(ctx, draft.getProposalId()));
        kernelAdapter.denyAll = true;
        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.approveProposal(ctx, draft.getProposalId())));
    }

    @Test
    @DisplayName("approveProposal from DRAFT throws IllegalStateException")
    void shouldThrowOnApproveFromDraft() {
        Proposal draft = runPromise(() -> service.generateProposal(ctx, validCommand()));
        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(() -> runPromise(() -> service.approveProposal(ctx, draft.getProposalId())));
    }

    @Test
    @DisplayName("constructor rejects null kernelAdapter")
    void constructorRejectsNullAdapter() {
        assertThatThrownBy(() -> new ProposalServiceImpl(null, repository))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("constructor rejects null repository")
    void constructorRejectsNullRepository() {
        assertThatThrownBy(() -> new ProposalServiceImpl(kernelAdapter, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("command rejects blank strategyId")
    void commandRejectsBlankStrategyId() {
        assertThatThrownBy(() -> new ProposalService.GenerateProposalCommand("", "tmpl-1", "v1.0", ""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("strategyId");
    }

    @Test
    @DisplayName("command rejects blank templateId")
    void commandRejectsBlankTemplateId() {
        assertThatThrownBy(() -> new ProposalService.GenerateProposalCommand("strat-1", "", "v1.0", ""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("templateId");
    }

    @Test
    @DisplayName("command rejects blank templateVersion")
    void commandRejectsBlankTemplateVersion() {
        assertThatThrownBy(() -> new ProposalService.GenerateProposalCommand("strat-1", "tmpl-1", "", ""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("templateVersion");
    }

    @Test
    @DisplayName("command rejects null assumptions")
    void commandRejectsNullAssumptions() {
        assertThatThrownBy(() -> new ProposalService.GenerateProposalCommand("strat-1", "tmpl-1", "v1.0", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("assumptions");
    }

    // ---- test doubles ----

    private static final class RecordingKernelAdapter implements DigitalMarketingKernelAdapter {
        boolean denyAll = false;
        String lastAuditAction;

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }

        @Override
        public Promise<Boolean> isAuthorized(DmOperationContext ctx, String resource, String action) {
            return Promise.of(!denyAll);
        }

        @Override
        public Promise<String> recordAudit(DmOperationContext ctx, String entityId, String action,
                Map<String, Object> attributes) {
            this.lastAuditAction = action;
            return Promise.of("audit-id");
        }

        @Override
        public Promise<Boolean> verifyConsent(DmOperationContext ctx, String subjectId, String purpose) {
            return Promise.of(true);
        }

        @Override
        public Promise<String> requestApproval(
                DmOperationContext ctx,
                String operationType,
                String subjectId,
                String description) {
            return Promise.of("approval-1");
        }
    }

    private static final class InMemoryProposalRepository implements ProposalRepository {
        private final ConcurrentHashMap<String, Proposal> store = new ConcurrentHashMap<>();
        private volatile String latestId;

        @Override
        public Promise<Proposal> save(Proposal proposal) {
            store.put(proposal.getProposalId(), proposal);
            latestId = proposal.getProposalId();
            return Promise.of(proposal);
        }

        @Override
        public Promise<Optional<Proposal>> findLatestByWorkspace(DmWorkspaceId workspaceId) {
            return Promise.of(Optional.ofNullable(latestId).map(store::get));
        }

        @Override
        public Promise<Optional<Proposal>> findById(String proposalId) {
            return Promise.of(Optional.ofNullable(store.get(proposalId)));
        }
    }
}
