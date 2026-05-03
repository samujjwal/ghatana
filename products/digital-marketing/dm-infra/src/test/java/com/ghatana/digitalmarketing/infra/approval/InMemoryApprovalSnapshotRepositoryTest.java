package com.ghatana.digitalmarketing.infra.approval;

import com.ghatana.digitalmarketing.domain.approval.ApprovalSnapshot;
import com.ghatana.digitalmarketing.domain.approval.ApprovalTargetType;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("InMemoryApprovalSnapshotRepository")
class InMemoryApprovalSnapshotRepositoryTest extends EventloopTestBase {

    private InMemoryApprovalSnapshotRepository repository;

    private static final String WS_ID = "ws-1";

    @BeforeEach
    void setUp() {
        repository = new InMemoryApprovalSnapshotRepository();
    }

    @Test
    @DisplayName("save returns the saved snapshot")
    void shouldReturnSavedSnapshot() {
        ApprovalSnapshot snap = buildSnapshot("req-1");
        ApprovalSnapshot saved = runPromise(() -> repository.save(WS_ID, snap));
        assertThat(saved).isSameAs(snap);
    }

    @Test
    @DisplayName("findByRequestId returns saved snapshot in same workspace")
    void shouldFindSavedSnapshot() {
        runPromise(() -> repository.save(WS_ID, buildSnapshot("req-1")));

        Optional<ApprovalSnapshot> found = runPromise(() -> repository.findByRequestId(WS_ID, "req-1"));

        assertThat(found).isPresent();
        assertThat(found.get().requestId()).isEqualTo("req-1");
    }

    @Test
    @DisplayName("findByRequestId returns empty for missing request")
    void shouldReturnEmptyForMissing() {
        Optional<ApprovalSnapshot> found = runPromise(() -> repository.findByRequestId(WS_ID, "no-such-req"));
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("workspace isolation: snapshot saved in ws-1 is not visible from ws-2")
    void shouldIsolateWorkspaces() {
        runPromise(() -> repository.save(WS_ID, buildSnapshot("req-1")));

        Optional<ApprovalSnapshot> found = runPromise(() -> repository.findByRequestId("ws-2", "req-1"));

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("save overwrites snapshot with same requestId in same workspace")
    void shouldOverwriteExistingSnapshot() {
        ApprovalSnapshot first  = buildSnapshot("req-1");
        ApprovalSnapshot second = buildSnapshot("req-1");

        runPromise(() -> repository.save(WS_ID, first));
        runPromise(() -> repository.save(WS_ID, second));

        Optional<ApprovalSnapshot> found = runPromise(() -> repository.findByRequestId(WS_ID, "req-1"));
        assertThat(found).isPresent();
    }

    @Test
    @DisplayName("save rejects null workspaceId")
    void shouldRejectNullWorkspaceId() {
        assertThatNullPointerException()
            .isThrownBy(() -> repository.save(null, buildSnapshot("req-1")));
    }

    @Test
    @DisplayName("save rejects null snapshot")
    void shouldRejectNullSnapshot() {
        assertThatNullPointerException()
            .isThrownBy(() -> repository.save(WS_ID, null));
    }

    @Test
    @DisplayName("findByRequestId rejects null arguments")
    void shouldRejectNullFindArgs() {
        assertThatNullPointerException()
            .isThrownBy(() -> repository.findByRequestId(null, "req-1"));
        assertThatNullPointerException()
            .isThrownBy(() -> repository.findByRequestId(WS_ID, null));
    }

    private static ApprovalSnapshot buildSnapshot(String requestId) {
        return new ApprovalSnapshot(
            requestId,
            ApprovalTargetType.CAMPAIGN_LAUNCH,
            "target-entity-id",
            WS_ID,
            "Summary of snapshot",
            "val-result-123",
            2,
            "COMPLIANCE_OFFICER",
            Instant.now()
        );
    }
}
