/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.data.governance;

import java.util.Set;

/**
 * Thrown when a caller attempts to process data for a purpose that has not been
 * allowed via {@link PurposeLimitationEnforcer#bindPurpose}.
 *
 * @doc.type class
 * @doc.purpose Signal a purpose-limitation violation (GDPR Article 5(1)(b))
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public final class PurposeViolationException extends RuntimeException {

    private final String tenantId;
    private final String dataId;
    private final String requestedPurpose;
    private final Set<String> allowedPurposes;

    /**
     * Constructs a new {@code PurposeViolationException}.
     *
     * @param tenantId         the tenant whose data was accessed
     * @param dataId           the data asset identifier
     * @param requestedPurpose the purpose that was denied
     * @param allowedPurposes  the set of permitted purposes (may be empty)
     */
    public PurposeViolationException(
            String tenantId, String dataId, String requestedPurpose, Set<String> allowedPurposes) {
        super("Purpose '%s' is not allowed for data '%s' in tenant '%s'. Allowed: %s"
            .formatted(requestedPurpose, dataId, tenantId, allowedPurposes));
        this.tenantId = tenantId;
        this.dataId = dataId;
        this.requestedPurpose = requestedPurpose;
        this.allowedPurposes = Set.copyOf(allowedPurposes);
    }

    /** @return the tenant owning the data */
    public String tenantId() { return tenantId; }

    /** @return the data asset identifier */
    public String dataId() { return dataId; }

    /** @return the purpose that was rejected */
    public String requestedPurpose() { return requestedPurpose; }

    /** @return the set of permitted purposes at the time of the violation */
    public Set<String> allowedPurposes() { return allowedPurposes; }
}
