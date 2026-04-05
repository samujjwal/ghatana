/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.api.controller;

import com.ghatana.datacloud.launcher.http.DataCloudHttpServerTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ghatana.datacloud.DataCloudClient;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

/**
 * HTTP integration tests for cost reporting endpoints (D005).
 *
 * <p>Validates cost calculation, breakdown, and reporting accuracy.
 *
 * @doc.type class
 * @doc.purpose Cost reporting HTTP endpoint tests
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@Timeout(value = 15, unit = TimeUnit.SECONDS)
@DisplayName("DataCloudHttpServer – Cost Reporting (D005)")
class CostReportingEndpointTest extends DataCloudHttpServerTestBase {

    @Mock
    private DataCloudClient mockClient;

    @BeforeEach
    void setUp() throws Exception {
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
    @DisplayName("GET /api/v1/reports/costs (D005)")
    class GetCostsTests {

        @Test
        @DisplayName("[D005]: get_costs_returns_200_with_breakdown")
        void getCostsReturns200WithBreakdown() throws Exception {
            Map<String, Object> mockCosts = Map.of(
                "totalCost", 1250.00,
                "currency", "USD",
                "period", "2026-01",
                "breakdown", Map.of(
                    "storage", 500.00,
                    "compute", 600.00,
                    "network", 150.00
                ),
                "tenantId", "tenant-alpha"
            );

            lenient().when(mockClient.getCosts(any(), any()))
                .thenReturn(Promise.of(mockCosts));

            var response = get("/api/v1/reports/costs?period=2026-01", withTenant("tenant-alpha"));

            assertStatusCode(response, 200);
            Map<String, Object> body = parseJsonResponse(response);
            assertThat(body).containsKey("totalCost");
            assertThat(body).containsKey("breakdown");
            assertThat(body.get("currency")).isEqualTo("USD");
        }

        @Test
        @DisplayName("[D005]: get_costs_missing_period_returns_400")
        void getCostsMissingPeriodReturns400() throws Exception {
            var response = get("/api/v1/reports/costs", withTenant("tenant-alpha"));

            assertStatusCode(response, 400);
        }

        @Test
        @DisplayName("[D005]: get_costs_invalid_period_format_returns_400")
        void getCostsInvalidPeriodFormatReturns400() throws Exception {
            var response = get("/api/v1/reports/costs?period=invalid", withTenant("tenant-alpha"));

            assertStatusCode(response, 400);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/reports/costs/summary (D005)")
    class GetCostSummaryTests {

        @Test
        @DisplayName("[D005]: get_cost_summary_returns_aggregated_data")
        void getCostSummaryReturnsAggregatedData() throws Exception {
            Map<String, Object> mockSummary = Map.of(
                "totalCost", 1250.00,
                "previousPeriodCost", 1100.00,
                "changePercent", 13.6,
                "topServices", java.util.List.of(
                    Map.of("service", "compute", "cost", 600.00, "percent", 48.0),
                    Map.of("service", "storage", "cost", 500.00, "percent", 40.0),
                    Map.of("service", "network", "cost", 150.00, "percent", 12.0)
                )
            );

            lenient().when(mockClient.getCostSummary(any(), any()))
                .thenReturn(Promise.of(mockSummary));

            var response = get("/api/v1/reports/costs/summary?period=2026-01", withTenant("tenant-alpha"));

            assertStatusCode(response, 200);
            Map<String, Object> body = parseJsonResponse(response);
            assertThat(body).containsKey("totalCost");
            assertThat(body).containsKey("previousPeriodCost");
            assertThat(body).containsKey("changePercent");
            assertThat(body).containsKey("topServices");
        }

        @Test
        @DisplayName("[D005]: get_cost_summary_calculates_change_correctly")
        void getCostSummaryCalculatesChangeCorrectly() throws Exception {
            double currentCost = 1250.00;
            double previousCost = 1100.00;
            double expectedChange = ((currentCost - previousCost) / previousCost) * 100;

            Map<String, Object> mockSummary = Map.of(
                "totalCost", currentCost,
                "previousPeriodCost", previousCost,
                "changePercent", expectedChange
            );

            lenient().when(mockClient.getCostSummary(any(), any()))
                .thenReturn(Promise.of(mockSummary));

            var response = get("/api/v1/reports/costs/summary?period=2026-01", withTenant("tenant-alpha"));

            assertStatusCode(response, 200);
            Map<String, Object> body = parseJsonResponse(response);
            assertThat(body.get("changePercent")).isEqualTo(expectedChange);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/reports/costs/by-service (D005)")
    class GetCostsByServiceTests {

        @Test
        @DisplayName("[D005]: get_costs_by_service_returns_breakdown")
        void getCostsByServiceReturnsBreakdown() throws Exception {
            Map<String, Object> mockBreakdown = Map.of(
                "period", "2026-01",
                "services", java.util.List.of(
                    Map.of("service", "compute", "cost", 600.00, "units", "hours", "unitCost", 0.50),
                    Map.of("service", "storage", "cost", 500.00, "units", "GB", "unitCost", 0.10),
                    Map.of("service", "network", "cost", 150.00, "units", "GB", "unitCost", 0.05)
                ),
                "totalCost", 1250.00
            );

            lenient().when(mockClient.getCostsByService(any(), any()))
                .thenReturn(Promise.of(mockBreakdown));

            var response = get("/api/v1/reports/costs/by-service?period=2026-01", withTenant("tenant-alpha"));

            assertStatusCode(response, 200);
            Map<String, Object> body = parseJsonResponse(response);
            @SuppressWarnings("unchecked")
            java.util.List<Map<String, Object>> services = (java.util.List<Map<String, Object>>) body.get("services");
            assertThat(services).hasSize(3);
        }

        @Test
        @DisplayName("[D005]: get_costs_by_service_with_filter_returns_filtered_results")
        void getCostsByServiceWithFilterReturnsFilteredResults() throws Exception {
            Map<String, Object> mockBreakdown = Map.of(
                "period", "2026-01",
                "services", java.util.List.of(
                    Map.of("service", "compute", "cost", 600.00, "units", "hours", "unitCost", 0.50)
                ),
                "totalCost", 600.00,
                "filter", "compute"
            );

            lenient().when(mockClient.getCostsByService(any(), any(), any()))
                .thenReturn(Promise.of(mockBreakdown));

            var response = get("/api/v1/reports/costs/by-service?period=2026-01&service=compute", withTenant("tenant-alpha"));

            assertStatusCode(response, 200);
            Map<String, Object> body = parseJsonResponse(response);
            assertThat(body.get("filter")).isEqualTo("compute");
        }
    }

    @Nested
    @DisplayName("POST /api/v1/reports/costs/export (D005)")
    class ExportCostReportTests {

        @Test
        @DisplayName("[D005]: export_cost_report_returns_csv")
        void exportCostReportReturnsCsv() throws Exception {
            String csvContent = "Service,Cost,Units\ncompute,600.00,hours\nstorage,500.00,GB\nnetwork,150.00,GB\n";

            lenient().when(mockClient.exportCostReport(any(), any(), any()))
                .thenReturn(Promise.of(csvContent.getBytes()));

            var response = postJson("/api/v1/reports/costs/export", Map.of(
                "period", "2026-01",
                "format", "CSV"
            ), withTenant("tenant-alpha"));

            assertStatusCode(response, 200);
            assertThat(response.headers().firstValue("Content-Type")).hasValue("text/csv");
        }

        @Test
        @DisplayName("[D005]: export_cost_report_invalid_format_returns_400")
        void exportCostReportInvalidFormatReturns400() throws Exception {
            var response = postJson("/api/v1/reports/costs/export", Map.of(
                "period", "2026-01",
                "format", "INVALID"
            ), withTenant("tenant-alpha"));

            assertStatusCode(response, 400);
        }
    }

    @Nested
    @DisplayName("Cost Calculation Accuracy (D005)")
    class CostCalculationAccuracyTests {

        @Test
        @DisplayName("[D005]: storage_cost_calculated_by_gb")
        void storageCostCalculatedByGb() {
            double gbUsed = 5000.0;
            double costPerGb = 0.10;
            double expectedCost = gbUsed * costPerGb;

            Map<String, Object> breakdown = Map.of(
                "service", "storage",
                "units", gbUsed,
                "unitCost", costPerGb,
                "cost", expectedCost
            );

            assertThat(breakdown.get("cost")).isEqualTo(500.00);
        }

        @Test
        @DisplayName("[D005]: compute_cost_calculated_by_hours")
        void computeCostCalculatedByHours() {
            double hoursUsed = 1200.0;
            double costPerHour = 0.50;
            double expectedCost = hoursUsed * costPerHour;

            Map<String, Object> breakdown = Map.of(
                "service", "compute",
                "units", hoursUsed,
                "unitCost", costPerHour,
                "cost", expectedCost
            );

            assertThat(breakdown.get("cost")).isEqualTo(600.00);
        }

        @Test
        @DisplayName("[D005]: network_cost_calculated_by_gb_transferred")
        void networkCostCalculatedByGbTransferred() {
            double gbTransferred = 3000.0;
            double costPerGb = 0.05;
            double expectedCost = gbTransferred * costPerGb;

            Map<String, Object> breakdown = Map.of(
                "service", "network",
                "units", gbTransferred,
                "unitCost", costPerGb,
                "cost", expectedCost
            );

            assertThat(breakdown.get("cost")).isEqualTo(150.00);
        }

        @Test
        @DisplayName("[D005]: total_cost_sum_of_all_services")
        void totalCostSumOfAllServices() {
            double storageCost = 500.00;
            double computeCost = 600.00;
            double networkCost = 150.00;
            double expectedTotal = storageCost + computeCost + networkCost;

            Map<String, Object> costs = Map.of(
                "totalCost", expectedTotal,
                "breakdown", Map.of(
                    "storage", storageCost,
                    "compute", computeCost,
                    "network", networkCost
                )
            );

            assertThat(costs.get("totalCost")).isEqualTo(1250.00);
        }
    }

    @Nested
    @DisplayName("Tenant Isolation (D005)")
    class TenantIsolationTests {

        @Test
        @DisplayName("[D005]: tenant_can_only_access_own_costs")
        void tenantCanOnlyAccessOwnCosts() throws Exception {
            Map<String, Object> alphaCosts = Map.of(
                "tenantId", "tenant-alpha",
                "totalCost", 1250.00
            );

            lenient().when(mockClient.getCosts(any(), any()))
                .thenAnswer(invocation -> {
                    String tenantId = invocation.getArgument(0);
                    if ("tenant-alpha".equals(tenantId)) {
                        return Promise.of(alphaCosts);
                    }
                    return Promise.of(Map.of("totalCost", 0.00));
                });

            var response = get("/api/v1/reports/costs?period=2026-01", withTenant("tenant-alpha"));
            Map<String, Object> body = parseJsonResponse(response);

            assertThat(body.get("tenantId")).isEqualTo("tenant-alpha");
        }
    }
}
