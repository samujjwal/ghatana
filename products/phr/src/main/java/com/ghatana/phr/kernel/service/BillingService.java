package com.ghatana.phr.kernel.service;

import com.ghatana.kernel.context.KernelContext;

import com.ghatana.plugin.ledger.LedgerPlugin;
import com.ghatana.plugin.ledger.LedgerTransaction;
import io.activej.promise.Promise;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
public class BillingService extends PhrServiceBase {

    private static final Logger log = LoggerFactory.getLogger(BillingService.class);

    private static final String ENCOUNTER_DATASET = "phr.billing.encounters";
    private static final String CLAIM_DATASET = "phr.billing.claims";

    /** Retained for tenant-context lookup in async callbacks. */
    private final KernelContext kernelContext;
    private final LedgerPlugin ledgerPlugin;

    public BillingService(KernelContext context) {
        this(context, null);
    }

    /**
     * Creates a BillingService with Finance ledger integration enabled.
     *
     * @param context              kernel context providing DataCloudKernelAdapter
     * @param ledgerPlugin         ledger plugin for cross-domain billing integration;
     *                             may be {@code null} to disable ledger posting
     */
    public BillingService(KernelContext context, LedgerPlugin ledgerPlugin) {
        super(context);
        this.kernelContext = context;
        this.ledgerPlugin = ledgerPlugin;
    }

    /**
     * Creates a BillingService with Finance ledger integration enabled and a custom executor.
     *
     * @param context              kernel context providing DataCloudKernelAdapter
     * @param ledgerPlugin         ledger plugin for cross-domain billing integration;
     *                             may be {@code null} to disable ledger posting
     * @param executor             executor for blocking operations (e.g., in tests)
     */
    public BillingService(KernelContext context, LedgerPlugin ledgerPlugin, java.util.concurrent.Executor executor) {
        super(context, executor);
        this.kernelContext = context;
        this.ledgerPlugin = ledgerPlugin;
    }

    @Override
    public Promise<Void> start() {
        return super.start();
    }

    @Override
    public Promise<Void> stop() {
        return super.stop();
    }

    @Override
    public String getName() {
        return "billing";
    }

    @Override
    protected Promise<Void> initializeDatasets() {
        Promise<Void> encounters = createSchema(
            ENCOUNTER_DATASET,
            Map.of("id", "string", "patientId", "string", "status", "string",
                "createdAt", "timestamp"),
            Map.of("retention", "10years")
        );

        Promise<Void> claims = createSchema(
            CLAIM_DATASET,
            Map.of("id", "string", "patientId", "string", "status", "string",
                "submittedAt", "timestamp"),
            Map.of("retention", "10years")
        );

        return encounters.then($ -> claims);
    }

    // ==================== Core Operations ====================

    /**
     * Creates a billing encounter (a billable healthcare visit).
     *
     * @param encounter the billing encounter
     * @return Promise containing the stored encounter
     */
    public Promise<BillingEncounter> createEncounter(BillingEncounter encounter) {
        ensureRunning();

        String patientId = PhrInputSanitizationUtils.requireSafeIdentifier(encounter.patientId(), "patientId");
        String providerId = PhrInputSanitizationUtils.requireSafeIdentifier(encounter.providerId(), "providerId");
        String facilityId = encounter.facilityId() == null
            ? null
            : PhrInputSanitizationUtils.requireSafeIdentifier(encounter.facilityId(), "facilityId");
        List<ServiceLine> serviceLines = sanitizeServiceLines(encounter.serviceLines());
        if (encounter.totalAmount() == null || encounter.totalAmount().signum() < 0) {
            return Promise.ofException(new IllegalArgumentException("totalAmount must be non-negative"));
        }
        String currency = PhrInputSanitizationUtils.requireSafeCode(encounter.currency(), "currency");

        String id = encounter.id() != null ? encounter.id() : generateId("enc");
        BillingEncounter toStore = new BillingEncounter(
            id,
            patientId,
            providerId,
            facilityId,
            serviceLines,
            encounter.totalAmount(),
            currency,
            EncounterStatus.OPEN,
            Instant.now(),
            null
        );

        return createRecord(
            ENCOUNTER_DATASET,
            id,
            toStore,
            enrichMetadata(Map.of("patientId", toStore.patientId(), "status", "OPEN")),
            "BillingEncounter",
            1
        ).then(stored -> audit("CREATE_ENCOUNTER", stored.patientId(),
            "Billing encounter created: " + id)
            .map($ -> stored));
    }

    public Promise<BillingEncounter> closeEncounter(String encounterId) {
        ensureRunning();

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
                return updateRecord(
                    ENCOUNTER_DATASET,
                    encounterId,
                    closed,
                    enrichMetadata(Map.of("patientId", closed.patientId(), "status", "CLOSED")),
                    "BillingEncounter",
                    1
                ).then(updated -> audit("CLOSE_ENCOUNTER", updated.patientId(),
                    "Encounter closed: " + encounterId)
                    .then($ -> postEncounterToLedger(updated))
                    .map($ -> updated));
            });
    }

    /**
     * Posts a closed encounter as a {@link LedgerTransaction} to the Finance ledger.
     * A no-op when no {@link LedgerPlugin} was injected.
     */
    private Promise<Void> postEncounterToLedger(BillingEncounter encounter) {
        if (ledgerPlugin == null) {
            return Promise.complete();
        }
        String tenantId = Optional.ofNullable(kernelContext.getTenantContext())
            .map(com.ghatana.kernel.context.KernelTenantContext::getTenantId)
            .orElse("default");
        LedgerTransaction tx = LedgerTransaction.builder()
            .transactionId("enc:" + encounter.id())
            .sourceId("phr")
            .debitAccount("PHR:AR:" + encounter.patientId())
            .creditAccount("PHR:REVENUE:" + encounter.providerId())
            .amount(encounter.totalAmount())
            .currency(encounter.currency())
            .type(LedgerTransaction.TransactionType.CHARGE)
            .description("Healthcare encounter closed: " + encounter.id())
            .externalReferenceId(encounter.id())
            .tenantId(tenantId)
            .occurredAt(encounter.closedAt())
            .build();

        return ledgerPlugin.postTransaction(tx)
            .map(entryId -> {
                log.info("Encounter '{}' posted to ledger as entry '{}'", encounter.id(), entryId);
                return (Void) null;
            });
    }

    public Promise<InsuranceClaim> submitClaim(InsuranceClaim claim) {
        ensureRunning();

        String patientId = PhrInputSanitizationUtils.requireSafeIdentifier(claim.patientId(), "patientId");
        String encounterId = PhrInputSanitizationUtils.requireSafeIdentifier(claim.encounterId(), "encounterId");
        String insurerId = PhrInputSanitizationUtils.requireSafeIdentifier(claim.insurerId(), "insurerId");
        String policyNumber = PhrInputSanitizationUtils.requireSafeCode(claim.policyNumber(), "policyNumber");
        if (claim.claimedAmount() == null || claim.claimedAmount().signum() < 0) {
            return Promise.ofException(new IllegalArgumentException("claimedAmount must be non-negative"));
        }
        String currency = PhrInputSanitizationUtils.requireSafeCode(claim.currency(), "currency");

        String id = claim.id() != null ? claim.id() : generateId("clm");
        InsuranceClaim toStore = new InsuranceClaim(
            id,
            patientId,
            encounterId,
            insurerId,
            policyNumber,
            claim.claimedAmount(),
            currency,
            ClaimStatus.SUBMITTED,
            Instant.now(),
            null,
            null
        );

        return createRecord(
            CLAIM_DATASET,
            id,
            toStore,
            enrichMetadata(Map.of("patientId", toStore.patientId(), "status", "SUBMITTED")),
            "InsuranceClaim",
            1
        ).then(stored -> audit("SUBMIT_CLAIM", stored.patientId(),
            "Claim submitted to insurer: " + stored.insurerId())
            .map($ -> stored));
    }

    public Promise<InsuranceClaim> updateClaimStatus(String claimId, ClaimStatus newStatus, String adjNote) {
        ensureRunning();

        String sanitizedClaimId = PhrInputSanitizationUtils.requireSafeIdentifier(claimId, "claimId");
        String sanitizedAdjudicationNote = PhrInputSanitizationUtils.sanitizeOptionalText(adjNote, "adjNote", 1000);

        return getClaim(sanitizedClaimId)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.<InsuranceClaim>ofException(
                        new IllegalStateException("Claim not found: " + sanitizedClaimId));
                }
                InsuranceClaim existing = opt.get();
                InsuranceClaim updated = new InsuranceClaim(
                    existing.id(), existing.patientId(), existing.encounterId(),
                    existing.insurerId(), existing.policyNumber(),
                    existing.claimedAmount(), existing.currency(),
                    newStatus, existing.submittedAt(),
                    newStatus == ClaimStatus.APPROVED || newStatus == ClaimStatus.DENIED
                        ? Instant.now() : existing.adjudicatedAt(),
                    sanitizedAdjudicationNote
                );
                return updateRecord(
                    CLAIM_DATASET,
                    sanitizedClaimId,
                    updated,
                    enrichMetadata(Map.of("patientId", updated.patientId(), "status", newStatus.name())),
                    "InsuranceClaim",
                    1
                ).then(saved -> audit("UPDATE_CLAIM_STATUS", saved.patientId(),
                    "Claim status: " + newStatus)
                    .map($ -> saved));
            });
    }

    public Promise<Optional<BillingEncounter>> getEncounter(String encounterId) {
        ensureRunning();
        return readRecord(ENCOUNTER_DATASET, encounterId, BillingEncounter.class);
    }

    public Promise<Optional<InsuranceClaim>> getClaim(String claimId) {
        ensureRunning();
        return readRecord(CLAIM_DATASET, claimId, InsuranceClaim.class);
    }

    public Promise<List<BillingEncounter>> getPatientBillingHistory(String patientId) {
        ensureRunning();

        return queryRecords(
            ENCOUNTER_DATASET,
            "patientId = :patientId",
            Map.of("patientId", patientId),
            1000,
            0,
            BillingEncounter.class
        ).map(encounters -> encounters.stream()
            .sorted((a, b) -> b.createdAt().compareTo(a.createdAt()))
            .toList());
    }

    // ==================== Private Helpers ====================

    private Map<String, String> enrichMetadata(Map<String, String> baseMetadata) {
        String principalId = Optional.ofNullable(kernelContext.getTenantContext())
            .map(ctx -> ctx.getSecurityContext())
            .map(com.ghatana.kernel.context.KernelTenantContext.SecurityContext::getUserId)
            .orElse("system");
        return mutationMetadata(baseMetadata, principalId);
    }

    private List<ServiceLine> sanitizeServiceLines(List<ServiceLine> serviceLines) {
        if (serviceLines == null || serviceLines.isEmpty()) {
            throw new IllegalArgumentException("serviceLines is required");
        }
        return serviceLines.stream()
            .map(line -> {
                String serviceCode = PhrInputSanitizationUtils.requireSafeCode(line.serviceCode(), "serviceCode");
                String description = PhrInputSanitizationUtils.sanitizeRequiredText(line.description(), "description", 500);
                if (line.quantity() <= 0) {
                    throw new IllegalArgumentException("quantity must be positive");
                }
                if (line.unitPrice() == null || line.unitPrice().signum() < 0) {
                    throw new IllegalArgumentException("unitPrice must be non-negative");
                }
                String currency = PhrInputSanitizationUtils.requireSafeCode(line.currency(), "currency");
                return new ServiceLine(serviceCode, description, line.quantity(), line.unitPrice(), currency);
            })
            .toList();
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
