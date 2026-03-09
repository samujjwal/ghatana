/*
 * Copyright (c) 2025 Ghatana Platform Contributors
 */

package com.ghatana.yappc.core.dotnet;

import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**

 * @doc.type class

 * @doc.purpose Handles dotnet build spec test operations

 * @doc.layer core

 * @doc.pattern Test

 */

class DotnetBuildSpecTest {

    @Test
    void testBuilderCreatesValidSpec() {
        DotnetBuildSpec spec = DotnetBuildSpec.builder()
                .projectName("TestProject")
                .version("1.0.0")
                .targetFramework("net8.0")
                .projectType(DotnetBuildSpec.DotnetProjectType.API)
                .outputType(DotnetBuildSpec.DotnetOutputType.EXE)
                .build();

        assertEquals("TestProject", spec.projectName());
        assertEquals("1.0.0", spec.version());
        assertEquals("net8.0", spec.targetFramework());
        assertEquals(DotnetBuildSpec.DotnetProjectType.API, spec.projectType());
    }

    @Test
    void testBuilderRequiresProjectName() {
        assertThrows(IllegalStateException.class, () -> {
            DotnetBuildSpec.builder()
                    .version("1.0.0")
                    .build();
        });
    }

    @Test
    void testBuilderWithDependencies() {
        List<DotnetBuildSpec.DotnetDependency> deps = List.of(
                new DotnetBuildSpec.DotnetDependency("Swashbuckle.AspNetCore", "6.5.0"));

        DotnetBuildSpec spec = DotnetBuildSpec.builder()
                .projectName("TestProject")
                .dependencies(deps)
                .build();

        assertEquals(1, spec.dependencies().size());
        assertEquals("Swashbuckle.AspNetCore", spec.dependencies().get(0).packageId());
    }

    @Test
    void testDefaultFeatures() {
        DotnetBuildSpec.DotnetFeatures features = DotnetBuildSpec.DotnetFeatures.defaults();

        assertTrue(features.enableNullable());
        assertTrue(features.enableImplicitUsings());
        assertTrue(features.generateDocumentation());
        assertFalse(features.publishTrimmed());
    }
}
