/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.messaging.strategy;

/**
 * Exception thrown when a keyed producer send operation fails.
 *
 * @doc.type class
 * @doc.purpose Surface typed connector send failure details to callers
 * @doc.layer infrastructure
 * @doc.pattern Exception
 */
public final class ConnectorSendException extends RuntimeException {
    private final ConnectorSendFailure failure;

    public ConnectorSendException(ConnectorSendFailure failure) {
        super(failure != null ? failure.message() : "Connector send failed",
            failure != null ? failure.cause() : null);
        this.failure = failure;
    }

    public ConnectorSendFailure failure() {
        return failure;
    }
}
