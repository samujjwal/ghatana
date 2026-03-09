/*
 * Copyright (c) 2025 Ghatana Platform Contributors
 */

package com.ghatana.yappc.core.cmake;

import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**

 * @doc.type class

 * @doc.purpose Handles c make build spec test operations

 * @doc.layer core

 * @doc.pattern Test

 */

class CMakeBuildSpecTest {

    @Test
    void testBuilderCreatesValidSpec() {
        CMakeBuildSpec spec = CMakeBuildSpec.builder()
                .projectName("TestProject")
                .version("1.0.0")
                .cmakeVersion("3.20")
                .cxxStandard("17")
                .projectType(CMakeBuildSpec.CMakeProjectType.EXECUTABLE)
                .build();

        assertEquals("TestProject", spec.projectName());
        assertEquals("1.0.0", spec.version());
        assertEquals("3.20", spec.cmakeVersion());
        assertEquals("17", spec.cxxStandard());
        assertEquals(CMakeBuildSpec.CMakeProjectType.EXECUTABLE, spec.projectType());
    }

    @Test
    void testBuilderRequiresProjectName() {
        assertThrows(IllegalStateException.class, () -> {
            CMakeBuildSpec.builder()
                    .version("1.0.0")
                    .build();
        });
    }

    @Test
    void testBuilderWithTargets() {
        List<CMakeBuildSpec.CMakeTarget> targets = List.of(
                new CMakeBuildSpec.CMakeTarget(
                        "main",
                        "executable",
                        List.of("src/main.cpp"),
                        List.of("include/main.h"),
                        List.of()));

        CMakeBuildSpec spec = CMakeBuildSpec.builder()
                .projectName("TestProject")
                .targets(targets)
                .build();

        assertEquals(1, spec.targets().size());
        assertEquals("main", spec.targets().get(0).name());
    }

    @Test
    void testDefaultFeatures() {
        CMakeBuildSpec.CMakeFeatures features = CMakeBuildSpec.CMakeFeatures.defaults();

        assertTrue(features.enableTesting());
        assertTrue(features.enableWarnings());
        assertFalse(features.warningsAsErrors());
        assertFalse(features.enableSanitizers());
    }
}
