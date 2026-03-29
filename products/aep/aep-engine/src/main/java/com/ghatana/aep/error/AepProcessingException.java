/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.error;

/**
 * Thrown when an event cannot be processed through the AEP pipeline.
 *
 * @doc.type class
 * @doc.purpose Signals event processing pipeline failures
 * @doc.layer product
 * @doc.pattern Exception
 * @since 1.2.0
 */
public final class AepProcessingException extends AepException {

    public AepProcessingException(String tenantId, String eventId, String reason) {
        super(tenantId, "event.process[" + eventId + "]", reason);
    }

    public AepProcessingException(String tenantId, String eventId, String reason, Throwable cause) {
        super(tenantId, "event.process[" + eventId + "]", reason, cause);
    }
}
