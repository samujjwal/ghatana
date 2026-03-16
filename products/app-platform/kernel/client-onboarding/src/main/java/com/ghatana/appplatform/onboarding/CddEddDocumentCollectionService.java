package com.ghatana.appplatform.onboarding;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Manage CDD (Customer Due Diligence) and EDD (Enhanced Due Diligence)
 *              document collection for onboarding clients.
 *              Standard CDD: government ID, proof of address, selfie/liveness.
 *              EDD (HIGH risk or PEP): additionally – source of funds proof,
 *              business relationship explanation, beneficial ownership declaration (>25% UBO).
 *              EDD cases are assigned a senior reviewer automatically.
 * @doc.layer   Client Onboarding (W-02)
 * @doc.pattern Port-Adapter; HikariCP + JDBC; Promise.ofBlocking
 *
 * STORY-W02-008: CDD and EDD document collection
 *
 * DDL:
 * <pre>
 * CREATE TABLE IF NOT EXISTS onboarding_doc_requirements (
 *   requirement_id  TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   instance_id     TEXT NOT NULL,
 *   doc_type        TEXT NOT NULL,
 *   doc_category    TEXT NOT NULL,   -- CDD | EDD
 *   status          TEXT NOT NULL DEFAULT 'PENDING',
 *   uploaded_at     TIMESTAMPTZ,
 *   document_ref    TEXT,
 *   review_notes    TEXT,
 *   created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
 * );
 * CREATE TABLE IF NOT EXISTS onboarding_senior_reviewer_assignments (
 *   assignment_id   TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   instance_id     TEXT NOT NULL,
 *   reviewer_id     TEXT NOT NULL,
 *   assigned_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 *   rationale       TEXT NOT NULL
 * );
 * </pre>
 */
public class CddEddDocumentCollectionService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface DocumentStoragePort {
        String store(String instanceId, String docType, byte[] content, String mimeType) throws Exception;
    }

    public interface ReviewerPoolPort {
        /** Find an available senior reviewer for EDD cases. */
        String assignSeniorReviewer(String instanceId) throws Exception;
    }

    public interface EventPublishPort {
        void publish(String eventType, Map<String, Object> payload) throws Exception;
    }

    // ── Value types ───────────────────────────────────────────────────────────

    public enum DocType {
        // CDD
        GOVERNMENT_ID, PROOF_OF_ADDRESS, SELFIE_LIVENESS,
        // EDD
        SOURCE_OF_FUNDS_PROOF, BUSINESS_RELATIONSHIP_EXPLANATION, UBO_DECLARATION
    }

    public enum DocCategory { CDD, EDD }
    public enum DocStatus { PENDING, UPLOADED, ACCEPTED, REJECTED }

    public record DocRequirement(
        String requirementId,
        String instanceId,
        DocType docType,
        DocCategory docCategory,
        DocStatus status,
        String documentRef,
        String uploadedAt
    ) {}

    // ── Standard doc sets ─────────────────────────────────────────────────────

    private static final List<DocType> CDD_DOCS = List.of(
        DocType.GOVERNMENT_ID, DocType.PROOF_OF_ADDRESS, DocType.SELFIE_LIVENESS
    );
    private static final List<DocType> EDD_DOCS = List.of(
        DocType.SOURCE_OF_FUNDS_PROOF,
        DocType.BUSINESS_RELATIONSHIP_EXPLANATION,
        DocType.UBO_DECLARATION
    );

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final DocumentStoragePort documentStorage;
    private final ReviewerPoolPort reviewerPool;
    private final EventPublishPort eventPublish;
    private final Executor executor;
    private final Counter cddInitCounter;
    private final Counter eddInitCounter;
    private final Counter uploadCounter;

    public CddEddDocumentCollectionService(
        javax.sql.DataSource ds,
        DocumentStoragePort documentStorage,
        ReviewerPoolPort reviewerPool,
        EventPublishPort eventPublish,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds              = ds;
        this.documentStorage = documentStorage;
        this.reviewerPool    = reviewerPool;
        this.eventPublish    = eventPublish;
        this.executor        = executor;
        this.cddInitCounter  = Counter.builder("onboarding.cdd.initiated").register(registry);
        this.eddInitCounter  = Counter.builder("onboarding.edd.initiated").register(registry);
        this.uploadCounter   = Counter.builder("onboarding.doc.uploaded").register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Initialise the document requirement checklist for an instance.
     * EDD is triggered when eddRequired=true (HIGH risk tier or PEP).
     */
    public Promise<List<DocRequirement>> initRequirements(String instanceId, boolean eddRequired) {
        return Promise.ofBlocking(executor, () -> {
            List<DocType> docs = new ArrayList<>(CDD_DOCS);
            if (eddRequired) docs.addAll(EDD_DOCS);

            List<DocRequirement> requirements = new ArrayList<>();
            try (Connection c = ds.getConnection()) {
                for (DocType docType : docs) {
                    DocCategory cat = CDD_DOCS.contains(docType) ? DocCategory.CDD : DocCategory.EDD;
                    String id = insertRequirement(c, instanceId, docType, cat);
                    requirements.add(new DocRequirement(id, instanceId, docType, cat, DocStatus.PENDING, null, null));
                }
            }

            if (eddRequired) {
                String reviewerId = reviewerPool.assignSeniorReviewer(instanceId);
                assignSeniorReviewer(instanceId, reviewerId, "EDD case: high-risk or PEP client");
                eddInitCounter.increment();
                eventPublish.publish("EddInitiated", Map.of(
                    "instanceId", instanceId, "reviewerId", reviewerId
                ));
            } else {
                cddInitCounter.increment();
                eventPublish.publish("CddInitiated", Map.of("instanceId", instanceId));
            }
            return requirements;
        });
    }

    /**
     * Upload (and store) a document for a given requirement. Transitions the requirement to UPLOADED.
     */
    public Promise<DocRequirement> uploadDocument(
        String instanceId, String requirementId, DocType docType, byte[] content, String mimeType
    ) {
        return Promise.ofBlocking(executor, () -> {
            String documentRef = documentStorage.store(instanceId, docType.name(), content, mimeType);
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE onboarding_doc_requirements " +
                     "SET status = 'UPLOADED', document_ref = ?, uploaded_at = NOW() " +
                     "WHERE requirement_id = ? AND instance_id = ? " +
                     "RETURNING requirement_id, doc_type, doc_category, status, document_ref, uploaded_at"
                 )) {
                ps.setString(1, documentRef);
                ps.setString(2, requirementId);
                ps.setString(3, instanceId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new IllegalArgumentException("Requirement not found: " + requirementId);
                    uploadCounter.increment();
                    eventPublish.publish("DocumentUploaded", Map.of(
                        "instanceId", instanceId, "docType", docType.name(), "documentRef", documentRef
                    ));
                    return mapRow(rs, instanceId);
                }
            }
        });
    }

    /**
     * List all document requirements for an instance.
     */
    public Promise<List<DocRequirement>> listRequirements(String instanceId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT requirement_id, doc_type, doc_category, status, document_ref, uploaded_at " +
                     "FROM onboarding_doc_requirements WHERE instance_id = ? ORDER BY created_at"
                 )) {
                ps.setString(1, instanceId);
                List<DocRequirement> list = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) list.add(mapRow(rs, instanceId));
                }
                return list;
            }
        });
    }

    /**
     * Check whether all required documents have been uploaded (status UPLOADED or ACCEPTED).
     */
    public Promise<Boolean> allDocumentsUploaded(String instanceId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM onboarding_doc_requirements " +
                     "WHERE instance_id = ? AND status = 'PENDING'"
                 )) {
                ps.setString(1, instanceId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getInt(1) == 0;
                }
            }
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String insertRequirement(Connection c, String instanceId, DocType docType, DocCategory cat)
        throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
            "INSERT INTO onboarding_doc_requirements (instance_id, doc_type, doc_category) " +
            "VALUES (?, ?, ?) RETURNING requirement_id"
        )) {
            ps.setString(1, instanceId);
            ps.setString(2, docType.name());
            ps.setString(3, cat.name());
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getString(1); }
        }
    }

    private void assignSeniorReviewer(String instanceId, String reviewerId, String rationale) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO onboarding_senior_reviewer_assignments (instance_id, reviewer_id, rationale) " +
                 "VALUES (?, ?, ?)"
             )) {
            ps.setString(1, instanceId);
            ps.setString(2, reviewerId);
            ps.setString(3, rationale);
            ps.executeUpdate();
        }
    }

    private DocRequirement mapRow(ResultSet rs, String instanceId) throws SQLException {
        return new DocRequirement(
            rs.getString("requirement_id"),
            instanceId,
            DocType.valueOf(rs.getString("doc_type")),
            DocCategory.valueOf(rs.getString("doc_category")),
            DocStatus.valueOf(rs.getString("status")),
            rs.getString("document_ref"),
            rs.getString("uploaded_at")
        );
    }
}
