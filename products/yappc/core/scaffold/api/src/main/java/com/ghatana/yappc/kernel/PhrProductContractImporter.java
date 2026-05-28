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
 * Imports canonical PHR route and IA contracts into the YAPPC ProductUnit model.
 *
 * @doc.type class
 * @doc.purpose Converts PHR route/use-case contracts into YAPPC ProductUnit intent and artifact plans
 * @doc.layer product
 * @doc.pattern Importer
 */
public final class PhrProductContractImporter {

    private static final String DEFAULT_RUNTIME_PROVIDER = "ghatana-file-registry";
    private static final String DEFAULT_SOURCE_PROVIDER = "ghatana-file-registry";
    private static final String DEFAULT_LIFECYCLE_PROFILE = "mobile-plus-api-product";

    private final ObjectMapper mapper;

    /**
     * Creates a PHR product contract importer with the default JSON mapper.
     */
    public PhrProductContractImporter() {
        this(new ObjectMapper());
    }

    /**
     * Creates a PHR product contract importer with an explicit mapper.
     *
     * @param mapper JSON mapper
     */
    public PhrProductContractImporter(@NotNull ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    }

    /**
     * Imports canonical PHR contracts from disk.
     *
     * @param routeContractPath path to products/phr/config/phr-route-contract.json
     * @param useCaseBaselinePath path to products/phr/config/phr-usecase-baseline.json
     * @return imported YAPPC PHR product model
     */
    public ImportedPhrProduct importProduct(@NotNull Path routeContractPath, @NotNull Path useCaseBaselinePath) {
        Objects.requireNonNull(routeContractPath, "routeContractPath must not be null");
        Objects.requireNonNull(useCaseBaselinePath, "useCaseBaselinePath must not be null");
        try {
            PhrRouteContract routeContract = mapper.readValue(routeContractPath.toFile(), PhrRouteContract.class);
            PhrUseCaseBaseline useCaseBaseline = mapper.readValue(useCaseBaselinePath.toFile(), PhrUseCaseBaseline.class);
            return buildImportedProduct(routeContract, useCaseBaseline);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to import PHR product contracts", exception);
        }
    }

    private ImportedPhrProduct buildImportedProduct(PhrRouteContract routeContract, PhrUseCaseBaseline useCaseBaseline) {
        validate(routeContract, useCaseBaseline);

        List<PhrRoute> sortedRoutes = routeContract.routes().stream()
                .sorted(Comparator.comparing(PhrRoute::path))
                .toList();
        List<PhrUseCase> sortedUseCases = useCaseBaseline.usecases().stream()
                .sorted(Comparator.comparing(PhrUseCase::id))
                .toList();
        List<GeneratedArtifact> artifacts = generateArtifacts(sortedRoutes, sortedUseCases);
        ProductUnitIntentExporter.Request intentRequest = ProductUnitIntentExporter.Request.builder()
                .projectId(routeContract.product())
                .projectName("Personal Health Record")
                .targetType("kernel-product-unit")
                .surfaces(deriveSurfaces(sortedRoutes, sortedUseCases))
                .runtimeProvider(DEFAULT_RUNTIME_PROVIDER)
                .sourceProvider(DEFAULT_SOURCE_PROVIDER)
                .lifecycleProfile(DEFAULT_LIFECYCLE_PROFILE)
                .tenantId("phr-contract-import")
                .workspaceId("yappc-phr-roundtrip")
                .sourcePhase("contract-import")
                .metadata(buildMetadata(routeContract, sortedRoutes, sortedUseCases, artifacts))
                .build();

        return new ImportedPhrProduct(
                routeContract.product(),
                routeContract.version(),
                sortedRoutes,
                sortedUseCases,
                artifacts,
                intentRequest);
    }

    private static void validate(PhrRouteContract routeContract, PhrUseCaseBaseline useCaseBaseline) {
        if (routeContract.product() == null || routeContract.product().isBlank()) {
            throw new IllegalArgumentException("PHR route contract product is required");
        }
        if (!"phr".equals(routeContract.product())) {
            throw new IllegalArgumentException("Expected PHR route contract, got: " + routeContract.product());
        }
        if (routeContract.routes().isEmpty()) {
            throw new IllegalArgumentException("PHR route contract must contain routes");
        }
        if (useCaseBaseline.usecases().isEmpty()) {
            throw new IllegalArgumentException("PHR use-case baseline must contain use cases");
        }

        Set<String> routePaths = new LinkedHashSet<>();
        for (PhrRoute route : routeContract.routes()) {
            if (!routePaths.add(route.path())) {
                throw new IllegalArgumentException("Duplicate PHR route path: " + route.path());
            }
            if ("stable".equals(route.stability())) {
                requireStableMetadata(route);
            }
        }
    }

    private static void requireStableMetadata(PhrRoute route) {
        if (isBlank(route.apiEndpoint())) {
            throw new IllegalArgumentException("Stable PHR route missing apiEndpoint: " + route.path());
        }
        if (isBlank(route.policyId())) {
            throw new IllegalArgumentException("Stable PHR route missing policyId: " + route.path());
        }
        if (isBlank(route.testId())) {
            throw new IllegalArgumentException("Stable PHR route missing testId: " + route.path());
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static List<String> deriveSurfaces(List<PhrRoute> routes, List<PhrUseCase> useCases) {
        Set<String> surfaces = new LinkedHashSet<>();
        if (routes.stream().anyMatch(route -> !isBlank(route.apiEndpoint()))) {
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
            PhrRouteContract routeContract,
            List<PhrRoute> routes,
            List<PhrUseCase> useCases,
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

    private static List<GeneratedArtifact> generateArtifacts(List<PhrRoute> routes, List<PhrUseCase> useCases) {
        List<GeneratedArtifact> artifacts = new ArrayList<>();
        for (PhrRoute route : routes) {
            if (!isBlank(route.apiEndpoint())) {
                artifacts.add(new GeneratedArtifact(
                        route.testId() + "-backend-contract",
                        "backend-api",
                        "java-route-contract-test",
                        route.apiEndpoint(),
                        route.policyId()));
            }
            if (!route.path().startsWith("/mobile/")) {
                artifacts.add(new GeneratedArtifact(
                        route.testId() + "-web-route",
                        "web",
                        "react-route",
                        route.path(),
                        route.policyId()));
            }
        }
        for (PhrUseCase useCase : useCases) {
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

    /**
     * Imported PHR product model used by YAPPC generation roundtrips.
     *
     * @doc.type record
     * @doc.purpose Carries imported PHR routes, use cases, generated artifacts, and ProductUnit intent request
     * @doc.layer product
     * @doc.pattern DTO
     */
    public record ImportedPhrProduct(
            String product,
            String version,
            List<PhrRoute> routes,
            List<PhrUseCase> useCases,
            List<GeneratedArtifact> generatedArtifacts,
            ProductUnitIntentExporter.Request productUnitIntentRequest
    ) {
    }

    /**
     * Generated artifact projection derived from PHR route/use-case contracts.
     *
     * @doc.type record
     * @doc.purpose Represents a YAPPC generated artifact target for PHR roundtrip validation
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
    record PhrRouteContract(String product, String version, List<PhrRoute> routes) {
        PhrRouteContract {
            routes = routes == null ? List.of() : List.copyOf(routes);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PhrRoute(
            String path,
            String label,
            String apiEndpoint,
            String policyId,
            String testId,
            String stability
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PhrUseCaseBaseline(String product, String version, List<PhrUseCase> usecases) {
        PhrUseCaseBaseline {
            usecases = usecases == null ? List.of() : List.copyOf(usecases);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PhrUseCase(
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
        public PhrUseCase {
            backendApis = backendApis == null ? List.of() : List.copyOf(backendApis);
        }
    }
}
