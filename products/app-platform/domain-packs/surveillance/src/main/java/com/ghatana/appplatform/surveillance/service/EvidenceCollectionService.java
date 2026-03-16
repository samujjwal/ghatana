package com.ghatana.appplatform.surveillance.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Collects evidence for surveillance cases: trade records, order history,
 *              account details, and beneficial ownership. Assembles a timestamped evidence
 *              package with SHA-256 content hash for integrity verification. Evidence stored
 *              via StoragePort (S3/MinIO). K-07 AuditPort logs each evidence linkage.
 *              Satisfies STORY-D08-013.
 * @doc.layer   Domain
 * @doc.pattern Evidence collection; SHA-256 integrity; StoragePort; K-07 AuditPort; Counter.
 */
public class EvidenceCollectionService {

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final StoragePort      storagePort;
    private final AuditPort        auditPort;
    private final Counter          packageCollectedCounter;
    private final Counter          evidenceItemCounter;

    public EvidenceCollectionService(HikariDataSource dataSource, Executor executor,
                                      StoragePort storagePort, AuditPort auditPort,
                                      MeterRegistry registry) {
        this.dataSource              = dataSource;
        this.executor                = executor;
        this.storagePort             = storagePort;
        this.auditPort               = auditPort;
        this.packageCollectedCounter = Counter.builder("surveillance.evidence.packages_total").register(registry);
        this.evidenceItemCounter     = Counter.builder("surveillance.evidence.items_total").register(registry);
    }

    // ─── Inner ports ─────────────────────────────────────────────────────────

    public interface StoragePort {
        String store(String bucket, String key, byte[] data, String contentType);
        byte[] retrieve(String bucket, String key);
    }

    public interface AuditPort {
        void logEvidenceLinked(String caseId, String evidenceId, String sourceSystem,
                               String contentHash, LocalDateTime at);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public enum EvidenceType { TRADE_RECORDS, ORDER_HISTORY, ACCOUNT_DETAILS, BENEFICIAL_OWNERSHIP }

    public record EvidenceItem(String evidenceId, String caseId, EvidenceType type,
                               String sourceSystem, String storageKey, String contentHash,
                               LocalDateTime collectedAt) {}

    public record EvidencePackage(String packageId, String caseId, List<EvidenceItem> items,
                                  String packageHash, String storageKey, LocalDateTime assembledAt) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<EvidencePackage> collectEvidenceForCase(String caseId) {
        return Promise.ofBlocking(executor, () -> {
            List<EvidenceItem> items = new ArrayList<>();
            items.addAll(collectTradeRecords(caseId));
            items.addAll(collectOrderHistory(caseId));
            items.addAll(collectAccountDetails(caseId));
            items.addAll(collectBeneficialOwnership(caseId));

            EvidencePackage pkg = assemblePackage(caseId, items);
            for (EvidenceItem item : items) {
                auditPort.logEvidenceLinked(caseId, item.evidenceId(), item.sourceSystem(),
                        item.contentHash(), item.collectedAt());
                evidenceItemCounter.increment();
            }
            packageCollectedCounter.increment();
            return pkg;
        });
    }

    // ─── Evidence collectors ─────────────────────────────────────────────────

    private List<EvidenceItem> collectTradeRecords(String caseId) throws Exception {
        String subjectClient = loadSubjectClient(caseId);
        String sql = """
                SELECT t.trade_id, t.instrument_id, t.trade_date, t.quantity, t.price, t.side, t.venue
                FROM trades t
                WHERE t.client_id = ?
                ORDER BY t.trade_date DESC
                LIMIT 10000
                """;
        return collectFromQuery(caseId, sql, subjectClient, EvidenceType.TRADE_RECORDS, "trades_db");
    }

    private List<EvidenceItem> collectOrderHistory(String caseId) throws Exception {
        String subjectClient = loadSubjectClient(caseId);
        String sql = """
                SELECT o.order_id, o.instrument_id, o.submitted_at, o.quantity, o.price, o.side,
                       o.status, o.cancel_reason
                FROM orders o
                WHERE o.client_id = ?
                ORDER BY o.submitted_at DESC
                LIMIT 10000
                """;
        return collectFromQuery(caseId, sql, subjectClient, EvidenceType.ORDER_HISTORY, "orders_db");
    }

    private List<EvidenceItem> collectAccountDetails(String caseId) throws Exception {
        String subjectClient = loadSubjectClient(caseId);
        String sql = """
                SELECT a.account_id, a.client_id, a.account_type, a.kyc_status, a.onboarded_at,
                       a.risk_rating, a.jurisdiction
                FROM accounts a
                WHERE a.client_id = ?
                """;
        return collectFromQuery(caseId, sql, subjectClient, EvidenceType.ACCOUNT_DETAILS, "client_db");
    }

    private List<EvidenceItem> collectBeneficialOwnership(String caseId) throws Exception {
        String subjectClient = loadSubjectClient(caseId);
        String sql = """
                SELECT bo.owner_id, bo.client_id, bo.owner_name, bo.ownership_pct,
                       bo.nationality, bo.pep_flag, bo.effective_from
                FROM beneficial_owners bo
                WHERE bo.client_id = ?
                """;
        return collectFromQuery(caseId, sql, subjectClient, EvidenceType.BENEFICIAL_OWNERSHIP, "kyc_db");
    }

    // ─── Assembly ────────────────────────────────────────────────────────────

    private List<EvidenceItem> collectFromQuery(String caseId, String sql, String clientId,
                                                 EvidenceType type, String sourceSystem)
            throws Exception {
        StringBuilder csv = new StringBuilder();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, clientId);
            try (ResultSet rs = ps.executeQuery()) {
                var meta = rs.getMetaData();
                int cols = meta.getColumnCount();
                for (int c = 1; c <= cols; c++) {
                    csv.append(meta.getColumnName(c));
                    if (c < cols) csv.append(',');
                }
                csv.append('\n');
                while (rs.next()) {
                    for (int c = 1; c <= cols; c++) {
                        String val = rs.getString(c);
                        csv.append(val == null ? "" : val.replace(",", ";"));
                        if (c < cols) csv.append(',');
                    }
                    csv.append('\n');
                }
            }
        }
        byte[] data = csv.toString().getBytes(StandardCharsets.UTF_8);
        String hash = sha256Hex(data);
        String evidenceId = UUID.randomUUID().toString();
        String storageKey = "evidence/" + caseId + "/" + type.name().toLowerCase() + "/" + evidenceId + ".csv";
        storagePort.store("surveillance", storageKey, data, "text/csv");

        EvidenceItem item = new EvidenceItem(evidenceId, caseId, type, sourceSystem,
                storageKey, hash, LocalDateTime.now());
        persistEvidenceItem(item);
        return List.of(item);
    }

    private EvidencePackage assemblePackage(String caseId, List<EvidenceItem> items) throws Exception {
        String packageId = UUID.randomUUID().toString();
        StringBuilder manifest = new StringBuilder("PACKAGE:" + packageId + "\nCASE:" + caseId + "\n");
        for (EvidenceItem item : items) {
            manifest.append(item.type()).append(':').append(item.contentHash()).append('\n');
        }
        byte[] manifestBytes = manifest.toString().getBytes(StandardCharsets.UTF_8);
        String packageHash   = sha256Hex(manifestBytes);
        String storageKey    = "evidence/" + caseId + "/manifest_" + packageId + ".txt";
        storagePort.store("surveillance", storageKey, manifestBytes, "text/plain");

        EvidencePackage pkg = new EvidencePackage(packageId, caseId, items, packageHash,
                storageKey, LocalDateTime.now());
        persistPackage(pkg);
        return pkg;
    }

    // ─── SHA-256 ─────────────────────────────────────────────────────────────

    private String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    // ─── DB helpers ──────────────────────────────────────────────────────────

    private String loadSubjectClient(String caseId) throws SQLException {
        String sql = "SELECT subject_client FROM surveillance_cases WHERE case_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, caseId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Case not found: " + caseId);
                return rs.getString("subject_client");
            }
        }
    }

    private void persistEvidenceItem(EvidenceItem item) throws SQLException {
        String sql = """
                INSERT INTO evidence_items
                    (evidence_id, case_id, type, source_system, storage_key, content_hash, collected_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (evidence_id) DO NOTHING
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, item.evidenceId());
            ps.setString(2, item.caseId());
            ps.setString(3, item.type().name());
            ps.setString(4, item.sourceSystem());
            ps.setString(5, item.storageKey());
            ps.setString(6, item.contentHash());
            ps.setObject(7, item.collectedAt());
            ps.executeUpdate();
        }
    }

    private void persistPackage(EvidencePackage pkg) throws SQLException {
        String sql = """
                INSERT INTO evidence_packages
                    (package_id, case_id, package_hash, storage_key, assembled_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (package_id) DO NOTHING
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, pkg.packageId());
            ps.setString(2, pkg.caseId());
            ps.setString(3, pkg.packageHash());
            ps.setString(4, pkg.storageKey());
            ps.setObject(5, pkg.assembledAt());
            ps.executeUpdate();
        }
    }
}
