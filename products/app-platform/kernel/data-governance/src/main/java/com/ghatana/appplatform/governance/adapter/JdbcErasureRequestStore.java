/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.governance.adapter;

import com.ghatana.appplatform.governance.RightToErasureHandlerService.ErasureStatus;
import com.ghatana.appplatform.governance.RightToErasureHandlerService.ProofCertificate;
import com.ghatana.appplatform.governance.port.ErasureRequestStore;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.Optional;

/**
 * JDBC implementation of {@link ErasureRequestStore}.
 *
 * @doc.type class
 * @doc.purpose JDBC adapter for GDPR erasure request persistence (PostgreSQL)
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class JdbcErasureRequestStore implements ErasureRequestStore {

    private final DataSource dataSource;

    public JdbcErasureRequestStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void persistRequest(String requestId, String clientId, ErasureStatus status,
                               String holdReason, Instant initiatedAt) throws Exception {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO erasure_requests " +
                 "(request_id, client_id, status, hold_reason, initiated_at) " +
                 "VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, requestId);
            ps.setString(2, clientId);
            ps.setString(3, status.name());
            ps.setString(4, holdReason);
            ps.setTimestamp(5, Timestamp.from(initiatedAt));
            ps.executeUpdate();
        }
    }

    @Override
    public void markInProgress(String requestId) throws Exception {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE erasure_requests SET status = 'IN_PROGRESS' WHERE request_id = ?")) {
            ps.setString(1, requestId);
            ps.executeUpdate();
        }
    }

    @Override
    public void persistCompletion(String requestId, ErasureStatus status,
                                  ProofCertificate cert, Instant completedAt) throws Exception {
        String certJson = String.format(
            "{\"requestId\":\"%s\",\"clientId\":\"%s\",\"datasetsErased\":%d," +
            "\"completedAt\":\"%s\",\"attestation\":\"%s\"}",
            cert.requestId(), cert.clientId(), cert.datasetsErased().size(),
            cert.completedAt(), cert.attestationHash());

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE erasure_requests SET status = ?, proof_certificate = ?::jsonb, " +
                 "completed_at = ? WHERE request_id = ?")) {
            ps.setString(1, status.name());
            ps.setString(2, certJson);
            ps.setTimestamp(3, Timestamp.from(completedAt));
            ps.setString(4, requestId);
            ps.executeUpdate();
        }
    }

    @Override
    public Optional<ErasureRequestRow> findRequest(String requestId) throws Exception {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT client_id, status, hold_reason, initiated_at, completed_at " +
                 "FROM erasure_requests WHERE request_id = ?")) {
            ps.setString(1, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                ErasureStatus status = ErasureStatus.valueOf(rs.getString("status"));
                String holdReason = rs.getString("hold_reason");
                Timestamp completedTs = rs.getTimestamp("completed_at");
                Instant completedAt = completedTs != null ? completedTs.toInstant() : null;
                return Optional.of(new ErasureRequestRow(
                    rs.getString("client_id"), status, holdReason,
                    rs.getTimestamp("initiated_at").toInstant(), completedAt
                ));
            }
        }
    }
}
