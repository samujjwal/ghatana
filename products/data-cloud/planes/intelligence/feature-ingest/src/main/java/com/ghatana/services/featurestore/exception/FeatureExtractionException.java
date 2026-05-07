/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.services.featurestore.exception;

/**
 * Thrown when feature vector extraction fails for a given event payload.
 *
 * <p>This is a permanent failure — retrying with the same payload will not help
 * unless there is a bug fix. Failed events should be sent to the dead-letter queue.
 *
 * @doc.type class
 * @doc.purpose Exception for feature extraction failures in the ingest pipeline
 * @doc.layer product
 * @doc.pattern Exception
 */
public class FeatureExtractionException extends FeatureIngestException {

    private final String eventId;
    private final String tenantId;

    public FeatureExtractionException(String eventId, String tenantId, String message, Throwable cause) {
        super(message, ErrorCategory.EXTRACTION_FAILURE, cause);
        this.eventId = eventId;
        this.tenantId = tenantId;
    }

    public String getEventId() {
        return eventId;
    }

    public String getTenantId() {
        return tenantId;
    }
}
