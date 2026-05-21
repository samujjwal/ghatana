package com.ghatana.phr.healthcare.service;

import com.ghatana.phr.healthcare.domain.ConsentAction;
import com.ghatana.phr.healthcare.domain.ConsentRecord;
import com.ghatana.phr.healthcare.domain.DataClassification;
import com.ghatana.phr.healthcare.port.ConsentStore;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Consent grant and revocation service.
 *
 * <p>Provides a clear, enforced workflow for consent grants and revocations.
 * All consent changes are append-only and immutable.</p>
 *
 * @doc.type class
 * @doc.purpose Consent grants/revocations workflow service
 * @doc.layer domain-pack
 * @doc.pattern Service
 */
public class ConsentGrantService {

    private final ConsentStore consentStore;
    private final Executor executor;
    private final Counter grantedCounter;
    private final Counter revokedCounter;

    public record GrantConsentRequest(
        String tenantId,
        UUID patientId,
        String grantorId,
        String grantorType,
        String granteeId,
        String granteeType,
        List<ConsentAction> actions,
        DataClassification classification,
        String purposeOfUse,
        Instant expiresAt,
        String createdBy
    ) {
        public GrantConsentRequest {
            Objects.requireNonNull(tenantId, "tenantId must not be null");
            Objects.requireNonNull(patientId, "patientId must not be null");
            Objects.requireNonNull(grantorId, "grantorId must not be null");
            Objects.requireNonNull(grantorType, "grantorType must not be null");
            Objects.requireNonNull(granteeId, "granteeId must not be null");
            Objects.requireNonNull(granteeType, "granteeType must not be null");
            Objects.requireNonNull(actions, "actions must not be null");
            if (actions.isEmpty()) {
                throw new IllegalArgumentException("actions must not be empty");
            }
            Objects.requireNonNull(classification, "classification must not be null");
            Objects.requireNonNull(purposeOfUse, "purposeOfUse must not be null");
            Objects.requireNonNull(createdBy, "createdBy must not be null");
        }
    }

    public record RevokeConsentRequest(
        String tenantId,
        UUID patientId,
        UUID consentId,
        String revocationReason,
        String revokedBy
    ) {
        public RevokeConsentRequest {
            Objects.requireNonNull(tenantId, "tenantId must not be null");
            Objects.requireNonNull(patientId, "patientId must not be null");
            Objects.requireNonNull(consentId, "consentId must not be null");
            Objects.requireNonNull(revocationReason, "revocationReason must not be null");
            if (revocationReason.isBlank()) {
                throw new IllegalArgumentException("revocationReason must not be blank");
            }
            Objects.requireNonNull(revokedBy, "revokedBy must not be null");
        }
    }

    public ConsentGrantService(ConsentStore consentStore, Executor executor, MeterRegistry registry) {
        this.consentStore = Objects.requireNonNull(consentStore, "consentStore must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
        this.grantedCounter = Counter.builder("healthcare.consent.grants_total")
            .description("Total number of consent grants")
            .register(registry);
        this.revokedCounter = Counter.builder("healthcare.consent.revocations_total")
            .description("Total number of consent revocations")
            .register(registry);
    }

    /**
     * Grants a new consent for the specified actions.
     *
     * @param request the grant consent request
     * @return the newly created consent record
     */
    public Promise<ConsentRecord> grantConsent(GrantConsentRequest request) {
        return Promise.ofBlocking(executor, () -> {
            ConsentRecord consent = ConsentRecord.newGrant(
                request.tenantId(),
                request.patientId(),
                request.grantorId(),
                request.grantorType(),
                request.granteeId(),
                request.granteeType(),
                request.actions(),
                request.classification(),
                request.purposeOfUse(),
                request.expiresAt(),
                request.createdBy()
            );
            consentStore.append(consent);
            grantedCounter.increment();
            return consent;
        });
    }

    /**
     * Revokes an existing consent.
     *
     * @param request the revoke consent request
     * @return the revoked consent record
     */
    public Promise<ConsentRecord> revokeConsent(RevokeConsentRequest request) {
        return Promise.ofBlocking(executor, () -> {
            // Find the consent to revoke
            List<ConsentRecord> allForPatient = consentStore.findAllForPatient(
                request.tenantId(), request.patientId()
            );
            
            ConsentRecord toRevoke = allForPatient.stream()
                .filter(c -> c.consentId().equals(request.consentId()))
                .filter(c -> c.status() == com.ghatana.phr.healthcare.domain.ConsentStatus.ACTIVE)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                    "Active consent not found: " + request.consentId()
                ));
            
            // Create revoked copy
            ConsentRecord revoked = toRevoke.revoked(
                request.revocationReason(), Instant.now()
            );
            consentStore.append(revoked);
            revokedCounter.increment();
            return revoked;
        });
    }

    /**
     * Lists all consents for a patient.
     *
     * @param tenantId the tenant scope
     * @param patientId the patient's UUID
     * @return all consent records for the patient
     */
    public Promise<List<ConsentRecord>> listConsentsForPatient(String tenantId, UUID patientId) {
        return Promise.ofBlocking(executor, () -> 
            consentStore.findAllForPatient(tenantId, patientId)
        );
    }

    /**
     * Lists all consents for a grantee.
     *
     * @param tenantId the tenant scope
     * @param granteeId the grantee's id
     * @return all consent records for the grantee
     */
    public Promise<List<ConsentRecord>> listConsentsForGrantee(String tenantId, String granteeId) {
        return Promise.ofBlocking(executor, () -> 
            consentStore.findAllForGrantee(tenantId, granteeId)
        );
    }
}
