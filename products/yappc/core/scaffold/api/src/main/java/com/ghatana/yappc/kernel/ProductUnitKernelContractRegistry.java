/*
 * Copyright (c) 2026 Ghatana Platform Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.ghatana.yappc.kernel;

import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Narrow Java registry for Kernel ProductUnit public contract values consumed by YAPPC.
 *
 * @doc.type class
 * @doc.purpose Validate YAPPC ProductUnitIntent provider, profile, and surface values against Kernel contract names
 * @doc.layer product
 * @doc.pattern Registry
 */
public final class ProductUnitKernelContractRegistry {

    private final Set<String> providers;
    private final Set<String> lifecycleProfiles;
    private final Set<String> surfaces;

    /**
     * Creates the default registry aligned with Kernel ProductUnit contract fixtures.
     */
    public ProductUnitKernelContractRegistry() {
        this(
                Set.of("ghatana-kernel", "ghatana-file-registry", "platform-registry", "external"),
                Set.of(
                        "standard-web-api-product",
                        "standard-polyglot-product",
                        "backend-only-java-service",
                        "mobile-plus-api-product",
                        "platform-provider-product"
                ),
                Set.of("web-api", "frontend", "backend", "mobile", "worker", "admin", "cli")
        );
    }

    /**
     * Creates a registry with explicit values for tests or generated contract imports.
     *
     * @param providers provider identifiers
     * @param lifecycleProfiles lifecycle profile identifiers
     * @param surfaces surface identifiers
     */
    public ProductUnitKernelContractRegistry(
            @NotNull Set<String> providers,
            @NotNull Set<String> lifecycleProfiles,
            @NotNull Set<String> surfaces
    ) {
        this.providers = Set.copyOf(providers);
        this.lifecycleProfiles = Set.copyOf(lifecycleProfiles);
        this.surfaces = Set.copyOf(surfaces);
    }

    public boolean isProviderKnown(String provider) {
        return provider != null && providers.contains(provider);
    }

    public boolean isLifecycleProfileKnown(String profile) {
        return profile != null && lifecycleProfiles.contains(profile);
    }

    public boolean isSurfaceKnown(String surface) {
        return surface != null && surfaces.contains(surface);
    }

    public Set<String> providers() {
        return providers;
    }

    public Set<String> lifecycleProfiles() {
        return lifecycleProfiles;
    }

    public Set<String> surfaces() {
        return surfaces;
    }
}
