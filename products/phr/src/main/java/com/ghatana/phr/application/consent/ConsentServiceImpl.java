package com.ghatana.phr.application.consent;

import com.ghatana.phr.application.patient.PatientOperationContext;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Implementation of ConsentService with in-memory storage.
 *
 * @doc.type class
 * @doc.purpose Provides consent management operations
 * @doc.layer product
 * @doc.pattern Service Implementation
 */
public class ConsentServiceImpl implements ConsentService {

    private final ConcurrentMap<String, Consent> consents = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, List<ConsentAuditEntry>> auditTrails = new ConcurrentHashMap<>();

    @Override
    public Promise<ConsentRequest> requestConsent(PatientOperationContext ctx, RequestConsentRequest request) {
        String requestId = "REQ-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        ConsentRequest consentRequest = new ConsentRequest(
            requestId,
            request.patientId(),
            request.consentType(),
            request.scope(),
            request.purpose(),
            "PENDING",
            Instant.now().toString()
        );
        return Promise.complete(consentRequest);
    }

    @Override
    public Promise<Optional<Consent>> getConsent(PatientOperationContext ctx, String consentId) {
        return Promise.complete(Optional.ofNullable(consents.get(consentId)));
    }

    @Override
    public Promise<Consent> updateConsent(PatientOperationContext ctx, String consentId, UpdateConsentRequest request) {
        Consent existing = consents.get(consentId);
        if (existing == null) {
            return Promise.ofException(new IllegalArgumentException("Consent not found: " + consentId));
        }
        Consent updated = new Consent(
            consentId,
            existing.patientId(),
            existing.consentType(),
            request.scope() != null ? request.scope() : existing.scope(),
            existing.purpose(),
            existing.status(),
            existing.grantedAt(),
            request.expiresAt() != null ? request.expiresAt() : existing.expiresAt(),
            existing.revokedAt()
        );
        consents.put(consentId, updated);
        return Promise.complete(updated);
    }

    @Override
    public Promise<Consent> revokeConsent(PatientOperationContext ctx, String consentId) {
        Consent existing = consents.get(consentId);
        if (existing == null) {
            return Promise.ofException(new IllegalArgumentException("Consent not found: " + consentId));
        }
        Consent revoked = new Consent(
            consentId,
            existing.patientId(),
            existing.consentType(),
            existing.scope(),
            existing.purpose(),
            Consent.ConsentStatus.REVOKED,
            existing.grantedAt(),
            existing.expiresAt(),
            Instant.now().toString()
        );
        consents.put(consentId, revoked);
        return Promise.complete(revoked);
    }

    @Override
    public Promise<List<Consent>> listConsents(PatientOperationContext ctx, String patientId) {
        return Promise.complete(consents.values().stream()
            .filter(c -> c.patientId().equals(patientId))
            .toList());
    }

    @Override
    public Promise<ConsentAuditTrail> getConsentAuditTrail(PatientOperationContext ctx, String patientId) {
        List<ConsentAuditEntry> entries = auditTrails.getOrDefault(patientId, List.of());
        ConsentAuditTrail trail = new ConsentAuditTrail(patientId, entries);
        return Promise.complete(trail);
    }
}
