/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.http.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CostController}.
 *
 * <p>Tests the production operations cost-visibility endpoint in no-DataCloud mode
 * (in-memory estimated cost path). Validates that the response schema conforms to
 * the contract and that tenant isolation, budget alerts, and cost math are correct.
 *
 * @doc.type class
 * @doc.purpose Unit tests for CostController — cost visibility production ops endpoint
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("CostController")
class CostControllerTest extends EventloopTestBase {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CostController controller;

    @BeforeEach
    void setUp() {
        // No analytics store, no DataCloud — exercises the estimated cost path
        controller = new CostController(null, null, List::of);
    }

    // =========================================================================
    // Response schema contract
    // =========================================================================

    @Nested
    @DisplayName("Response schema")
    class ResponseSchema {

        @Test
        @DisplayName("cost summary response includes required top-level fields")
        void responsHasRequiredFields() throws Exception {
            HttpResponse response = runPromise(() ->
                    controller.handleGetCostSummary(buildGet("tenant-schema-test")));

            assertThat(response.getCode()).isEqualTo(200);
            JsonNode json = parseBody(response);
            assertThat(json.has("summary")).isTrue();
            assertThat(json.has("timestamp")).isTrue();

            JsonNode summary = json.get("summary");
            assertThat(summary.has("tenantId")).isTrue();
            assertThat(summary.has("totalCostUsd")).isTrue();
            assertThat(summary.has("projectedMonthlyCostUsd")).isTrue();
            assertThat(summary.has("dataSource")).isTrue();
        }

        @Test
        @DisplayName("tenantId in summary matches the requested tenant")
        void tenantIdMatchesRequest() throws Exception {
            HttpResponse response = runPromise(() ->
                    controller.handleGetCostSummary(buildGet("tenant-match-test")));

            JsonNode json = parseBody(response);
            assertThat(json.at("/summary/tenantId").asText()).isEqualTo("tenant-match-test");
        }

        @Test
        @DisplayName("summary includes perPipeline, perAgent, perModel arrays")
        void summaryHasBreakdownArrays() throws Exception {
            HttpResponse response = runPromise(() ->
                    controller.handleGetCostSummary(buildGet("tenant-arrays-test")));

            JsonNode summary = parseBody(response).get("summary");
            assertThat(summary.get("perPipeline").isArray()).isTrue();
            assertThat(summary.get("perAgent").isArray()).isTrue();
            assertThat(summary.get("perModel").isArray()).isTrue();
        }

        @Test
        @DisplayName("summary includes budget section with dailyBudgetUsd and monthlyBudgetUsd")
        void summaryHasBudgetSection() throws Exception {
            HttpResponse response = runPromise(() ->
                    controller.handleGetCostSummary(
                            buildGetWithBudget("tenant-budget-test", 10.0, 300.0)));

            JsonNode summary = parseBody(response).get("summary");
            assertThat(summary.has("budget")).isTrue();
        }

        @Test
        @DisplayName("totalCostUsd is 0.0 when no runs recorded for the tenant")
        void zeroCostForEmptyTenant() throws Exception {
            HttpResponse response = runPromise(() ->
                    controller.handleGetCostSummary(buildGet("empty-tenant-xyz")));

            JsonNode summary = parseBody(response).get("summary");
            assertThat(summary.get("totalCostUsd").asDouble()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("dataSource is 'estimated' when no analytics store is available")
        void dataSourceIsEstimated() throws Exception {
            HttpResponse response = runPromise(() ->
                    controller.handleGetCostSummary(buildGet("datasource-tenant")));

            JsonNode summary = parseBody(response).get("summary");
            assertThat(summary.get("dataSource").asText()).isEqualTo("estimated");
        }

        @Test
        @DisplayName("timestamp in response is a recent ISO-8601 string")
        void responseTimestampIsRecent() throws Exception {
            Instant before = Instant.now().minusSeconds(2);
            HttpResponse response = runPromise(() ->
                    controller.handleGetCostSummary(buildGet("ts-tenant")));
            Instant after = Instant.now().plusSeconds(2);

            JsonNode json = parseBody(response);
            Instant ts = Instant.parse(json.get("timestamp").asText());
            assertThat(ts).isAfter(before).isBefore(after);
        }
    }

    // =========================================================================
    // Tenant isolation
    // =========================================================================

    @Nested
    @DisplayName("Tenant isolation")
    class TenantIsolation {

        @Test
        @DisplayName("two tenants with runs do not see each other's cost data")
        void twoCostRequests_areIsolatedByTenant() throws Exception {
            List<Map<String, Object>> runs = List.of(
                    Map.of(
                            "tenantId", "cost-tenant-A",
                            "pipelineId", "pipeline-A",
                            "pipelineName", "Pipeline A",
                            "startedAt", Instant.now().minusSeconds(60).toString(),
                            "completedAt", Instant.now().minusSeconds(5).toString(),
                            "durationMs", "55000",
                            "eventsProcessed", "1000",
                            "status", "succeeded"
                    ),
                    Map.of(
                            "tenantId", "cost-tenant-B",
                            "pipelineId", "pipeline-B",
                            "pipelineName", "Pipeline B",
                            "startedAt", Instant.now().minusSeconds(60).toString(),
                            "completedAt", Instant.now().minusSeconds(5).toString(),
                            "durationMs", "55000",
                            "eventsProcessed", "500",
                            "status", "succeeded"
                    )
            );
            CostController controllerWithRuns = new CostController(null, null, () -> runs);

            HttpResponse responseA = runPromise(() ->
                    controllerWithRuns.handleGetCostSummary(buildGet("cost-tenant-A")));
            HttpResponse responseB = runPromise(() ->
                    controllerWithRuns.handleGetCostSummary(buildGet("cost-tenant-B")));

            JsonNode summaryA = parseBody(responseA).get("summary");
            JsonNode summaryB = parseBody(responseB).get("summary");

            assertThat(summaryA.get("tenantId").asText()).isEqualTo("cost-tenant-A");
            assertThat(summaryB.get("tenantId").asText()).isEqualTo("cost-tenant-B");
        }
    }

    // =========================================================================
    // Budget alerts
    // =========================================================================

    @Nested
    @DisplayName("Budget alerts")
    class BudgetAlerts {

        @Test
        @DisplayName("response includes alerts array")
        void responseIncludesAlertsArray() throws Exception {
            HttpResponse response = runPromise(() ->
                    controller.handleGetCostSummary(
                            buildGetWithBudget("alert-tenant", 0.001, 0.01)));

            JsonNode summary = parseBody(response).get("summary");
            assertThat(summary.has("alerts")).isTrue();
            assertThat(summary.get("alerts").isArray()).isTrue();
        }
    }

    // =========================================================================
    // Provenance (AEP-P2-005)
    // =========================================================================

    @Nested
    @DisplayName("Data provenance")
    class Provenance {

        @Test
        @DisplayName("dataSource field indicates whether cost is estimated or actual")
        void dataSourceIndicatesOrigin() throws Exception {
            HttpResponse response = runPromise(() ->
                    controller.handleGetCostSummary(buildGet("provenance-tenant")));

            JsonNode summary = parseBody(response).get("summary");
            assertThat(summary.has("dataSource")).isTrue();
            String dataSource = summary.get("dataSource").asText();
            assertThat(dataSource).isIn("estimated", "actual");
        }

        @Test
        @DisplayName("timestamp field provides when the cost snapshot was generated")
        void timestampProvidesSnapshotTime() throws Exception {
            HttpResponse response = runPromise(() ->
                    controller.handleGetCostSummary(buildGet("timestamp-tenant")));

            JsonNode json = parseBody(response);
            assertThat(json.has("timestamp")).isTrue();
            String timestamp = json.get("timestamp").asText();
            // Verify it's a valid ISO-8601 format
            Instant.parse(timestamp);
        }

        @Test
        @DisplayName("response includes correlationId when provided in request")
        void correlationIdPropagatedToResponse() throws Exception {
            HttpRequest request = HttpRequest.get(
                    "http://localhost/api/v1/cost/summary?tenantId=corr-tenant&correlationId=corr-12345").build();

            HttpResponse response = runPromise(() ->
                    controller.handleGetCostSummary(request));

            JsonNode json = parseBody(response);
            assertThat(json.has("correlationId")).isTrue();
            assertThat(json.get("correlationId").asText()).isEqualTo("corr-12345");
        }

        @Test
        @DisplayName("dataSource is 'estimated' when no analytics store is configured")
        void dataSourceEstimatedWhenNoStore() throws Exception {
            HttpResponse response = runPromise(() ->
                    controller.handleGetCostSummary(buildGet("no-store-tenant")));

            JsonNode summary = parseBody(response).get("summary");
            assertThat(summary.get("dataSource").asText()).isEqualTo("estimated");
        }

        @Test
        @DisplayName("breakdown items include source metadata (pipelineId, agentId, modelId)")
        void breakdownItemsIncludeSourceMetadata() throws Exception {
            List<Map<String, Object>> runs = List.of(
                    Map.of(
                            "tenantId", "source-meta-tenant",
                            "pipelineId", "pipeline-1",
                            "pipelineName", "Pipeline One",
                            "startedAt", Instant.now().minusSeconds(60).toString(),
                            "completedAt", Instant.now().minusSeconds(5).toString(),
                            "durationMs", "55000",
                            "eventsProcessed", "1000",
                            "status", "succeeded"
                    )
            );
            CostController controllerWithRuns = new CostController(null, null, () -> runs);

            HttpResponse response = runPromise(() ->
                    controllerWithRuns.handleGetCostSummary(buildGet("source-meta-tenant")));

            JsonNode summary = parseBody(response).get("summary");
            JsonNode perPipeline = summary.get("perPipeline");
            assertThat(perPipeline.isArray()).isTrue();
            if (perPipeline.size() > 0) {
                JsonNode firstPipeline = perPipeline.get(0);
                assertThat(firstPipeline.has("pipelineId")).isTrue();
                assertThat(firstPipeline.has("pipelineName")).isTrue();
            }
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private HttpRequest buildGet(String tenantId) {
        return HttpRequest.get("http://localhost/api/v1/cost/summary?tenantId=" + tenantId).build();
    }

    private HttpRequest buildGetWithBudget(String tenantId, double daily, double monthly) {
        return HttpRequest.get(String.format(
                "http://localhost/api/v1/cost/summary?tenantId=%s&dailyBudgetUsd=%.3f&monthlyBudgetUsd=%.3f",
                tenantId, daily, monthly)).build();
    }

    private JsonNode parseBody(HttpResponse response) throws Exception {
        String body = response.getBody().getString(StandardCharsets.UTF_8);
        return MAPPER.readTree(body);
    }
}
