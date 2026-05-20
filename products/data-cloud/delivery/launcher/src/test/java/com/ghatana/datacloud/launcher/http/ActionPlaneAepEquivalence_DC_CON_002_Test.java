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
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DC-CON-002: Validates that {@code action-plane.yaml} is the canonical contract.
 *
 * <p>DC-P1-10: AEP (Agentic Event Processor) has been retired as a standalone product.
 * {@code aep.yaml} is DEPRECATED and retained only for historical reference.
 * {@code action-plane.yaml} is the canonical Data Cloud Action Plane contract. This test enforces:
 * <ol>
 *   <li>{@code aep.yaml} is marked with a DEPRECATED header comment.</li>
 *   <li>{@code aep.yaml} references {@code action-plane.yaml} as the replacement.</li>
 *   <li>{@code aep.yaml} no longer describes itself as "AEP" standalone API.</li>
 *   <li>{@code action-plane.yaml} describes itself as "Data Cloud Action Plane API".</li>
 *   <li>{@code action-plane.yaml} has authenticated tenant model (not header/query-based).</li>
 *   <li>Every API path in {@code aep.yaml} exists in {@code action-plane.yaml} (no silent drift).</li>
 *   <li>Every API path in {@code action-plane.yaml} exists in {@code aep.yaml} (no silent drift).</li>
 *   <li>Operation semantics (methods per path) are equivalent between contracts.</li>
 * </ol>
 *
 * <p>When {@code aep.yaml} is eventually removed, delete this test and update DC-CON-002 accordingly.
 *
 * @doc.type class
 * @doc.purpose CI equivalence guard for action-plane.yaml vs aep.yaml — DC-CON-002
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DC-CON-002: action-plane.yaml ↔ aep.yaml equivalence")
class ActionPlaneAepEquivalence_DC_CON_002_Test {

    private static final String ACTION_PLANE_FILE = "products/data-cloud/contracts/openapi/action-plane.yaml";
    private static final String AEP_FILE = "products/data-cloud/contracts/openapi/aep.yaml";

    private static final Pattern OPENAPI_PATH_PATTERN = Pattern.compile("^  (/[^:]+):$");

    @Test
    @DisplayName("aep.yaml is marked DEPRECATED")
    void aepYaml_isMarkedDeprecated() throws IOException {
        Path aepFile = resolveFromRepoRoot(AEP_FILE);
        List<String> lines = Files.readAllLines(aepFile);
        boolean hasDeprecatedComment = lines.stream()
                .anyMatch(line -> line.contains("DEPRECATED"));
        assertThat(hasDeprecatedComment)
                .as("aep.yaml must contain a DEPRECATED notice — it is superseded by action-plane.yaml")
                .isTrue();
    }

    @Test
    @DisplayName("aep.yaml references action-plane.yaml in its deprecation notice")
    void aepYaml_referencesActionPlane() throws IOException {
        Path aepFile = resolveFromRepoRoot(AEP_FILE);
        List<String> lines = Files.readAllLines(aepFile);
        boolean referencesActionPlane = lines.stream()
                .anyMatch(line -> line.contains("action-plane.yaml"));
        assertThat(referencesActionPlane)
                .as("aep.yaml must reference action-plane.yaml as the replacement")
                .isTrue();
    }

    @Test
    @DisplayName("every path in aep.yaml exists in action-plane.yaml (no silent drift)")
    void aepPaths_allExistInActionPlane() throws IOException {
        Set<String> aepPaths = extractPaths(AEP_FILE);
        Set<String> actionPlanePaths = extractPaths(ACTION_PLANE_FILE);

        Set<String> onlyInAep = new TreeSet<>(aepPaths);
        onlyInAep.removeAll(actionPlanePaths);

        assertThat(onlyInAep)
                .as("Paths in aep.yaml that are missing from action-plane.yaml — add them to action-plane.yaml")
                .isEmpty();
    }

    @Test
    @DisplayName("every path in action-plane.yaml exists in aep.yaml (no silent drift)")
    void actionPlanePaths_allExistInAep() throws IOException {
        Set<String> aepPaths = extractPaths(AEP_FILE);
        Set<String> actionPlanePaths = extractPaths(ACTION_PLANE_FILE);

        Set<String> onlyInActionPlane = new TreeSet<>(actionPlanePaths);
        onlyInActionPlane.removeAll(aepPaths);

        assertThat(onlyInActionPlane)
                .as("Paths in action-plane.yaml that are missing from aep.yaml — either sync aep.yaml or retire it fully")
                .isEmpty();
    }

    @Test
    @DisplayName("both files define at least 50 paths (sanity guard)")
    void bothFiles_haveSubstantialPathCount() throws IOException {
        Set<String> aepPaths = extractPaths(AEP_FILE);
        Set<String> actionPlanePaths = extractPaths(ACTION_PLANE_FILE);

        assertThat(aepPaths.size())
                .as("aep.yaml should have >= 50 paths")
                .isGreaterThanOrEqualTo(50);
        assertThat(actionPlanePaths.size())
                .as("action-plane.yaml should have >= 50 paths")
                .isGreaterThanOrEqualTo(50);
    }

    @Test
    @DisplayName("action-plane.yaml title reflects Data Cloud Action Plane (not AEP)")
    void actionPlaneYaml_hasCorrectProductTitle() throws IOException {
        Path file = resolveFromRepoRoot(ACTION_PLANE_FILE);
        List<String> lines = Files.readAllLines(file);
        boolean hasCorrectTitle = lines.stream()
            .anyMatch(line -> line.contains("Data Cloud Action Plane API"));
        assertThat(hasCorrectTitle)
            .as("action-plane.yaml title must reflect Data Cloud Action Plane product (not AEP)")
            .isTrue();
    }

    @Test
    @DisplayName("action-plane.yaml describes authenticated tenant model (not header/query-based)")
    void actionPlaneYaml_hasAuthenticatedTenantModel() throws IOException {
        Path file = resolveFromRepoRoot(ACTION_PLANE_FILE);
        String content = Files.readString(file);
        assertThat(content)
            .as("action-plane.yaml must describe authenticated tenant model")
            .contains("authenticated identity")
            .contains("tenant_id");
    }

    @Test
    @DisplayName("action-plane.yaml has proper security schemes (bearer + apiKey)")
    void actionPlaneYaml_hasSecuritySchemes() throws IOException {
        Path file = resolveFromRepoRoot(ACTION_PLANE_FILE);
        String content = Files.readString(file);
        assertThat(content)
            .as("action-plane.yaml must define security schemes")
            .contains("bearerAuth")
            .contains("apiKeyAuth");
    }

    @Test
    @DisplayName("action-plane.yaml X-Tenant-Id header is marked deprecated")
    void actionPlaneYaml_tenantHeaderDeprecated() throws IOException {
        Path file = resolveFromRepoRoot(ACTION_PLANE_FILE);
        String content = Files.readString(file);
        assertThat(content)
            .as("action-plane.yaml X-Tenant-Id header must be marked deprecated")
            .containsIgnoringCase("deprecated");
    }

    @Test
    @DisplayName("aep.yaml does not describe itself as standalone AEP API")
    void aepYaml_noLongerStandaloneAep() throws IOException {
        Path file = resolveFromRepoRoot(AEP_FILE);
        String content = Files.readString(file);
        // DC-P1-10: aep.yaml should not claim to be the standalone AEP API
        assertThat(content.contains("AEP (Agentic Event Processor) API"))
            .as("aep.yaml should no longer describe itself as standalone AEP API")
            .isFalse();
    }

    @Test
    @DisplayName("semantic equivalence - operations per path match between contracts")
    void semanticEquivalence_operationsMatch() throws IOException {
        Map<String, Set<String>> aepOperations = extractPathOperations(AEP_FILE);
        Map<String, Set<String>> actionPlaneOperations = extractPathOperations(ACTION_PLANE_FILE);

        // For every path in aep, operations in action-plane must match
        for (Map.Entry<String, Set<String>> entry : aepOperations.entrySet()) {
            String path = entry.getKey();
            Set<String> aepOps = entry.getValue();
            Set<String> actionPlaneOps = actionPlaneOperations.get(path);

            assertThat(actionPlaneOps)
                .as("Path %s operations must match between aep.yaml and action-plane.yaml", path)
                .isNotNull()
                .containsExactlyInAnyOrderElementsOf(aepOps);
        }
    }

    @Test
    @DisplayName("DC-P1-06: security schemes equivalence between contracts")
    void securitySchemes_equivalence() throws IOException {
        String aepContent = Files.readString(resolveFromRepoRoot(AEP_FILE));
        String actionPlaneContent = Files.readString(resolveFromRepoRoot(ACTION_PLANE_FILE));

        // Both should define bearerAuth and apiKeyAuth
        assertThat(aepContent)
            .as("aep.yaml must define bearerAuth security scheme")
            .contains("bearerAuth");
        assertThat(aepContent)
            .as("aep.yaml must define apiKeyAuth security scheme")
            .contains("apiKeyAuth");
        assertThat(actionPlaneContent)
            .as("action-plane.yaml must define bearerAuth security scheme")
            .contains("bearerAuth");
        assertThat(actionPlaneContent)
            .as("action-plane.yaml must define apiKeyAuth security scheme")
            .contains("apiKeyAuth");
    }

    @Test
    @DisplayName("DC-P1-06: error response codes equivalence between contracts")
    void errorResponseCodes_equivalence() throws IOException {
        String aepContent = Files.readString(resolveFromRepoRoot(AEP_FILE));
        String actionPlaneContent = Files.readString(resolveFromRepoRoot(ACTION_PLANE_FILE));

        // Both should define standard error codes: TENANT_REQUIRED, TENANT_MISMATCH, MISSING_TENANT_CLAIM
        assertThat(aepContent)
            .as("aep.yaml must define TENANT_REQUIRED error code")
            .contains("TENANT_REQUIRED");
        assertThat(aepContent)
            .as("aep.yaml must define TENANT_MISMATCH error code")
            .contains("TENANT_MISMATCH");
        assertThat(aepContent)
            .as("aep.yaml must define MISSING_TENANT_CLAIM error code")
            .contains("MISSING_TENANT_CLAIM");
        assertThat(actionPlaneContent)
            .as("action-plane.yaml must define TENANT_REQUIRED error code")
            .contains("TENANT_REQUIRED");
        assertThat(actionPlaneContent)
            .as("action-plane.yaml must define TENANT_MISMATCH error code")
            .contains("TENANT_MISMATCH");
        assertThat(actionPlaneContent)
            .as("action-plane.yaml must define MISSING_TENANT_CLAIM error code")
            .contains("MISSING_TENANT_CLAIM");
    }

    @Test
    @DisplayName("DC-P1-06: tenant authentication model equivalence")
    void tenantAuthenticationModel_equivalence() throws IOException {
        String aepContent = Files.readString(resolveFromRepoRoot(AEP_FILE));
        String actionPlaneContent = Files.readString(resolveFromRepoRoot(ACTION_PLANE_FILE));

        // Both should describe authenticated tenant model (not header/query-based)
        assertThat(aepContent)
            .as("aep.yaml must describe authenticated identity tenant model")
            .contains("authenticated identity")
            .contains("tenant_id");
        assertThat(actionPlaneContent)
            .as("action-plane.yaml must describe authenticated identity tenant model")
            .contains("authenticated identity")
            .contains("tenant_id");
    }

    @Test
    @DisplayName("DC-P1-08: X-Tenant-Id header deprecation equivalence")
    void xTenantIdHeader_deprecationEquivalence() throws IOException {
        String aepContent = Files.readString(resolveFromRepoRoot(AEP_FILE));
        String actionPlaneContent = Files.readString(resolveFromRepoRoot(ACTION_PLANE_FILE));

        // Both should mark X-Tenant-Id as deprecated
        assertThat(aepContent)
            .as("aep.yaml X-Tenant-Id must be marked deprecated")
            .containsIgnoringCase("deprecated");
        assertThat(actionPlaneContent)
            .as("action-plane.yaml X-Tenant-Id must be marked deprecated")
            .containsIgnoringCase("deprecated");
    }

    @Test
    @DisplayName("DC-P1-08: operation IDs equivalence between contracts")
    void operationIds_equivalence() throws IOException {
        Map<String, Map<String, String>> aepOperationIds = extractPathOperationIds(AEP_FILE);
        Map<String, Map<String, String>> actionPlaneOperationIds = extractPathOperationIds(ACTION_PLANE_FILE);

        // For every path/method in aep, operationId should match in action-plane
        for (Map.Entry<String, Map<String, String>> pathEntry : aepOperationIds.entrySet()) {
            String path = pathEntry.getKey();
            for (Map.Entry<String, String> methodEntry : pathEntry.getValue().entrySet()) {
                String method = methodEntry.getKey();
                String aepOpId = methodEntry.getValue();
                
                Map<String, String> actionPlaneMethods = actionPlaneOperationIds.get(path);
                assertThat(actionPlaneMethods)
                    .as("Path %s must exist in action-plane.yaml", path)
                    .isNotNull();
                
                String actionPlaneOpId = actionPlaneMethods.get(method);
                assertThat(actionPlaneOpId)
                    .as("Operation ID for %s %s must match between contracts", method, path)
                    .isEqualTo(aepOpId);
            }
        }
    }

    @Test
    @DisplayName("DC-P1-08: tag categorization equivalence between contracts")
    void tagCategorization_equivalence() throws IOException {
        Map<String, Set<String>> aepTags = extractPathTags(AEP_FILE);
        Map<String, Set<String>> actionPlaneTags = extractPathTags(ACTION_PLANE_FILE);

        // For every path in aep, tags should match in action-plane
        for (Map.Entry<String, Set<String>> entry : aepTags.entrySet()) {
            String path = entry.getKey();
            Set<String> aepPathTags = entry.getValue();
            Set<String> actionPlanePathTags = actionPlaneTags.get(path);

            assertThat(actionPlanePathTags)
                .as("Tags for path %s must match between contracts", path)
                .isNotNull()
                .containsExactlyInAnyOrderElementsOf(aepPathTags);
        }
    }

    @Test
    @DisplayName("DC-P1-08: response status codes equivalence between contracts")
    void responseStatusCodes_equivalence() throws IOException {
        Map<String, Map<String, Set<String>>> aepResponses = extractPathResponses(AEP_FILE);
        Map<String, Map<String, Set<String>>> actionPlaneResponses = extractPathResponses(ACTION_PLANE_FILE);

        // For every path/method in aep, response codes should match in action-plane
        for (Map.Entry<String, Map<String, Set<String>>> pathEntry : aepResponses.entrySet()) {
            String path = pathEntry.getKey();
            for (Map.Entry<String, Set<String>> methodEntry : pathEntry.getValue().entrySet()) {
                String method = methodEntry.getKey();
                Set<String> aepCodes = methodEntry.getValue();
                
                Map<String, Set<String>> actionPlaneMethods = actionPlaneResponses.get(path);
                assertThat(actionPlaneMethods)
                    .as("Path %s must exist in action-plane.yaml", path)
                    .isNotNull();
                
                Set<String> actionPlaneCodes = actionPlaneMethods.get(method);
                assertThat(actionPlaneCodes)
                    .as("Response codes for %s %s must match between contracts", method, path)
                    .isNotNull()
                    .containsExactlyInAnyOrderElementsOf(aepCodes);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Set<String> extractPaths(String relativePath) throws IOException {
        Path file = resolveFromRepoRoot(relativePath);
        Set<String> paths = new TreeSet<>();
        for (String line : Files.readAllLines(file)) {
            Matcher m = OPENAPI_PATH_PATTERN.matcher(line);
            if (m.matches()) {
                paths.add(m.group(1).trim());
            }
        }
        return paths;
    }

    /**
     * Extracts path -> operations mapping for semantic comparison.
     * Pattern matches lines like "  get:" under a path definition.
     */
    private Map<String, Set<String>> extractPathOperations(String relativePath) throws IOException {
        Path file = resolveFromRepoRoot(relativePath);
        Map<String, Set<String>> result = new java.util.HashMap<>();
        String currentPath = null;

        for (String line : Files.readAllLines(file)) {
            // Check if this is a path definition line
            Matcher pathMatcher = OPENAPI_PATH_PATTERN.matcher(line);
            if (pathMatcher.matches()) {
                currentPath = pathMatcher.group(1).trim();
                result.putIfAbsent(currentPath, new java.util.HashSet<>());
                continue;
            }

            // Check if this is an HTTP method line under a path
            if (currentPath != null) {
                String trimmed = line.trim();
                if (trimmed.matches("^(get|post|put|patch|delete|head|options|trace):")) {
                    String method = trimmed.replace(":", "").toUpperCase();
                    result.get(currentPath).add(method);
                }
            }
        }
        return result;
    }

    /**
     * Extracts path -> method -> operationId mapping for semantic comparison.
     */
    private Map<String, Map<String, String>> extractPathOperationIds(String relativePath) throws IOException {
        Path file = resolveFromRepoRoot(relativePath);
        Map<String, Map<String, String>> result = new java.util.HashMap<>();
        String currentPath = null;
        String currentMethod = null;

        for (String line : Files.readAllLines(file)) {
            // Check if this is a path definition line
            Matcher pathMatcher = OPENAPI_PATH_PATTERN.matcher(line);
            if (pathMatcher.matches()) {
                currentPath = pathMatcher.group(1).trim();
                result.putIfAbsent(currentPath, new java.util.HashMap<>());
                continue;
            }

            // Check if this is an HTTP method line under a path
            if (currentPath != null) {
                String trimmed = line.trim();
                if (trimmed.matches("^(get|post|put|patch|delete|head|options|trace):")) {
                    currentMethod = trimmed.replace(":", "").toUpperCase();
                    result.get(currentPath).putIfAbsent(currentMethod, null);
                } else if (currentMethod != null && trimmed.startsWith("operationId:")) {
                    String opId = trimmed.substring("operationId:".length()).trim();
                    result.get(currentPath).put(currentMethod, opId);
                }
            }
        }
        return result;
    }

    /**
     * Extracts path -> tags mapping for semantic comparison.
     */
    private Map<String, Set<String>> extractPathTags(String relativePath) throws IOException {
        Path file = resolveFromRepoRoot(relativePath);
        Map<String, Set<String>> result = new java.util.HashMap<>();
        String currentPath = null;
        String currentMethod = null;
        boolean inTagsSection = false;

        for (String line : Files.readAllLines(file)) {
            // Check if this is a path definition line
            Matcher pathMatcher = OPENAPI_PATH_PATTERN.matcher(line);
            if (pathMatcher.matches()) {
                currentPath = pathMatcher.group(1).trim();
                result.putIfAbsent(currentPath, new java.util.HashSet<>());
                continue;
            }

            // Check if this is an HTTP method line under a path
            if (currentPath != null) {
                String trimmed = line.trim();
                if (trimmed.matches("^(get|post|put|patch|delete|head|options|trace):")) {
                    currentMethod = trimmed.replace(":", "").toUpperCase();
                } else if (trimmed.startsWith("tags:")) {
                    inTagsSection = true;
                } else if (inTagsSection && trimmed.startsWith("- ")) {
                    String tag = trimmed.substring(2).trim();
                    result.get(currentPath).add(tag);
                } else if (!trimmed.startsWith("- ") && !trimmed.isEmpty()) {
                    inTagsSection = false;
                }
            }
        }
        return result;
    }

    /**
     * Extracts path -> method -> response status codes mapping for semantic comparison.
     */
    private Map<String, Map<String, Set<String>>> extractPathResponses(String relativePath) throws IOException {
        Path file = resolveFromRepoRoot(relativePath);
        Map<String, Map<String, Set<String>>> result = new java.util.HashMap<>();
        String currentPath = null;
        String currentMethod = null;
        boolean inResponsesSection = false;

        for (String line : Files.readAllLines(file)) {
            // Check if this is a path definition line
            Matcher pathMatcher = OPENAPI_PATH_PATTERN.matcher(line);
            if (pathMatcher.matches()) {
                currentPath = pathMatcher.group(1).trim();
                result.putIfAbsent(currentPath, new java.util.HashMap<>());
                continue;
            }

            // Check if this is an HTTP method line under a path
            if (currentPath != null) {
                String trimmed = line.trim();
                if (trimmed.matches("^(get|post|put|patch|delete|head|options|trace):")) {
                    currentMethod = trimmed.replace(":", "").toUpperCase();
                    result.get(currentPath).putIfAbsent(currentMethod, new java.util.HashSet<>());
                } else if (trimmed.startsWith("responses:")) {
                    inResponsesSection = true;
                } else if (inResponsesSection && trimmed.matches("^\\s+'\\d{3}':")) {
                    String code = trimmed.replaceAll("[^']", "").replaceAll("'", "");
                    result.get(currentPath).get(currentMethod).add(code);
                } else if (trimmed.matches("^[a-z]+:") && !trimmed.startsWith("description:")) {
                    inResponsesSection = false;
                }
            }
        }
        return result;
    }

    private Path resolveFromRepoRoot(String relativePath) throws IOException {
        Path cwd = Paths.get("").toAbsolutePath();
        Path candidate = cwd;
        for (int i = 0; i < 10; i++) {
            if (Files.exists(candidate.resolve("settings.gradle.kts"))) {
                Path result = candidate.resolve(relativePath);
                if (!Files.exists(result)) {
                    throw new IOException("File not found: " + result);
                }
                return result;
            }
            candidate = candidate.getParent();
            if (candidate == null) {
                break;
            }
        }
        throw new IOException("Could not locate repo root from: " + cwd);
    }
}
