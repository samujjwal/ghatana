/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.messaging.strategy;

/**
 * Typed outcome of a producer send operation.
 *
 * @param messageId message id on success (null on failure)
 * @param failure typed failure on failure (null on success)
 *
 * @doc.type record
 * @doc.purpose Provide explicit success/failure send outcome contract
 * @doc.layer infrastructure
 * @doc.pattern ValueObject
 */
public record ConnectorSendResult(String messageId, ConnectorSendFailure failure) {
    public static ConnectorSendResult success(String messageId) {
        return new ConnectorSendResult(messageId, null);
    }

    public static ConnectorSendResult failure(ConnectorSendFailure failure) {
        return new ConnectorSendResult(null, failure);
    }

    public boolean isSuccess() {
        return failure == null;
    }
}
