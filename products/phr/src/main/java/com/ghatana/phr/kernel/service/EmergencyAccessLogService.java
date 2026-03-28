package com.ghatana.phr.kernel.service;

import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter;
import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter.DataQueryRequest;
import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter.DataReadRequest;
import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter.DataWriteRequest;
import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter.QueryResult;
import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.service.KernelLifecycleAware;
import com.ghatana.kernel.util.TypedDataSerializer;
import io.activej.promise.Promise;
import io.activej.promise.Promises;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Emergency Access Log Service for PHR — "Break-the-Glass" Audit Trail.
 *
 * <p>Records every instance where a provider bypasses normal consent controls
 * to access a patient's PHR in an emergency. All break-the-glass access is
 * persisted permanently and surfaced for mandatory post-hoc review. This
 * service is intentionally append-only: existing log entries cannot be deleted
 * or modified — only their review status may be updated.</p>
 *
 * <p>This is distinct from {@link ConsentManagementService} emergency grants,
 * which are pre-authorized emergency delegates. This service covers unplanned,
 * on-demand overrides triggered by clinical need.</p>
 *
 * @doc.type class
 * @doc.purpose PHR emergency access audit trail — break-the-glass logging and review workflow
 * @doc.layer product
 * @doc.pattern Service
 * @author Ghatana PHR Team
 * @since 1.0.0
 */
public class EmergencyAccessLogService implements KernelLifecycleAware {

    private static final String LOG_DATASET    = "phr.emergency.access.log";
    private static final String REVIEW_DATASET = "phr.emergency.access.review";

    private final DataCloudKernelAdapter dataCloud;
    private volatile boolean running = false;

    /**
     * Constructs an EmergencyAccessLogService.
     *
     * @param context kernel context providing DataCloudKernelAdapter
     */
    public EmergencyAccessLogService(KernelContext context) {
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
        return "emergency-access-log";
    }

    // ==================== Core Operations ====================

    /**
     * Logs an emergency access event.
     *
     * <p>Must be called immediately at the time of access override, before or
     * during the access itself. Caller is responsible for triggering an
     * out-of-band patient notification.</p>
     *
     * @param event the emergency access event
     * @return Promise containing the persisted event
     */
    public Promise<EmergencyAccessEvent> logAccess(EmergencyAccessEvent event) {
        if (!running) {
            return Promise.ofException(new IllegalStateException("Service not running"));
        }

        Objects.requireNonNull(event.patientId(), "patientId");
        Objects.requireNonNull(event.accessorId(), "accessorId");
        Objects.requireNonNull(event.justification(), "justification");

        String id = event.id() != null ? event.id() : generateId("emrg");
        EmergencyAccessEvent toStore = new EmergencyAccessEvent(
                id,
                event.patientId(),
                event.accessorId(),
                event.accessorRole(),
                event.justification(),
                event.resourcesAccessed() != null ? event.resourcesAccessed() : Set.of(),
                Instant.now(),
                ReviewStatus.PENDING_REVIEW,
                null,
                null
        );

        DataWriteRequest req = new DataWriteRequest(
                LOG_DATASET,
                id,
                TypedDataSerializer.toBytes(toStore, "EmergencyAccessEvent", 1),
                Map.of("patientId", toStore.patientId(),
                        "accessorId", toStore.accessorId(),
                        "reviewStatus", "PENDING_REVIEW",
                        "accessedAt", toStore.accessedAt().toString())
        );

        return dataCloud.writeData(req).map($ -> toStore);
    }

    /**
     * Updates the review status of an emergency access event.
     *
     * <p>May only move from {@code PENDING_REVIEW} to {@code REVIEWED} or {@code ESCALATED}.
     * Once escalated or reviewed, the event is immutable.</p>
     *
     * @param eventId       the event identifier
     * @param newStatus     the updated review status
     * @param reviewedBy    identity of the reviewer
     * @param reviewerNotes optional notes
     * @return Promise containing the updated event
     */
    public Promise<EmergencyAccessEvent> markReviewed(
            String eventId, String reviewedBy, ReviewStatus newStatus, String reviewerNotes) {
        if (!running) {
            return Promise.ofException(new IllegalStateException("Service not running"));
        }

        if (newStatus == ReviewStatus.PENDING_REVIEW) {
            return Promise.ofException(
                    new IllegalArgumentException("Cannot transition back to PENDING_REVIEW"));
        }

        return getEvent(eventId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.<EmergencyAccessEvent>ofException(
                                new IllegalStateException("Event not found: " + eventId));
                    }
                    EmergencyAccessEvent existing = opt.get();
                    if (existing.reviewStatus() != ReviewStatus.PENDING_REVIEW) {
                        return Promise.<EmergencyAccessEvent>ofException(
                                new IllegalStateException(
                                        "Event already reviewed with status: " + existing.reviewStatus()));
                    }
                    EmergencyAccessEvent updated = new EmergencyAccessEvent(
                            existing.id(), existing.patientId(), existing.accessorId(),
                            existing.accessorRole(), existing.justification(),
                            existing.resourcesAccessed(), existing.accessedAt(),
                            newStatus, reviewedBy, reviewerNotes
                    );
                    DataWriteRequest req = new DataWriteRequest(
                            LOG_DATASET, eventId,
                            TypedDataSerializer.toBytes(updated, "EmergencyAccessEvent", 1),
                            Map.of("reviewStatus", newStatus.name())
                    );
                    return dataCloud.writeData(req).map($ -> updated);
                });
    }

    /**
     * Retrieves a specific emergency access event.
     *
     * @param eventId the event identifier
     * @return Promise containing the event if found
     */
    public Promise<Optional<EmergencyAccessEvent>> getEvent(String eventId) {
        if (!running) {
            return Promise.of(Optional.empty());
        }

        return dataCloud.readData(new DataReadRequest(LOG_DATASET, eventId, Map.of()))
                .map(result -> {
                    if (result == null || result.getData() == null) return Optional.empty();
                    return Optional.ofNullable(
                            TypedDataSerializer.fromBytes(result.getData(), EmergencyAccessEvent.class));
                })
                ;
    }

    /**
     * Returns the complete emergency access log for a patient, sorted newest-first.
     *
     * @param patientId the patient identifier
     * @return Promise containing all access events for the patient
     */
    public Promise<List<EmergencyAccessEvent>> getPatientEmergencyLog(String patientId) {
        if (!running) {
            return Promise.of(List.of());
        }

        return dataCloud.queryData(new DataQueryRequest(
                LOG_DATASET,
                "patientId = :patientId",
                Map.of("patientId", patientId),
                1000, 0
        )).map(QueryResult::getResults)
                .map(results -> results.stream()
                        .map(r -> TypedDataSerializer.fromBytes(r.getData(), EmergencyAccessEvent.class))
                        .filter(Objects::nonNull)
                        .sorted((a, b) -> b.accessedAt().compareTo(a.accessedAt()))
                        .toList());
    }

    /**
     * Returns all events that are pending post-hoc review.
     *
     * @param limit maximum number of events to return
     * @return Promise containing pending review events, oldest-first (FIFO review queue)
     */
    public Promise<List<EmergencyAccessEvent>> getPendingReviews(int limit) {
        if (!running) {
            return Promise.of(List.of());
        }

        return dataCloud.queryData(new DataQueryRequest(
                LOG_DATASET,
                "reviewStatus = :status",
                Map.of("reviewStatus", "PENDING_REVIEW"),
                limit, 0
        )).map(QueryResult::getResults)
                .map(results -> results.stream()
                        .map(r -> TypedDataSerializer.fromBytes(r.getData(), EmergencyAccessEvent.class))
                        .filter(Objects::nonNull)
                        .filter(e -> e.reviewStatus() == ReviewStatus.PENDING_REVIEW)
                        .sorted((a, b) -> a.accessedAt().compareTo(b.accessedAt()))
                        .toList());
    }

    // ==================== Private Helpers ====================

    private Promise<Void> initializeDatasets() {
        Promise<Void> log = dataCloud.createSchema(new DataCloudKernelAdapter.SchemaCreateRequest(
                LOG_DATASET,
                Map.of("id", "string", "patientId", "string", "accessorId", "string",
                        "reviewStatus", "string", "accessedAt", "timestamp"),
                Map.of("retention", "permanent")
        ));

        Promise<Void> review = dataCloud.createSchema(new DataCloudKernelAdapter.SchemaCreateRequest(
                REVIEW_DATASET,
                Map.of("eventId", "string", "reviewedBy", "string", "timestamp", "timestamp"),
                Map.of("retention", "permanent")
        ));

        return Promises.all(log, review).map($ -> null);
    }

    private String generateId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    // ==================== Inner Types ====================

    /**
     * An emergency PHR access event.
     *
     * @param id               unique event identifier
     * @param patientId        the patient whose record was accessed
     * @param accessorId       the provider who performed the access override
     * @param accessorRole     clinical role of the accessor (e.g. "ER_PHYSICIAN")
     * @param justification    clinical justification provided at time of access
     * @param resourcesAccessed set of PHR resource types accessed (e.g. {"medications", "labs"})
     * @param accessedAt       timestamp of the access
     * @param reviewStatus     current post-hoc review status
     * @param reviewedBy       identity of the reviewer (null if not yet reviewed)
     * @param reviewerNotes    reviewer's notes or escalation reason
     */
    public record EmergencyAccessEvent(
            String id,
            String patientId,
            String accessorId,
            String accessorRole,
            String justification,
            Set<String> resourcesAccessed,
            Instant accessedAt,
            ReviewStatus reviewStatus,
            String reviewedBy,
            String reviewerNotes
    ) {}

    /** Post-hoc review status of an emergency access event. */
    public enum ReviewStatus {
        /** Access has been logged but not yet reviewed by a supervisor/compliance officer. */
        PENDING_REVIEW,
        /** Access was reviewed and found to be clinically justified. */
        REVIEWED,
        /** Access was flagged for further investigation or disciplinary action. */
        ESCALATED
    }
}
