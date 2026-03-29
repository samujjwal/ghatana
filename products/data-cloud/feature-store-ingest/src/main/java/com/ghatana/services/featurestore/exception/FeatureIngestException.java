/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.services.featurestore.exception;

/**
 * Base exception for feature ingestion failures.
 *
 * <p>All exceptions thrown by the feature ingest pipeline extend this class so
 * callers can use a single catch-all when detailed classification is not needed.
 *
 * @doc.type class
 * @doc.purpose Base exception for feature ingestion failures
 * @doc.layer product
 * @doc.pattern Exception
 */
public class FeatureIngestException extends RuntimeException {

    private final ErrorCategory category;

    public enum ErrorCategory {
        /** Payload could not be parsed or feature vectors could not be extracted. */
        EXTRACTION_FAILURE,
        /** Feature store write failed (transient or permanent). */
        STORE_WRITE_FAILURE,
        /** Downstream store is unavailable (connection error, circuit open). */
        STORE_UNAVAILABLE,
        /** Event offset could not be advanced (internal state corruption). */
        OFFSET_FAILURE
    }

    public FeatureIngestException(String message, ErrorCategory category) {
        super(message);
        this.category = category;
    }

    public FeatureIngestException(String message, ErrorCategory category, Throwable cause) {
        super(message, cause);
        this.category = category;
    }

    public ErrorCategory getCategory() {
        return category;
    }
}
