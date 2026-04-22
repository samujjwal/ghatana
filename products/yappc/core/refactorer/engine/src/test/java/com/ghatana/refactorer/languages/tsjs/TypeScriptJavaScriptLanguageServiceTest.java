/* Licensed under Apache-2.0 */
package com.ghatana.refactorer.languages.tsjs;

import static org.junit.jupiter.api.Assertions.*;

import com.ghatana.refactorer.shared.FixAction;
import com.ghatana.refactorer.shared.PolyfixConfig;
import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.refactorer.shared.UnifiedDiagnostic;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class) // GH-90000
/**
 * @doc.type class
 * @doc.purpose Handles type script java script language service test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class TypeScriptJavaScriptLanguageServiceTest extends EventloopTestBase {

    private TypeScriptJavaScriptLanguageService tsJsService;
    private PolyfixProjectContext projectContext;

    @TempDir Path tempDir;

    @Mock private UnifiedDiagnostic mockDiagnostic;

    @BeforeEach
    void setUpTsJsConfig() { // GH-90000
        // Initialize service with Reactor from EventloopTestBase
        tsJsService = new TypeScriptJavaScriptLanguageService(); // GH-90000

        // Create a basic configuration
        PolyfixConfig config =
                new PolyfixConfig( // GH-90000
                        List.of("typescript-javascript [GH-90000]"),
                        List.of("schemas [GH-90000]"),
                        new PolyfixConfig.Budgets(3, 20), // GH-90000
                        new PolyfixConfig.Policies(true, true, true, false), // GH-90000
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

        // Initialize project context
        projectContext =
                new PolyfixProjectContext( // GH-90000
                        tempDir, // root
                        config, // config
                        List.of(tsJsService), // languages // GH-90000
                        Executors.newSingleThreadExecutor(), // exec // GH-90000
                        LogManager.getLogger(TypeScriptJavaScriptLanguageServiceTest.class) // log // GH-90000
                        );
    }

    @Test
    void testId() { // GH-90000
        assertEquals("typescript-javascript", tsJsService.id()); // GH-90000
    }

    @Test
    void testSupportsTypeScriptJavaScriptFiles() { // GH-90000
        // TypeScript files
        assertTrue(tsJsService.supports(Path.of("test.ts [GH-90000]")));
        assertTrue(tsJsService.supports(Path.of("src/main/ts/module.ts [GH-90000]")));
        assertTrue(tsJsService.supports(Path.of("component.tsx [GH-90000]")));

        // JavaScript files
        assertTrue(tsJsService.supports(Path.of("script.js [GH-90000]")));
        assertTrue(tsJsService.supports(Path.of("src/main/js/app.js [GH-90000]")));
        assertTrue(tsJsService.supports(Path.of("component.jsx [GH-90000]")));
        assertTrue(tsJsService.supports(Path.of("module.mjs [GH-90000]")));
        assertTrue(tsJsService.supports(Path.of("common.cjs [GH-90000]")));
    }

    @Test
    void testDoesNotSupportNonTypeScriptJavaScriptFiles() { // GH-90000
        assertFalse(tsJsService.supports(Path.of("test.java [GH-90000]")), "Should not support Java files");
        assertFalse( // GH-90000
                tsJsService.supports(Path.of("package.json [GH-90000]")), "Should not support package.json");
        assertFalse( // GH-90000
                tsJsService.supports(Path.of("tsconfig.json [GH-90000]")), "Should not support tsconfig.json");
    }

    @Test
    void testDiagnoseEmptyProject() { // GH-90000
        List<UnifiedDiagnostic> diagnostics = runPromise( // GH-90000
                () -> tsJsService.diagnose(projectContext, List.of())); // GH-90000
        assertNotNull(diagnostics); // GH-90000
        assertTrue(diagnostics.isEmpty()); // GH-90000
    }

    @Test
    void testDiagnoseValidTypeScriptFile(@TempDir Path tempDir) throws Exception { // GH-90000
        // Create a simple TypeScript file
        Path tsFile = tempDir.resolve("test.ts [GH-90000]");
        Files.writeString( // GH-90000
                tsFile, "const message: string = 'Hello, TypeScript!';\nconsole.log(message);"); // GH-90000

        List<UnifiedDiagnostic> diagnostics = runPromise( // GH-90000
                () -> tsJsService.diagnose(projectContext, List.of(tsFile))); // GH-90000
        assertNotNull(diagnostics); // GH-90000
        // Should not have any diagnostics for a valid file in this basic implementation
        assertTrue(diagnostics.isEmpty()); // GH-90000
    }

    @Test
    void testDiagnoseEmptyFile(@TempDir Path tempDir) throws Exception { // GH-90000
        // Create an empty TypeScript file
        Path emptyFile = tempDir.resolve("empty.ts [GH-90000]");
        Files.createFile(emptyFile); // GH-90000

        List<UnifiedDiagnostic> diagnostics = runPromise( // GH-90000
                () -> tsJsService.diagnose(projectContext, List.of(emptyFile))); // GH-90000
        assertNotNull(diagnostics); // GH-90000
        assertEquals(1, diagnostics.size()); // GH-90000

        UnifiedDiagnostic diagnostic = diagnostics.get(0); // GH-90000
        assertEquals("Empty TypeScript/JavaScript source file", diagnostic.message()); // GH-90000
        assertEquals("tsjs.empty_file", diagnostic.rule()); // GH-90000
        assertEquals(emptyFile.toString(), diagnostic.file()); // GH-90000
    }

    @Test
    void testPlanFixes() { // GH-90000
        // Test that planFixes returns an empty list by default
        List<FixAction> fixes = runPromise( // GH-90000
                () -> tsJsService.planFixes(mockDiagnostic, projectContext)); // GH-90000
        assertNotNull(fixes); // GH-90000
        assertTrue(fixes.isEmpty()); // GH-90000
    }
}
