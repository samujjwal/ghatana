/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.governance.adapter;

import com.ghatana.appplatform.governance.port.StewardshipStore;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/**
 * JDBC implementation of {@link StewardshipStore}.
 *
 * @doc.type class
 * @doc.purpose JDBC adapter for stewardship assignment persistence (PostgreSQL)
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class JdbcStewardshipStore implements StewardshipStore {

    private final DataSource dataSource;

    public JdbcStewardshipStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void upsertDomainAssignment(String assignmentId, String domainId, String stewardId,
                                       int slaDays, Instant slaDeadline) throws Exception {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO data_steward_assignments " +
                 "(assignment_id, scope, domain_id, asset_id, steward_id, sla_days, assigned_at, sla_deadline) " +
                 "VALUES (?, 'DOMAIN', ?, NULL, ?, ?, NOW(), ?) " +
                 "ON CONFLICT (domain_id) WHERE scope = 'DOMAIN' DO UPDATE SET " +
                 "steward_id = EXCLUDED.steward_id, sla_days = EXCLUDED.sla_days, " +
                 "sla_deadline = EXCLUDED.sla_deadline, assigned_at = NOW()")) {
            ps.setString(1, assignmentId);
            ps.setString(2, domainId);
            ps.setString(3, stewardId);
            ps.setInt(4, slaDays);
            ps.setTimestamp(5, Timestamp.from(slaDeadline));
            ps.executeUpdate();
        }
    }

    @Override
    public void upsertAssetAssignment(String assignmentId, String domainId, String assetId,
                                      String stewardId, int slaDays, Instant slaDeadline) throws Exception {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO data_steward_assignments " +
                 "(assignment_id, scope, domain_id, asset_id, steward_id, sla_days, assigned_at, sla_deadline) " +
                 "VALUES (?, 'ASSET', ?, ?, ?, ?, NOW(), ?) " +
                 "ON CONFLICT (asset_id) WHERE scope = 'ASSET' DO UPDATE SET " +
                 "steward_id = EXCLUDED.steward_id, sla_days = EXCLUDED.sla_days, " +
                 "sla_deadline = EXCLUDED.sla_deadline, assigned_at = NOW()")) {
            ps.setString(1, assignmentId);
            ps.setString(2, domainId);
            ps.setString(3, assetId);
            ps.setString(4, stewardId);
            ps.setInt(5, slaDays);
            ps.setTimestamp(6, Timestamp.from(slaDeadline));
            ps.executeUpdate();
        }
    }

    @Override
    public void insertAction(String actionId, String assignmentId, String stewardId,
                             String actionType) throws Exception {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO steward_action_log " +
                 "(action_id, assignment_id, steward_id, action_type, status, created_at) " +
                 "VALUES (?, ?, ?, ?, 'IN_PROGRESS', NOW())")) {
            ps.setString(1, actionId);
            ps.setString(2, assignmentId);
            ps.setString(3, stewardId);
            ps.setString(4, actionType);
            ps.executeUpdate();
        }
    }

    @Override
    public void escalateAssignment(String assignmentId) throws Exception {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE data_steward_assignments SET escalated = TRUE, " +
                 "escalated_at = NOW() WHERE assignment_id = ? AND NOT escalated")) {
            ps.setString(1, assignmentId);
            ps.executeUpdate();
        }
    }

    @Override
    public Optional<String> resolveEffectiveSteward(String assetId, String domainId) throws Exception {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT steward_id FROM data_steward_assignments " +
                 "WHERE (asset_id = ? AND scope = 'ASSET') OR (domain_id = ? AND scope = 'DOMAIN') " +
                 "ORDER BY scope DESC LIMIT 1")) {
            ps.setString(1, assetId);
            ps.setString(2, domainId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(rs.getString("steward_id"));
            }
        }
        return Optional.empty();
    }

    @Override
    public List<Map<String, String>> fetchOverdueAssignments() throws Exception {
        List<Map<String, String>> results = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT assignment_id, steward_id, domain_id FROM data_steward_assignments " +
                 "WHERE sla_deadline < NOW() AND NOT escalated")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, String> row = new HashMap<>();
                    row.put("assignment_id", rs.getString("assignment_id"));
                    row.put("steward_id", rs.getString("steward_id"));
                    row.put("domain_id", rs.getString("domain_id"));
                    results.add(row);
                }
            }
        }
        return results;
    }
}
