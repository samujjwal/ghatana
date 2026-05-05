package com.ghatana.phr.kernel.service;

import com.ghatana.kernel.context.KernelContext;

import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Referral Management Service for PHR.
 *
 * <p>Manages provider-to-provider patient referrals including specialist referrals,
 * hospital-to-hospital transfers, and referral acceptance workflows. Tracks referral
 * lifecycle from creation through acceptance, consultation, and closure. Complies with
 * Nepal's Health Referral System guidelines.</p>
 *
 * @doc.type class
 * @doc.purpose PHR referral management — specialist referrals with lifecycle tracking
 * @doc.layer product
 * @doc.pattern Service
 * @author Ghatana PHR Team
 * @since 1.0.0
 */
public class ReferralService extends PhrServiceBase {

    private static final String REFERRAL_DATASET = "phr.referrals";

    public ReferralService(KernelContext context) {
        super(context);
    }

    @Override
    public String getName() {
        return "referral";
    }

    @Override
    protected Promise<Void> initializeDatasets() {
        return createSchema(
            REFERRAL_DATASET,
            Map.of("id", "string", "patientId", "string", "status", "string",
                "specialtyCode", "string", "createdAt", "timestamp"),
            Map.of("retention", "10years")
        );
    }

    // ==================== Core Operations ====================

    /**
     * Creates a new patient referral.
     *
     * @param referral the referral to create
     * @return Promise containing the stored referral with generated ID
     */
    public Promise<Referral> createReferral(Referral referral) {
        ensureRunning();

        String patientId = PhrInputSanitizationUtils.requireSafeIdentifier(referral.patientId(), "patientId");
        String encounterId = referral.encounterId() == null
            ? null
            : PhrInputSanitizationUtils.requireSafeIdentifier(referral.encounterId(), "encounterId");
        String referringProviderId = PhrInputSanitizationUtils.requireSafeIdentifier(
            referral.referringProviderId(),
            "referringProviderId"
        );
        String receivingProviderId = referral.receivingProviderId() == null
            ? null
            : PhrInputSanitizationUtils.requireSafeIdentifier(referral.receivingProviderId(), "receivingProviderId");
        String specialtyCode = PhrInputSanitizationUtils.requireSafeCode(referral.specialtyCode(), "specialtyCode");
        String clinicalReason = PhrInputSanitizationUtils.sanitizeRequiredText(referral.clinicalReason(), "clinicalReason", 2000);

        String id = referral.id() != null ? referral.id() : generateId("ref");
        Referral toStore = new Referral(
            id,
            patientId,
            encounterId,
            referringProviderId,
            receivingProviderId,
            specialtyCode,
            clinicalReason,
            referral.urgency(),
            ReferralStatus.PENDING,
            Instant.now(),
            null,
            null
        );

        return createRecord(
            REFERRAL_DATASET,
            id,
            toStore,
            Map.of(
                "patientId", toStore.patientId(),
                "status", toStore.status().name(),
                "urgency", toStore.urgency().name()
            ),
            "Referral",
            1
        ).then(stored -> audit("CREATE_REFERRAL", stored.patientId(),
            "Referral created to specialty: " + stored.specialtyCode() + " [" + stored.urgency() + "]")
            .map($ -> stored));
    }

    public Promise<Referral> acceptReferral(String referralId, String acceptingProviderId) {
        ensureRunning();

        String sanitizedReferralId = PhrInputSanitizationUtils.requireSafeIdentifier(referralId, "referralId");
        String sanitizedAcceptingProviderId = PhrInputSanitizationUtils.requireSafeIdentifier(
            acceptingProviderId,
            "acceptingProviderId"
        );

        return getReferral(sanitizedReferralId)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.<Referral>ofException(
                        new IllegalStateException("Referral not found: " + sanitizedReferralId));
                }
                Referral existing = opt.get();
                if (existing.status() != ReferralStatus.PENDING) {
                    return Promise.<Referral>ofException(
                        new IllegalStateException("Cannot accept referral in status: " + existing.status()));
                }
                Referral accepted = new Referral(
                    existing.id(), existing.patientId(), existing.encounterId(),
                    existing.referringProviderId(), sanitizedAcceptingProviderId, existing.specialtyCode(),
                    existing.clinicalReason(), existing.urgency(),
                    ReferralStatus.ACCEPTED, existing.createdAt(), Instant.now(), null
                );
                return updateRecord(
                    REFERRAL_DATASET,
                    sanitizedReferralId,
                    accepted,
                    Map.of("status", "ACCEPTED"),
                    "Referral",
                    1
                ).then(updated -> audit("ACCEPT_REFERRAL", updated.patientId(),
                    "Referral accepted by: " + sanitizedAcceptingProviderId)
                    .map($ -> updated));
            });
    }

    public Promise<Referral> closeReferral(String referralId, String closureNotes) {
        ensureRunning();

        String sanitizedReferralId = PhrInputSanitizationUtils.requireSafeIdentifier(referralId, "referralId");
        String sanitizedClosureNotes = PhrInputSanitizationUtils.sanitizeOptionalText(closureNotes, "closureNotes", 1000);

        return getReferral(sanitizedReferralId)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.<Referral>ofException(
                        new IllegalStateException("Referral not found: " + sanitizedReferralId));
                }
                Referral existing = opt.get();
                Referral closed = new Referral(
                    existing.id(), existing.patientId(), existing.encounterId(),
                    existing.referringProviderId(), existing.receivingProviderId(), existing.specialtyCode(),
                    existing.clinicalReason(), existing.urgency(),
                    ReferralStatus.COMPLETED, existing.createdAt(), existing.acceptedAt(), Instant.now()
                );
                return updateRecord(
                    REFERRAL_DATASET,
                    sanitizedReferralId,
                    closed,
                    Map.of("status", "COMPLETED"),
                    "Referral",
                    1
                ).then(updated -> audit(
                        "CLOSE_REFERRAL",
                        updated.patientId(),
                        sanitizedClosureNotes == null ? "Referral closed" : "Referral closed: " + sanitizedClosureNotes)
                    .map($ -> updated));
            });
    }

    public Promise<Optional<Referral>> getReferral(String referralId) {
        ensureRunning();
        return readRecord(REFERRAL_DATASET, referralId, Referral.class);
    }

    public Promise<List<Referral>> getPatientReferrals(String patientId) {
        ensureRunning();

        return queryRecords(
            REFERRAL_DATASET,
            "patientId = :patientId",
            Map.of("patientId", patientId),
            500,
            0,
            Referral.class
        ).map(referrals -> referrals.stream()
            .sorted((a, b) -> b.createdAt().compareTo(a.createdAt()))
            .toList());
    }

    // ==================== Private Helpers ====================

    // ==================== Inner Types ====================

    /**
     * A patient referral record.
     *
     * @param id                    unique referral identifier
     * @param patientId             patient being referred
     * @param referringProviderId   the provider initiating the referral
     * @param receivingProviderId   the provider receiving the referral (may be null until accepted)
     * @param specialtyCode         target specialty (e.g. "CARDIOLOGY", "ORTHOPEDICS")
     * @param urgency               urgency level
     * @param clinicalReason        clinical reason for the referral
     * @param notes                 additional notes or closure summary
     * @param status                referral lifecycle status
     * @param createdAt             when the referral was created
     * @param acceptedAt            when the referral was accepted (null if pending)
     * @param closedAt              when the referral was closed (null if open)
     */
    public record Referral(
            String id,
            String patientId,
            String encounterId,
            String referringProviderId,
            String receivingProviderId,
            String specialtyCode,
            String clinicalReason,
            ReferralUrgency urgency,
            ReferralStatus status,
            Instant createdAt,
            Instant acceptedAt,
            Instant closedAt
    ) {}

    /** Urgency classification for a referral. */
    public enum ReferralUrgency {
        /** Routine, non-urgent referral. */
        ROUTINE,
        /** Urgent referral requiring prompt attention. */
        URGENT,
        /** Emergency referral requiring immediate action. */
        EMERGENCY
    }

    /** Lifecycle status for a referral. */
    public enum ReferralStatus {
        PENDING, ACCEPTED, DECLINED, COMPLETED, CANCELLED
    }
}
