package com.ghatana.digitalmarketing.domain.content;

/**
 * Type of content item managed in DMOS.
 *
 * @doc.type enum
 * @doc.purpose DMOS content item type enumeration for versioned content assets
 * @doc.layer product
 * @doc.pattern StatePattern
 */
public enum ContentItemType {

    /** Landing page creative. */
    LANDING_PAGE,

    /** Advertisement creative (banner, social, search). */
    AD,

    /** Email marketing creative. */
    EMAIL,

    /** Regulatory or marketing claim. */
    CLAIM,

    /** Required legal or regulatory disclosure. */
    DISCLOSURE,

    /** AI-generated content section (body copy, headline, etc.). */
    GENERATED_SECTION
}
