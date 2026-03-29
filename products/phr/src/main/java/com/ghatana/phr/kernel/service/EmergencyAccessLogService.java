package com.ghatana.phr.kernel.service;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.service.AbstractDataService;
import io.activej.promise.Promise;
import io.activej.promise.Promises;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

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
public class EmergencyAccessLogService extends AbstractDataService {

    private static final String LOG_DATASET = "phr.emergency.access.log";

    public EmergencyAccessLogService(KernelContext context) {
        super(context);
    }

    @Override
    public String getName() {
        return "emergency-access-log";
    }

    @Override
    protected Promise<Void> initializeDatasets() {
        return createSchema(
            LOG_DATASET,
            Map.of("id", "string", "patientId", "string", "accessorId", "string",
                "reviewStatus", "string", "accessedAt", "timestamp"),
            Map.of("retention", "permanent")
        );
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
        ensureRunning();

        validateRequired(event.patientId(), "patientId");
        validateRequired(event.accessorId(), "accessorId");
        validateRequired(event.justification(), "justification");

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

        return createRecord(
            LOG_DATASET,
            id,
            toStore,
            Map.of("patientId", toStore.patientId(),
                "accessorId", toStore.accessorId(),
                "reviewStatus", "PENDING_REVIEW",
                "accessedAt", toStore.accessedAt().toString()),
            "EmergencyAccessEvent",
            1
        ).map($ -> toStore);
    }

    public Promise<EmergencyAccessEvent> markReviewed(
            String eventId, String reviewedBy, ReviewStatus newStatus, String reviewerNotes) {
        ensureRunning();

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
                return updateRecord(
                    LOG_DATASET,
                    eventId,
                    updated,
                    Map.of("reviewStatus", newStatus.name()),
                    "EmergencyAccessEvent",
                    1
                ).map($ -> updated);
            });
    }

    public Promise<Optional<EmergencyAccessEvent>> getEvent(String eventId) {
        ensureRunning();
        return readRecord(LOG_DATASET, eventId, EmergencyAccessEvent.class);
    }

    public Promise<List<EmergencyAccessEvent>> getPatientEmergencyLog(String patientId) {
        ensureRunning();

        return queryRecords(
            LOG_DATASET,
            "patientId = :patientId",
            Map.of("patientId", patientId),
            1000,
            0,
            EmergencyAccessEvent.class
        ).map(events -> events.stream()
            .sorted((a, b) -> b.accessedAt().compareTo(a.accessedAt()))
            .toList());
    }

    public Promise<List<EmergencyAccessEvent>> getPendingReviews(int limit) {
        ensureRunning();

        return queryRecords(
            LOG_DATASET,
            "reviewStatus = :status",
            Map.of("reviewStatus", "PENDING_REVIEW"),
            limit,
            0,
            EmergencyAccessEvent.class
        ).map(events -> events.stream()
            .filter(e -> e.reviewStatus() == ReviewStatus.PENDING_REVIEW)
            .sorted((a, b) -> a.accessedAt().compareTo(b.accessedAt()))
            .toList());
    }

    // ==================== Private Helpers ====================

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
