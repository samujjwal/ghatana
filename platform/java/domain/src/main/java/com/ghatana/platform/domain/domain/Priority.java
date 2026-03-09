package com.ghatana.platform.domain.domain;

/**
 * Canonical priority levels used across all EventCloud services.
 * 
 * Consolidated type-safe enumeration for prioritizing events, alerts, and operations.
 * Provides consistent priority semantics across all modules and prevents duplication of priority definitions.
 * 
 * Supported Priority Levels (ordered highest to lowest urgency):
 * - CRITICAL: Immediate action required, system or data integrity at risk
 * - HIGH: Urgent issue, significant impact, needs prompt attention
 * - MEDIUM: Important but not urgent, moderate impact, schedule for near-term resolution
 * - NORMAL: Routine operation, low impact, standard processing priority
 * - LOW: Minor issue, minimal impact, can be deferred
 * 
 * Architecture Role:
 * - Used by: Event processors, alert routing systems, job schedulers, resource allocation
 * - Created by: Event ingestion, monitoring systems, pattern matching engines
 * - Stored in: Event metadata, alert definitions, SLA tracking
 * - Purpose: Determine processing order, routing decisions, resource allocation, SLA compliance
 * 
 * Priority Impact:
 * - Event Processing: CRITICAL events processed with highest throughput allocation
 * - Routing: CRITICAL → immediate escalation paths, LOW → batch processing
 * - Alerting: CRITICAL triggers page-on-call, MEDIUM creates ticket, LOW → summary report
 * - Storage: CRITICAL events hot-stored, LOW can use cheaper cold storage after retention period
 * - Retention: CRITICAL retained longer, LOW purged after standard period
 * 
 * Usage Pattern:
 * {@code
 * // Assign priority to event
 * event.setPriority(Priority.HIGH);
 * 
 * // Route based on priority
 * if (event.priority() == Priority.CRITICAL) {
 *     escalationService.notifyOnCall(event);
 * } else if (event.priority().compareTo(Priority.MEDIUM) >= 0) {
 *     alertService.createTicket(event);
 * }
 * 
 * // Use in collections for sorting
 * events.stream()
 *     .sorted((e1, e2) -> e2.priority().ordinal() - e1.priority().ordinal())
 *     .forEach(eventProcessor::process);
 * }
 * 
 * Priority Ordering:
 * Enum ordinal order matches urgency hierarchy (lower ordinal = higher priority).
 * Can be compared with compareTo() for priority-based decision making.
 * 
 * System Integration Points:
 * 1. Event Ingestion: Infer priority from source system or assign default
 * 2. Pattern Matching: Rules may escalate priority based on patterns
 * 3. Alert System: Route based on priority (CRITICAL → PagerDuty, HIGH → Slack, etc.)
 * 4. Resource Scheduler: Allocate CPU/memory based on priority
 * 5. SLA Tracking: Enforce response time SLAs based on priority
 * 6. Retention Policy: Store duration depends on priority
 * 
 * Thread Safety: Enum constants are immutable and thread-safe.
 * Performance: O(1) comparison via ordinal comparison.
 * 
 * @doc.type enum
 * @doc.layer domain
 * @doc.purpose type-safe enumeration for prioritizing events and operations
 * @doc.pattern enum-constants
 * @doc.test-hints test each priority level in decision trees, test compareTo/ordering
 * @see Severity (related enum for severity classification)
 * @see EventParameterType (used in event metadata)
 */
public enum Priority {
    /**
     * CRITICAL: Immediate action required.
     * 
     * Used when: System or data integrity at risk, service degradation occurring,
     * customer-impacting outage, or security incident.
     * 
     * Example: Database replication failure, authentication service down,
     * data corruption detected, DDoS attack in progress.
     * 
     * SLA: Respond within 15 minutes, resolve within 1 hour.
     * Escalation: Page on-call engineer immediately.
     */
    CRITICAL,
    
    /**
     * HIGH: Urgent issue requiring prompt attention.
     * 
     * Used when: Significant impact but not immediate threat, degraded functionality,
     * users experiencing issues, but workarounds exist.
     * 
     * Example: Slow API response times, partial service unavailability,
     * data sync delays, high error rates.
     * 
     * SLA: Respond within 1 hour, resolve within 4 hours.
     * Escalation: Create urgent ticket, notify team lead.
     */
    HIGH,
    
    /**
     * MEDIUM: Important but not urgent.
     * 
     * Used when: Moderate impact, affects non-critical functionality,
     * minor degradation, or preventive improvements.
     * 
     * Example: UI latency, non-critical feature malfunction,
     * minor data inconsistencies, performance optimization opportunity.
     * 
     * SLA: Respond within 4 hours, resolve within 24 hours.
     * Escalation: Create standard ticket for next sprint.
     */
    MEDIUM,
    
    /**
     * NORMAL: Routine operation or minor issue.
     * 
     * Used when: Standard processing, low impact, minimal user inconvenience,
     * or cosmetic issues.
     * 
     * Example: Log file cleanup, cache refresh, routine maintenance,
     * minor UI inconsistency.
     * 
     * SLA: Address within 2 weeks, integrate in normal sprint work.
     * Escalation: None, standard process.
     */
    NORMAL,
    
    /**
     * LOW: Minimal impact, can be deferred.
     * 
     * Used when: Non-blocking issues, nice-to-have improvements,
     * minor bugs without workarounds, or enhancement requests.
     * 
     * Example: Cosmetic UI bug, typo in error message,
     * performance micro-optimization, documentation updates.
     * 
     * SLA: Address at backlog discretion, no fixed timeline.
     * Escalation: None, backlog item only.
     */
    LOW
}
