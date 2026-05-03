package com.ghatana.digitalmarketing.domain.report;

import com.ghatana.digitalmarketing.domain.report.DmPerformanceReport;
import com.ghatana.digitalmarketing.domain.report.DmPerformanceReport.DmReportPeriod;
import com.ghatana.digitalmarketing.domain.report.DmPerformanceReport.DmReportSection;
import com.ghatana.digitalmarketing.domain.report.DmReportStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("DmPerformanceReport domain entity")
class DmPerformanceReportTest {

    private DmPerformanceReport valid() {
        Instant now = Instant.now();
        return DmPerformanceReport.builder()
            .id("rpt-1").tenantId("t1").workspaceId("ws1").title("Q1 Report")
            .period(new DmReportPeriod(now.minusSeconds(86400 * 90), now))
            .sections(List.of(new DmReportSection("Overview", "content", "SUMMARY")))
            .status(DmReportStatus.READY)
            .generatedByActor("user-1").generatedAt(now).createdAt(now).build();
    }

    @Test @DisplayName("builder creates valid entity")
    void shouldBuildValid() {
        DmPerformanceReport r = valid();
        assertThat(r.getId()).isEqualTo("rpt-1");
        assertThat(r.getStatus()).isEqualTo(DmReportStatus.READY);
    }

    @Test @DisplayName("builder rejects blank id")
    void shouldRejectBlankId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmPerformanceReport.builder().id("").tenantId("t").title("t")
                .sections(List.of()).status(DmReportStatus.PENDING).createdAt(Instant.now()).build());
    }

    @Test @DisplayName("builder rejects blank title")
    void shouldRejectBlankTitle() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmPerformanceReport.builder().id("x").tenantId("t").title("")
                .sections(List.of()).status(DmReportStatus.PENDING).createdAt(Instant.now()).build());
    }

    @Test @DisplayName("sections list is immutable")
    void shouldHaveImmutableSections() {
        assertThat(valid().getSections()).isUnmodifiable();
    }

    @Test @DisplayName("equals and hashCode are id-based")
    void shouldEqualById() {
        assertThat(valid()).isEqualTo(valid());
        assertThat(valid().hashCode()).isEqualTo(valid().hashCode());
    }

    @Test @DisplayName("equals returns false for null")
    void shouldNotEqualNull() { assertThat(valid()).isNotEqualTo(null); }

    @Test @DisplayName("equals returns false for different type")
    void shouldNotEqualDifferentType() { assertThat(valid()).isNotEqualTo("x"); }

    @Test @DisplayName("all getters return expected values")
    void shouldExposeAllGetters() {
        DmPerformanceReport r = valid();
        assertThat(r.getTenantId()).isEqualTo("t1");
        assertThat(r.getWorkspaceId()).isEqualTo("ws1");
        assertThat(r.getTitle()).isEqualTo("Q1 Report");
        assertThat(r.getPeriod()).isNotNull();
        assertThat(r.getSections()).hasSize(1);
        assertThat(r.getGeneratedByActor()).isEqualTo("user-1");
        assertThat(r.getGeneratedAt()).isNotNull();
        assertThat(r.getCreatedAt()).isNotNull();
        assertThat(r.toString()).contains("rpt-1");
    }

    @Test @DisplayName("builder rejects null status")
    void shouldRejectNullStatus() {
        Instant now = Instant.now();
        assertThatNullPointerException().isThrownBy(() ->
            DmPerformanceReport.builder().id("x").tenantId("t").title("t")
                .period(new DmReportPeriod(now.minusSeconds(1), now))
                .sections(List.of()).status(null).createdAt(now).build());
    }

    @Test @DisplayName("builder rejects null sections")
    void shouldRejectNullSections() {
        Instant now = Instant.now();
        assertThatNullPointerException().isThrownBy(() ->
            DmPerformanceReport.builder().id("x").tenantId("t").title("t")
                .period(new DmReportPeriod(now.minusSeconds(1), now))
                .sections(null).status(DmReportStatus.PENDING).createdAt(now).build());
    }

    @Test @DisplayName("null id throws")
    void shouldRejectNullId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmPerformanceReport.builder().id(null).tenantId("t").title("t")
                .sections(java.util.List.of()).status(DmReportStatus.PENDING)
                .createdAt(java.time.Instant.now()).build());
    }

    @Test @DisplayName("blank tenantId throws")
    void shouldRejectBlankTenantId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmPerformanceReport.builder().id("x").tenantId("").title("t")
                .sections(java.util.List.of()).status(DmReportStatus.PENDING)
                .createdAt(java.time.Instant.now()).build());
    }
}
