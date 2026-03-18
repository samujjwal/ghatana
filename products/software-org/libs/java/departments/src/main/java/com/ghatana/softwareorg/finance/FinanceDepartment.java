package com.ghatana.softwareorg.finance;

import com.ghatana.virtualorg.framework.AbstractOrganization;
import com.ghatana.virtualorg.framework.event.EventPublisher;
import com.ghatana.softwareorg.domain.common.BaseSoftwareOrgDepartment;
import com.ghatana.platform.types.identity.Identifier;

/**
 * Finance Department for software-org.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides extension hooks for finance-specific operations. Core behavior
 * (event types, workflows, agents) is defined in YAML configuration.
 *
 * <p>
 * <b>Extension Points</b><br>
 * - Invoicing
 * - Revenue recognition
 * - Budget tracking
 * - Forecasting
 *
 * @doc.type class
 * @doc.purpose Finance department extension hooks
 * @doc.layer product
 * @doc.pattern Extension Point
 */
public class FinanceDepartment extends BaseSoftwareOrgDepartment {

    public static final String DEPARTMENT_TYPE = "FINANCE";
    public static final String DEPARTMENT_NAME = "Finance";

    public FinanceDepartment(AbstractOrganization org, EventPublisher publisher) {
        super(org, publisher, DEPARTMENT_NAME, DEPARTMENT_TYPE);
    }

    /**
     * Hook: Generate invoice for customer.
     *
     * @param customerId    customer to invoice
     * @param billingPeriod billing period
     * @return invoice ID
     */
    public String generateInvoice(String customerId, String billingPeriod) {
        String invoiceId = Identifier.random().raw();

        publishEvent("InvoiceGenerated", newPayload()
                .withField("invoice_id", invoiceId)
                .withField("customer_id", customerId)
                .withField("billing_period", billingPeriod)
                .withField("status", "GENERATED")
                .withTimestamp()
                .build());

        return invoiceId;
    }

    /**
     * Hook: Record invoice payment.
     *
     * @param invoiceId      invoice being paid
     * @param amountReceived amount paid
     */
    public void recordPayment(String invoiceId, float amountReceived) {
        publishEvent("InvoicePaymentReceived", newPayload()
                .withField("invoice_id", invoiceId)
                .withField("amount_received", amountReceived)
                .withTimestamp()
                .build());
    }

    /**
     * Hook: Recognize revenue from contract.
     *
     * @param dealId contract to recognize revenue from
     * @param amount amount to recognize
     */
    public void recognizeRevenue(String dealId, float amount) {
        publishEvent("RevenueRecognized", newPayload()
                .withField("deal_id", dealId)
                .withField("amount", amount)
                .withTimestamp()
                .build());
    }

    /**
     * Hook: Check budget against spending.
     *
     * @param department department to check
     * @param actualSpend  actual spend amount
     * @param budgetLimit  budget limit
     */
    public void checkBudget(String department, float actualSpend, float budgetLimit) {
        boolean exceeded = actualSpend > budgetLimit;
        
        publishEvent(exceeded ? "BudgetExceeded" : "BudgetChecked", newPayload()
                .withField("department", department)
                .withField("actual_spend", actualSpend)
                .withField("budget_limit", budgetLimit)
                .withField("status", exceeded ? "EXCEEDED" : "WITHIN_BUDGET")
                .withTimestamp()
                .build());
    }

    /**
     * Hook: Generate quarterly forecast.
     *
     * @param quarter quarter to forecast
     * @return forecast ID
     */
    public String generateForecast(String quarter) {
        String forecastId = Identifier.random().raw();

        publishEvent("QuarterlyForecastUpdated", newPayload()
                .withField("forecast_id", forecastId)
                .withField("quarter", quarter)
                .withField("status", "GENERATED")
                .withTimestamp()
                .build());

        return forecastId;
    }
}
