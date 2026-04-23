/*
 * Copyright (c) 2025 Ghatana Platform Contributors // GH-90000
 */

package com.ghatana.yappc.core.python;

import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**

 * @doc.type class

 * @doc.purpose Handles python build spec test operations

 * @doc.layer core

 * @doc.pattern Test

 */

class PythonBuildSpecTest {

    @Test
    void testBuilderCreatesValidSpec() { // GH-90000
        PythonBuildSpec spec = PythonBuildSpec.builder() // GH-90000
                .projectName("test-project")
                .version("0.1.0")
                .pythonVersion(">=3.11")
                .projectType(PythonBuildSpec.PythonProjectType.SERVICE) // GH-90000
                .buildTool(PythonBuildSpec.PythonBuildTool.UV) // GH-90000
                .build(); // GH-90000

        assertEquals("test-project", spec.projectName()); // GH-90000
        assertEquals("0.1.0", spec.version()); // GH-90000
        assertEquals(">=3.11", spec.pythonVersion()); // GH-90000
        assertEquals(PythonBuildSpec.PythonProjectType.SERVICE, spec.projectType()); // GH-90000
        assertEquals(PythonBuildSpec.PythonBuildTool.UV, spec.buildTool()); // GH-90000
    }

    @Test
    void testBuilderRequiresProjectName() { // GH-90000
        assertThrows(IllegalStateException.class, () -> { // GH-90000
            PythonBuildSpec.builder() // GH-90000
                    .version("0.1.0")
                    .build(); // GH-90000
        });
    }

    @Test
    void testBuilderWithDependencies() { // GH-90000
        List<PythonBuildSpec.PythonDependency> deps = List.of( // GH-90000
                new PythonBuildSpec.PythonDependency("fastapi", ">=0.104.0"), // GH-90000
                new PythonBuildSpec.PythonDependency("uvicorn", ">=0.24.0")); // GH-90000

        PythonBuildSpec spec = PythonBuildSpec.builder() // GH-90000
                .projectName("test-project")
                .dependencies(deps) // GH-90000
                .build(); // GH-90000

        assertEquals(2, spec.dependencies().size()); // GH-90000
        assertEquals("fastapi", spec.dependencies().get(0).name()); // GH-90000
    }

    @Test
    void testDefaultFeatures() { // GH-90000
        PythonBuildSpec.PythonFeatures features = PythonBuildSpec.PythonFeatures.defaults(); // GH-90000

        assertTrue(features.enableLinting()); // GH-90000
        assertTrue(features.enableTypeChecking()); // GH-90000
        assertTrue(features.enableTesting()); // GH-90000
    }
}
