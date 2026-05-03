package com.ghatana.digitalmarketing.domain.marketplace;

/**
 * Status of a marketplace listing.
 *
 * @doc.type class
 * @doc.purpose Lifecycle status for DmMarketplaceListing (DMOS-F5-001)
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum DmMarketplaceListingStatus {
    DRAFT,
    PUBLISHED,
    UNLISTED,
    REMOVED
}
