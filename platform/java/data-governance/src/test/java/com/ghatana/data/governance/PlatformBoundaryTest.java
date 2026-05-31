/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.data.governance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for platform boundary enforcement (Pass 10 boundary cleanup).
 *
 * @doc.type class
 * @doc.purpose Validate data-governance does not import product packages
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Data-Governance Platform Boundary Tests")
class PlatformBoundaryTest {

    @Test
    @DisplayName("Should not import com.ghatana.products packages")
    void shouldNotImportProductPackages() throws Exception {
        Path srcDir = Paths.get("src/main/java");
        List<Path> javaFiles = Files.walk(srcDir)
            .filter(p -> p.toString().endsWith(".java"))
            .toList();

        for (Path javaFile : javaFiles) {
            String content = Files.readString(javaFile);
            assertThat(content)
                .doesNotContain("import com.ghatana.products.")
                .as("File %s should not import product packages", javaFile);
        }
    }

    @Test
    @DisplayName("Should only depend on platform modules")
    void shouldOnlyDependOnPlatformModules() throws Exception {
        Path buildFile = Paths.get("build.gradle.kts");
        String content = Files.readString(buildFile);

        assertThat(content)
            .contains("project(\":platform:java:core\")")
            .contains("project(\":platform:java:domain\")")
            .contains("project(\":platform:java:governance\")")
            .contains("project(\":platform:java:database\")")
            .doesNotContain("project(\":products:")
            .as("Data-governance should only depend on platform modules");
    }
}
