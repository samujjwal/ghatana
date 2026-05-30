package com.ghatana.phr.kernel.retention;

import com.ghatana.phr.kernel.policy.PhrDataClassification;
import io.activej.promise.Promise;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;

/**
 * Service for generating retention proof evidence per resource type.
 *
 * <p>This service provides evidence that PHR data retention policies are being followed
 * for each resource type. It generates retention proof reports that can be used for
 * compliance audits and regulatory reporting.</p>
 *
 * <p>Retention periods by resource type (Nepal Privacy Act 2075 compliant):
 * <ul>
 *   <li>Patient demographics: 10 years after last activity</li>
 *   <li>Clinical notes: 25 years from creation</li>
 *   <li>Lab results: 25 years from creation</li>
 *   <li>Medications: 10 years from last prescription</li>
 *   <li>Imaging studies: 25 years from creation</li>
 *   <li>Documents: 10 years from upload</li>
 *   <li>Consent records: 10 years after expiry</li>
 *   <li>Audit trails: 7 years (minimum)</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Generates retention proof evidence per resource type for compliance
 * @doc.layer product
 * @doc.pattern Service
 */
public final class RetentionProofService {

    private RetentionProofService() {}

    /**
     * Retention period configuration per resource type.
     */
    public static final Map<String, RetentionPolicy> RETENTION_POLICIES = Map.ofEntries(
        Map.entry("Patient", new RetentionPolicy(PhrDataClassification.C2, 10, ChronoUnit.YEARS, "10 years after last activity")),
        Map.entry("ClinicalNote", new RetentionPolicy(PhrDataClassification.C3, 25, ChronoUnit.YEARS, "25 years from creation")),
        Map.entry("LabResult", new RetentionPolicy(PhrDataClassification.C3, 25, ChronoUnit.YEARS, "25 years from creation")),
        Map.entry("Medication", new RetentionPolicy(PhrDataClassification.C2, 10, ChronoUnit.YEARS, "10 years from last prescription")),
        Map.entry("ImagingStudy", new RetentionPolicy(PhrDataClassification.C3, 25, ChronoUnit.YEARS, "25 years from creation")),
        Map.entry("Document", new RetentionPolicy(PhrDataClassification.C2, 10, ChronoUnit.YEARS, "10 years from upload")),
        Map.entry("Consent", new RetentionPolicy(PhrDataClassification.C2, 10, ChronoUnit.YEARS, "10 years after expiry")),
        Map.entry("AuditTrail", new RetentionPolicy(PhrDataClassification.C1, 7, ChronoUnit.YEARS, "7 years minimum")),
        Map.entry("Encounter", new RetentionPolicy(PhrDataClassification.C2, 10, ChronoUnit.YEARS, "10 years from encounter date")),
        Map.entry("Immunization", new RetentionPolicy(PhrDataClassification.C2, 10, ChronoUnit.YEARS, "10 years from administration")),
        Map.entry("Allergy", new RetentionPolicy(PhrDataClassification.C2, 10, ChronoUnit.YEARS, "10 years from recording")),
        Map.entry("Condition", new RetentionPolicy(PhrDataClassification.C2, 10, ChronoUnit.YEARS, "10 years from diagnosis")),
        Map.entry("Procedure", new RetentionPolicy(PhrDataClassification.C2, 10, ChronoUnit.YEARS, "10 years from procedure date")),
        Map.entry("Observation", new RetentionPolicy(PhrDataClassification.C2, 10, ChronoUnit.YEARS, "10 years from observation"))
    );

    /**
     * Generates retention proof for a specific resource.
     *
     * @param resourceType the type of resource
     * @param resourceId the resource identifier
     * @param createdAt when the resource was created
     * @param lastActivity when the resource was last accessed/modified
     * @return Promise containing the retention proof
     */
    public static Promise<RetentionProof> generateProof(
            String resourceType,
            String resourceId,
            Instant createdAt,
            Instant lastActivity) {

        RetentionPolicy policy = RETENTION_POLICIES.get(resourceType);
        if (policy == null) {
            // Default policy for unknown resource types
            policy = new RetentionPolicy(PhrDataClassification.C2, 10, ChronoUnit.YEARS, "Default 10 years");
        }

        Instant retentionExpiry = calculateRetentionExpiry(createdAt, lastActivity, policy);
        boolean isEligibleForDeletion = Instant.now().isAfter(retentionExpiry);

        RetentionProof proof = new RetentionProof(
            resourceType,
            resourceId,
            policy.classification(),
            policy.period(),
            policy.unit(),
            policy.description(),
            createdAt,
            lastActivity,
            retentionExpiry,
            isEligibleForDeletion,
            isEligibleForDeletion ? "Eligible for deletion per retention policy" : "Must be retained per retention policy"
        );

        return Promise.of(proof);
    }

    /**
     * Generates retention proof for multiple resources.
     *
     * @param resources the resources to generate proof for
     * @return Promise containing the retention proof report
     */
    public static Promise<RetentionProofReport> generateReport(
            Set<ResourceForProof> resources) {

        java.util.List<RetentionProof> proofs = new java.util.ArrayList<>();

        for (ResourceForProof resource : resources) {
            RetentionPolicy policy = RETENTION_POLICIES.get(resource.resourceType());
            if (policy == null) {
                policy = new RetentionPolicy(PhrDataClassification.C2, 10, ChronoUnit.YEARS, "Default 10 years");
            }

            Instant retentionExpiry = calculateRetentionExpiry(resource.createdAt(), resource.lastActivity(), policy);
            boolean isEligibleForDeletion = Instant.now().isAfter(retentionExpiry);

            proofs.add(new RetentionProof(
                resource.resourceType(),
                resource.resourceId(),
                policy.classification(),
                policy.period(),
                policy.unit(),
                policy.description(),
                resource.createdAt(),
                resource.lastActivity(),
                retentionExpiry,
                isEligibleForDeletion,
                isEligibleForDeletion ? "Eligible for deletion per retention policy" : "Must be retained per retention policy"
            ));
        }

        long eligibleCount = proofs.stream().filter(RetentionProof::eligibleForDeletion).count();
        long mustRetainCount = proofs.size() - eligibleCount;

        RetentionProofReport report = new RetentionProofReport(
            Instant.now(),
            proofs,
            eligibleCount,
            mustRetainCount,
            proofs.size()
        );

        return Promise.of(report);
    }

    private static Instant calculateRetentionExpiry(Instant createdAt, Instant lastActivity, RetentionPolicy policy) {
        Instant baseTime = lastActivity != null && lastActivity.isAfter(createdAt) ? lastActivity : createdAt;
        return baseTime.plus(policy.period(), policy.unit());
    }

    /**
     * Retention policy for a resource type.
     */
    public record RetentionPolicy(
            PhrDataClassification classification,
            int period,
            ChronoUnit unit,
            String description) {}

    /**
     * Resource requiring retention proof.
     */
    public record ResourceForProof(
            String resourceType,
            String resourceId,
            Instant createdAt,
            Instant lastActivity) {}

    /**
     * Retention proof for a single resource.
     */
    public record RetentionProof(
            String resourceType,
            String resourceId,
            PhrDataClassification classification,
            int retentionPeriod,
            ChronoUnit retentionUnit,
            String policyDescription,
            Instant createdAt,
            Instant lastActivity,
            Instant retentionExpiry,
            boolean eligibleForDeletion,
            String status) {}

    /**
     * Retention proof report for multiple resources.
     */
    public record RetentionProofReport(
            Instant generatedAt,
            java.util.List<RetentionProof> proofs,
            long eligibleForDeletion,
            long mustRetain,
            long totalResources) {

        public double eligiblePercentage() {
            return totalResources > 0 ? (eligibleForDeletion * 100.0) / totalResources : 0.0;
        }
    }
}
