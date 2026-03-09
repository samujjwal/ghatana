package com.ghatana.softwareorg.finance.handlers;

import com.ghatana.platform.observability.MetricsCollector;

/**
 * Handles BudgetAllocated events from Finance department.
 *
 * @doc.type class
 * @doc.purpose Finance event handler
 * @doc.layer product
 * @doc.pattern EventHandler
 */
public class BudgetAllocatedHandler {

    private final MetricsCollector metrics;

    public BudgetAllocatedHandler(MetricsCollector metrics) {
        this.metrics = metrics;
    }

    public void handle(String departmentId, double amount) {
        metrics.incrementCounter("finance.budget.allocated", "amount", String.valueOf(amount));
    }
}
