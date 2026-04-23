/*
 * Copyright (c) 2025 Ghatana Platform Contributors // GH-90000
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
    void testBuilderCreatesValidSpec() { // GH-90000
        CMakeBuildSpec spec = CMakeBuildSpec.builder() // GH-90000
                .projectName("TestProject")
                .version("1.0.0")
                .cmakeVersion("3.20")
                .cxxStandard("17")
                .projectType(CMakeBuildSpec.CMakeProjectType.EXECUTABLE) // GH-90000
                .build(); // GH-90000

        assertEquals("TestProject", spec.projectName()); // GH-90000
        assertEquals("1.0.0", spec.version()); // GH-90000
        assertEquals("3.20", spec.cmakeVersion()); // GH-90000
        assertEquals("17", spec.cxxStandard()); // GH-90000
        assertEquals(CMakeBuildSpec.CMakeProjectType.EXECUTABLE, spec.projectType()); // GH-90000
    }

    @Test
    void testBuilderRequiresProjectName() { // GH-90000
        assertThrows(IllegalStateException.class, () -> { // GH-90000
            CMakeBuildSpec.builder() // GH-90000
                    .version("1.0.0")
                    .build(); // GH-90000
        });
    }

    @Test
    void testBuilderWithTargets() { // GH-90000
        List<CMakeBuildSpec.CMakeTarget> targets = List.of( // GH-90000
                new CMakeBuildSpec.CMakeTarget( // GH-90000
                        "main",
                        "executable",
                        List.of("src/main.cpp"),
                        List.of("include/main.h"),
                        List.of())); // GH-90000

        CMakeBuildSpec spec = CMakeBuildSpec.builder() // GH-90000
                .projectName("TestProject")
                .targets(targets) // GH-90000
                .build(); // GH-90000

        assertEquals(1, spec.targets().size()); // GH-90000
        assertEquals("main", spec.targets().get(0).name()); // GH-90000
    }

    @Test
    void testDefaultFeatures() { // GH-90000
        CMakeBuildSpec.CMakeFeatures features = CMakeBuildSpec.CMakeFeatures.defaults(); // GH-90000

        assertTrue(features.enableTesting()); // GH-90000
        assertTrue(features.enableWarnings()); // GH-90000
        assertFalse(features.warningsAsErrors()); // GH-90000
        assertFalse(features.enableSanitizers()); // GH-90000
    }
}
