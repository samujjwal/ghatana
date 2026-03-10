/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.repository.jdbc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.security.rbac.RolePermissionRegistry;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JDBC-backed implementation of {@link RolePermissionRegistry} with an in-memory read cache.
 *
 * <p><b>Purpose</b><br>
 * Stores role-to-permission mappings in PostgreSQL (replacing hardcoded in-memory definitions)
 * so that RBAC configuration survives restarts and can be updated at runtime without redeployment.
 *
 * <p><b>Caching Strategy</b><br>
 * Permissions are loaded eagerly from the DB on construction, then cached in a
 * {@link ConcurrentHashMap} for O(1) read performance. {@link #registerRole} writes through
 * to the DB and updates the cache atomically.
 *
 * <p><b>Multi-Tenancy</b><br>
 * This registry is system-wide (roles are shared across tenants). Per-tenant overrides
 * would require a separate {@code tenant_id}-scoped table — out of scope for this phase.
 *
 * @doc.type class
 * @doc.purpose JDBC-backed role-permission registry with eager-load read cache
 * @doc.layer product
 * @doc.pattern Repository
 */
public class JdbcRolePermissionRegistry implements RolePermissionRegistry {

  private static final Logger logger = LoggerFactory.getLogger(JdbcRolePermissionRegistry.class);
  private static final TypeReference<Set<String>> SET_TYPE = new TypeReference<>() {};

  private final DataSource dataSource;
  private final ObjectMapper objectMapper;

  /** In-memory read cache populated on startup. Writes go through to DB + cache. */
  private final Map<String, Set<String>> cache = new ConcurrentHashMap<>();

  /**
   * Creates a JdbcRolePermissionRegistry and eagerly loads all roles from the DB.
   *
   * @param dataSource the JDBC data source
   * @param objectMapper Jackson mapper for permissions JSONB serialization
   */
  public JdbcRolePermissionRegistry(DataSource dataSource, ObjectMapper objectMapper) {
    this.dataSource = Objects.requireNonNull(dataSource, "DataSource must not be null");
    this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper must not be null");
    loadAll();
  }

  @Override
  public Set<String> getPermissions(String role) {
    return cache.get(role);
  }

  @Override
  public boolean hasPermission(String role, String permission) {
    Set<String> permissions = cache.get(role);
    return permissions != null && permissions.contains(permission);
  }

  @Override
  public void registerRole(String role, Set<String> permissions) {
    if (role == null || role.isBlank()) {
      throw new IllegalArgumentException("Role cannot be null or empty");
    }
    Objects.requireNonNull(permissions, "Permissions cannot be null");

    try {
      String permJson = objectMapper.writeValueAsString(permissions);
      String sql = """
          INSERT INTO role_permissions (role_name, permissions, created_at, updated_at)
          VALUES (?, ?::jsonb, ?, ?)
          ON CONFLICT (role_name)
          DO UPDATE SET permissions = EXCLUDED.permissions, updated_at = EXCLUDED.updated_at
          """;

      try (Connection conn = dataSource.getConnection();
          PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setString(1, role);
        ps.setString(2, permJson);
        ps.setTimestamp(3, Timestamp.from(Instant.now()));
        ps.setTimestamp(4, Timestamp.from(Instant.now()));
        ps.executeUpdate();
      }

      // Update cache after successful DB write
      cache.put(role, Set.copyOf(permissions));
      logger.debug("Registered role '{}' with {} permissions", role, permissions.size());

    } catch (Exception e) {
      throw new RuntimeException("Failed to register role '" + role + "' in DB", e);
    }
  }

  // -------------------------------------------------------------------------
  // Private helpers
  // -------------------------------------------------------------------------

  /**
   * Eagerly loads all role-permission mappings from the DB into the in-memory cache.
   * Called once during construction. Logs a warning if the table is empty (likely first boot
   * before V15 migration seeds defaults).
   */
  private void loadAll() {
    String sql = "SELECT role_name, permissions FROM role_permissions";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql);
        ResultSet rs = ps.executeQuery()) {

      int count = 0;
      while (rs.next()) {
        String role = rs.getString("role_name");
        String permJson = rs.getString("permissions");
        Set<String> permissions = objectMapper.readValue(permJson, SET_TYPE);
        cache.put(role, Set.copyOf(permissions));
        count++;
      }

      if (count == 0) {
        logger.warn(
            "JdbcRolePermissionRegistry: No roles found in DB. "
                + "Ensure V15 migration has run to seed default roles.");
      } else {
        logger.info("JdbcRolePermissionRegistry: Loaded {} roles from PostgreSQL", count);
      }

    } catch (Exception e) {
      throw new RuntimeException("Failed to load role permissions from DB", e);
    }
  }
}
