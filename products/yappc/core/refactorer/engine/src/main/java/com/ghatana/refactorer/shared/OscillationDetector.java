package com.ghatana.refactorer.shared;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Detects oscillating edits (A↔B toggling) using recent diff hashes per file/fingerprint.
 *
 * <p>This component prevents infinite loops where fixes toggle between two states by:
 *
 * <ul>
 *   <li>Tracking diff hashes for each file and diagnostic fingerprint
 *   <li>Detecting when the same diff hash appears multiple times
 *   <li>Blocking further edits when oscillation is detected
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * OscillationDetector detector = new OscillationDetector();
 *
 * // Record a diff
 * String diffHash = "abc123";
 * String fingerprint = "ruff:F401:unused-import";
 * detector.recordDiff(Paths.get("src/main.py"), fingerprint, diffHash);
 *
 * // Check for oscillation
 * if (detector.isOscillating(Paths.get("src/main.py"), fingerprint)) {
 *     // Skip this fix to prevent infinite loop
 * }
 * }</pre>
 
 * @doc.type class
 * @doc.purpose Handles oscillation detector operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public class OscillationDetector {

    private static final Logger log = LoggerFactory.getLogger(OscillationDetector.class);

    private static final int DEFAULT_WINDOW_SIZE = 5;
    private static final int DEFAULT_OSCILLATION_THRESHOLD = 2;
    private static final long DEFAULT_TTL_HOURS = 24;

    private final Map<String, List<DiffRecord>> diffHistory = new ConcurrentHashMap<>();
    private final int windowSize;
    private final int oscillationThreshold;
    private final long ttlHours;

    /**
 * Creates an oscillation detector with default settings. */
    public OscillationDetector() {
        this(DEFAULT_WINDOW_SIZE, DEFAULT_OSCILLATION_THRESHOLD, DEFAULT_TTL_HOURS);
    }

    /**
     * Creates an oscillation detector with custom settings.
     *
     * @param windowSize Number of recent diffs to track per file/fingerprint
     * @param oscillationThreshold Number of times a diff hash must repeat to be considered
     *     oscillating
     * @param ttlHours Hours after which old records are cleaned up
     */
    public OscillationDetector(int windowSize, int oscillationThreshold, long ttlHours) {
        if (windowSize <= 0) {
            throw new IllegalArgumentException("Window size must be positive");
        }
        if (oscillationThreshold <= 1) {
            throw new IllegalArgumentException("Oscillation threshold must be greater than 1");
        }
        if (ttlHours <= 0) {
            throw new IllegalArgumentException("TTL hours must be positive");
        }

        this.windowSize = windowSize;
        this.oscillationThreshold = oscillationThreshold;
        this.ttlHours = ttlHours;
    }

    /**
     * Records a diff hash for a file and diagnostic fingerprint.
     *
     * @param filePath The file that was modified
     * @param fingerprint The diagnostic fingerprint
     * @param diffHash Hash of the diff that was applied
     */
    public void recordDiff(Path filePath, String fingerprint, String diffHash) {
        Objects.requireNonNull(filePath, "filePath cannot be null");
        Objects.requireNonNull(fingerprint, "fingerprint cannot be null");
        Objects.requireNonNull(diffHash, "diffHash cannot be null");

        String key = createKey(filePath, fingerprint);
        DiffRecord record = new DiffRecord(Instant.now(), diffHash);

        diffHistory.compute(
                key,
                (k, records) -> {
                    if (records == null) {
                        records = new ArrayList<>();
                    }

                    records.add(record);

                    // Keep only the most recent records within window size
                    if (records.size() > windowSize) {
                        records = records.subList(records.size() - windowSize, records.size());
                    }

                    return records;
                });

        // Debug: Recorded diff for key: diffHash (total records: diffHistory.get(key).size())
    }

    /**
     * Checks if a file/fingerprint combination is currently oscillating.
     *
     * @param filePath The file to check
     * @param fingerprint The diagnostic fingerprint to check
     * @return true if oscillation is detected
     */
    public boolean isOscillating(Path filePath, String fingerprint) {
        Objects.requireNonNull(filePath, "filePath cannot be null");
        Objects.requireNonNull(fingerprint, "fingerprint cannot be null");

        String key = createKey(filePath, fingerprint);
        List<DiffRecord> records = diffHistory.get(key);

        if (records == null || records.size() < oscillationThreshold) {
            return false;
        }

        // Count occurrences of each diff hash
        Map<String, Long> hashCounts =
                records.stream()
                        .collect(
                                Collectors.groupingBy(DiffRecord::diffHash, Collectors.counting()));

        // Check if any hash appears more than the threshold
        boolean oscillating =
                hashCounts.values().stream().anyMatch(count -> count >= oscillationThreshold);

        if (oscillating) {
            log.error("WARNING: Oscillation detected for {}: hash counts = {}", key, hashCounts);
        }

        return oscillating;
    }

    /**
     * Gets the oscillation status for a file/fingerprint combination.
     *
     * @param filePath The file to check
     * @param fingerprint The diagnostic fingerprint to check
     * @return OscillationStatus with details
     */
    public OscillationStatus getOscillationStatus(Path filePath, String fingerprint) {
        Objects.requireNonNull(filePath, "filePath cannot be null");
        Objects.requireNonNull(fingerprint, "fingerprint cannot be null");

        String key = createKey(filePath, fingerprint);
        List<DiffRecord> records = diffHistory.get(key);

        if (records == null || records.isEmpty()) {
            return new OscillationStatus(false, 0, Map.of());
        }

        Map<String, Long> hashCounts =
                records.stream()
                        .collect(
                                Collectors.groupingBy(DiffRecord::diffHash, Collectors.counting()));

        long maxCount = hashCounts.values().stream().mapToLong(Long::longValue).max().orElse(0);
        boolean oscillating = maxCount >= oscillationThreshold;

        return new OscillationStatus(oscillating, records.size(), hashCounts);
    }

    /**
 * Cleans up old records based on TTL. */
    public void cleanupOldRecords() {
        Instant cutoff = Instant.now().minusSeconds(ttlHours * 3600L);

        diffHistory
                .entrySet()
                .removeIf(
                        entry -> {
                            List<DiffRecord> records = entry.getValue();
                            records.removeIf(record -> record.timestamp().isBefore(cutoff));
                            return records.isEmpty();
                        });

        // Debug: Cleaned up old oscillation records, keys remaining: diffHistory.size()
    }

    /**
     * Clears all oscillation history for a specific file/fingerprint.
     *
     * @param filePath The file to clear
     * @param fingerprint The diagnostic fingerprint to clear
     */
    public void clearHistory(Path filePath, String fingerprint) {
        Objects.requireNonNull(filePath, "filePath cannot be null");
        Objects.requireNonNull(fingerprint, "fingerprint cannot be null");

        String key = createKey(filePath, fingerprint);
        diffHistory.remove(key);

        // Debug: Cleared oscillation history for key
    }

    /**
 * Gets the total number of tracked file/fingerprint combinations. */
    public int getTrackedCount() {
        return diffHistory.size();
    }

    private String createKey(Path filePath, String fingerprint) {
        return filePath.toAbsolutePath().toString() + ":" + fingerprint;
    }

    /**
 * Record of a diff applied to a file. */
    private record DiffRecord(Instant timestamp, String diffHash) {}

    /**
 * Status of oscillation detection for a file/fingerprint combination. */
    public record OscillationStatus(
            boolean isOscillating, int recordCount, Map<String, Long> hashCounts) {}
}
