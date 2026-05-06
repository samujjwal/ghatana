package com.ghatana.digitalmarketing.application.approval;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.application.metrics.DmosMetricsCollector;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.approval.ApprovalSnapshot;
import com.ghatana.digitalmarketing.domain.approval.ApprovalTargetType;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.plugin.approval.ApprovalDecision;
import com.ghatana.plugin.approval.ApprovalRecord;
import com.ghatana.plugin.approval.ApprovalRequest;
import com.ghatana.plugin.approval.ApprovalStatus;
import com.ghatana.plugin.approval.HumanApprovalPlugin;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.ghatana.digitalmarketing.application.approval.ApprovalWorkflowService.RecordApprovalDecisionCommand;
import com.ghatana.digitalmarketing.application.approval.ApprovalWorkflowService.SubmitForApprovalCommand;
import static com.ghatana.digitalmarketing.application.approval.ApprovalWorkflowServiceImpl.ROLE_BRAND_MANAGER;
import static com.ghatana.digitalmarketing.application.approval.ApprovalWorkflowServiceImpl.ROLE_EXEC_SPONSOR;
import static com.ghatana.digitalmarketing.application.approval.ApprovalWorkflowServiceImpl.ROLE_MARKETING_DIRECTOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ApprovalWorkflowServiceImpl")
class ApprovalWorkflowServiceImplTest extends EventloopTestBase {

    private RecordingKernelAdapter      kernelAdapter;
    private InMemoryApprovalPlugin      approvalPlugin;
    private InMemorySnapshotRepository  snapshotRepository;
    private ApprovalWorkflowServiceImpl service;
    private DmOperationContext          ctx;

    @BeforeEach
    void setUp() {
        kernelAdapter      = new RecordingKernelAdapter();
        approvalPlugin     = new InMemoryApprovalPlugin();
        snapshotRepository = new InMemorySnapshotRepository();
        service = new ApprovalWorkflowServiceImpl(
            kernelAdapter, approvalPlugin, snapshotRepository, DmosMetricsCollector.noop());

        ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-test"))
            .actor(ActorRef.user("user-alice"))
            .correlationId(DmCorrelationId.generate())
            .build();
    }

    // -------------------------------------------------------------------------
    // submitForApproval — security
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("submitForApproval throws SecurityException when actor not authorised")
    void submitDenied() {
        kernelAdapter.denyAll = true;
        SubmitForApprovalCommand cmd = new SubmitForApprovalCommand(
            ApprovalTargetType.CONTENT_VERSION, "cv-1", "Approve content", 2, ROLE_BRAND_MANAGER, null);

        assertThatThrownBy(() -> runPromise(() -> service.submitForApproval(ctx, cmd)))
            .isInstanceOf(SecurityException.class);
    }

    // -------------------------------------------------------------------------
    // submitForApproval — happy path
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("submitForApproval returns PENDING approval record")
    void submitCreatesApprovalRecord() {
        SubmitForApprovalCommand cmd = new SubmitForApprovalCommand(
            ApprovalTargetType.CONTENT_VERSION, "cv-1", "Approve this ad copy", 2, ROLE_BRAND_MANAGER, "vr-1");

        ApprovalRecord record = runPromise(() -> service.submitForApproval(ctx, cmd));

        assertThat(record).isNotNull();
        assertThat(record.status()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(record.subjectId()).isEqualTo("cv-1");
        assertThat(record.requestedBy()).isEqualTo("user-alice");
    }

    @Test
    @DisplayName("submitForApproval persists snapshot with correct risk and role")
    void submitPersistsSnapshot() {
        SubmitForApprovalCommand cmd = new SubmitForApprovalCommand(
            ApprovalTargetType.STRATEGY, "strat-1", "Approve strategy", 3, ROLE_MARKETING_DIRECTOR, null);

        ApprovalRecord record = runPromise(() -> service.submitForApproval(ctx, cmd));

        Optional<ApprovalSnapshot> snapshot = runPromise(() ->
            snapshotRepository.findByRequestId("ws-test", record.requestId()));

        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().targetType()).isEqualTo(ApprovalTargetType.STRATEGY);
        assertThat(snapshot.get().riskLevel()).isEqualTo(3);
        assertThat(snapshot.get().requiredApproverRole()).isEqualTo(ROLE_MARKETING_DIRECTOR);
    }

    @Test
    @DisplayName("submitForApproval records audit event")
    void submitRecordsAudit() {
        SubmitForApprovalCommand cmd = new SubmitForApprovalCommand(
            ApprovalTargetType.BUDGET, "budget-1", "Approve budget increase", 4, ROLE_EXEC_SPONSOR, null);

        runPromise(() -> service.submitForApproval(ctx, cmd));

        assertThat(kernelAdapter.auditedActions).contains("approval-submitted");
    }

    @Test
    @DisplayName("submitForApproval stores validationResultId in snapshot when provided")
    void submitStoresValidationResultId() {
        SubmitForApprovalCommand cmd = new SubmitForApprovalCommand(
            ApprovalTargetType.CONTENT_VERSION, "cv-2", "Approve content", 1, ROLE_BRAND_MANAGER, "vr-ref-123");

        ApprovalRecord record = runPromise(() -> service.submitForApproval(ctx, cmd));

        Optional<ApprovalSnapshot> snapshot = runPromise(() ->
            snapshotRepository.findByRequestId("ws-test", record.requestId()));

        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().validationResultId()).isEqualTo("vr-ref-123");
    }

    // -------------------------------------------------------------------------
    // recordDecision — security
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("recordDecision throws SecurityException when actor not authorised")
    void decideDenied() {
        kernelAdapter.denyAll = true;
        RecordApprovalDecisionCommand cmd = new RecordApprovalDecisionCommand(
            "req-1", ApprovalDecision.APPROVED, null);

        assertThatThrownBy(() -> runPromise(() -> service.recordDecision(ctx, cmd)))
            .isInstanceOf(SecurityException.class);
    }

    // -------------------------------------------------------------------------
    // recordDecision — approve path
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("recordDecision APPROVED transitions record to APPROVED")
    void approveDecision() {
        ApprovalRecord pending = approvalPlugin.seedPending("req-approved", "cv-1", "user-alice");

        RecordApprovalDecisionCommand cmd = new RecordApprovalDecisionCommand(
            "req-approved", ApprovalDecision.APPROVED, "LGTM");

        ApprovalRecord result = runPromise(() -> service.recordDecision(ctx, cmd));

        assertThat(result.status()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(result.reviewerId()).isEqualTo("user-alice");
        assertThat(result.reviewerNotes()).isEqualTo("LGTM");
    }

    // -------------------------------------------------------------------------
    // recordDecision — reject path
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("recordDecision REJECTED transitions record to REJECTED")
    void rejectDecision() {
        approvalPlugin.seedPending("req-rejected", "cv-2", "user-alice");

        RecordApprovalDecisionCommand cmd = new RecordApprovalDecisionCommand(
            "req-rejected", ApprovalDecision.REJECTED, "Missing legal disclosure");

        ApprovalRecord result = runPromise(() -> service.recordDecision(ctx, cmd));

        assertThat(result.status()).isEqualTo(ApprovalStatus.REJECTED);
        assertThat(result.reviewerNotes()).isEqualTo("Missing legal disclosure");
    }

    @Test
    @DisplayName("recordDecision REJECTED without notes throws IllegalArgumentException")
    void rejectWithoutNotesThrows() {
        approvalPlugin.seedPending("req-no-notes", "cv-3", "user-alice");

        RecordApprovalDecisionCommand cmd = new RecordApprovalDecisionCommand(
            "req-no-notes", ApprovalDecision.REJECTED, null);

        assertThatThrownBy(() -> runPromise(() -> service.recordDecision(ctx, cmd)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Notes are required");
    }

    @Test
    @DisplayName("recordDecision REJECTED with blank notes throws IllegalArgumentException")
    void rejectWithBlankNotesThrows() {
        approvalPlugin.seedPending("req-blank-notes", "cv-4", "user-alice");

        RecordApprovalDecisionCommand cmd = new RecordApprovalDecisionCommand(
            "req-blank-notes", ApprovalDecision.REJECTED, "   ");

        assertThatThrownBy(() -> runPromise(() -> service.recordDecision(ctx, cmd)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("recordDecision records audit event for decision")
    void decisionRecordsAudit() {
        approvalPlugin.seedPending("req-audit", "cv-5", "user-alice");

        RecordApprovalDecisionCommand cmd = new RecordApprovalDecisionCommand(
            "req-audit", ApprovalDecision.APPROVED, null);

        runPromise(() -> service.recordDecision(ctx, cmd));

        assertThat(kernelAdapter.auditedActions).contains("approval-decision");
    }

    // -------------------------------------------------------------------------
    // getApprovalStatus
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getApprovalStatus returns PENDING record when exists")
    void getApprovalStatusExists() {
        approvalPlugin.seedPending("req-get", "cv-10", "user-alice");

        Optional<ApprovalRecord> result = runPromise(
            () -> service.getApprovalStatus(ctx, "req-get"));

        assertThat(result).isPresent();
        assertThat(result.get().requestId()).isEqualTo("req-get");
        assertThat(result.get().status()).isEqualTo(ApprovalStatus.PENDING);
    }

    @Test
    @DisplayName("getApprovalStatus returns empty when not found")
    void getApprovalStatusNotFound() {
        Optional<ApprovalRecord> result = runPromise(
            () -> service.getApprovalStatus(ctx, "req-missing"));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getApprovalStatus throws SecurityException when denied")
    void getApprovalStatusDenied() {
        kernelAdapter.denyAll = true;

        assertThatThrownBy(() -> runPromise(() -> service.getApprovalStatus(ctx, "req-1")))
            .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("getApprovalStatus throws when requestId is blank")
    void getApprovalStatusBlankId() {
        assertThatThrownBy(() -> runPromise(() -> service.getApprovalStatus(ctx, " ")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // -------------------------------------------------------------------------
    // listPendingApprovals
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("listPendingApprovals returns pending records for subject")
    void listPendingForSubject() {
        approvalPlugin.seedPending("req-a", "subject-ws1", "user-alice");
        approvalPlugin.seedPending("req-b", "subject-ws1", "user-alice");

        List<ApprovalRecord> results = runPromise(
            () -> service.listPendingApprovals(ctx, "subject-ws1"));

        assertThat(results).hasSize(2);
    }

    @Test
    @DisplayName("listPendingApprovals returns empty when none pending")
    void listPendingEmpty() {
        List<ApprovalRecord> results = runPromise(
            () -> service.listPendingApprovals(ctx, "subject-empty"));

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("listPendingApprovals throws SecurityException when denied")
    void listPendingDenied() {
        kernelAdapter.denyAll = true;

        assertThatThrownBy(() -> runPromise(() -> service.listPendingApprovals(ctx, "ws-1")))
            .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("listPendingApprovals throws when subjectId is blank")
    void listPendingBlankSubject() {
        assertThatThrownBy(() -> runPromise(() -> service.listPendingApprovals(ctx, "  ")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // -------------------------------------------------------------------------
    // getSnapshot
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getSnapshot returns stored snapshot for request")
    void getSnapshotExists() {
        SubmitForApprovalCommand cmd = new SubmitForApprovalCommand(
            ApprovalTargetType.PROPOSAL, "prop-1", "Approve proposal", 2, ROLE_BRAND_MANAGER, null);

        ApprovalRecord record = runPromise(() -> service.submitForApproval(ctx, cmd));

        Optional<ApprovalSnapshot> snapshot = runPromise(
            () -> service.getSnapshot(ctx, record.requestId()));

        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().targetId()).isEqualTo("prop-1");
        assertThat(snapshot.get().targetType()).isEqualTo(ApprovalTargetType.PROPOSAL);
    }

    @Test
    @DisplayName("getSnapshot returns empty for unknown requestId")
    void getSnapshotNotFound() {
        Optional<ApprovalSnapshot> snapshot = runPromise(
            () -> service.getSnapshot(ctx, "req-unknown"));

        assertThat(snapshot).isEmpty();
    }

    @Test
    @DisplayName("getSnapshot throws SecurityException when denied")
    void getSnapshotDenied() {
        kernelAdapter.denyAll = true;

        assertThatThrownBy(() -> runPromise(() -> service.getSnapshot(ctx, "req-1")))
            .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("getSnapshot throws when requestId is blank")
    void getSnapshotBlankId() {
        assertThatThrownBy(() -> runPromise(() -> service.getSnapshot(ctx, "")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // -------------------------------------------------------------------------
    // Routing logic
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("resolveApproverRole routes risk 1 to brand-manager")
    void routingRisk1() {
        assertThat(ApprovalWorkflowServiceImpl.resolveApproverRole(
            ApprovalTargetType.CONTENT_VERSION, 1)).isEqualTo(ROLE_BRAND_MANAGER);
    }

    @Test
    @DisplayName("resolveApproverRole routes risk 2 to brand-manager")
    void routingRisk2() {
        assertThat(ApprovalWorkflowServiceImpl.resolveApproverRole(
            ApprovalTargetType.STRATEGY, 2)).isEqualTo(ROLE_BRAND_MANAGER);
    }

    @Test
    @DisplayName("resolveApproverRole routes risk 3 to marketing-director")
    void routingRisk3() {
        assertThat(ApprovalWorkflowServiceImpl.resolveApproverRole(
            ApprovalTargetType.BUDGET, 3)).isEqualTo(ROLE_MARKETING_DIRECTOR);
    }

    @Test
    @DisplayName("resolveApproverRole routes risk 4 to exec-sponsor")
    void routingRisk4() {
        assertThat(ApprovalWorkflowServiceImpl.resolveApproverRole(
            ApprovalTargetType.CAMPAIGN_LAUNCH, 4)).isEqualTo(ROLE_EXEC_SPONSOR);
    }

    @Test
    @DisplayName("resolveApproverRole routes risk 5 to exec-sponsor")
    void routingRisk5() {
        assertThat(ApprovalWorkflowServiceImpl.resolveApproverRole(
            ApprovalTargetType.CONNECTOR_WRITE, 5)).isEqualTo(ROLE_EXEC_SPONSOR);
    }

    @Test
    @DisplayName("resolveApproverRole always routes OVERRIDE to exec-sponsor")
    void routingOverrideAlwaysExecSponsor() {
        for (int risk = 1; risk <= 5; risk++) {
            assertThat(ApprovalWorkflowServiceImpl.resolveApproverRole(
                ApprovalTargetType.OVERRIDE, risk)).isEqualTo(ROLE_EXEC_SPONSOR);
        }
    }

    // -------------------------------------------------------------------------
    // Domain — ApprovalSnapshot validation
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("ApprovalSnapshot rejects blank requestId")
    void snapshotRejectsBlankRequestId() {
        assertThatThrownBy(() -> new ApprovalSnapshot(
            " ", ApprovalTargetType.CONTENT_VERSION, "t-1", "ws-1", "desc", null, 2, "role", Instant.now(), 1L))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ApprovalSnapshot rejects invalid riskLevel")
    void snapshotRejectsInvalidRisk() {
        assertThatThrownBy(() -> new ApprovalSnapshot(
            "req-1", ApprovalTargetType.CONTENT_VERSION, "t-1", "ws-1", "desc", null, 0, "role", Instant.now(), 1L))
            .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new ApprovalSnapshot(
            "req-2", ApprovalTargetType.CONTENT_VERSION, "t-1", "ws-1", "desc", null, 6, "role", Instant.now(), 1L))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ApprovalSnapshot accepts valid fields including null validationResultId")
    void snapshotAcceptsNullValidationResultId() {
        ApprovalSnapshot snapshot = new ApprovalSnapshot(
            "req-ok", ApprovalTargetType.STRATEGY, "s-1", "ws-1",
            "Approve strategy", null, 3, ROLE_MARKETING_DIRECTOR, Instant.now(), 1L);

        assertThat(snapshot.validationResultId()).isNull();
        assertThat(snapshot.riskLevel()).isEqualTo(3);
    }

    // -------------------------------------------------------------------------
    // Test doubles
    // -------------------------------------------------------------------------

    static final class RecordingKernelAdapter implements DigitalMarketingKernelAdapter {
        boolean denyAll = false;
        final List<String> auditedActions = new ArrayList<>();

        @Override public void start() {}
        @Override public void stop() {}

        @Override
        public Promise<Boolean> isAuthorized(DmOperationContext ctx, String resource, String action) {
            return Promise.of(!denyAll);
        }

        @Override
        public Promise<String> recordAudit(DmOperationContext ctx, String entityId,
                String action, Map<String, Object> details) {
            auditedActions.add(action);
            return Promise.of("audit-id");
        }

        @Override
        public Promise<Boolean> verifyConsent(DmOperationContext ctx, String subjectId, String purpose) {
            return Promise.of(!denyAll);
        }

        @Override
        public Promise<String> requestApproval(DmOperationContext ctx, String operationType,
                String subjectId, String description) {
            return Promise.of("approval-id");
        }
    }

    static final class InMemoryApprovalPlugin implements HumanApprovalPlugin {
        private final Map<String, ApprovalRecord> store = new HashMap<>();

        /** Seeds a PENDING record for testing decision operations. */
        ApprovalRecord seedPending(String requestId, String subjectId, String requestedBy) {
            ApprovalRecord record = new ApprovalRecord(
                requestId, subjectId, requestedBy,
                "dmos-approval/test", ApprovalStatus.PENDING,
                Instant.now(), null, null, null, null, null);
            store.put(requestId, record);
            return record;
        }

        @Override
        public com.ghatana.platform.plugin.PluginMetadata metadata() {
            return com.ghatana.platform.plugin.PluginMetadata.builder()
                .id("com.ghatana.test.in-memory-approval")
                .name("InMemoryApprovalPlugin")
                .version("0.0.1")
                .description("Test double")
                .type(com.ghatana.platform.plugin.PluginType.GOVERNANCE)
                .author("test")
                .license("test")
                .build();
        }

        @Override
        public com.ghatana.platform.plugin.PluginState getState() {
            return com.ghatana.platform.plugin.PluginState.RUNNING;
        }

        @Override
        public Promise<Void> initialize(com.ghatana.platform.plugin.PluginContext context) {
            return Promise.complete();
        }

        @Override
        public Promise<Void> start() { return Promise.complete(); }

        @Override
        public Promise<Void> stop() { return Promise.complete(); }

        @Override
        public Promise<Void> shutdown() { return Promise.complete(); }

        @Override
        public Promise<ApprovalRecord> requestApproval(ApprovalRequest request) {
            ApprovalRecord record = ApprovalRecord.pending(request);
            store.put(request.requestId(), record);
            return Promise.of(record);
        }

        @Override
        public Promise<Optional<ApprovalRecord>> getApprovalStatus(String requestId) {
            return Promise.of(Optional.ofNullable(store.get(requestId)));
        }

        @Override
        public Promise<ApprovalRecord> completeApproval(String requestId, ApprovalDecision decision,
                String reviewerId, String notes) {
            ApprovalRecord existing = store.get(requestId);
            if (existing == null) {
                return Promise.ofException(new java.util.NoSuchElementException("Not found: " + requestId));
            }
            ApprovalRecord decided = existing.withDecision(decision, reviewerId, notes, Instant.now());
            store.put(requestId, decided);
            return Promise.of(decided);
        }

        @Override
        public Promise<List<ApprovalRecord>> listPendingForSubject(String subjectId) {
            List<ApprovalRecord> pending = new ArrayList<>();
            for (ApprovalRecord r : store.values()) {
                if (r.subjectId().equals(subjectId) && r.status() == ApprovalStatus.PENDING) {
                    pending.add(r);
                }
            }
            return Promise.of(pending);
        }

        @Override
        public Promise<List<ApprovalRecord>> listPendingForWorkspace(String workspaceId) {
            List<ApprovalRecord> pending = store.values().stream()
                .filter(r -> r.status() == ApprovalStatus.PENDING)
                .toList();
            return Promise.of(pending);
        }

        @Override
        public Promise<Void> cancelApproval(String requestId, String reason) {
            ApprovalRecord existing = store.get(requestId);
            if (existing != null) {
                store.put(requestId, existing.cancelled(reason, Instant.now()));
            }
            return Promise.of(null);
        }
    }

    static final class InMemorySnapshotRepository implements ApprovalSnapshotRepository {
        private final Map<String, ApprovalSnapshot> store = new HashMap<>();

        @Override
        public Promise<ApprovalSnapshot> save(String workspaceId, ApprovalSnapshot snapshot) {
            store.put(snapshot.requestId(), snapshot);
            return Promise.of(snapshot);
        }

        @Override
        public Promise<Optional<ApprovalSnapshot>> findByRequestId(String workspaceId, String requestId) {
            return Promise.of(Optional.ofNullable(store.get(requestId)));
        }
    }
}
