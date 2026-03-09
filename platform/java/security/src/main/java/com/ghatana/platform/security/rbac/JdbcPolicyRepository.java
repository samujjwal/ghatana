package com.ghatana.platform.security.rbac;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/**
 * JDBC-backed implementation of PolicyRepository for production use.
 *
 * <p><b>Purpose</b><br>
 * Provides persistent storage for RBAC policies using a relational database.
 * Replaces {@link InMemoryPolicyRepository} for production deployments.
 *
 * <p><b>Database Schema</b><br>
 * Requires the following tables:
 * <ul>
 *   <li>{@code policies} - Policy metadata (id, name, description, role, resource)</li>
 *   <li>{@code policy_permissions} - Policy permissions (policy_id, permission)</li>
 * </ul>
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * DataSource dataSource = // obtain from connection pool
 * PolicyRepository repository = new JdbcPolicyRepository(dataSource);
 * 
 * // Save a policy
 * Policy policy = new Policy("admin-all", "Admin policy", "admin", "*", Set.of("read", "write"));
 * repository.save(policy);
 * 
 * // Find policies by role
 * List<Policy> adminPolicies = repository.findByRole("admin");
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe when backed by a properly configured connection pool.
 *
 * @see PolicyRepository
 * @see InMemoryPolicyRepository
 * @doc.type class
 * @doc.purpose Database-backed policy repository
 * @doc.layer core
 * @doc.pattern Repository
 */
public class JdbcPolicyRepository implements PolicyRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcPolicyRepository.class);

    private static final String INSERT_POLICY = """
            INSERT INTO policies (id, name, description, role, resource, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                name = EXCLUDED.name,
                description = EXCLUDED.description,
                role = EXCLUDED.role,
                resource = EXCLUDED.resource,
                updated_at = EXCLUDED.updated_at
            """;

    private static final String INSERT_PERMISSION = """
            INSERT INTO policy_permissions (policy_id, permission)
            VALUES (?, ?)
            ON CONFLICT (policy_id, permission) DO NOTHING
            """;

    private static final String DELETE_PERMISSIONS = """
            DELETE FROM policy_permissions WHERE policy_id = ?
            """;

    private static final String SELECT_BY_ID = """
            SELECT p.id, p.name, p.description, p.role, p.resource
            FROM policies p
            WHERE p.id = ?
            """;

    private static final String SELECT_BY_ROLE = """
            SELECT p.id, p.name, p.description, p.role, p.resource
            FROM policies p
            WHERE p.role = ?
            """;

    private static final String SELECT_BY_RESOURCE = """
            SELECT p.id, p.name, p.description, p.role, p.resource
            FROM policies p
            WHERE p.resource = ? OR p.resource = '*'
            """;

    private static final String SELECT_ALL = """
            SELECT p.id, p.name, p.description, p.role, p.resource
            FROM policies p
            """;

    private static final String SELECT_PERMISSIONS = """
            SELECT permission FROM policy_permissions WHERE policy_id = ?
            """;

    private static final String DELETE_BY_ID = """
            DELETE FROM policies WHERE id = ?
            """;

    private final DataSource dataSource;

    /**
     * Creates a new JdbcPolicyRepository with the specified data source.
     *
     * @param dataSource the JDBC data source for database connections
     * @throws NullPointerException if dataSource is null
     */
    public JdbcPolicyRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
    }

    @Override
    public Optional<Policy> findById(String id) {
        Objects.requireNonNull(id, "id must not be null");

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_BY_ID)) {

            stmt.setString(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Policy policy = mapPolicy(rs);
                    Set<String> permissions = loadPermissions(conn, id);
                    return Optional.of(policy.withPermissions(permissions));
                }
            }
            return Optional.empty();

        } catch (SQLException e) {
            LOGGER.error("Failed to find policy by id: {}", id, e);
            throw new RuntimeException("Failed to find policy", e);
        }
    }

    @Override
    public List<Policy> findByRole(String role) {
        Objects.requireNonNull(role, "role must not be null");

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_BY_ROLE)) {

            stmt.setString(1, role);
            return executeQueryAndMapPolicies(conn, stmt);

        } catch (SQLException e) {
            LOGGER.error("Failed to find policies by role: {}", role, e);
            throw new RuntimeException("Failed to find policies by role", e);
        }
    }

    @Override
    public List<Policy> findByResource(String resource) {
        Objects.requireNonNull(resource, "resource must not be null");

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_BY_RESOURCE)) {

            stmt.setString(1, resource);
            return executeQueryAndMapPolicies(conn, stmt);

        } catch (SQLException e) {
            LOGGER.error("Failed to find policies by resource: {}", resource, e);
            throw new RuntimeException("Failed to find policies by resource", e);
        }
    }

    @Override
    public List<Policy> findAll() {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_ALL)) {

            return executeQueryAndMapPolicies(conn, stmt);

        } catch (SQLException e) {
            LOGGER.error("Failed to find all policies", e);
            throw new RuntimeException("Failed to find all policies", e);
        }
    }

    @Override
    public Policy save(Policy policy) {
        Objects.requireNonNull(policy, "policy must not be null");

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            try {
                // Ensure policy has an ID
                String policyId = policy.getId();
                if (policyId == null || policyId.isBlank()) {
                    policyId = UUID.randomUUID().toString();
                    policy = Policy.builder()
                            .id(policyId)
                            .name(policy.getName())
                            .description(policy.getDescription())
                            .role(policy.getRole())
                            .resource(policy.getResource())
                            .permissions(policy.getPermissions())
                            .enabled(policy.isEnabled())
                            .build();
                }

                Timestamp now = new Timestamp(System.currentTimeMillis());

                // Insert/update policy
                try (PreparedStatement stmt = conn.prepareStatement(INSERT_POLICY)) {
                    stmt.setString(1, policyId);
                    stmt.setString(2, policy.getName());
                    stmt.setString(3, policy.getDescription());
                    stmt.setString(4, policy.getRole());
                    stmt.setString(5, policy.getResource());
                    stmt.setTimestamp(6, now);
                    stmt.setTimestamp(7, now);
                    stmt.executeUpdate();
                }

                // Delete existing permissions
                try (PreparedStatement stmt = conn.prepareStatement(DELETE_PERMISSIONS)) {
                    stmt.setString(1, policyId);
                    stmt.executeUpdate();
                }

                // Insert new permissions
                if (policy.getPermissions() != null && !policy.getPermissions().isEmpty()) {
                    try (PreparedStatement stmt = conn.prepareStatement(INSERT_PERMISSION)) {
                        for (String permission : policy.getPermissions()) {
                            stmt.setString(1, policyId);
                            stmt.setString(2, permission);
                            stmt.addBatch();
                        }
                        stmt.executeBatch();
                    }
                }

                conn.commit();
                LOGGER.debug("Saved policy: {}", policyId);
                return policy;

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }

        } catch (SQLException e) {
            LOGGER.error("Failed to save policy: {}", policy.getId(), e);
            throw new RuntimeException("Failed to save policy", e);
        }
    }

    @Override
    public boolean deleteById(String id) {
        Objects.requireNonNull(id, "id must not be null");

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            try {
                // Delete permissions first (foreign key constraint)
                try (PreparedStatement stmt = conn.prepareStatement(DELETE_PERMISSIONS)) {
                    stmt.setString(1, id);
                    stmt.executeUpdate();
                }

                // Delete policy
                try (PreparedStatement stmt = conn.prepareStatement(DELETE_BY_ID)) {
                    stmt.setString(1, id);
                    int deleted = stmt.executeUpdate();
                    conn.commit();

                    if (deleted > 0) {
                        LOGGER.debug("Deleted policy: {}", id);
                        return true;
                    }
                    return false;
                }

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }

        } catch (SQLException e) {
            LOGGER.error("Failed to delete policy: {}", id, e);
            throw new RuntimeException("Failed to delete policy", e);
        }
    }

    private List<Policy> executeQueryAndMapPolicies(Connection conn, PreparedStatement stmt)
            throws SQLException {
        List<Policy> policies = new ArrayList<>();

        try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                Policy policy = mapPolicy(rs);
                Set<String> permissions = loadPermissions(conn, policy.getId());
                policies.add(policy.withPermissions(permissions));
            }
        }

        return policies;
    }

    private Policy mapPolicy(ResultSet rs) throws SQLException {
        return Policy.builder()
                .id(rs.getString("id"))
                .name(rs.getString("name"))
                .description(rs.getString("description"))
                .role(rs.getString("role"))
                .resource(rs.getString("resource"))
                .permissions(new HashSet<>()) // Permissions loaded separately
                .build();
    }

    private Set<String> loadPermissions(Connection conn, String policyId) throws SQLException {
        Set<String> permissions = new HashSet<>();

        try (PreparedStatement stmt = conn.prepareStatement(SELECT_PERMISSIONS)) {
            stmt.setString(1, policyId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    permissions.add(rs.getString("permission"));
                }
            }
        }

        return permissions;
    }
}
