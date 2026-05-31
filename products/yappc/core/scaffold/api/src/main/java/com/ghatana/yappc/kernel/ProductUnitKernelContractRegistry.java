/*
 * Copyright (c) 2026 Ghatana Platform Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.ghatana.yappc.kernel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Set;

/**
 * Registry for Kernel ProductUnit public contract values consumed by YAPPC.
 *
 * @doc.type class
 * @doc.purpose Validates YAPPC ProductUnitIntent values against imported Kernel public contract values
 * @doc.layer product
 * @doc.pattern Registry
 */
public final class ProductUnitKernelContractRegistry {

    private static final String MINIMUM_SCHEMA_VERSION = "1.0.0";

    private final KernelProductUnitContract contract;
    private final ContractSourceMetadata sourceMetadata;

    /**
     * Creates the default registry from the imported Kernel ProductUnit contract resource.
     * KRN-06: The contract DTO path is the single source of truth shared with ProductUnitIntentExporter.
     */
    public ProductUnitKernelContractRegistry() {
        LoadedContract loaded = loadDefaultContract();
        validateNonEmptyContractSets(loaded.contract());
        this.contract = loaded.contract();
        this.sourceMetadata = loaded.metadata();
    }

    /**
     * Creates a registry with explicit values for tests or generated contract imports.
     *
     * @param contract imported Kernel ProductUnit contract
     */
    public ProductUnitKernelContractRegistry(@NotNull KernelProductUnitContract contract) {
        validateSchemaVersion(contract.schemaVersion());
        this.contract = contract;
        this.sourceMetadata = ContractSourceMetadata.unknown();
    }

    /**
     * Creates a registry with explicit values for tests or generated contract imports.
     *
     * @param providers provider identifiers
     * @param lifecycleProfiles lifecycle profile identifiers
     * @param surfaces surface type identifiers
     */
    public ProductUnitKernelContractRegistry(
            @NotNull Set<String> providers,
            @NotNull Set<String> lifecycleProfiles,
            @NotNull Set<String> surfaces
    ) {
        this(new KernelProductUnitContract(
                "1.0.0",
                providers,
                providers,
                lifecycleProfiles,
                surfaces,
                Set.of(),
                Set.of()));
    }

    public ProductUnitKernelContractRegistry(
            @NotNull Set<String> providers,
            @NotNull Set<String> sourceProviders,
            @NotNull Set<String> lifecycleProfiles,
            @NotNull Set<String> surfaces,
            @NotNull Set<String> productUnitKinds,
            @NotNull Set<String> implementationStatuses
    ) {
        this(new KernelProductUnitContract(
            "1.0.0",
            providers,
            sourceProviders,
            lifecycleProfiles,
            surfaces,
            productUnitKinds,
            implementationStatuses));
    }

    public String contractVersion() {
        return contract.schemaVersion();
    }

    public String generatedAt() {
        return sourceMetadata.generatedAt();
    }

    public String sourceCommit() {
        return sourceMetadata.sourceCommit();
    }

    public boolean isProviderKnown(String provider) {
        return provider != null && contract.providers().contains(provider);
    }

    public boolean isLifecycleProfileKnown(String profile) {
        return profile != null && contract.lifecycleProfiles().contains(profile);
    }

    public boolean isSourceProviderKnown(String sourceProvider) {
        return sourceProvider != null && contract.sourceProviders().contains(sourceProvider);
    }

    public boolean isSurfaceKnown(String surface) {
        return surface != null && contract.surfaces().contains(surface);
    }

    public boolean isProductUnitKindKnown(String kind) {
        return kind != null && contract.productUnitKinds().contains(kind);
    }

    public boolean isImplementationStatusKnown(String implementationStatus) {
        return implementationStatus != null && contract.implementationStatuses().contains(implementationStatus);
    }

    public Set<String> providers() {
        return contract.providers();
    }

    public Set<String> lifecycleProfiles() {
        return contract.lifecycleProfiles();
    }

    public Set<String> sourceProviders() {
        return contract.sourceProviders();
    }

    public Set<String> surfaces() {
        return contract.surfaces();
    }

    public Set<String> productUnitKinds() {
        return contract.productUnitKinds();
    }

    public Set<String> implementationStatuses() {
        return contract.implementationStatuses();
    }

    private static LoadedContract loadDefaultContract() {
        try (InputStream input = ProductUnitKernelContractRegistry.class.getResourceAsStream("/kernel-product-unit-contract.json")) {
            if (input == null) {
                throw new IllegalStateException("Missing Kernel ProductUnit contract resource: /kernel-product-unit-contract.json");
            }
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(input);
            KernelProductUnitContract contract = mapper.treeToValue(root, KernelProductUnitContract.class);
            validateSchemaVersion(contract.schemaVersion());
            ContractSourceMetadata metadata = new ContractSourceMetadata(
                    contract.schemaVersion(),
                    root.path("generatedAt").asText("unknown"),
                    root.path("sourceCommit").asText("unknown")
            );
            return new LoadedContract(contract, metadata);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load Kernel ProductUnit contract resource", e);
        }
    }

    private static void validateNonEmptyContractSets(KernelProductUnitContract contract) {
        if (contract.providers().isEmpty()) {
            throw new IllegalStateException("Kernel ProductUnit contract providers cannot be empty");
        }
        if (contract.sourceProviders().isEmpty()) {
            throw new IllegalStateException("Kernel ProductUnit contract sourceProviders cannot be empty");
        }
        if (contract.productUnitKinds().isEmpty()) {
            throw new IllegalStateException("Kernel ProductUnit contract productUnitKinds cannot be empty");
        }
        if (contract.implementationStatuses().isEmpty()) {
            throw new IllegalStateException("Kernel ProductUnit contract implementationStatuses cannot be empty");
        }
    }

    private static void validateSchemaVersion(String schemaVersion) {
        if (schemaVersion == null || schemaVersion.trim().isEmpty()) {
            throw new IllegalStateException("Kernel ProductUnit contract schema version is null or empty");
        }
        if (!isSchemaVersionCompatible(schemaVersion)) {
            throw new IllegalStateException(
                "Kernel ProductUnit contract schema version '" + schemaVersion +
                "' is not compatible with minimum required version '" + MINIMUM_SCHEMA_VERSION + "'"
            );
        }
    }

    private static boolean isSchemaVersionCompatible(String schemaVersion) {
        String[] requiredParts = MINIMUM_SCHEMA_VERSION.split("\\.");
        String[] actualParts = schemaVersion.split("\\.");

        for (int i = 0; i < Math.min(requiredParts.length, actualParts.length); i++) {
            int required = Integer.parseInt(requiredParts[i]);
            int actual = Integer.parseInt(actualParts[i]);

            if (actual > required) {
                return true;
            }
            if (actual < required) {
                return false;
            }
        }

        return actualParts.length >= requiredParts.length;
    }

    /**
     * Imported Kernel ProductUnit public contract values.
     *
     * @doc.type record
     * @doc.purpose Carries generated/imported Kernel ProductUnit contract values for YAPPC validation
     * @doc.layer product
     * @doc.pattern DTO
     */
    public record KernelProductUnitContract(
            String schemaVersion,
            Set<String> providers,
            Set<String> sourceProviders,
            Set<String> lifecycleProfiles,
            Set<String> surfaces,
            Set<String> productUnitKinds,
            Set<String> implementationStatuses
    ) {
        public KernelProductUnitContract {
            providers = Set.copyOf(providers);
            sourceProviders = Set.copyOf(sourceProviders);
            lifecycleProfiles = Set.copyOf(lifecycleProfiles);
            surfaces = Set.copyOf(surfaces);
            productUnitKinds = Set.copyOf(productUnitKinds);
            implementationStatuses = Set.copyOf(implementationStatuses);
        }
    }

    public record ContractSourceMetadata(
            String contractVersion,
            String generatedAt,
            String sourceCommit
    ) {
        static ContractSourceMetadata unknown() {
            return new ContractSourceMetadata("unknown", "unknown", "unknown");
        }
    }

    private record LoadedContract(KernelProductUnitContract contract, ContractSourceMetadata metadata) {
    }
}
