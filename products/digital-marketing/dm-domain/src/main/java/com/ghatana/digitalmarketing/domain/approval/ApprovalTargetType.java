package com.ghatana.digitalmarketing.domain.approval;

/**
 * Canonical target types that can require human approval in DMOS.
 *
 * @doc.type class
 * @doc.purpose DMOS F1-022 approval target type taxonomy
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum ApprovalTargetType {
    /** Marketing strategy plan approval. */
    STRATEGY,
    /** Proposal document approval before sending. */
    PROPOSAL,
    /** Statement-of-work document approval. */
    SOW,
    /** Content version (ad copy, landing page, email) approval. */
    CONTENT_VERSION,
    /** Monthly or campaign budget allocation approval. */
    BUDGET,
    /** Campaign launch approval (preflight passed → ready to go live). */
    CAMPAIGN_LAUNCH,
    /** Connector write operation approval (e.g. Google Ads account change). */
    CONNECTOR_WRITE,
    /** Explicit risk/policy override approval. */
    OVERRIDE
}
