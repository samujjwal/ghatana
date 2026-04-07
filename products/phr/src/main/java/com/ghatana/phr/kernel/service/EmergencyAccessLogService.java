package com.ghatana.phr.kernel.service;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.service.AbstractDataService;
import io.activej.promise.Promise;

import java.time.Duration;
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
    private static final Duration EMERGENCY_ACCESS_WINDOW = Duration.ofHours(4);
    private static final Duration MANDATORY_REVIEW_WINDOW = Duration.ofHours(24);

    private final EmergencyAccessReviewWorkflow reviewWorkflow;

    public EmergencyAccessLogService(KernelContext context) {
        this(context, EmergencyAccessReviewWorkflow.fromContext(context));
    }

    public EmergencyAccessLogService(KernelContext context, EmergencyAccessReviewWorkflow reviewWorkflow) {
        super(context);
        this.reviewWorkflow = Objects.requireNonNull(reviewWorkflow, "reviewWorkflow must not be null");
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
                "reviewStatus", "string", "accessedAt", "timestamp", "accessExpiresAt", "timestamp",
                "reviewDueAt", "timestamp", "reviewedAt", "timestamp", "reviewCaseId", "string"),
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

        String patientId = PhrInputSanitizationUtils.requireSafeIdentifier(event.patientId(), "patientId");
        String accessorId = PhrInputSanitizationUtils.requireSafeIdentifier(event.accessorId(), "accessorId");
        String accessorRole = PhrInputSanitizationUtils.requireSafeCode(event.accessorRole(), "accessorRole");
        String justification = PhrInputSanitizationUtils.sanitizeRequiredText(event.justification(), "justification", 2000);
        Set<String> resourcesAccessed = event.resourcesAccessed() == null ? Set.of() : event.resourcesAccessed().stream()
            .map(resource -> PhrInputSanitizationUtils.requireSafeCode(resource, "resourcesAccessed"))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

        String id = event.id() != null ? event.id() : generateId("emrg");
        Instant accessedAt = Instant.now();
        EmergencyAccessEvent toStore = new EmergencyAccessEvent(
            id,
            patientId,
            accessorId,
            accessorRole,
            justification,
            resourcesAccessed,
            accessedAt,
            accessedAt.plus(EMERGENCY_ACCESS_WINDOW),
            ReviewStatus.PENDING_REVIEW,
            accessedAt.plus(MANDATORY_REVIEW_WINDOW),
            null,
            null,
            null,
            EmergencyAccessReviewWorkflow.createCaseId()
        );

        return createRecord(
            LOG_DATASET,
            id,
            toStore,
            Map.of("patientId", toStore.patientId(),
                "accessorId", toStore.accessorId(),
                "reviewStatus", "PENDING_REVIEW",
                "accessedAt", toStore.accessedAt().toString(),
                "accessExpiresAt", toStore.accessExpiresAt().toString(),
                "reviewDueAt", toStore.reviewDueAt().toString(),
                "reviewCaseId", toStore.reviewCaseId()),
            "EmergencyAccessEvent",
            1
        ).then($ -> reviewWorkflow.initiate(toStore).map(__ -> toStore));
    }

    public Promise<EmergencyAccessEvent> markReviewed(
            String eventId, String reviewedBy, ReviewStatus newStatus, String reviewerNotes) {
        ensureRunning();

        String sanitizedEventId = PhrInputSanitizationUtils.requireSafeIdentifier(eventId, "eventId");
        String sanitizedReviewedBy = PhrInputSanitizationUtils.requireSafeIdentifier(reviewedBy, "reviewedBy");
        String sanitizedReviewerNotes = PhrInputSanitizationUtils.sanitizeOptionalText(
            reviewerNotes,
            "reviewerNotes",
            2000
        );

        if (newStatus == ReviewStatus.PENDING_REVIEW) {
            return Promise.ofException(
                new IllegalArgumentException("Cannot transition back to PENDING_REVIEW"));
        }
        if (newStatus == ReviewStatus.ESCALATED && sanitizedReviewerNotes == null) {
            return Promise.ofException(
                new IllegalArgumentException("Escalated emergency reviews require reviewer notes"));
        }

        return getEvent(sanitizedEventId)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.<EmergencyAccessEvent>ofException(
                        new IllegalStateException("Event not found: " + sanitizedEventId));
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
                    existing.resourcesAccessed(), existing.accessedAt(), existing.accessExpiresAt(),
                    newStatus, existing.reviewDueAt(), Instant.now(), sanitizedReviewedBy, sanitizedReviewerNotes,
                    existing.reviewCaseId()
                );
                return updateRecord(
                    LOG_DATASET,
                    sanitizedEventId,
                    updated,
                    Map.of(
                        "reviewStatus", newStatus.name(),
                        "reviewDueAt", updated.reviewDueAt().toString(),
                        "reviewedAt", updated.reviewedAt().toString(),
                        "reviewCaseId", updated.reviewCaseId()),
                    "EmergencyAccessEvent",
                    1
                ).then($ -> reviewWorkflow.complete(updated).map(__ -> updated));
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
            .sorted((a, b) -> {
                if (a.isReviewOverdue() && !b.isReviewOverdue()) {
                    return -1;
                }
                if (!a.isReviewOverdue() && b.isReviewOverdue()) {
                    return 1;
                }
                return a.reviewDueAt().compareTo(b.reviewDueAt());
            })
            .toList());
    }

    public Promise<List<EmergencyAccessEvent>> getOverdueReviews(int limit) {
        ensureRunning();

        return queryRecords(
            LOG_DATASET,
            "reviewStatus = :status",
            Map.of("reviewStatus", "PENDING_REVIEW"),
            Math.max(limit, 1000),
            0,
            EmergencyAccessEvent.class
        ).map(events -> events.stream()
            .filter(EmergencyAccessEvent::isReviewOverdue)
            .sorted((a, b) -> a.reviewDueAt().compareTo(b.reviewDueAt()))
            .limit(limit)
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
     * @param accessExpiresAt  timestamp when the emergency access window closes
     * @param reviewStatus     current post-hoc review status
     * @param reviewDueAt      deadline by which compliance review must complete
     * @param reviewedAt       timestamp when the event was reviewed or escalated
     * @param reviewedBy       identity of the reviewer (null if not yet reviewed)
     * @param reviewerNotes    reviewer's notes or escalation reason
     * @param reviewCaseId     compliance review case identifier
     */
    public record EmergencyAccessEvent(
            String id,
            String patientId,
            String accessorId,
            String accessorRole,
            String justification,
            Set<String> resourcesAccessed,
            Instant accessedAt,
            Instant accessExpiresAt,
            ReviewStatus reviewStatus,
            Instant reviewDueAt,
            Instant reviewedAt,
            String reviewedBy,
            String reviewerNotes,
            String reviewCaseId
    ) {
        public boolean isAccessExpired() {
            return accessExpiresAt != null && Instant.now().isAfter(accessExpiresAt);
        }

        public boolean isReviewOverdue() {
            return reviewStatus == ReviewStatus.PENDING_REVIEW
                && reviewDueAt != null
                && Instant.now().isAfter(reviewDueAt);
        }
    }

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
