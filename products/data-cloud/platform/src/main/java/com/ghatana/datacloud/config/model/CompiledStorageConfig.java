package com.ghatana.datacloud.config.model;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Compiled storage configuration for a collection.
 *
 * @doc.type record
 * @doc.purpose Immutable compiled storage configuration for runtime access
 * @doc.layer core
 * @doc.pattern Immutable Value Object
 */
public record CompiledStorageConfig(
        String profileName,
        String partitionKey,
        String sortKey,
        List<CompiledStorageTier> tiers
        ) {

    /**
     * Creates a CompiledStorageConfig with validation.
     */
    public CompiledStorageConfig    {
        Objects.requireNonNull(profileName, "Storage profile name cannot be null");
        tiers = tiers != null ? List.copyOf(tiers) : List.of();
    }

    /**
     * Check if this is a multi-tier storage configuration.
     *
     * @return true if multiple tiers configured
     */
    public boolean isMultiTier() {
        return !tiers.isEmpty();
    }

    /**
     * Get the hot tier (first tier) if multi-tier.
     *
     * @return hot tier or null if single-tier
     */
    public CompiledStorageTier getHotTier() {
        return tiers.isEmpty() ? null : tiers.get(0);
    }

    /**
     * Get the cold/archive tier (last tier) if multi-tier.
     *
     * @return cold tier or null if single-tier
     */
    public CompiledStorageTier getColdTier() {
        return tiers.isEmpty() ? null : tiers.get(tiers.size() - 1);
    }
}
