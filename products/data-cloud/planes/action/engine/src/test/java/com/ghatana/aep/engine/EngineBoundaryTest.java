/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boundary test to ensure the engine module does not depend on Data Cloud
 * durable registry implementation classes.
 *
 * The engine must only depend on platform SPI contracts (AgentRegistry,
 * AgentLogicProviderRegistry) and runtime composition should wire in
 * the durable registry implementation at the orchestrator layer.
 *
 * @doc.type class
 * @doc.purpose Verify engine module boundary compliance
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Engine Boundary Tests")
class EngineBoundaryTest {

    @Test
    @DisplayName("Engine should not import Data Cloud implementation packages")
    void shouldNotImportDataCloudImplementationPackages() throws Exception {
        Path engineSourceDir = Path.of("src/main/java/com/ghatana/aep");
        
        List<String> forbiddenImports = List.of(
            "com.ghatana.datacloud.agent.registry",
            "com.ghatana.datacloud.agent.mastery",
            "com.ghatana.datacloud.agent.learning",
            "com.ghatana.datacloud.agent.evaluation",
            "com.ghatana.datacloud.agent.obsolescence"
        );

        Files.walk(engineSourceDir)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(javaFile -> {
                try {
                    String content = Files.readString(javaFile);
                    for (String forbidden : forbiddenImports) {
                        assertThat(content)
                            .as("File %s should not import forbidden package %s", 
                                javaFile.getFileName(), forbidden)
                            .doesNotContain(forbidden);
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Failed to read file: " + javaFile, e);
                }
            });
    }

    @Test
    @DisplayName("Engine should only use platform SPI contracts for registry")
    void shouldOnlyUsePlatformSpiContracts() throws Exception {
        Path engineSourceDir = Path.of("src/main/java/com/ghatana/aep");
        
        // Allowed SPI contracts
        List<String> allowedSpiImports = List.of(
            "com.ghatana.agent.spi.AgentRegistry",
            "com.ghatana.agent.spi.AgentLogicProviderRegistry",
            "com.ghatana.agent.catalog.CatalogRegistry"
        );

        Files.walk(engineSourceDir)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(javaFile -> {
                try {
                    String content = Files.readString(javaFile);
                    // If the file uses any registry import, it must be from allowed SPI
                    if (content.contains("Registry") && content.contains("import")) {
                        boolean usesAllowedSpi = allowedSpiImports.stream()
                            .anyMatch(allowed -> content.contains(allowed));
                        
                        assertThat(usesAllowedSpi)
                            .as("File %s should only use allowed SPI registry contracts", 
                                javaFile.getFileName())
                            .isTrue();
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Failed to read file: " + javaFile, e);
                }
            });
    }
}
