package com.ghatana.appplatform.regulator;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Regulatory document vault providing filtered read-access for regulators.
 *              Supports document types: TRADE_CONFIRM, SETTLEMENT_NOTICE, REGULATORY_REPORT,
 *              AUDIT_SUMMARY, COMPLIANCE_CERT.
 *              Filter by: type, tenant, date range. PDF inline preview and download.
 *              Every download is logged with user, document ID, and timestamp.
 *              Retention period is enforced per K-08 policy — expired docs purged.
 * @doc.layer   Regulator Portal (R-01)
 * @doc.pattern Port-Adapter; HikariCP + JDBC; Promise.ofBlocking; Micrometer
 *
 * STORY-R01-004: Regulatory document access vault
 *
 * DDL:
 * <pre>
 * CREATE TABLE IF NOT EXISTS regulatory_documents (
 *   document_id    TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   tenant_id      TEXT NOT NULL,
 *   doc_type       TEXT NOT NULL,
 *   doc_name       TEXT NOT NULL,
 *   storage_path   TEXT NOT NULL,           -- path in blob store (K-16)
 *   file_size_bytes BIGINT,
 *   generated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 *   retain_until   TIMESTAMPTZ NOT NULL,
 *   metadata       JSONB NOT NULL DEFAULT '{}'
 * );
 * CREATE TABLE IF NOT EXISTS regulatory_document_access_log (
 *   access_id      TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   document_id    TEXT NOT NULL,
 *   user_id        TEXT NOT NULL,
 *   access_type    TEXT NOT NULL,           -- PREVIEW | DOWNLOAD
 *   accessed_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
 * );
 * </pre>
 */
public class RegulatoryDocumentVaultService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    /** K-16 blob store integration. */
    public interface BlobStorePort {
        byte[] read(String storagePath) throws Exception;
        boolean exists(String storagePath) throws Exception;
    }

    public interface AuditPort {
        void record(String actorId, String action, String detail) throws Exception;
    }

    // ── Value types ───────────────────────────────────────────────────────────

    public record DocumentDescriptor(
        String documentId, String tenantId, String docType, String docName,
        long fileSizeBytes, Instant generatedAt, Instant retainUntil
    ) {}

    public record DocumentFilter(
        String tenantId, String docType, Instant from, Instant to
    ) {}

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final BlobStorePort blobStore;
    private final AuditPort audit;
    private final Executor executor;
    private final Counter previewCounter;
    private final Counter downloadCounter;
    private final Counter retentionPurgeCounter;

    public RegulatoryDocumentVaultService(
        javax.sql.DataSource ds,
        BlobStorePort blobStore,
        AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds                   = ds;
        this.blobStore            = blobStore;
        this.audit                = audit;
        this.executor             = executor;
        this.previewCounter       = Counter.builder("regulator.vault.previews").register(registry);
        this.downloadCounter      = Counter.builder("regulator.vault.downloads").register(registry);
        this.retentionPurgeCounter = Counter.builder("regulator.vault.purged").register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * List documents matching the filter. Returns metadata only (no byte content).
     */
    public Promise<List<DocumentDescriptor>> list(DocumentFilter filter) {
        return Promise.ofBlocking(executor, () -> {
            StringBuilder sql = new StringBuilder(
                "SELECT document_id, tenant_id, doc_type, doc_name, file_size_bytes, generated_at, retain_until " +
                "FROM regulatory_documents WHERE retain_until > NOW()");
            List<Object> args = new ArrayList<>();
            if (filter.tenantId() != null) { sql.append(" AND tenant_id=?"); args.add(filter.tenantId()); }
            if (filter.docType()  != null) { sql.append(" AND doc_type=?");  args.add(filter.docType()); }
            if (filter.from()     != null) { sql.append(" AND generated_at >= ?"); args.add(Timestamp.from(filter.from())); }
            if (filter.to()       != null) { sql.append(" AND generated_at <= ?"); args.add(Timestamp.from(filter.to())); }
            sql.append(" ORDER BY generated_at DESC LIMIT 500");

            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(sql.toString())) {
                for (int i = 0; i < args.size(); i++) ps.setObject(i + 1, args.get(i));
                List<DocumentDescriptor> result = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(new DocumentDescriptor(
                            rs.getString("document_id"), rs.getString("tenant_id"),
                            rs.getString("doc_type"), rs.getString("doc_name"),
                            rs.getLong("file_size_bytes"),
                            rs.getTimestamp("generated_at").toInstant(),
                            rs.getTimestamp("retain_until").toInstant()));
                    }
                }
                return result;
            }
        });
    }

    /**
     * Preview a document (PDF inline; read-only). Access logged.
     */
    public Promise<byte[]> preview(String documentId, String userId) {
        return Promise.ofBlocking(executor, () -> {
            String path = requireStoragePath(documentId);
            byte[] bytes = blobStore.read(path);
            logAccess(documentId, userId, "PREVIEW");
            previewCounter.increment();
            audit.record(userId, "DOCUMENT_PREVIEWED", "documentId=" + documentId);
            return bytes;
        });
    }

    /**
     * Download a document. Access logged.
     */
    public Promise<byte[]> download(String documentId, String userId) {
        return Promise.ofBlocking(executor, () -> {
            String path = requireStoragePath(documentId);
            byte[] bytes = blobStore.read(path);
            logAccess(documentId, userId, "DOWNLOAD");
            downloadCounter.increment();
            audit.record(userId, "DOCUMENT_DOWNLOADED", "documentId=" + documentId);
            return bytes;
        });
    }

    /**
     * Register a new document in the vault. Called by document generation pipelines.
     */
    public Promise<String> register(DocumentDescriptor desc, String storagePath, String registeredBy) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO regulatory_documents (tenant_id, doc_type, doc_name, storage_path, file_size_bytes, retain_until) " +
                     "VALUES (?,?,?,?,?,?) RETURNING document_id"
                 )) {
                ps.setString(1, desc.tenantId()); ps.setString(2, desc.docType());
                ps.setString(3, desc.docName()); ps.setString(4, storagePath);
                ps.setLong(5, desc.fileSizeBytes()); ps.setTimestamp(6, Timestamp.from(desc.retainUntil()));
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    String docId = rs.getString("document_id");
                    audit.record(registeredBy, "DOCUMENT_REGISTERED", "docId=" + docId + " type=" + desc.docType());
                    return docId;
                }
            }
        });
    }

    /**
     * Purge documents whose retain_until has passed. Called by retention scheduler.
     */
    public Promise<Integer> purgeExpired() {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM regulatory_documents WHERE retain_until < NOW() RETURNING document_id"
                 )) {
                int count = 0;
                try (ResultSet rs = ps.executeQuery()) { while (rs.next()) count++; }
                if (count > 0) retentionPurgeCounter.increment(count);
                return count;
            }
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String requireStoragePath(String documentId) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT storage_path FROM regulatory_documents WHERE document_id=? AND retain_until > NOW()"
             )) {
            ps.setString(1, documentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Document not found or expired: " + documentId);
                return rs.getString("storage_path");
            }
        }
    }

    private void logAccess(String documentId, String userId, String accessType) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO regulatory_document_access_log (document_id, user_id, access_type) VALUES (?,?,?)"
             )) {
            ps.setString(1, documentId); ps.setString(2, userId); ps.setString(3, accessType);
            ps.executeUpdate();
        }
    }
}
