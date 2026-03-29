/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.services.featurestore.exception;

/**
 * Thrown when a feature store write fails after all retry attempts are exhausted.
 *
 * <p>This may be a transient failure (network blip, timeout) that resolved before
 * retries completed, or a permanent failure (schema mismatch, quota exceeded).
 * The handler should route the event to the dead-letter queue for later replay.
 *
 * @doc.type class
 * @doc.purpose Exception for feature store write failures in the ingest pipeline
 * @doc.layer product
 * @doc.pattern Exception
 */
public class FeatureStoreWriteException extends FeatureIngestException {

    private final String featureName;
    private final String tenantId;
    private final int attemptCount;

    public FeatureStoreWriteException(String featureName, String tenantId, int attemptCount,
                                      String message, Throwable cause) {
        super(message, ErrorCategory.STORE_WRITE_FAILURE, cause);
        this.featureName = featureName;
        this.tenantId = tenantId;
        this.attemptCount = attemptCount;
    }

    public String getFeatureName() {
        return featureName;
    }

    public String getTenantId() {
        return tenantId;
    }

    public int getAttemptCount() {
        return attemptCount;
    }
}
