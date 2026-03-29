/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.error;

/**
 * Thrown when consent evaluation or enforcement fails in an unexpected way.
 *
 * <p>This is distinct from a normal consent denial, which is represented by
 * {@code ConsentDecision.deny(reason)} and does not throw. This exception is
 * reserved for infrastructure failures (e.g. consent platform unreachable) or
 * programming errors (e.g. null consent context at an enforced boundary).
 *
 * @doc.type class
 * @doc.purpose Signals consent evaluation infrastructure failures
 * @doc.layer product
 * @doc.pattern Exception
 * @since 1.2.0
 */
public final class AepConsentException extends AepException {

    public AepConsentException(String tenantId, String reason) {
        super(tenantId, "consent.eval", reason);
    }

    public AepConsentException(String tenantId, String reason, Throwable cause) {
        super(tenantId, "consent.eval", reason, cause);
    }
}
