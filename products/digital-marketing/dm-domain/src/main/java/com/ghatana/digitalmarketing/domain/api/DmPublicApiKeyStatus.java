package com.ghatana.digitalmarketing.domain.api;

/**
 * Status of a public API key.
 *
 * @doc.type class
 * @doc.purpose Lifecycle status for DmPublicApiKey (DMOS-F5-002)
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum DmPublicApiKeyStatus {
    ACTIVE,
    EXPIRED,
    REVOKED
}
