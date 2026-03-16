/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.governance.adapter;

import com.ghatana.appplatform.governance.DataRetentionPolicyService.RetentionAction;
import com.ghatana.appplatform.governance.DataRetentionPolicyService.RetentionPolicy;
import com.ghatana.appplatform.governance.port.RetentionPolicyStore;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC implementation of {@link RetentionPolicyStore}.
 *
 * @doc.type class
 * @doc.purpose JDBC adapter for retention policy persistence (PostgreSQL)
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class JdbcRetentionPolicyStore implements RetentionPolicyStore {

    private final DataSource dataSource;

    public JdbcRetentionPolicyStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public RetentionPolicy insertPolicy(String policyId, String assetPattern, int retentionDays,
                                        RetentionAction action, String regulatoryBasis) throws Exception {
        String sql = """
                INSERT INTO retention_policies
                    (policy_id, asset_pattern, retention_days, action, regulatory_basis,
                     active, created_at)
                VALUES (?, ?, ?, ?, ?, TRUE, NOW())
                ON CONFLICT (asset_pattern, action) DO UPDATE
                    SET retention_days=EXCLUDED.retention_days,
                        regulatory_basis=EXCLUDED.regulatory_basis
                RETURNING *
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, policyId);
            ps.setString(2, assetPattern);
            ps.setInt(3, retentionDays);
            ps.setString(4, action.name());
            ps.setString(5, regulatoryBasis);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return mapRow(rs);
            }
        }
    }

    @Override
    public RetentionPolicy loadPolicy(String policyId) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM retention_policies WHERE policy_id=?")) {
            ps.setString(1, policyId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Policy not found: " + policyId);
                return mapRow(rs);
            }
        }
    }

    @Override
    public List<RetentionPolicy> fetchAllPolicies() throws Exception {
        List<RetentionPolicy> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM retention_policies ORDER BY created_at DESC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        }
        return list;
    }

    @Override
    public long countActivePolicies() throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM retention_policies WHERE active=TRUE");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    @Override
    public RetentionPolicy deactivatePolicy(String policyId) throws Exception {
        String sql = "UPDATE retention_policies SET active=FALSE WHERE policy_id=? RETURNING *";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, policyId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Policy not found: " + policyId);
                return mapRow(rs);
            }
        }
    }

    @Override
    public List<AssetPolicyMatch> matchAssets() throws Exception {
        String sql = """
                SELECT dc.asset_id, dc.name, rp.policy_id
                FROM data_catalog dc
                CROSS JOIN retention_policies rp
                WHERE rp.active=TRUE
                  AND dc.name ILIKE REPLACE(REPLACE(rp.asset_pattern, '*', '%'), '?', '_')
                """;
        List<AssetPolicyMatch> matches = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                matches.add(new AssetPolicyMatch(
                        rs.getString("asset_id"), rs.getString("name"), rs.getString("policy_id")));
            }
        }
        return matches;
    }

    @Override
    public void applyPolicy(String policyId, String assetId) throws Exception {
        String sql = """
                INSERT INTO retention_policy_applications
                    (application_id, policy_id, asset_id, applied_at)
                VALUES (gen_random_uuid(), ?, ?, NOW())
                ON CONFLICT (policy_id, asset_id) DO UPDATE SET applied_at=NOW()
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, policyId);
            ps.setString(2, assetId);
            ps.executeUpdate();
        }
    }

    private RetentionPolicy mapRow(ResultSet rs) throws SQLException {
        return new RetentionPolicy(rs.getString("policy_id"), rs.getString("asset_pattern"),
                rs.getInt("retention_days"), RetentionAction.valueOf(rs.getString("action")),
                rs.getString("regulatory_basis"), rs.getBoolean("active"),
                rs.getObject("created_at", LocalDateTime.class));
    }
}
