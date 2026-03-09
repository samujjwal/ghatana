package com.ghatana.yappc.core.pack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/**
 * Default implementation of SnapshotTester for golden snapshot testing. Stores snapshots as files
 * in a designated directory and compares generated content with stored fixtures.
 *
 * @doc.type class
 * @doc.purpose Default implementation of SnapshotTester for golden snapshot testing. Stores snapshots as files
 * @doc.layer platform
 * @doc.pattern Component
 */
public class DefaultSnapshotTester implements SnapshotTester {

    private final Path snapshotDirectory;
    private final boolean updateSnapshots;

    /**
     * Create a snapshot tester with the specified directory
     *
     * @param snapshotDirectory Directory where snapshots are stored
     */
    public DefaultSnapshotTester(Path snapshotDirectory) {
        this(snapshotDirectory, false);
    }

    /**
     * Create a snapshot tester with options
     *
     * @param snapshotDirectory Directory where snapshots are stored
     * @param updateSnapshots If true, snapshots will be updated rather than compared
     */
    public DefaultSnapshotTester(Path snapshotDirectory, boolean updateSnapshots) {
        this.snapshotDirectory = Objects.requireNonNull(snapshotDirectory);
        this.updateSnapshots = updateSnapshots;

        // Ensure snapshot directory exists
        try {
            Files.createDirectories(snapshotDirectory);
        } catch (IOException e) {
            throw new RuntimeException(
                    "Failed to create snapshot directory: " + snapshotDirectory, e);
        }
    }

    @Override
    public SnapshotTestResult assertSnapshot(String snapshotName, String actualContent)
            throws IOException {
        Path snapshotPath = getSnapshotPath(snapshotName);

        // Normalize null content to empty string
        String normalizedActualContent = actualContent == null ? "" : actualContent;

        if (updateSnapshots) {
            updateSnapshot(snapshotName, normalizedActualContent);
            return SnapshotTestResult.match(normalizedActualContent, snapshotPath);
        }

        if (!Files.exists(snapshotPath)) {
            return SnapshotTestResult.missing(normalizedActualContent, snapshotPath);
        }

        String expectedContent = Files.readString(snapshotPath);

        // Normalize line endings for cross-platform compatibility
        String normalizedExpected = normalizeLineEndings(expectedContent);
        String normalizedActual = normalizeLineEndings(normalizedActualContent);

        if (normalizedExpected.equals(normalizedActual)) {
            return SnapshotTestResult.match(normalizedActualContent, snapshotPath);
        } else {
            String diff = generateDiff(normalizedExpected, normalizedActual);
            return SnapshotTestResult.mismatch(
                    expectedContent, normalizedActualContent, diff, snapshotPath);
        }
    }

    @Override
    public void updateSnapshot(String snapshotName, String content) throws IOException {
        Path snapshotPath = getSnapshotPath(snapshotName);
        Files.createDirectories(snapshotPath.getParent());
        String normalizedContent = content == null ? "" : content;
        Files.writeString(
                snapshotPath,
                normalizedContent,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
    }

    @Override
    public boolean hasSnapshot(String snapshotName) {
        return Files.exists(getSnapshotPath(snapshotName));
    }

    @Override
    public Path getSnapshotDirectory() {
        return snapshotDirectory;
    }

    private Path getSnapshotPath(String snapshotName) {
        // Sanitize snapshot name for filesystem
        String sanitized = snapshotName.replaceAll("[^a-zA-Z0-9._-]", "_");
        return snapshotDirectory.resolve(sanitized + ".snapshot");
    }

    private String normalizeLineEndings(String content) {
        if (content == null) {
            return "";
        }
        return content.replaceAll("\r\n", "\n").replaceAll("\r", "\n");
    }

    /**
     * Generate a simple diff between expected and actual content. This is a basic implementation -
     * in production you might want to use a more sophisticated diff library like java-diff-utils.
     */
    private String generateDiff(String expected, String actual) {
        String[] expectedLines = expected.split("\\n");
        String[] actualLines = actual.split("\\n");

        StringBuilder diff = new StringBuilder();
        diff.append("Expected vs Actual:\\n");

        int maxLines = Math.max(expectedLines.length, actualLines.length);
        for (int i = 0; i < maxLines; i++) {
            String expectedLine = i < expectedLines.length ? expectedLines[i] : "";
            String actualLine = i < actualLines.length ? actualLines[i] : "";

            if (!expectedLine.equals(actualLine)) {
                diff.append(String.format("Line %d:\\n", i + 1));
                diff.append(String.format("- %s\\n", expectedLine));
                diff.append(String.format("+ %s\\n", actualLine));
            }
        }

        return diff.toString();
    }
}
