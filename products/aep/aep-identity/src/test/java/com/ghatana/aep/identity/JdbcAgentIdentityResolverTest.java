/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
@DisplayName("JdbcAgentIdentityResolver [GH-90000]")
@ExtendWith(MockitoExtension.class) // GH-90000
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
    @DisplayName("resolve returns active registered agent identity with derived capability scopes [GH-90000]")
    void resolveReturnsActiveRegisteredIdentity() throws Exception { // GH-90000
        when(dataSource.getConnection()).thenReturn(connection); // GH-90000
        when(connection.prepareStatement(anyString())) // GH-90000
            .thenReturn(resolveAgentStatement, capabilitiesStatement); // GH-90000
        when(resolveAgentStatement.executeQuery()).thenReturn(agentResultSet); // GH-90000
        when(capabilitiesStatement.executeQuery()).thenReturn(capabilitiesResultSet); // GH-90000

        when(agentResultSet.next()).thenReturn(true, false); // GH-90000
        when(agentResultSet.getTimestamp("updated_at [GH-90000]"))
            .thenReturn(Timestamp.from(Instant.parse("2026-04-15T12:00:00Z [GH-90000]")));

        when(capabilitiesResultSet.next()).thenReturn(true, true, false); // GH-90000
        when(capabilitiesResultSet.getString("capability [GH-90000]"))
            .thenReturn("routing", "enrichment"); // GH-90000

        JdbcAgentIdentityResolver resolver = new JdbcAgentIdentityResolver(dataSource); // GH-90000

        Optional<AgentIdentity> identity = runPromise(() -> resolver.resolve("tenant-a", "agent-1")); // GH-90000

        assertThat(identity).isPresent(); // GH-90000
        assertThat(identity.orElseThrow().tenantId()).isEqualTo("tenant-a [GH-90000]");
        assertThat(identity.orElseThrow().agentId()).isEqualTo("agent-1 [GH-90000]");
        assertThat(identity.orElseThrow().spiffeId()) // GH-90000
            .isEqualTo("spiffe://ghatana.io/tenant/tenant-a/agent/agent-1 [GH-90000]");
        assertThat(identity.orElseThrow().scopes()) // GH-90000
            .contains("aep:execute", "aep:capability:routing", "aep:capability:enrichment"); // GH-90000
    }

    @Test
    @DisplayName("resolve returns empty for unknown or inactive agent [GH-90000]")
    void resolveReturnsEmptyWhenAgentMissing() throws Exception { // GH-90000
        when(dataSource.getConnection()).thenReturn(connection); // GH-90000
        when(connection.prepareStatement(anyString())).thenReturn(resolveAgentStatement); // GH-90000
        when(resolveAgentStatement.executeQuery()).thenReturn(agentResultSet); // GH-90000
        when(agentResultSet.next()).thenReturn(false); // GH-90000

        JdbcAgentIdentityResolver resolver = new JdbcAgentIdentityResolver(dataSource); // GH-90000

        Optional<AgentIdentity> identity = runPromise(() -> resolver.resolve("tenant-a", "missing-agent")); // GH-90000

        assertThat(identity).isEmpty(); // GH-90000
    }
}