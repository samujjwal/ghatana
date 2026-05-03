package com.ghatana.digitalmarketing.domain.connector;

/**
 * Status of a Meta Ads connector.
 *
 * @doc.type class
 * @doc.purpose Lifecycle status for DmMetaAdsConnector (DMOS-F4-001)
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum DmMetaAdsConnectorStatus {
    PENDING,
    ACTIVE,
    FAILED,
    DISCONNECTED
}
