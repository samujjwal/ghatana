package com.ghatana.kernel.connector;

/**
 * Type of external platform connector.
 *
 * @doc.type enum
 * @doc.purpose Supported connector types for external platforms (KERNEL-P1)
 * @doc.layer core
 */
public enum ConnectorType {
    /**
     * Meta (Facebook, Instagram) advertising connector.
     */
    META_ADS,

    /**
     * LinkedIn advertising connector.
     */
    LINKEDIN_ADS,

    /**
     * TikTok advertising connector.
     */
    TIKTOK_ADS,

    /**
     * YouTube/CTV advertising connector.
     */
    YOUTUBE_CTV,

    /**
     * Email service provider connector.
     */
    EMAIL_PROVIDER,

    /**
     * Content Management System connector.
     */
    CMS,

    /**
     * SEO tool connector.
     */
    SEO_TOOL,

    /**
     * Customer Relationship Management connector.
     */
    CRM,

    /**
     * Analytics platform connector.
     */
    ANALYTICS,

    /**
     * E-signature service connector.
     */
    ESIGNATURE
}
