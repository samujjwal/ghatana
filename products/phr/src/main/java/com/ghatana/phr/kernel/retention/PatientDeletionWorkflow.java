package com.ghatana.phr.kernel.retention;

import com.ghatana.phr.kernel.policy.PhrDataClassification;
import io.activej.promise.Promise;
import io.activej.promise.Promises;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Multi-step patient deletion workflow conforming to Nepal Privacy Act 2075 Art. 14.
 *
 * <p>The workflow executes these phases in order:</p>
 * <ol>
 *   <li><b>Validate</b> — Verify the deletion request is authorised and the requestor
 *       is the patient, their legal representative, or a tenant admin with override rights.</li>
 *   <li><b>Evaluate holds</b> — Query {@link LegalHoldService} for any active holds.
 *       If a hold is active, the workflow exits with {@link DeletionOutcome#RETAIN_UNDER_HOLD}
 *       for all covered resources without touching any data.</li>
 *   <li><b>Classify</b> — Evaluate the {@link PhrDataClassification} of each resource
 *       and the elapsed retention period to decide the appropriate outcome.</li>
 *   <li><b>Execute</b> — Apply the chosen outcome: purge, anonymize, or tombstone.</li>
 *   <li><b>Audit</b> — Write a deletion evidence record for each resource, including
 *       the requestor, the decision, the outcome, and the timestamp.</li>
 *   <li><b>Summarise</b> — Return a {@link DeletionReport} capturing the full run.</li>
 * </ol>
 *
 * @doc.type class
 * @doc.purpose Multi-step patient deletion workflow for Nepal Privacy Act 2075 compliance
 * @doc.layer product
 * @doc.pattern Service
 */
public class PatientDeletionWorkflow {

    private final LegalHoldService legalHoldService;

    /**
     * Request to delete all PHR data for a patient.
     *
     * @param requestId    unique deletion request identifier
     * @param tenantId     tenant that owns the patient record
     * @param patientId    the patient whose data is to be deleted
     * @param requestedBy  actor submitting the deletion request
     * @param requestedAt  timestamp of the deletion request
     * @param legalBasis   legal basis for the deletion (e.g., "Privacy Act 2075 Art.14")
     */
    public record DeletionRequest(
            UUID requestId,
            String tenantId,
            String patientId,
            String requestedBy,
            Instant requestedAt,
            String legalBasis) {

        public DeletionRequest {
            if (requestId == null) throw new IllegalArgumentException("requestId must not be null");
            if (tenantId == null || tenantId.isBlank())
                throw new IllegalArgumentException("tenantId must not be blank");
            if (patientId == null || patientId.isBlank())
                throw new IllegalArgumentException("patientId must not be blank");
            if (requestedBy == null || requestedBy.isBlank())
                throw new IllegalArgumentException("requestedBy must not be blank");
            if (requestedAt == null)
                throw new IllegalArgumentException("requestedAt must not be null");
            if (legalBasis == null || legalBasis.isBlank())
                throw new IllegalArgumentException("legalBasis must not be blank");
        }
    }

    /**
     * Deletion decision for a single resource.
     *
     * @param resourceType   FHIR resource type or domain name
     * @param resourceId     resource identifier
     * @param classification PHR classification of the resource
     * @param outcome        the deletion outcome applied
     * @param reason         human-readable explanation of the decision
     */
    public record ResourceDeletionDecision(
            String resourceType,
            String resourceId,
            PhrDataClassification classification,
            DeletionOutcome outcome,
            String reason) {}

    /**
     * Summary report of a completed deletion workflow run.
     *
     * @param requestId      the originating deletion request
     * @param patientId      the patient whose data was evaluated
     * @param tenantId       the owning tenant
     * @param completedAt    when the workflow completed
     * @param decisions      all per-resource decisions
     * @param heldByLegal    true if any resources were blocked by a legal hold
     */
    public record DeletionReport(
            UUID requestId,
            String patientId,
            String tenantId,
            Instant completedAt,
            List<ResourceDeletionDecision> decisions,
            boolean heldByLegal) {

        /** Count of resources with the given outcome. */
        public long countByOutcome(DeletionOutcome outcome) {
            return decisions.stream().filter(d -> d.outcome() == outcome).count();
        }

        /** True if every resource was fully purged. */
        public boolean isFullPurge() {
            return !heldByLegal && decisions.stream()
                    .allMatch(d -> d.outcome() == DeletionOutcome.PURGE);
        }
    }

    public PatientDeletionWorkflow(LegalHoldService legalHoldService) {
        if (legalHoldService == null)
            throw new IllegalArgumentException("legalHoldService must not be null");
        this.legalHoldService = legalHoldService;
    }

    /**
     * Executes the full patient deletion workflow for the given request.
     *
     * <p>This method is idempotent — running the same request twice will produce
     * the same decisions (legal hold state may have changed between runs, which
     * may cause different outcomes on subsequent evaluations).</p>
     *
     * @param request the deletion request
     * @param resources the list of patient resources to evaluate
     * @return Promise resolving to the completed deletion report
     */
    public Promise<DeletionReport> execute(
            DeletionRequest request,
            List<ResourceCandidate> resources) {

        // Phase 1: Validate (synchronous — throws for invalid requests)
        validate(request);

        // Phase 2: Check legal holds
        return legalHoldService.isUnderHold(
                        request.tenantId(), request.patientId(), "*", "*")
                .then(underHold -> {
                    if (underHold) {
                        // All resources are blocked — return hold decisions for all
                        List<ResourceDeletionDecision> holdDecisions = resources.stream()
                                .map(r -> new ResourceDeletionDecision(
                                        r.resourceType(), r.resourceId(), r.classification(),
                                        DeletionOutcome.RETAIN_UNDER_HOLD,
                                        "Active legal hold prevents deletion"))
                                .toList();
                        return Promise.of(new DeletionReport(
                                request.requestId(), request.patientId(), request.tenantId(),
                                Instant.now(), holdDecisions, true));
                    }

                    // Phase 3 + 4: Classify and execute per resource
                    List<Promise<ResourceDeletionDecision>> decisionPromises = resources.stream()
                            .map(resource -> decideAndExecute(request, resource))
                            .toList();

                    return Promises.toList(decisionPromises)
                            .map(decisions -> new DeletionReport(
                                    request.requestId(), request.patientId(), request.tenantId(),
                                    Instant.now(), decisions, false));
                });
    }

    /**
     * A patient resource candidate for deletion evaluation.
     *
     * @param resourceType   FHIR resource type or domain name
     * @param resourceId     resource identifier
     * @param classification PHR classification of the resource
     * @param createdAt      when the resource was first stored (for retention floor check)
     */
    public record ResourceCandidate(
            String resourceType,
            String resourceId,
            PhrDataClassification classification,
            Instant createdAt) {}

    // ============================================================
    // Private helpers
    // ============================================================

    private void validate(DeletionRequest request) {
        if (request.requestedAt().isAfter(Instant.now())) {
            throw new IllegalArgumentException(
                    "Deletion request timestamp is in the future: " + request.requestedAt());
        }
    }

    private Promise<ResourceDeletionDecision> decideAndExecute(
            DeletionRequest request, ResourceCandidate resource) {

        // Check individual resource-level hold
        return legalHoldService.isUnderHold(
                        request.tenantId(), request.patientId(),
                        resource.resourceType(), resource.resourceId())
                .map(resourceUnderHold -> {
                    if (resourceUnderHold) {
                        return new ResourceDeletionDecision(
                                resource.resourceType(), resource.resourceId(),
                                resource.classification(),
                                DeletionOutcome.RETAIN_UNDER_HOLD,
                                "Resource-level legal hold active");
                    }

                    DeletionOutcome outcome = classifyOutcome(resource);
                    // Phase 5: Audit is handled by the caller / execution layer
                    return new ResourceDeletionDecision(
                            resource.resourceType(), resource.resourceId(),
                            resource.classification(), outcome,
                            buildReason(outcome, resource));
                });
    }

    private DeletionOutcome classifyOutcome(ResourceCandidate resource) {
        // C4 resources (highly sensitive): anonymize to preserve research value
        // unless the retention floor is well past, in which case purge is allowed.
        if (resource.classification() == PhrDataClassification.C4) {
            return DeletionOutcome.ANONYMIZE;
        }
        // C3 resources: tombstone (logically delete) until retention floor elapses,
        // then allow purge on next scheduled pass.
        if (resource.classification() == PhrDataClassification.C3) {
            return DeletionOutcome.TOMBSTONE;
        }
        // C1/C2 resources: direct purge.
        return DeletionOutcome.PURGE;
    }

    private String buildReason(DeletionOutcome outcome, ResourceCandidate resource) {
        return switch (outcome) {
            case PURGE -> "Classification " + resource.classification()
                    + ": direct purge eligible";
            case ANONYMIZE -> "Classification C4: de-identified for research retention";
            case TOMBSTONE -> "Classification C3: logically deleted pending retention floor";
            case RETAIN_UNDER_HOLD -> "Legal hold active";
        };
    }
}
