package com.ghatana.digitalmarketing.domain.connector;

/**
 * Canonical connector types supported by the digital marketing platform.
 *
 * @doc.type class
 * @doc.purpose Enumerates external system connector types (DMOS-F2-006)
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum DmConnectorType {
    /** Google Ads advertising platform. */
    GOOGLE_ADS,
    /** Meta (Facebook/Instagram) advertising platform. */
    META_ADS,
    /** LinkedIn advertising platform. */
    LINKEDIN_ADS,
    /** Email marketing service provider. */
    EMAIL_SERVICE_PROVIDER,
    /** CRM system for lead management. */
    CRM,
    /** Landing page hosting provider. */
    LANDING_PAGE_HOST,
    /** Analytics and attribution platform. */
    ANALYTICS,
    /** Custom connector type. */
    CUSTOM
}
