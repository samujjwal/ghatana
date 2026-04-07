package com.ghatana.phr.kernel.service;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.service.AbstractDataService;
import com.ghatana.platform.security.ratelimit.RateLimiter;
import io.activej.promise.Promise;
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
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofMinutes(1);
    private static final int SCHEDULE_SESSION_MAX_PER_WINDOW = 20;
    private static final int QUERY_SESSION_MAX_PER_WINDOW = 120;
    private static final int LIFECYCLE_SESSION_MAX_PER_WINDOW = 40;
    private final PhrNotificationSender notificationSender;
    private final RateLimiter scheduleSessionLimiter;
    private final RateLimiter querySessionLimiter;
    private final RateLimiter lifecycleSessionLimiter;

    public TelemedicineService(KernelContext context) {
        this(context, PhrNotificationSenders.fromContext(context));
    }

    TelemedicineService(KernelContext context, PhrNotificationSender notificationSender) {
        super(context);
        this.notificationSender = Objects.requireNonNull(notificationSender, "notificationSender must not be null");
        this.scheduleSessionLimiter = PhrRateLimitUtils.createLimiter(SCHEDULE_SESSION_MAX_PER_WINDOW, RATE_LIMIT_WINDOW);
        this.querySessionLimiter = PhrRateLimitUtils.createLimiter(QUERY_SESSION_MAX_PER_WINDOW, RATE_LIMIT_WINDOW);
        this.lifecycleSessionLimiter = PhrRateLimitUtils.createLimiter(LIFECYCLE_SESSION_MAX_PER_WINDOW, RATE_LIMIT_WINDOW);
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

        String patientId = PhrInputSanitizationUtils.requireSafeIdentifier(session.patientId(), "patientId");
        String providerId = PhrInputSanitizationUtils.requireSafeIdentifier(session.providerId(), "providerId");
        PhrRateLimitUtils.requireAllowed(
            scheduleSessionLimiter,
            patientId,
            "Telemedicine scheduling rate limit exceeded for patient: " + patientId
        );
        if (session.scheduledAt() == null) {
            return Promise.ofException(new IllegalArgumentException("scheduledAt is required"));
        }
        if (session.durationMinutes() <= 0 || session.durationMinutes() > 480) {
            return Promise.ofException(new IllegalArgumentException("durationMinutes must be between 1 and 480"));
        }
        String platform = PhrInputSanitizationUtils.requireSafeCode(session.platform(), "platform");
        String joinUrl = PhrInputSanitizationUtils.requireHttpsUrl(session.joinUrl(), "joinUrl");

        String correlationId = PhrTraceContext.newCorrelationId("phr_telemedicine_schedule");
        String id = session.id() != null ? session.id() : generateId("tele");
        TeleSession toStore = new TeleSession(
            id,
            patientId,
            providerId,
            session.scheduledAt(),
            session.durationMinutes(),
            platform,
            joinUrl,
            SessionStatus.SCHEDULED,
            null,
            null,
            null
        );

        return writeSession(toStore, "SCHEDULE_SESSION", "Scheduled telemedicine session", correlationId, "phr_telemedicine_schedule")
            .then(stored -> notificationSender.notifyTelemedicineSession(new PhrNotificationSender.TelemedicineSessionNotification(
                stored.id(),
                stored.patientId(),
                stored.providerId(),
                stored.scheduledAt(),
                PhrNotificationSender.TelemedicineNotificationType.SESSION_SCHEDULED,
                PhrNotificationSender.DEFAULT_CHANNELS,
                correlationId,
                "phr_telemedicine_schedule"
            )).map($ -> stored));
    }

    public Promise<TeleSession> startSession(String sessionId) {
        ensureRunning();
        String sanitizedSessionId = PhrInputSanitizationUtils.requireSafeIdentifier(sessionId, "sessionId");
        PhrRateLimitUtils.requireAllowed(
            lifecycleSessionLimiter,
            sanitizedSessionId,
            "Telemedicine lifecycle rate limit exceeded for session: " + sanitizedSessionId
        );

        return getSession(sanitizedSessionId)
            .then(opt -> requireFound(opt, sanitizedSessionId))
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
                return writeSession(
                    updated,
                    "START_SESSION",
                    "Session started",
                    PhrTraceContext.newCorrelationId("phr_telemedicine_start"),
                    "phr_telemedicine_start"
                );
            });
    }

    public Promise<TeleSession> completeSession(String sessionId, String notes) {
        ensureRunning();

        String sanitizedSessionId = PhrInputSanitizationUtils.requireSafeIdentifier(sessionId, "sessionId");
        String sanitizedNotes = PhrInputSanitizationUtils.sanitizeOptionalText(notes, "notes", 2000);
        PhrRateLimitUtils.requireAllowed(
            lifecycleSessionLimiter,
            sanitizedSessionId,
            "Telemedicine lifecycle rate limit exceeded for session: " + sanitizedSessionId
        );

        return getSession(sanitizedSessionId)
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
                    SessionStatus.COMPLETED, existing.startedAt(), Instant.now(), sanitizedNotes
                );
                return writeSession(
                    updated,
                    "COMPLETE_SESSION",
                    "Session completed" + (actual != null ? " after " + actual.toMinutes() + "m" : ""),
                    PhrTraceContext.newCorrelationId("phr_telemedicine_complete"),
                    "phr_telemedicine_complete"
                );
            });
    }

    public Promise<TeleSession> cancelSession(String sessionId, String reason) {
        ensureRunning();

        String sanitizedSessionId = PhrInputSanitizationUtils.requireSafeIdentifier(sessionId, "sessionId");
        String sanitizedReason = PhrInputSanitizationUtils.sanitizeRequiredText(reason, "reason", 500);
        PhrRateLimitUtils.requireAllowed(
            lifecycleSessionLimiter,
            sanitizedSessionId,
            "Telemedicine lifecycle rate limit exceeded for session: " + sanitizedSessionId
        );

        return getSession(sanitizedSessionId)
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
                    SessionStatus.CANCELLED, existing.startedAt(), null, sanitizedReason
                );
                String correlationId = PhrTraceContext.newCorrelationId("phr_telemedicine_cancel");
                return writeSession(updated, "CANCEL_SESSION", "Session cancelled: " + sanitizedReason, correlationId, "phr_telemedicine_cancel")
                    .then(stored -> notificationSender.notifyTelemedicineSession(new PhrNotificationSender.TelemedicineSessionNotification(
                        stored.id(),
                        stored.patientId(),
                        stored.providerId(),
                        stored.scheduledAt(),
                        PhrNotificationSender.TelemedicineNotificationType.SESSION_CANCELLED,
                        PhrNotificationSender.DEFAULT_CHANNELS,
                        correlationId,
                        "phr_telemedicine_cancel"
                    )).map($ -> stored));
            });
    }

    public Promise<TeleSession> rescheduleSession(String sessionId, Instant scheduledAt, int durationMinutes, String joinUrl) {
        ensureRunning();

        String sanitizedSessionId = PhrInputSanitizationUtils.requireSafeIdentifier(sessionId, "sessionId");
        String sanitizedJoinUrl = PhrInputSanitizationUtils.requireHttpsUrl(joinUrl, "joinUrl");
        if (scheduledAt == null) {
            return Promise.ofException(new IllegalArgumentException("scheduledAt is required"));
        }
        if (durationMinutes <= 0 || durationMinutes > 480) {
            return Promise.ofException(new IllegalArgumentException("durationMinutes must be between 1 and 480"));
        }
        PhrRateLimitUtils.requireAllowed(
            lifecycleSessionLimiter,
            sanitizedSessionId,
            "Telemedicine lifecycle rate limit exceeded for session: " + sanitizedSessionId
        );

        return getSession(sanitizedSessionId)
            .then(opt -> requireFound(opt, sanitizedSessionId))
            .then(existing -> {
                if (existing.status() == SessionStatus.COMPLETED || existing.status() == SessionStatus.NO_SHOW) {
                    return Promise.<TeleSession>ofException(new IllegalStateException(
                        "Cannot reschedule session in status: " + existing.status()));
                }
                TeleSession updated = new TeleSession(
                    existing.id(),
                    existing.patientId(),
                    existing.providerId(),
                    scheduledAt,
                    durationMinutes,
                    existing.platform(),
                    sanitizedJoinUrl,
                    SessionStatus.SCHEDULED,
                    null,
                    null,
                    null
                );
                String correlationId = PhrTraceContext.newCorrelationId("phr_telemedicine_reschedule");
                return writeSession(updated, "RESCHEDULE_SESSION", "Session rescheduled", correlationId, "phr_telemedicine_reschedule")
                    .then(stored -> notificationSender.notifyTelemedicineSession(new PhrNotificationSender.TelemedicineSessionNotification(
                        stored.id(),
                        stored.patientId(),
                        stored.providerId(),
                        stored.scheduledAt(),
                        PhrNotificationSender.TelemedicineNotificationType.SESSION_RESCHEDULED,
                        PhrNotificationSender.DEFAULT_CHANNELS,
                        correlationId,
                        "phr_telemedicine_reschedule"
                    )).map($ -> stored));
            });
    }

    public Promise<TeleSession> markNoShow(String sessionId, String reason) {
        ensureRunning();

        String sanitizedSessionId = PhrInputSanitizationUtils.requireSafeIdentifier(sessionId, "sessionId");
        String sanitizedReason = PhrInputSanitizationUtils.sanitizeRequiredText(reason, "reason", 500);
        PhrRateLimitUtils.requireAllowed(
            lifecycleSessionLimiter,
            sanitizedSessionId,
            "Telemedicine lifecycle rate limit exceeded for session: " + sanitizedSessionId
        );

        return getSession(sanitizedSessionId)
            .then(opt -> requireFound(opt, sanitizedSessionId))
            .then(existing -> {
                if (existing.status() == SessionStatus.COMPLETED || existing.status() == SessionStatus.CANCELLED) {
                    return Promise.<TeleSession>ofException(new IllegalStateException(
                        "Cannot mark no-show in status: " + existing.status()));
                }
                TeleSession updated = new TeleSession(
                    existing.id(),
                    existing.patientId(),
                    existing.providerId(),
                    existing.scheduledAt(),
                    existing.durationMinutes(),
                    existing.platform(),
                    existing.joinUrl(),
                    SessionStatus.NO_SHOW,
                    existing.startedAt(),
                    Instant.now(),
                    sanitizedReason
                );
                String correlationId = PhrTraceContext.newCorrelationId("phr_telemedicine_no_show");
                return writeSession(updated, "NO_SHOW_SESSION", "Session marked no-show", correlationId, "phr_telemedicine_no_show")
                    .then(stored -> notificationSender.notifyTelemedicineSession(new PhrNotificationSender.TelemedicineSessionNotification(
                        stored.id(),
                        stored.patientId(),
                        stored.providerId(),
                        stored.scheduledAt(),
                        PhrNotificationSender.TelemedicineNotificationType.SESSION_NO_SHOW,
                        PhrNotificationSender.DEFAULT_CHANNELS,
                        correlationId,
                        "phr_telemedicine_no_show"
                    )).map($ -> stored));
            });
    }

    public Promise<Optional<TeleSession>> getSession(String sessionId) {
        ensureRunning();
        return readRecord(SESSION_DATASET, sessionId, TeleSession.class);
    }

    public Promise<List<TeleSession>> getPatientSessions(String patientId) {
        ensureRunning();
        String sanitizedPatientId = PhrInputSanitizationUtils.requireSafeIdentifier(patientId, "patientId");
        PhrRateLimitUtils.requireAllowed(
            querySessionLimiter,
            sanitizedPatientId,
            "Telemedicine query rate limit exceeded for patient: " + sanitizedPatientId
        );

        return queryRecords(
            SESSION_DATASET,
            "patientId = :patientId",
            Map.of("patientId", sanitizedPatientId),
            500,
            0,
            TeleSession.class
        ).map(sessions -> sessions.stream()
            .sorted((a, b) -> b.scheduledAt().compareTo(a.scheduledAt()))
            .toList());
    }

    // ==================== Private Helpers ====================

    private Promise<TeleSession> writeSession(
            TeleSession session,
            String action,
            String detail,
            String correlationId,
            String traceOperation) {
        return createRecord(
            SESSION_DATASET,
            session.id(),
            session,
            PhrTraceContext.metadata(correlationId, traceOperation, Map.of(
                "patientId", session.patientId(),
                "status", session.status().name()
            )),
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
