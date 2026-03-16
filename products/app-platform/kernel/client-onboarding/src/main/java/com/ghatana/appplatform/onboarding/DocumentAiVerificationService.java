package com.ghatana.appplatform.onboarding;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose AI-driven document verification for KYC onboarding.
 *              Uses OCR to extract fields (name, DOB, ID number, expiry date),
 *              performs authenticity checks (template matching, security features),
 *              and cross-references extracted data against client-supplied information.
 *              Confidence scoring: HIGH / MEDIUM / LOW.
 *              LOW confidence → routed to manual review queue.
 *              T3 plugin support (K-09 AI Governance port) for jurisdiction-specific ID types.
 *              SLA target: OCR extraction under 5 seconds.
 * @doc.layer   Application
 * @doc.pattern Inner-Port
 */
public class DocumentAiVerificationService {

    // -----------------------------------------------------------------------
    // Inner Ports
    // -----------------------------------------------------------------------

    public interface OcrEnginePort {
        /**
         * Extract structured fields from a document image/PDF.
         * Returns JSON with extracted fields and engine-level confidence per field.
         * Must complete within 5 seconds.
         */
        Promise<OcrResult> extract(String storageObjectKey, String documentType, String mimeType);
    }

    public interface AuthenticityCheckerPort {
        /**
         * Verify document authenticity: template matching against known ID templates,
         * security feature checks (watermarks, holograms, micro-printing cues).
         */
        Promise<AuthenticityResult> check(String storageObjectKey, String documentType, OcrResult ocrResult);
    }

    public interface ClientDataResolverPort {
        /** Retrieve the client data record to cross-reference against. */
        Promise<ClientDataRecord> getClientData(String instanceId);
    }

    /**
     * K-09 AI Governance port – registers/retrieves AI model usage, logs inferences,
     * and enforces jurisdiction-specific rule overrides for T3 plugins.
     */
    public interface AiGovernancePort {
        /** Log an AI inference event for governance/audit. Returns the governance log ID. */
        Promise<String> logInference(String modelId, String inputSummary, String outputSummary,
                                     String confidence, String instanceId);

        /** Retrieve jurisdiction-specific ID validation rules for a T3 plugin. */
        Promise<String> getJurisdictionRules(String countryCode, String documentType);
    }

    public interface ManualReviewQueuePort {
        /** Route a LOW-confidence result to human review queue. Returns queue item ID. */
        Promise<String> enqueue(String instanceId, String documentType, String reason,
                                 String extractedDataJson);
    }

    public interface AuditPort {
        Promise<Void> log(String action, String actor, String entityId, String entityType,
                          String beforeJson, String afterJson);
    }

    // -----------------------------------------------------------------------
    // Records and Enums
    // -----------------------------------------------------------------------

    public enum Confidence { HIGH, MEDIUM, LOW }
    public enum VerificationOutcome { APPROVED, REQUIRES_REVIEW, REJECTED }

    public record OcrResult(
        String documentType,
        String extractedName,
        String extractedDateOfBirth,
        String extractedIdNumber,
        String extractedExpiryDate,
        String extractedCountryCode,
        double overallConfidence,  // 0.0 – 1.0
        String rawFieldsJson
    ) {}

    public record AuthenticityResult(
        boolean templateMatchPassed,
        boolean securityFeaturesPassed,
        double score,              // 0.0 – 1.0
        String notes
    ) {}

    public record ClientDataRecord(
        String instanceId,
        String firstName,
        String lastName,
        String dateOfBirthGregorian,
        String nationality
    ) {}

    public record CrossReferenceResult(
        boolean nameMatched,
        boolean dobMatched,
        boolean nationalityMatched,
        boolean documentNotExpired,
        int matchScore          // 0-100 composite
    ) {}

    public record DocumentVerificationResult(
        String verificationId,
        String instanceId,
        String documentType,
        Confidence confidence,
        VerificationOutcome outcome,
        OcrResult ocrResult,
        AuthenticityResult authenticityResult,
        CrossReferenceResult crossReferenceResult,
        String manualReviewQueueItemId,  // non-null when routed to review
        String aiGovernanceLogId,
        String verifiedAt
    ) {}

    // -----------------------------------------------------------------------
    // Thresholds
    // -----------------------------------------------------------------------

    /** OCR confidence ≥ 0.85 → HIGH; ≥ 0.65 → MEDIUM; below → LOW. */
    private static final double HIGH_CONFIDENCE_THRESHOLD   = 0.85;
    private static final double MEDIUM_CONFIDENCE_THRESHOLD = 0.65;

    /** Composite cross-reference score thresholds. */
    private static final int CROSS_REF_HIGH_THRESHOLD   = 90;
    private static final int CROSS_REF_MEDIUM_THRESHOLD = 70;

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

    private final DataSource dataSource;
    private final Executor executor;
    private final OcrEnginePort ocrEngine;
    private final AuthenticityCheckerPort authenticityChecker;
    private final ClientDataResolverPort clientDataResolver;
    private final AiGovernancePort aiGovernance;
    private final ManualReviewQueuePort manualReviewQueue;
    private final AuditPort auditPort;

    private final Counter verificationsTotalCounter;
    private final Counter approvedCounter;
    private final Counter requiresReviewCounter;
    private final Counter rejectedCounter;
    private final Counter lowConfidenceRoutedCounter;
    private final Timer ocrDurationTimer;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    public DocumentAiVerificationService(DataSource dataSource,
                                          Executor executor,
                                          MeterRegistry meterRegistry,
                                          OcrEnginePort ocrEngine,
                                          AuthenticityCheckerPort authenticityChecker,
                                          ClientDataResolverPort clientDataResolver,
                                          AiGovernancePort aiGovernance,
                                          ManualReviewQueuePort manualReviewQueue,
                                          AuditPort auditPort) {
        this.dataSource          = dataSource;
        this.executor            = executor;
        this.ocrEngine           = ocrEngine;
        this.authenticityChecker = authenticityChecker;
        this.clientDataResolver  = clientDataResolver;
        this.aiGovernance        = aiGovernance;
        this.manualReviewQueue   = manualReviewQueue;
        this.auditPort           = auditPort;

        this.verificationsTotalCounter = Counter.builder("kyc.doc_verification.total")
                .description("Total document verification attempts")
                .register(meterRegistry);
        this.approvedCounter           = Counter.builder("kyc.doc_verification.approved_total")
                .register(meterRegistry);
        this.requiresReviewCounter     = Counter.builder("kyc.doc_verification.requires_review_total")
                .register(meterRegistry);
        this.rejectedCounter           = Counter.builder("kyc.doc_verification.rejected_total")
                .register(meterRegistry);
        this.lowConfidenceRoutedCounter = Counter.builder("kyc.doc_verification.low_confidence_routed_total")
                .description("Documents routed to manual review due to LOW AI confidence")
                .register(meterRegistry);
        this.ocrDurationTimer          = Timer.builder("kyc.doc_verification.ocr_duration_ms")
                .description("OCR extraction latency in milliseconds")
                .register(meterRegistry);
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Verify a document uploaded to object storage.
     * Full pipeline: OCR → authenticity check → cross-reference → decision.
     */
    public Promise<DocumentVerificationResult> verify(String instanceId,
                                                       String documentType,
                                                       String storageObjectKey,
                                                       String mimeType) {
        verificationsTotalCounter.increment();

        // Time OCR using a wrapper
        long ocrStart = System.nanoTime();
        Promise<OcrResult> ocrPromise = ocrEngine.extract(storageObjectKey, documentType, mimeType);

        return ocrPromise
            .then(ocrResult -> {
                ocrDurationTimer.record(System.nanoTime() - ocrStart,
                        java.util.concurrent.TimeUnit.NANOSECONDS);
                return authenticityChecker.check(storageObjectKey, documentType, ocrResult)
                    .combine(clientDataResolver.getClientData(instanceId),
                             (auth, clientData) -> new Object[]{ ocrResult, auth, clientData });
            })
            .then(parts -> {
                OcrResult       ocrResult   = (OcrResult)        parts[0];
                AuthenticityResult auth     = (AuthenticityResult) parts[1];
                ClientDataRecord clientData = (ClientDataRecord)  parts[2];

                CrossReferenceResult crossRef = crossReference(ocrResult, clientData, documentType);
                Confidence confidence         = computeConfidence(ocrResult, auth, crossRef);
                VerificationOutcome outcome   = computeOutcome(auth, confidence);

                String govInputSummary  = summarizeOcr(ocrResult);
                String govOutputSummary = confidence.name() + "/" + outcome.name();

                return aiGovernance.logInference("doc-ai-v1", govInputSummary, govOutputSummary,
                                                  confidence.name(), instanceId)
                    .then(govLogId -> {
                        if (confidence == Confidence.LOW) {
                            lowConfidenceRoutedCounter.increment();
                            return manualReviewQueue.enqueue(instanceId, documentType,
                                                             "AI confidence below threshold",
                                                             ocrResult.rawFieldsJson())
                                .then(queueItemId -> persistAndReturn(instanceId, documentType,
                                      confidence, outcome, ocrResult, auth, crossRef, queueItemId, govLogId));
                        }
                        return persistAndReturn(instanceId, documentType, confidence, outcome,
                                                ocrResult, auth, crossRef, null, govLogId);
                    });
            })
            .then(result -> {
                switch (result.outcome()) {
                    case APPROVED        -> approvedCounter.increment();
                    case REQUIRES_REVIEW -> requiresReviewCounter.increment();
                    case REJECTED        -> rejectedCounter.increment();
                }
                auditPort.log("DOC_AI_VERIFIED", "system", instanceId, "KYC_DOCUMENT",
                              null, buildOutcomeJson(result));
                return Promise.of(result);
            });
    }

    /**
     * Complete manual review for a document. Called by KYC coordinator after reviewing a queued item.
     */
    public Promise<Void> completeManualReview(String verificationId, VerificationOutcome outcome,
                                               String reviewerNotes, String reviewedBy) {
        return Promise.ofBlocking(executor, () -> {
            updateVerificationOutcome(verificationId, outcome, reviewerNotes);
            auditPort.log("DOC_MANUAL_REVIEW_COMPLETED", reviewedBy, verificationId,
                          "DOC_VERIFICATION", null,
                          "{\"outcome\":\"" + outcome + "\",\"notes\":\"" + reviewerNotes + "\"}");
            return null;
        });
    }

    /** Retrieve a verification result by ID. */
    public Promise<DocumentVerificationResult> getVerification(String verificationId) {
        return Promise.ofBlocking(executor, () -> queryVerification(verificationId));
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private CrossReferenceResult crossReference(OcrResult ocr, ClientDataRecord client,
                                                 String documentType) {
        boolean nameMatched = approximateNameMatch(
                (client.firstName() + " " + client.lastName()).toLowerCase(),
                ocr.extractedName() != null ? ocr.extractedName().toLowerCase() : ""
        );
        boolean dobMatched = client.dateOfBirthGregorian() != null
                && client.dateOfBirthGregorian().equals(ocr.extractedDateOfBirth());
        boolean nationalityMatched = client.nationality() != null
                && client.nationality().equalsIgnoreCase(ocr.extractedCountryCode());
        boolean notExpired = ocr.extractedExpiryDate() == null
                || ocr.extractedExpiryDate().compareTo(java.time.LocalDate.now().toString()) > 0;

        int score = 0;
        if (nameMatched)        score += 40;
        if (dobMatched)         score += 30;
        if (nationalityMatched) score += 15;
        if (notExpired)         score += 15;

        return new CrossReferenceResult(nameMatched, dobMatched, nationalityMatched, notExpired, score);
    }

    /** Levenshtein-distance-based approximate name match (handles minor OCR typos). */
    private boolean approximateNameMatch(String expected, String actual) {
        if (expected.isBlank() || actual.isBlank()) return false;
        int dist = levenshtein(expected, actual);
        return dist <= Math.max(2, expected.length() / 10);
    }

    private int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            int[] curr = new int[b.length() + 1];
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(curr[j - 1] + 1, Math.min(prev[j] + 1, prev[j - 1] + cost));
            }
            prev = curr;
        }
        return prev[b.length()];
    }

    private Confidence computeConfidence(OcrResult ocr, AuthenticityResult auth, CrossReferenceResult crossRef) {
        double combined = (ocr.overallConfidence() * 0.4)
                        + (auth.score() * 0.3)
                        + (crossRef.matchScore() / 100.0 * 0.3);
        if (combined >= HIGH_CONFIDENCE_THRESHOLD)   return Confidence.HIGH;
        if (combined >= MEDIUM_CONFIDENCE_THRESHOLD) return Confidence.MEDIUM;
        return Confidence.LOW;
    }

    private VerificationOutcome computeOutcome(AuthenticityResult auth, Confidence confidence) {
        if (!auth.templateMatchPassed() || !auth.securityFeaturesPassed()) return VerificationOutcome.REJECTED;
        return switch (confidence) {
            case HIGH   -> VerificationOutcome.APPROVED;
            case MEDIUM -> VerificationOutcome.REQUIRES_REVIEW;
            case LOW    -> VerificationOutcome.REQUIRES_REVIEW;
        };
    }

    private Promise<DocumentVerificationResult> persistAndReturn(String instanceId, String documentType,
            Confidence confidence, VerificationOutcome outcome, OcrResult ocr,
            AuthenticityResult auth, CrossReferenceResult crossRef,
            String queueItemId, String govLogId) {
        return Promise.ofBlocking(executor, () -> {
            String verificationId = insertVerification(instanceId, documentType, confidence, outcome,
                                                        ocr, auth, crossRef, queueItemId, govLogId);
            return new DocumentVerificationResult(verificationId, instanceId, documentType,
                    confidence, outcome, ocr, auth, crossRef, queueItemId, govLogId,
                    java.time.Instant.now().toString());
        });
    }

    private String insertVerification(String instanceId, String documentType,
            Confidence confidence, VerificationOutcome outcome, OcrResult ocr,
            AuthenticityResult auth, CrossReferenceResult crossRef,
            String queueItemId, String govLogId) {
        String sql = """
            INSERT INTO kyc_document_verifications
                (verification_id, instance_id, document_type, confidence, outcome,
                 ocr_extracted_fields, authenticity_score, cross_ref_score,
                 manual_review_queue_item_id, ai_governance_log_id, verified_at)
            VALUES (gen_random_uuid()::text, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, now())
            RETURNING verification_id
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, instanceId);
            ps.setString(2, documentType);
            ps.setString(3, confidence.name());
            ps.setString(4, outcome.name());
            ps.setString(5, ocr.rawFieldsJson());
            ps.setDouble(6, auth.score());
            ps.setInt(7, crossRef.matchScore());
            ps.setString(8, queueItemId);
            ps.setString(9, govLogId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString("verification_id");
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to persist verification for " + instanceId, e);
        }
    }

    private void updateVerificationOutcome(String verificationId, VerificationOutcome outcome,
                                            String reviewerNotes) {
        String sql = """
            UPDATE kyc_document_verifications
               SET outcome = ?, reviewer_notes = ?, reviewed_at = now()
             WHERE verification_id = ?
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, outcome.name());
            ps.setString(2, reviewerNotes);
            ps.setString(3, verificationId);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to update verification " + verificationId, e);
        }
    }

    private DocumentVerificationResult queryVerification(String verificationId) {
        String sql = """
            SELECT verification_id, instance_id, document_type, confidence, outcome,
                   ocr_extracted_fields::text, authenticity_score, cross_ref_score,
                   manual_review_queue_item_id, ai_governance_log_id, verified_at::text
              FROM kyc_document_verifications
             WHERE verification_id = ?
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, verificationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new RuntimeException("Verification not found: " + verificationId);

                // Reconstruct partial result objects from persisted summary columns.
                // Only score/matchScore and rawFieldsJson are stored; other sub-fields
                // are left at their zero-defaults which is a well-documented limitation
                // of the lookup API (full context is only available on the in-memory verify path).
                String rawFieldsJson = rs.getString("ocr_extracted_fields");
                double authenticityScore = rs.getDouble("authenticity_score");
                int crossRefScore = rs.getInt("cross_ref_score");

                OcrResult ocrResult = new OcrResult(
                        rs.getString("document_type"),
                        null, null, null, null, null,   // extracted fields not individually stored
                        authenticityScore,              // use authenticity score as surrogate confidence
                        rawFieldsJson);

                AuthenticityResult authenticityResult = new AuthenticityResult(
                        false, false,                  // template/security flags not stored
                        authenticityScore,
                        null);

                CrossReferenceResult crossReferenceResult = new CrossReferenceResult(
                        false, false, false, false,    // individual match flags not stored
                        crossRefScore);

                return new DocumentVerificationResult(
                    rs.getString("verification_id"), rs.getString("instance_id"),
                    rs.getString("document_type"),
                    Confidence.valueOf(rs.getString("confidence")),
                    VerificationOutcome.valueOf(rs.getString("outcome")),
                    ocrResult,
                    authenticityResult,
                    crossReferenceResult,
                    rs.getString("manual_review_queue_item_id"),
                    rs.getString("ai_governance_log_id"),
                    rs.getString("verified_at")
                );
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to query verification " + verificationId, e);
        }
    }

    private String summarizeOcr(OcrResult ocr) {
        return "{\"docType\":\"" + ocr.documentType() + "\",\"confidence\":" + ocr.overallConfidence() + "}";
    }

    private String buildOutcomeJson(DocumentVerificationResult r) {
        return "{\"verificationId\":\"" + r.verificationId() + "\",\"confidence\":\"" +
               r.confidence() + "\",\"outcome\":\"" + r.outcome() + "\"}";
    }
}
