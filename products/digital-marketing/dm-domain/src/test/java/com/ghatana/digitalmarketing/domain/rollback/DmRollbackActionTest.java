package com.ghatana.digitalmarketing.domain.rollback;

import com.ghatana.digitalmarketing.domain.rollback.DmRollbackAction;
import com.ghatana.digitalmarketing.domain.rollback.DmRollbackStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

@DisplayName("DmRollbackAction domain entity")
class DmRollbackActionTest {

    private DmRollbackAction valid() {
        return DmRollbackAction.builder()
            .id("rb-1").tenantId("t1").workspaceId("ws1")
            .commandId("cmd-1").actionType("DEACTIVATE_CAMPAIGN")
            .targetEntityId("entity-1").targetEntityType("Campaign")
            .status(DmRollbackStatus.PENDING).createdAt(Instant.now()).build();
    }

    @Test @DisplayName("builder creates valid entity")
    void shouldBuildValid() {
        DmRollbackAction r = valid();
        assertThat(r.getId()).isEqualTo("rb-1");
        assertThat(r.getStatus()).isEqualTo(DmRollbackStatus.PENDING);
    }

    @Test @DisplayName("builder rejects blank id")
    void shouldRejectBlankId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmRollbackAction.builder().id("").tenantId("t").commandId("c")
                .actionType("a").targetEntityId("e").targetEntityType("t")
                .status(DmRollbackStatus.PENDING).createdAt(Instant.now()).build());
    }

    @Test @DisplayName("markCompleted transitions from PENDING")
    void shouldMarkCompleted() {
        DmRollbackAction completed = valid().markCompleted();
        assertThat(completed.getStatus()).isEqualTo(DmRollbackStatus.COMPLETED);
        assertThat(completed.getExecutedAt()).isNotNull();
    }

    @Test @DisplayName("markCompleted rejects non-PENDING state")
    void shouldNotCompleteTwice() {
        DmRollbackAction completed = valid().markCompleted();
        assertThatIllegalStateException().isThrownBy(completed::markCompleted);
    }

    @Test @DisplayName("markFailed transitions from PENDING")
    void shouldMarkFailed() {
        DmRollbackAction failed = valid().markFailed("error");
        assertThat(failed.getStatus()).isEqualTo(DmRollbackStatus.FAILED);
        assertThat(failed.getFailureReason()).isEqualTo("error");
    }

    @Test @DisplayName("equals and hashCode are id-based")
    void shouldEqualById() {
        assertThat(valid()).isEqualTo(valid());
        assertThat(valid().hashCode()).isEqualTo(valid().hashCode());
    }

    @Test @DisplayName("null id throws")
    void shouldRejectNullId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmRollbackAction.builder().id(null).tenantId("t").commandId("c")
                .actionType("a").targetEntityId("e").targetEntityType("t")
                .status(DmRollbackStatus.PENDING).createdAt(java.time.Instant.now()).build());
    }

    @Test @DisplayName("blank tenantId throws")
    void shouldRejectBlankTenantId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmRollbackAction.builder().id("x").tenantId("").commandId("c")
                .actionType("a").targetEntityId("e").targetEntityType("t")
                .status(DmRollbackStatus.PENDING).createdAt(java.time.Instant.now()).build());
    }
}
