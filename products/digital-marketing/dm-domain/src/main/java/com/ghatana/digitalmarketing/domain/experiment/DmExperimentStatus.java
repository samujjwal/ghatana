package com.ghatana.digitalmarketing.domain.experiment;

/**
 * Lifecycle status for an A/B experiment.
 *
 * @doc.type class
 * @doc.purpose Tracks experiment lifecycle (DMOS-F3-003)
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum DmExperimentStatus {
    DRAFT,
    RUNNING,
    CONCLUDED,
    CANCELLED
}
