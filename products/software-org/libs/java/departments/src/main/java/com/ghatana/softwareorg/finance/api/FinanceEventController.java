package com.ghatana.softwareorg.finance.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.virtualorg.framework.event.EventPublisher;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * REST API controller for Finance department events.
 *
 * <p>
 * <b>Purpose</b><br>
 * Exposes HTTP endpoints for financial operations (budget tracking, expense
 * management, revenue recognition, forecasting).
 *
 * @doc.type class
 * @doc.purpose Finance domain REST API
 * @doc.layer product
 * @doc.pattern Controller
 */
public class FinanceEventController {

    private final EventPublisher eventPublisher;
    private final MetricsCollector metrics;
    private final ObjectMapper objectMapper = JsonUtils.getDefaultMapper();

    public FinanceEventController(EventPublisher eventPublisher, MetricsCollector metrics) {
        this.eventPublisher = eventPublisher;
        this.metrics = metrics;
    }

    /**
     * Allocates budget for department or project.
     *
     * @param tenantId tenant context
     * @param budgetId budget allocation identifier
     * @param department department receiving budget
     * @param amount budget amount
     * @param period budget period (QUARTERLY, ANNUAL)
     */
    public void allocateBudget(
            String tenantId, String budgetId, String department, double amount, String period) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("budgetId", budgetId);
        payload.put("department", department);
        payload.put("amount", amount);
        payload.put("period", period);

        payload.put("tenantId", tenantId);
        try {
            eventPublisher.publish("finance.budget.allocated", objectMapper.writeValueAsBytes(payload));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize event payload", e);
        }
        metrics.recordTimer("finance.budget.allocated", (long) amount);
    }

    /**
     * Records expense transaction.
     *
     * @param tenantId tenant context
     * @param expenseId expense identifier
     * @param amount expense amount
     * @param category expense category
     * @param description expense description
     */
    public void recordExpense(
            String tenantId, String expenseId, double amount, String category, String description) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("expenseId", expenseId);
        payload.put("amount", amount);
        payload.put("category", category);
        payload.put("description", description);

        payload.put("tenantId", tenantId);
        try {
            eventPublisher.publish("finance.expense.recorded", objectMapper.writeValueAsBytes(payload));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize event payload", e);
        }
        metrics.recordTimer("finance.expenses", (long) amount, "category", category);
    }

    /**
     * Records forecast update.
     *
     * @param tenantId tenant context
     * @param forecastId forecast identifier
     * @param period forecast period
     * @param projectedRevenue projected revenue
     * @param projectedCost projected cost
     * @param confidence confidence level (0.0-1.0)
     */
    public void recordForecast(
            String tenantId,
            String forecastId,
            String period,
            double projectedRevenue,
            double projectedCost,
            double confidence) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("forecastId", forecastId);
        payload.put("period", period);
        payload.put("projectedRevenue", projectedRevenue);
        payload.put("projectedCost", projectedCost);
        payload.put("margin", (projectedRevenue - projectedCost) / projectedRevenue);
        payload.put("confidence", confidence);

        payload.put("tenantId", tenantId);
        try {
            eventPublisher.publish("finance.forecast.updated", objectMapper.writeValueAsBytes(payload));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize event payload", e);
        }
        metrics.recordTimer("finance.forecast.margin", (long) ((projectedRevenue - projectedCost) / projectedRevenue * 100));
    }
}
