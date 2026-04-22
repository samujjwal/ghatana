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

@ExtendWith(MockitoExtension.class) // GH-90000
@MockitoSettings(strictness = Strictness.LENIENT) // GH-90000
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
    void setUp() throws IOException { // GH-90000
        System.setProperty("test.environment", "true"); // GH-90000
        tempDir = Files.createTempDirectory("polyfix-test- [GH-90000]");
        tempDir.toFile().deleteOnExit(); // GH-90000

        // Create a proper logger for testing
        testLog = org.apache.logging.log4j.LogManager.getLogger(PolyfixOrchestratorTest.class); // GH-90000
    }

    private PolyfixProjectContext createTestContext() { // GH-90000
        // Minimal context: no language services and no budget stubs to avoid unnecessary stubbing
        return new PolyfixProjectContext(tempDir, mockConfig, List.of(), mockExecutor, testLog); // GH-90000
    }

    @Test
    void run_shouldGenerateReportWhenReportDirIsConfigured() throws IOException { // GH-90000
        // Given
        Path reportDir = tempDir.resolve("reports [GH-90000]");
        // Ensure the report directory exists before creating the orchestrator
        Files.createDirectories(reportDir); // GH-90000

        try (MockedStatic<DiagnosticsRunner> mockedRunner = // GH-90000
                Mockito.mockStatic(DiagnosticsRunner.class)) { // GH-90000
            // Create the test context with mocks BEFORE stubbing runAll
            testContext = createTestContext(); // GH-90000

            // Mock the static method to return an empty list of diagnostics for this context
            mockedRunner.when(() -> DiagnosticsRunner.runAll(testContext)).thenReturn(List.of()); // GH-90000

            // Create the orchestrator with the report directory
            PolyfixOrchestrator orchestrator = new PolyfixOrchestrator(null, reportDir); // GH-90000

            // Run the orchestrator with the test context
            PolyfixOrchestrator.RunSummary summary = orchestrator.run(testContext); // GH-90000

            // Then
            assertNotNull(summary, "Run should complete successfully"); // GH-90000
            assertEquals(1, summary.passes(), "Should complete in one pass with no issues"); // GH-90000
            assertEquals(0, summary.editsApplied(), "No edits should be applied"); // GH-90000

            // Verify report was generated
            assertTrue(Files.exists(reportDir), "Report directory should be created"); // GH-90000
            assertTrue( // GH-90000
                    countFilesWithExtension(reportDir, "json") > 0, // GH-90000
                    "At least one JSON report should be generated. Found files: "
                            + String.join( // GH-90000
                                    ", ",
                                    Files.list(reportDir) // GH-90000
                                            .map(Path::toString) // GH-90000
                                            .toArray(String[]::new))); // GH-90000

            // Verify the static method was called with our test context
            mockedRunner.verify(() -> DiagnosticsRunner.runAll(testContext)); // GH-90000
        }
    }

    @Test
    void run_shouldNotFailWhenReportDirIsNull() { // GH-90000
        try (MockedStatic<DiagnosticsRunner> mockedRunner = // GH-90000
                Mockito.mockStatic(DiagnosticsRunner.class)) { // GH-90000
            // Create the test context with mocks BEFORE stubbing runAll
            testContext = createTestContext(); // GH-90000

            // Mock diagnostics to return no issues (empty list) for this context // GH-90000
            mockedRunner.when(() -> DiagnosticsRunner.runAll(testContext)).thenReturn(List.of()); // GH-90000

            // Create the orchestrator without a report directory
            PolyfixOrchestrator orchestrator = new PolyfixOrchestrator(); // GH-90000

            // Run the orchestrator with the test context
            PolyfixOrchestrator.RunSummary summary = orchestrator.run(testContext); // GH-90000

            // Then - should complete without throwing exceptions
            assertNotNull(summary, "Run should complete successfully"); // GH-90000
            assertEquals(1, summary.passes(), "Should complete in one pass with no issues"); // GH-90000
            assertEquals(0, summary.editsApplied(), "No edits should be applied"); // GH-90000

            // Verify the static method was called with our test context
            mockedRunner.verify(() -> DiagnosticsRunner.runAll(testContext)); // GH-90000
        }
    }

    @Test
    void run_shouldIncludeErrorInReportWhenExceptionOccurs() throws IOException { // GH-90000
        // Given
        Path reportDir = tempDir.resolve("error-reports [GH-90000]");
        // Ensure the report directory exists before creating the orchestrator
        Files.createDirectories(reportDir); // GH-90000

        try (MockedStatic<DiagnosticsRunner> mockedRunner = // GH-90000
                Mockito.mockStatic(DiagnosticsRunner.class)) { // GH-90000
            // Create the test context with mocks first
            testContext = createTestContext(); // GH-90000

            // Now mock the diagnostics to throw an exception
            RuntimeException testError = new RuntimeException("Test error [GH-90000]");
            mockedRunner.when(() -> DiagnosticsRunner.runAll(testContext)).thenThrow(testError); // GH-90000

            // Create the orchestrator with the report directory
            PolyfixOrchestrator orchestrator = new PolyfixOrchestrator(null, reportDir); // GH-90000

            // When - run the orchestrator with the test context
            PolyfixOrchestrator.RunSummary summary = orchestrator.run(testContext); // GH-90000

            // Then
            assertNotNull(summary, "Run should complete with error"); // GH-90000
            assertTrue( // GH-90000
                    summary.status().contains("ERROR [GH-90000]"),
                    "Status should indicate an error occurred. Actual status: " + summary.status()); // GH-90000
            assertTrue( // GH-90000
                    summary.status().contains("Test error [GH-90000]"),
                    "Status should include the error message. Actual status: " + summary.status()); // GH-90000

            // Verify error report was generated
            assertTrue(Files.exists(reportDir), "Report directory should be created"); // GH-90000
            List<Path> reportFiles;
            try (var files = Files.list(reportDir)) { // GH-90000
                reportFiles = files.toList(); // GH-90000
            }

            assertTrue( // GH-90000
                    countFilesWithExtension(reportDir, "json") > 0, // GH-90000
                    "Error report should be generated. Found files: "
                            + String.join( // GH-90000
                                    ", ",
                                    reportFiles.stream() // GH-90000
                                            .map(Path::toString) // GH-90000
                                            .toArray(String[]::new))); // GH-90000

            Path reportFile =
                    reportFiles.stream() // GH-90000
                            .filter(Files::isRegularFile) // GH-90000
                            .filter(p -> p.toString().toLowerCase().endsWith(".json [GH-90000]"))
                            .findFirst() // GH-90000
                            .orElseThrow(() -> new AssertionError("No JSON report generated [GH-90000]"));

            String reportContent = Files.readString(reportFile); // GH-90000
            assertTrue( // GH-90000
                    reportContent.contains("ERROR [GH-90000]"),
                    "Report content should include error status. Content: " + reportContent);
            assertTrue( // GH-90000
                    reportContent.contains("Test error [GH-90000]"),
                    "Report content should include error message. Content: " + reportContent);

            // Verify the static method was called with our test context
            mockedRunner.verify(() -> DiagnosticsRunner.runAll(testContext)); // GH-90000
        }
    }

    // Helper method to count files with a specific extension in a directory
    private long countFilesWithExtension(Path directory, String extension) { // GH-90000
        try (var files = Files.list(directory)) { // GH-90000
            return files.filter(Files::isRegularFile) // GH-90000
                    .filter(p -> p.toString().toLowerCase().endsWith("." + extension.toLowerCase())) // GH-90000
                    .count(); // GH-90000
        } catch (IOException e) { // GH-90000
            return 0;
        }
    }
}
