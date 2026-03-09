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

@ExtendWith(MockitoExtension.class)
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
    void setUpTsJsConfig() {
        // Initialize service with Reactor from EventloopTestBase
        tsJsService = new TypeScriptJavaScriptLanguageService();
        
        // Create a basic configuration
        PolyfixConfig config =
                new PolyfixConfig(
                        List.of("typescript-javascript"),
                        List.of("schemas"),
                        new PolyfixConfig.Budgets(3, 20),
                        new PolyfixConfig.Policies(true, true, true, false),
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

        // Initialize project context
        projectContext =
                new PolyfixProjectContext(
                        tempDir, // root
                        config, // config
                        List.of(tsJsService), // languages
                        Executors.newSingleThreadExecutor(), // exec
                        LogManager.getLogger(TypeScriptJavaScriptLanguageServiceTest.class) // log
                        );
    }

    @Test
    void testId() {
        assertEquals("typescript-javascript", tsJsService.id());
    }

    @Test
    void testSupportsTypeScriptJavaScriptFiles() {
        // TypeScript files
        assertTrue(tsJsService.supports(Path.of("test.ts")));
        assertTrue(tsJsService.supports(Path.of("src/main/ts/module.ts")));
        assertTrue(tsJsService.supports(Path.of("component.tsx")));

        // JavaScript files
        assertTrue(tsJsService.supports(Path.of("script.js")));
        assertTrue(tsJsService.supports(Path.of("src/main/js/app.js")));
        assertTrue(tsJsService.supports(Path.of("component.jsx")));
        assertTrue(tsJsService.supports(Path.of("module.mjs")));
        assertTrue(tsJsService.supports(Path.of("common.cjs")));
    }

    @Test
    void testDoesNotSupportNonTypeScriptJavaScriptFiles() {
        assertFalse(tsJsService.supports(Path.of("test.java")), "Should not support Java files");
        assertFalse(
                tsJsService.supports(Path.of("package.json")), "Should not support package.json");
        assertFalse(
                tsJsService.supports(Path.of("tsconfig.json")), "Should not support tsconfig.json");
    }

    @Test
    void testDiagnoseEmptyProject() {
        List<UnifiedDiagnostic> diagnostics = runPromise(
                () -> tsJsService.diagnose(projectContext, List.of()));
        assertNotNull(diagnostics);
        assertTrue(diagnostics.isEmpty());
    }

    @Test
    void testDiagnoseValidTypeScriptFile(@TempDir Path tempDir) throws Exception {
        // Create a simple TypeScript file
        Path tsFile = tempDir.resolve("test.ts");
        Files.writeString(
                tsFile, "const message: string = 'Hello, TypeScript!';\nconsole.log(message);");

        List<UnifiedDiagnostic> diagnostics = runPromise(
                () -> tsJsService.diagnose(projectContext, List.of(tsFile)));
        assertNotNull(diagnostics);
        // Should not have any diagnostics for a valid file in this basic implementation
        assertTrue(diagnostics.isEmpty());
    }

    @Test
    void testDiagnoseEmptyFile(@TempDir Path tempDir) throws Exception {
        // Create an empty TypeScript file
        Path emptyFile = tempDir.resolve("empty.ts");
        Files.createFile(emptyFile);

        List<UnifiedDiagnostic> diagnostics = runPromise(
                () -> tsJsService.diagnose(projectContext, List.of(emptyFile)));
        assertNotNull(diagnostics);
        assertEquals(1, diagnostics.size());

        UnifiedDiagnostic diagnostic = diagnostics.get(0);
        assertEquals("Empty TypeScript/JavaScript source file", diagnostic.message());
        assertEquals("tsjs.empty_file", diagnostic.rule());
        assertEquals(emptyFile.toString(), diagnostic.file());
    }

    @Test
    void testPlanFixes() {
        // Test that planFixes returns an empty list by default
        List<FixAction> fixes = runPromise(
                () -> tsJsService.planFixes(mockDiagnostic, projectContext));
        assertNotNull(fixes);
        assertTrue(fixes.isEmpty());
    }
}
