/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.ledger.service;

import com.ghatana.appplatform.ledger.domain.Currency;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Reconciliation break aging and escalation tracker (STORY-K16-014).
 *
 * <p>Tracks the lifecycle of reconciliation breaks produced by
 * {@link ReconciliationService}. Each break is identified by a unique
 * {@code breakId} and progresses through the following severity tiers based on
 * elapsed time since detection:
 *
 * <pre>
 * T+0  → NEW        (just reported, no age yet)
 * T+1  → AGED       (≥ 1 day; soft escalation alert)
 * T+3  → ESCALATED  (≥ 3 days; owner notification required)
 * T+5  → CRITICAL   (≥ 5 days; executive escalation)
 * </pre>
 *
 * <p>Break lifecycle transitions:
 * <pre>
 * OPEN → INVESTIGATING → RESOLVED → CLOSED
 * </pre>
 *
 * <p>Resolution requires a non-blank {@code resolutionNotes} field and a valid
 * {@code approverId}. Closing a break is only permitted after it has been
 * resolved.
 *
 * <h2>Escalation notifications</h2>
 * <p>Callers register {@link EscalationListener}s to receive age-tier transitions.
 * Listeners are fired once per tier per break, de-duplicated by the tracker.
 *
 * @doc.type class
 * @doc.purpose Reconciliation break aging, lifecycle management, and escalation (K16-014)
 * @doc.layer product
 * @doc.pattern Service
 */
public final class ReconciliationBreakTracker {

    /** Registry of all tracked breaks, keyed by breakId. */
    private final Map<String, BreakRecord> breaks = new ConcurrentHashMap<>();

    private final List<EscalationListener> escalationListeners = new ArrayList<>();

    // ─── Break reporting ──────────────────────────────────────────────────────

    /**
     * Reports a new reconciliation break derived from a {@link ReconciliationService} result.
     *
     * <p>Only {@link ReconciliationService.ReconciliationStatus#BREAK BREAK} items should
     * be submitted. The break is registered at severity {@link AgeSeverity#NEW NEW} and
     * lifecycle status {@link BreakStatus#OPEN OPEN}.
     *
     * @param item the reconciliation item in BREAK or PENDING_REVIEW status; not null
     * @return newly created {@link BreakRecord} with a generated {@code breakId}
     * @throws IllegalArgumentException if the item is MATCHED (not a real break)
     */
    public BreakRecord reportBreak(ReconciliationService.ReconciliationItem item) {
        Objects.requireNonNull(item, "item");
        if (item.status() == ReconciliationService.ReconciliationStatus.MATCHED) {
            throw new IllegalArgumentException(
                "Cannot report a MATCHED item as a break; only BREAK or PENDING_REVIEW.");
        }
        String breakId = UUID.randomUUID().toString();
        BreakRecord record = new BreakRecord(
            breakId,
            item.request().accountId(),
            item.request().currency(),
            item.difference(),
            item.request().reconId(),
            BreakStatus.OPEN,
            AgeSeverity.NEW,
            item.reconAt(),
            null,   // resolutionNotes
            null,   // approverId
            null    // resolvedAt
        );
        breaks.put(breakId, record);
        return record;
    }

    // ─── Aging ───────────────────────────────────────────────────────────────

    /**
     * Evaluates aging for all tracked (non-closed) breaks as of the given point in time.
     *
     * <p>For each break that advances to a higher severity tier, the new break record is
     * persisted and registered {@link EscalationListener}s are notified.
     *
     * <p>Aging is monotonic — severity never decreases. Closed breaks are skipped.
     *
     * @param asOf evaluation timestamp; not null
     */
    public void ageBreaks(Instant asOf) {
        Objects.requireNonNull(asOf, "asOf");
        breaks.replaceAll((breakId, record) -> {
            if (record.status() == BreakStatus.CLOSED) return record;
            long days = ChronoUnit.DAYS.between(record.detectedAt(), asOf);
            AgeSeverity newSeverity = computeSeverity(days);
            if (newSeverity.ordinal() > record.severity().ordinal()) {
                BreakRecord updated = record.withSeverity(newSeverity);
                fireEscalation(updated);
                return updated;
            }
            return record;
        });
    }

    // ─── Lifecycle transitions ────────────────────────────────────────────────

    /**
     * Transitions a break to {@link BreakStatus#INVESTIGATING INVESTIGATING}.
     *
     * @param breakId id of the break to investigate; must exist
     * @throws IllegalArgumentException  if breakId is unknown
     * @throws IllegalStateException     if the break is not in OPEN status
     */
    public BreakRecord startInvestigation(String breakId) {
        return transition(breakId, BreakStatus.OPEN, BreakStatus.INVESTIGATING, null, null);
    }

    /**
     * Resolves a break with mandatory notes and approver.
     *
     * @param breakId         id of the break; must exist
     * @param resolutionNotes human-readable explanation; must not be blank
     * @param approverId      identifier of the approving user/system; must not be blank
     * @param resolvedAt      timestamp of resolution; not null
     * @return updated break record
     * @throws IllegalArgumentException if notes or approverId are blank, or breakId unknown
     * @throws IllegalStateException    if break is not in INVESTIGATING status
     */
    public BreakRecord resolveBreak(String breakId, String resolutionNotes,
                                    String approverId, Instant resolvedAt) {
        Objects.requireNonNull(resolutionNotes, "resolutionNotes");
        Objects.requireNonNull(approverId, "approverId");
        Objects.requireNonNull(resolvedAt, "resolvedAt");
        if (resolutionNotes.isBlank()) {
            throw new IllegalArgumentException("resolutionNotes must not be blank");
        }
        if (approverId.isBlank()) {
            throw new IllegalArgumentException("approverId must not be blank");
        }
        BreakRecord existing = getOrThrow(breakId);
        if (existing.status() != BreakStatus.INVESTIGATING) {
            throw new IllegalStateException(
                "Break must be INVESTIGATING to resolve; current=" + existing.status());
        }
        BreakRecord resolved = new BreakRecord(
            existing.breakId(), existing.accountId(), existing.currency(),
            existing.difference(), existing.reconId(),
            BreakStatus.RESOLVED, existing.severity(),
            existing.detectedAt(), resolutionNotes, approverId, resolvedAt);
        breaks.put(breakId, resolved);
        return resolved;
    }

    /**
     * Closes a resolved break.
     *
     * @param breakId id of the break; must exist and be RESOLVED
     * @return updated break record with status CLOSED
     * @throws IllegalStateException if break is not RESOLVED
     */
    public BreakRecord closeBreak(String breakId) {
        return transition(breakId, BreakStatus.RESOLVED, BreakStatus.CLOSED, null, null);
    }

    // ─── Dashboard ────────────────────────────────────────────────────────────

    /**
     * Returns a summary of break counts grouped by lifecycle status.
     *
     * @return immutable break summary snapshot
     */
    public BreakSummary getBreakSummary() {
        Map<BreakStatus, Long> counts = breaks.values().stream()
            .collect(Collectors.groupingBy(BreakRecord::status, Collectors.counting()));
        long open          = counts.getOrDefault(BreakStatus.OPEN, 0L);
        long investigating = counts.getOrDefault(BreakStatus.INVESTIGATING, 0L);
        long resolved      = counts.getOrDefault(BreakStatus.RESOLVED, 0L);
        long closed        = counts.getOrDefault(BreakStatus.CLOSED, 0L);

        Map<AgeSeverity, Long> bySeverity = breaks.values().stream()
            .filter(b -> b.status() != BreakStatus.CLOSED)
            .collect(Collectors.groupingBy(BreakRecord::severity, Collectors.counting()));

        return new BreakSummary(open, investigating, resolved, closed,
                Map.copyOf(bySeverity), Instant.now());
    }

    /**
     * Returns a specific break record by ID.
     *
     * @param breakId break identifier
     * @throws IllegalArgumentException if unknown
     */
    public BreakRecord getBreak(String breakId) {
        return getOrThrow(breakId);
    }

    // ─── Listeners ───────────────────────────────────────────────────────────

    /**
     * Registers a listener to receive escalation notifications when a break ages
     * into a higher severity tier.
     *
     * @param listener escalation recipient; not null
     */
    public void addEscalationListener(EscalationListener listener) {
        escalationListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private static AgeSeverity computeSeverity(long days) {
        if (days >= 5) return AgeSeverity.CRITICAL;
        if (days >= 3) return AgeSeverity.ESCALATED;
        if (days >= 1) return AgeSeverity.AGED;
        return AgeSeverity.NEW;
    }

    private BreakRecord transition(String breakId, BreakStatus required, BreakStatus next,
                                   String notes, String approverId) {
        BreakRecord existing = getOrThrow(breakId);
        if (existing.status() != required) {
            throw new IllegalStateException(
                "Break must be " + required + " to transition to " + next
                + "; current=" + existing.status());
        }
        BreakRecord updated = new BreakRecord(
            existing.breakId(), existing.accountId(), existing.currency(),
            existing.difference(), existing.reconId(),
            next, existing.severity(),
            existing.detectedAt(), notes, approverId, null);
        breaks.put(breakId, updated);
        return updated;
    }

    private BreakRecord getOrThrow(String breakId) {
        BreakRecord record = breaks.get(breakId);
        if (record == null) {
            throw new IllegalArgumentException("Unknown breakId: " + breakId);
        }
        return record;
    }

    private void fireEscalation(BreakRecord record) {
        escalationListeners.forEach(l -> l.onEscalation(record));
    }

    // ─── Inner types ──────────────────────────────────────────────────────────

    /**
     * Break lifecycle status.
     */
    public enum BreakStatus {
        /** Newly reported; awaiting investigation. */
        OPEN,
        /** Under active investigation. */
        INVESTIGATING,
        /** Resolved with notes and approver; awaiting formal close. */
        RESOLVED,
        /** Formally closed; no further action required. */
        CLOSED
    }

    /**
     * Age-based severity tier for a break.
     *
     * <p>Tiers are monotonically ordered (NEW &lt; AGED &lt; ESCALATED &lt; CRITICAL).
     */
    public enum AgeSeverity {
        /** Less than 1 day old (T+0). */
        NEW,
        /** 1–2 days old (T+1). */
        AGED,
        /** 3–4 days old (T+3). */
        ESCALATED,
        /** 5+ days old (T+5); executive escalation required. */
        CRITICAL
    }

    /**
     * Immutable snapshot of a tracked reconciliation break.
     *
     * @param breakId         unique identifier
     * @param accountId       ledger account the break belongs to
     * @param currency        currency of the difference amount
     * @param difference      absolute monetary difference that caused the break
     * @param reconId         identifier of the originating reconciliation run item
     * @param status          lifecycle status (OPEN → INVESTIGATING → RESOLVED → CLOSED)
     * @param severity        age-based severity tier
     * @param detectedAt      timestamp when the break was first reported
     * @param resolutionNotes human-readable explanation (set on RESOLVED)
     * @param approverId      approver identifier (set on RESOLVED)
     * @param resolvedAt      timestamp of resolution (set on RESOLVED)
     */
    public record BreakRecord(
            String breakId,
            UUID accountId,
            Currency currency,
            java.math.BigDecimal difference,
            UUID reconId,
            BreakStatus status,
            AgeSeverity severity,
            Instant detectedAt,
            String resolutionNotes,
            String approverId,
            Instant resolvedAt
    ) {
        /** Returns a copy of this record with an updated severity (all other fields unchanged). */
        public BreakRecord withSeverity(AgeSeverity newSeverity) {
            return new BreakRecord(breakId, accountId, currency, difference, reconId,
                status, newSeverity, detectedAt, resolutionNotes, approverId, resolvedAt);
        }
    }

    /**
     * Point-in-time summary of tracked reconciliation breaks grouped by lifecycle status.
     *
     * @param openCount          number of OPEN breaks
     * @param investigatingCount number of INVESTIGATING breaks
     * @param resolvedCount      number of RESOLVED breaks
     * @param closedCount        number of CLOSED breaks
     * @param bySeverity         count of non-closed breaks per age severity tier
     * @param snapshotAt         timestamp of the snapshot
     */
    public record BreakSummary(
            long openCount,
            long investigatingCount,
            long resolvedCount,
            long closedCount,
            Map<AgeSeverity, Long> bySeverity,
            Instant snapshotAt
    ) {
        /** Total number of active (non-closed) breaks. */
        public long totalActive() {
            return openCount + investigatingCount + resolvedCount;
        }
    }

    /**
     * Listener notified when a break ages into a higher severity tier.
     */
    @FunctionalInterface
    public interface EscalationListener {
        /** Called when {@code record} has just advanced to a new age severity tier. */
        void onEscalation(BreakRecord record);
    }
}
