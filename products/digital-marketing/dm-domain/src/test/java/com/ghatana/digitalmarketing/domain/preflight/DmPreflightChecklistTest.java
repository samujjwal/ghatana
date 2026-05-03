package com.ghatana.digitalmarketing.domain.preflight;

import com.ghatana.digitalmarketing.domain.preflight.DmPreflightChecklist;
import com.ghatana.digitalmarketing.domain.preflight.DmPreflightChecklist.DmPreflightCheckItem;
import com.ghatana.digitalmarketing.domain.preflight.DmPreflightCheckResult;
import com.ghatana.digitalmarketing.domain.preflight.DmPreflightStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("DmPreflightChecklist domain entity")
class DmPreflightChecklistTest {

    private DmPreflightChecklist valid() {
        return DmPreflightChecklist.builder()
            .id("pf-1").tenantId("t1").workspaceId("ws1").campaignId("c1")
            .items(List.of(new DmPreflightCheckItem("budget", "Budget set", true, DmPreflightCheckResult.PASSED, null)))
            .status(DmPreflightStatus.PASSED).createdAt(Instant.now()).build();
    }

    @Test @DisplayName("builder creates valid entity")
    void shouldBuildValid() {
        DmPreflightChecklist pf = valid();
        assertThat(pf.getId()).isEqualTo("pf-1");
        assertThat(pf.getStatus()).isEqualTo(DmPreflightStatus.PASSED);
    }

    @Test @DisplayName("builder rejects blank id")
    void shouldRejectBlankId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmPreflightChecklist.builder().id("").tenantId("t").workspaceId("w").campaignId("c")
                .items(List.of()).status(DmPreflightStatus.PENDING).createdAt(Instant.now()).build());
    }

    @Test @DisplayName("allRequiredPassed returns true when all required items pass")
    void shouldReturnTrueWhenAllRequiredPass() {
        assertThat(valid().allRequiredPassed()).isTrue();
    }

    @Test @DisplayName("allRequiredPassed returns false when required item fails")
    void shouldReturnFalseWhenRequiredFails() {
        DmPreflightChecklist failed = DmPreflightChecklist.builder()
            .id("pf-2").tenantId("t1").workspaceId("ws1").campaignId("c1")
            .items(List.of(new DmPreflightCheckItem("budget", "Budget", true, DmPreflightCheckResult.FAILED, "no budget")))
            .status(DmPreflightStatus.BLOCKED).createdAt(Instant.now()).build();
        assertThat(failed.allRequiredPassed()).isFalse();
    }

    @Test @DisplayName("items list is immutable")
    void shouldHaveImmutableItems() {
        assertThat(valid().getItems()).isUnmodifiable();
    }

    @Test @DisplayName("equals and hashCode are id-based")
    void shouldEqualById() {
        assertThat(valid()).isEqualTo(valid());
    }
}
