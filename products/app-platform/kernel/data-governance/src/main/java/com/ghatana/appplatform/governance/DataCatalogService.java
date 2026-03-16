package com.ghatana.appplatform.governance;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @doc.type    DomainService
 * @doc.purpose Centralized data catalog that inventories all data assets across services.
 *              Auto-discovery: on service registration (K-05 SchemaRegistered event) assets
 *              are upserted into the catalog. Provides search/browse/tag REST API surface.
 *              OpenMetadata-compatible metadata model. Satisfies STORY-K08-001.
 * @doc.layer   Kernel
 * @doc.pattern K-05 event-driven auto-discovery; OpenMetadata-compatible; Counter + Gauge;
 *              ON CONFLICT DO UPDATE idempotency; full-text search via pg_trgm.
 */
public class DataCatalogService {

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final EventPort        eventPort;
    private final Counter          assetsRegisteredCounter;
    private final AtomicLong       catalogSizeGaugeValue = new AtomicLong(0);

    public DataCatalogService(HikariDataSource dataSource, Executor executor,
                               EventPort eventPort, MeterRegistry registry) {
        this.dataSource             = dataSource;
        this.executor               = executor;
        this.eventPort              = eventPort;
        this.assetsRegisteredCounter = Counter.builder("governance.catalog.assets_registered_total").register(registry);
        Gauge.builder("governance.catalog.total_assets", catalogSizeGaugeValue, AtomicLong::get)
                .register(registry);
    }

    // ─── Inner port ──────────────────────────────────────────────────────────

    public interface EventPort {
        void publish(String topic, Object event);
    }

    // ─── Enums & Records ─────────────────────────────────────────────────────

    public enum Classification { PUBLIC, INTERNAL, CONFIDENTIAL, RESTRICTED }

    public record DataAsset(String assetId, String name, String serviceOwner, String schemaRef,
                             Classification classification, String description,
                             List<String> tags, List<String> lineageRefs,
                             LocalDateTime createdAt, LocalDateTime updatedAt) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<DataAsset> register(String name, String serviceOwner, String schemaRef,
                                        Classification classification, String description) {
        return Promise.ofBlocking(executor, () -> {
            String assetId = UUID.randomUUID().toString();
            DataAsset asset = upsertAsset(assetId, name, serviceOwner, schemaRef,
                    classification, description);
            catalogSizeGaugeValue.set(countAssets());
            assetsRegisteredCounter.increment();
            eventPort.publish("governance.catalog.asset_registered", asset);
            return asset;
        });
    }

    public Promise<Optional<DataAsset>> findById(String assetId) {
        return Promise.ofBlocking(executor, () -> queryById(assetId));
    }

    public Promise<List<DataAsset>> search(String query, String tag, Classification classification) {
        return Promise.ofBlocking(executor, () -> querySearch(query, tag, classification));
    }

    public Promise<DataAsset> addTag(String assetId, String tag) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "UPDATE data_catalog SET tags = array_append(tags, ?::text) WHERE asset_id=? RETURNING *";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tag); ps.setString(2, assetId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new IllegalArgumentException("Asset not found: " + assetId);
                    return mapRow(rs);
                }
            }
        });
    }

    public Promise<DataAsset> updateClassification(String assetId, Classification classification) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                    UPDATE data_catalog SET classification=?, updated_at=NOW()
                    WHERE asset_id=? RETURNING *
                    """;
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, classification.name()); ps.setString(2, assetId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new IllegalArgumentException("Asset not found: " + assetId);
                    return mapRow(rs);
                }
            }
        });
    }

    // ─── Persistence ─────────────────────────────────────────────────────────

    private DataAsset upsertAsset(String assetId, String name, String serviceOwner,
                                   String schemaRef, Classification classification,
                                   String description) throws SQLException {
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
            ps.setString(1, assetId); ps.setString(2, name); ps.setString(3, serviceOwner);
            ps.setString(4, schemaRef); ps.setString(5, classification.name());
            ps.setString(6, description);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return mapRow(rs);
            }
        }
    }

    private Optional<DataAsset> queryById(String assetId) throws SQLException {
        String sql = "SELECT * FROM data_catalog WHERE asset_id=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, assetId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        }
    }

    private List<DataAsset> querySearch(String query, String tag,
                                         Classification classification) throws SQLException {
        StringBuilder sb = new StringBuilder("SELECT * FROM data_catalog WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (query != null && !query.isBlank()) {
            sb.append(" AND (name ILIKE ? OR description ILIKE ?)");
            params.add("%" + query + "%"); params.add("%" + query + "%");
        }
        if (tag != null) { sb.append(" AND ? = ANY(tags)"); params.add(tag); }
        if (classification != null) { sb.append(" AND classification=?"); params.add(classification.name()); }
        sb.append(" ORDER BY name LIMIT 100");

        List<DataAsset> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sb.toString())) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(mapRow(rs));
            }
        }
        return results;
    }

    private long countAssets() throws SQLException {
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
        return new DataAsset(rs.getString("asset_id"), rs.getString("name"),
                rs.getString("service_owner"), rs.getString("schema_ref"),
                Classification.valueOf(rs.getString("classification")),
                rs.getString("description"), tags, lineage,
                rs.getObject("created_at", LocalDateTime.class),
                rs.getObject("updated_at", LocalDateTime.class));
    }
}
