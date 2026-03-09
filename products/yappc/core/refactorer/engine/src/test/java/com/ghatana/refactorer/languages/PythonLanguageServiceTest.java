/* Licensed under Apache-2.0 */
package com.ghatana.refactorer.languages;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.ghatana.refactorer.shared.FixAction;
import com.ghatana.refactorer.shared.PolyfixConfig;
import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.platform.domain.domain.Severity;
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

@ExtendWith(MockitoExtension.class)
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
    void setUpPythonConfig() {
        // Initialize service with Reactor from EventloopTestBase
        pythonService = new PythonLanguageService(eventloop());
        
        // Update config to include Python
        projectContext
                = new PolyfixProjectContext(
                        tempDir, // root
                        new PolyfixConfig(
                                List.of("python"),
                                projectContext.config().schemaPaths(),
                                projectContext.config().budgets(),
                                projectContext.config().policies(),
                                projectContext.config().tools()),
                        projectContext.languages(), // languages
                        projectContext.exec(), // exec
                        projectContext.log() // log
                );
    }

    @Test
    void testId() {
        assertEquals("python", pythonService.id());
    }

    @Test
    void testSupportsPythonFile() {
        assertTrue(pythonService.supports(Path.of("test.py")));
        assertTrue(pythonService.supports(Path.of("src/main/python/module.py")));
        assertTrue(pythonService.supports(Path.of("script.pyi")));
        assertTrue(pythonService.supports(Path.of("gui.pyw")));
    }

    @Test
    void testDoesNotSupportNonPythonFiles() {
        assertFalse(pythonService.supports(Path.of("test.java")), "Should not support Java files");
        assertFalse(
                pythonService.supports(Path.of("requirements.txt")),
                "Should not support requirements.txt");
        assertTrue(
                pythonService.supports(Path.of("setup.py")),
                "Should support setup.py as it's a Python file");
    }

    @Test
    void testDiagnoseEmptyProject() {
        List<UnifiedDiagnostic> diagnostics = runPromise(
                () -> pythonService.diagnose(projectContext, List.of()));
        assertNotNull(diagnostics);
        assertTrue(diagnostics.isEmpty());
    }

    @Test
    void testDiagnoseValidPythonFile(@TempDir Path tempDir) throws Exception {
        // Create a simple valid Python file
        Path pythonFile = tempDir.resolve("test.py");
        Files.writeString(
                pythonFile,
                """
            def hello():
                print(\"Hello, World!\")

            if __name__ == \"__main__\":
                hello()
            """);

        List<UnifiedDiagnostic> diagnostics = runPromise(
                () -> pythonService.diagnose(projectContext, List.of(pythonFile)));
        assertNotNull(diagnostics);
        // No errors expected in valid Python code
        assertTrue(diagnostics.isEmpty());
    }

    @Test
    void testPlanFixes() {
        // Create a test diagnostic with required fields
        UnifiedDiagnostic diagnostic
                = new UnifiedDiagnostic(
                        "test-tool", // tool
                        "test-rule", // rule
                        "Test message", // message
                        "test.py", // file
                        1, // startLine
                        1, // startColumn
                        Severity.ERROR, // severity
                        Map.of() // meta
                );

        List<FixAction> fixes = runPromise(
                () -> pythonService.planFixes(diagnostic, projectContext));
        assertNotNull(fixes);
        // TODO: Add more specific assertions once fix planning is implemented
    }
}
