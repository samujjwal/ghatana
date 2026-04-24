/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.messaging.strategy;

/**
 * Retryability classification for connector send failures.
 *
 * @doc.type enum
 * @doc.purpose Classify connector failures by retryability semantics
 * @doc.layer infrastructure
 * @doc.pattern ValueObject
 */
public enum ConnectorFailureClassification {
    RETRYABLE,
    NON_RETRYABLE
}
