package com.ghatana.aep.server.http.controllers;

import com.ghatana.aep.eventcloud.store.EventCloudAgentStore;
import com.ghatana.aep.server.analytics.DataCloudAnalyticsStore;
import com.ghatana.aep.server.http.HttpHelper;
import com.ghatana.datacloud.DataCloudClient;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * @doc.type class
 * @doc.purpose Cost visibility endpoint for pipeline and agent operating spend
 * @doc.layer product
 * @doc.pattern Controller
 */
public final class CostController {

    private static final double BASE_RUN_COST_USD = 0.0002;
    private static final double COST_PER_SECOND_USD = 0.0020;
    private static final double COST_PER_EVENT_USD = 0.00001;
    private static final double ERROR_SURCHARGE_USD = 0.0010;

    @Nullable
    private final DataCloudAnalyticsStore analyticsStore;
    @Nullable
    private final DataCloudClient dataCloudClient;
    private final Supplier<List<Map<String, Object>>> recentRunsSupplier;

    public CostController(
            @Nullable DataCloudAnalyticsStore analyticsStore,
            @Nullable DataCloudClient dataCloudClient,
            Supplier<List<Map<String, Object>>> recentRunsSupplier) {
        this.analyticsStore = analyticsStore;
        this.dataCloudClient = dataCloudClient;
        this.recentRunsSupplier = recentRunsSupplier;
    }

    public Promise<HttpResponse> handleGetCostSummary(HttpRequest request) {
        String tenantId = HttpHelper.resolveTenantId(request);
        Instant to = parseInstant(request.getQueryParameter("to"), Instant.now());
        Instant from = parseInstant(request.getQueryParameter("from"), to.minus(Duration.ofHours(24)));
        double dailyBudgetUsd = parseDouble(request.getQueryParameter("dailyBudgetUsd"), 25.0);
        double monthlyBudgetUsd = parseDouble(request.getQueryParameter("monthlyBudgetUsd"), 750.0);
        String correlationId = request.getQueryParameter("correlationId");

        return summarizeCost(tenantId, from, to, dailyBudgetUsd, monthlyBudgetUsd)
                .map(summary -> {
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("summary", summary);
                    response.put("estimated", "estimated".equals(summary.dataSource()));
                    response.put("timestamp", Instant.now().toString());
                    if (correlationId != null) {
                        response.put("correlationId", correlationId);
                    }
                    return HttpHelper.jsonResponse(response);
                });
    }

    private Promise<CostSummary> summarizeCost(
            String tenantId,
            Instant from,
            Instant to,
            double dailyBudgetUsd,
            double monthlyBudgetUsd) {
        if (analyticsStore == null) {
            return summarizeEstimatedCost(tenantId, from, to, dailyBudgetUsd, monthlyBudgetUsd);
        }

        return analyticsStore.queryMetrics(tenantId, null, null, from, to, 2_000)
                .then(metrics -> {
                    CostSummary metricsSummary = summarizeFromMetrics(tenantId, from, to, metrics, dailyBudgetUsd, monthlyBudgetUsd);
                    if (metricsSummary.totalCostUsd() > 0.0
                            || !metricsSummary.perPipeline().isEmpty()
                            || !metricsSummary.perAgent().isEmpty()) {
                        return Promise.of(metricsSummary);
                    }
                    return summarizeEstimatedCost(tenantId, from, to, dailyBudgetUsd, monthlyBudgetUsd);
                });
    }

    private Promise<CostSummary> summarizeEstimatedCost(
            String tenantId,
            Instant from,
            Instant to,
            double dailyBudgetUsd,
            double monthlyBudgetUsd) {
        List<Map<String, Object>> filteredRuns = recentRunsSupplier.get().stream()
                .filter(run -> tenantId.equals(stringValue(run.get("tenantId"), "default")))
                .filter(run -> withinWindow(run, from, to))
                .toList();

        Map<String, MutableBreakdown> pipelineCosts = new LinkedHashMap<>();
        Map<String, MutableBreakdown> modelCosts = new LinkedHashMap<>();
        double totalCostUsd = 0.0;
        int totalRuns = 0;

        for (Map<String, Object> run : filteredRuns) {
            double runCostUsd = estimateRunCost(run);
            totalCostUsd += runCostUsd;
            totalRuns += 1;
            String pipelineId = stringValue(run.get("pipelineId"), "event");
            String pipelineName = stringValue(run.get("pipelineName"), pipelineId);
            pipelineCosts.computeIfAbsent(pipelineId, ignored -> new MutableBreakdown(pipelineId, pipelineName))
                    .accumulate(runCostUsd, parseInstant(stringValue(run.get("startedAt"), Instant.now().toString()), Instant.now()));
            String modelId = stringValue(run.get("model"), "").trim();
            if (!modelId.isEmpty()) {
                modelCosts.computeIfAbsent(modelId, ignored -> new MutableBreakdown(modelId, modelId))
                        .accumulate(runCostUsd, parseInstant(stringValue(run.get("startedAt"), Instant.now().toString()), Instant.now()));
            }
        }

        double projectedMonthlyCostUsd = projectMonthly(totalCostUsd, from, to);
        List<CostBreakdown> perPipeline = finalizeBreakdown(pipelineCosts, totalCostUsd);
        List<CostBreakdown> perModel = finalizeBreakdown(modelCosts, totalCostUsd);
        final double finalTotalCostUsd = totalCostUsd;
        final int finalTotalRuns = totalRuns;
        final double finalProjectedMonthlyCostUsd = projectedMonthlyCostUsd;
        final List<CostBreakdown> finalPerModel = perModel;

        return loadRegisteredAgents(tenantId).map(agents -> {
            Map<String, MutableBreakdown> agentCosts = new LinkedHashMap<>();
            if (!agents.isEmpty() && finalTotalCostUsd > 0.0) {
                double perAgentCostUsd = finalTotalCostUsd / agents.size();
                for (AgentRecord agent : agents) {
                    agentCosts.computeIfAbsent(agent.id(), ignored -> new MutableBreakdown(agent.id(), agent.name()))
                            .accumulate(perAgentCostUsd, null);
                }
            }

            List<CostBreakdown> perAgent = finalizeBreakdown(agentCosts, finalTotalCostUsd);
            List<CostAlert> alerts = buildAlerts(
                    finalTotalCostUsd,
                    finalProjectedMonthlyCostUsd,
                    dailyBudgetUsd,
                    monthlyBudgetUsd,
                    perPipeline,
                    perAgent,
                    true);

            return new CostSummary(
                    tenantId,
                    from,
                    to,
                    round(finalTotalCostUsd),
                    round(finalProjectedMonthlyCostUsd),
                    finalTotalRuns == 0 ? 0.0 : round(finalTotalCostUsd / finalTotalRuns),
                    perPipeline,
                    perAgent,
                    finalPerModel,
                    buildBudgetSummary(finalTotalCostUsd, finalProjectedMonthlyCostUsd, dailyBudgetUsd, monthlyBudgetUsd),
                    alerts,
                    "estimated",
                    agents.isEmpty() ? "no-agent-registry" : "equal-share-fallback");
        });
    }

    private CostSummary summarizeFromMetrics(
            String tenantId,
            Instant from,
            Instant to,
            List<DataCloudAnalyticsStore.MetricDataPoint> metrics,
            double dailyBudgetUsd,
            double monthlyBudgetUsd) {
        Map<String, MutableBreakdown> pipelineCosts = new LinkedHashMap<>();
        Map<String, MutableBreakdown> agentCosts = new LinkedHashMap<>();
        Map<String, MutableBreakdown> modelCosts = new LinkedHashMap<>();
        double totalCostUsd = 0.0;
        int totalRuns = 0;

        for (DataCloudAnalyticsStore.MetricDataPoint metric : metrics) {
            if (!isCostMetric(metric)) {
                continue;
            }
            switch (metric.metricName()) {
                case "cost.pipeline.usd" -> {
                    String id = !metric.entityId().isBlank() ? metric.entityId() : tagValue(metric.tags(), "pipeline");
                    String name = defaultString(tagValue(metric.tags(), "name"), id);
                    pipelineCosts.computeIfAbsent(id, ignored -> new MutableBreakdown(id, name))
                            .accumulate(metric.value(), metric.recordedAt());
                    totalCostUsd += metric.value();
                    totalRuns += 1;
                }
                case "cost.agent.usd" -> {
                    String id = !metric.entityId().isBlank() ? metric.entityId() : tagValue(metric.tags(), "agent");
                    String name = defaultString(tagValue(metric.tags(), "name"), id);
                    agentCosts.computeIfAbsent(id, ignored -> new MutableBreakdown(id, name))
                            .accumulate(metric.value(), metric.recordedAt());
                }
                case "cost.model.usd" -> {
                    String id = !metric.entityId().isBlank() ? metric.entityId() : tagValue(metric.tags(), "model");
                    String name = defaultString(tagValue(metric.tags(), "name"), id);
                    modelCosts.computeIfAbsent(id, ignored -> new MutableBreakdown(id, name))
                            .accumulate(metric.value(), metric.recordedAt());
                }
                case "cost.run.usd", "cost.usd", "cost.tenant.usd" -> {
                    totalCostUsd += metric.value();
                    totalRuns += 1;
                }
                default -> {
                }
            }
        }

        if (totalCostUsd == 0.0 && !pipelineCosts.isEmpty()) {
            totalCostUsd = pipelineCosts.values().stream().mapToDouble(MutableBreakdown::costUsd).sum();
        }

        double projectedMonthlyCostUsd = projectMonthly(totalCostUsd, from, to);
        List<CostBreakdown> perPipeline = finalizeBreakdown(pipelineCosts, totalCostUsd);
        List<CostBreakdown> perAgent = finalizeBreakdown(agentCosts, totalCostUsd);
        List<CostBreakdown> perModel = finalizeBreakdown(modelCosts, totalCostUsd);
        List<CostAlert> alerts = buildAlerts(
                totalCostUsd,
                projectedMonthlyCostUsd,
                dailyBudgetUsd,
                monthlyBudgetUsd,
                perPipeline,
                perAgent,
                perAgent.isEmpty());

        return new CostSummary(
                tenantId,
                from,
                to,
                round(totalCostUsd),
                round(projectedMonthlyCostUsd),
                totalRuns == 0 ? 0.0 : round(totalCostUsd / totalRuns),
                perPipeline,
                perAgent,
                perModel,
                buildBudgetSummary(totalCostUsd, projectedMonthlyCostUsd, dailyBudgetUsd, monthlyBudgetUsd),
                alerts,
                "metrics",
                perAgent.isEmpty() ? "metrics-without-agent-breakdown" : "analytics-metrics");
    }

    private static CostBudgetSummary buildBudgetSummary(
            double totalCostUsd,
            double projectedMonthlyCostUsd,
            double dailyBudgetUsd,
            double monthlyBudgetUsd) {
        return new CostBudgetSummary(
                buildBudgetWindow(totalCostUsd, totalCostUsd, dailyBudgetUsd),
                buildBudgetWindow(projectedMonthlyCostUsd, projectedMonthlyCostUsd, monthlyBudgetUsd));
    }

    private static CostBudgetWindow buildBudgetWindow(double observedUsd, double projectedUsd, double budgetUsd) {
        double remainingUsd = round(budgetUsd - projectedUsd);
        double usagePercent = budgetUsd <= 0.0 ? 0.0 : round((projectedUsd / budgetUsd) * 100.0);
        String status = projectedUsd > budgetUsd
                ? "exceeded"
                : usagePercent >= 80.0
                ? "warning"
                : "healthy";
        return new CostBudgetWindow(
                round(budgetUsd),
                round(observedUsd),
                remainingUsd,
                usagePercent,
                status);
    }

    private Promise<List<AgentRecord>> loadRegisteredAgents(String tenantId) {
        if (dataCloudClient == null || dataCloudClient.entityStore() == null) {
            return Promise.of(List.of());
        }
        EventCloudAgentStore agentStore = new EventCloudAgentStore(dataCloudClient.entityStore());
        return agentStore.listAgents(tenantId, 200)
                .map(agents -> agents.stream()
                        .map(agent -> new AgentRecord(
                                stringValue(agent.data().get("id"), agent.id().toString()),
                                stringValue(agent.data().get("name"), agent.id().toString())))
                        .toList());
    }

    private static List<CostBreakdown> finalizeBreakdown(Map<String, MutableBreakdown> breakdowns, double totalCostUsd) {
        return breakdowns.values().stream()
                .sorted(Comparator.comparingDouble(MutableBreakdown::costUsd).reversed())
                .map(item -> new CostBreakdown(
                        item.id,
                        item.name,
                        round(item.costUsd),
                        totalCostUsd <= 0.0 ? 0.0 : round((item.costUsd / totalCostUsd) * 100.0),
                        item.runCount,
                        item.lastSeenAt != null ? item.lastSeenAt.toString() : null,
                        item.id,
                        item.name))
                .toList();
    }

    private static List<CostAlert> buildAlerts(
            double totalCostUsd,
            double projectedMonthlyCostUsd,
            double dailyBudgetUsd,
            double monthlyBudgetUsd,
            List<CostBreakdown> perPipeline,
            List<CostBreakdown> perAgent,
            boolean missingAgentTelemetry) {
        List<CostAlert> alerts = new ArrayList<>();
        if (totalCostUsd > dailyBudgetUsd) {
            alerts.add(new CostAlert(
                    "daily-budget",
                    "warning",
                    "Daily spend exceeded",
                    "Current daily spend is above the configured daily budget.",
                    round(totalCostUsd),
                    round(dailyBudgetUsd)));
        }
        if (projectedMonthlyCostUsd > monthlyBudgetUsd) {
            alerts.add(new CostAlert(
                    "monthly-projection",
                    "critical",
                    "Monthly projection at risk",
                    "Projected monthly spend is above the configured budget envelope.",
                    round(projectedMonthlyCostUsd),
                    round(monthlyBudgetUsd)));
        }
        if (!perPipeline.isEmpty() && perPipeline.getFirst().sharePercent() >= 50.0) {
            alerts.add(new CostAlert(
                    "pipeline-concentration",
                    "info",
                    "Pipeline cost concentration",
                    "One pipeline is responsible for at least half of observed spend.",
                    perPipeline.getFirst().sharePercent(),
                    50.0));
        }
        if (missingAgentTelemetry) {
            alerts.add(new CostAlert(
                    "agent-telemetry",
                    "info",
                    "Agent telemetry incomplete",
                    "Per-agent costs are estimated until cost.agent.usd metrics are published.",
                    perAgent.isEmpty() ? 0.0 : perAgent.getFirst().costUsd(),
                    0.0));
        }
        return alerts;
    }

    private static boolean withinWindow(Map<String, Object> run, Instant from, Instant to) {
        Instant startedAt = parseInstant(stringValue(run.get("startedAt"), Instant.now().toString()), Instant.now());
        return !startedAt.isBefore(from) && !startedAt.isAfter(to);
    }

    private static double estimateRunCost(Map<String, Object> run) {
        long durationMs = parseLong(run.get("durationMs"), 1_000L);
        int eventsProcessed = parseInt(run.get("eventsProcessed"), 1);
        int errorsCount = parseInt(run.get("errorsCount"), 0);
        double seconds = Math.max(durationMs, 1L) / 1_000.0;
        return round(BASE_RUN_COST_USD
                + (seconds * COST_PER_SECOND_USD)
                + (eventsProcessed * COST_PER_EVENT_USD)
                + (errorsCount * ERROR_SURCHARGE_USD));
    }

    private static double projectMonthly(double observedCostUsd, Instant from, Instant to) {
        long observedSeconds = Math.max(Duration.between(from, to).getSeconds(), 1L);
        double monthlySeconds = 30.0 * 24.0 * 60.0 * 60.0;
        return round((observedCostUsd / observedSeconds) * monthlySeconds);
    }

    private static boolean isCostMetric(DataCloudAnalyticsStore.MetricDataPoint metric) {
        return metric.metricName().startsWith("cost.") || "usd".equalsIgnoreCase(metric.unit());
    }

    private static String tagValue(List<String> tags, String prefix) {
        String expectedPrefix = prefix + ':';
        return tags.stream()
                .filter(tag -> tag.startsWith(expectedPrefix))
                .map(tag -> tag.substring(expectedPrefix.length()))
                .findFirst()
                .orElse("");
    }

    private static String defaultString(@Nullable String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String stringValue(@Nullable Object value, String fallback) {
        return value == null ? fallback : value.toString();
    }

    private static Instant parseInstant(@Nullable String raw, Instant fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException ignored) {
            return fallback;
        }
    }

    private static long parseLong(@Nullable Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int parseInt(@Nullable Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static double parseDouble(@Nullable String raw, double fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static final class MutableBreakdown {
        private final String id;
        private final String name;
        private double costUsd;
        private int runCount;
        @Nullable
        private Instant lastSeenAt;

        private MutableBreakdown(String id, String name) {
            this.id = id;
            this.name = name;
        }

        private void accumulate(double additionalCostUsd, @Nullable Instant observedAt) {
            this.costUsd += additionalCostUsd;
            this.runCount += 1;
            if (observedAt != null && (lastSeenAt == null || observedAt.isAfter(lastSeenAt))) {
                this.lastSeenAt = observedAt;
            }
        }

        private double costUsd() {
            return costUsd;
        }
    }

    private record AgentRecord(String id, String name) {
    }

    /**
     * @doc.type record
     * @doc.purpose Cost dashboard summary response
     * @doc.layer product
     * @doc.pattern DTO
     */
    public record CostSummary(
            String tenantId,
            Instant windowStart,
            Instant windowEnd,
            double totalCostUsd,
            double projectedMonthlyCostUsd,
            double averageCostPerRunUsd,
            List<CostBreakdown> perPipeline,
            List<CostBreakdown> perAgent,
            List<CostBreakdown> perModel,
            CostBudgetSummary budget,
            List<CostAlert> alerts,
            String dataSource,
            String allocationModel) {
    }

    /**
     * @doc.type record
     * @doc.purpose Cost allocation row for dashboard tables and charts
     * @doc.layer product
     * @doc.pattern DTO
     */
    public record CostBreakdown(
            String id,
            String name,
            double costUsd,
            double sharePercent,
            int runCount,
            @Nullable String lastSeenAt,
            String pipelineId,
            String pipelineName) {
    }

    /**
     * @doc.type record
     * @doc.purpose Cost alert payload surfaced on the operator dashboard
     * @doc.layer product
     * @doc.pattern DTO
     */
    public record CostAlert(
            String id,
            String severity,
            String title,
            String description,
            double currentValue,
            double thresholdValue) {
    }

    /**
     * @doc.type record
     * @doc.purpose Daily and monthly cost budget status for tenant spend controls
     * @doc.layer product
     * @doc.pattern DTO
     */
    public record CostBudgetSummary(
            CostBudgetWindow daily,
            CostBudgetWindow monthly) {
    }

    /**
     * @doc.type record
     * @doc.purpose Single budget window status for cost threshold visibility
     * @doc.layer product
     * @doc.pattern DTO
     */
    public record CostBudgetWindow(
            double budgetUsd,
            double observedUsd,
            double remainingUsd,
            double usagePercent,
            String status) {
    }
}
