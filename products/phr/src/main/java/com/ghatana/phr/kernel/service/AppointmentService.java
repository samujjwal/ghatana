package com.ghatana.phr.kernel.service;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ghatana.kernel.adapter.datacloud.DataQueryRequest;
import com.ghatana.kernel.adapter.datacloud.DataReadRequest;
import com.ghatana.kernel.adapter.datacloud.DataWriteRequest;
import com.ghatana.kernel.adapter.datacloud.QueryResult;
import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.service.AbstractDataService;
import com.ghatana.platform.security.ratelimit.RateLimiter;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Appointment Service with conflict resolution and timezone handling.
 *
 * <p>Manages patient appointments with:
 * <ul>
 *   <li>Optimistic locking for double-booking prevention</li>
 *   <li>Timezone handling for NRN (Non-Resident Nepalese)</li>
 *   <li>Slot availability management</li>
 *   <li>Reminder planning</li>
 * </ul></p>
 *
 * @doc.type class
 * @doc.purpose PHR appointment service with conflict resolution
 * @doc.layer product
 * @doc.pattern Service
 * @author Ghatana PHR Team
 * @since 1.0.0
 */
public class AppointmentService extends AbstractDataService {

    private static final String APPOINTMENT_DATASET = "phr.appointments";
    private static final String SLOT_DATASET = "phr.appointment.slots";
    private static final ZoneId NEPAL_ZONE = ZoneId.of("Asia/Kathmandu");
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofMinutes(1);
    private static final int CREATE_APPOINTMENT_MAX_PER_WINDOW = 20;
    private static final int QUERY_APPOINTMENT_MAX_PER_WINDOW = 120;
    private static final int CANCEL_APPOINTMENT_MAX_PER_WINDOW = 30;

    private final Map<String, SlotCacheEntry> slotCache = new ConcurrentHashMap<>();
    private final PhrNotificationSender notificationSender;
    private final RateLimiter createAppointmentLimiter;
    private final RateLimiter queryAppointmentLimiter;
    private final RateLimiter cancelAppointmentLimiter;

    public AppointmentService(KernelContext context) {
        this(context, PhrNotificationSenders.fromContext(context));
    }

    AppointmentService(KernelContext context, PhrNotificationSender notificationSender) {
        super(context);
        this.notificationSender = Objects.requireNonNull(notificationSender, "notificationSender must not be null");
        this.createAppointmentLimiter = PhrRateLimitUtils.createLimiter(
            CREATE_APPOINTMENT_MAX_PER_WINDOW,
            RATE_LIMIT_WINDOW
        );
        this.queryAppointmentLimiter = PhrRateLimitUtils.createLimiter(
            QUERY_APPOINTMENT_MAX_PER_WINDOW,
            RATE_LIMIT_WINDOW
        );
        this.cancelAppointmentLimiter = PhrRateLimitUtils.createLimiter(
            CANCEL_APPOINTMENT_MAX_PER_WINDOW,
            RATE_LIMIT_WINDOW
        );
    }

    @Override
    public String getName() {
        return "appointment";
    }

    @Override
    protected Promise<Void> initializeDatasets() {
        Promise<Void> appointmentSchema = createSchema(
            APPOINTMENT_DATASET,
            Map.of(
                "appointmentId", "string",
                "patientId", "string",
                "providerId", "string",
                "scheduledAt", "timestamp",
                "status", "string"
            ),
            Map.of("retention", "7years")
        );

        Promise<Void> slotSchema = createSchema(
            SLOT_DATASET,
            Map.of(
                "slotId", "string",
                "providerId", "string",
                "startTime", "timestamp",
                "available", "boolean"
            ),
            Map.of("retention", "1year")
        );

        return appointmentSchema.then($ -> slotSchema);
    }

    @Override
    protected Promise<Void> onStop() {
        slotCache.clear();
        return Promise.complete();
    }

    // ==================== Core Appointment Operations ====================

    /**
     * Creates a new appointment with conflict detection.
     *
     * @param request the appointment request
     * @return Promise containing the created appointment
     */
    public Promise<Appointment> createAppointment(AppointmentRequest request) {
        ensureRunning();

        String patientId = PhrInputSanitizationUtils.requireSafeIdentifier(request.getPatientId(), "patientId");
        String providerId = PhrInputSanitizationUtils.requireSafeIdentifier(request.getProviderId(), "providerId");
        String slotId = PhrInputSanitizationUtils.requireSafeIdentifier(request.getSlotId(), "slotId");
        PhrRateLimitUtils.requireAllowed(
            createAppointmentLimiter,
            patientId,
            "Appointment creation rate limit exceeded for patient: " + patientId
        );
        if (request.getScheduledTime() == null) {
            return Promise.ofException(new IllegalArgumentException("scheduledTime is required"));
        }
        if (request.getDurationMinutes() <= 0 || request.getDurationMinutes() > 480) {
            return Promise.ofException(new IllegalArgumentException("durationMinutes must be between 1 and 480"));
        }
        String reason = PhrInputSanitizationUtils.sanitizeRequiredText(request.getReason(), "reason", 500);
        String appointmentType = PhrInputSanitizationUtils.requireAllowedValue(
            request.getAppointmentType(),
            "appointmentType",
            java.util.Set.of("IN_PERSON", "TELEMEDICINE")
        );

        // Check slot availability with optimistic locking
        return checkSlotAvailability(slotId)
            .then(available -> {
                if (!available) {
                    return Promise.<Appointment>ofException(
                        new IllegalStateException("Slot no longer available"));
                }

                String appointmentId = generateId();
                Instant now = Instant.now();

                String correlationId = PhrTraceContext.newCorrelationId("phr_appointment_create");

                Appointment appointment = new Appointment(
                    appointmentId,
                    patientId,
                    providerId,
                    slotId,
                    request.getScheduledTime(),
                    request.getDurationMinutes(),
                    reason,
                    "SCHEDULED",
                    appointmentType,
                    now,
                    now,
                    1 // version for optimistic locking
                );

                // Write appointment
                DataWriteRequest writeRequest = new DataWriteRequest(
                    APPOINTMENT_DATASET,
                    appointmentId,
                    serialize(appointment, "Appointment", appointment.getVersion()),
                    PhrTraceContext.metadata(correlationId, "phr_appointment_create", Map.of(
                        "patientId", appointment.getPatientId(),
                        "providerId", appointment.getProviderId(),
                        "status", appointment.getStatus(),
                        "scheduledTime", appointment.getScheduledTime().toString()
                    ))
                );

                return dataCloud.writeData(writeRequest)
                    .then($ -> markSlotBooked(slotId))
                    .then($ -> createReminderPlan(appointment, correlationId))
                    .then($ -> audit("APPOINTMENT_CREATE", patientId,
                        "Appointment scheduled with " + providerId + " [" + correlationId + "]"))
                    .map($ -> appointment)
                    .whenException(e -> {
                        // Conflict detected - slot was booked by another request
                        if (e.getMessage().contains("conflict")) {
                            throw new IllegalStateException(
                                "This slot was just booked. Please select another.");
                        }
                    });
            });
    }

    /**
     * Gets appointments for a patient.
     *
     * @param patientId the patient identifier
     * @param status optional status filter
     * @return Promise containing list of appointments
     */
    public Promise<List<Appointment>> getPatientAppointments(String patientId, String status) {
        if (!running) {
            return Promise.of(List.of());
        }

        String sanitizedPatientId = PhrInputSanitizationUtils.requireSafeIdentifier(patientId, "patientId");
        PhrRateLimitUtils.requireAllowed(
            queryAppointmentLimiter,
            sanitizedPatientId,
            "Appointment query rate limit exceeded for patient: " + sanitizedPatientId
        );

        String query = "patientId = :patientId";
        Map<String, Object> params = new ConcurrentHashMap<>();
        params.put("patientId", sanitizedPatientId);

        if (status != null) {
            query += " AND status = :status";
            params.put("status", status);
        }

        DataQueryRequest request = new DataQueryRequest(
            APPOINTMENT_DATASET,
            query,
            params,
            100,
            0
        );

        return dataCloud.queryData(request)
            .map(QueryResult::getResults)
            .map(results -> results.stream()
                .map(r -> deserialize(r.getData(), Appointment.class))
                .filter(Objects::nonNull)
                .sorted((a, b) -> b.getScheduledTime().compareTo(a.getScheduledTime()))
                .toList());
    }

    /**
     * Cancels an appointment.
     *
     * @param appointmentId the appointment identifier
     * @param reason the cancellation reason
     * @return Promise completing when cancelled
     */
    public Promise<Void> cancelAppointment(String appointmentId, String reason) {
        if (!running) {
            return Promise.ofException(new IllegalStateException("Service not running"));
        }

        String sanitizedAppointmentId = PhrInputSanitizationUtils.requireSafeIdentifier(appointmentId, "appointmentId");
        String sanitizedReason = PhrInputSanitizationUtils.sanitizeRequiredText(reason, "reason", 500);
        PhrRateLimitUtils.requireAllowed(
            cancelAppointmentLimiter,
            sanitizedAppointmentId,
            "Appointment cancellation rate limit exceeded for appointment: " + sanitizedAppointmentId
        );

        return getAppointment(sanitizedAppointmentId)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.ofException(new IllegalStateException("Appointment not found"));
                }

                Appointment appointment = opt.get();
                if (!"SCHEDULED".equals(appointment.getStatus())) {
                    return Promise.ofException(
                        new IllegalStateException("Cannot cancel " + appointment.getStatus() + " appointment"));
                }

                Appointment cancelled = appointment.withStatus("CANCELLED").withUpdatedAt(Instant.now());

                return updateAppointment(cancelled)
                    .then($ -> freeSlot(appointment.getSlotId()))
                    .then($ -> cancelReminders(cancelled))
                    .then($ -> audit("APPOINTMENT_CANCEL", appointment.getPatientId(), sanitizedReason));
            });
    }

    /**
     * Gets available slots for a provider.
     *
     * @param providerId the provider identifier
     * @param date the date (in Nepal timezone)
     * @return Promise containing available slots
     */
    public Promise<List<TimeSlot>> getAvailableSlots(String providerId, String date) {
        if (!running) {
            return Promise.of(List.of());
        }

        DataQueryRequest request = new DataQueryRequest(
            SLOT_DATASET,
            "providerId = :providerId AND date = :date AND status = :status",
            Map.of(
                "providerId", providerId,
                "date", date,
                "status", "AVAILABLE"
            ),
            100,
            0
        );

        return dataCloud.queryData(request)
            .map(QueryResult::getResults)
            .map(results -> results.stream()
                .map(r -> deserializeSlot(r.getData()))
                .filter(Objects::nonNull)
                .sorted((a, b) -> a.getStartTime().compareTo(b.getStartTime()))
                .toList());
    }

    // ==================== Private Methods ====================

    private Promise<Boolean> checkSlotAvailability(String slotId) {
        // Check cache first
        SlotCacheEntry cached = slotCache.get(slotId);
        if (cached != null && cached.isAvailable()) {
            return Promise.of(true);
        }

        // Query from storage
        DataReadRequest request = new DataReadRequest(SLOT_DATASET, slotId, Map.of());

        return dataCloud.readData(request)
            .map(result -> deserializeSlot(result.getData()))
            .map(slot -> slot != null && "AVAILABLE".equals(slot.getStatus()))
            .whenException(e -> Promise.of(false));
    }

    private Promise<Void> markSlotBooked(String slotId) {
        DataReadRequest readRequest = new DataReadRequest(SLOT_DATASET, slotId, Map.of());

        return dataCloud.readData(readRequest)
            .then(result -> {
                TimeSlot slot = deserializeSlot(result.getData());
                if (slot == null) {
                    return Promise.ofException(new IllegalStateException("Slot not found"));
                }
                if (!"AVAILABLE".equals(slot.getStatus())) {
                    return Promise.ofException(new IllegalStateException("Slot conflict"));
                }

                TimeSlot booked = slot.withStatus("BOOKED");
                DataWriteRequest writeRequest = new DataWriteRequest(
                    SLOT_DATASET,
                    slotId,
                    serializeSlot(booked),
                    Map.of("status", "BOOKED")
                );

                slotCache.put(slotId, new SlotCacheEntry(false));
                return dataCloud.writeData(writeRequest);
            });
    }

    private Promise<Void> freeSlot(String slotId) {
        DataReadRequest readRequest = new DataReadRequest(SLOT_DATASET, slotId, Map.of());

        return dataCloud.readData(readRequest)
            .then(result -> {
                TimeSlot slot = deserializeSlot(result.getData());
                if (slot == null) {
                    return Promise.complete();
                }

                TimeSlot freed = slot.withStatus("AVAILABLE");
                DataWriteRequest writeRequest = new DataWriteRequest(
                    SLOT_DATASET,
                    slotId,
                    serializeSlot(freed),
                    Map.of("status", "AVAILABLE")
                );

                slotCache.put(slotId, new SlotCacheEntry(true));
                return dataCloud.writeData(writeRequest);
            });
    }

    private Promise<Optional<Appointment>> getAppointment(String appointmentId) {
        DataReadRequest request = new DataReadRequest(APPOINTMENT_DATASET, appointmentId, Map.of());

        return dataCloud.readData(request)
            .map(result -> Optional.ofNullable(deserialize(result.getData(), Appointment.class)));
    }

    private Promise<Void> updateAppointment(Appointment appointment) {
        DataWriteRequest request = new DataWriteRequest(
            APPOINTMENT_DATASET,
            appointment.getId(),
            serialize(appointment, "Appointment", appointment.getVersion()),
            Map.of("version", String.valueOf(appointment.getVersion() + 1))
        );

        return dataCloud.writeData(request);
    }

    private byte[] serializeSlot(TimeSlot slot) {
        return serialize(slot, "TimeSlot", 1);
    }

    private TimeSlot deserializeSlot(byte[] data) {
        return deserialize(data, TimeSlot.class);
    }

    private Promise<Void> createReminderPlan(Appointment appointment, String correlationId) {
        return notificationSender.scheduleAppointmentReminder(new PhrNotificationSender.AppointmentReminderNotification(
            appointment.getId(),
            appointment.getPatientId(),
            appointment.getProviderId(),
            appointment.getScheduledTime(),
            PhrNotificationSender.DEFAULT_CHANNELS,
            correlationId,
            "phr_appointment_reminder_schedule"
        ));
    }

    private Promise<Void> cancelReminders(Appointment appointment) {
        return notificationSender.cancelAppointmentReminder(new PhrNotificationSender.AppointmentReminderNotification(
            appointment.getId(),
            appointment.getPatientId(),
            appointment.getProviderId(),
            appointment.getScheduledTime(),
            PhrNotificationSender.DEFAULT_CHANNELS,
            PhrTraceContext.newCorrelationId("phr_appointment_reminder_cancel"),
            "phr_appointment_reminder_cancel"
        ));
    }

    @Override
    protected Promise<Void> audit(String action, String entityId, String details) {
        return super.audit(action, entityId, details);
    }

    // ==================== Inner Types ====================

    public static class Appointment {
        private final String id;
        private final String patientId;
        private final String providerId;
        private final String slotId;
        private final Instant scheduledTime;
        private final int durationMinutes;
        private final String reason;
        private final String status; // SCHEDULED, COMPLETED, CANCELLED, NO_SHOW
        private final String appointmentType; // IN_PERSON, TELEMEDICINE
        private final Instant createdAt;
        private final Instant updatedAt;
        private final int version;

        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        public Appointment(
            @JsonProperty("id") String id,
            @JsonProperty("patientId") String patientId,
            @JsonProperty("providerId") String providerId,
            @JsonProperty("slotId") String slotId,
            @JsonProperty("scheduledTime") Instant scheduledTime,
            @JsonProperty("durationMinutes") int durationMinutes,
            @JsonProperty("reason") String reason,
            @JsonProperty("status") String status,
            @JsonProperty("appointmentType") String appointmentType,
            @JsonProperty("createdAt") Instant createdAt,
            @JsonProperty("updatedAt") Instant updatedAt,
            @JsonProperty("version") int version) {
            this.id = id;
            this.patientId = patientId;
            this.providerId = providerId;
            this.slotId = slotId;
            this.scheduledTime = scheduledTime;
            this.durationMinutes = durationMinutes;
            this.reason = reason;
            this.status = status;
            this.appointmentType = appointmentType;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
            this.version = version;
        }

        public String getId() { return id; }
        public String getPatientId() { return patientId; }
        public String getProviderId() { return providerId; }
        public String getSlotId() { return slotId; }
        public Instant getScheduledTime() { return scheduledTime; }
        public int getDurationMinutes() { return durationMinutes; }
        public String getReason() { return reason; }
        public String getStatus() { return status; }
        public String getAppointmentType() { return appointmentType; }
        public Instant getCreatedAt() { return createdAt; }
        public Instant getUpdatedAt() { return updatedAt; }
        public int getVersion() { return version; }

        public Appointment withStatus(String newStatus) {
            return new Appointment(id, patientId, providerId, slotId, scheduledTime,
                durationMinutes, reason, newStatus, appointmentType, createdAt, updatedAt, version);
        }

        public Appointment withUpdatedAt(Instant newUpdatedAt) {
            return new Appointment(id, patientId, providerId, slotId, scheduledTime,
                durationMinutes, reason, status, appointmentType, createdAt, newUpdatedAt, version);
        }
    }

    public static class AppointmentRequest {
        private final String patientId;
        private final String providerId;
        private final String slotId;
        private final Instant scheduledTime;
        private final int durationMinutes;
        private final String reason;
        private final String appointmentType;

        public AppointmentRequest(String patientId, String providerId, String slotId,
                                   Instant scheduledTime, int durationMinutes,
                                   String reason, String appointmentType) {
            this.patientId = patientId;
            this.providerId = providerId;
            this.slotId = slotId;
            this.scheduledTime = scheduledTime;
            this.durationMinutes = durationMinutes;
            this.reason = reason;
            this.appointmentType = appointmentType;
        }

        public String getPatientId() { return patientId; }
        public String getProviderId() { return providerId; }
        public String getSlotId() { return slotId; }
        public Instant getScheduledTime() { return scheduledTime; }
        public int getDurationMinutes() { return durationMinutes; }
        public String getReason() { return reason; }
        public String getAppointmentType() { return appointmentType; }
    }

    public static class TimeSlot {
        private final String id;
        private final String providerId;
        private final String date; // YYYY-MM-DD in Nepal timezone
        private final Instant startTime;
        private final Instant endTime;
        private final String status; // AVAILABLE, BOOKED, BLOCKED

        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        public TimeSlot(
            @JsonProperty("id") String id,
            @JsonProperty("providerId") String providerId,
            @JsonProperty("date") String date,
            @JsonProperty("startTime") Instant startTime,
            @JsonProperty("endTime") Instant endTime,
            @JsonProperty("status") String status) {
            this.id = id;
            this.providerId = providerId;
            this.date = date;
            this.startTime = startTime;
            this.endTime = endTime;
            this.status = status;
        }

        public String getId() { return id; }
        public String getProviderId() { return providerId; }
        public String getDate() { return date; }
        public Instant getStartTime() { return startTime; }
        public Instant getEndTime() { return endTime; }
        public String getStatus() { return status; }

        public TimeSlot withStatus(String newStatus) {
            return new TimeSlot(id, providerId, date, startTime, endTime, newStatus);
        }
    }

    private static class SlotCacheEntry {
        private final boolean available;
        private final Instant cachedAt;

        SlotCacheEntry(boolean available) {
            this.available = available;
            this.cachedAt = Instant.now();
        }

        boolean isAvailable() { return available; }
        boolean isStale() { return cachedAt.plusSeconds(30).isBefore(Instant.now()); }
    }
}
