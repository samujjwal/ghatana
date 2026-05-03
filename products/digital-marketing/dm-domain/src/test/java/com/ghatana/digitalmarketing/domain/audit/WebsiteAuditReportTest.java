package com.ghatana.digitalmarketing.domain.audit;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("WebsiteAuditReport")
class WebsiteAuditReportTest {

    @Test
    @DisplayName("builds report with findings")
    void shouldBuildReport() {
        WebsiteAuditFinding finding = new WebsiteAuditFinding(
            AuditSeverity.WARNING,
            "SEO",
            "Missing H1",
            "Landing page has no primary heading.",
            "Add a clear H1 aligned with search intent.",
            "https://acme.test"
        );

        WebsiteAuditReport report = WebsiteAuditReport.builder()
            .reportId("audit-1")
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .websiteUrl("https://acme.test")
            .findings(List.of(finding))
            .generatedAt(Instant.now())
            .generatedBy("user-1")
            .build();

        assertThat(report.getFindings()).hasSize(1);
        assertThat(report.getReportId()).isEqualTo("audit-1");
        assertThat(report.getWorkspaceId()).isEqualTo(DmWorkspaceId.of("ws-1"));
        assertThat(report.getWebsiteUrl()).isEqualTo("https://acme.test");
        assertThat(report.getGeneratedBy()).isEqualTo("user-1");

        assertThat(finding.severity()).isEqualTo(AuditSeverity.WARNING);
        assertThat(finding.category()).isEqualTo("SEO");
        assertThat(finding.evidence()).isEqualTo("Missing H1");
        assertThat(finding.rationale()).contains("primary heading");
        assertThat(finding.recommendedAction()).contains("Add a clear H1");
        assertThat(finding.sourceUrl()).isEqualTo("https://acme.test");
    }

    @Test
    @DisplayName("rejects invalid finding and report fields")
    void shouldRejectInvalidInputs() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new WebsiteAuditFinding(
                AuditSeverity.INFO,
                "",
                "evidence",
                "rationale",
                "action",
                "https://acme.test"
            ));

        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new WebsiteAuditFinding(
                AuditSeverity.INFO,
                "SEO",
                "evidence",
                "rationale",
                "action",
                " "
            ));

        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> WebsiteAuditReport.builder()
                .reportId(" ")
                .workspaceId(DmWorkspaceId.of("ws-1"))
                .websiteUrl("https://acme.test")
                .generatedAt(Instant.now())
                .generatedBy("user-1")
                .build());

        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> WebsiteAuditReport.builder()
                .reportId("audit-1")
                .workspaceId(DmWorkspaceId.of("ws-1"))
                .websiteUrl(" ")
                .generatedAt(Instant.now())
                .generatedBy("user-1")
                .build());

        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> WebsiteAuditReport.builder()
                .reportId("audit-1")
                .workspaceId(DmWorkspaceId.of("ws-1"))
                .websiteUrl("https://acme.test")
                .generatedAt(Instant.now())
                .generatedBy(" ")
                .build());
    }

    @Test
    @DisplayName("finding rejects null severity and null/blank string fields")
    void shouldRejectNullAndBlankFindingFields() {
        assertThatNullPointerException()
            .isThrownBy(() -> new WebsiteAuditFinding(null, "SEO", "ev", "rat", "action", "https://url"));

        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new WebsiteAuditFinding(AuditSeverity.INFO, null, "ev", "rat", "action", "https://url"));

        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new WebsiteAuditFinding(AuditSeverity.INFO, "SEO", null, "rat", "action", "https://url"));

        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new WebsiteAuditFinding(AuditSeverity.INFO, "SEO", " ", "rat", "action", "https://url"));

        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new WebsiteAuditFinding(AuditSeverity.INFO, "SEO", "ev", null, "action", "https://url"));

        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new WebsiteAuditFinding(AuditSeverity.INFO, "SEO", "ev", " ", "action", "https://url"));

        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new WebsiteAuditFinding(AuditSeverity.INFO, "SEO", "ev", "rat", null, "https://url"));

        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new WebsiteAuditFinding(AuditSeverity.INFO, "SEO", "ev", "rat", " ", "https://url"));

        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new WebsiteAuditFinding(AuditSeverity.INFO, "SEO", "ev", "rat", "action", null));
    }
}
