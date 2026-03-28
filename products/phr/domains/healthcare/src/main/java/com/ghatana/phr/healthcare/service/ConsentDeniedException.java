package com.ghatana.phr.healthcare.service;

import com.ghatana.platform.core.exception.GovernancePolicyException;

/**
 * Thrown when consent enforcement denies an access request.
 *
 * @doc.type class
 * @doc.purpose Consent denial exception — carries requestId and reason code for audit
 * @doc.layer domain-pack
 * @doc.pattern Exception
 * @since 1.0.0
 */
public class ConsentDeniedException extends GovernancePolicyException {

    private final String requestId;
    private final String reasonCode;

    public ConsentDeniedException(String message, String requestId, String reasonCode) {
        super(message);
        this.requestId = requestId;
        this.reasonCode = reasonCode;
    }

    public String getRequestId() { return requestId; }
    public String getReasonCode() { return reasonCode; }
}
