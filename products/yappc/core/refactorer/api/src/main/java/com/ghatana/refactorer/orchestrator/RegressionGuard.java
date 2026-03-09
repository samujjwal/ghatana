package com.ghatana.refactorer.orchestrator;

import com.ghatana.refactorer.shared.UnifiedDiagnostic;
import java.nio.file.Path;
import java.util.*;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tracks diagnostic counts to detect regressions after applying fixes. This is a simplified
 * implementation that logs regressions but does not perform rollbacks.
 
 * @doc.type class
 * @doc.purpose Handles regression guard operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public class RegressionGuard {

    private static final Logger log = LoggerFactory.getLogger(RegressionGuard.class);
    private final Map<String, Integer> baselineCounts = new ConcurrentHashMap<>();
    private final double regressionThreshold = 1.2; // 20% increase in issues triggers warning

    /**
     * Records the current diagnostic count for a file.
     *
     * @param filePath The file being analyzed
     * @param diagnostics Current diagnostics for the file
     * @return A snapshot ID that can be used to check for regressions later
     */
    public String recordBaseline(Path filePath, List<UnifiedDiagnostic> diagnostics) {
        Objects.requireNonNull(filePath, "filePath cannot be null");
        Objects.requireNonNull(diagnostics, "diagnostics cannot be null");

        String snapshotId = filePath.toString() + "_" + System.currentTimeMillis();
        baselineCounts.put(snapshotId, diagnostics.size());
        return snapshotId;
    }

    /**
     * Checks if there's a regression compared to a previous snapshot.
     *
     * @param snapshotId The ID returned by recordBaseline
     * @param currentDiagnostics Current diagnostics for the file
     * @return true if a regression was detected, false otherwise
     */
    public boolean checkForRegression(
            String snapshotId, List<UnifiedDiagnostic> currentDiagnostics) {
        Objects.requireNonNull(snapshotId, "snapshotId cannot be null");
        Objects.requireNonNull(currentDiagnostics, "currentDiagnostics cannot be null");

        Integer baselineCount = baselineCounts.get(snapshotId);
        if (baselineCount == null) {
            log.warn("Warning: No baseline found for snapshot {}", snapshotId);
            return false;
        }

        int currentCount = currentDiagnostics.size();
        if (baselineCount == 0) {
            return currentCount > 0; // Any issues after baseline of 0 is a regression
        }

        double increaseRatio = (double) currentCount / baselineCount;
        boolean isRegression = increaseRatio >= regressionThreshold;

        if (isRegression) {
            log.info("Warning: Possible regression detected - "
                            + "diagnostics increased from %d to %d (%.1fx)%n",
                    baselineCount, currentCount, increaseRatio);
        }

        return isRegression;
    }

    /**
     * Cleans up a snapshot when it's no longer needed.
     *
     * @param snapshotId The snapshot ID to clean up
     */
    public void cleanupSnapshot(String snapshotId) {
        Objects.requireNonNull(snapshotId, "snapshotId cannot be null");
        baselineCounts.remove(snapshotId);
        log.info("Debug: Cleaned up regression snapshot: {}", snapshotId);
    }
}
