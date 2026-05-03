package com.ghatana.digitalmarketing.domain;

/**
 * Thrown when a DMOS product feature is invoked while its runtime feature flag is disabled.
 *
 * <p>This is a domain-level, recoverable exception signalling that a valid operation was
 * attempted on a feature that is currently switched off. Callers should map this to an
 * HTTP 423 (Locked) or 503 (Service Unavailable) response depending on context, never a
 * 500 Internal Server Error.</p>
 *
 * @doc.type class
 * @doc.purpose Domain exception for disabled DMOS feature flags (replaces UnsupportedOperationException)
 * @doc.layer product
 * @doc.pattern Exception
 */
public final class DmosFeatureDisabledException extends RuntimeException {

    private final String featureKey;

    /**
     * Constructs a new exception for the given feature flag key.
     *
     * @param featureKey the DMOS feature flag key that is disabled, e.g.
     *                   {@code "dmos.ai.enabled"}; must not be null
     */
    public DmosFeatureDisabledException(String featureKey) {
        super("DMOS feature is disabled: " + featureKey);
        this.featureKey = featureKey;
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
