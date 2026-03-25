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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Billing and Insurance Baseline Service for PHR.
 *
 * <p>Manages patient billing records including service line items, insurance claims,
 * claim status tracking, and explanation of benefits (EOB). Supports Nepal's Social
 * Health Security Fund (NHSF) claim codes and private insurance claim formats.
 * This is the baseline implementation covering core CRUD; advanced clearinghouse
 * EDI integration is out of scope for v1.</p>
 *
 * @doc.type class
 * @doc.purpose PHR billing baseline — service items, insurance claims, claim status
 * @doc.layer product
 * @doc.pattern Service
 * @author Ghatana PHR Team
 * @since 1.0.0
 */
public class BillingService {

    private static final String ENCOUNTER_DATASET = "phr.billing.encounters";
    private static final String CLAIM_DATASET = "phr.billing.claims";
    private static final String AUDIT_DATASET = "phr.billing.audit";

    private final DataCloudKernelAdapter dataCloud;
    private volatile boolean running = false;

    /**
     * Constructs a BillingService.
     *
     * @param context kernel context providing DataCloudKernelAdapter
     */
    public BillingService(KernelContext context) {
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
        return "billing";
    }

    // ==================== Core Operations ====================

    /**
     * Creates a billing encounter (a billable healthcare visit).
     *
     * @param encounter the billing encounter
     * @return Promise containing the stored encounter
     */
    public Promise<BillingEncounter> createEncounter(BillingEncounter encounter) {
        if (!running) {
            return Promise.ofException(new IllegalStateException("Service not running"));
        }

        Objects.requireNonNull(encounter.patientId(), "patientId");
        Objects.requireNonNull(encounter.providerId(), "providerId");

        String id = encounter.id() != null ? encounter.id() : generateId("enc");
        BillingEncounter toStore = new BillingEncounter(
                id,
                encounter.patientId(),
                encounter.providerId(),
                encounter.facilityId(),
                encounter.serviceLines(),
                encounter.totalAmount(),
                encounter.currency(),
                EncounterStatus.OPEN,
                Instant.now(),
                null
        );

        DataWriteRequest request = new DataWriteRequest(
                ENCOUNTER_DATASET,
                id,
                TypedDataSerializer.toBytes(toStore, "BillingEncounter", 1),
                Map.of("patientId", toStore.patientId(), "status", "OPEN")
        );

        return dataCloud.writeData(request)
                .then($ -> audit("CREATE_ENCOUNTER", toStore.patientId(),
                        "Billing encounter created: " + id))
                .map($ -> toStore);
    }

    /**
     * Closes (finalizes) a billing encounter so it can be submitted for payment.
     *
     * @param encounterId the encounter identifier
     * @return Promise containing the finalized encounter
     */
    public Promise<BillingEncounter> closeEncounter(String encounterId) {
        if (!running) {
            return Promise.ofException(new IllegalStateException("Service not running"));
        }

        return getEncounter(encounterId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.<BillingEncounter>ofException(
                                new IllegalStateException("Encounter not found: " + encounterId));
                    }
                    BillingEncounter existing = opt.get();
                    BillingEncounter closed = new BillingEncounter(
                            existing.id(), existing.patientId(), existing.providerId(),
                            existing.facilityId(), existing.serviceLines(), existing.totalAmount(),
                            existing.currency(), EncounterStatus.CLOSED,
                            existing.createdAt(), Instant.now()
                    );
                    DataWriteRequest req = new DataWriteRequest(
                            ENCOUNTER_DATASET, encounterId,
                            TypedDataSerializer.toBytes(closed, "BillingEncounter", 1),
                            Map.of("status", "CLOSED")
                    );
                    return dataCloud.writeData(req)
                            .then($ -> audit("CLOSE_ENCOUNTER", closed.patientId(),
                                    "Encounter closed: " + encounterId))
                            .map($ -> closed);
                });
    }

    /**
     * Submits an insurance claim for a closed encounter.
     *
     * @param claim the insurance claim
     * @return Promise containing the stored claim
     */
    public Promise<InsuranceClaim> submitClaim(InsuranceClaim claim) {
        if (!running) {
            return Promise.ofException(new IllegalStateException("Service not running"));
        }

        Objects.requireNonNull(claim.patientId(), "patientId");
        Objects.requireNonNull(claim.encounterId(), "encounterId");

        String id = claim.id() != null ? claim.id() : generateId("clm");
        InsuranceClaim toStore = new InsuranceClaim(
                id,
                claim.patientId(),
                claim.encounterId(),
                claim.insurerId(),
                claim.policyNumber(),
                claim.claimedAmount(),
                claim.currency(),
                ClaimStatus.SUBMITTED,
                Instant.now(),
                null,
                null
        );

        DataWriteRequest request = new DataWriteRequest(
                CLAIM_DATASET,
                id,
                TypedDataSerializer.toBytes(toStore, "InsuranceClaim", 1),
                Map.of("patientId", toStore.patientId(), "status", "SUBMITTED")
        );

        return dataCloud.writeData(request)
                .then($ -> audit("SUBMIT_CLAIM", toStore.patientId(),
                        "Claim submitted to insurer: " + toStore.insurerId()))
                .map($ -> toStore);
    }

    /**
     * Updates the status of an insurance claim (e.g. after adjudication).
     *
     * @param claimId    the claim identifier
     * @param newStatus  the updated claim status
     * @param adjNote    optional adjudication note
     * @return Promise containing the updated claim
     */
    public Promise<InsuranceClaim> updateClaimStatus(String claimId, ClaimStatus newStatus, String adjNote) {
        if (!running) {
            return Promise.ofException(new IllegalStateException("Service not running"));
        }

        return getClaim(claimId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.<InsuranceClaim>ofException(
                                new IllegalStateException("Claim not found: " + claimId));
                    }
                    InsuranceClaim existing = opt.get();
                    InsuranceClaim updated = new InsuranceClaim(
                            existing.id(), existing.patientId(), existing.encounterId(),
                            existing.insurerId(), existing.policyNumber(),
                            existing.claimedAmount(), existing.currency(),
                            newStatus, existing.submittedAt(),
                            newStatus == ClaimStatus.APPROVED || newStatus == ClaimStatus.DENIED
                                    ? Instant.now() : existing.adjudicatedAt(),
                            adjNote
                    );
                    DataWriteRequest req = new DataWriteRequest(
                            CLAIM_DATASET, claimId,
                            TypedDataSerializer.toBytes(updated, "InsuranceClaim", 1),
                            Map.of("status", newStatus.name())
                    );
                    return dataCloud.writeData(req)
                            .then($ -> audit("UPDATE_CLAIM_STATUS", updated.patientId(),
                                    "Claim status: " + newStatus))
                            .map($ -> updated);
                });
    }

    /**
     * Retrieves a billing encounter by ID.
     *
     * @param encounterId the encounter identifier
     * @return Promise containing the encounter if found
     */
    public Promise<Optional<BillingEncounter>> getEncounter(String encounterId) {
        if (!running) {
            return Promise.of(Optional.empty());
        }

        return dataCloud.readData(new DataReadRequest(ENCOUNTER_DATASET, encounterId, Map.of()))
                .map(result -> {
                    if (result == null || result.getData() == null) return Optional.empty();
                    return Optional.ofNullable((BillingEncounter) TypedDataSerializer.fromBytes(result.getData(), BillingEncounter.class));
                })
                ;
    }

    /**
     * Retrieves an insurance claim by ID.
     *
     * @param claimId the claim identifier
     * @return Promise containing the claim if found
     */
    public Promise<Optional<InsuranceClaim>> getClaim(String claimId) {
        if (!running) {
            return Promise.of(Optional.empty());
        }

        return dataCloud.readData(new DataReadRequest(CLAIM_DATASET, claimId, Map.of()))
                .map(result -> {
                    if (result == null || result.getData() == null) return Optional.empty();
                    return Optional.ofNullable((InsuranceClaim) TypedDataSerializer.fromBytes(result.getData(), InsuranceClaim.class));
                })
                ;
    }

    /**
     * Returns billing history for a patient.
     *
     * @param patientId the patient identifier
     * @return Promise containing all billing encounters
     */
    public Promise<List<BillingEncounter>> getPatientBillingHistory(String patientId) {
        if (!running) {
            return Promise.of(List.of());
        }

        return dataCloud.queryData(new DataQueryRequest(
                ENCOUNTER_DATASET,
                "patientId = :patientId",
                Map.of("patientId", patientId),
                1000,
                0
        )).map(QueryResult::getResults)
                .map(results -> results.stream()
                        .map(r -> TypedDataSerializer.fromBytes(r.getData(), BillingEncounter.class))
                        .filter(Objects::nonNull)
                        .sorted((a, b) -> b.createdAt().compareTo(a.createdAt()))
                        .toList());
    }

    // ==================== Private Helpers ====================

    private Promise<Void> initializeDatasets() {
        Promise<Void> encounters = dataCloud.createSchema(new DataCloudKernelAdapter.SchemaCreateRequest(
                ENCOUNTER_DATASET,
                Map.of("id", "string", "patientId", "string", "status", "string",
                        "createdAt", "timestamp"),
                Map.of("retention", "10years")
        ));

        Promise<Void> claims = dataCloud.createSchema(new DataCloudKernelAdapter.SchemaCreateRequest(
                CLAIM_DATASET,
                Map.of("id", "string", "patientId", "string", "status", "string",
                        "submittedAt", "timestamp"),
                Map.of("retention", "10years")
        ));

        Promise<Void> audit = dataCloud.createSchema(new DataCloudKernelAdapter.SchemaCreateRequest(
                AUDIT_DATASET,
                Map.of("action", "string", "patientId", "string", "timestamp", "timestamp"),
                Map.of("retention", "25years")
        ));

        return Promises.all(encounters, claims, audit).map($ -> null);
    }

    private Promise<Void> audit(String action, String patientId, String details) {
        String auditId = generateId("aud");
        record AuditEntry(String id, Instant ts, String action, String patient, String details) {}
        return dataCloud.writeData(new DataWriteRequest(
                AUDIT_DATASET, auditId,
                TypedDataSerializer.toBytes(
                        new AuditEntry(auditId, Instant.now(), action, patientId, details),
                        "BillingAuditEntry", 1),
                Map.of("timestamp", Instant.now().toString())
        ));
    }

    private String generateId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    // ==================== Inner Types ====================

    /**
     * A single line item in a billing encounter (one service rendered).
     *
     * @param serviceCode    CPT or national billing code
     * @param description    human-readable service description
     * @param quantity       units of service
     * @param unitPrice      price per unit
     * @param currency       ISO 4217 currency code (e.g. "NPR")
     */
    public record ServiceLine(
            String serviceCode,
            String description,
            int quantity,
            BigDecimal unitPrice,
            String currency
    ) {
        /** Computes the total charge for this line item. */
        public BigDecimal total() {
            return unitPrice.multiply(BigDecimal.valueOf(quantity));
        }
    }

    /**
     * A billing encounter grouping all services rendered during a visit.
     *
     * @param id            unique encounter identifier
     * @param patientId     patient billed
     * @param providerId    provider rendering services
     * @param facilityId    facility where services were rendered
     * @param serviceLines  individual service line items
     * @param totalAmount   pre-computed total amount
     * @param currency      ISO 4217 currency code (e.g. "NPR")
     * @param status        encounter status
     * @param createdAt     when the encounter was created
     * @param closedAt      when the encounter was closed (null if open)
     */
    public record BillingEncounter(
            String id,
            String patientId,
            String providerId,
            String facilityId,
            List<ServiceLine> serviceLines,
            BigDecimal totalAmount,
            String currency,
            EncounterStatus status,
            Instant createdAt,
            Instant closedAt
    ) {}

    /**
     * An insurance claim submitted on behalf of a patient.
     *
     * @param id             unique claim identifier
     * @param patientId      patient the claim is for
     * @param encounterId    the billing encounter this claim covers
     * @param insurerId      insurer identifier (NHSF code or private insurer ID)
     * @param policyNumber   patient's policy number with the insurer
     * @param claimedAmount  total amount claimed
     * @param currency       ISO 4217 currency code
     * @param status         claim lifecycle status
     * @param submittedAt    when the claim was submitted
     * @param adjudicatedAt  when the claim was adjudicated (null if pending)
     * @param adjudicationNote insurer or adjudicator notes
     */
    public record InsuranceClaim(
            String id,
            String patientId,
            String encounterId,
            String insurerId,
            String policyNumber,
            BigDecimal claimedAmount,
            String currency,
            ClaimStatus status,
            Instant submittedAt,
            Instant adjudicatedAt,
            String adjudicationNote
    ) {}

    /** Billing encounter lifecycle status. */
    public enum EncounterStatus {
        OPEN, CLOSED, VOIDED
    }

    /** Insurance claim lifecycle status. */
    public enum ClaimStatus {
        SUBMITTED, UNDER_REVIEW, APPROVED, DENIED, APPEALED, PAID
    }
}
