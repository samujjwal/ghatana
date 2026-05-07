package com.ghatana.digitalmarketing.domain.connector;

/**
 * Canonical connector types supported by the digital marketing platform.
 *
 * @doc.type class
 * @doc.purpose Enumerates external system connector types (DMOS-F2-006, P3-003)
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
    /** TikTok advertising platform. */
    TIKTOK_ADS,
    /** YouTube/CTV advertising platform. */
    YOUTUBE_CTV,
    /** Email marketing service provider. */
    EMAIL_SERVICE_PROVIDER,
    /** Content Management System. */
    CMS,
    /** SEO tool. */
    SEO_TOOL,
    /** CRM system for lead management. */
    CRM,
    /** Landing page hosting provider. */
    LANDING_PAGE_HOST,
    /** Analytics and attribution platform. */
    ANALYTICS,
    /** E-signature service. */
    ESIGNATURE,
    /** Custom connector type. */
    CUSTOM
}
