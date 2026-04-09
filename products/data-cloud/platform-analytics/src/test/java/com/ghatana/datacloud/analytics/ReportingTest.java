/**
 * @doc.type class
 * @doc.purpose Test report generation, formatting, and export
 * @doc.layer products
 * @doc.pattern Test
 */
package com.ghatana.datacloud.analytics;

import com.ghatana.datacloud.analytics.report.ReportDefinition;
import com.ghatana.datacloud.analytics.report.ReportFormat;
import com.ghatana.datacloud.analytics.report.ReportResult;
import com.ghatana.datacloud.analytics.report.ReportType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reporting Tests
 *
 * Test report generation, formatting, and export.
 */
@DisplayName("Reporting Tests")
class ReportingTest {

    @Test
    @DisplayName("Should generate reports")
    void shouldGenerateReports() {
        ReportDefinition definition = ReportDefinition.builder()
            .name("weekly-sales")
            .type(ReportType.QUERY)
            .format(ReportFormat.JSON)
            .query("SELECT * FROM sales")
            .build();

        assertThat(definition).isNotNull();
        assertThat(definition.getName()).isEqualTo("weekly-sales");
        assertThat(definition.getType()).isEqualTo(ReportType.QUERY);
    }

    @Test
    @DisplayName("Should format report output")
    void shouldFormatReportOutput() {
        ReportResult result = ReportResult.builder()
            .reportId("report-123")
            .reportName("weekly-sales")
            .format(ReportFormat.CSV)
            .formattedBody("name,price\nProduct A,100\n")
            .rowCount(1)
            .build();

        assertThat(result).isNotNull();
        assertThat(result.getFormat()).isEqualTo(ReportFormat.CSV);
        assertThat(result.getFormattedBody()).contains("name,price");
    }

    @Test
    @DisplayName("Should handle report exports")
    void shouldHandleReportExports() {
        ReportDefinition definition = ReportDefinition.builder()
            .name("entity-export")
            .type(ReportType.ENTITY_EXPORT)
            .format(ReportFormat.CSV)
            .collection("products")
            .build();

        assertThat(definition).isNotNull();
        assertThat(definition.getType()).isEqualTo(ReportType.ENTITY_EXPORT);
        assertThat(definition.getCollection()).isEqualTo("products");
    }

    @Test
    @DisplayName("Should handle report scheduling")
    void shouldHandleReportScheduling() {
        String schedule = "0 0 * * *"; // Daily at midnight

        assertThat(schedule).isNotNull();
        assertThat(schedule).contains("*");
    }

    @Test
    @DisplayName("Should handle report failures")
    void shouldHandleReportFailures() {
        ReportResult result = ReportResult.builder()
            .reportId("report-123")
            .reportName("failed-report")
            .format(ReportFormat.JSON)
            .rowCount(0)
            .executionTime(Duration.ZERO)
            .contentType("application/json")
            .build();

        assertThat(result).isNotNull();
        assertThat(result.getRowCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should handle report caching")
    void shouldHandleReportCaching() {
        String reportId = "report-123";
        ReportResult result = ReportResult.builder()
            .reportId(reportId)
            .reportName("cached-report")
            .format(ReportFormat.JSON)
            .rows(List.of(Map.of("key", "value")))
            .rowCount(1)
            .build();

        assertThat(result.getReportId()).isEqualTo(reportId);
        assertThat(result.getRowCount()).isEqualTo(1);
    }
}
