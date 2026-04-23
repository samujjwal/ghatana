/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.application;

import com.ghatana.datacloud.analytics.Report;
import com.ghatana.datacloud.analytics.ReportRepository;
import com.ghatana.datacloud.analytics.ReportTemplate;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ReportServiceImpl Tests - 100% Coverage
 *
 * @doc.type class
 * @doc.purpose Comprehensive tests for ReportServiceImpl
 * @doc.layer test
 * @doc.pattern Unit Test
 */
@DisplayName("ReportServiceImpl Tests")
class ReportServiceImplTest extends EventloopTestBase {

    @Mock
    private ReportRepository repository;

    @Mock
    private MetricsCollector metrics;

    private ReportServiceImpl service;

    @BeforeEach
    void setUp() { // GH-90000
        MockitoAnnotations.openMocks(this); // GH-90000
        service = new ReportServiceImpl(repository, metrics); // GH-90000
    }

    @Nested
    @DisplayName("Generate Report")
    class GenerateReportTests {

        @Test
        @DisplayName("[TEST-005]: generateReport_successfully_creates_report")
        void generateReportSuccessfully() { // GH-90000
            // Given
            String tenantId = "tenant-alpha";
            ReportTemplate template = new ReportTemplate() // GH-90000
                .withName("Monthly Sales")
                .withFormat("CSV")
                .withQuery("SELECT * FROM sales WHERE month = '2024-01'");

            when(repository.save(any(Report.class))).thenAnswer(invocation -> // GH-90000
                Promise.of(invocation.getArgument(0))); // GH-90000

            // When
            Report result = runPromise(() -> service.generateReport(tenantId, template)); // GH-90000

            // Then
            assertThat(result).isNotNull(); // GH-90000
            assertThat(result.getTenantId()).isEqualTo(tenantId); // GH-90000
            assertThat(result.getName()).isEqualTo("Monthly Sales");
            assertThat(result.getFormat()).isEqualTo("CSV");
            assertThat(result.getStatus()).isEqualTo("COMPLETED");
            verify(metrics).incrementCounter("report.generate.success", "tenant", tenantId, "format", "CSV"); // GH-90000
        }

        @Test
        @DisplayName("[TEST-005]: generateReport_increments_error_metric_on_failure")
        void generateReportError() { // GH-90000
            // Given
            String tenantId = "tenant-alpha";
            ReportTemplate template = new ReportTemplate() // GH-90000
                .withName("Monthly Sales")
                .withFormat("CSV")
                .withQuery("SELECT 1");

            when(repository.save(any(Report.class))) // GH-90000
                .thenReturn(Promise.ofException(new RuntimeException("DB error")));

            // When
            clearFatalError(); // GH-90000
            try {
                runPromise(() -> service.generateReport(tenantId, template)); // GH-90000
            } catch (Exception e) { // GH-90000
                // Expected
            }

            // Then
            verify(metrics).incrementCounter("report.generate.error", "tenant", tenantId, "error", "RuntimeException"); // GH-90000
        }
    }

    @Nested
    @DisplayName("Get Report")
    class GetReportTests {

        @Test
        @DisplayName("[TEST-006]: getReport_returns_report_when_found")
        void getReportFound() { // GH-90000
            // Given
            String tenantId = "tenant-alpha";
            String reportId = "report-1";

            Report report = new Report() // GH-90000
                .withId(reportId) // GH-90000
                .withTenantId(tenantId) // GH-90000
                .withName("Monthly Sales")
                .withFormat("CSV")
                .withStatus("COMPLETED");

            when(repository.findById(tenantId, reportId)).thenReturn(Promise.of(report)); // GH-90000

            // When
            Report result = runPromise(() -> service.getReport(tenantId, reportId)); // GH-90000

            // Then
            assertThat(result).isNotNull(); // GH-90000
            assertThat(result.getId()).isEqualTo(reportId); // GH-90000
            verify(metrics).incrementCounter("report.get.success", "tenant", tenantId); // GH-90000
        }

        @Test
        @DisplayName("[TEST-006]: getReport_increments_not_found_when_missing")
        void getReportNotFound() { // GH-90000
            // Given
            String tenantId = "tenant-alpha";
            String reportId = "report-1";

            when(repository.findById(tenantId, reportId)).thenReturn(Promise.of(null)); // GH-90000

            // When
            Report result = runPromise(() -> service.getReport(tenantId, reportId)); // GH-90000

            // Then
            assertThat(result).isNull(); // GH-90000
            verify(metrics).incrementCounter("report.get.not_found", "tenant", tenantId); // GH-90000
        }
    }

    @Nested
    @DisplayName("List Reports")
    class ListReportsTests {

        @Test
        @DisplayName("[TEST-007]: listReports_returns_all_tenant_reports")
        void listReports() { // GH-90000
            // Given
            String tenantId = "tenant-alpha";

            List<Report> reports = List.of( // GH-90000
                new Report().withId("r1").withTenantId(tenantId).withName("Report 1").withFormat("CSV"),
                new Report().withId("r2").withTenantId(tenantId).withName("Report 2").withFormat("PDF")
            );

            when(repository.findAllByTenant(tenantId)).thenReturn(Promise.of(reports)); // GH-90000

            // When
            List<Report> result = runPromise(() -> service.listReports(tenantId)); // GH-90000

            // Then
            assertThat(result).hasSize(2); // GH-90000
            verify(metrics).increment("report.list.count", 2, Map.of("tenant", tenantId)); // GH-90000
        }
    }

    @Nested
    @DisplayName("Update Report")
    class UpdateReportTests {

        @Test
        @DisplayName("[TEST-008]: updateReport_successfully_updates")
        void updateReportSuccessfully() { // GH-90000
            // Given
            String tenantId = "tenant-alpha";
            String reportId = "report-1";
            Map<String, String> updates = Map.of("name", "Updated Name"); // GH-90000

            Report existing = new Report() // GH-90000
                .withId(reportId) // GH-90000
                .withTenantId(tenantId) // GH-90000
                .withName("Original Name")
                .withFormat("CSV")
                .withStatus("COMPLETED")
                .withCreatedAt(System.currentTimeMillis()) // GH-90000
                .withCreatedBy("system");

            when(repository.findById(tenantId, reportId)).thenReturn(Promise.of(existing)); // GH-90000
            when(repository.save(any(Report.class))).thenAnswer(invocation -> // GH-90000
                Promise.of(invocation.getArgument(0))); // GH-90000

            // When
            Report result = runPromise(() -> service.updateReport(tenantId, reportId, updates)); // GH-90000

            // Then
            assertThat(result.getName()).isEqualTo("Updated Name");
            assertThat(result.getFormat()).isEqualTo("CSV");
            verify(metrics).incrementCounter("report.update.success", "tenant", tenantId); // GH-90000
        }

        @Test
        @DisplayName("[TEST-008]: updateReport_throws_when_not_found")
        void updateReportNotFound() { // GH-90000
            // Given
            String tenantId = "tenant-alpha";
            String reportId = "report-1";
            Map<String, String> updates = Map.of("name", "Updated"); // GH-90000

            when(repository.findById(tenantId, reportId)).thenReturn(Promise.of(null)); // GH-90000

            // When
            clearFatalError(); // GH-90000
            try {
                runPromise(() -> service.updateReport(tenantId, reportId, updates)); // GH-90000
            } catch (Exception e) { // GH-90000
                // Expected
            }

            // Then
            verify(metrics).incrementCounter("report.update.error", "tenant", tenantId, "error", "IllegalArgumentException"); // GH-90000
        }
    }

    @Nested
    @DisplayName("Delete Report")
    class DeleteReportTests {

        @Test
        @DisplayName("[TEST-009]: deleteReport_successfully_deletes")
        void deleteReportSuccessfully() { // GH-90000
            // Given
            String tenantId = "tenant-alpha";
            String reportId = "report-1";

            when(repository.delete(tenantId, reportId)).thenReturn(Promise.of(null)); // GH-90000

            // When
            runPromise(() -> service.deleteReport(tenantId, reportId)); // GH-90000

            // Then
            verify(repository).delete(tenantId, reportId); // GH-90000
            verify(metrics).incrementCounter("report.delete.success", "tenant", tenantId); // GH-90000
        }
    }

    @Nested
    @DisplayName("Download Report")
    class DownloadReportTests {

        @Test
        @DisplayName("[TEST-010]: downloadReport_returns_binary_data")
        void downloadReportSuccessfully() { // GH-90000
            // Given
            String tenantId = "tenant-alpha";
            String reportId = "report-1";
            String format = "csv";

            Report report = new Report() // GH-90000
                .withId(reportId) // GH-90000
                .withTenantId(tenantId) // GH-90000
                .withName("Sales Report")
                .withFormat(format); // GH-90000

            when(repository.findById(tenantId, reportId)).thenReturn(Promise.of(report)); // GH-90000

            // When
            byte[] result = runPromise(() -> service.downloadReport(tenantId, reportId, format)); // GH-90000

            // Then
            assertThat(result).isNotEmpty(); // GH-90000
            verify(metrics).incrementCounter("report.download.success", "tenant", tenantId, "format", "csv"); // GH-90000
        }

        @Test
        @DisplayName("[TEST-010]: downloadReport_throws_when_not_found")
        void downloadReportNotFound() { // GH-90000
            // Given
            String tenantId = "tenant-alpha";
            String reportId = "report-1";
            String format = "csv";

            when(repository.findById(tenantId, reportId)).thenReturn(Promise.of(null)); // GH-90000

            // When
            clearFatalError(); // GH-90000
            try {
                runPromise(() -> service.downloadReport(tenantId, reportId, format)); // GH-90000
            } catch (Exception e) { // GH-90000
                // Expected
            }

            // Then
            verify(metrics).incrementCounter("report.download.error", "tenant", tenantId, "error", "IllegalArgumentException"); // GH-90000
        }
    }
}
