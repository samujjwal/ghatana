package com.ghatana.digitalmarketing.domain.model;

/**
 * Status of a custom model training job.
 *
 * @doc.type class
 * @doc.purpose Lifecycle status for DmCustomModelTrainingJob (DMOS-F5-004)
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum DmCustomModelTrainingStatus {
    QUEUED,
    TRAINING,
    EVALUATING,
    COMPLETE,
    FAILED,
    CANCELLED
}
