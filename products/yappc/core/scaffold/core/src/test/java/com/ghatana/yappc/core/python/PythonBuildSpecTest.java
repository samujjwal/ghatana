/*
 * Copyright (c) 2025 Ghatana Platform Contributors
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
    void testBuilderCreatesValidSpec() {
        PythonBuildSpec spec = PythonBuildSpec.builder()
                .projectName("test-project")
                .version("0.1.0")
                .pythonVersion(">=3.11")
                .projectType(PythonBuildSpec.PythonProjectType.SERVICE)
                .buildTool(PythonBuildSpec.PythonBuildTool.UV)
                .build();

        assertEquals("test-project", spec.projectName());
        assertEquals("0.1.0", spec.version());
        assertEquals(">=3.11", spec.pythonVersion());
        assertEquals(PythonBuildSpec.PythonProjectType.SERVICE, spec.projectType());
        assertEquals(PythonBuildSpec.PythonBuildTool.UV, spec.buildTool());
    }

    @Test
    void testBuilderRequiresProjectName() {
        assertThrows(IllegalStateException.class, () -> {
            PythonBuildSpec.builder()
                    .version("0.1.0")
                    .build();
        });
    }

    @Test
    void testBuilderWithDependencies() {
        List<PythonBuildSpec.PythonDependency> deps = List.of(
                new PythonBuildSpec.PythonDependency("fastapi", ">=0.104.0"),
                new PythonBuildSpec.PythonDependency("uvicorn", ">=0.24.0"));

        PythonBuildSpec spec = PythonBuildSpec.builder()
                .projectName("test-project")
                .dependencies(deps)
                .build();

        assertEquals(2, spec.dependencies().size());
        assertEquals("fastapi", spec.dependencies().get(0).name());
    }

    @Test
    void testDefaultFeatures() {
        PythonBuildSpec.PythonFeatures features = PythonBuildSpec.PythonFeatures.defaults();

        assertTrue(features.enableLinting());
        assertTrue(features.enableTypeChecking());
        assertTrue(features.enableTesting());
    }
}
