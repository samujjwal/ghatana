package com.ghatana.appplatform.audit.approval;

import com.ghatana.appplatform.audit.domain.AuditEntry;
import com.ghatana.appplatform.audit.port.AuditTrailStore;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.logging.Logger;

/**
 * Links maker-checker approval records to the originating audit entry.
 *
 * <p>When an operation requires a second-factor approver, two audit entries exist:
 * one for the maker (initiator) and one for the checker (approver). This service
 * writes the linkage into {@code audit_approval_links}, enabling tamper-evident
 * four-eyes principle verification.
 *
 * @doc.type class
 * @doc.purpose Maker-checker audit linkage service (STORY-K07-013/014)
 * @doc.layer product
 * @doc.pattern Service
 */
public class MakerCheckerAuditLinker {

    private static final Logger LOG = Logger.getLogger(MakerCheckerAuditLinker.class.getName());

    private final DataSource dataSource;
    private final AuditTrailStore auditTrailStore;

    public MakerCheckerAuditLinker(DataSource dataSource, AuditTrailStore auditTrailStore) {
        this.dataSource = dataSource;
        this.auditTrailStore = auditTrailStore;
    }

    /**
     * Record a maker-checker linkage: maker initiated, checker approved.
     *
     * @param tenantId         Tenant scope
     * @param makerAuditId     Audit entry ID for the maker action
     * @param checkerAuditId   Audit entry ID for the checker approval
     * @param approvalOutcome  "APPROVED" or "REJECTED"
     */
    public void link(String tenantId, String makerAuditId, String checkerAuditId, String approvalOutcome) {
        String sql = """
            INSERT INTO audit_approval_links
              (tenant_id, maker_audit_id, checker_audit_id, approval_outcome, linked_at)
            VALUES (?, ?, ?, ?, NOW())
            ON CONFLICT (maker_audit_id, checker_audit_id) DO NOTHING
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tenantId);
            ps.setString(2, makerAuditId);
            ps.setString(3, checkerAuditId);
            ps.setString(4, approvalOutcome);
            ps.executeUpdate();
            LOG.info("[MakerCheckerAuditLinker] Linked maker=" + makerAuditId
                + " checker=" + checkerAuditId + " outcome=" + approvalOutcome);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to link maker-checker audit entries", e);
        }
    }
}
