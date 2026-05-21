/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.digitalmarketing.connector.googleads;

/**
 * Readiness state for the Google Ads connector.
 *
 * <p>This enum represents the current operational state of the Google Ads connector.
 * The connector must never return fake success; it must report the actual readiness state.</p>
 *
 * @doc.type enum
 * @doc.purpose Google Ads connector readiness states
 * @doc.layer product
 * @doc.pattern Enum
 */
public enum GoogleAdsConnectorReadinessState {
    /**
     * Connector is ready to accept requests.
     * All required configuration is present and the API is reachable.
     */
    READY,

    /**
     * Connector is not ready due to missing or invalid configuration.
     * Required credentials (developer token, customer ID) are missing.
     */
    NOT_READY,

    /**
     * Authentication has failed.
     * The provided access token is invalid or expired.
     */
    AUTH_FAILED,

    /**
     * The Google Ads API has rate-limited requests.
     * The connector should back off and retry.
     */
    RATE_LIMITED,

    /**
     * Remote validation failed.
     * The Google Ads API rejected the request due to validation errors.
     */
    REMOTE_VALIDATION_FAILED,

    /**
     * Publish operation failed.
     * The campaign creation or update failed on the Google Ads side.
     */
    PUBLISH_FAILED,

    /**
     * Environment is blocked.
     * The connector is not configured for the current environment (e.g., production vs test).
     */
    ENVIRONMENT_BLOCKED
}
