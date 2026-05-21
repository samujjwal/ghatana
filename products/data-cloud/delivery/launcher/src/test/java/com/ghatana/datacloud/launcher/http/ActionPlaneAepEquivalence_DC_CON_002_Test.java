/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
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
 * DC-CON-002: Validates Action Plane canonical contract ownership.
 *
 * <p>{@code action-plane.yaml} is the active canonical Action Plane contract and owns
 * {@code /api/v1/action/*}. {@code aep.yaml} is retained for legacy compatibility only.
 */
@DisplayName("DC-CON-002: Action Plane canonical contract ownership")
class ActionPlaneAepEquivalence_DC_CON_002_Test {

    private static final String ACTION_PLANE_FILE = "products/data-cloud/contracts/openapi/action-plane.yaml";
    private static final String AEP_FILE = "products/data-cloud/contracts/openapi/aep.yaml";
    private static final String ROUTER_FILE = "products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/DataCloudRouterBuilder.java";

    private static final Pattern OPENAPI_PATH_PATTERN = Pattern.compile("^  (/[^:]+):$");
    private static final Pattern CODE_ROUTE_PATTERN = Pattern.compile("\\.with\\(HttpMethod\\.[A-Z]+,\\s*\"([^\"]+)\"");
    private static final Pattern PATH_PARAMETER_PATTERN = Pattern.compile(":([a-zA-Z_][a-zA-Z0-9_]*)");

    @Test
    @DisplayName("aep.yaml is marked compatibility-only/deprecated")
    void aepYaml_isMarkedCompatibilityOnly() throws IOException {
        String content = Files.readString(resolveFromRepoRoot(AEP_FILE));
        assertThat(content)
                .as("aep.yaml must contain a DEPRECATED notice and reference action-plane.yaml")
                .contains("DEPRECATED")
                .contains("action-plane.yaml");
    }

    @Test
    @DisplayName("action-plane.yaml declares canonical Action Plane tenant/security model")
    void actionPlaneYaml_hasCanonicalProductAndSecurityModel() throws IOException {
        String content = Files.readString(resolveFromRepoRoot(ACTION_PLANE_FILE));
        assertThat(content)
                .contains("Data Cloud Action Plane API")
                .contains("authenticated identity")
                .contains("tenant_id")
                .contains("bearerAuth")
                .contains("apiKeyAuth")
                .contains("TENANT_REQUIRED")
                .contains("TENANT_MISMATCH")
                .contains("MISSING_TENANT_CLAIM")
                .containsIgnoringCase("deprecated");
    }

    @Test
    @DisplayName("action-plane.yaml owns only canonical /api/v1/action paths")
    void actionPlaneYaml_ownsOnlyCanonicalActionNamespace() throws IOException {
        Set<String> paths = extractPaths(ACTION_PLANE_FILE);
        assertThat(paths)
                .as("action-plane.yaml must contain the live canonical Action Plane surface")
                .isNotEmpty()
                .allMatch(path -> path.startsWith("/api/v1/action/"));
        assertThat(paths)
                .doesNotContain("/api/v1/events", "/api/v1/events/batch", "/api/v1/patterns");
    }

    @Test
    @DisplayName("action-plane.yaml paths match live canonical Action Plane router paths")
    void actionPlaneYaml_matchesRouterActionPaths() throws IOException {
        Set<String> actionPlanePaths = extractPaths(ACTION_PLANE_FILE);
        Set<String> routerActionPaths = extractRouterActionPaths();

        Set<String> routerOnly = new TreeSet<>(routerActionPaths);
        routerOnly.removeAll(actionPlanePaths);
        Set<String> specOnly = new TreeSet<>(actionPlanePaths);
        specOnly.removeAll(routerActionPaths);

        assertThat(routerOnly)
                .as("Live /api/v1/action routes missing from action-plane.yaml")
                .isEmpty();
        assertThat(specOnly)
                .as("action-plane.yaml paths not registered in DataCloudRouterBuilder")
                .isEmpty();
    }

    @Test
    @DisplayName("aep.yaml remains compatibility contract, not canonical Action namespace")
    void aepYaml_remainsCompatibilityContract() throws IOException {
        Set<String> aepPaths = extractPaths(AEP_FILE);
        assertThat(aepPaths)
                .as("aep.yaml should retain legacy compatibility paths for older clients")
                .contains("/api/v1/events", "/api/v1/patterns");
        assertThat(aepPaths.stream().noneMatch(path -> path.startsWith("/api/v1/action/")))
                .as("canonical /api/v1/action/* paths belong in action-plane.yaml, not aep.yaml")
                .isTrue();
    }

    private Set<String> extractPaths(String relativePath) throws IOException {
        Set<String> paths = new TreeSet<>();
        for (String line : Files.readAllLines(resolveFromRepoRoot(relativePath))) {
            Matcher matcher = OPENAPI_PATH_PATTERN.matcher(line);
            if (matcher.matches()) {
                paths.add(matcher.group(1).trim());
            }
        }
        return paths;
    }

    private Set<String> extractRouterActionPaths() throws IOException {
        Set<String> paths = new TreeSet<>();
        for (String line : Files.readAllLines(resolveFromRepoRoot(ROUTER_FILE))) {
            Matcher matcher = CODE_ROUTE_PATTERN.matcher(line);
            if (matcher.find()) {
                String normalized = PATH_PARAMETER_PATTERN.matcher(matcher.group(1)).replaceAll("{$1}");
                if (normalized.startsWith("/api/v1/action/")) {
                    paths.add(normalized);
                }
            }
        }
        return paths;
    }

    private Path resolveFromRepoRoot(String relativePath) {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relativePath);
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not find " + relativePath + " from working directory");
    }
}
