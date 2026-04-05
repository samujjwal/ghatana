/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import com.ghatana.datacloud.DataCloudClient;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Integration tests for Reports HTTP endpoints (Requirements D001, D004).
 *
 * <p>Tests report generation, listing, and retrieval with caching validation.
 *
 * @doc.type class
 * @doc.purpose HTTP integration tests for Reports endpoints
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@Timeout(value = 15, unit = TimeUnit.SECONDS)
@DisplayName("DataCloudHttpServer – Reports Endpoints (D001, D004)")
class DataCloudHttpServerReportsTest extends DataCloudHttpServerTestBase {

    private DataCloudClient mockClient;

    @BeforeEach
    void setUp() throws Exception {
        mockClient = org.mockito.Mockito.mock(DataCloudClient.class);
        port = findFreePort();
        startServer();
    }

    @Override
    protected void startServer() throws Exception {
        server = new DataCloudHttpServer(mockClient, port);
        server.start();
        waitForServerReady(5000);
    }

    @Nested
    @DisplayName("POST /api/v1/reports/generate (D001)")
    class GenerateReportTests {

        @Test
        @DisplayName("[D001]: generate_report_valid_request_returns_200")
        void generateReportValidRequestReturns200() throws Exception {
            Map<String, Object> mockReport = Map.of(
                "id", "report-001",
                "name", "Sales Report",
                "status", "PENDING",
                "format", "CSV"
            );

            lenient().when(mockClient.generateReport(any(), any()))
                .thenReturn(Promise.of(mockReport));

            var response = postJson("/api/v1/reports/generate", Map.of(
                "name", "Sales Report",
                "query", "SELECT * FROM sales",
                "format", "CSV"
            ));

            assertStatusCode(response, 200);
            Map<String, Object> body = parseJsonResponse(response);
            assertThat(body).containsKey("id");
            assertThat(body).containsKey("status");
        }

        @Test
        @DisplayName("[D001]: generate_report_missing_name_returns_400")
        void generateReportMissingNameReturns400() throws Exception {
            var response = postJson("/api/v1/reports/generate", Map.of(
                "query", "SELECT * FROM sales"
            ));

            assertStatusCode(response, 400);
        }

        @Test
        @DisplayName("[D001]: generate_report_invalid_format_returns_400")
        void generateReportInvalidFormatReturns400() throws Exception {
            var response = postJson("/api/v1/reports/generate", Map.of(
                "name", "Sales Report",
                "query", "SELECT * FROM sales",
                "format", "INVALID_FORMAT"
            ));

            assertStatusCode(response, 400);
        }

        @Test
        @DisplayName("[D001]: generate_report_with_cache_enabled_stores_in_cache")
        void generateReportWithCacheEnabledStoresInCache() throws Exception {
            Map<String, Object> mockReport = Map.of(
                "id", "report-cached-001",
                "name", "Cached Report",
                "status", "COMPLETED",
                "cached", true
            );

            lenient().when(mockClient.generateReport(any(), any()))
                .thenReturn(Promise.of(mockReport));

            var response = postJson("/api/v1/reports/generate", Map.of(
                "name", "Cached Report",
                "query", "SELECT * FROM sales",
                "format", "CSV",
                "cache", true
            ));

            assertStatusCode(response, 200);
            Map<String, Object> body = parseJsonResponse(response);
            assertThat(body).containsEntry("cached", true);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/reports (D004)")
    class ListReportsTests {

        @Test
        @DisplayName("[D004]: list_reports_returns_200_with_reports_array")
        void listReportsReturns200WithReportsArray() throws Exception {
            var reports = java.util.List.of(
                Map.of("id", "report-001", "name", "Report 1", "status", "COMPLETED"),
                Map.of("id", "report-002", "name", "Report 2", "status", "PENDING")
            );

            lenient().when(mockClient.listReports(any(), any()))
                .thenReturn(Promise.of(reports));

            var response = get("/api/v1/reports", withTenant("tenant-alpha"));

            assertStatusCode(response, 200);
            Map<String, Object> body = parseJsonResponse(response);
            assertThat(body).containsKey("reports");
            assertThat(body).containsKey("total");
        }

        @Test
        @DisplayName("[D004]: list_reports_with_status_filter_returns_filtered")
        void listReportsWithStatusFilterReturnsFiltered() throws Exception {
            var reports = java.util.List.of(
                Map.of("id", "report-001", "name", "Report 1", "status", "COMPLETED")
            );

            lenient().when(mockClient.listReports(any(), eq("COMPLETED")))
                .thenReturn(Promise.of(reports));

            var response = get("/api/v1/reports?status=COMPLETED", withTenant("tenant-alpha"));

            assertStatusCode(response, 200);
            Map<String, Object> body = parseJsonResponse(response);
            @SuppressWarnings("unchecked")
            java.util.List<Map<String, Object>> reportsList = (java.util.List<Map<String, Object>>) body.get("reports");
            assertThat(reportsList).hasSize(1);
        }

        @Test
        @DisplayName("[D004]: list_reports_empty_returns_empty_array")
        void listReportsEmptyReturnsEmptyArray() throws Exception {
            lenient().when(mockClient.listReports(any(), any()))
                .thenReturn(Promise.of(java.util.List.of()));

            var response = get("/api/v1/reports", withTenant("tenant-alpha"));

            assertStatusCode(response, 200);
            Map<String, Object> body = parseJsonResponse(response);
            assertThat(body).containsEntry("total", 0);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/reports/{id} (D004)")
    class GetReportTests {

        @Test
        @DisplayName("[D004]: get_existing_report_returns_200_with_report")
        void getExistingReportReturns200WithReport() throws Exception {
            Map<String, Object> mockReport = Map.of(
                "id", "report-001",
                "name", "Sales Report",
                "status", "COMPLETED",
                "format", "CSV"
            );

            lenient().when(mockClient.getReport(eq("tenant-alpha"), eq("report-001")))
                .thenReturn(Promise.of(mockReport));

            var response = get("/api/v1/reports/report-001", withTenant("tenant-alpha"));

            assertStatusCode(response, 200);
            Map<String, Object> body = parseJsonResponse(response);
            assertThat(body).containsEntry("id", "report-001");
        }

        @Test
        @DisplayName("[D004]: get_nonexistent_report_returns_404")
        void getNonexistentReportReturns404() throws Exception {
            lenient().when(mockClient.getReport(any(), any()))
                .thenReturn(Promise.of(null));

            var response = get("/api/v1/reports/nonexistent", withTenant("tenant-alpha"));

            assertStatusCode(response, 404);
        }

        @Test
        @DisplayName("[Tenant Isolation]: different_tenant_cannot_access_report")
        void differentTenantCannotAccessReport() throws Exception {
            Map<String, Object> mockReport = Map.of(
                "id", "report-001",
                "tenantId", "tenant-alpha",
                "name", "Sales Report",
                "status", "COMPLETED"
            );

            lenient().when(mockClient.getReport(eq("tenant-beta"), eq("report-001")))
                .thenReturn(Promise.of(null));

            var response = get("/api/v1/reports/report-001", withTenant("tenant-beta"));

            assertStatusCode(response, 404);
        }

        @Test
        @DisplayName("[Cache]: get_cached_report_returns_from_cache")
        void getCachedReportReturnsFromCache() throws Exception {
            Map<String, Object> cachedReport = Map.of(
                "id", "report-cached",
                "name", "Cached Report",
                "status", "COMPLETED",
                "cached", true,
                "cacheHit", true
            );

            lenient().when(mockClient.getReport(any(), eq("report-cached")))
                .thenReturn(Promise.of(cachedReport));

            var response = get("/api/v1/reports/report-cached", withTenant("tenant-alpha"));

            assertStatusCode(response, 200);
            Map<String, Object> body = parseJsonResponse(response);
            assertThat(body).containsEntry("cacheHit", true);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/reports/{id}/download")
    class DownloadReportTests {

        @Test
        @DisplayName("[Download]: download_completed_report_returns_file")
        void downloadCompletedReportReturnsFile() throws Exception {
            Map<String, Object> mockReport = Map.of(
                "id", "report-001",
                "name", "sales_report.csv",
                "status", "COMPLETED",
                "format", "CSV"
            );

            lenient().when(mockClient.getReport(any(), eq("report-001")))
                .thenReturn(Promise.of(mockReport));
            lenient().when(mockClient.downloadReport(any(), eq("report-001")))
                .thenReturn(Promise.of("product,quantity\nWidget,10\n".getBytes()));

            var response = get("/api/v1/reports/report-001/download", withTenant("tenant-alpha"));

            assertStatusCode(response, 200);
            assertThat(response.headers().firstValue("Content-Type")).hasValue("text/csv");
            assertThat(response.headers().firstValue("Content-Disposition"))
                .hasValueContaining("sales_report.csv");
        }

        @Test
        @DisplayName("[Download]: download_pending_report_returns_404")
        void downloadPendingReportReturns404() throws Exception {
            Map<String, Object> mockReport = Map.of(
                "id", "report-002",
                "status", "PENDING"
            );

            lenient().when(mockClient.getReport(any(), eq("report-002")))
                .thenReturn(Promise.of(mockReport));

            var response = get("/api/v1/reports/report-002/download", withTenant("tenant-alpha"));

            assertStatusCode(response, 404);
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/reports/{id}")
    class DeleteReportTests {

        @Test
        @DisplayName("[Delete]: delete_existing_report_returns_200")
        void deleteExistingReportReturns200() throws Exception {
            Map<String, Object> mockReport = Map.of(
                "id", "report-001",
                "tenantId", "tenant-alpha",
                "status", "COMPLETED"
            );

            lenient().when(mockClient.getReport(any(), eq("report-001")))
                .thenReturn(Promise.of(mockReport));
            lenient().when(mockClient.deleteReport(any(), eq("report-001")))
                .thenReturn(Promise.of(Map.of("deleted", true)));

            var response = delete("/api/v1/reports/report-001");

            assertStatusCode(response, 200);
            Map<String, Object> body = parseJsonResponse(response);
            assertThat(body).containsEntry("deleted", true);
        }

        @Test
        @DisplayName("[Delete]: delete_nonexistent_report_returns_404")
        void deleteNonexistentReportReturns404() throws Exception {
            lenient().when(mockClient.getReport(any(), any()))
                .thenReturn(Promise.of(null));

            var response = delete("/api/v1/reports/nonexistent");

            assertStatusCode(response, 404);
        }
    }
}
