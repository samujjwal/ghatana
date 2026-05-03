package com.ghatana.digitalmarketing.domain.landingpage;

/**
 * Publishing status for a DmLandingPage.
 *
 * @doc.type class
 * @doc.purpose Lifecycle status values for landing pages (DMOS-F2-010)
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum DmLandingPageStatus {
    DRAFT,
    PUBLISHED,
    UNPUBLISHED,
    FAILED
}
