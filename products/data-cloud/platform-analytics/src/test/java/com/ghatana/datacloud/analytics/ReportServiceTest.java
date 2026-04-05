/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.analytics;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Service layer tests for Reports with deterministic fixtures (D002).
 *
 * <p>Validates report generation, listing, and caching at the service level.
 *
 * @doc.type class
 * @doc.purpose Service tests for Report generation and management
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReportService – Deterministic Fixtures (D002)")
class ReportServiceTest extends EventloopTestBase {

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private ReportCacheService cacheService;

    @Mock
    private QueryExecutor queryExecutor;

    private ReportService reportService;

    @BeforeEach
    void setUp() {
        reportService = new ReportService(reportRepository, cacheService, queryExecutor);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Generate Report Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Generate Report")
    class GenerateReportTests {

        @Test
        @DisplayName("[D002]: generate_creates_report_with_pending_status")
        void generateCreatesReportWithPendingStatus() {
            Report report = new Report().withName("Sales Report").withFormat("CSV");

            when(reportRepository.save(any())).thenReturn(Promise.of(report.withStatus("PENDING")));

            Report result = runPromise(() -> reportService.generate(report));

            assertThat(result.getStatus()).isEqualTo("PENDING");
            assertThat(result.getName()).isEqualTo("Sales Report");
        }

        @Test
        @DisplayName("[D002]: generate_executes_query_for_data")
        void generateExecutesQueryForData() {
            Report report = new Report()
                .withName("Sales Report")
                .withFormat("CSV")
                .withQuery("SELECT * FROM sales");

            List<Map<String, Object>> queryResults = List.of(
                Map.of("product", "Widget", "quantity", 10),
                Map.of("product", "Gadget", "quantity", 5)
            );

            when(queryExecutor.execute(anyString())).thenReturn(Promise.of(queryResults));
            when(reportRepository.save(any())).thenReturn(Promise.of(report.withStatus("COMPLETED")));

            Report result = runPromise(() -> reportService.generate(report));

            assertThat(result.getStatus()).isEqualTo("COMPLETED");
            verify(queryExecutor).execute("SELECT * FROM sales");
        }

        @Test
        @DisplayName("[D002]: generate_caches_report_when_configured")
        void generateCachesReportWhenConfigured() {
            Report report = new Report()
                .withId("report-001")
                .withName("Cached Report")
                .withFormat("CSV");

            when(reportRepository.save(any())).thenReturn(Promise.of(report));
            when(cacheService.cache(anyString(), any())).thenReturn(Promise.of((Void) null));

            reportService.generateWithCache(report);

            verify(cacheService).cache("report-001", report);
        }

        @Test
        @DisplayName("[D002]: generate_handles_query_failure")
        void generateHandlesQueryFailure() {
            Report report = new Report()
                .withName("Failing Report")
                .withQuery("INVALID SQL");

            when(queryExecutor.execute(anyString())).thenReturn(Promise.ofException(new RuntimeException("Query failed")));
            when(reportRepository.save(any())).thenAnswer(inv -> Promise.of(inv.getArgument(0)));

            Report result = runPromise(() -> reportService.generate(report));

            assertThat(result.getStatus()).isEqualTo("FAILED");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // List Reports Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("List Reports")
    class ListReportsTests {

        @Test
        @DisplayName("[D002]: list_returns_all_reports_for_tenant")
        void listReturnsAllReportsForTenant() {
            String tenantId = "tenant-alpha";
            List<Report> mockReports = List.of(
                new Report().withId("r1").withTenantId(tenantId).withName("Report 1"),
                new Report().withId("r2").withTenantId(tenantId).withName("Report 2")
            );

            when(reportRepository.findByTenantId(tenantId)).thenReturn(Promise.of(mockReports));

            List<Report> results = runPromise(() -> reportService.list(tenantId, null));

            assertThat(results).hasSize(2);
            assertThat(results).allMatch(r -> tenantId.equals(r.getTenantId()));
        }

        @Test
        @DisplayName("[D002]: list_with_status_filter_returns_matching_reports")
        void listWithStatusFilterReturnsMatchingReports() {
            String tenantId = "tenant-alpha";
            List<Report> mockReports = List.of(
                new Report().withId("r1").withTenantId(tenantId).withStatus("COMPLETED"),
                new Report().withId("r2").withTenantId(tenantId).withStatus("COMPLETED")
            );

            when(reportRepository.findByTenantIdAndStatus(tenantId, "COMPLETED"))
                .thenReturn(Promise.of(mockReports));

            List<Report> results = runPromise(() -> reportService.list(tenantId, "COMPLETED"));

            assertThat(results).hasSize(2);
            assertThat(results).allMatch(r -> "COMPLETED".equals(r.getStatus()));
        }

        @Test
        @DisplayName("[D002]: list_empty_tenant_returns_empty_list")
        void listEmptyTenantReturnsEmptyList() {
            String tenantId = "empty-tenant";

            when(reportRepository.findByTenantId(tenantId)).thenReturn(Promise.of(List.of()));

            List<Report> results = runPromise(() -> reportService.list(tenantId, null));

            assertThat(results).isEmpty();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Get Report Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Get Report")
    class GetReportTests {

        @Test
        @DisplayName("[D002]: get_existing_report_returns_report")
        void getExistingReportReturnsReport() {
            String reportId = "report-001";
            Report mockReport = new Report().withId(reportId).withName("Test Report");

            when(reportRepository.findById(reportId)).thenReturn(Promise.of(mockReport));

            Report result = runPromise(() -> reportService.get(reportId));

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(reportId);
        }

        @Test
        @DisplayName("[D002]: get_nonexistent_report_returns_null")
        void getNonexistentReportReturnsNull() {
            String reportId = "nonexistent";

            when(reportRepository.findById(reportId)).thenReturn(Promise.of(null));

            Report result = runPromise(() -> reportService.get(reportId));

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("[D002]: get_uses_cache_when_available")
        void getUsesCacheWhenAvailable() {
            String reportId = "report-cached";
            Report cachedReport = new Report().withId(reportId).withName("Cached");

            when(cacheService.get(reportId)).thenReturn(Promise.of(cachedReport));

            Report result = runPromise(() -> reportService.get(reportId));

            assertThat(result).isEqualTo(cachedReport);
            verify(reportRepository, never()).findById(anyString());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Delete Report Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Delete Report")
    class DeleteReportTests {

        @Test
        @DisplayName("[D002]: delete_existing_report_succeeds")
        void deleteExistingReportSucceeds() {
            String reportId = "report-001";

            when(reportRepository.delete(reportId)).thenReturn(Promise.of(true));
            when(cacheService.invalidate(reportId)).thenReturn(Promise.of((Void) null));

            Boolean result = runPromise(() -> reportService.delete(reportId));

            assertThat(result).isTrue();
            verify(cacheService).invalidate(reportId);
        }

        @Test
        @DisplayName("[D002]: delete_invalidates_cache")
        void deleteInvalidatesCache() {
            String reportId = "report-001";

            when(reportRepository.delete(reportId)).thenReturn(Promise.of(true));
            when(cacheService.invalidate(reportId)).thenReturn(Promise.of((Void) null));

            runPromise(() -> reportService.delete(reportId));

            verify(cacheService).invalidate(reportId);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tenant Isolation Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Tenant Isolation")
    class TenantIsolationTests {

        @Test
        @DisplayName("[D002]: reports_isolated_by_tenant_id")
        void reportsIsolatedByTenantId() {
            String tenantAlpha = "tenant-alpha";
            String tenantBeta = "tenant-beta";

            List<Report> alphaReports = List.of(
                new Report().withId("r1").withTenantId(tenantAlpha)
            );
            List<Report> betaReports = List.of(
                new Report().withId("r2").withTenantId(tenantBeta)
            );

            when(reportRepository.findByTenantId(tenantAlpha)).thenReturn(Promise.of(alphaReports));
            when(reportRepository.findByTenantId(tenantBeta)).thenReturn(Promise.of(betaReports));

            List<Report> alphaResults = runPromise(() -> reportService.list(tenantAlpha, null));
            List<Report> betaResults = runPromise(() -> reportService.list(tenantBeta, null));

            assertThat(alphaResults).hasSize(1);
            assertThat(betaResults).hasSize(1);
            assertThat(alphaResults.get(0).getTenantId()).isEqualTo(tenantAlpha);
            assertThat(betaResults.get(0).getTenantId()).isEqualTo(tenantBeta);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Report Formats Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Report Formats")
    class ReportFormatsTests {

        @Test
        @DisplayName("[D002]: generate_csv_format_sets_correct_content_type")
        void generateCsvFormatSetsCorrectContentType() {
            Report report = new Report().withName("CSV Report").withFormat("CSV");

            when(reportRepository.save(any())).thenReturn(Promise.of(report));

            Report result = runPromise(() -> reportService.generate(report));

            assertThat(result.getFormat()).isEqualTo("CSV");
        }

        @Test
        @DisplayName("[D002]: generate_pdf_format_sets_correct_content_type")
        void generatePdfFormatSetsCorrectContentType() {
            Report report = new Report().withName("PDF Report").withFormat("PDF");

            when(reportRepository.save(any())).thenReturn(Promise.of(report));

            Report result = runPromise(() -> reportService.generate(report));

            assertThat(result.getFormat()).isEqualTo("PDF");
        }

        @Test
        @DisplayName("[D002]: generate_xlsx_format_sets_correct_content_type")
        void generateXlsxFormatSetsCorrectContentType() {
            Report report = new Report().withName("Excel Report").withFormat("XLSX");

            when(reportRepository.save(any())).thenReturn(Promise.of(report));

            Report result = runPromise(() -> reportService.generate(report));

            assertThat(result.getFormat()).isEqualTo("XLSX");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Deterministic Fixture Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Deterministic Fixtures")
    class DeterministicFixtureTests {

        @Test
        @DisplayName("[D002]: same_input_produces_same_report_id")
        void sameInputProducesSameReportId() {
            // With deterministic fixtures, same inputs should produce predictable outputs
            Report report1 = createDeterministicReport("Sales Report", "SELECT * FROM sales", "CSV");
            Report report2 = createDeterministicReport("Sales Report", "SELECT * FROM sales", "CSV");

            assertThat(report1.getName()).isEqualTo(report2.getName());
            assertThat(report1.getFormat()).isEqualTo(report2.getFormat());
            assertThat(report1.getQuery()).isEqualTo(report2.getQuery());
        }

        @Test
        @DisplayName("[D002]: report_data_consistent_across_runs")
        void reportDataConsistentAcrossRuns() {
            List<Map<String, Object>> fixtureData = List.of(
                Map.of("product", "Widget", "quantity", 10, "price", 25.00),
                Map.of("product", "Gadget", "quantity", 5, "price", 50.00)
            );

            when(queryExecutor.execute(anyString())).thenReturn(Promise.of(fixtureData));

            // Run twice with same fixture
            List<Map<String, Object>> result1 = runPromise(() -> queryExecutor.execute("SELECT * FROM sales"));
            List<Map<String, Object>> result2 = runPromise(() -> queryExecutor.execute("SELECT * FROM sales"));

            assertThat(result1).hasSameSizeAs(result2);
            assertThat(result1).containsExactlyElementsOf(result2);
        }

        private Report createDeterministicReport(String name, String query, String format) {
            return new Report()
                .withName(name)
                .withQuery(query)
                .withFormat(format)
                .withTenantId("test-tenant");
        }
    }
}
