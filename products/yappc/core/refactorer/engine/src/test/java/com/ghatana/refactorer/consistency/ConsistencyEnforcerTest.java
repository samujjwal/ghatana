package com.ghatana.refactorer.consistency;

import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.refactorer.shared.UnifiedDiagnostic;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;

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
    void setUp() {
        context = mock(PolyfixProjectContext.class);
        ConsistencyConfig config = ConsistencyConfig.builder()
                .withMode(ConsistencyConfig.Mode.CHECK_ONLY)
                .withFailOnError(true)
                .build();
        enforcer = new ConsistencyEnforcer(context, config, eventloop());
    }

    @Test
    void testCheckFilesWithEmptyList() {
        var diagnostics = runPromise(() -> enforcer.checkFiles(List.of()));
        assertTrue(diagnostics.isEmpty(), "Should return empty list for empty input");
    }

    @Test
    void testCheckFilesWithNonExistentFile() {
        Path nonExistentFile = tempDir.resolve("nonexistent.txt");
        var diagnostics = runPromise(() -> enforcer.checkFiles(List.of(nonExistentFile)));
        assertTrue(diagnostics.isEmpty(), "Should handle non-existent files gracefully");
    }

    @Test
    void testFixFiles() {
        assertDoesNotThrow(() -> 
            runPromise(() -> enforcer.fixFiles(List.of(tempDir.resolve("test.sh"))))
        );
    }

    @Test
    void testFormatFiles() {
        assertDoesNotThrow(() -> 
            runPromise(() -> enforcer.formatFiles(List.of(tempDir.resolve("test.sh"))))
        );
    }

    @Test
    void testFixModeEnabled() {
        ConsistencyConfig config = ConsistencyConfig.builder()
                .withMode(ConsistencyConfig.Mode.FIX)
                .build();
        assertTrue(config.isModeEnabled(ConsistencyConfig.Mode.FIX));
        assertFalse(config.isModeEnabled(ConsistencyConfig.Mode.FORMAT));
    }

    @Test
    void testMultipleModesEnabled() {
        ConsistencyConfig config = ConsistencyConfig.builder()
                .withMode(ConsistencyConfig.Mode.FIX)
                .withMode(ConsistencyConfig.Mode.FORMAT)
                .build();
        assertTrue(config.isModeEnabled(ConsistencyConfig.Mode.FIX));
        assertTrue(config.isModeEnabled(ConsistencyConfig.Mode.FORMAT));
    }
}
