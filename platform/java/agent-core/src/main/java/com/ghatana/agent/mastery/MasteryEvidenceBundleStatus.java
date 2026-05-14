/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.mastery;

/**
 * Status of a mastery evidence bundle in the promotion workflow.
 *
 * <p>Bundles follow a lifecycle: PENDING → APPROVED/REJECTED → APPLIED.
 *
 * @doc.type enum
 * @doc.purpose Status of mastery evidence bundle
 * @doc.layer agent-core
 * @doc.pattern Enumeration
 */
public enum MasteryEvidenceBundleStatus {
    /**
     * Bundle is created and pending review.
     */
    PENDING,

    /**
     * Bundle has been approved for application.
     */
    APPROVED,

    /**
     * Bundle has been rejected (insufficient evidence or failed validation).
     */
    REJECTED,

    /**
     * Bundle has been applied to the mastery state.
     */
    APPLIED,

    /**
     * Bundle application failed (transient error, retry possible).
     */
    FAILED,

    /**
     * Bundle is superseded by a newer bundle.
     */
    SUPERSEDED
}
