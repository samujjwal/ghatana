package com.ghatana.phr.application.emergency;

import com.ghatana.phr.application.patient.PatientOperationContext;
import io.activej.promise.Promise;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Implementation of EmergencyAccessService with in-memory storage.
 *
 * @doc.type class
 * @doc.purpose Provides emergency access operations with mandatory review
 * @doc.layer product
 * @doc.pattern Service Implementation
 */
public class EmergencyAccessServiceImpl implements EmergencyAccessService {

    private static final Duration DEFAULT_ACCESS_DURATION = Duration.ofHours(1);
    private static final Duration REVIEW_DEADLINE = Duration.ofHours(1);
    
    private final ConcurrentMap<String, EmergencyAccess> emergencyAccesses = new ConcurrentHashMap<>();

    @Override
    public Promise<EmergencyAccess> requestEmergencyAccess(PatientOperationContext ctx, EmergencyAccessRequest request) {
        String emergencyAccessId = "EMG-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Instant accessedAt = Instant.now();
        Instant accessExpiresAt = accessedAt.plus(DEFAULT_ACCESS_DURATION);
        Instant reviewDueAt = accessedAt.plus(REVIEW_DEADLINE);
        
        EmergencyAccess emergencyAccess = new EmergencyAccess(
            emergencyAccessId,
            request.patientId(),
            request.accessorId(),
            request.justification(),
            request.reason(),
            accessedAt,
            accessExpiresAt,
            reviewDueAt,
            EmergencyAccessStatus.ACTIVE,
            null
        );

        emergencyAccesses.put(emergencyAccessId, emergencyAccess);
        return Promise.of(emergencyAccess);
    }

    @Override
    public Promise<Optional<EmergencyAccess>> getEmergencyAccess(PatientOperationContext ctx, String emergencyAccessId) {
        return Promise.of(Optional.ofNullable(emergencyAccesses.get(emergencyAccessId)));
    }

    @Override
    public Promise<EmergencyAccess> extendEmergencyAccess(PatientOperationContext ctx, String emergencyAccessId, int extensionMinutes) {
        EmergencyAccess existing = emergencyAccesses.get(emergencyAccessId);
        if (existing == null) {
            return Promise.ofException(new IllegalArgumentException("Emergency access not found: " + emergencyAccessId));
        }

        Instant newExpiresAt = existing.accessExpiresAt().plus(Duration.ofMinutes(extensionMinutes));
        
        EmergencyAccess extended = new EmergencyAccess(
            emergencyAccessId,
            existing.patientId(),
            existing.accessorId(),
            existing.justification(),
            existing.reason(),
            existing.accessedAt(),
            newExpiresAt,
            existing.reviewDueAt(),
            existing.status(),
            existing.reviewCaseId()
        );

        emergencyAccesses.put(emergencyAccessId, extended);
        return Promise.of(extended);
    }

    @Override
    public Promise<List<EmergencyAccess>> listEmergencyAccess(PatientOperationContext ctx, String patientId) {
        return Promise.of(emergencyAccesses.values().stream()
            .filter(e -> e.patientId().equals(patientId))
            .toList());
    }

    @Override
    public Promise<EmergencyAccess> completeReview(PatientOperationContext ctx, String emergencyAccessId, ReviewResult reviewResult) {
        EmergencyAccess existing = emergencyAccesses.get(emergencyAccessId);
        if (existing == null) {
            return Promise.ofException(new IllegalArgumentException("Emergency access not found: " + emergencyAccessId));
        }

        EmergencyAccessStatus newStatus = "ESCALATED".equals(reviewResult.reviewStatus()) 
            ? EmergencyAccessStatus.ESCALATED 
            : EmergencyAccessStatus.REVIEWED;

        EmergencyAccess reviewed = new EmergencyAccess(
            emergencyAccessId,
            existing.patientId(),
            existing.accessorId(),
            existing.justification(),
            existing.reason(),
            existing.accessedAt(),
            existing.accessExpiresAt(),
            existing.reviewDueAt(),
            newStatus,
            existing.reviewCaseId()
        );

        emergencyAccesses.put(emergencyAccessId, reviewed);
        return Promise.of(reviewed);
    }
}
