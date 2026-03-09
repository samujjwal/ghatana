package com.ghatana.platform.domain.domain;

/**
 * Canonical severity levels used across all EventCloud services.
 *
 * Consolidated type-safe enumeration for classifying the severity/impact level
 * of events, alerts, and issues. Provides consistent severity semantics across
 * all modules and enables compatibility wrapping of module-specific variants.
 *
 * Supported Severity Levels (ordered highest to lowest severity): - CRITICAL:
 * System failure or complete service unavailability, immediate action required
 * - ERROR: Operational error or major issue, significant functionality impaired
 * - HIGH: Significant problem, important features affected - MEDIUM: Moderate
 * problem or warning condition - LOW: Minor issue or informational condition -
 * INFO: Informational message, normal operation - WARNING: Warning condition,
 * potential issue - INFORMATIONAL: Routine informational message - NONE: No
 * severity (used for events that don't fit other categories)
 *
 * Architecture Role: - Used by: Monitoring systems, alert routing, logging,
 * error reporting, analytics - Created by: Event processors, error handlers,
 * monitoring services, log aggregators - Stored in: Event metadata, alert
 * records, log entries, metrics - Purpose: Categorize event impact, route to
 * appropriate team, trigger alerting rules
 *
 * Severity vs. Priority: - Severity: Objective classification of the problem's
 * nature/impact (what happened) - Priority: Subjective assessment of
 * urgency/handling (how quickly to respond) - Example: A CRITICAL event may
 * have LOW priority if it occurs at 2 AM on weekends
 *
 * Usage Pattern:  {@code
 * // Classify event by severity
 * event.setSeverity(Severity.ERROR);
 *
 * // Route based on severity
 * if (event.severity() == Severity.CRITICAL) {
 *     escalationService.page(event);
 *     metricService.recordCritical(event);
 * } else if (event.severity().ordinal() <= Severity.HIGH.ordinal()) {
 *     alertService.createAlert(event);
 * } else if (event.severity() == Severity.INFO) {
 *     logger.info("Informational event: {}", event);
 * }
 *
 * // Use in reporting
 * Map<Severity, Long> counts = events.stream()
 * .collect(groupingBy(Event::severity, counting())); }
 *
 * Mapping Multiple Severity Schemes: When consolidating other severity enums,
 * map to these canonical values: - syslog EMERG/ALERT/CRIT → Severity.CRITICAL
 * - syslog ERR → Severity.ERROR - syslog WARNING → Severity.WARNING - syslog
 * NOTICE/INFO → Severity.INFO - syslog DEBUG → Severity.INFORMATIONAL
 *
 * System Integration Points: 1. Event Ingestion: Parse source severity and map
 * to canonical value 2. Monitoring Dashboards: Color-code by severity
 * (CRITICAL=red, ERROR=orange, etc.) 3. Alert Rules: Trigger conditions based
 * on severity thresholds 4. Log Aggregation: Separate streams by severity for
 * analysis 5. SLA Tracking: Response time SLAs may depend on severity 6.
 * Metrics: Separate counters for events by severity level 7. Retention Policy:
 * Storage duration often depends on severity
 *
 * Thread Safety: Enum constants are immutable and thread-safe. Performance:
 * O(1) comparison via ordinal comparison.
 *
 * @doc.type enum
 * @doc.layer domain
 * @doc.purpose type-safe enumeration for classifying event/issue severity
 * levels
 * @doc.pattern enum-constants
 * @doc.test-hints test each severity level in routing logic, test ordinal
 * ordering
 * @see Priority (related enum for operation priority)
 * @see EventParameterType (used in event metadata)
 */
public enum Severity {
    /**
     * CRITICAL: System failure or complete service unavailability.
     *
     * Indicates: Complete loss of service, system crash, data loss, security
     * breach, or total feature unavailability.
     *
     * Example: Database down, authentication service unavailable, all events
     * failing to process, data corruption detected.
     *
     * Response: Immediate page-out, establish war room, emergency response.
     */
    CRITICAL,
    /**
     * ERROR: Operational error with significant functionality impaired.
     *
     * Indicates: Major functionality not working, user-facing features broken,
     * degraded service quality, or data inconsistencies.
     *
     * Example: API returning 500 errors, background job failed, database query
     * timeout, integration service unreachable.
     *
     * Response: Create urgent ticket, escalate to on-call if outside business
     * hours.
     */
    ERROR,
    /**
     * HIGH: Significant problem affecting important features.
     *
     * Indicates: Important but not critical functionality impacted, partial
     * degradation, user inconvenience.
     *
     * Example: API response slow, optional feature not working, cache miss
     * increasing response time.
     *
     * Response: Create ticket, include in next sprint or emergency deployment.
     */
    HIGH,
    /**
     * MEDIUM: Moderate problem or warning condition.
     *
     * Indicates: Non-critical issue, warning signal, potential problem
     * developing, or degraded performance.
     *
     * Example: Memory usage trending up, queue depth increasing, retry rate
     * elevated, deprecated API being used.
     *
     * Response: Create issue for future resolution, monitor trend.
     */
    MEDIUM,
    /**
     * LOW: Minor issue or edge case problem.
     *
     * Indicates: Isolated issue, edge case, minimal impact, or cosmetic
     * problem.
     *
     * Example: Rare parsing error, minor UI glitch, non-critical configuration
     * warning.
     *
     * Response: Document in backlog, address in regular maintenance.
     */
    LOW,
    /**
     * INFO: Informational message about normal operation.
     *
     * Indicates: Normal event, standard operation, routine activity, or
     * informational milestone.
     *
     * Example: Service started, request processed, scheduled task completed,
     * user logged in.
     *
     * Response: None, log for audit/analytics purposes.
     */
    INFO,
    /**
     * WARNING: Warning condition indicating potential issue.
     *
     * Indicates: Potential problem, suboptimal condition, or preventive
     * warning, but system still operating.
     *
     * Example: Disk space low, connection pool nearing limit, unrecognized
     * request pattern detected, experimental code path used.
     *
     * Response: Monitor for escalation, create maintenance issue.
     */
    WARNING,
    /**
     * INFORMATIONAL: Routine informational message.
     *
     * Indicates: General informational message, debugging information, or
     * verbose activity tracking.
     *
     * Example: Method entry/exit, variable values at checkpoint, cache hit,
     * request details.
     *
     * Response: None, typically only logged at DEBUG level.
     */
    INFORMATIONAL,
    /**
     * NONE: No specific severity classification.
     *
     * Used for events that don't fit traditional severity categories or when
     * severity is not applicable.
     *
     * Response: No severity-based routing applied.
     */
    NONE,
    /**
     * UNKNOWN: Unknown or unspecified severity.
     *
     * Used when the source system does not provide a mapped severity value or
     * when an incoming value cannot be mapped to any existing canonical level.
     * Maps conceptually to NONE (no routing) but preserved as a first-class
     * enum constant for backward compatibility with older modules.
     */
    UNKNOWN,
    /**
     * HINT: Very low-severity hint or suggestion, typically used for guidance
     * or developer-facing notes. Conceptually similar to INFORMATIONAL but kept
     * separate when callers require a distinct constant.
     */
    HINT
}
