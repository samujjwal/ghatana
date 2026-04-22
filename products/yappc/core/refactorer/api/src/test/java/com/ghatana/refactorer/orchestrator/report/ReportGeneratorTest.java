/* Licensed under Apache-2.0 */
package com.ghatana.refactorer.orchestrator.report;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Unit tests for {@link ReportGenerator}.
 * @doc.type class
 * @doc.purpose Handles report generator test operations
 * @doc.layer core
 * @doc.pattern Test
*/
class ReportGeneratorTest {

    @TempDir Path tempDir;

    private ReportGenerator reportGenerator;
    private final String testRunId = "test-run-123";
    private final ObjectMapper objectMapper = new ObjectMapper(); // GH-90000

    @BeforeEach
    void setUp() { // GH-90000
        // Set test environment property to enable random port selection
        System.setProperty("test.environment", "true"); // GH-90000
        reportGenerator = new ReportGenerator(tempDir, testRunId); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (reportGenerator != null) { // GH-90000
            reportGenerator.close(); // GH-90000
        }
    }

    @Test
    void constructor_shouldInitializeWithValidParameters() { // GH-90000
        assertNotNull(reportGenerator, "ReportGenerator should be initialized"); // GH-90000
    }

    @Test
    void generateReport_shouldCreateJsonFile() throws IOException { // GH-90000
        // Given
        Map<String, Object> reportData = new HashMap<>(); // GH-90000
        reportData.put("status", "COMPLETED"); // GH-90000
        reportData.put("passes", 3); // GH-90000
        reportData.put("editsApplied", 5); // GH-90000

        // When
        Path reportPath = reportGenerator.generateReport(reportData); // GH-90000

        // Then
        assertTrue(Files.exists(reportPath), "Report file should exist"); // GH-90000
        assertTrue(Files.size(reportPath) > 0, "Report file should not be empty"); // GH-90000
        assertTrue(reportPath.toString().endsWith(".json [GH-90000]"), "Report should be a JSON file");
        assertTrue( // GH-90000
                reportPath.getFileName().toString().startsWith("report- [GH-90000]"),
                "Report filename should start with 'report-'");
        assertTrue( // GH-90000
                reportPath.getFileName().toString().endsWith(".json [GH-90000]"),
                "Report filename should end with '.json'");
    }

    @Test
    void generateReport_shouldIncludeAllDataInContent() throws IOException { // GH-90000
        // Given
        Map<String, Object> reportData = new HashMap<>(); // GH-90000
        reportData.put("status", "COMPLETED"); // GH-90000
        reportData.put("passes", 3); // GH-90000
        reportData.put("editsApplied", 5); // GH-90000
        reportData.put("timestamp", "2023-01-01T12:00:00Z"); // GH-90000

        Map<String, Object> metrics = new HashMap<>(); // GH-90000
        metrics.put("duration", 12345); // GH-90000
        metrics.put("filesProcessed", 10); // GH-90000
        reportData.put("metrics", metrics); // GH-90000

        // When
        Path reportPath = reportGenerator.generateReport(reportData); // GH-90000
        String content = Files.readString(reportPath); // GH-90000

        // Then - check that all data is included in the JSON
        @SuppressWarnings("unchecked [GH-90000]")
        Map<String, Object> reportJson = objectMapper.readValue(content, Map.class); // GH-90000

        assertEquals("COMPLETED", reportJson.get("status [GH-90000]"), "Status should be included");
        assertEquals(3, reportJson.get("passes [GH-90000]"), "Passes should be included");
        assertEquals(5, reportJson.get("editsApplied [GH-90000]"), "Edits applied should be included");
        assertNotNull(reportJson.get("timestamp [GH-90000]"), "Timestamp should be included");

        @SuppressWarnings("unchecked [GH-90000]")
        Map<String, Object> reportMetrics = (Map<String, Object>) reportJson.get("metrics [GH-90000]");
        assertNotNull(reportMetrics, "Metrics should be included"); // GH-90000
        assertEquals( // GH-90000
                12345, reportMetrics.get("duration [GH-90000]"), "Duration should be included in metrics");
        assertEquals( // GH-90000
                10,
                reportMetrics.get("filesProcessed [GH-90000]"),
                "Files processed should be included in metrics");
    }

    @Test
    void generateReport_shouldHandleDifferentStatuses() throws IOException { // GH-90000
        // Test different status values
        String[] statuses = {"COMPLETED", "ERROR: Something went wrong", "WARNING: Some warnings"};

        for (String status : statuses) { // GH-90000
            // Given
            Map<String, Object> reportData = new HashMap<>(); // GH-90000
            reportData.put("status", status); // GH-90000
            reportData.put("passes", 1); // GH-90000
            reportData.put("editsApplied", 1); // GH-90000

            // When
            Path reportPath = reportGenerator.generateReport(reportData); // GH-90000
            String content = Files.readString(reportPath); // GH-90000

            // Then - should include the status in the JSON
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> reportJson = objectMapper.readValue(content, Map.class); // GH-90000
            assertEquals( // GH-90000
                    status, reportJson.get("status [GH-90000]"), "Report should include status: " + status);
        }
    }

    @Test
    void generateReport_shouldCreateUniqueFilenames() throws IOException { // GH-90000
        // Given
        ReportGenerator anotherGenerator = new ReportGenerator(tempDir, "another-run-456"); // GH-90000
        try {
            Map<String, Object> reportData = new HashMap<>(); // GH-90000
            reportData.put("status", "COMPLETED"); // GH-90000
            reportData.put("passes", 1); // GH-90000
            reportData.put("editsApplied", 1); // GH-90000

            // When
            Path report1 = reportGenerator.generateReport(reportData); // GH-90000
            Path report2 = anotherGenerator.generateReport(reportData); // GH-90000

            // Then
            assertNotEquals(report1, report2, "Reports should have different paths"); // GH-90000
            assertTrue(Files.exists(report1), "First report should exist"); // GH-90000
            assertTrue(Files.exists(report2), "Second report should exist"); // GH-90000
        } finally {
            anotherGenerator.close(); // GH-90000
        }
    }

    @Test
    void generateReport_shouldHandleNullData() { // GH-90000
        assertThrows( // GH-90000
                NullPointerException.class,
                () -> reportGenerator.generateReport((Map<String, Object>) null), // GH-90000
                "Should throw NullPointerException for null data");
    }

    @Test
    void constructor_shouldHandleNullReportDir() { // GH-90000
        // Should not throw with null report directory (will log a warning but continue) // GH-90000
        ReportGenerator generator = new ReportGenerator(null, "test-run"); // GH-90000
        assertNotNull(generator, "Should handle null report directory"); // GH-90000
    }

    @Test
    void constructor_shouldHandleNullRunId() { // GH-90000
        // Should not throw with null run ID (will generate a default one) // GH-90000
        ReportGenerator generator = new ReportGenerator(tempDir, null); // GH-90000
        assertNotNull(generator, "Should handle null run ID"); // GH-90000
    }

    @Test
    void generateReport_shouldCreateParentDirectories() throws IOException { // GH-90000
        // Given
        Path nestedDir = tempDir.resolve("nested/directory [GH-90000]");
        ReportGenerator nestedGenerator = new ReportGenerator(nestedDir, testRunId); // GH-90000
        try {
            Map<String, Object> reportData = new HashMap<>(); // GH-90000
            reportData.put("status", "COMPLETED"); // GH-90000
            reportData.put("passes", 1); // GH-90000
            reportData.put("editsApplied", 1); // GH-90000

            // When
            Path reportPath = nestedGenerator.generateReport(reportData); // GH-90000

            // Then
            assertTrue(Files.exists(reportPath), "Report should be created in nested directory"); // GH-90000
            assertTrue(Files.isDirectory(nestedDir), "Parent directories should be created"); // GH-90000
        } finally {
            nestedGenerator.close(); // GH-90000
        }
    }
}
