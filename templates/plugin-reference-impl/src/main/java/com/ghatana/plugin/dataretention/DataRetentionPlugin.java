package com.ghatana.plugin.dataretention;

import com.ghatana.platform.plugin.Plugin;
import io.activej.promise.Promise;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * DataRetentionPlugin — reference plugin interface.
 *
 * <p>Illustrates the three canonical plugin design patterns:</p>
 *
 * <h2>Pattern 1 — Policy Evaluation</h2>
 * <p>{@link #evaluateRetentionPolicy} is a deterministic rule check:
 * given a data record, return whether it is within policy. Fully synchronous
 * inside an async shell (no I/O, no side effects). Suitable for compliance
 * checks, threshold gates, and classification decisions.</p>
 *
 * <h2>Pattern 2 — Event-Driven Processing</h2>
 * <p>{@link #handleDataCreated} fires when any data record is stored. The
 * plugin registers itself with the kernel event bus and reacts to domain
 * events. This is the pattern for audit trail entries, expiry scheduling,
 * real-time analytics, and cross-product notification.</p>
 *
 * <h2>Pattern 3 — Human Approval Integration</h2>
 * <p>{@link #requestRetentionException} escalates to the
 * {@code plugin-human-approval} workflow when automated evaluation cannot
 * decide (e.g., legal hold, regulatory grey area). The plugin creates an
 * approval request, suspends the caller, and resumes when a human approves
 * or rejects. This is the pattern for regulated exceptions and override flows.</p>
 *
 * @doc.type interface
 * @doc.purpose Reference: data retention policy plugin demonstrating all 3 plugin patterns
 * @doc.layer platform
 * @doc.pattern Plugin
 */
public interface DataRetentionPlugin extends Plugin {

    // ──────────────────────────────────────────────────────────────────────────
    // Pattern 1 — Policy Evaluation
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Evaluates whether a data record is within retention policy.
     *
     * <p>This is a pure, deterministic policy check — no side effects, no I/O.
     * The implementation must be idempotent and fast (target &lt;1 ms).
     *
     * @param tenantId  the tenant owning the record
     * @param record    the data record to evaluate
     * @return a {@code Promise<RetentionDecision>} indicating compliant, expired, or hold
     */
    Promise<RetentionDecision> evaluateRetentionPolicy(String tenantId, DataRecord record);

    /**
     * Returns the configured retention period for a given data classification.
     *
     * @param tenantId           tenant scope
     * @param dataClassification e.g. "PHI", "FINANCIAL", "OPERATIONAL"
     * @return the configured retention duration for that classification
     */
    Promise<Duration> getRetentionPeriod(String tenantId, String dataClassification);

    // ──────────────────────────────────────────────────────────────────────────
    // Pattern 2 — Event-Driven Processing
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Handles a data-created event.
     *
     * <p>Called by the kernel event bus whenever a new record is stored.
     * The plugin schedules an expiry task and records an audit entry.
     * Implementations must be idempotent — the event may be delivered more
     * than once (at-least-once guarantee).
     *
     * @param event the data-created domain event
     * @return a {@code Promise<Void>} completing when the event is processed
     */
    Promise<Void> handleDataCreated(DataCreatedEvent event);

    /**
     * Handles a data-accessed event for audit trail purposes.
     *
     * @param event the data-accessed domain event
     * @return a {@code Promise<Void>} completing when the event is processed
     */
    Promise<Void> handleDataAccessed(DataAccessedEvent event);

    /**
     * Returns all records due for expiry within the given look-ahead window.
     *
     * @param tenantId   tenant scope
     * @param lookAhead  how far into the future to look
     * @return list of records whose retention period expires within the window
     */
    Promise<List<DataRecord>> findExpiring(String tenantId, Duration lookAhead);

    // ──────────────────────────────────────────────────────────────────────────
    // Pattern 3 — Human Approval Integration
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Requests a retention exception through the human-approval workflow.
     *
     * <p>Used when a record would normally expire but a business or legal
     * reason requires extending its retention. The caller suspends until a
     * human approves or rejects via the approval plugin. If approved, the
     * retention policy is extended; if rejected, the record is scheduled for
     * immediate deletion.
     *
     * @param tenantId  tenant scope
     * @param record    the record requiring an exception
     * @param reason    the stated business/legal reason for the exception
     * @param requestor the identity requesting the exception
     * @return an {@code ApprovalOutcome} indicating approved or rejected
     */
    Promise<ApprovalOutcome> requestRetentionException(
        String tenantId,
        DataRecord record,
        String reason,
        String requestor
    );

    // ──────────────────────────────────────────────────────────────────────────
    // Domain records
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Represents a data record subject to retention policy.
     */
    record DataRecord(
        String recordId,
        String tenantId,
        String dataClassification,
        Instant createdAt,
        Instant lastAccessedAt,
        boolean legalHold,
        java.util.Map<String, String> metadata
    ) {}

    /**
     * Event emitted when a data record is created.
     */
    record DataCreatedEvent(
        String eventId,
        String tenantId,
        DataRecord record,
        String actorId,
        Instant occurredAt
    ) {}

    /**
     * Event emitted when a data record is accessed.
     */
    record DataAccessedEvent(
        String eventId,
        String tenantId,
        String recordId,
        String actorId,
        String accessPurpose,
        Instant occurredAt
    ) {}

    /**
     * Result of a retention policy evaluation.
     */
    enum Decision { COMPLIANT, EXPIRED, LEGAL_HOLD, EXCEPTION_GRANTED }

    record RetentionDecision(
        Decision decision,
        String reason,
        Instant evaluatedAt,
        java.util.Optional<Instant> expiresAt
    ) {}

    /**
     * Outcome of a human approval workflow.
     */
    enum ApprovalOutcome { APPROVED, REJECTED, TIMEOUT }
}
