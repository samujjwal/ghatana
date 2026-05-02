package com.ghatana.digitalmarketing.application.campaign;

/**
 * Raised when a campaign fails product compliance preflight checks.
 *
 * @doc.type class
 * @doc.purpose Public compliance exception type for campaign application flows
 * @doc.layer product
 * @doc.pattern Exception
 */
public final class CampaignComplianceViolationException extends RuntimeException {

    public CampaignComplianceViolationException(String message) {
        super(message);
    }
}