/* Licensed under Apache-2.0 */
package com.ghatana.refactorer.orchestrator;

import static org.junit.jupiter.api.Assertions.*;

import com.ghatana.refactorer.diagnostics.DiagnosticsRunner;
import com.ghatana.refactorer.shared.PolyfixConfig;
import com.ghatana.refactorer.shared.PolyfixProjectContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Slf4j
/**
 * @doc.type class
 * @doc.purpose Handles polyfix orchestrator test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class PolyfixOrchestratorTest {

    @Mock private PolyfixConfig mockConfig;

    @Mock private PolyfixConfig.Budgets mockBudgets;

    @Mock private ExecutorService mockExecutor;

    private Path tempDir;
    private PolyfixProjectContext testContext;
    private org.apache.logging.log4j.Logger testLog;

    // No LanguageService mocks needed for these tests

    @BeforeEach
    void setUp() throws IOException {
        System.setProperty("test.environment", "true");
        tempDir = Files.createTempDirectory("polyfix-test-");
        tempDir.toFile().deleteOnExit();

        // Create a proper logger for testing
        testLog = org.apache.logging.log4j.LogManager.getLogger(PolyfixOrchestratorTest.class);
    }

    private PolyfixProjectContext createTestContext() {
        // Minimal context: no language services and no budget stubs to avoid unnecessary stubbing
        return new PolyfixProjectContext(tempDir, mockConfig, List.of(), mockExecutor, testLog);
    }

    @Test
    void run_shouldGenerateReportWhenReportDirIsConfigured() throws IOException {
        // Given
        Path reportDir = tempDir.resolve("reports");
        // Ensure the report directory exists before creating the orchestrator
        Files.createDirectories(reportDir);

        try (MockedStatic<DiagnosticsRunner> mockedRunner =
                Mockito.mockStatic(DiagnosticsRunner.class)) {
            // Create the test context with mocks BEFORE stubbing runAll
            testContext = createTestContext();

            // Mock the static method to return an empty list of diagnostics for this context
            mockedRunner.when(() -> DiagnosticsRunner.runAll(testContext)).thenReturn(List.of());

            // Create the orchestrator with the report directory
            PolyfixOrchestrator orchestrator = new PolyfixOrchestrator(null, reportDir);

            // Run the orchestrator with the test context
            PolyfixOrchestrator.RunSummary summary = orchestrator.run(testContext);

            // Then
            assertNotNull(summary, "Run should complete successfully");
            assertEquals(1, summary.passes(), "Should complete in one pass with no issues");
            assertEquals(0, summary.editsApplied(), "No edits should be applied");

            // Verify report was generated
            assertTrue(Files.exists(reportDir), "Report directory should be created");
            assertTrue(
                    countFilesWithExtension(reportDir, "json") > 0,
                    "At least one JSON report should be generated. Found files: "
                            + String.join(
                                    ", ",
                                    Files.list(reportDir)
                                            .map(Path::toString)
                                            .toArray(String[]::new)));

            // Verify the static method was called with our test context
            mockedRunner.verify(() -> DiagnosticsRunner.runAll(testContext));
        }
    }

    @Test
    void run_shouldNotFailWhenReportDirIsNull() {
        try (MockedStatic<DiagnosticsRunner> mockedRunner =
                Mockito.mockStatic(DiagnosticsRunner.class)) {
            // Create the test context with mocks BEFORE stubbing runAll
            testContext = createTestContext();

            // Mock diagnostics to return no issues (empty list) for this context
            mockedRunner.when(() -> DiagnosticsRunner.runAll(testContext)).thenReturn(List.of());

            // Create the orchestrator without a report directory
            PolyfixOrchestrator orchestrator = new PolyfixOrchestrator();

            // Run the orchestrator with the test context
            PolyfixOrchestrator.RunSummary summary = orchestrator.run(testContext);

            // Then - should complete without throwing exceptions
            assertNotNull(summary, "Run should complete successfully");
            assertEquals(1, summary.passes(), "Should complete in one pass with no issues");
            assertEquals(0, summary.editsApplied(), "No edits should be applied");

            // Verify the static method was called with our test context
            mockedRunner.verify(() -> DiagnosticsRunner.runAll(testContext));
        }
    }

    @Test
    void run_shouldIncludeErrorInReportWhenExceptionOccurs() throws IOException {
        // Given
        Path reportDir = tempDir.resolve("error-reports");
        // Ensure the report directory exists before creating the orchestrator
        Files.createDirectories(reportDir);

        try (MockedStatic<DiagnosticsRunner> mockedRunner =
                Mockito.mockStatic(DiagnosticsRunner.class)) {
            // Create the test context with mocks first
            testContext = createTestContext();

            // Now mock the diagnostics to throw an exception
            RuntimeException testError = new RuntimeException("Test error");
            mockedRunner.when(() -> DiagnosticsRunner.runAll(testContext)).thenThrow(testError);

            // Create the orchestrator with the report directory
            PolyfixOrchestrator orchestrator = new PolyfixOrchestrator(null, reportDir);

            // When - run the orchestrator with the test context
            PolyfixOrchestrator.RunSummary summary = orchestrator.run(testContext);

            // Then
            assertNotNull(summary, "Run should complete with error");
            assertTrue(
                    summary.status().contains("ERROR"),
                    "Status should indicate an error occurred. Actual status: " + summary.status());
            assertTrue(
                    summary.status().contains("Test error"),
                    "Status should include the error message. Actual status: " + summary.status());

            // Verify error report was generated
            assertTrue(Files.exists(reportDir), "Report directory should be created");
            List<Path> reportFiles;
            try (var files = Files.list(reportDir)) {
                reportFiles = files.toList();
            }

            assertTrue(
                    countFilesWithExtension(reportDir, "json") > 0,
                    "Error report should be generated. Found files: "
                            + String.join(
                                    ", ",
                                    reportFiles.stream()
                                            .map(Path::toString)
                                            .toArray(String[]::new)));

            Path reportFile =
                    reportFiles.stream()
                            .filter(Files::isRegularFile)
                            .filter(p -> p.toString().toLowerCase().endsWith(".json"))
                            .findFirst()
                            .orElseThrow(() -> new AssertionError("No JSON report generated"));

            String reportContent = Files.readString(reportFile);
            assertTrue(
                    reportContent.contains("ERROR"),
                    "Report content should include error status. Content: " + reportContent);
            assertTrue(
                    reportContent.contains("Test error"),
                    "Report content should include error message. Content: " + reportContent);

            // Verify the static method was called with our test context
            mockedRunner.verify(() -> DiagnosticsRunner.runAll(testContext));
        }
    }

    // Helper method to count files with a specific extension in a directory
    private long countFilesWithExtension(Path directory, String extension) {
        try (var files = Files.list(directory)) {
            return files.filter(Files::isRegularFile)
                    .filter(p -> p.toString().toLowerCase().endsWith("." + extension.toLowerCase()))
                    .count();
        } catch (IOException e) {
            return 0;
        }
    }
}
