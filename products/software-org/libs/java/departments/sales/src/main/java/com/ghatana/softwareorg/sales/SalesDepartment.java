package com.ghatana.softwareorg.sales;

import com.ghatana.virtualorg.framework.AbstractOrganization;
import com.ghatana.virtualorg.framework.event.EventPublisher;
import com.ghatana.softwareorg.domain.common.BaseSoftwareOrgDepartment;
import com.ghatana.platform.types.identity.Identifier;

/**
 * Sales Department for software-org.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides extension hooks for sales-specific operations. Core behavior
 * (event types, workflows, agents) is defined in YAML configuration.
 *
 * <p>
 * <b>Extension Points</b><br>
 * - Lead management
 * - Opportunity tracking
 * - Deal closure
 * - Pipeline analytics
 *
 * @doc.type class
 * @doc.purpose Sales department extension hooks
 * @doc.layer product
 * @doc.pattern Extension Point
 */
public class SalesDepartment extends BaseSoftwareOrgDepartment {

    public static final String DEPARTMENT_TYPE = "SALES";
    public static final String DEPARTMENT_NAME = "Sales";

    public SalesDepartment(AbstractOrganization org, EventPublisher publisher) {
        super(org, publisher, DEPARTMENT_NAME, DEPARTMENT_TYPE);
    }

    /**
     * Hook: Create new sales lead.
     *
     * @param company  company name
     * @param contact  contact name
     * @param industry industry type
     * @return lead ID
     */
    public String createLead(String company, String contact, String industry) {
        String leadId = Identifier.random().raw();

        publishEvent("SalesLeadCreated", newPayload()
                .withField("lead_id", leadId)
                .withField("company", company)
                .withField("contact", contact)
                .withField("industry", industry)
                .withField("status", "CREATED")
                .withTimestamp()
                .build());

        return leadId;
    }

    /**
     * Hook: Qualify sales lead.
     *
     * @param leadId             lead to qualify
     * @param qualificationScore HIGH/MEDIUM/LOW
     */
    public void qualifyLead(String leadId, String qualificationScore) {
        publishEvent("SalesLeadQualified", newPayload()
                .withField("lead_id", leadId)
                .withField("qualification_score", qualificationScore)
                .withTimestamp()
                .build());
    }

    /**
     * Hook: Create opportunity from qualified lead.
     *
     * @param leadId         source lead
     * @param estimatedValue contract value estimate
     * @return opportunity ID
     */
    public String createOpportunity(String leadId, float estimatedValue) {
        String opportunityId = Identifier.random().raw();

        publishEvent("SalesOpportunityCreated", newPayload()
                .withField("opportunity_id", opportunityId)
                .withField("lead_id", leadId)
                .withField("estimated_value", estimatedValue)
                .withField("status", "CREATED")
                .withTimestamp()
                .build());

        return opportunityId;
    }

    /**
     * Hook: Advance opportunity through sales stages.
     *
     * @param opportunityId opportunity to advance
     * @param newStage      target sales stage
     */
    public void advanceOpportunity(String opportunityId, String newStage) {
        publishEvent("SalesOpportunityAdvanced", newPayload()
                .withField("opportunity_id", opportunityId)
                .withField("new_stage", newStage)
                .withTimestamp()
                .build());
    }

    /**
     * Hook: Close sales deal.
     *
     * @param opportunityId  opportunity to close
     * @param finalValue     final contract value
     * @param durationMonths contract duration in months
     */
    public void closeDeal(String opportunityId, float finalValue, int durationMonths) {
        publishEvent("SalesDealClosed", newPayload()
                .withField("opportunity_id", opportunityId)
                .withField("final_value", finalValue)
                .withField("duration_months", durationMonths)
                .withField("status", "CLOSED")
                .withTimestamp()
                .build());
    }
}
