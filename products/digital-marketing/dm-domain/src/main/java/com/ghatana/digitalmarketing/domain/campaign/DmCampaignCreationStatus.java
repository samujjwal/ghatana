package com.ghatana.digitalmarketing.domain.campaign;

/**
 * Status lifecycle for a Google campaign creation request.
 *
 * @doc.type class
 * @doc.purpose Tracks campaign submission lifecycle (DMOS-F2-008)
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum DmCampaignCreationStatus {
    /** Request created but not yet submitted to the platform. */
    PENDING,
    /** Request submitted to the external platform. */
    SUBMITTED,
    /** Submission failed. */
    FAILED,
    /** Campaign successfully active on the platform. */
    ACTIVE
}
