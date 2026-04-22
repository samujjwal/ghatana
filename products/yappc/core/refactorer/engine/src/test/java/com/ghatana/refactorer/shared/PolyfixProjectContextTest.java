/* Licensed under Apache-2.0 */
package com.ghatana.refactorer.shared;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ghatana.refactorer.shared.service.LanguageService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**

 * @doc.type class

 * @doc.purpose Handles polyfix project context test operations

 * @doc.layer core

 * @doc.pattern Test

 */

class PolyfixProjectContextTest {

    @TempDir
    Path tempDir;

    private PolyfixProjectContext context;
    private LanguageService mockLanguageService1;
    private LanguageService mockLanguageService2;
    private PolyfixConfig config;
    private ExecutorService executor;
    private Logger logger;

    @BeforeEach
    void setUp() { // GH-90000
        // Setup test data
        config
                = new PolyfixConfig( // GH-90000
                        List.of("java", "python"), // GH-90000
                        List.of("schema1.json [GH-90000]"),
                        new PolyfixConfig.Budgets(5, 1000), // GH-90000
                        new PolyfixConfig.Policies(true, true, false, true), // GH-90000
                        new PolyfixConfig.Tools( // GH-90000
                                "node",
                                "eslint",
                                "tsc",
                                "prettier",
                                "ruff",
                                "black",
                                "mypy",
                                "shellcheck",
                                "shfmt",
                                "cargo",
                                "rustfmt",
                                "semgrep"));

        // Setup mock language services
        mockLanguageService1 = mock(LanguageService.class); // GH-90000
        mockLanguageService2 = mock(LanguageService.class); // GH-90000

        // Setup test context
        executor = Executors.newSingleThreadExecutor(); // GH-90000
        logger = LogManager.getLogger(PolyfixProjectContextTest.class); // GH-90000

        context
                = new PolyfixProjectContext( // GH-90000
                        tempDir,
                        config,
                        List.of(mockLanguageService1, mockLanguageService2), // GH-90000
                        executor,
                        logger);
    }

    @Test
    void testGetProjectRoot() { // GH-90000
        assertEquals(tempDir, context.getProjectRoot()); // GH-90000
    }

    @Test
    void testGetMaxPasses() { // GH-90000
        assertEquals(5, context.getMaxPasses()); // GH-90000
    }

    @Test
    void testIsDryRun() { // GH-90000
        assertFalse(context.isDryRun()); // GH-90000
    }

    @Test
    void testGetSourceFiles() throws IOException { // GH-90000
        // Setup filesystem
        Path file1 = Files.createFile(tempDir.resolve("file1.java [GH-90000]"));
        Path file2 = Files.createFile(tempDir.resolve("file2.py [GH-90000]"));

        when(mockLanguageService1.supports(any(Path.class))).thenReturn(false); // GH-90000
        when(mockLanguageService2.supports(any(Path.class))).thenReturn(false); // GH-90000

        when(mockLanguageService1.supports(file1)).thenReturn(true); // GH-90000
        when(mockLanguageService2.supports(file2)).thenReturn(true); // GH-90000

        var sourceFiles = context.getSourceFiles(); // GH-90000
        assertEquals(2, sourceFiles.size()); // GH-90000
        assertTrue(sourceFiles.contains(file1)); // GH-90000
        assertTrue(sourceFiles.contains(file2)); // GH-90000
    }

    @Test
    void testGetActiveRules() { // GH-90000
        assertTrue(context.getActiveRules().isEmpty()); // GH-90000
    }
}
