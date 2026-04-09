/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.contracts;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Base contract definition that all kernel contract families extend.
 *
 * <p>A contract is a versioned, validated agreement between a provider
 * (kernel module, plugin, or domain pack) and its consumers.
 * Contracts carry metadata for discovery, validation, and governance.</p>
 *
 * @doc.type class
 * @doc.purpose Base contract model for all kernel contract families
 * @doc.layer core
 * @doc.pattern ValueObject
 * @author Ghatana Kernel Team
 * @since 1.1.0
 */
public abstract class KernelContract {

    private final String contractId;
    private final String name;
    private final String version;
    private final ContractFamily family;
    private final Map<String, String> metadata;
    protected Object schema;

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

    protected KernelContract(String contractId, String name, String version,
                             ContractFamily family, Map<String, String> metadata) {
        this.contractId = Objects.requireNonNull(contractId, "contractId required");
        this.name = Objects.requireNonNull(name, "name required");
        this.version = Objects.requireNonNull(version, "version required");
        this.family = Objects.requireNonNull(family, "family required");
        this.metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
        // NOTE: validate() is NOT called here — subclasses call it at the end
        // of their own constructor after all fields are initialized.
    }

    public String getContractId() { return contractId; }
    public String getName() { return name; }
    public String getVersion() { return version; }
    public ContractFamily getFamily() { return family; }
    public Map<String, String> getMetadata() { return metadata; }
    public Object getSchema() { return schema; }

    /**
     * Returns metadata as Map<String, Object> for compatibility with validators.
     * Values are boxed as Objects.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getMetadataAsObjects() {
        return (Map<String, Object>) (Map<?, ?>) metadata;
    }

    /**
     * Validates the contract. Subclasses override to add family-specific rules.
     *
     * @throws IllegalArgumentException if validation fails
     */
    protected void validate() {
        if (contractId.isBlank()) {
            throw new IllegalArgumentException("contractId cannot be blank");
        }
        if (!contractId.matches("^[a-z0-9][a-z0-9._-]*$")) {
            throw new IllegalArgumentException("contractId must match ^[a-z0-9][a-z0-9._-]*$: " + contractId);
        }
        if (!version.matches("^\\d+\\.\\d+\\.\\d+.*$")) {
            throw new IllegalArgumentException("version must be semver: " + version);
        }
    }

    /**
     * Returns validation errors for this contract (empty if valid).
     */
    public List<String> getValidationErrors() {
        try {
            validate();
            return List.of();
        } catch (IllegalArgumentException e) {
            return List.of(e.getMessage());
        }
    }

    @Override
    public String toString() {
        return family + ":" + contractId + "@" + version;
    }
}
