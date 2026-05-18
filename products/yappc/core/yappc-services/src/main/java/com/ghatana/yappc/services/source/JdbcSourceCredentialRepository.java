package com.ghatana.yappc.services.source;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;

/**
 * @doc.type class
 * @doc.purpose JDBC-backed repository for governed source credential reference ownership
 * @doc.layer infrastructure
 * @doc.pattern Repository
 *
 * P0: Provides durable storage for credential bindings with tenant/workspace/project scoping.
 * Credential bindings map a credential reference to a secret key (environment variable name)
 * that contains the actual credential value.
 *
 * P0: Enforces tenant/workspace/project ownership validation at the database level.
 */
public final class JdbcSourceCredentialRepository implements SourceCredentialRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcSourceCredentialRepository.class);

    private static final String FIND_BINDING_SQL = """
        SELECT tenant_id, workspace_id, project_id, provider, credential_ref, secret_key
        FROM source_credential_bindings
        WHERE tenant_id = ? AND workspace_id = ? AND project_id = ? AND provider = ? AND credential_ref = ?
        """;

    private final DataSource dataSource;

    public JdbcSourceCredentialRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
    }

    @Override
    public Optional<CredentialBinding> findBinding(
            String tenantId,
            String workspaceId,
            String projectId,
            String provider,
            String credentialRef
    ) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        Objects.requireNonNull(projectId, "projectId must not be null");
        Objects.requireNonNull(provider, "provider must not be null");
        Objects.requireNonNull(credentialRef, "credentialRef must not be null");

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_BINDING_SQL)) {

            statement.setString(1, tenantId);
            statement.setString(2, workspaceId);
            statement.setString(3, projectId);
            statement.setString(4, provider);
            statement.setString(5, credentialRef);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(new CredentialBinding(
                        resultSet.getString("tenant_id"),
                        resultSet.getString("workspace_id"),
                        resultSet.getString("project_id"),
                        resultSet.getString("provider"),
                        resultSet.getString("credential_ref"),
                        resultSet.getString("secret_key")
                    ));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            log.error("Failed to query credential binding for tenantId={}, workspaceId={}, projectId={}, provider={}, credentialRef={}",
                tenantId, workspaceId, projectId, provider, credentialRef, e);
            return Optional.empty();
        }
    }
}
