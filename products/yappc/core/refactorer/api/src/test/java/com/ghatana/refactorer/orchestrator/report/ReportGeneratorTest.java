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
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        // Set test environment property to enable random port selection
        System.setProperty("test.environment", "true");
        reportGenerator = new ReportGenerator(tempDir, testRunId);
    }

    @AfterEach
    void tearDown() {
        if (reportGenerator != null) {
            reportGenerator.close();
        }
    }

    @Test
    void constructor_shouldInitializeWithValidParameters() {
        assertNotNull(reportGenerator, "ReportGenerator should be initialized");
    }

    @Test
    void generateReport_shouldCreateJsonFile() throws IOException {
        // Given
        Map<String, Object> reportData = new HashMap<>();
        reportData.put("status", "COMPLETED");
        reportData.put("passes", 3);
        reportData.put("editsApplied", 5);

        // When
        Path reportPath = reportGenerator.generateReport(reportData);

        // Then
        assertTrue(Files.exists(reportPath), "Report file should exist");
        assertTrue(Files.size(reportPath) > 0, "Report file should not be empty");
        assertTrue(reportPath.toString().endsWith(".json"), "Report should be a JSON file");
        assertTrue(
                reportPath.getFileName().toString().startsWith("report-"),
                "Report filename should start with 'report-'");
        assertTrue(
                reportPath.getFileName().toString().endsWith(".json"),
                "Report filename should end with '.json'");
    }

    @Test
    void generateReport_shouldIncludeAllDataInContent() throws IOException {
        // Given
        Map<String, Object> reportData = new HashMap<>();
        reportData.put("status", "COMPLETED");
        reportData.put("passes", 3);
        reportData.put("editsApplied", 5);
        reportData.put("timestamp", "2023-01-01T12:00:00Z");

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("duration", 12345);
        metrics.put("filesProcessed", 10);
        reportData.put("metrics", metrics);

        // When
        Path reportPath = reportGenerator.generateReport(reportData);
        String content = Files.readString(reportPath);

        // Then - check that all data is included in the JSON
        @SuppressWarnings("unchecked")
        Map<String, Object> reportJson = objectMapper.readValue(content, Map.class);

        assertEquals("COMPLETED", reportJson.get("status"), "Status should be included");
        assertEquals(3, reportJson.get("passes"), "Passes should be included");
        assertEquals(5, reportJson.get("editsApplied"), "Edits applied should be included");
        assertNotNull(reportJson.get("timestamp"), "Timestamp should be included");

        @SuppressWarnings("unchecked")
        Map<String, Object> reportMetrics = (Map<String, Object>) reportJson.get("metrics");
        assertNotNull(reportMetrics, "Metrics should be included");
        assertEquals(
                12345, reportMetrics.get("duration"), "Duration should be included in metrics");
        assertEquals(
                10,
                reportMetrics.get("filesProcessed"),
                "Files processed should be included in metrics");
    }

    @Test
    void generateReport_shouldHandleDifferentStatuses() throws IOException {
        // Test different status values
        String[] statuses = {"COMPLETED", "ERROR: Something went wrong", "WARNING: Some warnings"};

        for (String status : statuses) {
            // Given
            Map<String, Object> reportData = new HashMap<>();
            reportData.put("status", status);
            reportData.put("passes", 1);
            reportData.put("editsApplied", 1);

            // When
            Path reportPath = reportGenerator.generateReport(reportData);
            String content = Files.readString(reportPath);

            // Then - should include the status in the JSON
            @SuppressWarnings("unchecked")
            Map<String, Object> reportJson = objectMapper.readValue(content, Map.class);
            assertEquals(
                    status, reportJson.get("status"), "Report should include status: " + status);
        }
    }

    @Test
    void generateReport_shouldCreateUniqueFilenames() throws IOException {
        // Given
        ReportGenerator anotherGenerator = new ReportGenerator(tempDir, "another-run-456");
        try {
            Map<String, Object> reportData = new HashMap<>();
            reportData.put("status", "COMPLETED");
            reportData.put("passes", 1);
            reportData.put("editsApplied", 1);

            // When
            Path report1 = reportGenerator.generateReport(reportData);
            Path report2 = anotherGenerator.generateReport(reportData);

            // Then
            assertNotEquals(report1, report2, "Reports should have different paths");
            assertTrue(Files.exists(report1), "First report should exist");
            assertTrue(Files.exists(report2), "Second report should exist");
        } finally {
            anotherGenerator.close();
        }
    }

    @Test
    void generateReport_shouldHandleNullData() {
        assertThrows(
                NullPointerException.class,
                () -> reportGenerator.generateReport((Map<String, Object>) null),
                "Should throw NullPointerException for null data");
    }

    @Test
    void constructor_shouldHandleNullReportDir() {
        // Should not throw with null report directory (will log a warning but continue)
        ReportGenerator generator = new ReportGenerator(null, "test-run");
        assertNotNull(generator, "Should handle null report directory");
    }

    @Test
    void constructor_shouldHandleNullRunId() {
        // Should not throw with null run ID (will generate a default one)
        ReportGenerator generator = new ReportGenerator(tempDir, null);
        assertNotNull(generator, "Should handle null run ID");
    }

    @Test
    void generateReport_shouldCreateParentDirectories() throws IOException {
        // Given
        Path nestedDir = tempDir.resolve("nested/directory");
        ReportGenerator nestedGenerator = new ReportGenerator(nestedDir, testRunId);
        try {
            Map<String, Object> reportData = new HashMap<>();
            reportData.put("status", "COMPLETED");
            reportData.put("passes", 1);
            reportData.put("editsApplied", 1);

            // When
            Path reportPath = nestedGenerator.generateReport(reportData);

            // Then
            assertTrue(Files.exists(reportPath), "Report should be created in nested directory");
            assertTrue(Files.isDirectory(nestedDir), "Parent directories should be created");
        } finally {
            nestedGenerator.close();
        }
    }
}
