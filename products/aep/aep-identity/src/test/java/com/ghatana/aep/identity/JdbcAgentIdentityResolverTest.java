/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.identity;

import com.ghatana.identity.AgentIdentity;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link JdbcAgentIdentityResolver}.
 *
 * @doc.type class
 * @doc.purpose Verify JDBC-backed identity resolution maps active agent registrations to AgentIdentity
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("JdbcAgentIdentityResolver")
@ExtendWith(MockitoExtension.class)
class JdbcAgentIdentityResolverTest extends EventloopTestBase {

    @Mock
    private DataSource dataSource;
    @Mock
    private Connection connection;
    @Mock
    private PreparedStatement resolveAgentStatement;
    @Mock
    private PreparedStatement capabilitiesStatement;
    @Mock
    private ResultSet agentResultSet;
    @Mock
    private ResultSet capabilitiesResultSet;

    @Test
    @DisplayName("resolve returns active registered agent identity with derived capability scopes")
    void resolveReturnsActiveRegisteredIdentity() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString()))
            .thenReturn(resolveAgentStatement, capabilitiesStatement);
        when(resolveAgentStatement.executeQuery()).thenReturn(agentResultSet);
        when(capabilitiesStatement.executeQuery()).thenReturn(capabilitiesResultSet);

        when(agentResultSet.next()).thenReturn(true, false);
        when(agentResultSet.getTimestamp("updated_at"))
            .thenReturn(Timestamp.from(Instant.parse("2026-04-15T12:00:00Z")));

        when(capabilitiesResultSet.next()).thenReturn(true, true, false);
        when(capabilitiesResultSet.getString("capability"))
            .thenReturn("routing", "enrichment");

        JdbcAgentIdentityResolver resolver = new JdbcAgentIdentityResolver(dataSource);

        Optional<AgentIdentity> identity = runPromise(() -> resolver.resolve("tenant-a", "agent-1"));

        assertThat(identity).isPresent();
        assertThat(identity.orElseThrow().tenantId()).isEqualTo("tenant-a");
        assertThat(identity.orElseThrow().agentId()).isEqualTo("agent-1");
        assertThat(identity.orElseThrow().spiffeId())
            .isEqualTo("spiffe://ghatana.io/tenant/tenant-a/agent/agent-1");
        assertThat(identity.orElseThrow().scopes())
            .contains("aep:execute", "aep:capability:routing", "aep:capability:enrichment");
    }

    @Test
    @DisplayName("resolve returns empty for unknown or inactive agent")
    void resolveReturnsEmptyWhenAgentMissing() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(resolveAgentStatement);
        when(resolveAgentStatement.executeQuery()).thenReturn(agentResultSet);
        when(agentResultSet.next()).thenReturn(false);

        JdbcAgentIdentityResolver resolver = new JdbcAgentIdentityResolver(dataSource);

        Optional<AgentIdentity> identity = runPromise(() -> resolver.resolve("tenant-a", "missing-agent"));

        assertThat(identity).isEmpty();
    }
}