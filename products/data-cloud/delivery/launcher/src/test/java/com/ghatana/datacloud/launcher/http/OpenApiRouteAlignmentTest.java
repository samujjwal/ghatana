package com.ghatana.datacloud.launcher.http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Verifies the canonical OpenAPI contract stays aligned with live launcher routes.
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("OpenAPI Route Alignment")
class OpenApiRouteAlignmentTest {

    private static final Pattern CODE_ROUTE_PATTERN =
        Pattern.compile("\\.with\\(HttpMethod\\.([A-Z]+),\\s*\"([^\"]+)\"");
    private static final Pattern PATH_PARAMETER_PATTERN =
        Pattern.compile(":([a-zA-Z_][a-zA-Z0-9_]*)");
    private static final Pattern SPEC_PATH_PATTERN =
        Pattern.compile("^\\s{2}(/[^:]+):\\s*$");
    private static final Pattern COMPATIBILITY_PATH_PATTERN =
        Pattern.compile("^\\s*- path: \"([^\"]+)\"");

    @Test
    @DisplayName("canonical OpenAPI specs cover every registered HTTP route by owner")
    void shouldKeepSpecAndRegisteredRoutesInSync() throws IOException {
        Path repoRoot = resolveRepoRoot();
        Path serverFile = repoRoot.resolve(
            "products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/DataCloudRouterBuilder.java");
        Path dataSpecFile = repoRoot.resolve("products/data-cloud/contracts/openapi/data-cloud.yaml");
        Path actionSpecFile = repoRoot.resolve("products/data-cloud/contracts/openapi/action-plane.yaml");
        Path compatibilityFile = repoRoot.resolve("products/data-cloud/contracts/openapi/route-compatibility-registry.yaml");

        Set<String> codeRoutes = readCodeRoutes(serverFile);
        Set<String> compatibilityRoutes = readCompatibilityPaths(compatibilityFile);
        Set<String> dataCodeRoutes = new TreeSet<>(codeRoutes.stream()
            .filter(route -> !route.startsWith("/api/v1/action/"))
            .filter(route -> !compatibilityRoutes.contains(route))
            .toList());
        Set<String> actionCodeRoutes = new TreeSet<>(codeRoutes.stream()
            .filter(route -> route.startsWith("/api/v1/action/"))
            .toList());
        Set<String> dataSpecPaths = readSpecPaths(dataSpecFile);
        Set<String> actionSpecPaths = readSpecPaths(actionSpecFile);
        Set<String> compatibilitySpecPathsInData = new TreeSet<>(dataSpecPaths);
        compatibilitySpecPathsInData.retainAll(compatibilityRoutes);

        Set<String> dataCodeOnly = new TreeSet<>(dataCodeRoutes);
        dataCodeOnly.removeAll(dataSpecPaths);
        Set<String> dataSpecOnly = new TreeSet<>(dataSpecPaths);
        dataSpecOnly.removeAll(dataCodeRoutes);

        Set<String> actionCodeOnly = new TreeSet<>(actionCodeRoutes);
        actionCodeOnly.removeAll(actionSpecPaths);
        Set<String> actionSpecOnly = new TreeSet<>(actionSpecPaths);
        actionSpecOnly.removeAll(actionCodeRoutes);

        assertThat(dataCodeOnly)
            .as("routes registered in DataCloudHttpServer but missing from contracts/openapi/data-cloud.yaml")
            .isEmpty();
        assertThat(dataSpecOnly)
            .as("paths documented in contracts/openapi/data-cloud.yaml but not registered in DataCloudHttpServer")
            .isEmpty();
        assertThat(actionCodeOnly)
            .as("Action Plane routes registered in DataCloudHttpServer but missing from contracts/openapi/action-plane.yaml")
            .isEmpty();
        assertThat(actionSpecOnly)
            .as("paths documented in contracts/openapi/action-plane.yaml but not registered as Action Plane routes")
            .isEmpty();
        assertThat(compatibilitySpecPathsInData)
            .as("legacy Action compatibility paths must not be documented in canonical data-cloud.yaml")
            .isEmpty();
    }

    @Test
    @DisplayName("canonical OpenAPI specs cover every registered HTTP operation by owner")
    void shouldKeepSpecAndRegisteredOperationsInSync() throws IOException {
        Path repoRoot = resolveRepoRoot();
        Path serverFile = repoRoot.resolve(
            "products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/DataCloudRouterBuilder.java");
        Path dataSpecFile = repoRoot.resolve("products/data-cloud/contracts/openapi/data-cloud.yaml");
        Path actionSpecFile = repoRoot.resolve("products/data-cloud/contracts/openapi/action-plane.yaml");
        Path compatibilityFile = repoRoot.resolve("products/data-cloud/contracts/openapi/route-compatibility-registry.yaml");

        Set<String> codeOperations = readCodeOperations(serverFile);
        Set<String> compatibilityRoutes = readCompatibilityPaths(compatibilityFile);
        Set<String> dataCodeOperations = new TreeSet<>(codeOperations.stream()
            .filter(operation -> !operation.startsWith("GET /api/v1/action/")
                && !operation.startsWith("POST /api/v1/action/")
                && !operation.startsWith("PUT /api/v1/action/")
                && !operation.startsWith("DELETE /api/v1/action/")
                && !operation.startsWith("PATCH /api/v1/action/"))
            .filter(operation -> {
                String route = operation.substring(operation.indexOf(' ') + 1);
                return !compatibilityRoutes.contains(route);
            })
            .toList());
        Set<String> actionCodeOperations = new TreeSet<>(codeOperations.stream()
            .filter(operation -> operation.contains(" /api/v1/action/"))
            .toList());

        Set<String> dataSpecOperations = readSpecOperations(dataSpecFile);
        Set<String> actionSpecOperations = readSpecOperations(actionSpecFile);

        Set<String> dataCodeOnly = new TreeSet<>(dataCodeOperations);
        dataCodeOnly.removeAll(dataSpecOperations);
        Set<String> dataSpecOnly = new TreeSet<>(dataSpecOperations);
        dataSpecOnly.removeAll(dataCodeOperations);

        Set<String> actionCodeOnly = new TreeSet<>(actionCodeOperations);
        actionCodeOnly.removeAll(actionSpecOperations);
        Set<String> actionSpecOnly = new TreeSet<>(actionSpecOperations);
        actionSpecOnly.removeAll(actionCodeOperations);

        assertThat(dataCodeOnly)
            .as("operations registered in DataCloudHttpServer but missing from contracts/openapi/data-cloud.yaml")
            .isEmpty();
        assertThat(dataSpecOnly)
            .as("operations documented in contracts/openapi/data-cloud.yaml but not registered in DataCloudHttpServer")
            .isEmpty();
        assertThat(actionCodeOnly)
            .as("Action Plane operations registered in DataCloudHttpServer but missing from contracts/openapi/action-plane.yaml")
            .isEmpty();
        assertThat(actionSpecOnly)
            .as("operations documented in contracts/openapi/action-plane.yaml but not registered as Action Plane routes")
            .isEmpty();
    }

    @Test
    @DisplayName("OpenAPI description keeps Data Cloud workflow terminology boundary explicit")
    void shouldDocumentDataCloudWorkflowBoundary() throws IOException {
        Path repoRoot = resolveRepoRoot();
        Path specFile = repoRoot.resolve("products/data-cloud/contracts/openapi/data-cloud.yaml");
        String spec = Files.readString(specFile);

        assertThat(spec)
            .contains("In this API, workflow and pipeline execution means Action Plane runtime execution within Data Cloud.")
            .contains("The Action Plane runtime (formerly AEP) is integrated within Data Cloud");
    }

    private static Set<String> readCodeRoutes(Path serverFile) throws IOException {
        Set<String> routes = new TreeSet<>();
        for (String line : Files.readAllLines(serverFile)) {
            Matcher matcher = CODE_ROUTE_PATTERN.matcher(line);
            if (!matcher.find()) {
                continue;
            }

            String route = normalizeRoutePath(matcher.group(2));
            if (!"/ws".equals(route)) {
                routes.add(route);
            }
        }
        return routes;
    }

    private static Set<String> readCodeOperations(Path serverFile) throws IOException {
        Set<String> operations = new TreeSet<>();
        for (String line : Files.readAllLines(serverFile)) {
            Matcher matcher = CODE_ROUTE_PATTERN.matcher(line);
            if (!matcher.find()) {
                continue;
            }
            String method = matcher.group(1);
            String route = normalizeRoutePath(matcher.group(2));
            if (!"/ws".equals(route)) {
                operations.add(method + " " + route);
            }
        }
        return operations;
    }

    private static Set<String> readSpecPaths(Path specFile) throws IOException {
        Set<String> paths = new TreeSet<>();
        for (String line : Files.readAllLines(specFile)) {
            Matcher matcher = SPEC_PATH_PATTERN.matcher(line);
            if (matcher.find()) {
                paths.add(matcher.group(1));
            }
        }
        return paths;
    }

    private static Set<String> readSpecOperations(Path specFile) throws IOException {
        Set<String> operations = new TreeSet<>();
        String currentPath = null;

        for (String line : Files.readAllLines(specFile)) {
            Matcher pathMatcher = SPEC_PATH_PATTERN.matcher(line);
            if (pathMatcher.find()) {
                currentPath = pathMatcher.group(1);
                continue;
            }

            if (currentPath == null) {
                continue;
            }

            String trimmed = line.trim();
            if (trimmed.equals("get:")
                || trimmed.equals("post:")
                || trimmed.equals("put:")
                || trimmed.equals("delete:")
                || trimmed.equals("patch:")) {
                String method = trimmed.substring(0, trimmed.length() - 1).toUpperCase();
                operations.add(method + " " + currentPath);
            }
        }

        return operations;
    }

    private static Set<String> readCompatibilityPaths(Path specFile) throws IOException {
        Set<String> paths = new TreeSet<>();
        for (String line : Files.readAllLines(specFile)) {
            Matcher matcher = COMPATIBILITY_PATH_PATTERN.matcher(line);
            if (matcher.find()) {
                paths.add(normalizeRoutePath(matcher.group(1)));
            }
        }
        return paths;
    }

    private static String normalizeRoutePath(String route) {
        return PATH_PARAMETER_PATTERN.matcher(route).replaceAll("{$1}");
    }

    private static Path resolveRepoRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("products/data-cloud/contracts/openapi/data-cloud.yaml"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate repository root for OpenAPI route alignment test");
    }
}
