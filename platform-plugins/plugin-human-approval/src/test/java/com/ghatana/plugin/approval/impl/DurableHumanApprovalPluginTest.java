package com.ghatana.plugin.approval.impl;

import com.ghatana.platform.plugin.PluginContext;
import com.ghatana.platform.plugin.PluginState;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.plugin.approval.ApprovalDecision;
import com.ghatana.plugin.approval.ApprovalRecord;
import com.ghatana.plugin.approval.ApprovalRequest;
import com.ghatana.plugin.approval.ApprovalStatus;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
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
 * Contract and durability tests for {@link DurableHumanApprovalPlugin}.
 *
 * <p>Covers:</p>
 * <ul>
 *   <li>KP-HAP-001: idempotency — requesting the same approval twice returns the same record</li>
 *   <li>KP-HAP-002: lifecycle state transitions (PENDING → APPROVED / REJECTED / CANCELLED / EXPIRED)</li>
 *   <li>KP-HAP-003: quorum (multi-reviewer) approval flow</li>
 *   <li>KP-HAP-004: tenant isolation — approvals for one subject must not appear in another subject's list</li>
 *   <li>KP-HAP-005: expiry escalation — approvals past their deadline are surfaced as EXPIRED</li>
 *   <li>KP-HAP-006: cancellation of a non-PENDING approval is a no-op</li>
 *   <li>KP-HAP-007: durability semantics declared in plugin metadata</li>
 * </ul>
 *
 * <p>Uses an H2 in-memory database so the test is self-contained with no external dependencies.
 *
 * @doc.type class
 * @doc.purpose KP-HAP contract and durability tests for DurableHumanApprovalPlugin
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("DurableHumanApprovalPlugin contract tests")
@ExtendWith(MockitoExtension.class)
class DurableHumanApprovalPluginTest extends EventloopTestBase {

    @Mock
    private PluginContext mockContext;

    private DurableHumanApprovalPlugin plugin;

    @BeforeEach
    void setUp() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:approval_test_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        plugin = new DurableHumanApprovalPlugin(ds);
        plugin.ensureSchema();
        runPromise(() -> plugin.initialize(mockContext));
        runPromise(() -> plugin.start());
    }

    @AfterEach
    void tearDown() {
        runPromise(() -> plugin.stop());
        runPromise(() -> plugin.shutdown());
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Plugin should follow UNLOADED→INITIALIZED→RUNNING→STOPPED→UNLOADED")
    void testLifecycleTransitions() {
        assertThat(plugin.getState()).isEqualTo(PluginState.RUNNING);
        runPromise(() -> plugin.stop());
        assertThat(plugin.getState()).isEqualTo(PluginState.STOPPED);
        runPromise(() -> plugin.shutdown());
        assertThat(plugin.getState()).isEqualTo(PluginState.UNLOADED);
    }

    @Test
    @DisplayName("KP-HAP-007: metadata must declare durable variant and idempotency")
    void testMetadataDeclaresDurableSemantics() {
        var metadata = plugin.metadata();

        assertThat(metadata.id()).isEqualTo("com.ghatana.plugin.human-approval.durable");
        assertThat(metadata.capabilities()).contains("approval:durable");
        assertThat(metadata.properties())
                .containsEntry("variant", "durable-jdbc")
                .containsEntry("durability", "durable")
                .containsEntry("idempotency", "enforced-by-pk");
    }

    // ── KP-HAP-001: Idempotency ───────────────────────────────────────────────

    @Test
    @DisplayName("KP-HAP-001: requesting the same approval twice returns the existing record")
    void testIdempotentRequestApproval() {
        String requestId = UUID.randomUUID().toString();
        ApprovalRequest request = buildRequest(requestId, "patient-001", "PENDING_DISCHARGE",
                "discharge-review", Map.of());

        ApprovalRecord first  = runPromise(() -> plugin.requestApproval(request));
        ApprovalRecord second = runPromise(() -> plugin.requestApproval(request));

        assertThat(second.requestId()).isEqualTo(first.requestId());
        assertThat(second.status()).isEqualTo(ApprovalStatus.PENDING);
    }

    // ── KP-HAP-002: Approve / Reject lifecycle ────────────────────────────────

    @Test
    @DisplayName("KP-HAP-002a: PENDING approval can be APPROVED by a reviewer")
    void testApproveTransition() {
        String requestId = UUID.randomUUID().toString();
        runPromise(() -> plugin.requestApproval(
                buildRequest(requestId, "trade-001", "LARGE_TRADE_APPROVAL", "large-trade", Map.of())));

        ApprovalRecord result = runPromise(() ->
                plugin.completeApproval(requestId, ApprovalDecision.APPROVED, "reviewer-a", "Looks good"));

        assertThat(result.status()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(result.reviewerId()).isEqualTo("reviewer-a");
        assertThat(result.reviewerNotes()).isEqualTo("Looks good");
        assertThat(result.decidedAt()).isNotNull();
    }

    @Test
    @DisplayName("KP-HAP-002b: PENDING approval can be REJECTED by a reviewer")
    void testRejectTransition() {
        String requestId = UUID.randomUUID().toString();
        runPromise(() -> plugin.requestApproval(
                buildRequest(requestId, "patient-002", "EMERGENCY_ACCESS", "break-glass", Map.of())));

        ApprovalRecord result = runPromise(() ->
                plugin.completeApproval(requestId, ApprovalDecision.REJECTED, "reviewer-b", "Insufficient justification"));

        assertThat(result.status()).isEqualTo(ApprovalStatus.REJECTED);
        assertThat(result.reviewerId()).isEqualTo("reviewer-b");
    }

    @Test
    @DisplayName("KP-HAP-002c: completing an already-decided approval is a no-op")
    void testCompletingDecidedApprovalIsNoOp() {
        String requestId = UUID.randomUUID().toString();
        runPromise(() -> plugin.requestApproval(
                buildRequest(requestId, "trade-002", "TRADE_APPROVAL", "trade-review", Map.of())));
        runPromise(() -> plugin.completeApproval(requestId, ApprovalDecision.APPROVED, "reviewer-a", null));

        // Second decision attempt must be no-op
        ApprovalRecord second = runPromise(() ->
                plugin.completeApproval(requestId, ApprovalDecision.REJECTED, "reviewer-b", "Overruling"));

        assertThat(second.status()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(second.reviewerId()).isEqualTo("reviewer-a");
    }

    // ── KP-HAP-003: Quorum approval ───────────────────────────────────────────

    @Test
    @DisplayName("KP-HAP-003: two-reviewer quorum approval requires both votes")
    void testQuorumApproval() {
        String requestId = UUID.randomUUID().toString();
        runPromise(() -> plugin.requestApproval(
                buildRequest(requestId, "policy-001", "POLICY_WAIVER", "waiver-review",
                        Map.of("requiredApprovals", 2))));

        // First vote — should still be PENDING
        ApprovalRecord afterFirst = runPromise(() ->
                plugin.completeApproval(requestId, ApprovalDecision.APPROVED, "reviewer-a", "Approve"));
        assertThat(afterFirst.status()).isEqualTo(ApprovalStatus.PENDING);

        // Second vote — quorum reached, should become APPROVED
        ApprovalRecord afterSecond = runPromise(() ->
                plugin.completeApproval(requestId, ApprovalDecision.APPROVED, "reviewer-b", "Approve"));
        assertThat(afterSecond.status()).isEqualTo(ApprovalStatus.APPROVED);
    }

    // ── KP-HAP-004: Tenant isolation ─────────────────────────────────────────

    @Test
    @DisplayName("KP-HAP-004: pending approvals for subject-A must not appear in subject-B list")
    void testTenantIsolation() {
        String requestA = UUID.randomUUID().toString();
        String requestB = UUID.randomUUID().toString();

        runPromise(() -> plugin.requestApproval(
                buildRequest(requestA, "subject-A", "ACTION_A", "purpose-a", Map.of())));
        runPromise(() -> plugin.requestApproval(
                buildRequest(requestB, "subject-B", "ACTION_B", "purpose-b", Map.of())));

        List<ApprovalRecord> pendingForA = runPromise(() -> plugin.listPendingForSubject("subject-A"));
        List<ApprovalRecord> pendingForB = runPromise(() -> plugin.listPendingForSubject("subject-B"));

        assertThat(pendingForA).hasSize(1);
        assertThat(pendingForA.get(0).requestId()).isEqualTo(requestA);

        assertThat(pendingForB).hasSize(1);
        assertThat(pendingForB.get(0).requestId()).isEqualTo(requestB);

        // Cross-check: A's record must not appear in B's list and vice versa
        assertThat(pendingForA).noneMatch(r -> r.requestId().equals(requestB));
        assertThat(pendingForB).noneMatch(r -> r.requestId().equals(requestA));
    }

    // ── KP-HAP-005: Expiry escalation ────────────────────────────────────────

    @Test
    @DisplayName("KP-HAP-005: approval past its deadline is escalated to EXPIRED on retrieval")
    void testExpiryEscalation() {
        String requestId = UUID.randomUUID().toString();
        Instant alreadyExpired = Instant.now().minusSeconds(60);

        ApprovalRequest request = new ApprovalRequest(
                requestId, "patient-003", "nurse-station", "MEDICATION_OVERRIDE",
                "Emergency override", Map.of(), Instant.now().minusSeconds(120), alreadyExpired);

        runPromise(() -> plugin.requestApproval(request));
        Optional<ApprovalRecord> status = runPromise(() -> plugin.getApprovalStatus(requestId));

        assertThat(status).isPresent();
        assertThat(status.get().status()).isEqualTo(ApprovalStatus.EXPIRED);
    }

    // ── KP-HAP-006: Cancellation ─────────────────────────────────────────────

    @Test
    @DisplayName("KP-HAP-006: cancelling a PENDING approval marks it as CANCELLED")
    void testCancelPendingApproval() {
        String requestId = UUID.randomUUID().toString();
        runPromise(() -> plugin.requestApproval(
                buildRequest(requestId, "patient-004", "SURGERY_APPROVAL", "surgery-consent", Map.of())));

        runPromise(() -> plugin.cancelApproval(requestId, "Procedure rescheduled"));

        Optional<ApprovalRecord> status = runPromise(() -> plugin.getApprovalStatus(requestId));
        assertThat(status).isPresent();
        assertThat(status.get().status()).isEqualTo(ApprovalStatus.CANCELLED);
    }

    @Test
    @DisplayName("KP-HAP-006: cancelling an already-approved approval is a no-op")
    void testCancelApprovedApprovalIsNoOp() {
        String requestId = UUID.randomUUID().toString();
        runPromise(() -> plugin.requestApproval(
                buildRequest(requestId, "patient-005", "DISCHARGE", "discharge-review", Map.of())));
        runPromise(() -> plugin.completeApproval(requestId, ApprovalDecision.APPROVED, "dr-jones", null));

        runPromise(() -> plugin.cancelApproval(requestId, "Late cancellation attempt"));

        Optional<ApprovalRecord> status = runPromise(() -> plugin.getApprovalStatus(requestId));
        assertThat(status).isPresent();
        assertThat(status.get().status()).isEqualTo(ApprovalStatus.APPROVED);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static ApprovalRequest buildRequest(String requestId, String subjectId,
                                                String action, String purpose,
                                                Map<String, Object> context) {
        return new ApprovalRequest(requestId, subjectId, "system", action, purpose,
                context, Instant.now(), null);
    }
}
