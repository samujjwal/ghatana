package com.ghatana.digitalmarketing.domain.approval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("ApprovalSnapshot")
class ApprovalSnapshotTest {

    private static final Instant NOW = Instant.now();

    private static ApprovalSnapshot valid(String requestId) {
        return new ApprovalSnapshot(
            requestId,
            ApprovalTargetType.CONTENT_VERSION,
            "cv-42",
            "ws-1",
            "Approve the ad copy",
            null,
            2,
            "brand-manager",
            NOW,
            1L
        );
    }

    // -------------------------------------------------------------------------
    // Happy path
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("creates valid snapshot with all required fields")
    void shouldCreateValidSnapshot() {
        ApprovalSnapshot snapshot = valid("req-001");
        assertThat(snapshot.requestId()).isEqualTo("req-001");
        assertThat(snapshot.targetType()).isEqualTo(ApprovalTargetType.CONTENT_VERSION);
        assertThat(snapshot.targetId()).isEqualTo("cv-42");
        assertThat(snapshot.targetWorkspaceId()).isEqualTo("ws-1");
        assertThat(snapshot.snapshotSummary()).isEqualTo("Approve the ad copy");
        assertThat(snapshot.validationResultId()).isNull();
        assertThat(snapshot.riskLevel()).isEqualTo(2);
        assertThat(snapshot.requiredApproverRole()).isEqualTo("brand-manager");
        assertThat(snapshot.snapshotAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("accepts null validationResultId")
    void shouldAcceptNullValidationResultId() {
        ApprovalSnapshot snapshot = new ApprovalSnapshot(
            "req-1", ApprovalTargetType.STRATEGY, "s-1", "ws-1",
            "summary", null, 1, "brand-manager", NOW, 1L);
        assertThat(snapshot.validationResultId()).isNull();
    }

    @Test
    @DisplayName("accepts non-null validationResultId")
    void shouldAcceptNonNullValidationResultId() {
        ApprovalSnapshot snapshot = new ApprovalSnapshot(
            "req-1", ApprovalTargetType.STRATEGY, "s-1", "ws-1",
            "summary", "vr-99", 1, "brand-manager", NOW, 1L);
        assertThat(snapshot.validationResultId()).isEqualTo("vr-99");
    }

    @Test
    @DisplayName("accepts riskLevel 1 (boundary)")
    void shouldAcceptRiskLevelMin() {
        assertThat(valid("req-min").riskLevel()).isEqualTo(2);
        ApprovalSnapshot s = new ApprovalSnapshot(
            "req-min", ApprovalTargetType.SOW, "s-1", "ws-1",
            "summary", null, 1, "brand-manager", NOW, 1L);
        assertThat(s.riskLevel()).isEqualTo(1);
    }

    @Test
    @DisplayName("accepts riskLevel 5 (boundary)")
    void shouldAcceptRiskLevelMax() {
        ApprovalSnapshot s = new ApprovalSnapshot(
            "req-max", ApprovalTargetType.BUDGET, "b-1", "ws-1",
            "summary", null, 5, "exec-sponsor", NOW, 1L);
        assertThat(s.riskLevel()).isEqualTo(5);
    }

    // -------------------------------------------------------------------------
    // Null checks
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("rejects null requestId")
    void shouldRejectNullRequestId() {
        assertThatNullPointerException().isThrownBy(() ->
            new ApprovalSnapshot(null, ApprovalTargetType.CONTENT_VERSION, "cv-1", "ws-1",
                "summary", null, 2, "brand-manager", NOW, 1L));
    }

    @Test
    @DisplayName("rejects null targetType")
    void shouldRejectNullTargetType() {
        assertThatNullPointerException().isThrownBy(() ->
            new ApprovalSnapshot("req-1", null, "cv-1", "ws-1",
                "summary", null, 2, "brand-manager", NOW, 1L));
    }

    @Test
    @DisplayName("rejects null targetId")
    void shouldRejectNullTargetId() {
        assertThatNullPointerException().isThrownBy(() ->
            new ApprovalSnapshot("req-1", ApprovalTargetType.CONTENT_VERSION, null, "ws-1",
                "summary", null, 2, "brand-manager", NOW, 1L));
    }

    @Test
    @DisplayName("rejects null targetWorkspaceId")
    void shouldRejectNullTargetWorkspaceId() {
        assertThatNullPointerException().isThrownBy(() ->
            new ApprovalSnapshot("req-1", ApprovalTargetType.CONTENT_VERSION, "cv-1", null,
                "summary", null, 2, "brand-manager", NOW, 1L));
    }

    @Test
    @DisplayName("rejects null snapshotSummary")
    void shouldRejectNullSnapshotSummary() {
        assertThatNullPointerException().isThrownBy(() ->
            new ApprovalSnapshot("req-1", ApprovalTargetType.CONTENT_VERSION, "cv-1", "ws-1",
                null, null, 2, "brand-manager", NOW, 1L));
    }

    @Test
    @DisplayName("rejects null requiredApproverRole")
    void shouldRejectNullRequiredApproverRole() {
        assertThatNullPointerException().isThrownBy(() ->
            new ApprovalSnapshot("req-1", ApprovalTargetType.CONTENT_VERSION, "cv-1", "ws-1",
                "summary", null, 2, null, NOW, 1L));
    }

    @Test
    @DisplayName("rejects null snapshotAt")
    void shouldRejectNullSnapshotAt() {
        assertThatNullPointerException().isThrownBy(() ->
            new ApprovalSnapshot("req-1", ApprovalTargetType.CONTENT_VERSION, "cv-1", "ws-1",
                "summary", null, 2, "brand-manager", null, 1L));
    }

    // -------------------------------------------------------------------------
    // Blank checks
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("rejects blank requestId")
    void shouldRejectBlankRequestId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            new ApprovalSnapshot("  ", ApprovalTargetType.CONTENT_VERSION, "cv-1", "ws-1",
                "summary", null, 2, "brand-manager", NOW, 1L));
    }

    @Test
    @DisplayName("rejects blank targetId")
    void shouldRejectBlankTargetId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            new ApprovalSnapshot("req-1", ApprovalTargetType.CONTENT_VERSION, "", "ws-1",
                "summary", null, 2, "brand-manager", NOW, 1L));
    }

    @Test
    @DisplayName("rejects blank targetWorkspaceId")
    void shouldRejectBlankTargetWorkspaceId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            new ApprovalSnapshot("req-1", ApprovalTargetType.CONTENT_VERSION, "cv-1", "  ",
                "summary", null, 2, "brand-manager", NOW, 1L));
    }

    @Test
    @DisplayName("rejects blank snapshotSummary")
    void shouldRejectBlankSnapshotSummary() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            new ApprovalSnapshot("req-1", ApprovalTargetType.CONTENT_VERSION, "cv-1", "ws-1",
                "", null, 2, "brand-manager", NOW, 1L));
    }

    @Test
    @DisplayName("rejects blank requiredApproverRole")
    void shouldRejectBlankRequiredApproverRole() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            new ApprovalSnapshot("req-1", ApprovalTargetType.CONTENT_VERSION, "cv-1", "ws-1",
                "summary", null, 2, "  ", NOW, 1L));
    }

    // -------------------------------------------------------------------------
    // Risk level bounds
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("rejects riskLevel 0")
    void shouldRejectRiskLevelZero() {
        assertThatIllegalArgumentException()
            .isThrownBy(() ->
                new ApprovalSnapshot("req-1", ApprovalTargetType.CONTENT_VERSION, "cv-1", "ws-1",
                    "summary", null, 0, "brand-manager", NOW, 1L))
            .withMessageContaining("riskLevel must be 1-5");
    }

    @Test
    @DisplayName("rejects riskLevel 6")
    void shouldRejectRiskLevelSix() {
        assertThatIllegalArgumentException()
            .isThrownBy(() ->
                new ApprovalSnapshot("req-1", ApprovalTargetType.CONTENT_VERSION, "cv-1", "ws-1",
                    "summary", null, 6, "brand-manager", NOW, 1L))
            .withMessageContaining("riskLevel must be 1-5");
    }

    // -------------------------------------------------------------------------
    // ApprovalTargetType enum coverage
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("ApprovalTargetType covers all canonical values")
    void shouldHaveAllTargetTypeValues() {
        ApprovalTargetType[] types = ApprovalTargetType.values();
        assertThat(types).containsExactlyInAnyOrder(
            ApprovalTargetType.STRATEGY,
            ApprovalTargetType.PROPOSAL,
            ApprovalTargetType.SOW,
            ApprovalTargetType.CONTENT_VERSION,
            ApprovalTargetType.BUDGET,
            ApprovalTargetType.CAMPAIGN_LAUNCH,
            ApprovalTargetType.CONNECTOR_WRITE,
            ApprovalTargetType.OVERRIDE
        );
    }

    @Test
    @DisplayName("ApprovalTargetType valueOf round-trips correctly")
    void shouldRoundTripValueOf() {
        for (ApprovalTargetType type : ApprovalTargetType.values()) {
            assertThat(ApprovalTargetType.valueOf(type.name())).isEqualTo(type);
        }
    }
}
