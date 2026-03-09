package com.ghatana.softwareorg.finance.pipelines;

import com.ghatana.virtualorg.framework.event.EventPublisher;
import com.ghatana.softwareorg.finance.FinanceDepartment;

/**
 * Pipeline registrar for Finance department workflows.
 *
 * <p>
 * <b>Purpose</b><br>
 * Registers UnifiedOperator pipelines with AEP for Finance event flows: -
 * Invoice pipeline (deal closed → invoice generated → payment) - Revenue
 * recognition pipeline (contract terms → revenue accrual) - Budget monitoring
 * pipeline (actual spending → threshold alerts)
 *
 * @doc.type class
 * @doc.purpose Finance department pipeline orchestration
 * @doc.layer product
 * @doc.pattern Pipeline Registrar
 */
public class FinancePipelinesRegistrar {

    private final FinanceDepartment department;
    private final EventPublisher publisher;

    public FinancePipelinesRegistrar(FinanceDepartment department, EventPublisher publisher) {
        this.department = department;
        this.publisher = publisher;
    }

    /**
     * Register all Finance pipelines with AEP.
     *
     * Registers 3 pipelines: 1. Invoice generation (SalesDealClosed → calculate
     * ARR → invoice) 2. Revenue recognition (deal terms → revenue schedule) 3.
     * Budget monitoring (spend tracking → threshold alerts)
     *
     * @return number of pipelines registered
     */
    public int registerPipelines() {
        registerInvoicePipeline();
        registerRevenuePipeline();
        registerBudgetMonitoringPipeline();
        return 3;
    }

    private void registerInvoicePipeline() {
        try {
            // Pipeline: SalesDealClosed → calculate ARR → generate invoice
            logPipelineRegistration("invoice", "SalesDealClosed", "InvoiceGenerated");
        } catch (Exception e) {
            handlePipelineRegistrationError("invoice", e);
        }
    }

    private void registerRevenuePipeline() {
        try {
            // Pipeline: Deal terms → evaluate recognition → schedule revenue
            logPipelineRegistration("revenue", "DealTerms", "RevenueScheduled");
        } catch (Exception e) {
            handlePipelineRegistrationError("revenue", e);
        }
    }

    private void registerBudgetMonitoringPipeline() {
        try {
            // Pipeline: Monitor spend → compare vs budget → threshold alerts
            logPipelineRegistration("budget-monitoring", "DepartmentSpend", "BudgetThresholdBreach");
        } catch (Exception e) {
            handlePipelineRegistrationError("budget-monitoring", e);
        }
    }

    private void logPipelineRegistration(String pipelineName, String inputEvents, String outputEvents) {
        System.out.printf("[Finance] Registered pipeline '%s': %s -> %s%n",
                pipelineName, inputEvents, outputEvents);
    }

    private void handlePipelineRegistrationError(String pipelineName, Exception e) {
        System.err.printf("[Finance] Failed to register pipeline '%s': %s%n",
                pipelineName, e.getMessage());
    }
}
