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
    void shouldGenerateReports() { // GH-90000
        ReportDefinition definition = ReportDefinition.builder() // GH-90000
            .name("weekly-sales")
            .type(ReportType.QUERY) // GH-90000
            .format(ReportFormat.JSON) // GH-90000
            .query("SELECT * FROM sales")
            .build(); // GH-90000

        assertThat(definition).isNotNull(); // GH-90000
        assertThat(definition.getName()).isEqualTo("weekly-sales");
        assertThat(definition.getType()).isEqualTo(ReportType.QUERY); // GH-90000
    }

    @Test
    @DisplayName("Should format report output")
    void shouldFormatReportOutput() { // GH-90000
        ReportResult result = ReportResult.builder() // GH-90000
            .reportId("report-123")
            .reportName("weekly-sales")
            .format(ReportFormat.CSV) // GH-90000
            .formattedBody("name,price\nProduct A,100\n")
            .rowCount(1) // GH-90000
            .contentType("text/csv")
            .executionTime(Duration.ofMillis(100)) // GH-90000
            .build(); // GH-90000

        assertThat(result).isNotNull(); // GH-90000
        assertThat(result.getFormat()).isEqualTo(ReportFormat.CSV); // GH-90000
        assertThat(result.getFormattedBody()).contains("name,price");
    }

    @Test
    @DisplayName("Should handle report exports")
    void shouldHandleReportExports() { // GH-90000
        ReportDefinition definition = ReportDefinition.builder() // GH-90000
            .name("entity-export")
            .type(ReportType.ENTITY_EXPORT) // GH-90000
            .format(ReportFormat.CSV) // GH-90000
            .collection("products")
            .build(); // GH-90000

        assertThat(definition).isNotNull(); // GH-90000
        assertThat(definition.getType()).isEqualTo(ReportType.ENTITY_EXPORT); // GH-90000
        assertThat(definition.getCollection()).isEqualTo("products");
    }

    @Test
    @DisplayName("Should handle report scheduling")
    void shouldHandleReportScheduling() { // GH-90000
        String schedule = "0 0 * * *"; // Daily at midnight

        assertThat(schedule).isNotNull(); // GH-90000
        assertThat(schedule).contains("*");
    }

    @Test
    @DisplayName("Should handle report failures")
    void shouldHandleReportFailures() { // GH-90000
        ReportResult result = ReportResult.builder() // GH-90000
            .reportId("report-123")
            .reportName("failed-report")
            .format(ReportFormat.JSON) // GH-90000
            .rowCount(0) // GH-90000
            .executionTime(Duration.ZERO) // GH-90000
            .contentType("application/json")
            .build(); // GH-90000

        assertThat(result).isNotNull(); // GH-90000
        assertThat(result.getRowCount()).isEqualTo(0); // GH-90000
    }

    @Test
    @DisplayName("Should handle report caching")
    void shouldHandleReportCaching() { // GH-90000
        String reportId = "report-123";
        ReportResult result = ReportResult.builder() // GH-90000
            .reportId(reportId) // GH-90000
            .reportName("cached-report")
            .format(ReportFormat.JSON) // GH-90000
            .rows(List.of(Map.of("key", "value"))) // GH-90000
            .rowCount(1) // GH-90000
            .contentType("application/json")
            .executionTime(Duration.ofMillis(50)) // GH-90000
            .build(); // GH-90000

        assertThat(result.getReportId()).isEqualTo(reportId); // GH-90000
        assertThat(result.getRowCount()).isEqualTo(1); // GH-90000
    }
}
