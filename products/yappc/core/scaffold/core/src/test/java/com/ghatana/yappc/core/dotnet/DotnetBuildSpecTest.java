/*
 * Copyright (c) 2025 Ghatana Platform Contributors // GH-90000
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
    void testBuilderCreatesValidSpec() { // GH-90000
        DotnetBuildSpec spec = DotnetBuildSpec.builder() // GH-90000
                .projectName("TestProject [GH-90000]")
                .version("1.0.0 [GH-90000]")
                .targetFramework("net8.0 [GH-90000]")
                .projectType(DotnetBuildSpec.DotnetProjectType.API) // GH-90000
                .outputType(DotnetBuildSpec.DotnetOutputType.EXE) // GH-90000
                .build(); // GH-90000

        assertEquals("TestProject", spec.projectName()); // GH-90000
        assertEquals("1.0.0", spec.version()); // GH-90000
        assertEquals("net8.0", spec.targetFramework()); // GH-90000
        assertEquals(DotnetBuildSpec.DotnetProjectType.API, spec.projectType()); // GH-90000
    }

    @Test
    void testBuilderRequiresProjectName() { // GH-90000
        assertThrows(IllegalStateException.class, () -> { // GH-90000
            DotnetBuildSpec.builder() // GH-90000
                    .version("1.0.0 [GH-90000]")
                    .build(); // GH-90000
        });
    }

    @Test
    void testBuilderWithDependencies() { // GH-90000
        List<DotnetBuildSpec.DotnetDependency> deps = List.of( // GH-90000
                new DotnetBuildSpec.DotnetDependency("Swashbuckle.AspNetCore", "6.5.0")); // GH-90000

        DotnetBuildSpec spec = DotnetBuildSpec.builder() // GH-90000
                .projectName("TestProject [GH-90000]")
                .dependencies(deps) // GH-90000
                .build(); // GH-90000

        assertEquals(1, spec.dependencies().size()); // GH-90000
        assertEquals("Swashbuckle.AspNetCore", spec.dependencies().get(0).packageId()); // GH-90000
    }

    @Test
    void testDefaultFeatures() { // GH-90000
        DotnetBuildSpec.DotnetFeatures features = DotnetBuildSpec.DotnetFeatures.defaults(); // GH-90000

        assertTrue(features.enableNullable()); // GH-90000
        assertTrue(features.enableImplicitUsings()); // GH-90000
        assertTrue(features.generateDocumentation()); // GH-90000
        assertFalse(features.publishTrimmed()); // GH-90000
    }
}
