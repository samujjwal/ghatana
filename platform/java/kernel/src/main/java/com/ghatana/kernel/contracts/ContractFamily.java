/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.contracts;

/**
 * Enumerates the kernel contract families.
 *
 * <p>Each family represents a distinct facet of the platform contract surface:</p>
 * <ul>
 *   <li>{@link #EXPERIENCE} — UI, screens, navigation, theming contracts</li>
 *   <li>{@link #API} — HTTP endpoints, RPC, gateway route contracts</li>
 *   <li>{@link #SCHEMA} — Data models, event schemas, evolution guarantees</li>
 *   <li>{@link #ANALYTICS} — Metrics, telemetry, dashboards, KPI contracts</li>
 *   <li>{@link #AUTONOMY} — Agent, AI model, policy, learning contracts</li>
 *   <li>{@link #PACKAGING} — Pack manifest, deployment, lifecycle contracts</li>
 * </ul>
 *
 * @doc.type enum
 * @doc.purpose Classifies contracts into platform-level families
 * @doc.layer core
 * @doc.pattern ValueObject
 * @author Ghatana Kernel Team
 * @since 1.1.0
 */
public enum ContractFamily {

    /** UI surfaces: screens, components, navigation, theming. */
    EXPERIENCE("experience"),

    /** API surfaces: HTTP routes, RPC services, gateway integration. */
    API("api"),

    /** Schema surfaces: event payloads, data models, evolution rules. */
    SCHEMA("schema"),

    /** Analytics surfaces: metrics, dashboards, telemetry pipelines. */
    ANALYTICS("analytics"),

    /** Autonomy surfaces: agent policies, model governance, learning loops. */
    AUTONOMY("autonomy"),

    /** Packaging surfaces: pack manifests, deployment units, lifecycle hooks. */
    PACKAGING("packaging");

    private final String key;

    ContractFamily(String key) {
        this.key = key;
    }

    /**
     * Returns the stable string key for serialization and contract-id prefixing.
     */
    public String getKey() {
        return key;
    }
}
