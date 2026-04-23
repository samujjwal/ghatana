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
        Pattern.compile("\\.with\\(HttpMethod\\.[A-Z]+,\\s*\"([^\"]+)\""); // GH-90000
    private static final Pattern PATH_PARAMETER_PATTERN =
        Pattern.compile(":([a-zA-Z_][a-zA-Z0-9_]*)");
    private static final Pattern SPEC_PATH_PATTERN =
        Pattern.compile("^\\s{2}(/[^:]+):\\s*$");

    @Test
    @DisplayName("canonical OpenAPI spec covers every registered HTTP route")
    void shouldKeepSpecAndRegisteredRoutesInSync() throws IOException { // GH-90000
        Path repoRoot = resolveRepoRoot(); // GH-90000
        Path serverFile = repoRoot.resolve( // GH-90000
            "products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/DataCloudRouterBuilder.java");
        Path specFile = repoRoot.resolve("products/data-cloud/api/openapi.yaml");

        Set<String> codeRoutes = readCodeRoutes(serverFile); // GH-90000
        Set<String> specPaths = readSpecPaths(specFile); // GH-90000

        Set<String> codeOnly = new TreeSet<>(codeRoutes); // GH-90000
        codeOnly.removeAll(specPaths); // GH-90000

        Set<String> specOnly = new TreeSet<>(specPaths); // GH-90000
        specOnly.removeAll(codeRoutes); // GH-90000

        assertThat(codeOnly) // GH-90000
            .as("routes registered in DataCloudHttpServer but missing from api/openapi.yaml")
            .isEmpty(); // GH-90000
        assertThat(specOnly) // GH-90000
            .as("paths documented in api/openapi.yaml but not registered in DataCloudHttpServer")
            .isEmpty(); // GH-90000
    }

    private static Set<String> readCodeRoutes(Path serverFile) throws IOException { // GH-90000
        Set<String> routes = new TreeSet<>(); // GH-90000
        for (String line : Files.readAllLines(serverFile)) { // GH-90000
            Matcher matcher = CODE_ROUTE_PATTERN.matcher(line); // GH-90000
            if (!matcher.find()) { // GH-90000
                continue;
            }

            String route = normalizeRoutePath(matcher.group(1)); // GH-90000
            if (!"/ws".equals(route)) { // GH-90000
                routes.add(route); // GH-90000
            }
        }
        return routes;
    }

    private static Set<String> readSpecPaths(Path specFile) throws IOException { // GH-90000
        Set<String> paths = new TreeSet<>(); // GH-90000
        for (String line : Files.readAllLines(specFile)) { // GH-90000
            Matcher matcher = SPEC_PATH_PATTERN.matcher(line); // GH-90000
            if (matcher.find()) { // GH-90000
                paths.add(matcher.group(1)); // GH-90000
            }
        }
        return paths;
    }

    private static String normalizeRoutePath(String route) { // GH-90000
        return PATH_PARAMETER_PATTERN.matcher(route).replaceAll("{$1}");
    }

    private static Path resolveRepoRoot() { // GH-90000
        Path current = Path.of("").toAbsolutePath();
        while (current != null) { // GH-90000
            if (Files.exists(current.resolve("products/data-cloud/api/openapi.yaml"))) {
                return current;
            }
            current = current.getParent(); // GH-90000
        }
        throw new IllegalStateException("Unable to locate repository root for OpenAPI route alignment test");
    }
}