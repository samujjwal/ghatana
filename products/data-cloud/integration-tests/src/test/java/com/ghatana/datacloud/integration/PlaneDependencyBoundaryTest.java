package com.ghatana.datacloud.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Data Cloud plane dependency boundaries")
class PlaneDependencyBoundaryTest {

    private static final Path DATA_CLOUD_ROOT = Path.of("products", "data-cloud");

    @Test
    @DisplayName("foundational planes do not depend on Action Plane Gradle modules")
    void foundationalPlanesDoNotDependOnActionPlaneModules() throws IOException {
        List<String> restrictedPlanes = List.of("data", "event", "context", "governance", "operations");

        for (String plane : restrictedPlanes) {
            Path planeRoot = DATA_CLOUD_ROOT.resolve("planes").resolve(plane);
            if (!Files.exists(planeRoot)) {
                continue;
            }

            assertThat(filesContaining(planeRoot, ":products:data-cloud:planes:action"))
                .as("Plane %s must not import Action Plane implementation modules", plane)
                .isEmpty();
        }
    }

    @Test
    @DisplayName("extensions do not depend on launcher internals")
    void extensionsDoNotDependOnLauncherInternals() throws IOException {
        Path extensionsRoot = DATA_CLOUD_ROOT.resolve("extensions");

        assertThat(filesContaining(extensionsRoot, ":products:data-cloud:delivery:launcher"))
            .as("Extensions must not depend on launcher internals")
            .isEmpty();
        assertThat(filesContaining(extensionsRoot, ":products:data-cloud:delivery:runtime-composition"))
            .as("Extensions must not depend on runtime composition internals")
            .isEmpty();
    }

    @Test
    @DisplayName("contracts do not depend on implementation modules")
    void contractsDoNotDependOnImplementationModules() throws IOException {
        Path contractsRoot = DATA_CLOUD_ROOT.resolve("contracts");

        assertThat(filesContaining(contractsRoot, ":products:data-cloud:planes:"))
            .as("Contracts must not depend on implementation planes")
            .isEmpty();
        assertThat(filesContaining(contractsRoot, ":products:data-cloud:delivery:"))
            .as("Contracts must not depend on delivery modules")
            .isEmpty();
    }

    private static List<Path> filesContaining(Path root, String needle) throws IOException {
        if (!Files.exists(root)) {
            return List.of();
        }

        try (Stream<Path> paths = Files.walk(root)) {
            return paths
                .filter(Files::isRegularFile)
                .filter(path -> isTextFile(path) && contains(path, needle))
                .toList();
        }
    }

    private static boolean isTextFile(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.endsWith(".gradle.kts")
            || fileName.endsWith(".md")
            || fileName.endsWith(".yaml")
            || fileName.endsWith(".yml")
            || fileName.endsWith(".java")
            || fileName.endsWith(".kt")
            || fileName.endsWith(".json");
    }

    private static boolean contains(Path path, String needle) {
        try {
            return Files.readString(path).contains(needle);
        } catch (IOException ignored) {
            return false;
        }
    }
}
