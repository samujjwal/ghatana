/*
 * Copyright (c) 2025 Ghatana.
 * All rights reserved.
 */
package com.ghatana.datacloud.config.model;

import java.util.List;
import java.util.Map;

/**
 * Raw storage profile configuration as parsed from YAML. Contains profile
 * definitions for tiered storage management.
 *
 * @doc.type record
 * @doc.purpose Raw YAML-parsed storage profile configuration
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record RawStorageProfileConfig(
        String apiVersion,
        String kind,
        RawStorageProfileMetadata metadata,
        List<RawStorageProfile> profiles
        ) {

    /**
     * Metadata for the storage profile list.
     */
    public record RawStorageProfileMetadata(
            String name,
            String namespace,
            Map<String, String> labels,
            Map<String, String> annotations
    ) {
    }

    /**
     * Individual storage profile definition.
     */
    public record RawStorageProfile(
            String name,
            String displayName,
            String tier,
            RawProfileBackend backend,
            RawCharacteristics characteristics,
            Map<String, Object> settings,
            RawProfileDefaults defaults
    ) {
    }

    /**
     * Backend plugin reference for the profile.
     */
    public record RawProfileBackend(
            String plugin,
            Map<String, String> overrides
    ) {
    }

    /**
     * Storage characteristics for the profile.
     */
    public record RawCharacteristics(
            String latencyClass,
            String costTier,
            String durability,
            Boolean appendOnly,
            Boolean immutable,
            String consistency
    ) {
    }

    /**
     * Default settings for collections using this profile.
     */
    public record RawProfileDefaults(
            String ttl,
            String maxSize,
            String evictionPolicy,
            Integer connectionPool,
            String indexStrategy,
            String partitioning
    ) {
    }
}
