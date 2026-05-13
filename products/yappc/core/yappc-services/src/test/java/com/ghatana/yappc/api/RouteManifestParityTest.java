/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.api;

import com.ghatana.yappc.api.generated.GeneratedRouteRegistry;
import com.ghatana.yappc.governance.route.RouteEntry;
import io.activej.http.HttpMethod;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Contract parity tests for route-manifest/OpenAPI/generated registry/authorization wiring.
 *
 * @doc.type class
 * @doc.purpose Ensure route contract parity across manifest, generated registry, OpenAPI, and authorization registry
 * @doc.layer test
 * @doc.pattern ContractTest
 */
class RouteManifestParityTest {

    @Test
    void manifestRoutesShouldExistInGeneratedRegistryAndAuthorizationRegistry() throws IOException {
        List<ManifestRoute> manifestRoutes = loadManifestRoutesForServer("yappc-services");
        Set<String> generatedRoutes = new HashSet<>();

        for (RouteEntry route : GeneratedRouteRegistry.getManifest().getRoutesForServer("yappc-services")) {
            generatedRoutes.add(route.method() + " " + route.path());
        }

        RouteAuthorizationRegistry authorizationRegistry = new RouteAuthorizationRegistry(mock(YappcAuthorizationService.class));

        for (ManifestRoute route : manifestRoutes) {
            String routeKey = route.method + " " + route.path;
            assertThat(generatedRoutes)
                .as("Generated registry should contain manifest route %s", routeKey)
                .contains(routeKey);

            assertThat(authorizationRegistry.getRouteDefinition(HttpMethod.valueOf(route.method), route.path))
                .as("Authorization registry should contain manifest route %s", routeKey)
                .isNotNull();
        }
    }

    @Test
    void manifestRoutesAndOperationIdsShouldExistInOpenApi() throws IOException {
        List<ManifestRoute> manifestRoutes = loadManifestRoutesForServer("yappc-services");
        String openApi = Files.readString(resolveRepoFile("products/yappc/docs/api/openapi.yaml"));

        for (ManifestRoute route : manifestRoutes) {
            if (!route.path.startsWith("/api")) {
                continue;
            }

            assertThat(openApi)
                .as("OpenAPI should contain path %s", route.path)
                .contains("\n  " + route.path + ":");

            String methodBlockPrefix = "\n    " + route.method.toLowerCase(Locale.ROOT) + ":";
            assertThat(openApi)
                .as("OpenAPI should contain method %s for path %s", route.method, route.path)
                .contains(methodBlockPrefix);

            assertThat(openApi)
                .as("OpenAPI should contain operationId %s", route.operationId)
                .contains("operationId: " + route.operationId);
        }
    }

    @Test
    void manifestOperationIdsShouldBeRepresentedInFrontendGeneratedClient() throws IOException {
        List<ManifestRoute> manifestRoutes = loadManifestRoutesForServer("yappc-services");
        String generatedServices = readAllTextUnder(resolveRepoFile("products/yappc/frontend/web/src/clients/generated/api/services"));

        int representedOperations = 0;
        for (ManifestRoute route : manifestRoutes) {
            // Generated method names are camelCase operation IDs in most cases.
            if (generatedServices.contains("public static " + route.operationId + "(")) {
                representedOperations++;
            }
        }

        assertThat(representedOperations)
            .as("At least core manifest operations should be represented in generated frontend services")
            .isGreaterThan(25);
    }

    private List<ManifestRoute> loadManifestRoutesForServer(String serverKey) throws IOException {
        List<String> lines = Files.readAllLines(resolveRepoFile("products/yappc/docs/api/route-manifest.yaml"));
        List<ManifestRoute> routes = new ArrayList<>();

        boolean inServerBlock = false;
        String method = null;
        String path = null;
        String operationId = null;

        for (String rawLine : lines) {
            String line = rawLine.stripTrailing();
            String trimmed = line.trim();

            if (trimmed.startsWith(serverKey + ":")) {
                inServerBlock = true;
                continue;
            }

            if (inServerBlock && !line.startsWith("  ") && !trimmed.isEmpty() && !trimmed.startsWith("#")) {
                break;
            }

            if (!inServerBlock || trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }

            if (trimmed.startsWith("- method:")) {
                if (method != null && path != null && operationId != null) {
                    routes.add(new ManifestRoute(method, path, operationId));
                }
                method = valueAfterColon(trimmed);
                path = null;
                operationId = null;
                continue;
            }

            if (trimmed.startsWith("path:")) {
                path = valueAfterColon(trimmed);
                continue;
            }

            if (trimmed.startsWith("operationId:")) {
                operationId = valueAfterColon(trimmed);
            }
        }

        if (method != null && path != null && operationId != null) {
            routes.add(new ManifestRoute(method, path, operationId));
        }

        return routes;
    }

    private String valueAfterColon(String line) {
        int idx = line.indexOf(':');
        return idx >= 0 ? line.substring(idx + 1).trim() : "";
    }

    private String readAllTextUnder(Path directory) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (var stream = Files.walk(directory)) {
            stream
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".ts"))
                .forEach(path -> {
                    try {
                        sb.append(Files.readString(path)).append('\n');
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
        }
        return sb.toString();
    }

    private Path resolveRepoFile(String relativePath) {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relativePath);
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not resolve repo file: " + relativePath);
    }

    private record ManifestRoute(String method, String path, String operationId) {}

}
