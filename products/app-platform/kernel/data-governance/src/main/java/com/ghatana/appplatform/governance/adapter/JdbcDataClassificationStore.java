/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.governance.adapter;

import com.ghatana.appplatform.governance.DataCatalogService.Classification;
import com.ghatana.appplatform.governance.port.DataClassificationStore;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC implementation of {@link DataClassificationStore}.
 *
 * @doc.type class
 * @doc.purpose JDBC adapter for classification persistence operations (PostgreSQL)
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class JdbcDataClassificationStore implements DataClassificationStore {

    private final DataSource dataSource;

    public JdbcDataClassificationStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public AssetRow loadAsset(String assetId) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT asset_id, name, schema_ref, classification FROM data_catalog WHERE asset_id=?")) {
            ps.setString(1, assetId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Asset not found: " + assetId);
                return new AssetRow(rs.getString("asset_id"), rs.getString("name"),
                        rs.getString("schema_ref"),
                        parseClassification(rs.getString("classification")));
            }
        }
    }

    @Override
    public List<String> loadAllAssetIds() throws Exception {
        List<String> ids = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT asset_id FROM data_catalog");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) ids.add(rs.getString("asset_id"));
        }
        return ids;
    }

    @Override
    public void updateClassification(String assetId, Classification classification) throws Exception {
        String sql = "UPDATE data_catalog SET classification=?, updated_at=NOW() WHERE asset_id=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, classification.name());
            ps.setString(2, assetId);
            ps.executeUpdate();
        }
    }

    @Override
    public void persistOverrideRequest(String requestId, String assetId, String taskId,
                                       String classification, String requestedBy,
                                       String reason) throws Exception {
        String sql = """
                INSERT INTO classification_override_requests
                    (request_id, asset_id, task_id, requested_classification, requested_by,
                     reason, status, submitted_at)
                VALUES (?, ?, ?, ?, ?, ?, 'PENDING', NOW())
                ON CONFLICT (request_id) DO NOTHING
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, requestId);
            ps.setString(2, assetId);
            ps.setString(3, taskId);
            ps.setString(4, classification);
            ps.setString(5, requestedBy);
            ps.setString(6, reason);
            ps.executeUpdate();
        }
    }

    @Override
    public void markOverrideApplied(String requestId) throws Exception {
        String sql = "UPDATE classification_override_requests SET status='APPLIED' WHERE request_id=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, requestId);
            ps.executeUpdate();
        }
    }

    @Override
    public OverrideRow loadOverrideRequest(String requestId) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT asset_id, requested_classification " +
                     "FROM classification_override_requests WHERE request_id=?")) {
            ps.setString(1, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Override not found: " + requestId);
                return new OverrideRow(rs.getString("asset_id"),
                        rs.getString("requested_classification"));
            }
        }
    }

    private Classification parseClassification(String raw) {
        return switch (raw.toUpperCase()) {
            case "RESTRICTED"   -> Classification.RESTRICTED;
            case "CONFIDENTIAL" -> Classification.CONFIDENTIAL;
            case "INTERNAL"     -> Classification.INTERNAL;
            default             -> Classification.PUBLIC;
        };
    }
}
