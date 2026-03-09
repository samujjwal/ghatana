package com.ghatana.platform.database.diagnostics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Detects N+1 query patterns by tracking query execution within request scopes.
 *
 * <p>When the same query template (normalized SQL) is executed more than a configurable
 * threshold within a single request scope, a warning is emitted with the call site and
 * count. This helps identify missing batch fetches and unnecessary round-trips.
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * // At request start:
 * NplusOneDetector.beginScope("GET /api/users");
 *
 * // Wrap your data access layer:
 * NplusOneDetector.recordQuery("SELECT * FROM orders WHERE user_id = ?");
 * NplusOneDetector.recordQuery("SELECT * FROM orders WHERE user_id = ?");
 * // ... repeated N times
 *
 * // At request end:
 * NplusOneDetector.endScope(); // Logs warning if threshold exceeded
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose N+1 query pattern detection
 * @doc.layer platform
 * @doc.pattern Diagnostic, Observer
 */
public final class NplusOneDetector {
    private static final Logger log = LoggerFactory.getLogger(NplusOneDetector.class);

    /** Default threshold: warn if same query fires 5+ times in one scope */
    private static int threshold = 5;

    /** Per-thread scope tracking */
    private static final ThreadLocal<Scope> CURRENT_SCOPE = new ThreadLocal<>();

    /** Global aggregate statistics */
    private static final ConcurrentHashMap<String, AtomicInteger> VIOLATION_COUNTS =
            new ConcurrentHashMap<>();

    private NplusOneDetector() {}

    /**
     * Sets the detection threshold (default 5).
     */
    public static void setThreshold(int n) {
        threshold = Math.max(2, n);
    }

    /**
     * Begins a new detection scope for the current thread.
     *
     * @param scopeName E.g. "GET /api/users" or a request ID
     */
    public static void beginScope(String scopeName) {
        CURRENT_SCOPE.set(new Scope(scopeName, Instant.now()));
    }

    /**
     * Records a query execution in the current scope.
     *
     * @param normalizedSql The parameterized SQL template (not the literal values)
     */
    public static void recordQuery(String normalizedSql) {
        Scope scope = CURRENT_SCOPE.get();
        if (scope == null) return;

        scope.queryCounts.merge(normalizedSql, 1, Integer::sum);
    }

    /**
     * Ends the current scope, analyzes queries, and logs warnings for N+1 patterns.
     *
     * @return List of detected violations (empty if none)
     */
    public static List<Violation> endScope() {
        Scope scope = CURRENT_SCOPE.get();
        CURRENT_SCOPE.remove();
        if (scope == null) return List.of();

        Duration elapsed = Duration.between(scope.startTime, Instant.now());
        List<Violation> violations = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : scope.queryCounts.entrySet()) {
            if (entry.getValue() >= threshold) {
                Violation v = new Violation(
                    scope.name,
                    entry.getKey(),
                    entry.getValue(),
                    elapsed
                );
                violations.add(v);

                log.warn("N+1 DETECTED in [{}]: query executed {}x (threshold={}): {}",
                    scope.name, entry.getValue(), threshold,
                    truncate(entry.getKey(), 120));

                VIOLATION_COUNTS
                    .computeIfAbsent(entry.getKey(), k -> new AtomicInteger())
                    .addAndGet(entry.getValue());
            }
        }

        return violations;
    }

    /**
     * Returns aggregate violation counts across all scopes (for monitoring dashboards).
     */
    public static Map<String, Integer> getAggregateViolations() {
        Map<String, Integer> result = new HashMap<>();
        VIOLATION_COUNTS.forEach((k, v) -> result.put(k, v.get()));
        return Collections.unmodifiableMap(result);
    }

    /**
     * Resets all aggregate stats (useful for testing).
     */
    public static void resetStats() {
        VIOLATION_COUNTS.clear();
    }

    private static String truncate(String s, int maxLen) {
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    // ── Internal types ───────────────────────────────────────────────────

    private static class Scope {
        final String name;
        final Instant startTime;
        final Map<String, Integer> queryCounts = new HashMap<>();

        Scope(String name, Instant startTime) {
            this.name = name;
            this.startTime = startTime;
        }
    }

    /**
     * Represents a detected N+1 violation.
     */
    public record Violation(
        String scopeName,
        String querySql,
        int executionCount,
        Duration scopeDuration
    ) {}
}
