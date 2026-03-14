package com.ghatana.appplatform.iam.rbac;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/**
 * PostgreSQL-backed RBAC store using the {@code iam_roles}, {@code iam_permissions},
 * and {@code iam_role_assignments} tables (V002 migration).
 *
 * @doc.type class
 * @doc.purpose PostgreSQL RBAC role-permission store adapter (STORY-K01-RBAC)
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class PostgresRolePermissionStore implements RolePermissionStore {

    private final DataSource dataSource;

    public PostgresRolePermissionStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void saveRole(Role role) {
        String upsertRole = """
            INSERT INTO iam_roles (role_id, role_name, tenant_id, description)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (tenant_id, role_name) DO UPDATE
               SET description = EXCLUDED.description
            """;
        String deletePerms = "DELETE FROM iam_permissions WHERE role_id = ?";
        String insertPerm  = "INSERT INTO iam_permissions (role_id, resource, action) VALUES (?, ?, ?)";

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(upsertRole)) {
                ps.setString(1, role.roleId());
                ps.setString(2, role.roleName());
                ps.setString(3, role.tenantId());
                ps.setString(4, role.description());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(deletePerms)) {
                ps.setString(1, role.roleId());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(insertPerm)) {
                for (Permission p : role.permissions()) {
                    ps.setString(1, role.roleId());
                    ps.setString(2, p.resource());
                    ps.setString(3, p.action());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            conn.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save role: " + role.roleName(), e);
        }
    }

    @Override
    public Optional<Role> findRole(String tenantId, String roleName) {
        String sql = """
            SELECT r.role_id, r.role_name, r.tenant_id, r.description,
                   p.resource, p.action
              FROM iam_roles r
              LEFT JOIN iam_permissions p ON p.role_id = r.role_id
             WHERE (r.tenant_id = ? OR r.tenant_id IS NULL)
               AND r.role_name = ?
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tenantId);
            ps.setString(2, roleName);
            return buildRoleFromResultSet(ps.executeQuery()).stream().findFirst();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find role: " + roleName, e);
        }
    }

    @Override
    public List<Role> listRoles(String tenantId) {
        String sql = """
            SELECT r.role_id, r.role_name, r.tenant_id, r.description,
                   p.resource, p.action
              FROM iam_roles r
              LEFT JOIN iam_permissions p ON p.role_id = r.role_id
             WHERE r.tenant_id = ? OR r.tenant_id IS NULL
             ORDER BY r.role_name
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tenantId);
            return buildRoleFromResultSet(ps.executeQuery());
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list roles for tenant: " + tenantId, e);
        }
    }

    @Override
    public void assignRole(String tenantId, String principalId, String roleName) {
        String sql = """
            INSERT INTO iam_role_assignments (tenant_id, principal_id, role_name)
            VALUES (?, ?, ?)
            ON CONFLICT (tenant_id, principal_id, role_name) DO NOTHING
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tenantId);
            ps.setString(2, principalId);
            ps.setString(3, roleName);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to assign role=" + roleName + " to principal=" + principalId, e);
        }
    }

    @Override
    public void revokeRole(String tenantId, String principalId, String roleName) {
        String sql = """
            DELETE FROM iam_role_assignments
             WHERE tenant_id = ? AND principal_id = ? AND role_name = ?
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tenantId);
            ps.setString(2, principalId);
            ps.setString(3, roleName);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to revoke role", e);
        }
    }

    @Override
    public List<Role> getPrincipalRoles(String tenantId, String principalId) {
        String sql = """
            SELECT r.role_id, r.role_name, r.tenant_id, r.description,
                   p.resource, p.action
              FROM iam_role_assignments a
              JOIN iam_roles r ON r.role_name = a.role_name
                              AND (r.tenant_id = a.tenant_id OR r.tenant_id IS NULL)
              LEFT JOIN iam_permissions p ON p.role_id = r.role_id
             WHERE a.tenant_id = ? AND a.principal_id = ?
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tenantId);
            ps.setString(2, principalId);
            return buildRoleFromResultSet(ps.executeQuery());
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get roles for principal=" + principalId, e);
        }
    }

    @Override
    public boolean hasPermission(String tenantId, String principalId, String resource, String action) {
        return getPrincipalRoles(tenantId, principalId).stream()
            .anyMatch(r -> r.hasPermission(resource, action));
    }

    // ── Private Helpers ───────────────────────────────────────────────────────

    private List<Role> buildRoleFromResultSet(ResultSet rs) throws SQLException {
        Map<String, Role> roles = new LinkedHashMap<>();
        Map<String, Set<Permission>> perms = new LinkedHashMap<>();

        while (rs.next()) {
            String roleId = rs.getString("role_id");
            roles.computeIfAbsent(roleId, id -> {
                try {
                    return new Role(id,
                        rs.getString("role_name"),
                        rs.getString("tenant_id"),
                        rs.getString("description"),
                        new HashSet<>());
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            });
            String resource = rs.getString("resource");
            String action   = rs.getString("action");
            if (resource != null && action != null) {
                perms.computeIfAbsent(roleId, id -> new HashSet<>())
                     .add(new Permission(resource, action));
            }
        }
        List<Role> result = new ArrayList<>();
        for (Map.Entry<String, Role> e : roles.entrySet()) {
            Role r = e.getValue();
            result.add(new Role(r.roleId(), r.roleName(), r.tenantId(), r.description(),
                perms.getOrDefault(r.roleId(), Set.of())));
        }
        return result;
    }
}
