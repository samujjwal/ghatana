/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.iam.adapter;

import com.ghatana.appplatform.iam.domain.ClientCredentials;
import com.ghatana.appplatform.iam.domain.ClientCredentials.ClientStatus;
import com.ghatana.appplatform.iam.port.ClientCredentialStore;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * PostgreSQL-backed implementation of {@link ClientCredentialStore}.
 *
 * <p>Reads from / writes to {@code iam_client_credentials} and
 * {@code iam_roles_permissions} tables created by
 * {@code V001__create_iam_schema.sql}.
 *
 * <p>All JDBC operations are wrapped in {@link Promise#ofBlocking} to avoid
 * blocking the ActiveJ event loop.
 *
 * @doc.type class
 * @doc.purpose PostgreSQL adapter for the ClientCredentialStore port (STORY-K01-001)
 * @doc.layer product
 * @doc.pattern Repository
 */
public final class PostgresClientCredentialStore implements ClientCredentialStore {

    private static final Logger log = LoggerFactory.getLogger(PostgresClientCredentialStore.class);

    // ──────────────────────────────────────────────────────────────────────
    // SQL statements
    // ──────────────────────────────────────────────────────────────────────

    private static final String SELECT_BY_CLIENT_ID_STR = """
            SELECT client_id, client_id_str, client_secret_hash,
                   tenant_id, granted_scopes, status
              FROM iam_client_credentials
             WHERE client_id_str = ?
            """;

    /**
     * Loads all permissions for the given roles scoped to a tenant.
     * Includes global role permissions (tenant_id IS NULL) via IS NOT DISTINCT FROM.
     * Passing a null tenantId returns only global (tenant_id IS NULL) permissions.
     */
    private static final String SELECT_PERMISSIONS_FOR_ROLES = """
            SELECT DISTINCT permission
              FROM iam_roles_permissions
             WHERE role_name = ANY(?)
               AND (tenant_id IS NULL OR tenant_id IS NOT DISTINCT FROM ?::uuid)
            """;

    private static final String UPDATE_LAST_USED = """
            UPDATE iam_client_credentials
               SET last_used_at = NOW()
             WHERE client_id = ?::uuid
            """;

    // ──────────────────────────────────────────────────────────────────────
    // Fields
    // ──────────────────────────────────────────────────────────────────────

    private final DataSource dataSource;
    private final Executor executor;

    /**
     * @param dataSource HikariCP-backed data source
     * @param executor   bounded thread pool for blocking JDBC calls
     */
    public PostgresClientCredentialStore(DataSource dataSource, Executor executor) {
        this.dataSource = dataSource;
        this.executor = executor;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Port implementation
    // ──────────────────────────────────────────────────────────────────────

    @Override
    public Promise<Optional<ClientCredentials>> getByClientIdStr(String clientIdStr) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SELECT_BY_CLIENT_ID_STR)) {
                ps.setString(1, clientIdStr);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public Promise<List<String>> loadPermissionsForRoles(List<String> roles, UUID tenantId) {
        if (roles == null || roles.isEmpty()) {
            return Promise.of(List.of());
        }
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SELECT_PERMISSIONS_FOR_ROLES)) {
                Array rolesArray = conn.createArrayOf("text", roles.toArray(String[]::new));
                ps.setArray(1, rolesArray);
                if (tenantId != null) {
                    ps.setString(2, tenantId.toString());
                } else {
                    ps.setNull(2, Types.VARCHAR);
                }
                List<String> permissions = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        permissions.add(rs.getString("permission"));
                    }
                }
                return List.copyOf(permissions);
            }
        });
    }

    @Override
    public Promise<Void> updateLastUsed(UUID clientId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(UPDATE_LAST_USED)) {
                ps.setString(1, clientId.toString());
                int updated = ps.executeUpdate();
                if (updated == 0) {
                    log.warn("updateLastUsed: no row matched for clientId={}", clientId);
                }
            }
            return null;
        });
    }

    // ──────────────────────────────────────────────────────────────────────
    // Row mapping
    // ──────────────────────────────────────────────────────────────────────

    private static ClientCredentials mapRow(ResultSet rs) throws SQLException {
        UUID clientId = UUID.fromString(rs.getString("client_id"));
        String clientIdStr = rs.getString("client_id_str");
        String secretHash = rs.getString("client_secret_hash");
        UUID tenantId = UUID.fromString(rs.getString("tenant_id"));

        Array scopesArray = rs.getArray("granted_scopes");
        List<String> grantedScopes = scopesArray != null
                ? List.of((String[]) scopesArray.getArray())
                : List.of();

        ClientStatus status = ClientStatus.valueOf(rs.getString("status"));

        return new ClientCredentials(clientId, clientIdStr, secretHash, tenantId, grantedScopes, status);
    }
}
