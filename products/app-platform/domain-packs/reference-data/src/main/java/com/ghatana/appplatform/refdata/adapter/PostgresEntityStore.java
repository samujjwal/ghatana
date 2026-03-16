package com.ghatana.appplatform.refdata.adapter;

import com.ghatana.appplatform.refdata.domain.EntityRelationship;
import com.ghatana.appplatform.refdata.domain.EntityType;
import com.ghatana.appplatform.refdata.domain.MarketEntity;
import com.ghatana.appplatform.refdata.domain.RelationshipType;
import com.ghatana.appplatform.refdata.port.EntityStore;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;
import javax.sql.DataSource;

/**
 * @doc.type       Driven Adapter (Repository)
 * @doc.purpose    PostgreSQL implementation of EntityStore.
 *                 Uses a recursive CTE for graph traversal (findAllDescendantIds),
 *                 enabling D11-005 ancestor/descendant queries without application-
 *                 layer recursion.
 * @doc.layer      Infrastructure
 * @doc.pattern    Hexagonal / Repository
 */
public class PostgresEntityStore implements EntityStore {

    private static final Logger log = LoggerFactory.getLogger(PostgresEntityStore.class);

    private final DataSource dataSource;
    private final Executor executor;

    public PostgresEntityStore(DataSource dataSource, Executor executor) {
        this.dataSource = dataSource;
        this.executor = executor;
    }

    @Override
    public Promise<MarketEntity> saveEntity(MarketEntity entity) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO entity_master " +
                         "(id, entity_type, name, short_name, registration_number, " +
                         " country, status, effective_from, effective_to, created_at_utc, metadata) " +
                         "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), ?::jsonb) " +
                         "ON CONFLICT (id) DO UPDATE SET " +
                         "  name = EXCLUDED.name, short_name = EXCLUDED.short_name, " +
                         "  status = EXCLUDED.status, effective_to = EXCLUDED.effective_to, " +
                         "  metadata = EXCLUDED.metadata")) {
                bindEntity(ps, entity);
                ps.executeUpdate();
            }
            return entity;
        });
    }

    @Override
    public Promise<Optional<MarketEntity>> findEntityById(UUID id) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT * FROM entity_master WHERE id = ? AND effective_to IS NULL")) {
                ps.setObject(1, id);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) return Optional.of(mapEntity(rs));
                return Optional.empty();
            }
        });
    }

    @Override
    public Promise<List<MarketEntity>> listEntities(EntityType typeFilter) {
        return Promise.ofBlocking(executor, () -> {
            List<MarketEntity> results = new ArrayList<>();
            String sql = "SELECT * FROM entity_master WHERE effective_to IS NULL" +
                    (typeFilter != null ? " AND entity_type = ?" : "") +
                    " ORDER BY name";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                if (typeFilter != null) ps.setString(1, typeFilter.name());
                ResultSet rs = ps.executeQuery();
                while (rs.next()) results.add(mapEntity(rs));
            }
            return results;
        });
    }

    @Override
    public Promise<EntityRelationship> saveRelationship(EntityRelationship relationship) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO entity_relationships " +
                         "(id, parent_entity_id, child_entity_id, relationship_type, " +
                         " effective_from, effective_to) " +
                         "VALUES (?, ?, ?, ?, ?, ?) " +
                         "ON CONFLICT (id) DO UPDATE SET " +
                         "  effective_to = EXCLUDED.effective_to")) {
                ps.setObject(1, relationship.id());
                ps.setObject(2, relationship.parentEntityId());
                ps.setObject(3, relationship.childEntityId());
                ps.setString(4, relationship.relationshipType().name());
                ps.setDate(5, Date.valueOf(relationship.effectiveFrom()));
                ps.setDate(6, relationship.effectiveTo() != null
                        ? Date.valueOf(relationship.effectiveTo()) : null);
                ps.executeUpdate();
            }
            return relationship;
        });
    }

    @Override
    public Promise<List<EntityRelationship>> findRelationships(UUID entityId) {
        return Promise.ofBlocking(executor, () -> {
            List<EntityRelationship> results = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT * FROM entity_relationships " +
                         "WHERE (parent_entity_id = ? OR child_entity_id = ?) " +
                         "  AND effective_to IS NULL " +
                         "ORDER BY effective_from")) {
                ps.setObject(1, entityId);
                ps.setObject(2, entityId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) results.add(mapRelationship(rs));
            }
            return results;
        });
    }

    /**
     * Uses a PostgreSQL recursive CTE to traverse the entity hierarchy and return
     * all descendant entity ids for the given root entity.
     * D11-005: findAllDescendants(entityId) acceptance criterion.
     */
    @Override
    public Promise<Set<UUID>> findAllDescendantIds(UUID rootEntityId) {
        return Promise.ofBlocking(executor, () -> {
            Set<UUID> descendants = new LinkedHashSet<>();
            String cte =
                    "WITH RECURSIVE descendants AS ( " +
                    "  SELECT child_entity_id " +
                    "  FROM entity_relationships " +
                    "  WHERE parent_entity_id = ? AND effective_to IS NULL " +
                    "  UNION ALL " +
                    "  SELECT er.child_entity_id " +
                    "  FROM entity_relationships er " +
                    "  JOIN descendants d ON d.child_entity_id = er.parent_entity_id " +
                    "  WHERE er.effective_to IS NULL " +
                    ") " +
                    "SELECT child_entity_id FROM descendants";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(cte)) {
                ps.setObject(1, rootEntityId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    descendants.add(UUID.fromString(rs.getString("child_entity_id")));
                }
            }
            return descendants;
        });
    }

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    private void bindEntity(PreparedStatement ps, MarketEntity e) throws SQLException {
        ps.setObject(1, e.id());
        ps.setString(2, e.entityType().name());
        ps.setString(3, e.name());
        ps.setString(4, e.shortName());
        ps.setString(5, e.registrationNumber());
        ps.setString(6, e.country());
        ps.setString(7, e.status());
        ps.setDate(8, Date.valueOf(e.effectiveFrom()));
        ps.setDate(9, e.effectiveTo() != null ? Date.valueOf(e.effectiveTo()) : null);
        ps.setString(10, e.metadata() != null ? e.metadata() : "{}");
    }

    private MarketEntity mapEntity(ResultSet rs) throws SQLException {
        return new MarketEntity(
                UUID.fromString(rs.getString("id")),
                EntityType.valueOf(rs.getString("entity_type")),
                rs.getString("name"),
                rs.getString("short_name"),
                rs.getString("registration_number"),
                rs.getString("country"),
                rs.getString("status"),
                rs.getDate("effective_from").toLocalDate(),
                rs.getDate("effective_to") != null
                        ? rs.getDate("effective_to").toLocalDate() : null,
                rs.getTimestamp("created_at_utc").toInstant(),
                rs.getString("metadata"));
    }

    private EntityRelationship mapRelationship(ResultSet rs) throws SQLException {
        return new EntityRelationship(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("parent_entity_id")),
                UUID.fromString(rs.getString("child_entity_id")),
                RelationshipType.valueOf(rs.getString("relationship_type")),
                rs.getDate("effective_from").toLocalDate(),
                rs.getDate("effective_to") != null
                        ? rs.getDate("effective_to").toLocalDate() : null);
    }
}
