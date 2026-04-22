/* Licensed under Apache-2.0 */
package com.ghatana.refactorer.languages;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.ghatana.refactorer.shared.FixAction;
import com.ghatana.refactorer.shared.PolyfixConfig;
import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.platform.domain.Severity;
import com.ghatana.refactorer.shared.UnifiedDiagnostic;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class) // GH-90000
/**
 * @doc.type class
 * @doc.purpose Handles python language service test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class PythonLanguageServiceTest extends AbstractLanguageTest {

    private PythonLanguageService pythonService;

    @Mock
    private UnifiedDiagnostic mockDiagnostic;

    @BeforeEach
    void setUpPythonConfig() { // GH-90000
        // Initialize service with Reactor from EventloopTestBase
        pythonService = new PythonLanguageService(eventloop()); // GH-90000

        // Update config to include Python
        projectContext
                = new PolyfixProjectContext( // GH-90000
                        tempDir, // root
                        new PolyfixConfig( // GH-90000
                                List.of("python [GH-90000]"),
                                projectContext.config().schemaPaths(), // GH-90000
                                projectContext.config().budgets(), // GH-90000
                                projectContext.config().policies(), // GH-90000
                                projectContext.config().tools()), // GH-90000
                        projectContext.languages(), // languages // GH-90000
                        projectContext.exec(), // exec // GH-90000
                        projectContext.log() // log // GH-90000
                );
    }

    @Test
    void testId() { // GH-90000
        assertEquals("python", pythonService.id()); // GH-90000
    }

    @Test
    void testSupportsPythonFile() { // GH-90000
        assertTrue(pythonService.supports(Path.of("test.py [GH-90000]")));
        assertTrue(pythonService.supports(Path.of("src/main/python/module.py [GH-90000]")));
        assertTrue(pythonService.supports(Path.of("script.pyi [GH-90000]")));
        assertTrue(pythonService.supports(Path.of("gui.pyw [GH-90000]")));
    }

    @Test
    void testDoesNotSupportNonPythonFiles() { // GH-90000
        assertFalse(pythonService.supports(Path.of("test.java [GH-90000]")), "Should not support Java files");
        assertFalse( // GH-90000
                pythonService.supports(Path.of("requirements.txt [GH-90000]")),
                "Should not support requirements.txt");
        assertTrue( // GH-90000
                pythonService.supports(Path.of("setup.py [GH-90000]")),
                "Should support setup.py as it's a Python file");
    }

    @Test
    void testDiagnoseEmptyProject() { // GH-90000
        List<UnifiedDiagnostic> diagnostics = runPromise( // GH-90000
                () -> pythonService.diagnose(projectContext, List.of())); // GH-90000
        assertNotNull(diagnostics); // GH-90000
        assertTrue(diagnostics.isEmpty()); // GH-90000
    }

    @Test
    void testDiagnoseValidPythonFile(@TempDir Path tempDir) throws Exception { // GH-90000
        // Create a simple valid Python file
        Path pythonFile = tempDir.resolve("test.py [GH-90000]");
        Files.writeString( // GH-90000
                pythonFile,
                """
            def hello(): // GH-90000
                print(\"Hello, World!\") // GH-90000

            if __name__ == \"__main__\":
                hello() // GH-90000
            """);

        List<UnifiedDiagnostic> diagnostics = runPromise( // GH-90000
                () -> pythonService.diagnose(projectContext, List.of(pythonFile))); // GH-90000
        assertNotNull(diagnostics); // GH-90000
        // No errors expected in valid Python code
        assertTrue(diagnostics.isEmpty()); // GH-90000
    }

    @Test
    void testPlanFixes() { // GH-90000
        // Create a test diagnostic with required fields
        UnifiedDiagnostic diagnostic
                = new UnifiedDiagnostic( // GH-90000
                        "test-tool", // tool
                        "test-rule", // rule
                        "Test message", // message
                        "test.py", // file
                        1, // startLine
                        1, // startColumn
                        Severity.ERROR, // severity
                        Map.of() // meta // GH-90000
                );

        List<FixAction> fixes = runPromise( // GH-90000
                () -> pythonService.planFixes(diagnostic, projectContext)); // GH-90000
        assertNotNull(fixes); // GH-90000
        // Fix planning may return empty list if no fixes are available
        // This test verifies the method completes successfully
        assertTrue(fixes != null); // GH-90000
    }
}
