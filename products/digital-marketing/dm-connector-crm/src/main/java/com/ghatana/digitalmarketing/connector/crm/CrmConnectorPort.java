package com.ghatana.digitalmarketing.connector.crm;

import io.activej.promise.Promise;

/**
 * Port interface for CRM connectors (DMOS-P3-002).
 *
 * <p>Defines the contract for integrating with external CRM systems like HubSpot, Salesforce, Pipedrive, and Zoho.</p>
 *
 * @doc.type interface
 * @doc.purpose Port interface for CRM connector integrations (DMOS-P3-002)
 * @doc.layer connector
 */
public interface CrmConnectorPort {

    /**
     * Sync a lead/contact to the CRM.
     */
    Promise<String> syncLead(String crmAccountId, String leadData);

    /**
     * Sync an opportunity to the CRM.
     */
    Promise<String> syncOpportunity(String crmAccountId, String opportunityData);

    /**
     * Link attribution from marketing to CRM.
     */
    Promise<Void> linkAttribution(String crmObjectId, String attributionData);

    /**
     * Resolve conflicts between CRM and DMOS data.
     */
    Promise<String> resolveConflict(String crmObjectId, String conflictData);

    /**
     * Propagate consent preferences to CRM.
     */
    Promise<Void> propagateConsent(String crmObjectId, String consentData);

    /**
     * Check connector health.
     */
    Promise<CrmConnectorHealth> checkHealth();

    /**
     * CRM connector health status.
     */
    record CrmConnectorHealth(boolean healthy, String message) {}
}
