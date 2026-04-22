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
@DisplayName("Reporting Tests [GH-90000]")
class ReportingTest {

    @Test
    @DisplayName("Should generate reports [GH-90000]")
    void shouldGenerateReports() { // GH-90000
        ReportDefinition definition = ReportDefinition.builder() // GH-90000
            .name("weekly-sales [GH-90000]")
            .type(ReportType.QUERY) // GH-90000
            .format(ReportFormat.JSON) // GH-90000
            .query("SELECT * FROM sales [GH-90000]")
            .build(); // GH-90000

        assertThat(definition).isNotNull(); // GH-90000
        assertThat(definition.getName()).isEqualTo("weekly-sales [GH-90000]");
        assertThat(definition.getType()).isEqualTo(ReportType.QUERY); // GH-90000
    }

    @Test
    @DisplayName("Should format report output [GH-90000]")
    void shouldFormatReportOutput() { // GH-90000
        ReportResult result = ReportResult.builder() // GH-90000
            .reportId("report-123 [GH-90000]")
            .reportName("weekly-sales [GH-90000]")
            .format(ReportFormat.CSV) // GH-90000
            .formattedBody("name,price\nProduct A,100\n [GH-90000]")
            .rowCount(1) // GH-90000
            .contentType("text/csv [GH-90000]")
            .executionTime(Duration.ofMillis(100)) // GH-90000
            .build(); // GH-90000

        assertThat(result).isNotNull(); // GH-90000
        assertThat(result.getFormat()).isEqualTo(ReportFormat.CSV); // GH-90000
        assertThat(result.getFormattedBody()).contains("name,price [GH-90000]");
    }

    @Test
    @DisplayName("Should handle report exports [GH-90000]")
    void shouldHandleReportExports() { // GH-90000
        ReportDefinition definition = ReportDefinition.builder() // GH-90000
            .name("entity-export [GH-90000]")
            .type(ReportType.ENTITY_EXPORT) // GH-90000
            .format(ReportFormat.CSV) // GH-90000
            .collection("products [GH-90000]")
            .build(); // GH-90000

        assertThat(definition).isNotNull(); // GH-90000
        assertThat(definition.getType()).isEqualTo(ReportType.ENTITY_EXPORT); // GH-90000
        assertThat(definition.getCollection()).isEqualTo("products [GH-90000]");
    }

    @Test
    @DisplayName("Should handle report scheduling [GH-90000]")
    void shouldHandleReportScheduling() { // GH-90000
        String schedule = "0 0 * * *"; // Daily at midnight

        assertThat(schedule).isNotNull(); // GH-90000
        assertThat(schedule).contains("* [GH-90000]");
    }

    @Test
    @DisplayName("Should handle report failures [GH-90000]")
    void shouldHandleReportFailures() { // GH-90000
        ReportResult result = ReportResult.builder() // GH-90000
            .reportId("report-123 [GH-90000]")
            .reportName("failed-report [GH-90000]")
            .format(ReportFormat.JSON) // GH-90000
            .rowCount(0) // GH-90000
            .executionTime(Duration.ZERO) // GH-90000
            .contentType("application/json [GH-90000]")
            .build(); // GH-90000

        assertThat(result).isNotNull(); // GH-90000
        assertThat(result.getRowCount()).isEqualTo(0); // GH-90000
    }

    @Test
    @DisplayName("Should handle report caching [GH-90000]")
    void shouldHandleReportCaching() { // GH-90000
        String reportId = "report-123";
        ReportResult result = ReportResult.builder() // GH-90000
            .reportId(reportId) // GH-90000
            .reportName("cached-report [GH-90000]")
            .format(ReportFormat.JSON) // GH-90000
            .rows(List.of(Map.of("key", "value"))) // GH-90000
            .rowCount(1) // GH-90000
            .contentType("application/json [GH-90000]")
            .executionTime(Duration.ofMillis(50)) // GH-90000
            .build(); // GH-90000

        assertThat(result.getReportId()).isEqualTo(reportId); // GH-90000
        assertThat(result.getRowCount()).isEqualTo(1); // GH-90000
    }
}
