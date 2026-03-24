package com.ghatana.phr.kernel.service;

import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter;
import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter.DataQueryRequest;
import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter.DataReadRequest;
import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter.DataWriteRequest;
import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter.QueryResult;
import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.util.TypedDataSerializer;
import io.activej.promise.Promise;
import io.activej.promise.Promises;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

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
public class TelemedicineService {

    private static final String SESSION_DATASET = "phr.telemedicine.sessions";
    private static final String AUDIT_DATASET   = "phr.telemedicine.audit";

    private final DataCloudKernelAdapter dataCloud;
    private volatile boolean running = false;

    /**
     * Constructs a TelemedicineService.
     *
     * @param context kernel context providing DataCloudKernelAdapter
     */
    public TelemedicineService(KernelContext context) {
        this.dataCloud = context.getDependency(DataCloudKernelAdapter.class);
    }

    /** Starts the service and initializes backing datasets. */
    public Promise<Void> start() {
        running = true;
        return initializeDatasets();
    }

    /** Stops the service. */
    public Promise<Void> stop() {
        running = false;
        return Promise.complete();
    }

    /** Returns {@code true} when the service is running. */
    public boolean isHealthy() {
        return running;
    }

    /** Returns the logical service name. */
    public String getName() {
        return "telemedicine";
    }

    // ==================== Core Operations ====================

    /**
     * Schedules a new telemedicine session.
     *
     * @param session the session to schedule (id may be null; will be generated)
     * @return Promise containing the stored session
     */
    public Promise<TeleSession> scheduleSession(TeleSession session) {
        if (!running) {
            return Promise.ofException(new IllegalStateException("Service not running"));
        }

        Objects.requireNonNull(session.patientId(), "patientId");
        Objects.requireNonNull(session.providerId(), "providerId");
        Objects.requireNonNull(session.scheduledAt(), "scheduledAt");

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

    /**
     * Marks a session as in-progress when the patient joins.
     *
     * @param sessionId the session identifier
     * @return Promise containing the updated session
     */
    public Promise<TeleSession> startSession(String sessionId) {
        if (!running) {
            return Promise.ofException(new IllegalStateException("Service not running"));
        }

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

    /**
     * Completes a session after the call ends.
     *
     * @param sessionId the session identifier
     * @param notes     optional clinical notes
     * @return Promise containing the completed session
     */
    public Promise<TeleSession> completeSession(String sessionId, String notes) {
        if (!running) {
            return Promise.ofException(new IllegalStateException("Service not running"));
        }

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

    /**
     * Cancels a scheduled session.
     *
     * @param sessionId the session identifier
     * @param reason    reason for cancellation
     * @return Promise containing the cancelled session
     */
    public Promise<TeleSession> cancelSession(String sessionId, String reason) {
        if (!running) {
            return Promise.ofException(new IllegalStateException("Service not running"));
        }

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

    /**
     * Retrieves a session by ID.
     *
     * @param sessionId the session identifier
     * @return Promise containing the session if found
     */
    public Promise<Optional<TeleSession>> getSession(String sessionId) {
        if (!running) {
            return Promise.of(Optional.empty());
        }

        return dataCloud.readData(new DataReadRequest(SESSION_DATASET, sessionId, Map.of()))
                .map(result -> {
                    if (result == null || result.getData() == null) return Optional.empty();
                    return Optional.ofNullable(TypedDataSerializer.fromBytes(result.getData(), TeleSession.class));
                })
                .whenException(e -> Promise.of(Optional.empty()));
    }

    /**
     * Returns all sessions for a patient, sorted newest-first.
     *
     * @param patientId the patient identifier
     * @return Promise containing the list of sessions
     */
    public Promise<List<TeleSession>> getPatientSessions(String patientId) {
        if (!running) {
            return Promise.of(List.of());
        }

        return dataCloud.queryData(new DataQueryRequest(
                SESSION_DATASET,
                "patientId = :patientId",
                Map.of("patientId", patientId),
                500,
                0
        )).map(QueryResult::getResults)
                .map(results -> results.stream()
                        .map(r -> TypedDataSerializer.fromBytes(r.getData(), TeleSession.class))
                        .filter(Objects::nonNull)
                        .sorted((a, b) -> b.scheduledAt().compareTo(a.scheduledAt()))
                        .toList());
    }

    // ==================== Private Helpers ====================

    private Promise<TeleSession> writeSession(TeleSession session, String action, String detail) {
        DataWriteRequest req = new DataWriteRequest(
                SESSION_DATASET,
                session.id(),
                TypedDataSerializer.toBytes(session, "TeleSession", 1),
                Map.of("patientId", session.patientId(), "status", session.status().name())
        );
        return dataCloud.writeData(req)
                .then($ -> audit(action, session.patientId(), detail + " [" + session.id() + "]"))
                .map($ -> session);
    }

    private <T> Promise<T> requireFound(Optional<T> opt, String id) {
        return opt.<Promise<T>>map(Promise::of).orElseGet(() ->
                Promise.ofException(new IllegalStateException("Session not found: " + id)));
    }

    private Promise<Void> initializeDatasets() {
        Promise<Void> sessions = dataCloud.createSchema(new DataCloudKernelAdapter.SchemaCreateRequest(
                SESSION_DATASET,
                Map.of("id", "string", "patientId", "string", "status", "string",
                        "scheduledAt", "timestamp"),
                Map.of("retention", "10years")
        )).whenException(e -> {});

        Promise<Void> audit = dataCloud.createSchema(new DataCloudKernelAdapter.SchemaCreateRequest(
                AUDIT_DATASET,
                Map.of("action", "string", "patientId", "string", "timestamp", "timestamp"),
                Map.of("retention", "10years")
        )).whenException(e -> {});

        return Promises.all(sessions, audit).map($ -> null);
    }

    private Promise<Void> audit(String action, String patientId, String details) {
        String auditId = generateId("aud");
        record AuditEntry(String id, Instant ts, String action, String patient, String details) {}
        return dataCloud.writeData(new DataWriteRequest(
                AUDIT_DATASET, auditId,
                TypedDataSerializer.toBytes(
                        new AuditEntry(auditId, Instant.now(), action, patientId, details),
                        "TelemedicineAuditEntry", 1),
                Map.of("timestamp", Instant.now().toString())
        )).whenException(e -> {});
    }

    private String generateId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
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
