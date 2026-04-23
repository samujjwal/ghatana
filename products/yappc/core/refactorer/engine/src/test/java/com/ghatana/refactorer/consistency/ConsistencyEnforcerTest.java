package com.ghatana.refactorer.consistency;

import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**

 * @doc.type class

 * @doc.purpose Handles consistency enforcer test operations

 * @doc.layer core

 * @doc.pattern Test

 */

class ConsistencyEnforcerTest extends EventloopTestBase {
    @TempDir
    Path tempDir;
    private ConsistencyEnforcer enforcer;
    private PolyfixProjectContext context;

    @BeforeEach
    void setUp() { // GH-90000
        context = mock(PolyfixProjectContext.class); // GH-90000
        ConsistencyConfig config = ConsistencyConfig.builder() // GH-90000
                .withMode(ConsistencyConfig.Mode.CHECK_ONLY) // GH-90000
                .withFailOnError(true) // GH-90000
                .build(); // GH-90000
        enforcer = new ConsistencyEnforcer(context, config, eventloop()); // GH-90000
    }

    @Test
    void testCheckFilesWithEmptyList() { // GH-90000
        var diagnostics = runPromise(() -> enforcer.checkFiles(List.of())); // GH-90000
        assertTrue(diagnostics.isEmpty(), "Should return empty list for empty input"); // GH-90000
    }

    @Test
    void testCheckFilesWithNonExistentFile() { // GH-90000
        Path nonExistentFile = tempDir.resolve("nonexistent.txt");
        var diagnostics = runPromise(() -> enforcer.checkFiles(List.of(nonExistentFile))); // GH-90000
        assertTrue(diagnostics.isEmpty(), "Should handle non-existent files gracefully"); // GH-90000
    }

    @Test
    void testFixFiles() { // GH-90000
        assertDoesNotThrow(() -> // GH-90000
            runPromise(() -> enforcer.fixFiles(List.of(tempDir.resolve("test.sh"))))
        );
    }

    @Test
    void testFormatFiles() { // GH-90000
        assertDoesNotThrow(() -> // GH-90000
            runPromise(() -> enforcer.formatFiles(List.of(tempDir.resolve("test.sh"))))
        );
    }

    @Test
    void testFixModeEnabled() { // GH-90000
        ConsistencyConfig config = ConsistencyConfig.builder() // GH-90000
                .withMode(ConsistencyConfig.Mode.FIX) // GH-90000
                .build(); // GH-90000
        assertTrue(config.isModeEnabled(ConsistencyConfig.Mode.FIX)); // GH-90000
        assertFalse(config.isModeEnabled(ConsistencyConfig.Mode.FORMAT)); // GH-90000
    }

    @Test
    void testMultipleModesEnabled() { // GH-90000
        ConsistencyConfig config = ConsistencyConfig.builder() // GH-90000
                .withMode(ConsistencyConfig.Mode.FIX) // GH-90000
                .withMode(ConsistencyConfig.Mode.FORMAT) // GH-90000
                .build(); // GH-90000
        assertTrue(config.isModeEnabled(ConsistencyConfig.Mode.FIX)); // GH-90000
        assertTrue(config.isModeEnabled(ConsistencyConfig.Mode.FORMAT)); // GH-90000
    }
}
