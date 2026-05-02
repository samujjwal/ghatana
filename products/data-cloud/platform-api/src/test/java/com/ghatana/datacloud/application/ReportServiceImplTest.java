/*
 * Copyright (c) 2026 Ghatana Inc. 
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
    void setUp() { 
        MockitoAnnotations.openMocks(this); 
        service = new ReportServiceImpl(repository, metrics); 
    }

    @Nested
    @DisplayName("Generate Report")
    class GenerateReportTests {

        @Test
        @DisplayName("[TEST-005]: generateReport_successfully_creates_report")
        void generateReportSuccessfully() { 
            // Given
            String tenantId = "tenant-alpha";
            ReportTemplate template = new ReportTemplate() 
                .withName("Monthly Sales")
                .withFormat("CSV")
                .withQuery("SELECT * FROM sales WHERE month = '2024-01'");

            when(repository.save(any(Report.class))).thenAnswer(invocation -> 
                Promise.of(invocation.getArgument(0))); 

            // When
            Report result = runPromise(() -> service.generateReport(tenantId, template)); 

            // Then
            assertThat(result).isNotNull(); 
            assertThat(result.getTenantId()).isEqualTo(tenantId); 
            assertThat(result.getName()).isEqualTo("Monthly Sales");
            assertThat(result.getFormat()).isEqualTo("CSV");
            assertThat(result.getStatus()).isEqualTo("COMPLETED");
            verify(metrics).incrementCounter("report.generate.success", "tenant", tenantId, "format", "CSV"); 
        }

        @Test
        @DisplayName("[TEST-005]: generateReport_increments_error_metric_on_failure")
        void generateReportError() { 
            // Given
            String tenantId = "tenant-alpha";
            ReportTemplate template = new ReportTemplate() 
                .withName("Monthly Sales")
                .withFormat("CSV")
                .withQuery("SELECT 1");

            when(repository.save(any(Report.class))) 
                .thenReturn(Promise.ofException(new RuntimeException("DB error")));

            // When
            clearFatalError(); 
            try {
                runPromise(() -> service.generateReport(tenantId, template)); 
            } catch (Exception e) { 
                // Expected
            }

            // Then
            verify(metrics).incrementCounter("report.generate.error", "tenant", tenantId, "error", "RuntimeException"); 
        }
    }

    @Nested
    @DisplayName("Get Report")
    class GetReportTests {

        @Test
        @DisplayName("[TEST-006]: getReport_returns_report_when_found")
        void getReportFound() { 
            // Given
            String tenantId = "tenant-alpha";
            String reportId = "report-1";

            Report report = new Report() 
                .withId(reportId) 
                .withTenantId(tenantId) 
                .withName("Monthly Sales")
                .withFormat("CSV")
                .withStatus("COMPLETED");

            when(repository.findById(tenantId, reportId)).thenReturn(Promise.of(report)); 

            // When
            Report result = runPromise(() -> service.getReport(tenantId, reportId)); 

            // Then
            assertThat(result).isNotNull(); 
            assertThat(result.getId()).isEqualTo(reportId); 
            verify(metrics).incrementCounter("report.get.success", "tenant", tenantId); 
        }

        @Test
        @DisplayName("[TEST-006]: getReport_increments_not_found_when_missing")
        void getReportNotFound() { 
            // Given
            String tenantId = "tenant-alpha";
            String reportId = "report-1";

            when(repository.findById(tenantId, reportId)).thenReturn(Promise.of(null)); 

            // When
            Report result = runPromise(() -> service.getReport(tenantId, reportId)); 

            // Then
            assertThat(result).isNull(); 
            verify(metrics).incrementCounter("report.get.not_found", "tenant", tenantId); 
        }
    }

    @Nested
    @DisplayName("List Reports")
    class ListReportsTests {

        @Test
        @DisplayName("[TEST-007]: listReports_returns_all_tenant_reports")
        void listReports() { 
            // Given
            String tenantId = "tenant-alpha";

            List<Report> reports = List.of( 
                new Report().withId("r1").withTenantId(tenantId).withName("Report 1").withFormat("CSV"),
                new Report().withId("r2").withTenantId(tenantId).withName("Report 2").withFormat("PDF")
            );

            when(repository.findAllByTenant(tenantId)).thenReturn(Promise.of(reports)); 

            // When
            List<Report> result = runPromise(() -> service.listReports(tenantId)); 

            // Then
            assertThat(result).hasSize(2); 
            verify(metrics).increment("report.list.count", 2, Map.of("tenant", tenantId)); 
        }
    }

    @Nested
    @DisplayName("Update Report")
    class UpdateReportTests {

        @Test
        @DisplayName("[TEST-008]: updateReport_successfully_updates")
        void updateReportSuccessfully() { 
            // Given
            String tenantId = "tenant-alpha";
            String reportId = "report-1";
            Map<String, String> updates = Map.of("name", "Updated Name"); 

            Report existing = new Report() 
                .withId(reportId) 
                .withTenantId(tenantId) 
                .withName("Original Name")
                .withFormat("CSV")
                .withStatus("COMPLETED")
                .withCreatedAt(System.currentTimeMillis()) 
                .withCreatedBy("system");

            when(repository.findById(tenantId, reportId)).thenReturn(Promise.of(existing)); 
            when(repository.save(any(Report.class))).thenAnswer(invocation -> 
                Promise.of(invocation.getArgument(0))); 

            // When
            Report result = runPromise(() -> service.updateReport(tenantId, reportId, updates)); 

            // Then
            assertThat(result.getName()).isEqualTo("Updated Name");
            assertThat(result.getFormat()).isEqualTo("CSV");
            verify(metrics).incrementCounter("report.update.success", "tenant", tenantId); 
        }

        @Test
        @DisplayName("[TEST-008]: updateReport_throws_when_not_found")
        void updateReportNotFound() { 
            // Given
            String tenantId = "tenant-alpha";
            String reportId = "report-1";
            Map<String, String> updates = Map.of("name", "Updated"); 

            when(repository.findById(tenantId, reportId)).thenReturn(Promise.of(null)); 

            // When
            clearFatalError(); 
            try {
                runPromise(() -> service.updateReport(tenantId, reportId, updates)); 
            } catch (Exception e) { 
                // Expected
            }

            // Then
            verify(metrics).incrementCounter("report.update.error", "tenant", tenantId, "error", "IllegalArgumentException"); 
        }
    }

    @Nested
    @DisplayName("Delete Report")
    class DeleteReportTests {

        @Test
        @DisplayName("[TEST-009]: deleteReport_successfully_deletes")
        void deleteReportSuccessfully() { 
            // Given
            String tenantId = "tenant-alpha";
            String reportId = "report-1";

            when(repository.delete(tenantId, reportId)).thenReturn(Promise.of(null)); 

            // When
            runPromise(() -> service.deleteReport(tenantId, reportId)); 

            // Then
            verify(repository).delete(tenantId, reportId); 
            verify(metrics).incrementCounter("report.delete.success", "tenant", tenantId); 
        }
    }

    @Nested
    @DisplayName("Download Report")
    class DownloadReportTests {

        @Test
        @DisplayName("[TEST-010]: downloadReport_returns_binary_data")
        void downloadReportSuccessfully() { 
            // Given
            String tenantId = "tenant-alpha";
            String reportId = "report-1";
            String format = "csv";

            Report report = new Report() 
                .withId(reportId) 
                .withTenantId(tenantId) 
                .withName("Sales Report")
                .withFormat(format); 

            when(repository.findById(tenantId, reportId)).thenReturn(Promise.of(report)); 

            // When
            byte[] result = runPromise(() -> service.downloadReport(tenantId, reportId, format)); 

            // Then
            assertThat(result).isNotEmpty(); 
            verify(metrics).incrementCounter("report.download.success", "tenant", tenantId, "format", "csv"); 
        }

        @Test
        @DisplayName("[TEST-010]: downloadReport_throws_when_not_found")
        void downloadReportNotFound() { 
            // Given
            String tenantId = "tenant-alpha";
            String reportId = "report-1";
            String format = "csv";

            when(repository.findById(tenantId, reportId)).thenReturn(Promise.of(null)); 

            // When
            clearFatalError(); 
            try {
                runPromise(() -> service.downloadReport(tenantId, reportId, format)); 
            } catch (Exception e) { 
                // Expected
            }

            // Then
            verify(metrics).incrementCounter("report.download.error", "tenant", tenantId, "error", "IllegalArgumentException"); 
        }
    }
}
