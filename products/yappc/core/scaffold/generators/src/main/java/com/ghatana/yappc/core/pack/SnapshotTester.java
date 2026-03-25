package com.ghatana.yappc.core.pack;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Snapshot testing interface for pack generation validation. Provides utilities for golden snapshot
 * testing by comparing generated output with stored fixtures.
 *
 * @doc.type interface
 * @doc.purpose Snapshot testing interface for pack generation validation. Provides utilities for golden snapshot
 * @doc.layer platform
 * @doc.pattern Component
 */
public interface SnapshotTester {

    /**
 * Result of a snapshot test comparison */
    record SnapshotTestResult(
            boolean matches,
            String expectedContent,
            String actualContent,
            String diff,
            Path snapshotPath) {
        public static SnapshotTestResult match(String content, Path snapshotPath) {
            return new SnapshotTestResult(true, content, content, "", snapshotPath);
        }

        public static SnapshotTestResult mismatch(
                String expected, String actual, String diff, Path snapshotPath) {
            return new SnapshotTestResult(false, expected, actual, diff, snapshotPath);
        }

        public static SnapshotTestResult missing(String actual, Path snapshotPath) {
            return new SnapshotTestResult(false, "", actual, "Missing snapshot file", snapshotPath);
        }
    }

    /**
     * Compare generated content with stored snapshot
     *
     * @param snapshotName Name of the snapshot (will be used as filename)
     * @param actualContent The generated content to compare
     * @return Result of the comparison
     */
    SnapshotTestResult assertSnapshot(String snapshotName, String actualContent) throws IOException;

    /**
     * Update a snapshot with new content (used when snapshots need to be updated)
     *
     * @param snapshotName Name of the snapshot
     * @param content New content to store
     */
    void updateSnapshot(String snapshotName, String content) throws IOException;

    /**
     * Check if snapshot exists
     *
     * @param snapshotName Name of the snapshot
     * @return true if snapshot file exists
     */
    boolean hasSnapshot(String snapshotName);

    /**
 * Get the path to the snapshot directory */
    Path getSnapshotDirectory();
}
