/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.repository.jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.application.version.VersionComparator;
import com.ghatana.datacloud.entity.Entity;
import com.ghatana.datacloud.entity.version.EntityVersion;
import com.ghatana.datacloud.entity.version.VersionDiff;
import com.ghatana.datacloud.entity.version.VersionMetadata;
import com.ghatana.datacloud.entity.version.VersionRecord;
import io.activej.promise.Promise;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JDBC-backed implementation of {@link VersionRecord} for durable entity version persistence.
 *
 * <p><b>Purpose</b><br>
 * Provides cross-restart durability for entity version history by persisting to PostgreSQL.
 * Entity snapshots are stored as JSONB for flexible schema evolution.
 *
 * <p><b>Async Strategy</b><br>
 * All database operations are wrapped in {@code Promise.ofBlocking} to avoid blocking the
 * ActiveJ event loop. A dedicated executor is used for JDBC calls.
 *
 * <p><b>Multi-Tenancy</b><br>
 * Every query is scoped by {@code tenant_id} enforced at the SQL level.
 *
 * @doc.type class
 * @doc.purpose JDBC-backed entity version repository for durable version history
 * @doc.layer product
 * @doc.pattern Repository
 */
public class JdbcVersionRecord implements VersionRecord {

  private static final Logger logger = LoggerFactory.getLogger(JdbcVersionRecord.class);

  private static final Executor JDBC_EXECUTOR =
      Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "yappc-version-jdbc");
        t.setDaemon(true);
        return t;
      });

  private final DataSource dataSource;
  private final ObjectMapper objectMapper;
  private final VersionComparator comparator;

  /**
   * Creates a JdbcVersionRecord.
   *
   * @param dataSource the JDBC data source
   * @param objectMapper Jackson mapper for Entity snapshot serialization
   */
  public JdbcVersionRecord(DataSource dataSource, ObjectMapper objectMapper) {
    this.dataSource = Objects.requireNonNull(dataSource, "DataSource must not be null");
    this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper must not be null");
    this.comparator = new VersionComparator();
  }

  @Override
  public Promise<EntityVersion> saveVersion(
      String tenantId, Entity entity, VersionMetadata metadata) {
    Objects.requireNonNull(tenantId, "Tenant ID must not be null");
    Objects.requireNonNull(entity, "Entity must not be null");
    Objects.requireNonNull(metadata, "Metadata must not be null");

    return Promise.ofBlocking(JDBC_EXECUTOR, () -> {
      try (Connection conn = dataSource.getConnection()) {
        // Determine next version number
        int nextVersion = getNextVersionNumber(conn, tenantId, entity.getId());

        UUID versionId = UUID.randomUUID();
        String snapshotJson = objectMapper.writeValueAsString(entity);

        String sql = """
            INSERT INTO entity_version_records
                (id, tenant_id, entity_id, version_number, entity_snapshot, author, version_timestamp, reason, created_at)
            VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?)
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
          ps.setObject(1, versionId);
          ps.setString(2, tenantId);
          ps.setObject(3, entity.getId());
          ps.setInt(4, nextVersion);
          ps.setString(5, snapshotJson);
          ps.setString(6, metadata.author());
          ps.setTimestamp(7, Timestamp.from(metadata.timestamp()));
          ps.setString(8, metadata.reason());
          ps.setTimestamp(9, Timestamp.from(Instant.now()));
          ps.executeUpdate();
        }

        logger.debug(
            "Saved entity version tenantId={} entityId={} version={}",
            tenantId, entity.getId(), nextVersion);

        return new EntityVersion(
            versionId,
            tenantId,
            entity.getId(),
            entity,
            nextVersion,
            metadata,
            Instant.now());
      }
    });
  }

  @Override
  public Promise<List<EntityVersion>> getVersionHistory(String tenantId, UUID entityId) {
    Objects.requireNonNull(tenantId, "Tenant ID must not be null");
    Objects.requireNonNull(entityId, "Entity ID must not be null");

    return Promise.ofBlocking(JDBC_EXECUTOR, () -> {
      String sql = """
          SELECT id, tenant_id, entity_id, version_number, entity_snapshot,
                 author, version_timestamp, reason, created_at
          FROM entity_version_records
          WHERE tenant_id = ? AND entity_id = ?
          ORDER BY version_number ASC
          """;

      try (Connection conn = dataSource.getConnection();
          PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setString(1, tenantId);
        ps.setObject(2, entityId);

        List<EntityVersion> versions = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
          while (rs.next()) {
            versions.add(mapRow(rs));
          }
        }
        return versions;
      }
    });
  }

  @Override
  public Promise<EntityVersion> getVersion(String tenantId, UUID entityId, Integer versionNumber) {
    Objects.requireNonNull(tenantId, "Tenant ID must not be null");
    Objects.requireNonNull(entityId, "Entity ID must not be null");
    Objects.requireNonNull(versionNumber, "Version number must not be null");

    if (versionNumber < 1) {
      return Promise.ofException(
          new IllegalArgumentException("Version number must be >= 1"));
    }

    return Promise.ofBlocking(JDBC_EXECUTOR, () -> {
      String sql = """
          SELECT id, tenant_id, entity_id, version_number, entity_snapshot,
                 author, version_timestamp, reason, created_at
          FROM entity_version_records
          WHERE tenant_id = ? AND entity_id = ? AND version_number = ?
          """;

      try (Connection conn = dataSource.getConnection();
          PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setString(1, tenantId);
        ps.setObject(2, entityId);
        ps.setInt(3, versionNumber);

        try (ResultSet rs = ps.executeQuery()) {
          if (rs.next()) {
            return mapRow(rs);
          }
          throw new NoSuchElementException(
              "Version not found: " + versionNumber + " for entity " + entityId);
        }
      }
    });
  }

  @Override
  public Promise<EntityVersion> getLatestVersion(String tenantId, UUID entityId) {
    Objects.requireNonNull(tenantId, "Tenant ID must not be null");
    Objects.requireNonNull(entityId, "Entity ID must not be null");

    return Promise.ofBlocking(JDBC_EXECUTOR, () -> {
      String sql = """
          SELECT id, tenant_id, entity_id, version_number, entity_snapshot,
                 author, version_timestamp, reason, created_at
          FROM entity_version_records
          WHERE tenant_id = ? AND entity_id = ?
          ORDER BY version_number DESC
          LIMIT 1
          """;

      try (Connection conn = dataSource.getConnection();
          PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setString(1, tenantId);
        ps.setObject(2, entityId);

        try (ResultSet rs = ps.executeQuery()) {
          if (rs.next()) {
            return mapRow(rs);
          }
          throw new NoSuchElementException("No versions found for entity " + entityId);
        }
      }
    });
  }

  @Override
  public Promise<VersionDiff> computeDiff(
      String tenantId, UUID entityId, Integer versionNumberFrom, Integer versionNumberTo) {
    Objects.requireNonNull(tenantId, "Tenant ID must not be null");
    Objects.requireNonNull(entityId, "Entity ID must not be null");
    Objects.requireNonNull(versionNumberFrom, "From version must not be null");
    Objects.requireNonNull(versionNumberTo, "To version must not be null");

    return Promise.ofBlocking(JDBC_EXECUTOR, () -> {
      String sql = """
          SELECT id, tenant_id, entity_id, version_number, entity_snapshot,
                 author, version_timestamp, reason, created_at
          FROM entity_version_records
          WHERE tenant_id = ? AND entity_id = ? AND version_number IN (?, ?)
          ORDER BY version_number ASC
          """;

      try (Connection conn = dataSource.getConnection();
          PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setString(1, tenantId);
        ps.setObject(2, entityId);
        ps.setInt(3, versionNumberFrom);
        ps.setInt(4, versionNumberTo);

        EntityVersion fromVersion = null;
        EntityVersion toVersion = null;

        try (ResultSet rs = ps.executeQuery()) {
          while (rs.next()) {
            EntityVersion ev = mapRow(rs);
            if (ev.getVersionNumber().equals(versionNumberFrom)) {
              fromVersion = ev;
            } else if (ev.getVersionNumber().equals(versionNumberTo)) {
              toVersion = ev;
            }
          }
        }

        if (fromVersion == null || toVersion == null) {
          throw new NoSuchElementException(
              "One or both versions not found for entity " + entityId);
        }

        return comparator.compare(
            fromVersion.getEntitySnapshot(), toVersion.getEntitySnapshot());
      }
    });
  }

  @Override
  public Promise<Integer> countVersions(String tenantId, UUID entityId) {
    Objects.requireNonNull(tenantId, "Tenant ID must not be null");
    Objects.requireNonNull(entityId, "Entity ID must not be null");

    return Promise.ofBlocking(JDBC_EXECUTOR, () -> {
      String sql = """
          SELECT COUNT(*) FROM entity_version_records
          WHERE tenant_id = ? AND entity_id = ?
          """;

      try (Connection conn = dataSource.getConnection();
          PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setString(1, tenantId);
        ps.setObject(2, entityId);

        try (ResultSet rs = ps.executeQuery()) {
          rs.next();
          return rs.getInt(1);
        }
      }
    });
  }

  @Override
  public Promise<Integer> deleteVersionHistory(String tenantId, UUID entityId) {
    Objects.requireNonNull(tenantId, "Tenant ID must not be null");
    Objects.requireNonNull(entityId, "Entity ID must not be null");

    return Promise.ofBlocking(JDBC_EXECUTOR, () -> {
      String sql = """
          DELETE FROM entity_version_records
          WHERE tenant_id = ? AND entity_id = ?
          """;

      try (Connection conn = dataSource.getConnection();
          PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setString(1, tenantId);
        ps.setObject(2, entityId);
        int deleted = ps.executeUpdate();
        logger.debug(
            "Deleted {} version records tenantId={} entityId={}", deleted, tenantId, entityId);
        return deleted;
      }
    });
  }

  // -------------------------------------------------------------------------
  // Private helpers
  // -------------------------------------------------------------------------

  /**
   * Returns the next sequential version number for a given entity.
   *
   * @param conn open JDBC connection
   * @param tenantId tenant ID
   * @param entityId entity UUID
   * @return 1-based next version number
   */
  private int getNextVersionNumber(Connection conn, String tenantId, UUID entityId)
      throws SQLException {
    String sql = """
        SELECT COALESCE(MAX(version_number), 0) + 1
        FROM entity_version_records
        WHERE tenant_id = ? AND entity_id = ?
        """;

    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, tenantId);
      ps.setObject(2, entityId);
      try (ResultSet rs = ps.executeQuery()) {
        rs.next();
        return rs.getInt(1);
      }
    }
  }

  /**
   * Maps a ResultSet row to an {@link EntityVersion}.
   *
   * @param rs open ResultSet positioned at a valid row
   * @return EntityVersion
   */
  private EntityVersion mapRow(ResultSet rs) throws Exception {
    UUID id = (UUID) rs.getObject("id");
    String tenantId = rs.getString("tenant_id");
    UUID entityId = (UUID) rs.getObject("entity_id");
    int versionNumber = rs.getInt("version_number");
    String snapshotJson = rs.getString("entity_snapshot");
    String author = rs.getString("author");
    Instant versionTimestamp = rs.getTimestamp("version_timestamp").toInstant();
    String reason = rs.getString("reason");
    Instant createdAt = rs.getTimestamp("created_at").toInstant();

    Entity snapshot = objectMapper.readValue(snapshotJson, Entity.class);
    VersionMetadata metadata = new VersionMetadata(author, versionTimestamp, reason);

    return new EntityVersion(id, tenantId, entityId, snapshot, versionNumber, metadata, createdAt);
  }
}
