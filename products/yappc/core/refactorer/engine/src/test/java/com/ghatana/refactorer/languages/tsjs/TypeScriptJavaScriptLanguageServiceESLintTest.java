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

@ExtendWith(MockitoExtension.class)
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
    void setUp() throws IOException {
        // Create a simple test file
        testFile = tempDir.resolve("sample.ts");
        Files.createDirectories(testFile.getParent());

        // Create test config
        testConfig = createTestConfig();

        projectContext =
                new PolyfixProjectContext(
                        tempDir, testConfig, List.of(), null, LogManager.getLogger("test"));

        // Create the service with our mock ESLintService and reactor
        tsJsService = new TypeScriptJavaScriptLanguageService(projectContext, mockESLintService, eventloop());
    }

    private PolyfixConfig createTestConfig() {
        return new PolyfixConfig(
                List.of("typescript", "javascript"),
                List.of(),
                new PolyfixConfig.Budgets(100, 1000),
                new PolyfixConfig.Policies(true, true, true, true),
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
    }

    @Test
    void testAnalyzeWithESLint() throws IOException {
        // Setup test file with content
        String content = "let x: number = 'hello';\nconsole.log(x);";
        Files.writeString(testFile, content);

        // Create a mock diagnostic
        UnifiedDiagnostic mockDiagnostic = mock(UnifiedDiagnostic.class);
        when(mockDiagnostic.rule()).thenReturn("test-rule");

        // Configure the mock ESLint service to return our mock diagnostic
        when(mockESLintService.analyze(
                        argThat(
                                files ->
                                        files != null
                                                && files.size() == 1
                                                && files.get(0).equals(testFile))))
                .thenReturn(Collections.singletonList(mockDiagnostic));

        // Execute the test
        List<UnifiedDiagnostic> diagnostics = runPromise(
                () -> tsJsService.diagnose(projectContext, Collections.singletonList(testFile)));

        // Verify the results
        assertThat(diagnostics)
                .as("Should contain the mock diagnostic from ESLint")
                .isNotEmpty()
                .anySatisfy(diag -> assertThat(diag.rule()).isEqualTo("test-rule"));

        // Verify the mock was called with the correct arguments
        verify(mockESLintService)
                .analyze(
                        argThat(
                                files ->
                                        files != null
                                                && files.size() == 1
                                                && files.get(0).equals(testFile)));
    }

    @Test
    void testDetectsESLintIssues() throws IOException {
        // Setup test file with content that would trigger an ESLint rule
        Files.writeString(testFile, "const unused = 42;");

        // Create a mock diagnostic for the ESLint rule
        UnifiedDiagnostic mockDiagnostic = mock(UnifiedDiagnostic.class);
        when(mockDiagnostic.rule()).thenReturn("@typescript-eslint/no-unused-vars");

        // Configure the mock ESLint service to return our mock diagnostic
        when(mockESLintService.analyze(anyList()))
                .thenReturn(Collections.singletonList(mockDiagnostic));

        // Execute the test
        List<UnifiedDiagnostic> diagnostics = runPromise(
                () -> tsJsService.diagnose(projectContext, Collections.singletonList(testFile)));

        // Verify the results
        assertThat(diagnostics)
                .as("Should detect ESLint issues")
                .isNotEmpty()
                .extracting(UnifiedDiagnostic::rule)
                .contains("@typescript-eslint/no-unused-vars");

        // Verify the mock was called with the correct arguments
        verify(mockESLintService)
                .analyze(
                        argThat(
                                files ->
                                        files != null
                                                && files.size() == 1
                                                && files.get(0).equals(testFile)));
    }

    @Test
    void testNoFalsePositivesOnValidCode() throws IOException {
        // Setup a valid test file
        Path validFile = tempDir.resolve("valid.ts");
        Files.writeString(validFile, "const x: number = 42;\nconsole.log(x);");

        // Configure the mock ESLint service to return no issues
        when(mockESLintService.analyze(anyList())).thenReturn(Collections.emptyList());

        // Execute the test
        List<UnifiedDiagnostic> diagnostics = runPromise(
                () -> tsJsService.diagnose(projectContext, Collections.singletonList(validFile)));

        // Verify no issues were found
        assertThat(diagnostics).as("Should not report issues on valid code").isEmpty();

        // Verify the mock was called with the correct arguments
        verify(mockESLintService)
                .analyze(
                        argThat(
                                files ->
                                        files != null
                                                && files.size() == 1
                                                && files.get(0).equals(validFile)));
    }

    @Test
    void testHandlesMissingESLintGracefully() throws IOException {
        // Setup test file with content
        Files.writeString(testFile, "const x = 42;");

        // Create a new service instance with a null ESLintService to simulate initialization
        // failure
        TypeScriptJavaScriptLanguageService serviceWithNoESLint =
                new TypeScriptJavaScriptLanguageService(projectContext, null, eventloop());

        // Execute the test - should not throw an exception
        List<UnifiedDiagnostic> diagnostics = runPromise(
                () -> serviceWithNoESLint.diagnose(projectContext, Collections.singletonList(testFile)));

        // Verify that no diagnostics were returned (graceful degradation)
        assertThat(diagnostics).as("Should handle missing ESLint gracefully").isEmpty();
    }
}
