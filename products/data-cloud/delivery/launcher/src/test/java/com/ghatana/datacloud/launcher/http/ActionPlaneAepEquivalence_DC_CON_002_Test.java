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
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DC-CON-002: Validates that {@code aep.yaml} and {@code action-plane.yaml} remain equivalent.
 *
 * <p>{@code aep.yaml} is a deprecated alias retained for historical reference only.
 * {@code action-plane.yaml} is the canonical contract. This test enforces:
 * <ol>
 *   <li>{@code aep.yaml} is marked with a DEPRECATED header comment.</li>
 *   <li>Every API path in {@code aep.yaml} exists in {@code action-plane.yaml} (no silent drift).</li>
 *   <li>Every API path in {@code action-plane.yaml} exists in {@code aep.yaml} (no silent drift).</li>
 *   <li>{@code aep.yaml} references {@code action-plane.yaml} in its deprecation notice.</li>
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
