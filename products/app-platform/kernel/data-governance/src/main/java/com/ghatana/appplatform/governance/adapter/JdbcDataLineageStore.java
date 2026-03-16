/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.governance.adapter;

import com.ghatana.appplatform.governance.DataLineageService.LineageEdge;
import com.ghatana.appplatform.governance.DataLineageService.LineageNode;
import com.ghatana.appplatform.governance.port.DataLineageStore;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JDBC implementation of {@link DataLineageStore}.
 *
 * @doc.type class
 * @doc.purpose JDBC adapter for lineage DAG persistence (PostgreSQL)
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class JdbcDataLineageStore implements DataLineageStore {

    private final DataSource dataSource;

    public JdbcDataLineageStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public LineageEdge insertEdge(String edgeId, String sourceAssetId, String targetAssetId,
                                  String transformationDesc) throws Exception {
        String sql = """
                INSERT INTO lineage_edges
                    (edge_id, source_asset_id, target_asset_id, transformation_desc, captured_at)
                VALUES (?, ?, ?, ?, NOW())
                ON CONFLICT (source_asset_id, target_asset_id) DO UPDATE
                    SET transformation_desc=EXCLUDED.transformation_desc,
                        captured_at=NOW()
                RETURNING *
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, edgeId);
            ps.setString(2, sourceAssetId);
            ps.setString(3, targetAssetId);
            ps.setString(4, transformationDesc);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return new LineageEdge(rs.getString("edge_id"), rs.getString("source_asset_id"),
                        rs.getString("target_asset_id"), rs.getString("transformation_desc"),
                        rs.getObject("captured_at", LocalDateTime.class));
            }
        }
    }

    @Override
    public List<LineageNode> fetchDirectDownstream(String sourceAssetId) throws Exception {
        String sql = """
                SELECT DISTINCT c.asset_id AS target_id, c.name, c.service_owner
                FROM lineage_edges e
                JOIN data_catalog c ON c.asset_id = e.target_asset_id
                WHERE e.source_asset_id = ?
                """;
        List<LineageNode> nodes = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sourceAssetId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    nodes.add(new LineageNode(rs.getString("target_id"),
                            rs.getString("name"), rs.getString("service_owner")));
                }
            }
        }
        return nodes;
    }

    @Override
    public List<LineageNode> fetchConnectedNodes(String assetId) throws Exception {
        String sql = """
                SELECT DISTINCT c.asset_id, c.name, c.service_owner FROM data_catalog c
                WHERE c.asset_id IN (
                    SELECT source_asset_id FROM lineage_edges WHERE target_asset_id=?
                    UNION SELECT target_asset_id FROM lineage_edges WHERE source_asset_id=?
                    UNION SELECT ?
                )
                """;
        List<LineageNode> nodes = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, assetId);
            ps.setString(2, assetId);
            ps.setString(3, assetId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    nodes.add(new LineageNode(rs.getString("asset_id"),
                            rs.getString("name"), rs.getString("service_owner")));
                }
            }
        }
        return nodes;
    }

    @Override
    public List<LineageEdge> fetchConnectedEdges(String assetId) throws Exception {
        String sql = """
                SELECT * FROM lineage_edges
                WHERE source_asset_id=? OR target_asset_id=?
                """;
        List<LineageEdge> edges = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, assetId);
            ps.setString(2, assetId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    edges.add(new LineageEdge(rs.getString("edge_id"),
                            rs.getString("source_asset_id"), rs.getString("target_asset_id"),
                            rs.getString("transformation_desc"),
                            rs.getObject("captured_at", LocalDateTime.class)));
                }
            }
        }
        return edges;
    }
}
