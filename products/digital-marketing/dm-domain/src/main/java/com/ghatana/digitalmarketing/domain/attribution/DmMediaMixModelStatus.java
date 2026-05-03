package com.ghatana.digitalmarketing.domain.attribution;

/**
 * Status for media mix model fitting runs.
 *
 * @doc.type class
 * @doc.purpose Lifecycle status for DmMediaMixModel (DMOS-F5-003)
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum DmMediaMixModelStatus {
    PENDING,
    FITTING,
    READY,
    FAILED
}
