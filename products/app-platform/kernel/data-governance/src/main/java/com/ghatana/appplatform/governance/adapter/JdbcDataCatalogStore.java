/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.governance.adapter;

import com.ghatana.appplatform.governance.DataCatalogService.Classification;
import com.ghatana.appplatform.governance.DataCatalogService.DataAsset;
import com.ghatana.appplatform.governance.port.DataCatalogStore;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC implementation of {@link DataCatalogStore}.
 *
 * @doc.type class
 * @doc.purpose JDBC adapter for data catalog persistence (PostgreSQL)
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class JdbcDataCatalogStore implements DataCatalogStore {

    private final DataSource dataSource;

    public JdbcDataCatalogStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public DataAsset upsert(String assetId, String name, String serviceOwner,
                            String schemaRef, Classification classification,
                            String description) throws Exception {
        String sql = """
                INSERT INTO data_catalog
                    (asset_id, name, service_owner, schema_ref, classification, description,
                     tags, lineage_refs, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, '{}', '{}', NOW(), NOW())
                ON CONFLICT (name, service_owner) DO UPDATE
                    SET schema_ref=EXCLUDED.schema_ref,
                        classification=EXCLUDED.classification,
                        description=EXCLUDED.description,
                        updated_at=NOW()
                RETURNING *
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, assetId);
            ps.setString(2, name);
            ps.setString(3, serviceOwner);
            ps.setString(4, schemaRef);
            ps.setString(5, classification.name());
            ps.setString(6, description);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return mapRow(rs);
            }
        }
    }

    @Override
    public Optional<DataAsset> findById(String assetId) throws Exception {
        String sql = "SELECT * FROM data_catalog WHERE asset_id=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, assetId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        }
    }

    @Override
    public List<DataAsset> search(String query, String tag, Classification classification) throws Exception {
        StringBuilder sb = new StringBuilder("SELECT * FROM data_catalog WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (query != null && !query.isBlank()) {
            sb.append(" AND (name ILIKE ? OR description ILIKE ?)");
            params.add("%" + query + "%");
            params.add("%" + query + "%");
        }
        if (tag != null) {
            sb.append(" AND ? = ANY(tags)");
            params.add(tag);
        }
        if (classification != null) {
            sb.append(" AND classification=?");
            params.add(classification.name());
        }
        sb.append(" ORDER BY name LIMIT 100");

        List<DataAsset> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sb.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(mapRow(rs));
            }
        }
        return results;
    }

    @Override
    public DataAsset addTag(String assetId, String tag) throws Exception {
        String sql = "UPDATE data_catalog SET tags = array_append(tags, ?::text) WHERE asset_id=? RETURNING *";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tag);
            ps.setString(2, assetId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Asset not found: " + assetId);
                return mapRow(rs);
            }
        }
    }

    @Override
    public DataAsset updateClassification(String assetId, Classification classification) throws Exception {
        String sql = """
                UPDATE data_catalog SET classification=?, updated_at=NOW()
                WHERE asset_id=? RETURNING *
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, classification.name());
            ps.setString(2, assetId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Asset not found: " + assetId);
                return mapRow(rs);
            }
        }
    }

    @Override
    public long countAssets() throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM data_catalog");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    @SuppressWarnings("unchecked")
    private DataAsset mapRow(ResultSet rs) throws SQLException {
        Array tagsArr = rs.getArray("tags");
        Array lineageArr = rs.getArray("lineage_refs");
        List<String> tags = tagsArr != null ? List.of((String[]) tagsArr.getArray()) : List.of();
        List<String> lineage = lineageArr != null ? List.of((String[]) lineageArr.getArray()) : List.of();
        return new DataAsset(
                rs.getString("asset_id"), rs.getString("name"),
                rs.getString("service_owner"), rs.getString("schema_ref"),
                Classification.valueOf(rs.getString("classification")),
                rs.getString("description"), tags, lineage,
                rs.getObject("created_at", LocalDateTime.class),
                rs.getObject("updated_at", LocalDateTime.class));
    }
}
