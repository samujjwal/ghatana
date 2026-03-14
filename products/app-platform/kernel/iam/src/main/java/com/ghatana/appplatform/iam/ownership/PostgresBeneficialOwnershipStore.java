/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.iam.ownership;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * PostgreSQL adapter for {@link BeneficialOwnershipStore}.
 *
 * <p>Uses a {@code WITH RECURSIVE} CTE to traverse the ownership graph via the
 * {@code iam_ownership_links} table.
 *
 * <p>Schema (see {@code V004__iam_ownership.sql}):
 * <pre>
 *   iam_ownership_entities: entity_id, entity_name, entity_type, tenant_id, created_at
 *   iam_ownership_links: parent_id, child_id, relationship_type, percentage,
 *                        tenant_id, valid_from, valid_to
 *   iam_beneficial_owners (view/cache): optional materialised
 * </pre>
 *
 * @doc.type class
 * @doc.purpose PostgreSQL adapter for beneficial-ownership graph storage (K01-019)
 * @doc.layer product
 * @doc.pattern Repository
 */
public final class PostgresBeneficialOwnershipStore implements BeneficialOwnershipStore {

    private static final Logger log = LoggerFactory.getLogger(PostgresBeneficialOwnershipStore.class);

    // ─── SQL ──────────────────────────────────────────────────────────────────

    private static final String SQL_INSERT_ENTITY = """
            INSERT INTO iam_ownership_entities
                (entity_id, entity_name, entity_type, tenant_id, created_at)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (entity_id, tenant_id) DO UPDATE
                SET entity_name = EXCLUDED.entity_name,
                    entity_type = EXCLUDED.entity_type
            """;

    private static final String SQL_FIND_ENTITY = """
            SELECT entity_id, entity_name, entity_type, tenant_id, created_at
            FROM iam_ownership_entities
            WHERE entity_id = ? AND tenant_id = ?
            """;

    private static final String SQL_INSERT_LINK = """
            INSERT INTO iam_ownership_links
                (parent_id, child_id, relationship_type, percentage, tenant_id, valid_from, valid_to)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SQL_DIRECT_LINKS = """
            SELECT parent_id, child_id, relationship_type, percentage, tenant_id, valid_from, valid_to
            FROM iam_ownership_links
            WHERE child_id = ? AND tenant_id = ? AND (valid_to IS NULL OR valid_to > NOW())
            """;

    /**
     * Recursive CTE: traverses ownership graph upward from a child entity, compounding
     * percentages at each hop (cumulative = parent_pct * current_pct / 100).
     *
     * <p>Parameters: child_id, tenant_id, tenant_id (in recursive join), maxDepth, thresholdPercent.
     */
    private static final String SQL_BENEFICIAL_OWNERS = """
            WITH RECURSIVE ownership_tree AS (
                -- Anchor: direct parents of the target entity
                SELECT
                    parent_id                         AS owner_id,
                    child_id                          AS entity_id,
                    tenant_id,
                    percentage                        AS cumulative_pct,
                    1                                 AS depth
                FROM iam_ownership_links
                WHERE child_id = ? AND tenant_id = ?
                  AND (valid_to IS NULL OR valid_to > NOW())

                UNION ALL

                -- Recursive: walk upward through the graph
                SELECT
                    ol.parent_id,
                    ot.entity_id,
                    ol.tenant_id,
                    (ot.cumulative_pct * ol.percentage / 100.0) AS cumulative_pct,
                    ot.depth + 1
                FROM iam_ownership_links ol
                JOIN ownership_tree ot ON ol.child_id = ot.owner_id
                WHERE ol.tenant_id = ?
                  AND (ol.valid_to IS NULL OR ol.valid_to > NOW())
                  AND ot.depth < ?
            )
            SELECT DISTINCT
                owner_id, entity_id, tenant_id,
                SUM(cumulative_pct) AS cumulative_pct,
                MIN(depth)          AS depth
            FROM ownership_tree
            GROUP BY owner_id, entity_id, tenant_id
            HAVING SUM(cumulative_pct) >= ?
            ORDER BY cumulative_pct DESC
            """;

    // ─── State ────────────────────────────────────────────────────────────────

    private final DataSource dataSource;
    private final Executor executor;

    public PostgresBeneficialOwnershipStore(DataSource dataSource, Executor executor) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.executor   = Objects.requireNonNull(executor, "executor");
    }

    // ─── BeneficialOwnershipStore ─────────────────────────────────────────────

    @Override
    public Promise<Void> saveEntity(OwnershipEntity entity) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SQL_INSERT_ENTITY)) {
                ps.setString(1, entity.id());
                ps.setString(2, entity.name());
                ps.setString(3, entity.entityType().name());
                ps.setString(4, entity.tenantId());
                ps.setTimestamp(5, Timestamp.from(entity.createdAt()));
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public Promise<OwnershipEntity> findEntity(String entityId, String tenantId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SQL_FIND_ENTITY)) {
                ps.setString(1, entityId);
                ps.setString(2, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return null;
                    return new OwnershipEntity(
                        rs.getString("entity_id"),
                        rs.getString("entity_name"),
                        OwnershipEntity.EntityType.valueOf(rs.getString("entity_type")),
                        rs.getString("tenant_id"),
                        rs.getTimestamp("created_at").toInstant()
                    );
                }
            }
        });
    }

    @Override
    public Promise<Void> saveLink(OwnershipLink link) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SQL_INSERT_LINK)) {
                ps.setString(1, link.parentId());
                ps.setString(2, link.childId());
                ps.setString(3, link.relationshipType().name());
                ps.setBigDecimal(4, link.percentage());
                ps.setString(5, link.tenantId());
                ps.setTimestamp(6, Timestamp.from(link.validFrom()));
                ps.setTimestamp(7, link.validTo() == null ? null : Timestamp.from(link.validTo()));
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public Promise<List<OwnershipLink>> findDirectLinks(String entityId, String tenantId) {
        return Promise.ofBlocking(executor, () -> {
            List<OwnershipLink> links = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SQL_DIRECT_LINKS)) {
                ps.setString(1, entityId);
                ps.setString(2, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Timestamp validTo = rs.getTimestamp("valid_to");
                        links.add(new OwnershipLink(
                            rs.getString("parent_id"),
                            rs.getString("child_id"),
                            OwnershipLink.RelationshipType.valueOf(rs.getString("relationship_type")),
                            rs.getBigDecimal("percentage"),
                            rs.getString("tenant_id"),
                            rs.getTimestamp("valid_from").toInstant(),
                            validTo == null ? null : validTo.toInstant()
                        ));
                    }
                }
            }
            return links;
        });
    }

    @Override
    public Promise<List<BeneficialOwner>> findBeneficialOwners(
            String entityId, String tenantId,
            BigDecimal thresholdPercent, int maxDepth) {

        return Promise.ofBlocking(executor, () -> {
            List<BeneficialOwner> owners = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SQL_BENEFICIAL_OWNERS)) {
                ps.setString(1, entityId);
                ps.setString(2, tenantId);
                ps.setString(3, tenantId);
                ps.setInt(4, maxDepth);
                ps.setBigDecimal(5, thresholdPercent);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        owners.add(new BeneficialOwner(
                            rs.getString("owner_id"),
                            rs.getString("entity_id"),
                            rs.getString("tenant_id"),
                            rs.getBigDecimal("cumulative_pct"),
                            rs.getInt("depth")
                        ));
                    }
                }
            }
            return owners;
        });
    }
}
