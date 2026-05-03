package com.ghatana.digitalmarketing.domain;

/**
 * Thrown when a DMOS connector is invoked while its runtime feature flag is disabled.
 *
 * <p>Subtype of {@link DmosFeatureDisabledException} that explicitly identifies
 * the connector name alongside the feature key. Callers should map this to HTTP 423 (Locked)
 * to signal that the connector is temporarily unavailable, not that the request is invalid.</p>
 *
 * @doc.type class
 * @doc.purpose Domain exception for disabled DMOS connector feature flags
 * @doc.layer product
 * @doc.pattern Exception
 */
public final class DmosConnectorDisabledException extends RuntimeException {

    private final String connectorName;
    private final String featureKey;

    /**
     * Constructs a new exception for the given connector and feature flag key.
     *
     * @param connectorName human-readable connector name, e.g. {@code "Google Ads"}; must not be null
     * @param featureKey    the DMOS feature flag key, e.g. {@code "dmos.google_ads_connector.enabled"}; must not be null
     */
    public DmosConnectorDisabledException(String connectorName, String featureKey) {
        super(connectorName + " connector is currently disabled (" + featureKey + "=false)");
        this.connectorName = connectorName;
        this.featureKey = featureKey;
    }

    /**
     * Returns the human-readable connector name.
     *
     * @return connector name; never null
     */
    public String getConnectorName() {
        return connectorName;
    }

    /**
     * Returns the feature flag key that is currently disabled.
     *
     * @return feature key; never null
     */
    public String getFeatureKey() {
        return featureKey;
    }
}
