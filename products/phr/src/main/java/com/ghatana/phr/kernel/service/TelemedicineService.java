package com.ghatana.phr.kernel.service;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.service.AbstractDataService;
import io.activej.promise.Promise;
import io.activej.promise.Promises;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Telemedicine Session Service for PHR.
 *
 * <p>Manages virtual appointment sessions: scheduling, joining, completing, and
 * cancellation. Stores session metadata and join-link references (actual
 * video-conferencing integration is handled by the telemedicine platform adapter
 * — this service manages the PHR-side state and audit trail).</p>
 *
 * @doc.type class
 * @doc.purpose PHR telemedicine — virtual session lifecycle management
 * @doc.layer product
 * @doc.pattern Service
 * @author Ghatana PHR Team
 * @since 1.0.0
 */
public class TelemedicineService extends AbstractDataService {

    private static final String SESSION_DATASET = "phr.telemedicine.sessions";

    public TelemedicineService(KernelContext context) {
        super(context);
    }

    @Override
    public String getName() {
        return "telemedicine";
    }

    @Override
    protected Promise<Void> initializeDatasets() {
        return createSchema(
            SESSION_DATASET,
            Map.of("id", "string", "patientId", "string", "status", "string",
                "scheduledAt", "timestamp"),
            Map.of("retention", "10years")
        );
    }

    // ==================== Core Operations ====================

    /**
     * Schedules a new telemedicine session.
     *
     * @param session the session to schedule (id may be null; will be generated)
     * @return Promise containing the stored session
     */
    public Promise<TeleSession> scheduleSession(TeleSession session) {
        ensureRunning();

        validateRequired(session.patientId(), "patientId");
        validateRequired(session.providerId(), "providerId");
        validateRequired(session.scheduledAt(), "scheduledAt");

        String id = session.id() != null ? session.id() : generateId("tele");
        TeleSession toStore = new TeleSession(
            id,
            session.patientId(),
            session.providerId(),
            session.scheduledAt(),
            session.durationMinutes(),
            session.platform(),
            session.joinUrl(),
            SessionStatus.SCHEDULED,
            null,
            null,
            null
        );

        return writeSession(toStore, "SCHEDULE_SESSION", "Scheduled telemedicine session");
    }

    public Promise<TeleSession> startSession(String sessionId) {
        ensureRunning();

        return getSession(sessionId)
            .then(opt -> requireFound(opt, sessionId))
            .then(existing -> {
                if (existing.status() == SessionStatus.IN_PROGRESS) {
                    return Promise.of(existing);
                }
                if (existing.status() != SessionStatus.SCHEDULED) {
                    return Promise.<TeleSession>ofException(new IllegalStateException(
                        "Cannot start session in status: " + existing.status()));
                }
                TeleSession updated = new TeleSession(
                    existing.id(), existing.patientId(), existing.providerId(),
                    existing.scheduledAt(), existing.durationMinutes(),
                    existing.platform(), existing.joinUrl(),
                    SessionStatus.IN_PROGRESS, Instant.now(), null, null
                );
                return writeSession(updated, "START_SESSION", "Session started");
            });
    }

    public Promise<TeleSession> completeSession(String sessionId, String notes) {
        ensureRunning();

        return getSession(sessionId)
            .then(opt -> requireFound(opt, sessionId))
            .then(existing -> {
                if (existing.status() == SessionStatus.COMPLETED) {
                    return Promise.of(existing);
                }
                if (existing.status() != SessionStatus.IN_PROGRESS) {
                    return Promise.<TeleSession>ofException(new IllegalStateException(
                        "Cannot complete session in status: " + existing.status()));
                }
                Duration actual = existing.startedAt() != null
                    ? Duration.between(existing.startedAt(), Instant.now())
                    : null;
                TeleSession updated = new TeleSession(
                    existing.id(), existing.patientId(), existing.providerId(),
                    existing.scheduledAt(), existing.durationMinutes(),
                    existing.platform(), existing.joinUrl(),
                    SessionStatus.COMPLETED, existing.startedAt(), Instant.now(), notes
                );
                return writeSession(updated, "COMPLETE_SESSION",
                    "Session completed" + (actual != null ? " after " + actual.toMinutes() + "m" : ""));
            });
    }

    public Promise<TeleSession> cancelSession(String sessionId, String reason) {
        ensureRunning();

        return getSession(sessionId)
            .then(opt -> requireFound(opt, sessionId))
            .then(existing -> {
                if (existing.status() == SessionStatus.COMPLETED
                    || existing.status() == SessionStatus.CANCELLED) {
                    return Promise.<TeleSession>ofException(new IllegalStateException(
                        "Cannot cancel session in status: " + existing.status()));
                }
                TeleSession updated = new TeleSession(
                    existing.id(), existing.patientId(), existing.providerId(),
                    existing.scheduledAt(), existing.durationMinutes(),
                    existing.platform(), existing.joinUrl(),
                    SessionStatus.CANCELLED, existing.startedAt(), null, reason
                );
                return writeSession(updated, "CANCEL_SESSION", "Session cancelled: " + reason);
            });
    }

    public Promise<Optional<TeleSession>> getSession(String sessionId) {
        ensureRunning();
        return readRecord(SESSION_DATASET, sessionId, TeleSession.class);
    }

    public Promise<List<TeleSession>> getPatientSessions(String patientId) {
        ensureRunning();

        return queryRecords(
            SESSION_DATASET,
            "patientId = :patientId",
            Map.of("patientId", patientId),
            500,
            0,
            TeleSession.class
        ).map(sessions -> sessions.stream()
            .sorted((a, b) -> b.scheduledAt().compareTo(a.scheduledAt()))
            .toList());
    }

    // ==================== Private Helpers ====================

    private Promise<TeleSession> writeSession(TeleSession session, String action, String detail) {
        return createRecord(
            SESSION_DATASET,
            session.id(),
            session,
            Map.of("patientId", session.patientId(), "status", session.status().name()),
            "TeleSession",
            1
        ).then(stored -> audit(action, stored.patientId(), detail + " [" + stored.id() + "]")
            .map($ -> stored));
    }

    private <T> Promise<T> requireFound(Optional<T> opt, String id) {
        return opt.<Promise<T>>map(Promise::of).orElseGet(() ->
            Promise.ofException(new IllegalStateException("Session not found: " + id)));
    }

    // ==================== Inner Types ====================

    /**
     * A telemedicine session record.
     *
     * @param id             unique session identifier
     * @param patientId      patient participating in the session
     * @param providerId     provider conducting the session
     * @param scheduledAt    when the session is/was scheduled
     * @param durationMinutes expected or actual session duration in minutes
     * @param platform       telemedicine platform identifier (e.g. "ZOOM_HEALTH", "CUSTOM")
     * @param joinUrl        deep-link URL to join the session (may be null before provisioning)
     * @param status         session lifecycle status
     * @param startedAt      when the session actually started (null if not yet started)
     * @param endedAt        when the session ended (null if not yet ended)
     * @param notes          post-session notes or cancellation reason
     */
    public record TeleSession(
            String id,
            String patientId,
            String providerId,
            Instant scheduledAt,
            int durationMinutes,
            String platform,
            String joinUrl,
            SessionStatus status,
            Instant startedAt,
            Instant endedAt,
            String notes
    ) {}

    /** Telemedicine session lifecycle status. */
    public enum SessionStatus {
        SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED, NO_SHOW
    }
}
