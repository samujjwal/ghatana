package com.ghatana.datacloud.operations;

/**
 * Product-wide categories for asynchronous or operator-visible work.
 *
 * @doc.type enum
 * @doc.purpose Canonical operation kind values for Data Cloud job timeline
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum OperationKind {
    CONNECTOR_SYNC,
    CONNECTOR_TEST,
    CONNECTOR_SCHEMA,
    CONNECTOR_HEALTH,
    CONNECTOR_CREDENTIAL_ROTATION,
    MEDIA_PROCESSING,
    MEDIA_RETENTION,
    MEDIA_DELETE,
    PIPELINE_EXECUTION,
    PIPELINE_CANCEL,
    PIPELINE_RETRY,
    PIPELINE_ROLLBACK,
    PIPELINE_CHECKPOINT,
    PIPELINE_RESTORE,
    AGENT_RUN,
    AEP_PATTERN_RUN,
    BACKGROUND_TASK
}
