/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.identity;

import com.ghatana.identity.AgentIdentity;
import com.ghatana.identity.spi.IdentityResolver;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * JDBC-backed {@link IdentityResolver} for AEP production deployments.
 *
 * <p>Resolves agent identities from the durable agent registration tables already
 * maintained by AEP runtime components. Only agents with {@code status = 'ACTIVE'}
 * are considered trusted.
 *
 * <p>Granted scopes are derived from persisted agent capabilities and always include
 * the base {@code aep:execute} scope for active registered agents.
 *
 * @doc.type class
 * @doc.purpose Resolve AEP agent identities from durable JDBC-backed registrations
 * @doc.layer product
 * @doc.pattern Repository, Strategy
 */
public final class JdbcAgentIdentityResolver implements IdentityResolver {

    private static final Logger log = LoggerFactory.getLogger(JdbcAgentIdentityResolver.class);

    private static final String SQL_RESOLVE_AGENT = """
        SELECT updated_at
          FROM agent_registrations
         WHERE tenant_id = ? AND agent_id = ? AND status = 'ACTIVE'
        """;

    private static final String SQL_AGENT_CAPABILITIES = """
        SELECT capability
          FROM agent_capabilities
         WHERE tenant_id = ? AND agent_id = ?
         ORDER BY capability
        """;

    private final DataSource dataSource;
    private final ExecutorService executor;

    public JdbcAgentIdentityResolver(@NotNull DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @Override
    public Promise<Optional<AgentIdentity>> resolve(String tenantId, String agentId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(agentId, "agentId");

        return Promise.ofBlocking(executor, () -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement agentStatement = connection.prepareStatement(SQL_RESOLVE_AGENT)) {
                agentStatement.setString(1, tenantId);
                agentStatement.setString(2, agentId);

                try (ResultSet agentResult = agentStatement.executeQuery()) {
                    if (!agentResult.next()) {
                        return Optional.<AgentIdentity>empty();
                    }

                    Instant verifiedAt = toInstant(agentResult.getTimestamp("updated_at"));
                    Set<String> scopes = resolveScopes(connection, tenantId, agentId);

                    return Optional.of(new AgentIdentity(
                        tenantId,
                        agentId,
                        "spiffe://ghatana.io/tenant/" + tenantId + "/agent/" + agentId,
                        scopes,
                        verifiedAt));
                }
            }
        }).whenException(e -> log.error("Failed to resolve JDBC identity for tenant={} agent={}", tenantId, agentId, e));
    }

    private Set<String> resolveScopes(Connection connection, String tenantId, String agentId) throws Exception {
        LinkedHashSet<String> scopes = new LinkedHashSet<>();
        scopes.add("aep:execute");

        try (PreparedStatement capabilityStatement = connection.prepareStatement(SQL_AGENT_CAPABILITIES)) {
            capabilityStatement.setString(1, tenantId);
            capabilityStatement.setString(2, agentId);

            try (ResultSet capabilities = capabilityStatement.executeQuery()) {
                while (capabilities.next()) {
                    String capability = capabilities.getString("capability");
                    if (capability != null && !capability.isBlank()) {
                        scopes.add("aep:capability:" + capability.trim());
                    }
                }
            }
        }

        return Set.copyOf(scopes);
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp != null ? timestamp.toInstant() : Instant.now();
    }
}