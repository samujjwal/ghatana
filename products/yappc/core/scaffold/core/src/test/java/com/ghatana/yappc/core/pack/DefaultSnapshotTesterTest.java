package com.ghatana.yappc.core.pack;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for DefaultSnapshotTester functionality 
 * @doc.type class
 * @doc.purpose Handles default snapshot tester test operations
 * @doc.layer core
 * @doc.pattern Test
*/
class DefaultSnapshotTesterTest {

    private DefaultSnapshotTester snapshotTester;
    private Path snapshotDir;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        snapshotDir = tempDir.resolve("snapshots");
        snapshotTester = new DefaultSnapshotTester(snapshotDir);
    }

    @Test
    void testCreateSnapshot() throws IOException {
        String content = "Test content\nLine 2\n";
        String snapshotName = "test_snapshot";

        SnapshotTester.SnapshotTestResult result =
                snapshotTester.assertSnapshot(snapshotName, content);

        // First time should create snapshot (treated as missing)
        assertFalse(result.matches()); // Missing snapshot
        assertEquals("", result.expectedContent());
        assertEquals(content, result.actualContent());
        assertTrue(result.snapshotPath().toString().contains(snapshotName));

        // Update the snapshot
        snapshotTester.updateSnapshot(snapshotName, content);
        assertTrue(snapshotTester.hasSnapshot(snapshotName));

        // Now it should match
        SnapshotTester.SnapshotTestResult matchResult =
                snapshotTester.assertSnapshot(snapshotName, content);
        assertTrue(matchResult.matches());
        assertEquals(content, matchResult.expectedContent());
        assertEquals(content, matchResult.actualContent());
    }

    @Test
    void testSnapshotMismatch() throws IOException {
        String originalContent = "Original content\n";
        String modifiedContent = "Modified content\n";
        String snapshotName = "mismatch_test";

        // Create original snapshot
        snapshotTester.updateSnapshot(snapshotName, originalContent);

        // Test with modified content
        SnapshotTester.SnapshotTestResult result =
                snapshotTester.assertSnapshot(snapshotName, modifiedContent);

        assertFalse(result.matches());
        assertEquals(originalContent, result.expectedContent());
        assertEquals(modifiedContent, result.actualContent());
        assertFalse(result.diff().isEmpty());
        assertTrue(result.diff().contains("Original content"));
        assertTrue(result.diff().contains("Modified content"));
    }

    @Test
    void testLineEndingNormalization() throws IOException {
        String unixContent = "Line 1\nLine 2\n";
        String windowsContent = "Line 1\r\nLine 2\r\n";
        String macContent = "Line 1\rLine 2\r";
        String snapshotName = "line_ending_test";

        // Create snapshot with Unix line endings
        snapshotTester.updateSnapshot(snapshotName, unixContent);

        // Test that different line endings are normalized and match
        SnapshotTester.SnapshotTestResult unixResult =
                snapshotTester.assertSnapshot(snapshotName, unixContent);
        assertTrue(unixResult.matches(), "Unix line endings should match");

        SnapshotTester.SnapshotTestResult windowsResult =
                snapshotTester.assertSnapshot(snapshotName, windowsContent);
        assertTrue(windowsResult.matches(), "Windows line endings should normalize and match");

        SnapshotTester.SnapshotTestResult macResult =
                snapshotTester.assertSnapshot(snapshotName, macContent);
        assertTrue(macResult.matches(), "Mac line endings should normalize and match");
    }

    @Test
    void testUpdateMode() throws IOException {
        Path updateSnapshotDir = snapshotDir.resolve("update_mode");
        DefaultSnapshotTester updateTester = new DefaultSnapshotTester(updateSnapshotDir, true);

        String content = "Update test content\n";
        String snapshotName = "update_test";

        // In update mode, should always return match
        SnapshotTester.SnapshotTestResult result =
                updateTester.assertSnapshot(snapshotName, content);
        assertTrue(result.matches());

        // Snapshot should be created
        assertTrue(updateTester.hasSnapshot(snapshotName));

        // File should exist and have correct content
        Path snapshotPath = updateTester.getSnapshotDirectory().resolve(snapshotName + ".snapshot");
        assertTrue(Files.exists(snapshotPath));
        assertEquals(content, Files.readString(snapshotPath));
    }

    @Test
    void testSnapshotNameSanitization() throws IOException {
        String problematicName = "test/with\\special:chars*and?spaces";
        String content = "Sanitization test\n";

        // Should not throw exception despite problematic characters
        assertDoesNotThrow(
                () -> {
                    snapshotTester.updateSnapshot(problematicName, content);
                    snapshotTester.assertSnapshot(problematicName, content);
                });

        assertTrue(snapshotTester.hasSnapshot(problematicName));
    }

    @Test
    void testEmptyContent() throws IOException {
        String emptyContent = "";
        String snapshotName = "empty_test";

        snapshotTester.updateSnapshot(snapshotName, emptyContent);
        SnapshotTester.SnapshotTestResult result =
                snapshotTester.assertSnapshot(snapshotName, emptyContent);

        assertTrue(result.matches());
        assertEquals("", result.expectedContent());
        assertEquals("", result.actualContent());
    }

    @Test
    void testNullContent() throws IOException {
        String snapshotName = "null_test";

        // Null content should be treated as empty string
        assertDoesNotThrow(
                () -> {
                    snapshotTester.updateSnapshot(snapshotName, null);
                    SnapshotTester.SnapshotTestResult result =
                            snapshotTester.assertSnapshot(snapshotName, null);
                    assertTrue(result.matches());
                });
    }

    @Test
    void testSnapshotDirectory() {
        assertEquals(snapshotDir, snapshotTester.getSnapshotDirectory());
        assertTrue(Files.exists(snapshotDir), "Snapshot directory should be created");
    }

    @Test
    void testDiffGeneration() throws IOException {
        String original = "Line 1\nLine 2\nLine 3\n";
        String modified = "Line 1\nModified Line 2\nLine 3\n";
        String snapshotName = "diff_test";

        snapshotTester.updateSnapshot(snapshotName, original);
        SnapshotTester.SnapshotTestResult result =
                snapshotTester.assertSnapshot(snapshotName, modified);

        assertFalse(result.matches());
        assertFalse(result.diff().isEmpty());
        assertTrue(result.diff().contains("Line 2"), "Diff should show original line");
        assertTrue(result.diff().contains("Modified Line 2"), "Diff should show modified line");
        assertTrue(result.diff().contains("Expected vs Actual"), "Diff should have header");
    }
}
