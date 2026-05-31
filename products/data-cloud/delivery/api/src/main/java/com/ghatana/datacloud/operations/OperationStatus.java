package com.ghatana.datacloud.operations;

/**
 * Lifecycle states for product-wide operation records.
 *
 * @doc.type enum
 * @doc.purpose Canonical operation status values for async lifecycle tracking
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum OperationStatus {
    ACCEPTED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    BLOCKED
}
