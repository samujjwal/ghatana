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
        Pattern.compile("\\.with\\(HttpMethod\\.[A-Z]+,\\s*\"([^\"]+)\""); 
    private static final Pattern PATH_PARAMETER_PATTERN =
        Pattern.compile(":([a-zA-Z_][a-zA-Z0-9_]*)");
    private static final Pattern SPEC_PATH_PATTERN =
        Pattern.compile("^\\s{2}(/[^:]+):\\s*$");

    @Test
    @DisplayName("canonical OpenAPI spec covers every registered HTTP route")
    void shouldKeepSpecAndRegisteredRoutesInSync() throws IOException { 
        Path repoRoot = resolveRepoRoot(); 
        Path serverFile = repoRoot.resolve( 
            "products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/DataCloudRouterBuilder.java");
        Path specFile = repoRoot.resolve("products/data-cloud/api/openapi.yaml");

        Set<String> codeRoutes = readCodeRoutes(serverFile); 
        Set<String> specPaths = readSpecPaths(specFile); 

        Set<String> codeOnly = new TreeSet<>(codeRoutes); 
        codeOnly.removeAll(specPaths); 

        Set<String> specOnly = new TreeSet<>(specPaths); 
        specOnly.removeAll(codeRoutes); 

        assertThat(codeOnly) 
            .as("routes registered in DataCloudHttpServer but missing from api/openapi.yaml")
            .isEmpty(); 
        assertThat(specOnly) 
            .as("paths documented in api/openapi.yaml but not registered in DataCloudHttpServer")
            .isEmpty(); 
    }

    @Test
    @DisplayName("OpenAPI description keeps Data Cloud workflow terminology boundary explicit")
    void shouldDocumentDataCloudWorkflowBoundary() throws IOException {
        Path repoRoot = resolveRepoRoot();
        Path specFile = repoRoot.resolve("products/data-cloud/api/openapi.yaml");
        String spec = Files.readString(specFile);

        assertThat(spec)
            .contains("In this API, workflow and pipeline execution means data-local plugin runtime execution.")
            .contains("AEP still owns broader agentic orchestration");
    }

    private static Set<String> readCodeRoutes(Path serverFile) throws IOException { 
        Set<String> routes = new TreeSet<>(); 
        for (String line : Files.readAllLines(serverFile)) { 
            Matcher matcher = CODE_ROUTE_PATTERN.matcher(line); 
            if (!matcher.find()) { 
                continue;
            }

            String route = normalizeRoutePath(matcher.group(1)); 
            if (!"/ws".equals(route)) { 
                routes.add(route); 
            }
        }
        return routes;
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

    private static String normalizeRoutePath(String route) { 
        return PATH_PARAMETER_PATTERN.matcher(route).replaceAll("{$1}");
    }

    private static Path resolveRepoRoot() { 
        Path current = Path.of("").toAbsolutePath();
        while (current != null) { 
            if (Files.exists(current.resolve("products/data-cloud/api/openapi.yaml"))) {
                return current;
            }
            current = current.getParent(); 
        }
        throw new IllegalStateException("Unable to locate repository root for OpenAPI route alignment test");
    }
}