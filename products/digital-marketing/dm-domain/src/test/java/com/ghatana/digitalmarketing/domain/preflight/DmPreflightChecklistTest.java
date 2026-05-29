package com.ghatana.digitalmarketing.domain.preflight;

import com.ghatana.digitalmarketing.domain.preflight.DmPreflightChecklist;
import com.ghatana.digitalmarketing.domain.preflight.DmPreflightChecklist.DmPreflightCheckItem;
import com.ghatana.digitalmarketing.domain.preflight.DmPreflightCheckResult;
import com.ghatana.digitalmarketing.domain.preflight.DmPreflightStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("DmPreflightChecklist domain entity")
class DmPreflightChecklistTest {

    private static final Instant NOW = Instant.parse("2026-05-02T18:00:00Z");

    private DmPreflightChecklist valid() {
        return DmPreflightChecklist.builder()
            .id("pf-1").tenantId("t1").workspaceId("ws1").campaignId("c1")
            .items(List.of(new DmPreflightCheckItem("budget", "Budget set", true, DmPreflightCheckResult.PASSED, null)))
            .status(DmPreflightStatus.PASSED).evaluatedAt(NOW).createdAt(NOW).build();
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
                .items(List.of()).status(DmPreflightStatus.PENDING).createdAt(NOW).build());
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
            .status(DmPreflightStatus.BLOCKED).createdAt(NOW).build();
        assertThat(failed.allRequiredPassed()).isFalse();
    }

    @Test @DisplayName("allRequiredPassed ignores optional failed checks")
    void shouldIgnoreOptionalFailures() {
        DmPreflightChecklist checklist = DmPreflightChecklist.builder()
            .id("pf-3").tenantId("t1").workspaceId("ws1").campaignId("c1")
            .items(List.of(
                new DmPreflightCheckItem("budget", "Budget", true, DmPreflightCheckResult.PASSED, null),
                new DmPreflightCheckItem("notes", "Optional notes", false, DmPreflightCheckResult.FAILED, "missing notes")
            ))
            .status(DmPreflightStatus.PASSED).createdAt(NOW).build();

        assertThat(checklist.allRequiredPassed()).isTrue();
    }

    @Test @DisplayName("items list is immutable")
    void shouldHaveImmutableItems() {
        assertThat(valid().getItems()).isUnmodifiable();
    }

    @Test @DisplayName("equals and hashCode are id-based")
    void shouldEqualById() {
        DmPreflightChecklist left = valid();
        DmPreflightChecklist sameId = valid();
        DmPreflightChecklist differentId = DmPreflightChecklist.builder()
            .id("pf-9").tenantId("t1").workspaceId("ws1").campaignId("c1")
            .items(List.of(new DmPreflightCheckItem("budget", "Budget set", true, DmPreflightCheckResult.PASSED, null)))
            .status(DmPreflightStatus.PASSED).createdAt(NOW).build();

        assertThat(left)
            .isEqualTo(sameId)
            .hasSameHashCodeAs(sameId)
            .isNotEqualTo(differentId)
            .isNotEqualTo("pf-1");
    }

    @Test @DisplayName("getters and toString expose checklist state")
    void shouldExposeState() {
        DmPreflightChecklist checklist = valid();

        assertThat(checklist.getTenantId()).isEqualTo("t1");
        assertThat(checklist.getWorkspaceId()).isEqualTo("ws1");
        assertThat(checklist.getCampaignId()).isEqualTo("c1");
        assertThat(checklist.getEvaluatedAt()).isEqualTo(NOW);
        assertThat(checklist.getCreatedAt()).isEqualTo(NOW);
        assertThat(checklist.toString()).contains("pf-1", "PASSED");
    }

    @Test @DisplayName("builder rejects blank tenant id")
    void shouldRejectBlankTenantId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmPreflightChecklist.builder().id("pf-1").tenantId(" ").workspaceId("w").campaignId("c")
                .items(List.of()).status(DmPreflightStatus.PENDING).createdAt(NOW).build());
    }

    @Test @DisplayName("builder requires status")
    void shouldRequireStatus() {
        assertThatNullPointerException().isThrownBy(() ->
            DmPreflightChecklist.builder().id("pf-1").tenantId("t1").workspaceId("w").campaignId("c")
                .items(List.of()).createdAt(NOW).build())
            .withMessage("status must not be null");
    }

    @Test @DisplayName("builder requires createdAt")
    void shouldRequireCreatedAt() {
        assertThatNullPointerException().isThrownBy(() ->
            DmPreflightChecklist.builder().id("pf-1").tenantId("t1").workspaceId("w").campaignId("c")
                .items(List.of()).status(DmPreflightStatus.PENDING).build())
            .withMessage("createdAt must not be null");
    }

    @Test @DisplayName("builder requires items list")
    void shouldRequireItems() {
        assertThatNullPointerException().isThrownBy(() ->
            DmPreflightChecklist.builder().id("pf-1").tenantId("t1").workspaceId("w").campaignId("c")
                .items(null).status(DmPreflightStatus.PENDING).createdAt(NOW).build())
            .withMessage("items must not be null");
    }

    @Test @DisplayName("check item requires name and result")
    void shouldValidateCheckItemFields() {
        assertThatNullPointerException().isThrownBy(() ->
            new DmPreflightCheckItem(null, "desc", true, DmPreflightCheckResult.PASSED, null))
            .withMessage("name must not be null");

        assertThatNullPointerException().isThrownBy(() ->
            new DmPreflightCheckItem("budget", "desc", true, null, null))
            .withMessage("result must not be null");
    }
}
