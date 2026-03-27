/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.data.governance;

/**
 * Thrown when data processing is attempted without the required consent.
 *
 * @doc.type class
 * @doc.purpose Signal that a required consent is absent before processing data
 * @doc.layer platform
 * @doc.pattern Exception
 */
public final class ConsentRequiredException extends RuntimeException {

    private final String tenantId;
    private final String subjectId;
    private final String purpose;

    public ConsentRequiredException(String tenantId, String subjectId, String purpose) {
        super("Consent required for purpose '" + purpose + "' from subject '"
            + subjectId + "' in tenant '" + tenantId + "'");
        this.tenantId = tenantId;
        this.subjectId = subjectId;
        this.purpose = purpose;
    }

    public String tenantId()  { return tenantId; }
    public String subjectId() { return subjectId; }
    public String purpose()   { return purpose; }
}
