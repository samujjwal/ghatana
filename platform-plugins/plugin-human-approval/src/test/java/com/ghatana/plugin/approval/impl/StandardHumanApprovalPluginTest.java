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
@DisplayName("StandardHumanApprovalPlugin Tests [GH-90000]")
@ExtendWith(MockitoExtension.class) // GH-90000
class StandardHumanApprovalPluginTest extends EventloopTestBase {

    @Mock
    private PluginContext mockContext;

    private StandardHumanApprovalPlugin plugin;

    @BeforeEach
    void setUp() { // GH-90000
        plugin = new StandardHumanApprovalPlugin(); // GH-90000
        runPromise(() -> plugin.initialize(mockContext).then(v -> plugin.start())); // GH-90000
    }

    // -------------------------------------------------------------------------
    // lifecycle
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Should reflect RUNNING state after start [GH-90000]")
    void testLifecycle_running() { // GH-90000
        assertThat(plugin.getState()).isEqualTo(PluginState.RUNNING); // GH-90000
    }

    @Test
    @DisplayName("Should transition to STOPPED after stop() [GH-90000]")
    void testLifecycle_stop() { // GH-90000
        runPromise(() -> plugin.stop()); // GH-90000
        assertThat(plugin.getState()).isEqualTo(PluginState.STOPPED); // GH-90000
    }

    @Test
    @DisplayName("Should transition to UNLOADED after shutdown() [GH-90000]")
    void testLifecycle_shutdown() { // GH-90000
        runPromise(() -> plugin.shutdown()); // GH-90000
        assertThat(plugin.getState()).isEqualTo(PluginState.UNLOADED); // GH-90000
    }

    @Test
    @DisplayName("Should return correct metadata id [GH-90000]")
    void testMetadata() { // GH-90000
        assertThat(plugin.metadata().id()).isEqualTo("com.ghatana.plugin.human-approval");
        assertThat(plugin.metadata().version()).isEqualTo("1.0.0");
    }

    // -------------------------------------------------------------------------
    // requestApproval
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Should create a PENDING approval record [GH-90000]")
    void testRequestApproval_createsPending() { // GH-90000
        ApprovalRequest req = buildRequest("patient-42", "nurse-7", "patient-data-export"); // GH-90000

        ApprovalRecord record = runPromise(() -> plugin.requestApproval(req)); // GH-90000

        assertThat(record.requestId()).isEqualTo(req.requestId()); // GH-90000
        assertThat(record.subjectId()).isEqualTo("patient-42");
        assertThat(record.action()).isEqualTo("patient-data-export");
        assertThat(record.status()).isEqualTo(ApprovalStatus.PENDING); // GH-90000
        assertThat(record.decidedAt()).isNull(); // GH-90000
        assertThat(record.reviewerId()).isNull(); // GH-90000
    }

    @Test
    @DisplayName("Should be idempotent on duplicate requestId [GH-90000]")
    void testRequestApproval_idempotent() { // GH-90000
        ApprovalRequest req = buildRequest("patient-42", "nurse-7", "patient-data-export"); // GH-90000

        ApprovalRecord first  = runPromise(() -> plugin.requestApproval(req)); // GH-90000
        ApprovalRecord second = runPromise(() -> plugin.requestApproval(req)); // GH-90000

        assertThat(second.requestId()).isEqualTo(first.requestId()); // GH-90000
        assertThat(second.status()).isEqualTo(first.status()); // GH-90000
    }

    @Test
    @DisplayName("Should reject null request [GH-90000]")
    void testRequestApproval_nullRequest() { // GH-90000
        assertThatThrownBy(() -> runPromise(() -> plugin.requestApproval(null))) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    // -------------------------------------------------------------------------
    // getApprovalStatus
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Should return empty for unknown requestId [GH-90000]")
    void testGetApprovalStatus_unknown() { // GH-90000
        Optional<ApprovalRecord> result = runPromise( // GH-90000
                () -> plugin.getApprovalStatus("does-not-exist"));
        assertThat(result).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("Should return the record for a known requestId [GH-90000]")
    void testGetApprovalStatus_known() { // GH-90000
        ApprovalRequest req = buildRequest("trade-99", "system", "large-trade-approval"); // GH-90000
        runPromise(() -> plugin.requestApproval(req)); // GH-90000

        Optional<ApprovalRecord> result = runPromise( // GH-90000
                () -> plugin.getApprovalStatus(req.requestId())); // GH-90000
        assertThat(result).isPresent(); // GH-90000
        assertThat(result.get().status()).isEqualTo(ApprovalStatus.PENDING); // GH-90000
    }

    @Test
    @DisplayName("Should reject blank requestId in getApprovalStatus [GH-90000]")
    void testGetApprovalStatus_blankId() { // GH-90000
        Promise<Optional<ApprovalRecord>> p = plugin.getApprovalStatus("   ");
        assertThatThrownBy(() -> runPromise(() -> p)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }

    // -------------------------------------------------------------------------
    // completeApproval
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Should transition to APPROVED [GH-90000]")
    void testCompleteApproval_approved() { // GH-90000
        ApprovalRequest req = buildRequest("patient-1", "dr-jones", "medication-override"); // GH-90000
        runPromise(() -> plugin.requestApproval(req)); // GH-90000

        ApprovalRecord decided = runPromise( // GH-90000
                () -> plugin.completeApproval(req.requestId(), ApprovalDecision.APPROVED, // GH-90000
                        "dr-jones", "Clinically justified"));

        assertThat(decided.status()).isEqualTo(ApprovalStatus.APPROVED); // GH-90000
        assertThat(decided.reviewerId()).isEqualTo("dr-jones");
        assertThat(decided.reviewerNotes()).isEqualTo("Clinically justified");
        assertThat(decided.decidedAt()).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("Should transition to REJECTED [GH-90000]")
    void testCompleteApproval_rejected() { // GH-90000
        ApprovalRequest req = buildRequest("patient-2", "system", "high-risk-procedure"); // GH-90000
        runPromise(() -> plugin.requestApproval(req)); // GH-90000

        ApprovalRecord decided = runPromise( // GH-90000
                () -> plugin.completeApproval(req.requestId(), ApprovalDecision.REJECTED, // GH-90000
                        "chief-medical", "Protocol not met"));

        assertThat(decided.status()).isEqualTo(ApprovalStatus.REJECTED); // GH-90000
    }

    @Test
    @DisplayName("completeApproval on already-decided record is no-op [GH-90000]")
    void testCompleteApproval_idempotent() { // GH-90000
        ApprovalRequest req = buildRequest("patient-3", "nurse-1", "export"); // GH-90000
        runPromise(() -> plugin.requestApproval(req)); // GH-90000
        runPromise(() -> plugin.completeApproval(req.requestId(), ApprovalDecision.APPROVED, // GH-90000
                "dr-a", "ok"));

        ApprovalRecord second = runPromise( // GH-90000
                () -> plugin.completeApproval(req.requestId(), ApprovalDecision.REJECTED, // GH-90000
                        "dr-b", "actually no"));

        // The first decision wins — stays APPROVED
        assertThat(second.status()).isEqualTo(ApprovalStatus.APPROVED); // GH-90000
        assertThat(second.reviewerId()).isEqualTo("dr-a");
    }

    @Test
    @DisplayName("completeApproval on unknown requestId returns exception [GH-90000]")
    void testCompleteApproval_unknown() { // GH-90000
        Promise<ApprovalRecord> p = plugin.completeApproval("bad-id", // GH-90000
                ApprovalDecision.APPROVED, "reviewer", null);
        assertThatThrownBy(() -> runPromise(() -> p)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }

    // -------------------------------------------------------------------------
    // listPendingForSubject
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Should return only pending records for a subject [GH-90000]")
    void testListPendingForSubject() { // GH-90000
        String subjectId = "patient-99";
        ApprovalRequest req1 = buildRequest(subjectId, "nurse-1", "export"); // GH-90000
        ApprovalRequest req2 = buildRequest(subjectId, "nurse-2", "medication-override"); // GH-90000
        ApprovalRequest req3 = buildRequest("other-subject", "nurse-3", "action"); // GH-90000

        runPromise(() -> plugin.requestApproval(req1) // GH-90000
                .then(v -> plugin.requestApproval(req2)) // GH-90000
                .then(v -> plugin.requestApproval(req3))); // GH-90000

        // Approve req1 — should no longer appear in pending list
        runPromise(() -> plugin.completeApproval(req1.requestId(), // GH-90000
                ApprovalDecision.APPROVED, "admin", null));

        List<ApprovalRecord> pending = runPromise( // GH-90000
                () -> plugin.listPendingForSubject(subjectId)); // GH-90000

        assertThat(pending).hasSize(1); // GH-90000
        assertThat(pending.get(0).requestId()).isEqualTo(req2.requestId()); // GH-90000
    }

    @Test
    @DisplayName("Should return empty list when subject has no pending approvals [GH-90000]")
    void testListPendingForSubject_empty() { // GH-90000
        List<ApprovalRecord> pending = runPromise( // GH-90000
                () -> plugin.listPendingForSubject("unknown-subject"));
        assertThat(pending).isEmpty(); // GH-90000
    }

    // -------------------------------------------------------------------------
    // cancelApproval
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Should cancel a pending approval [GH-90000]")
    void testCancelApproval() { // GH-90000
        ApprovalRequest req = buildRequest("patient-5", "system", "routine-export"); // GH-90000
        runPromise(() -> plugin.requestApproval(req)); // GH-90000
        runPromise(() -> plugin.cancelApproval(req.requestId(), "No longer needed")); // GH-90000

        Optional<ApprovalRecord> result = runPromise( // GH-90000
                () -> plugin.getApprovalStatus(req.requestId())); // GH-90000
        assertThat(result).isPresent(); // GH-90000
        assertThat(result.get().status()).isEqualTo(ApprovalStatus.CANCELLED); // GH-90000
        assertThat(result.get().reviewerNotes()).isEqualTo("No longer needed");
    }

    @Test
    @DisplayName("HA-1: terminal states do not transition back via completeApproval")
    void testStateMachine_terminalStateIsStable() {
        ApprovalRequest req = buildRequest("patient-fsm", "reviewer", "restricted-export");
        runPromise(() -> plugin.requestApproval(req));
        runPromise(() -> plugin.cancelApproval(req.requestId(), "withdrawn"));

        ApprovalRecord afterCompleteAttempt = runPromise(() ->
            plugin.completeApproval(req.requestId(), ApprovalDecision.APPROVED, "dr-fsm", "late approval")
        );

        assertThat(afterCompleteAttempt.status()).isEqualTo(ApprovalStatus.CANCELLED);
    }

    @Test
    @DisplayName("HA-2: approval requires quorum before transitioning to APPROVED")
    void testQuorumSemantics_requiresMultipleApprovals() {
        ApprovalRequest req = new ApprovalRequest(
            UUID.randomUUID().toString(),
            "trade-quorum",
            "system",
            "large-trade-approval",
            "Needs two approvers",
            Map.of("quorum.requiredApprovals", 2),
            Instant.now(),
            null
        );

        runPromise(() -> plugin.requestApproval(req));

        ApprovalRecord firstVote = runPromise(() ->
            plugin.completeApproval(req.requestId(), ApprovalDecision.APPROVED, "reviewer-a", "vote 1")
        );
        assertThat(firstVote.status()).isEqualTo(ApprovalStatus.PENDING);

        ApprovalRecord secondVote = runPromise(() ->
            plugin.completeApproval(req.requestId(), ApprovalDecision.APPROVED, "reviewer-b", "vote 2")
        );
        assertThat(secondVote.status()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(secondVote.reviewerId()).isEqualTo("reviewer-b");
    }

    @Test
    @DisplayName("HA-3: expired approvals escalate to EXPIRED and reject completion")
    void testTimeoutEscalation_expiresPendingApproval() {
        ApprovalRequest req = new ApprovalRequest(
            UUID.randomUUID().toString(),
            "patient-expire",
            "system",
            "critical-export",
            "short-lived approval",
            Map.of(),
            Instant.now().minusSeconds(120),
            Instant.now().minusSeconds(60)
        );

        runPromise(() -> plugin.requestApproval(req));

        Optional<ApprovalRecord> status = runPromise(() -> plugin.getApprovalStatus(req.requestId()));
        assertThat(status).isPresent();
        assertThat(status.get().status()).isEqualTo(ApprovalStatus.EXPIRED);

        ApprovalRecord completionAttempt = runPromise(() ->
            plugin.completeApproval(req.requestId(), ApprovalDecision.APPROVED, "reviewer-exp", "late")
        );
        assertThat(completionAttempt.status()).isEqualTo(ApprovalStatus.EXPIRED);
    }

    @Test
    @DisplayName("cancelApproval on already-decided record is no-op [GH-90000]")
    void testCancelApproval_alreadyDecidedIsNoOp() { // GH-90000
        ApprovalRequest req = buildRequest("patient-6", "dr-x", "sample"); // GH-90000
        runPromise(() -> plugin.requestApproval(req)); // GH-90000
        runPromise(() -> plugin.completeApproval(req.requestId(), // GH-90000
                ApprovalDecision.APPROVED, "dr-x", null));

        // cancel should be a no-op
        runPromise(() -> plugin.cancelApproval(req.requestId(), "late cancel")); // GH-90000

        Optional<ApprovalRecord> result = runPromise( // GH-90000
                () -> plugin.getApprovalStatus(req.requestId())); // GH-90000
        assertThat(result).isPresent();
        assertThat(result.orElseThrow().status()).isEqualTo(ApprovalStatus.APPROVED); // GH-90000
    }

    @Test
    @DisplayName("cancelApproval on unknown requestId returns exception [GH-90000]")
    void testCancelApproval_unknown() { // GH-90000
        Promise<Void> p = plugin.cancelApproval("ghost-id", "reason"); // GH-90000
        assertThatThrownBy(() -> runPromise(() -> p)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private ApprovalRequest buildRequest(String subjectId, String requestedBy, String action) { // GH-90000
        return new ApprovalRequest( // GH-90000
                UUID.randomUUID().toString(), // GH-90000
                subjectId,
                requestedBy,
                action,
                "Business justification for " + action,
                Map.of(), // GH-90000
                Instant.now(), // GH-90000
                null
        );
    }
}
