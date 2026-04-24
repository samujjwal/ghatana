/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.messaging.strategy;

/**
 * Typed connector send failure payload with retryability classification.
 *
 * @param classification retryability classification
 * @param message human-readable failure reason
 * @param cause original throwable (optional)
 *
 * @doc.type record
 * @doc.purpose Represent typed connector send failure with retry semantics
 * @doc.layer infrastructure
 * @doc.pattern ValueObject
 */
public record ConnectorSendFailure(
    ConnectorFailureClassification classification,
    String message,
    Throwable cause
) {
    public static ConnectorSendFailure retryable(String message) {
        return new ConnectorSendFailure(ConnectorFailureClassification.RETRYABLE, message, null);
    }

    public static ConnectorSendFailure of(
        ConnectorFailureClassification classification,
        String message,
        Throwable cause
    ) {
        return new ConnectorSendFailure(classification, message, cause);
    }
}
