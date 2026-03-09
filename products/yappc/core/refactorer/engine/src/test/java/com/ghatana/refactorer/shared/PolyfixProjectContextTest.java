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
    void setUp() {
        // Setup test data
        config
                = new PolyfixConfig(
                        List.of("java", "python"),
                        List.of("schema1.json"),
                        new PolyfixConfig.Budgets(5, 1000),
                        new PolyfixConfig.Policies(true, true, false, true),
                        new PolyfixConfig.Tools(
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
        mockLanguageService1 = mock(LanguageService.class);
        mockLanguageService2 = mock(LanguageService.class);

        // Setup test context
        executor = Executors.newSingleThreadExecutor();
        logger = LogManager.getLogger(PolyfixProjectContextTest.class);

        context
                = new PolyfixProjectContext(
                        tempDir,
                        config,
                        List.of(mockLanguageService1, mockLanguageService2),
                        executor,
                        logger);
    }

    @Test
    void testGetProjectRoot() {
        assertEquals(tempDir, context.getProjectRoot());
    }

    @Test
    void testGetMaxPasses() {
        assertEquals(5, context.getMaxPasses());
    }

    @Test
    void testIsDryRun() {
        assertFalse(context.isDryRun());
    }

    @Test
    void testGetSourceFiles() throws IOException {
        // Setup filesystem
        Path file1 = Files.createFile(tempDir.resolve("file1.java"));
        Path file2 = Files.createFile(tempDir.resolve("file2.py"));

        when(mockLanguageService1.supports(any(Path.class))).thenReturn(false);
        when(mockLanguageService2.supports(any(Path.class))).thenReturn(false);

        when(mockLanguageService1.supports(file1)).thenReturn(true);
        when(mockLanguageService2.supports(file2)).thenReturn(true);

        var sourceFiles = context.getSourceFiles();
        assertEquals(2, sourceFiles.size());
        assertTrue(sourceFiles.contains(file1));
        assertTrue(sourceFiles.contains(file2));
    }

    @Test
    void testGetActiveRules() {
        assertTrue(context.getActiveRules().isEmpty());
    }
}
