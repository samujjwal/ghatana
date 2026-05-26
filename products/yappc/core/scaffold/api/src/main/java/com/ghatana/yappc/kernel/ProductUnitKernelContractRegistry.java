/*
 * Copyright (c) 2026 Ghatana Platform Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.ghatana.yappc.kernel;

import com.fasterxml.jackson.databind.ObjectMapper;
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

    private static final String CONTRACT_RESOURCE = "/kernel-product-unit-contract.json";

    private final KernelProductUnitContract contract;

    /**
     * Creates the default registry from the imported Kernel ProductUnit contract resource.
     */
    public ProductUnitKernelContractRegistry() {
        this(loadDefaultContract());
    }

    /**
     * Creates a registry with explicit values for tests or generated contract imports.
     *
     * @param contract imported Kernel ProductUnit contract
     */
    public ProductUnitKernelContractRegistry(@NotNull KernelProductUnitContract contract) {
        this.contract = contract;
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
                lifecycleProfiles,
                surfaces,
                Set.of(),
                Set.of()));
    }

    public boolean isProviderKnown(String provider) {
        return provider != null && contract.providers().contains(provider);
    }

    public boolean isLifecycleProfileKnown(String profile) {
        return profile != null && contract.lifecycleProfiles().contains(profile);
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

    public Set<String> surfaces() {
        return contract.surfaces();
    }

    public Set<String> productUnitKinds() {
        return contract.productUnitKinds();
    }

    public Set<String> implementationStatuses() {
        return contract.implementationStatuses();
    }

    private static KernelProductUnitContract loadDefaultContract() {
        try (InputStream input = ProductUnitKernelContractRegistry.class.getResourceAsStream(CONTRACT_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Missing Kernel ProductUnit contract resource: " + CONTRACT_RESOURCE);
            }
            return new ObjectMapper().readValue(input, KernelProductUnitContract.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load Kernel ProductUnit contract resource", e);
        }
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
            Set<String> lifecycleProfiles,
            Set<String> surfaces,
            Set<String> productUnitKinds,
            Set<String> implementationStatuses
    ) {
        public KernelProductUnitContract {
            providers = Set.copyOf(providers);
            lifecycleProfiles = Set.copyOf(lifecycleProfiles);
            surfaces = Set.copyOf(surfaces);
            productUnitKinds = Set.copyOf(productUnitKinds);
            implementationStatuses = Set.copyOf(implementationStatuses);
        }
    }
}
