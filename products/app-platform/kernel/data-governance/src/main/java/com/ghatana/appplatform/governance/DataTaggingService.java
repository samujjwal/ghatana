package com.ghatana.appplatform.governance;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Tag management for data assets: business tags (domain, subdomain), technical
 *              tags (real-time, batch), compliance tags (GDPR, PII, NRB-regulated).
 *              Hierarchical tag taxonomy. Tag-based access control: RESTRICTED-tagged assets
 *              require elevated permissions enforced at query time. Bulk tagging API.
 *              Satisfies STORY-K08-005.
 * @doc.layer   Kernel
 * @doc.pattern Tag taxonomy hierarchy; bulk-tag operations; access-control integration;
 *              ON CONFLICT DO NOTHING; Counter.
 */
public class DataTaggingService {

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final Counter          tagsAddedCounter;

    public DataTaggingService(HikariDataSource dataSource, Executor executor, MeterRegistry registry) {
        this.dataSource     = dataSource;
        this.executor       = executor;
        this.tagsAddedCounter = Counter.builder("governance.tagging.tags_added_total").register(registry);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record TagDefinition(String tagId, String name, String category,
                                 String parentTagId, String description,
                                 boolean requiresElevatedAccess, LocalDateTime createdAt) {}

    public record AssetTagAssociation(String assetId, List<String> tags,
                                       LocalDateTime updatedAt) {}

    // ─── Taxonomy CRUD ────────────────────────────────────────────────────────

    public Promise<TagDefinition> defineTag(String name, String category, String parentTagId,
                                             String description, boolean requiresElevatedAccess) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                    INSERT INTO tag_definitions
                        (tag_id, name, category, parent_tag_id, description,
                         requires_elevated_access, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, NOW())
                    ON CONFLICT (name, category) DO NOTHING
                    RETURNING *
                    """;
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, UUID.randomUUID().toString()); ps.setString(2, name);
                ps.setString(3, category); ps.setString(4, parentTagId);
                ps.setString(5, description); ps.setBoolean(6, requiresElevatedAccess);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return loadTagByName(name, category);
                    return mapTagRow(rs);
                }
            }
        });
    }

    // ─── Asset tagging ────────────────────────────────────────────────────────

    public Promise<AssetTagAssociation> addTag(String assetId, String tag) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                    UPDATE data_catalog SET tags = array_append(tags, ?)
                    WHERE asset_id=? AND NOT (? = ANY(tags))
                    """;
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tag); ps.setString(2, assetId); ps.setString(3, tag);
                ps.executeUpdate();
            }
            tagsAddedCounter.increment();
            return loadAssetTags(assetId);
        });
    }

    public Promise<AssetTagAssociation> removeTag(String assetId, String tag) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                    UPDATE data_catalog SET tags = array_remove(tags, ?)
                    WHERE asset_id=?
                    """;
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tag); ps.setString(2, assetId);
                ps.executeUpdate();
            }
            return loadAssetTags(assetId);
        });
    }

    /** Bulk-tag up to 50 assets at once. */
    public Promise<List<AssetTagAssociation>> bulkTag(List<String> assetIds, String tag) {
        if (assetIds.size() > 50) throw new IllegalArgumentException("bulk tag limit is 50 assets");
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                    UPDATE data_catalog SET tags = array_append(tags, ?)
                    WHERE asset_id=ANY(?) AND NOT (? = ANY(tags))
                    """;
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tag);
                ps.setArray(2, conn.createArrayOf("text", assetIds.toArray()));
                ps.setString(3, tag);
                ps.executeUpdate();
            }
            tagsAddedCounter.increment(assetIds.size());
            List<AssetTagAssociation> results = new ArrayList<>();
            for (String id : assetIds) results.add(loadAssetTags(id));
            return results;
        });
    }

    public Promise<List<TagDefinition>> searchTags(String query, String category) {
        return Promise.ofBlocking(executor, () -> {
            StringBuilder sb = new StringBuilder("SELECT * FROM tag_definitions WHERE 1=1");
            List<Object> params = new ArrayList<>();
            if (query != null && !query.isBlank()) {
                sb.append(" AND name ILIKE ?"); params.add("%" + query + "%");
            }
            if (category != null) { sb.append(" AND category=?"); params.add(category); }
            sb.append(" ORDER BY category, name LIMIT 100");

            List<TagDefinition> tags = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sb.toString())) {
                for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) tags.add(mapTagRow(rs));
                }
            }
            return tags;
        });
    }

    /** Check if an asset tag requires elevated access (for K-01 gate). */
    public Promise<Boolean> requiresElevatedAccess(String assetId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                    SELECT EXISTS (
                        SELECT 1 FROM data_catalog dc
                        JOIN tag_definitions td ON td.name = ANY(dc.tags)
                        WHERE dc.asset_id=? AND td.requires_elevated_access=TRUE
                    )
                    """;
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, assetId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() && rs.getBoolean(1);
                }
            }
        });
    }

    // ─── Persistence helpers ──────────────────────────────────────────────────

    private AssetTagAssociation loadAssetTags(String assetId) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT tags, updated_at FROM data_catalog WHERE asset_id=?")) {
            ps.setString(1, assetId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Asset not found: " + assetId);
                Array arr = rs.getArray("tags");
                List<String> tags = arr != null ? Arrays.asList((String[]) arr.getArray()) : List.of();
                return new AssetTagAssociation(assetId, tags,
                        rs.getObject("updated_at", LocalDateTime.class));
            }
        }
    }

    private TagDefinition loadTagByName(String name, String category) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM tag_definitions WHERE name=? AND category=?")) {
            ps.setString(1, name); ps.setString(2, category);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("Tag not found: " + name);
                return mapTagRow(rs);
            }
        }
    }

    private TagDefinition mapTagRow(ResultSet rs) throws SQLException {
        return new TagDefinition(rs.getString("tag_id"), rs.getString("name"),
                rs.getString("category"), rs.getString("parent_tag_id"),
                rs.getString("description"), rs.getBoolean("requires_elevated_access"),
                rs.getObject("created_at", LocalDateTime.class));
    }
}
