package com.ghatana.phr.kernel.consent;

/**
 * Typed exception thrown by {@link ConsentService#assertAccess} when an access
 * decision results in a deny outcome.
 *
 * <p>The exception carries the full decision context required for audit logging
 * and HTTP error response construction. Callers must never log the full context
 * object to external channels as it contains tenant and actor identifiers.</p>
 *
 * @doc.type class
 * @doc.purpose Typed deny exception for ConsentService.assertAccess
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public final class ConsentAccessDeniedException extends RuntimeException {

    private final String requestId;
    private final String tenantId;
    private final String actorId;
    private final String patientId;
    private final ConsentService.ConsentAccessDecision decision;

    public ConsentAccessDeniedException(
            String requestId,
            String tenantId,
            String actorId,
            String patientId,
            ConsentService.ConsentAccessDecision decision) {
        super("Access denied — " + decision.reasonCode() + " [requestId=" + requestId + "]");
        this.requestId = requestId;
        this.tenantId = tenantId;
        this.actorId = actorId;
        this.patientId = patientId;
        this.decision = decision;
    }

    public String getRequestId() { return requestId; }
    public String getTenantId() { return tenantId; }
    public String getActorId() { return actorId; }
    public String getPatientId() { return patientId; }
    public ConsentService.ConsentAccessDecision getDecision() { return decision; }
}
