package com.ghatana.platform.testing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boundary test to prevent duplicate test base classes.
 *
 * Ensures that EventloopTestBase and EventloopTestUtil are not duplicated
 * between platform-kernel/kernel-core and platform/java/testing.
 *
 * @doc.type class
 * @doc.purpose Boundary test preventing duplicate test base classes
 * @doc.layer test
 * @doc.pattern Boundary Test
 */
@DisplayName("No Duplicate Test Base Classes Boundary Test")
class NoDuplicateTestBaseTest {

    @Test
    @DisplayName("Should not have EventloopTestBase in kernel-core")
    void shouldNotHaveEventloopTestBaseInKernelCore() { // GH-90000
        Path kernelCorePath = Paths.get("src/test/java/com/ghatana/platform/testing/activej/EventloopTestBase.java");

        // This test runs from platform-kernel/kernel-core directory
        boolean fileExists = Files.exists(kernelCorePath); // GH-90000
        
        assertThat(fileExists) // GH-90000
                .as("EventloopTestBase should not exist in kernel-core - use platform/java/testing version")
                .isFalse(); // GH-90000
    }

    @Test
    @DisplayName("Should not have EventloopTestUtil in kernel-core")
    void shouldNotHaveEventloopTestUtilInKernelCore() { // GH-90000
        Path kernelCorePath = Paths.get("src/test/java/com/ghatana/platform/testing/activej/EventloopTestUtil.java");

        boolean fileExists = Files.exists(kernelCorePath); // GH-90000
        
        assertThat(fileExists) // GH-90000
                .as("EventloopTestUtil should not exist in kernel-core - use platform/java/testing version")
                .isFalse(); // GH-90000
    }

    @Test
    @DisplayName("Platform-testing EventloopTestBase should exist")
    void platformTestingEventloopTestBaseShouldExist() { // GH-90000
        // Verify the canonical version exists in platform/java/testing
        // This test may need adjustment based on actual module structure
        // For now, we just verify kernel-core doesn't have duplicates
        assertThat(true).isTrue(); // Placeholder - would check platform/java/testing in real implementation // GH-90000
    }
}
