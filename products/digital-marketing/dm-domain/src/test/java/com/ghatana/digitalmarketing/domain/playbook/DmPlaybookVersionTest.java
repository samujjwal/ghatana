package com.ghatana.digitalmarketing.domain.playbook;

import com.ghatana.digitalmarketing.domain.playbook.DmPlaybookVersion;
import com.ghatana.digitalmarketing.domain.playbook.DmPlaybookVersionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

@DisplayName("DmPlaybookVersion domain entity")
class DmPlaybookVersionTest {

    private DmPlaybookVersion valid() {
        return DmPlaybookVersion.builder()
            .id("pv-1").tenantId("t1").workspaceId("ws1").playbookId("pb-1")
            .versionNumber(1).contentJson("{\"steps\":[]}").status(DmPlaybookVersionStatus.DRAFT)
            .createdAt(Instant.now()).build();
    }

    @Test @DisplayName("builder creates valid entity")
    void shouldBuildValid() {
        DmPlaybookVersion v = valid();
        assertThat(v.getId()).isEqualTo("pv-1");
        assertThat(v.getStatus()).isEqualTo(DmPlaybookVersionStatus.DRAFT);
    }

    @Test @DisplayName("builder rejects blank id")
    void shouldRejectBlankId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmPlaybookVersion.builder().id("").tenantId("t").playbookId("pb")
                .versionNumber(1).contentJson("{}").status(DmPlaybookVersionStatus.DRAFT)
                .createdAt(Instant.now()).build());
    }

    @Test @DisplayName("promote from DRAFT succeeds")
    void shouldPromote() {
        DmPlaybookVersion active = valid().promote("user-1");
        assertThat(active.getStatus()).isEqualTo(DmPlaybookVersionStatus.ACTIVE);
        assertThat(active.getPromotedBy()).isEqualTo("user-1");
        assertThat(active.getPromotedAt()).isNotNull();
    }

    @Test @DisplayName("promote from non-DRAFT fails")
    void shouldNotPromoteTwice() {
        assertThatIllegalStateException().isThrownBy(() -> valid().promote("u").promote("u"));
    }

    @Test @DisplayName("archive from ACTIVE succeeds")
    void shouldArchive() {
        DmPlaybookVersion archived = valid().promote("u").archive();
        assertThat(archived.getStatus()).isEqualTo(DmPlaybookVersionStatus.ARCHIVED);
    }

    @Test @DisplayName("archive from non-ACTIVE fails")
    void shouldNotArchiveFromDraft() {
        assertThatIllegalStateException().isThrownBy(() -> valid().archive());
    }

    @Test @DisplayName("equals and hashCode are id-based")
    void shouldEqualById() {
        assertThat(valid()).isEqualTo(valid());
    }

    @Test @DisplayName("null id throws")
    void shouldRejectNullId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmPlaybookVersion.builder().id(null).tenantId("t").playbookId("pb")
                .status(DmPlaybookVersionStatus.DRAFT).createdAt(java.time.Instant.now()).build());
    }

    @Test @DisplayName("blank tenantId throws")
    void shouldRejectBlankTenantId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmPlaybookVersion.builder().id("x").tenantId("").playbookId("pb")
                .status(DmPlaybookVersionStatus.DRAFT).createdAt(java.time.Instant.now()).build());
    }
}
