/*
 * Copyright (c) 2026 Ghatana Platform Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.ghatana.yappc.kernel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Imports canonical Kernel product route and IA contracts into the YAPPC ProductUnit model.
 *
 * @doc.type class
 * @doc.purpose Converts Kernel product route/use-case contracts into YAPPC ProductUnit intent and artifact plans
 * @doc.layer product
 * @doc.pattern Importer
 */
public final class KernelProductContractImporter {

    private static final String DEFAULT_RUNTIME_PROVIDER = "ghatana-file-registry";
    private static final String DEFAULT_SOURCE_PROVIDER = "ghatana-file-registry";
    private static final String DEFAULT_LIFECYCLE_PROFILE = "mobile-plus-api-product";

    private final ObjectMapper mapper;

    /**
     * Creates a product contract importer with the default JSON mapper.
     */
    public KernelProductContractImporter() {
        this(new ObjectMapper());
    }

    /**
     * Creates a product contract importer with an explicit mapper.
     *
     * @param mapper JSON mapper
     */
    public KernelProductContractImporter(@NotNull ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    }

    /**
     * Imports canonical Kernel product contracts from disk.
     *
     * @param routeContractPath path to a Kernel route contract JSON file
     * @param useCaseBaselinePath path to a Kernel use-case baseline JSON file
     * @return imported YAPPC product model
     */
    public ImportedKernelProduct importProduct(@NotNull Path routeContractPath, @NotNull Path useCaseBaselinePath) {
        Objects.requireNonNull(routeContractPath, "routeContractPath must not be null");
        Objects.requireNonNull(useCaseBaselinePath, "useCaseBaselinePath must not be null");
        try {
            KernelRouteContract routeContract = mapper.readValue(routeContractPath.toFile(), KernelRouteContract.class);
            KernelUseCaseBaseline useCaseBaseline = mapper.readValue(useCaseBaselinePath.toFile(), KernelUseCaseBaseline.class);
            return buildImportedProduct(routeContract, useCaseBaseline);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to import Kernel product contracts", exception);
        }
    }

    private ImportedKernelProduct buildImportedProduct(KernelRouteContract routeContract, KernelUseCaseBaseline useCaseBaseline) {
        validate(routeContract, useCaseBaseline);

        List<ProductRoute> sortedRoutes = routeContract.routes().stream()
                .sorted(Comparator.comparing(ProductRoute::path))
                .toList();
        List<ProductUseCase> sortedUseCases = useCaseBaseline.usecases().stream()
                .sorted(Comparator.comparing(ProductUseCase::id))
                .toList();
        List<GeneratedArtifact> artifacts = generateArtifacts(sortedRoutes, sortedUseCases);
        ProductUnitIntentExporter.Request intentRequest = ProductUnitIntentExporter.Request.builder()
                .projectId(routeContract.product())
                .projectName(routeContract.product())
                .targetType("kernel-product-unit")
                .surfaces(deriveSurfaces(sortedRoutes, sortedUseCases))
                .runtimeProvider(DEFAULT_RUNTIME_PROVIDER)
                .sourceProvider(DEFAULT_SOURCE_PROVIDER)
                .lifecycleProfile(DEFAULT_LIFECYCLE_PROFILE)
                .tenantId("product-contract-import")
                .workspaceId("yappc-product-roundtrip")
                .sourcePhase("contract-import")
                .metadata(buildMetadata(routeContract, sortedRoutes, sortedUseCases, artifacts))
                .build();

        return new ImportedKernelProduct(
                routeContract.product(),
                routeContract.version(),
                sortedRoutes,
                sortedUseCases,
                artifacts,
                intentRequest);
    }

    private static void validate(KernelRouteContract routeContract, KernelUseCaseBaseline useCaseBaseline) {
        if (routeContract.product() == null || routeContract.product().isBlank()) {
            throw new IllegalArgumentException("Kernel route contract product is required");
        }
        if (routeContract.routes().isEmpty()) {
            throw new IllegalArgumentException("Kernel route contract must contain routes");
        }
        if (useCaseBaseline.usecases().isEmpty()) {
            throw new IllegalArgumentException("Kernel use-case baseline must contain use cases");
        }
        if (useCaseBaseline.product() != null
                && !useCaseBaseline.product().isBlank()
                && !routeContract.product().equals(useCaseBaseline.product())) {
            throw new IllegalArgumentException("Kernel route contract product must match use-case baseline product");
        }

        Set<String> routePaths = new LinkedHashSet<>();
        for (ProductRoute route : routeContract.routes()) {
            if (!routePaths.add(route.path())) {
                throw new IllegalArgumentException("Duplicate Kernel route path: " + route.path());
            }
            if ("stable".equals(route.stability())) {
                requireStableMetadata(route);
            }
        }
    }

    private static void requireStableMetadata(ProductRoute route) {
        if (isBlank(apiEndpoint(route))) {
            throw new IllegalArgumentException("Stable Kernel route missing apiEndpoint: " + route.path());
        }

        if (isBlank(policyId(route))) {
            throw new IllegalArgumentException("Stable Kernel route missing policyId: " + route.path());
        }

        if (isBlank(testId(route))) {
            throw new IllegalArgumentException("Stable Kernel route missing testId: " + route.path());
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static List<String> deriveSurfaces(List<ProductRoute> routes, List<ProductUseCase> useCases) {
        Set<String> surfaces = new LinkedHashSet<>();
        if (routes.stream().anyMatch(route -> !isBlank(apiEndpoint(route)))) {
            surfaces.add("backend-api");
        }
        if (routes.stream().anyMatch(route -> !route.path().startsWith("/mobile/"))) {
            surfaces.add("web");
        }
        if (useCases.stream().anyMatch(useCase -> !isBlank(useCase.mobileScreen()))) {
            surfaces.add("mobile");
        }
        return List.copyOf(surfaces);
    }

    private static Map<String, Object> buildMetadata(
            KernelRouteContract routeContract,
            List<ProductRoute> routes,
            List<ProductUseCase> useCases,
            List<GeneratedArtifact> artifacts
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("sourceProduct", routeContract.product());
        metadata.put("routeContractVersion", routeContract.version());
        metadata.put("routeCount", routes.size());
        metadata.put("stableRouteCount", routes.stream().filter(route -> "stable".equals(route.stability())).count());
        metadata.put("useCaseCount", useCases.size());
        metadata.put("generatedArtifactCount", artifacts.size());
        return metadata;
    }

    private static List<GeneratedArtifact> generateArtifacts(List<ProductRoute> routes, List<ProductUseCase> useCases) {
        List<GeneratedArtifact> artifacts = new ArrayList<>();
        for (ProductRoute route : routes) {
            String apiEndpoint = apiEndpoint(route);
            String policyId = policyId(route);
            String testId = testId(route);

            if (!isBlank(apiEndpoint)) {
                artifacts.add(new GeneratedArtifact(
                        testId + "-backend-contract",
                        "backend-api",
                        "java-route-contract-test",
                        apiEndpoint,
                        policyId));
            }
            if (!route.path().startsWith("/mobile/")) {
                artifacts.add(new GeneratedArtifact(
                        testId + "-web-route",
                        "web",
                        "react-route",
                        route.path(),
                        policyId));
            }
        }
        for (ProductUseCase useCase : useCases) {
            if (!isBlank(useCase.mobileScreen())) {
                artifacts.add(new GeneratedArtifact(
                        useCase.id() + "-mobile-screen",
                        "mobile",
                        "react-native-screen",
                        useCase.mobileScreen(),
                        useCase.kernelCapability()));
            }
            artifacts.add(new GeneratedArtifact(
                    useCase.id() + "-journey-test",
                    "test",
                    "use-case-coverage",
                    useCase.webRoute(),
                    useCase.kernelCapability()));
        }
        return List.copyOf(artifacts);
    }

    private static String apiEndpoint(ProductRoute route) {
        return metadataString(route, "apiEndpoint", route.apiEndpoint());
    }

    private static String policyId(ProductRoute route) {
        return metadataString(route, "policyId", route.policyId());
    }

    private static String testId(ProductRoute route) {
        return metadataString(route, "testId", route.testId());
    }

    private static String metadataString(ProductRoute route, String key, String directValue) {
        if (!isBlank(directValue)) {
            return directValue;
        }
        Object metadataValue = route.metadata().get(key);
        return metadataValue instanceof String value ? value : null;
    }

    /**
     * Imported Kernel product model used by YAPPC generation roundtrips.
     *
     * @doc.type record
     * @doc.purpose Carries imported Kernel product routes, use cases, generated artifacts, and ProductUnit intent request
     * @doc.layer product
     * @doc.pattern DTO
     */
    public record ImportedKernelProduct(
            String product,
            String version,
            List<ProductRoute> routes,
            List<ProductUseCase> useCases,
            List<GeneratedArtifact> generatedArtifacts,
            ProductUnitIntentExporter.Request productUnitIntentRequest
    ) {
    }

    /**
     * Generated artifact projection derived from Kernel product route/use-case contracts.
     *
     * @doc.type record
     * @doc.purpose Represents a YAPPC generated artifact target for Kernel product roundtrip validation
     * @doc.layer product
     * @doc.pattern DTO
     */
    public record GeneratedArtifact(
            String id,
            String surface,
            String artifactType,
            String target,
            String policyOrCapability
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record KernelRouteContract(
            String schemaVersion,
            String product,
            String version,
            List<ProductRoute> routes
    ) {
        KernelRouteContract {
            routes = routes == null ? List.of() : List.copyOf(routes);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProductRoute(
            String path,
            String label,
            String description,
            String group,
            String minimumRole,
            List<String> personas,
            List<String> tiers,
            List<String> actions,
            List<String> cards,
            String stability,
            String apiEndpoint,
            String policyId,
            String testId,
            List<String> surface,
            String i18nKey,
            String descriptionI18nKey,
            String routeType,
            Map<String, Object> metadata
    ) {
        public ProductRoute {
            personas = personas == null ? List.of() : List.copyOf(personas);
            tiers = tiers == null ? List.of() : List.copyOf(tiers);
            actions = actions == null ? List.of() : List.copyOf(actions);
            cards = cards == null ? List.of() : List.copyOf(cards);
            surface = surface == null ? List.of() : List.copyOf(surface);
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record KernelUseCaseBaseline(String product, String version, List<ProductUseCase> usecases) {
        KernelUseCaseBaseline {
            usecases = usecases == null ? List.of() : List.copyOf(usecases);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProductUseCase(
            String id,
            String persona,
            String screen,
            String iaRoute,
            String webRoute,
            String mobileScreen,
            List<String> backendApis,
            String kernelCapability,
            boolean offlineSupport,
            String status,
            String phase
    ) {
        public ProductUseCase {
            backendApis = backendApis == null ? List.of() : List.copyOf(backendApis);
        }
    }
}
