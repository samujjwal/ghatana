package com.ghatana.digitalmarketing.application.sow;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.sow.SowClause;
import com.ghatana.digitalmarketing.domain.sow.SowClauseStatus;
import com.ghatana.digitalmarketing.domain.sow.SowDraft;
import com.ghatana.digitalmarketing.domain.sow.SowRiskType;
import com.ghatana.digitalmarketing.domain.sow.SowStatus;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SowServiceImpl")
class SowServiceImplTest extends EventloopTestBase {

    private RecordingKernelAdapter kernelAdapter;
    private InMemoryClauseRepository clauseRepository;
    private InMemorySowDraftRepository draftRepository;
    private SowServiceImpl service;
    private DmOperationContext ctx;

    @BeforeEach
    void setUp() {
        kernelAdapter = new RecordingKernelAdapter();
        clauseRepository = new InMemoryClauseRepository();
        draftRepository = new InMemorySowDraftRepository();
        service = new SowServiceImpl(kernelAdapter, clauseRepository, draftRepository);
        ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("owner-1"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .idempotencyKey(DmIdempotencyKey.of("idk-1"))
            .build();
    }

    private static SowService.GenerateSowCommand validCommand() {
        return new SowService.GenerateSowCommand(
            "prop-1", "v1.0", "Standard assumptions", "No media buying");
    }

    private static SowClause approvedClause() {
        return new SowClause(
            "clause-1", "SCOPE", "v1.0",
            "The service provider agrees to deliver the described services.",
            "Legal Team", "Senior Counsel",
            LocalDate.of(2026, 1, 1), SowClauseStatus.APPROVED);
    }

    // ---- generateDraft ----

    @Test
    @DisplayName("generateDraft creates a DRAFT SOW from approved clauses")
    void shouldGenerateDraft() {
        clauseRepository.clauses.add(approvedClause());
        SowDraft draft = runPromise(() -> service.generateDraft(ctx, validCommand()));

        assertThat(draft.getStatus()).isEqualTo(SowStatus.DRAFT);
        assertThat(draft.getWorkspaceId()).isEqualTo(DmWorkspaceId.of("ws-1"));
        assertThat(draft.getProposalId()).isEqualTo("prop-1");
        assertThat(draft.getTemplateVersion()).isEqualTo("v1.0");
        assertThat(draft.getModelVersion()).isEqualTo(SowServiceImpl.MODEL_VERSION);
        assertThat(draft.getSelectedClauses()).hasSize(1);
        assertThat(draft.getDisclaimer()).isEqualTo(SowDraft.LEGAL_DISCLAIMER);
        assertThat(draft.getCreatedAt()).isNotNull();
        assertThat(kernelAdapter.lastAuditAction).isEqualTo("sow-generated");
    }

    @Test
    @DisplayName("generateDraft uses assumption fallback when blank")
    void shouldUseFallbackAssumptionsWhenBlank() {
        clauseRepository.clauses.add(approvedClause());
        SowService.GenerateSowCommand cmd =
            new SowService.GenerateSowCommand("prop-1", "v1.0", "", "");
        SowDraft draft = runPromise(() -> service.generateDraft(ctx, cmd));

        assertThat(draft.getAssumptions()).isNotBlank();
        assertThat(draft.getExclusions()).isNotBlank();
    }

    @Test
    @DisplayName("generateDraft flags AMBIGUOUS_DELIVERABLE when assumptions blank")
    void shouldFlagAmbiguousDeliverableWhenAssumptionsBlank() {
        clauseRepository.clauses.add(approvedClause());
        SowService.GenerateSowCommand cmd =
            new SowService.GenerateSowCommand("prop-1", "v1.0", "", "");
        SowDraft draft = runPromise(() -> service.generateDraft(ctx, cmd));

        assertThat(draft.getRiskFlags()).extracting(f -> f.flagType())
            .contains(SowRiskType.AMBIGUOUS_DELIVERABLE);
    }

    @Test
    @DisplayName("generateDraft flags MISSING_APPROVAL for unapproved clause")
    void shouldFlagMissingApprovalForDraftClause() {
        SowClause draftClause = new SowClause(
            "clause-d", "PAYMENT", "v1.0",
            "Payment is due within 30 days.",
            "Finance", "CFO",
            LocalDate.of(2026, 1, 1), SowClauseStatus.DRAFT);
        clauseRepository.clauses.add(draftClause);
        SowDraft draft = runPromise(() -> service.generateDraft(ctx, validCommand()));

        assertThat(draft.getRiskFlags()).extracting(f -> f.flagType())
            .contains(SowRiskType.MISSING_APPROVAL);
    }

    @Test
    @DisplayName("generateDraft flags UNSUPPORTED_GUARANTEE for guarantee language")
    void shouldFlagUnsupportedGuarantee() {
        SowClause guaranteeClause = new SowClause(
            "clause-g", "SCOPE", "v1.0",
            "We guarantee results within 30 days.",
            "Sales", "Manager",
            LocalDate.of(2026, 1, 1), SowClauseStatus.APPROVED);
        clauseRepository.clauses.add(guaranteeClause);
        SowDraft draft = runPromise(() -> service.generateDraft(ctx, validCommand()));

        assertThat(draft.getRiskFlags()).extracting(f -> f.flagType())
            .contains(SowRiskType.UNSUPPORTED_GUARANTEE);
    }

    @Test
    @DisplayName("generateDraft flags PRIVACY_ISSUE for personal data references")
    void shouldFlagPrivacyIssue() {
        SowClause privacyClause = new SowClause(
            "clause-p", "DATA", "v1.0",
            "Provider will handle personal data in compliance with GDPR.",
            "Legal", "DPO",
            LocalDate.of(2026, 1, 1), SowClauseStatus.APPROVED);
        clauseRepository.clauses.add(privacyClause);
        SowDraft draft = runPromise(() -> service.generateDraft(ctx, validCommand()));

        assertThat(draft.getRiskFlags()).extracting(f -> f.flagType())
            .contains(SowRiskType.PRIVACY_ISSUE);
    }

    @Test
    @DisplayName("generateDraft denied returns SecurityException")
    void shouldDenyUnauthorizedGenerate() {
        kernelAdapter.denyAll = true;
        clauseRepository.clauses.add(approvedClause());
        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.generateDraft(ctx, validCommand())));
    }

    @Test
    @DisplayName("null ctx throws NullPointerException on generateDraft")
    void nullCtxThrowsOnGenerate() {
        assertThatThrownBy(() -> runPromise(() -> service.generateDraft(null, validCommand())))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("null command throws NullPointerException on generateDraft")
    void nullCommandThrowsOnGenerate() {
        assertThatThrownBy(() -> runPromise(() -> service.generateDraft(ctx, null)))
            .isInstanceOf(NullPointerException.class);
    }

    // ---- getDraft ----

    @Test
    @DisplayName("getDraft returns the latest saved draft")
    void shouldGetLatestDraft() {
        clauseRepository.clauses.add(approvedClause());
        runPromise(() -> service.generateDraft(ctx, validCommand()));
        SowDraft draft = runPromise(() -> service.getDraft(ctx));
        assertThat(draft.getProposalId()).isEqualTo("prop-1");
    }

    @Test
    @DisplayName("getDraft when none saved throws NoSuchElementException")
    void shouldThrowWhenNoneExists() {
        assertThatExceptionOfType(NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() -> service.getDraft(ctx)));
    }

    @Test
    @DisplayName("getDraft denied returns SecurityException")
    void shouldDenyUnauthorizedGet() {
        kernelAdapter.denyAll = true;
        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.getDraft(ctx)));
    }

    // ---- submitForReview ----

    @Test
    @DisplayName("submitForReview transitions DRAFT to PENDING_REVIEW")
    void shouldSubmitForReview() {
        clauseRepository.clauses.add(approvedClause());
        SowDraft draft = runPromise(() -> service.generateDraft(ctx, validCommand()));
        SowDraft submitted = runPromise(() -> service.submitForReview(ctx, draft.getSowId()));

        assertThat(submitted.getStatus()).isEqualTo(SowStatus.PENDING_REVIEW);
        assertThat(kernelAdapter.lastAuditAction).isEqualTo("sow-submitted");
    }

    @Test
    @DisplayName("submitForReview for missing draft throws NoSuchElementException")
    void shouldThrowOnMissingSowSubmit() {
        assertThatExceptionOfType(NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() -> service.submitForReview(ctx, "nonexistent")));
    }

    @Test
    @DisplayName("submitForReview denied returns SecurityException")
    void shouldDenyUnauthorizedSubmit() {
        clauseRepository.clauses.add(approvedClause());
        SowDraft draft = runPromise(() -> service.generateDraft(ctx, validCommand()));
        kernelAdapter.denyAll = true;
        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.submitForReview(ctx, draft.getSowId())));
    }

    @Test
    @DisplayName("submitForReview from non-DRAFT status throws IllegalStateException")
    void shouldThrowOnDoubleSubmit() {
        clauseRepository.clauses.add(approvedClause());
        SowDraft draft = runPromise(() -> service.generateDraft(ctx, validCommand()));
        runPromise(() -> service.submitForReview(ctx, draft.getSowId()));
        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(() -> runPromise(() -> service.submitForReview(ctx, draft.getSowId())));
    }

    // ---- approveDraft ----

    @Test
    @DisplayName("approveDraft transitions PENDING_REVIEW to APPROVED with approver")
    void shouldApproveDraft() {
        clauseRepository.clauses.add(approvedClause());
        SowDraft draft = runPromise(() -> service.generateDraft(ctx, validCommand()));
        runPromise(() -> service.submitForReview(ctx, draft.getSowId()));
        SowDraft approved = runPromise(() -> service.approveDraft(ctx, draft.getSowId()));

        assertThat(approved.getStatus()).isEqualTo(SowStatus.APPROVED);
        assertThat(approved.getApprovedBy()).isEqualTo("owner-1");
        assertThat(approved.getApprovedAt()).isNotNull();
        assertThat(kernelAdapter.lastAuditAction).isEqualTo("sow-approved");
    }

    @Test
    @DisplayName("approveDraft for missing draft throws NoSuchElementException")
    void shouldThrowOnMissingSowApprove() {
        assertThatExceptionOfType(NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() -> service.approveDraft(ctx, "nonexistent")));
    }

    @Test
    @DisplayName("approveDraft denied returns SecurityException")
    void shouldDenyUnauthorizedApprove() {
        clauseRepository.clauses.add(approvedClause());
        SowDraft draft = runPromise(() -> service.generateDraft(ctx, validCommand()));
        runPromise(() -> service.submitForReview(ctx, draft.getSowId()));
        kernelAdapter.denyAll = true;
        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.approveDraft(ctx, draft.getSowId())));
    }

    @Test
    @DisplayName("approveDraft from DRAFT throws IllegalStateException")
    void shouldThrowOnApproveFromDraft() {
        clauseRepository.clauses.add(approvedClause());
        SowDraft draft = runPromise(() -> service.generateDraft(ctx, validCommand()));
        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(() -> runPromise(() -> service.approveDraft(ctx, draft.getSowId())));
    }

    // ---- exportDraft ----

    @Test
    @DisplayName("exportDraft transitions APPROVED to EXPORTED")
    void shouldExportApprovedDraft() {
        clauseRepository.clauses.add(approvedClause());
        SowDraft draft = runPromise(() -> service.generateDraft(ctx, validCommand()));
        runPromise(() -> service.submitForReview(ctx, draft.getSowId()));
        runPromise(() -> service.approveDraft(ctx, draft.getSowId()));
        SowDraft exported = runPromise(() -> service.exportDraft(ctx, draft.getSowId()));

        assertThat(exported.getStatus()).isEqualTo(SowStatus.EXPORTED);
        assertThat(kernelAdapter.lastAuditAction).isEqualTo("sow-exported");
    }

    @Test
    @DisplayName("exportDraft for missing draft throws NoSuchElementException")
    void shouldThrowOnMissingSowExport() {
        assertThatExceptionOfType(NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() -> service.exportDraft(ctx, "nonexistent")));
    }

    @Test
    @DisplayName("exportDraft denied returns SecurityException")
    void shouldDenyUnauthorizedExport() {
        clauseRepository.clauses.add(approvedClause());
        SowDraft draft = runPromise(() -> service.generateDraft(ctx, validCommand()));
        runPromise(() -> service.submitForReview(ctx, draft.getSowId()));
        runPromise(() -> service.approveDraft(ctx, draft.getSowId()));
        kernelAdapter.denyAll = true;
        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.exportDraft(ctx, draft.getSowId())));
    }

    @Test
    @DisplayName("exportDraft from DRAFT throws IllegalStateException")
    void shouldThrowOnExportFromDraft() {
        clauseRepository.clauses.add(approvedClause());
        SowDraft draft = runPromise(() -> service.generateDraft(ctx, validCommand()));
        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(() -> runPromise(() -> service.exportDraft(ctx, draft.getSowId())));
    }

    // ---- constructor and command validation ----

    @Test
    @DisplayName("constructor rejects null kernelAdapter")
    void constructorRejectsNullAdapter() {
        assertThatThrownBy(() -> new SowServiceImpl(null, clauseRepository, draftRepository))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("constructor rejects null clauseRepository")
    void constructorRejectsNullClauseRepo() {
        assertThatThrownBy(() -> new SowServiceImpl(kernelAdapter, null, draftRepository))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("constructor rejects null draftRepository")
    void constructorRejectsNullDraftRepo() {
        assertThatThrownBy(() -> new SowServiceImpl(kernelAdapter, clauseRepository, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("command rejects blank proposalId")
    void commandRejectsBlankProposalId() {
        assertThatThrownBy(() -> new SowService.GenerateSowCommand("", "v1.0", "", ""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("proposalId");
    }

    @Test
    @DisplayName("command rejects blank templateVersion")
    void commandRejectsBlankTemplateVersion() {
        assertThatThrownBy(() -> new SowService.GenerateSowCommand("prop-1", "  ", "", ""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("templateVersion");
    }

    @Test
    @DisplayName("command rejects null proposalId")
    void commandRejectsNullProposalId() {
        assertThatThrownBy(() -> new SowService.GenerateSowCommand(null, "v1.0", "", ""))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("command rejects null assumptions")
    void commandRejectsNullAssumptions() {
        assertThatThrownBy(() -> new SowService.GenerateSowCommand("prop-1", "v1.0", null, ""))
            .isInstanceOf(NullPointerException.class);
    }

    // ---- test doubles ----

    private static final class RecordingKernelAdapter implements DigitalMarketingKernelAdapter {
        boolean denyAll = false;
        String lastAuditAction;

        @Override
        public void start() {}

        @Override
        public void stop() {}

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
        public Promise<String> requestApproval(DmOperationContext ctx, String operationType,
                String subjectId, String description) {
            return Promise.of("approval-1");
        }
    }

    private static final class InMemoryClauseRepository implements SowClauseRepository {
        final List<SowClause> clauses = new ArrayList<>();

        @Override
        public Promise<List<SowClause>> findApprovedClauses() {
            return Promise.of(new ArrayList<>(clauses));
        }
    }

    private static final class InMemorySowDraftRepository implements SowDraftRepository {
        private final ConcurrentHashMap<String, SowDraft> store = new ConcurrentHashMap<>();
        private volatile String latestId;

        @Override
        public Promise<SowDraft> save(SowDraft draft) {
            store.put(draft.getSowId(), draft);
            latestId = draft.getSowId();
            return Promise.of(draft);
        }

        @Override
        public Promise<Optional<SowDraft>> findLatestByWorkspace(DmWorkspaceId workspaceId) {
            return Promise.of(Optional.ofNullable(latestId).map(store::get));
        }

        @Override
        public Promise<Optional<SowDraft>> findById(String sowId) {
            return Promise.of(Optional.ofNullable(store.get(sowId)));
        }
    }
}
