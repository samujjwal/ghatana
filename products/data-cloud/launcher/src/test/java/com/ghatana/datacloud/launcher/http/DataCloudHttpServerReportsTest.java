/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.analytics.report.ReportDefinition;
import com.ghatana.datacloud.analytics.report.ReportFormat;
import com.ghatana.datacloud.analytics.report.ReportResult;
import com.ghatana.datacloud.analytics.report.ReportService;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Integration tests for current report HTTP endpoints backed by ReportService.
 *
 * @doc.type class
 * @doc.purpose HTTP integration tests for current reports endpoints
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@Timeout(value = 15, unit = TimeUnit.SECONDS)
@DisplayName("DataCloudHttpServer – Reports Endpoints")
class DataCloudHttpServerReportsTest extends DataCloudHttpServerTestBase {

    @Mock
    private DataCloudClient mockClient;

    @Mock
    private ReportService reportService;

    @BeforeEach
    void setUp() throws Exception {
        port = findFreePort();
        startServer();
    }

    @Override
    protected void startServer() throws Exception {
        server = new DataCloudHttpServer(mockClient, port)
            .withReportService(reportService);
        server.start();
        waitForServerReady(5000);
    }

    @Nested
    @DisplayName("POST /api/v1/reports")
    class CreateReportTests {

        @Test
        @DisplayName("creates a report and returns generated metadata")
        void createReportReturnsGeneratedMetadata() throws Exception {
            ReportResult result = ReportResult.builder()
                .reportId("report-001")
                .reportName("Sales Report")
                .format(ReportFormat.JSON)
                .rows(java.util.List.of(Map.of("region", "North", "total", 10)))
                .rowCount(1)
                .contentType("application/json")
                .generatedAt(Instant.parse("2026-04-06T00:00:00Z"))
                .executionTime(Duration.ofMillis(25))
                .build();

            when(reportService.generate(anyString(), any(ReportDefinition.class)))
                .thenReturn(Promise.of(result));

            var response = postJson("/api/v1/reports", Map.of(
                "name", "Sales Report",
                "type", "QUERY",
                "query", "SELECT * FROM sales",
                "format", "JSON"
            ), withTenant("tenant-alpha"));

            assertStatusCode(response, 200);
            Map<String, Object> body = parseJsonResponse(response);
            assertThat(body).containsEntry("reportId", "report-001");
            assertThat(body).containsEntry("reportName", "Sales Report");
            assertThat(body).containsEntry("rowCount", 1);
        }

        @Test
        @DisplayName("invalid request body returns 400")
        void invalidRequestBodyReturns400() throws Exception {
            var response = postJson("/api/v1/reports", Map.of(
                "type", "QUERY",
                "query", "SELECT * FROM sales"
            ), withTenant("tenant-alpha"));

            assertStatusCode(response, 400);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/reports")
    class ListReportsTests {

        @Test
        @DisplayName("lists cached reports")
        void listCachedReportsReturnsSnapshot() throws Exception {
            when(reportService.listCachedReports()).thenReturn(Map.of(
                "report-001", "Sales Report",
                "report-002", "Inventory Report"
            ));

            var response = get("/api/v1/reports", withTenant("tenant-alpha"));

            assertStatusCode(response, 200);
            Map<String, Object> body = parseJsonResponse(response);
            assertThat(body).containsKey("reports");
            assertThat(body).containsEntry("count", 2);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/reports/{reportId}")
    class GetReportTests {

        @Test
        @DisplayName("returns cached report details when present")
        void getCachedReportReturnsDetails() throws Exception {
            ReportResult cached = ReportResult.builder()
                .reportId("report-001")
                .reportName("Sales Report")
                .format(ReportFormat.CSV)
                .rowCount(2)
                .formattedBody("region,total\nNorth,10\nSouth,12\n")
                .contentType("text/csv")
                .generatedAt(Instant.parse("2026-04-06T00:00:00Z"))
                .executionTime(Duration.ofMillis(40))
                .build();

            when(reportService.getResult("report-001")).thenReturn(cached);

            var response = get("/api/v1/reports/report-001", withTenant("tenant-alpha"));

            assertStatusCode(response, 200);
            Map<String, Object> body = parseJsonResponse(response);
            assertThat(body).containsEntry("reportId", "report-001");
            assertThat(body).containsEntry("format", "CSV");
            assertThat(body).containsEntry("body", "region,total\nNorth,10\nSouth,12\n");
        }

        @Test
        @DisplayName("returns 404 when report is not cached")
        void getMissingReportReturns404() throws Exception {
            when(reportService.getResult("missing-report")).thenReturn(null);

            var response = get("/api/v1/reports/missing-report", withTenant("tenant-alpha"));

            assertStatusCode(response, 404);
        }
    }
}
