package com.ghatana.plugin.approval.impl;

import com.ghatana.platform.plugin.PluginContext;
import com.ghatana.platform.plugin.PluginState;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.plugin.approval.ApprovalDecision;
import com.ghatana.plugin.approval.ApprovalRecord;
import com.ghatana.plugin.approval.ApprovalRequest;
import com.ghatana.plugin.approval.ApprovalStatus;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type class
 * @doc.purpose Tests for StandardHumanApprovalPlugin
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("StandardHumanApprovalPlugin Tests")
@ExtendWith(MockitoExtension.class)
class StandardHumanApprovalPluginTest extends EventloopTestBase {

    @Mock
    private PluginContext mockContext;

    private StandardHumanApprovalPlugin plugin;

    @BeforeEach
    void setUp() {
        plugin = new StandardHumanApprovalPlugin();
        runPromise(() -> plugin.initialize(mockContext).then(v -> plugin.start()));
    }

    // -------------------------------------------------------------------------
    // lifecycle
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Should reflect RUNNING state after start")
    void testLifecycle_running() {
        assertThat(plugin.getState()).isEqualTo(PluginState.RUNNING);
    }

    @Test
    @DisplayName("Should transition to STOPPED after stop()")
    void testLifecycle_stop() {
        runPromise(() -> plugin.stop());
        assertThat(plugin.getState()).isEqualTo(PluginState.STOPPED);
    }

    @Test
    @DisplayName("Should transition to UNLOADED after shutdown()")
    void testLifecycle_shutdown() {
        runPromise(() -> plugin.shutdown());
        assertThat(plugin.getState()).isEqualTo(PluginState.UNLOADED);
    }

    @Test
    @DisplayName("Should return correct metadata id")
    void testMetadata() {
        assertThat(plugin.metadata().id()).isEqualTo("com.ghatana.plugin.human-approval");
        assertThat(plugin.metadata().version()).isEqualTo("1.0.0");
    }

    // -------------------------------------------------------------------------
    // requestApproval
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Should create a PENDING approval record")
    void testRequestApproval_createsPending() {
        ApprovalRequest req = buildRequest("patient-42", "nurse-7", "patient-data-export");

        ApprovalRecord record = runPromise(() -> plugin.requestApproval(req));

        assertThat(record.requestId()).isEqualTo(req.requestId());
        assertThat(record.subjectId()).isEqualTo("patient-42");
        assertThat(record.action()).isEqualTo("patient-data-export");
        assertThat(record.status()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(record.decidedAt()).isNull();
        assertThat(record.reviewerId()).isNull();
    }

    @Test
    @DisplayName("Should be idempotent on duplicate requestId")
    void testRequestApproval_idempotent() {
        ApprovalRequest req = buildRequest("patient-42", "nurse-7", "patient-data-export");

        ApprovalRecord first  = runPromise(() -> plugin.requestApproval(req));
        ApprovalRecord second = runPromise(() -> plugin.requestApproval(req));

        assertThat(second.requestId()).isEqualTo(first.requestId());
        assertThat(second.status()).isEqualTo(first.status());
    }

    @Test
    @DisplayName("Should reject null request")
    void testRequestApproval_nullRequest() {
        assertThatThrownBy(() -> plugin.requestApproval(null))
                .isInstanceOf(NullPointerException.class);
    }

    // -------------------------------------------------------------------------
    // getApprovalStatus
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Should return empty for unknown requestId")
    void testGetApprovalStatus_unknown() {
        Optional<ApprovalRecord> result = runPromise(
                () -> plugin.getApprovalStatus("does-not-exist"));
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should return the record for a known requestId")
    void testGetApprovalStatus_known() {
        ApprovalRequest req = buildRequest("trade-99", "system", "large-trade-approval");
        runPromise(() -> plugin.requestApproval(req));

        Optional<ApprovalRecord> result = runPromise(
                () -> plugin.getApprovalStatus(req.requestId()));
        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(ApprovalStatus.PENDING);
    }

    @Test
    @DisplayName("Should reject blank requestId in getApprovalStatus")
    void testGetApprovalStatus_blankId() {
        Promise<Optional<ApprovalRecord>> p = plugin.getApprovalStatus("  ");
        assertThatThrownBy(() -> runPromise(() -> p))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // -------------------------------------------------------------------------
    // completeApproval
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Should transition to APPROVED")
    void testCompleteApproval_approved() {
        ApprovalRequest req = buildRequest("patient-1", "dr-jones", "medication-override");
        runPromise(() -> plugin.requestApproval(req));

        ApprovalRecord decided = runPromise(
                () -> plugin.completeApproval(req.requestId(), ApprovalDecision.APPROVED,
                        "dr-jones", "Clinically justified"));

        assertThat(decided.status()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(decided.reviewerId()).isEqualTo("dr-jones");
        assertThat(decided.reviewerNotes()).isEqualTo("Clinically justified");
        assertThat(decided.decidedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should transition to REJECTED")
    void testCompleteApproval_rejected() {
        ApprovalRequest req = buildRequest("patient-2", "system", "high-risk-procedure");
        runPromise(() -> plugin.requestApproval(req));

        ApprovalRecord decided = runPromise(
                () -> plugin.completeApproval(req.requestId(), ApprovalDecision.REJECTED,
                        "chief-medical", "Protocol not met"));

        assertThat(decided.status()).isEqualTo(ApprovalStatus.REJECTED);
    }

    @Test
    @DisplayName("completeApproval on already-decided record is no-op")
    void testCompleteApproval_idempotent() {
        ApprovalRequest req = buildRequest("patient-3", "nurse-1", "export");
        runPromise(() -> plugin.requestApproval(req));
        runPromise(() -> plugin.completeApproval(req.requestId(), ApprovalDecision.APPROVED,
                "dr-a", "ok"));

        ApprovalRecord second = runPromise(
                () -> plugin.completeApproval(req.requestId(), ApprovalDecision.REJECTED,
                        "dr-b", "actually no"));

        // The first decision wins — stays APPROVED
        assertThat(second.status()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(second.reviewerId()).isEqualTo("dr-a");
    }

    @Test
    @DisplayName("completeApproval on unknown requestId returns exception")
    void testCompleteApproval_unknown() {
        Promise<ApprovalRecord> p = plugin.completeApproval("bad-id",
                ApprovalDecision.APPROVED, "reviewer", null);
        assertThatThrownBy(() -> runPromise(() -> p))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // -------------------------------------------------------------------------
    // listPendingForSubject
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Should return only pending records for a subject")
    void testListPendingForSubject() {
        String subjectId = "patient-99";
        ApprovalRequest req1 = buildRequest(subjectId, "nurse-1", "export");
        ApprovalRequest req2 = buildRequest(subjectId, "nurse-2", "medication-override");
        ApprovalRequest req3 = buildRequest("other-subject", "nurse-3", "action");

        runPromise(() -> plugin.requestApproval(req1)
                .then(v -> plugin.requestApproval(req2))
                .then(v -> plugin.requestApproval(req3)));

        // Approve req1 — should no longer appear in pending list
        runPromise(() -> plugin.completeApproval(req1.requestId(),
                ApprovalDecision.APPROVED, "admin", null));

        List<ApprovalRecord> pending = runPromise(
                () -> plugin.listPendingForSubject(subjectId));

        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).requestId()).isEqualTo(req2.requestId());
    }

    @Test
    @DisplayName("Should return empty list when subject has no pending approvals")
    void testListPendingForSubject_empty() {
        List<ApprovalRecord> pending = runPromise(
                () -> plugin.listPendingForSubject("unknown-subject"));
        assertThat(pending).isEmpty();
    }

    // -------------------------------------------------------------------------
    // cancelApproval
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Should cancel a pending approval")
    void testCancelApproval() {
        ApprovalRequest req = buildRequest("patient-5", "system", "routine-export");
        runPromise(() -> plugin.requestApproval(req));
        runPromise(() -> plugin.cancelApproval(req.requestId(), "No longer needed"));

        Optional<ApprovalRecord> result = runPromise(
                () -> plugin.getApprovalStatus(req.requestId()));
        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(ApprovalStatus.CANCELLED);
        assertThat(result.get().reviewerNotes()).isEqualTo("No longer needed");
    }

    @Test
    @DisplayName("cancelApproval on already-decided record is no-op")
    void testCancelApproval_alreadyDecidedIsNoOp() {
        ApprovalRequest req = buildRequest("patient-6", "dr-x", "sample");
        runPromise(() -> plugin.requestApproval(req));
        runPromise(() -> plugin.completeApproval(req.requestId(),
                ApprovalDecision.APPROVED, "dr-x", null));

        // cancel should be a no-op
        runPromise(() -> plugin.cancelApproval(req.requestId(), "late cancel"));

        Optional<ApprovalRecord> result = runPromise(
                () -> plugin.getApprovalStatus(req.requestId()));
        assertThat(result.get().status()).isEqualTo(ApprovalStatus.APPROVED);
    }

    @Test
    @DisplayName("cancelApproval on unknown requestId returns exception")
    void testCancelApproval_unknown() {
        Promise<Void> p = plugin.cancelApproval("ghost-id", "reason");
        assertThatThrownBy(() -> runPromise(() -> p))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private ApprovalRequest buildRequest(String subjectId, String requestedBy, String action) {
        return new ApprovalRequest(
                UUID.randomUUID().toString(),
                subjectId,
                requestedBy,
                action,
                "Business justification for " + action,
                Map.of(),
                Instant.now(),
                null
        );
    }
}
