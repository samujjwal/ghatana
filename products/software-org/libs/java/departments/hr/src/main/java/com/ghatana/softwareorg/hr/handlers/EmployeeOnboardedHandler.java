package com.ghatana.softwareorg.hr.handlers;

import com.ghatana.platform.observability.MetricsCollector;

/**
 * Handles EmployeeOnboarded events from HR department.
 *
 * @doc.type class
 * @doc.purpose HR event handler
 * @doc.layer product
 * @doc.pattern EventHandler
 */
public class EmployeeOnboardedHandler {

    private final MetricsCollector metrics;

    public EmployeeOnboardedHandler(MetricsCollector metrics) {
        this.metrics = metrics;
    }

    public void handle(String employeeId, String department) {
        metrics.incrementCounter("hr.employee.onboarded", "department", department);
    }
}
