/* Licensed under Apache-2.0 */
package com.ghatana.refactorer.languages.tsjs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

import com.ghatana.refactorer.languages.tsjs.eslint.ESLintService;
import com.ghatana.refactorer.shared.PolyfixConfig;
import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.refactorer.shared.UnifiedDiagnostic;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
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
 * @doc.purpose Handles type script java script language service es lint test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class TypeScriptJavaScriptLanguageServiceESLintTest extends EventloopTestBase {

    @TempDir Path tempDir;

    private PolyfixProjectContext projectContext;

    @Mock private ESLintService mockESLintService;

    private TypeScriptJavaScriptLanguageService tsJsService;
    private Path testFile;

    private PolyfixConfig testConfig;

    @BeforeEach
    void setUp() throws IOException { // GH-90000
        // Create a simple test file
        testFile = tempDir.resolve("sample.ts");
        Files.createDirectories(testFile.getParent()); // GH-90000

        // Create test config
        testConfig = createTestConfig(); // GH-90000

        projectContext =
                new PolyfixProjectContext( // GH-90000
                        tempDir, testConfig, List.of(), null, LogManager.getLogger("test"));

        // Create the service with our mock ESLintService and reactor
        tsJsService = new TypeScriptJavaScriptLanguageService(projectContext, mockESLintService, eventloop()); // GH-90000
    }

    private PolyfixConfig createTestConfig() { // GH-90000
        return new PolyfixConfig( // GH-90000
                List.of("typescript", "javascript"), // GH-90000
                List.of(), // GH-90000
                new PolyfixConfig.Budgets(100, 1000), // GH-90000
                new PolyfixConfig.Policies(true, true, true, true), // GH-90000
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
    }

    @Test
    void testAnalyzeWithESLint() throws IOException { // GH-90000
        // Setup test file with content
        String content = "let x: number = 'hello';\nconsole.log(x);"; // GH-90000
        Files.writeString(testFile, content); // GH-90000

        // Create a mock diagnostic
        UnifiedDiagnostic mockDiagnostic = mock(UnifiedDiagnostic.class); // GH-90000
        when(mockDiagnostic.rule()).thenReturn("test-rule");

        // Configure the mock ESLint service to return our mock diagnostic
        when(mockESLintService.analyze( // GH-90000
                        argThat( // GH-90000
                                files ->
                                        files != null
                                                && files.size() == 1 // GH-90000
                                                && files.get(0).equals(testFile)))) // GH-90000
                .thenReturn(Collections.singletonList(mockDiagnostic)); // GH-90000

        // Execute the test
        List<UnifiedDiagnostic> diagnostics = runPromise( // GH-90000
                () -> tsJsService.diagnose(projectContext, Collections.singletonList(testFile))); // GH-90000

        // Verify the results
        assertThat(diagnostics) // GH-90000
                .as("Should contain the mock diagnostic from ESLint")
                .isNotEmpty() // GH-90000
                .anySatisfy(diag -> assertThat(diag.rule()).isEqualTo("test-rule"));

        // Verify the mock was called with the correct arguments
        verify(mockESLintService) // GH-90000
                .analyze( // GH-90000
                        argThat( // GH-90000
                                files ->
                                        files != null
                                                && files.size() == 1 // GH-90000
                                                && files.get(0).equals(testFile))); // GH-90000
    }

    @Test
    void testDetectsESLintIssues() throws IOException { // GH-90000
        // Setup test file with content that would trigger an ESLint rule
        Files.writeString(testFile, "const unused = 42;"); // GH-90000

        // Create a mock diagnostic for the ESLint rule
        UnifiedDiagnostic mockDiagnostic = mock(UnifiedDiagnostic.class); // GH-90000
        when(mockDiagnostic.rule()).thenReturn("@typescript-eslint/no-unused-vars");

        // Configure the mock ESLint service to return our mock diagnostic
        when(mockESLintService.analyze(anyList())) // GH-90000
                .thenReturn(Collections.singletonList(mockDiagnostic)); // GH-90000

        // Execute the test
        List<UnifiedDiagnostic> diagnostics = runPromise( // GH-90000
                () -> tsJsService.diagnose(projectContext, Collections.singletonList(testFile))); // GH-90000

        // Verify the results
        assertThat(diagnostics) // GH-90000
                .as("Should detect ESLint issues")
                .isNotEmpty() // GH-90000
                .extracting(UnifiedDiagnostic::rule) // GH-90000
                .contains("@typescript-eslint/no-unused-vars");

        // Verify the mock was called with the correct arguments
        verify(mockESLintService) // GH-90000
                .analyze( // GH-90000
                        argThat( // GH-90000
                                files ->
                                        files != null
                                                && files.size() == 1 // GH-90000
                                                && files.get(0).equals(testFile))); // GH-90000
    }

    @Test
    void testNoFalsePositivesOnValidCode() throws IOException { // GH-90000
        // Setup a valid test file
        Path validFile = tempDir.resolve("valid.ts");
        Files.writeString(validFile, "const x: number = 42;\nconsole.log(x);"); // GH-90000

        // Configure the mock ESLint service to return no issues
        when(mockESLintService.analyze(anyList())).thenReturn(Collections.emptyList()); // GH-90000

        // Execute the test
        List<UnifiedDiagnostic> diagnostics = runPromise( // GH-90000
                () -> tsJsService.diagnose(projectContext, Collections.singletonList(validFile))); // GH-90000

        // Verify no issues were found
        assertThat(diagnostics).as("Should not report issues on valid code").isEmpty();

        // Verify the mock was called with the correct arguments
        verify(mockESLintService) // GH-90000
                .analyze( // GH-90000
                        argThat( // GH-90000
                                files ->
                                        files != null
                                                && files.size() == 1 // GH-90000
                                                && files.get(0).equals(validFile))); // GH-90000
    }

    @Test
    void testHandlesMissingESLintGracefully() throws IOException { // GH-90000
        // Setup test file with content
        Files.writeString(testFile, "const x = 42;"); // GH-90000

        // Create a new service instance with a null ESLintService to simulate initialization
        // failure
        TypeScriptJavaScriptLanguageService serviceWithNoESLint =
                new TypeScriptJavaScriptLanguageService(projectContext, null, eventloop()); // GH-90000

        // Execute the test - should not throw an exception
        List<UnifiedDiagnostic> diagnostics = runPromise( // GH-90000
                () -> serviceWithNoESLint.diagnose(projectContext, Collections.singletonList(testFile))); // GH-90000

        // Verify that no diagnostics were returned (graceful degradation) // GH-90000
        assertThat(diagnostics).as("Should handle missing ESLint gracefully").isEmpty();
    }
}
