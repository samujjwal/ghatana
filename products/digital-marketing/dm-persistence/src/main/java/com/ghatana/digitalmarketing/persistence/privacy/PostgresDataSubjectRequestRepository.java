package com.ghatana.digitalmarketing.persistence.privacy;

import com.ghatana.digitalmarketing.application.privacy.DataSubjectRequestRepository;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.privacy.DataSubjectRequest;
import io.activej.promise.Promise;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * PostgreSQL adapter for {@link DataSubjectRequestRepository} (DMOS-P1-017).
 *
 * @doc.type class
 * @doc.purpose PostgreSQL adapter for data subject request storage (DMOS-P1-017)
 * @doc.layer persistence
 * @doc.pattern Repository
 */
public final class PostgresDataSubjectRequestRepository implements DataSubjectRequestRepository {

    private final Connection connection;

    public PostgresDataSubjectRequestRepository(Connection connection) {
        this.connection = connection;
    }

    @Override
    public Promise<DataSubjectRequest> save(DataSubjectRequest request) {
        return Promise.ofBlocking(() -> {
            String sql = """
                INSERT INTO dmos_data_subject_requests (
                    id, tenant_id, workspace_id, request_type, contact_point_hash,
                    status, submitted_at, submitted_by, completed_at, completed_by,
                    rejection_reason, evidence_location
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    status = EXCLUDED.status,
                    completed_at = EXCLUDED.completed_at,
                    completed_by = EXCLUDED.completed_by,
                    rejection_reason = EXCLUDED.rejection_reason,
                    evidence_location = EXCLUDED.evidence_location
                """;

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, request.getId());
                stmt.setString(2, request.getTenantId().value());
                stmt.setString(3, request.getWorkspaceId().value());
                stmt.setString(4, request.getRequestType().name());
                stmt.setString(5, request.getContactPointHash());
                stmt.setString(6, request.getStatus().name());
                stmt.setTimestamp(7, Timestamp.from(request.getSubmittedAt()));
                stmt.setString(8, request.getSubmittedBy());
                stmt.setTimestamp(9, request.getCompletedAt() != null ? Timestamp.from(request.getCompletedAt()) : null);
                stmt.setString(10, request.getCompletedBy());
                stmt.setString(11, request.getRejectionReason());
                stmt.setString(12, request.getEvidenceLocation());

                stmt.executeUpdate();
                return request;
            }
        });
    }

    @Override
    public Promise<DataSubjectRequest> findById(String id) {
        return Promise.ofBlocking(() -> {
            String sql = """
                SELECT id, tenant_id, workspace_id, request_type, contact_point_hash,
                       status, submitted_at, submitted_by, completed_at, completed_by,
                       rejection_reason, evidence_location
                FROM dmos_data_subject_requests
                WHERE id = ?
                """;

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, id);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    return mapRow(rs);
                }
                throw new IllegalArgumentException("Data subject request not found: " + id);
            }
        });
    }

    @Override
    public Promise<List<DataSubjectRequest>> findByTenantAndWorkspace(DmTenantId tenantId, DmWorkspaceId workspaceId) {
        return Promise.ofBlocking(() -> {
            String sql = """
                SELECT id, tenant_id, workspace_id, request_type, contact_point_hash,
                       status, submitted_at, submitted_by, completed_at, completed_by,
                       rejection_reason, evidence_location
                FROM dmos_data_subject_requests
                WHERE tenant_id = ? AND workspace_id = ?
                ORDER BY submitted_at DESC
                """;

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, tenantId.value());
                stmt.setString(2, workspaceId.value());
                ResultSet rs = stmt.executeQuery();

                List<DataSubjectRequest> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
                return results;
            }
        });
    }

    @Override
    public Promise<List<DataSubjectRequest>> findByContactPointHash(String contactPointHash) {
        return Promise.ofBlocking(() -> {
            String sql = """
                SELECT id, tenant_id, workspace_id, request_type, contact_point_hash,
                       status, submitted_at, submitted_by, completed_at, completed_by,
                       rejection_reason, evidence_location
                FROM dmos_data_subject_requests
                WHERE contact_point_hash = ?
                ORDER BY submitted_at DESC
                """;

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, contactPointHash);
                ResultSet rs = stmt.executeQuery();

                List<DataSubjectRequest> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
                return results;
            }
        });
    }

    @Override
    public Promise<List<DataSubjectRequest>> findByStatus(DataSubjectRequest.RequestStatus status) {
        return Promise.ofBlocking(() -> {
            String sql = """
                SELECT id, tenant_id, workspace_id, request_type, contact_point_hash,
                       status, submitted_at, submitted_by, completed_at, completed_by,
                       rejection_reason, evidence_location
                FROM dmos_data_subject_requests
                WHERE status = ?
                ORDER BY submitted_at DESC
                """;

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, status.name());
                ResultSet rs = stmt.executeQuery();

                List<DataSubjectRequest> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
                return results;
            }
        });
    }

    @Override
    public Promise<DataSubjectRequest> update(DataSubjectRequest request) {
        return save(request);
    }

    @Override
    public Promise<Void> delete(String id) {
        return Promise.ofBlocking(() -> {
            String sql = "DELETE FROM dmos_data_subject_requests WHERE id = ?";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, id);
                stmt.executeUpdate();
                return null;
            }
        });
    }

    private DataSubjectRequest mapRow(ResultSet rs) throws SQLException {
        return DataSubjectRequest.builder()
            .id(rs.getString("id"))
            .tenantId(new DmTenantId(rs.getString("tenant_id")))
            .workspaceId(new DmWorkspaceId(rs.getString("workspace_id")))
            .requestType(DataSubjectRequest.RequestType.valueOf(rs.getString("request_type")))
            .contactPointHash(rs.getString("contact_point_hash"))
            .status(DataSubjectRequest.RequestStatus.valueOf(rs.getString("status")))
            .submittedAt(rs.getTimestamp("submitted_at").toInstant())
            .submittedBy(rs.getString("submitted_by"))
            .completedAt(rs.getTimestamp("completed_at") != null ? rs.getTimestamp("completed_at").toInstant() : null)
            .completedBy(rs.getString("completed_by"))
            .rejectionReason(rs.getString("rejection_reason"))
            .evidenceLocation(rs.getString("evidence_location"))
            .build();
    }
}
