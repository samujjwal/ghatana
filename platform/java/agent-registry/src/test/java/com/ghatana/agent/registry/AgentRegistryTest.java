/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.ghatana.agent.registry;

import com.ghatana.agent.*;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link InMemoryAgentRegistry} and {@link JdbcAgentRegistry}.
 *
 * <ul>
 *   <li>All async tests extend {@link EventloopTestBase} and use {@code runPromise()}.</li>
 *   <li>{@link JdbcAgentRegistry} tests use an in-memory H2 database with the same DDL
 *       as the production PostgreSQL schema.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Unit and integration tests for agent registry implementations
 * @doc.layer registry
 * @doc.pattern Test Suite
 */
@DisplayName("Agent Registry Tests")
@ExtendWith(MockitoExtension.class)
class AgentRegistryTest extends EventloopTestBase {

    // ─────────────────────────────────────────────────────────────────────────
    // Test fixtures
    // ─────────────────────────────────────────────────────────────────────────

    private static final String TENANT_ID = "test-tenant-001";

    private static TypedAgent<String, String> makeAgent(String agentId, String... capabilities) {
        AgentDescriptor descriptor = AgentDescriptor.builder()
                .agentId(agentId)
                .name("Test Agent " + agentId)
                .type(AgentType.DETERMINISTIC)
                .capabilities(Set.of(capabilities))
                .build();

        return new TypedAgent<>() {
            @Override public AgentDescriptor descriptor() { return descriptor; }
            @Override public Promise<AgentResult<String>> process(AgentContext ctx, String input) {
                return Promise.of(AgentResult.success(input.toUpperCase(), agentId, Duration.ofMillis(1)));
            }
            @Override public Promise<Void> initialize(AgentConfig config) { return Promise.complete(); }
            @Override public Promise<Void> shutdown() { return Promise.complete(); }
            @Override public Promise<HealthStatus> healthCheck() { return Promise.of(HealthStatus.HEALTHY); }
            @Override public Promise<Void> reconfigure(AgentConfig newConfig) { return Promise.complete(); }
            @Override public boolean validateInput(String input) { return input != null; }
            @Override public Promise<List<AgentResult<String>>> processBatch(AgentContext ctx, List<String> inputs) {
                return Promise.of(inputs.stream().map(i -> AgentResult.success(i.toUpperCase(), agentId, Duration.ofMillis(1))).toList());
            }
        };
    }

    private static AgentConfig makeConfig(String agentId) {
        return AgentConfig.builder()
                .agentId(agentId)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // InMemoryAgentRegistry tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("InMemoryAgentRegistry")
    class InMemoryRegistryTests {

        private InMemoryAgentRegistry registry;

        @BeforeEach
        void setUp() {
            registry = new InMemoryAgentRegistry();
        }

        @Test
        @DisplayName("register stores agent and can be resolved by ID")
        void shouldRegisterAndResolve() {
            TypedAgent<String, String> agent = makeAgent("agent-1", "text-processing");

            runPromise(() -> registry.register(agent, makeConfig("agent-1")));

            Optional<TypedAgent<String, String>> resolved =
                    runPromise(() -> registry.resolve("agent-1"));

            assertThat(resolved).isPresent();
            assertThat(resolved.get().descriptor().getAgentId()).isEqualTo("agent-1");
        }

        @Test
        @DisplayName("resolve returns empty for unknown agent")
        void shouldReturnEmptyForUnknownAgent() {
            Optional<TypedAgent<String, String>> result =
                    runPromise(() -> registry.resolve("does-not-exist"));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("deregister removes agent from registry")
        void shouldDeregisterAgent() {
            TypedAgent<String, String> agent = makeAgent("agent-2", "capability-a");
            runPromise(() -> registry.register(agent, makeConfig("agent-2")));

            // Verify registered
            assertThat(runPromise(() -> registry.<String, String>resolve("agent-2"))).isPresent();

            // Deregister
            runPromise(() -> registry.deregister("agent-2"));

            // Verify gone
            assertThat(runPromise(() -> registry.<String, String>resolve("agent-2"))).isEmpty();
        }

        @Test
        @DisplayName("deregister unknown agent is silent no-op")
        void shouldSilentlyDeregisterUnknown() {
            // Must not throw
            runPromise(() -> registry.deregister("non-existent"));
        }

        @Test
        @DisplayName("listAgentIds returns all registered IDs")
        void shouldListAllAgentIds() {
            runPromise(() -> registry.register(makeAgent("alpha"), makeConfig("alpha")));
            runPromise(() -> registry.register(makeAgent("beta"), makeConfig("beta")));

            Set<String> ids = runPromise(registry::listAgentIds);

            assertThat(ids).containsExactlyInAnyOrder("alpha", "beta");
        }

        @Test
        @DisplayName("findByCapability returns matching agent IDs")
        void shouldFindByCapability() {
            runPromise(() -> registry.register(makeAgent("cap-a-agent", "cap-a"), makeConfig("cap-a-agent")));
            runPromise(() -> registry.register(makeAgent("cap-b-agent", "cap-b"), makeConfig("cap-b-agent")));
            runPromise(() -> registry.register(makeAgent("both-agent", "cap-a", "cap-b"), makeConfig("both-agent")));

            List<String> capA = runPromise(() -> registry.findByCapability("cap-a"));

            assertThat(capA).containsExactlyInAnyOrder("cap-a-agent", "both-agent");
        }

        @Test
        @DisplayName("findByCapability returns empty list for unknown capability")
        void shouldReturnEmptyForUnknownCapability() {
            List<String> result = runPromise(() -> registry.findByCapability("unknown-cap"));
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("getStats reflects current registration count")
        void shouldReturnCorrectStats() {
            runPromise(() -> registry.register(makeAgent("s1"), makeConfig("s1")));
            runPromise(() -> registry.register(makeAgent("s2"), makeConfig("s2")));

            Map<String, Object> stats = runPromise(registry::getStats);

            assertThat(stats.get("registeredAgents")).isEqualTo(2);
            assertThat(stats.get("registryType")).isEqualTo("InMemory");
        }

        @Test
        @DisplayName("re-registration replaces existing entry")
        void shouldReplaceOnReRegister() {
            TypedAgent<String, String> v1 = makeAgent("evolving-agent", "old-cap");
            TypedAgent<String, String> v2 = makeAgent("evolving-agent", "new-cap");

            runPromise(() -> registry.register(v1, makeConfig("evolving-agent")));
            runPromise(() -> registry.register(v2, makeConfig("evolving-agent")));

            Set<String> ids = runPromise(registry::listAgentIds);
            assertThat(ids).hasSize(1);  // Still only one agent

            // new capability findable
            List<String> found = runPromise(() -> registry.findByCapability("new-cap"));
            assertThat(found).contains("evolving-agent");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // JdbcAgentRegistry tests — H2 in-memory DB
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("JdbcAgentRegistry")
    class JdbcRegistryTests {

        private JdbcAgentRegistry registry;
        private DataSource dataSource;

        @BeforeEach
        void setUp() throws Exception {
            dataSource = createH2DataSource();
            registry = new JdbcAgentRegistry(TENANT_ID, dataSource);
        }

        @AfterEach
        void tearDown() {
            registry.clearLocalCache();
        }

        @Test
        @DisplayName("register persists agent to DB and local cache")
        void shouldRegisterAndResolve() {
            TypedAgent<String, String> agent = makeAgent("jdbc-agent-1", "text-processing");

            runPromise(() -> registry.register(agent, makeConfig("jdbc-agent-1")));

            // Local cache hit (O(1))
            Optional<TypedAgent<String, String>> resolved =
                    runPromise(() -> registry.resolve("jdbc-agent-1"));

            assertThat(resolved).isPresent();
            assertThat(resolved.get().descriptor().getAgentId()).isEqualTo("jdbc-agent-1");
            assertThat(registry.localCacheSize()).isEqualTo(1);
        }

        @Test
        @DisplayName("listAgentIds queries DB for all active agents")
        void shouldListAllActiveAgentIds() {
            runPromise(() -> registry.register(makeAgent("jdbc-a"), makeConfig("jdbc-a")));
            runPromise(() -> registry.register(makeAgent("jdbc-b"), makeConfig("jdbc-b")));

            Set<String> ids = runPromise(registry::listAgentIds);

            assertThat(ids).containsExactlyInAnyOrder("jdbc-a", "jdbc-b");
        }

        @Test
        @DisplayName("deregister marks agent DEREGISTERED and removes from cache")
        void shouldDeregisterAgent() {
            runPromise(() -> registry.register(makeAgent("jdbc-dreg"), makeConfig("jdbc-dreg")));
            runPromise(() -> registry.deregister("jdbc-dreg"));

            // Cache cleared
            assertThat(registry.localCacheSize()).isEqualTo(0);

            // DB no longer returns as active
            Set<String> ids = runPromise(registry::listAgentIds);
            assertThat(ids).doesNotContain("jdbc-dreg");
        }

        @Test
        @DisplayName("findByCapability uses O(1) capability index")
        void shouldFindByCapabilityViaIndex() {
            runPromise(() -> registry.register(makeAgent("analyzer", "text-analysis", "nlp"), makeConfig("analyzer")));
            runPromise(() -> registry.register(makeAgent("transformer", "text-transform"), makeConfig("transformer")));
            runPromise(() -> registry.register(makeAgent("multi", "text-analysis", "text-transform"), makeConfig("multi")));

            List<String> nlpAgents = runPromise(() -> registry.findByCapability("text-analysis"));

            assertThat(nlpAgents).containsExactlyInAnyOrder("analyzer", "multi");
        }

        @Test
        @DisplayName("capability index is refreshed on re-registration")
        void shouldRefreshCapabilityIndexOnReRegister() {
            runPromise(() -> registry.register(makeAgent("evolving", "old-cap"), makeConfig("evolving")));
            runPromise(() -> registry.register(makeAgent("evolving", "new-cap"), makeConfig("evolving")));

            List<String> oldSearch = runPromise(() -> registry.findByCapability("old-cap"));
            List<String> newSearch = runPromise(() -> registry.findByCapability("new-cap"));

            assertThat(oldSearch).doesNotContain("evolving");  // old cap removed
            assertThat(newSearch).contains("evolving");         // new cap indexed
        }

        @Test
        @DisplayName("getStats returns accurate DB-sourced counts")
        void shouldReturnAccurateStats() {
            runPromise(() -> registry.register(makeAgent("stats-1", "cap-x"), makeConfig("stats-1")));
            runPromise(() -> registry.register(makeAgent("stats-2", "cap-y"), makeConfig("stats-2")));
            runPromise(() -> registry.deregister("stats-2"));

            Map<String, Object> stats = runPromise(registry::getStats);

            assertThat(stats.get("registryType")).isEqualTo("JdbcAgentRegistry");
            assertThat(stats.get("tenantId")).isEqualTo(TENANT_ID);
            assertThat(stats.get("activeAgents")).isEqualTo(1L);
            assertThat(stats.get("deregisteredAgents")).isEqualTo(1L);
            assertThat(stats.get("uniqueCapabilities")).isEqualTo(1L); // cap-x only (stats-2 deregistered → cascade delete cap)
        }

        @Test
        @DisplayName("tenant isolation: agents from a different tenant are invisible")
        void shouldIsolateTenants() {
            JdbcAgentRegistry otherTenant = new JdbcAgentRegistry("other-tenant-999", dataSource);

            runPromise(() -> registry.register(makeAgent("tenant-a-agent"), makeConfig("tenant-a-agent")));
            runPromise(() -> otherTenant.register(makeAgent("tenant-b-agent"), makeConfig("tenant-b-agent")));

            Set<String> tenantAIds = runPromise(registry::listAgentIds);
            Set<String> tenantBIds = runPromise(otherTenant::listAgentIds);

            assertThat(tenantAIds).containsOnly("tenant-a-agent");
            assertThat(tenantBIds).containsOnly("tenant-b-agent");
        }

        @Test
        @DisplayName("deregister unknown agent is silent no-op")
        void shouldSilentlyDeregisterUnknown() {
            runPromise(() -> registry.deregister("non-existent-agent"));
            // No exception = pass
        }

        // ── H2 schema setup ──────────────────────────────────────────────────

        private DataSource createH2DataSource() throws Exception {
            JdbcDataSource ds = new JdbcDataSource();
            ds.setURL("jdbc:h2:mem:agent_registry_test_" + System.nanoTime()
                    + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE");
            ds.setUser("sa");
            ds.setPassword("");

            try (Connection conn = ds.getConnection();
                 Statement stmt = conn.createStatement()) {
                // Create schema from V001 (H2 compatible version)
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS agent_registrations (
                        agent_id        VARCHAR(200)    NOT NULL,
                        tenant_id       VARCHAR(100)    NOT NULL,
                        agent_type      VARCHAR(100)    NOT NULL,
                        descriptor_json VARCHAR(65535)  NOT NULL,
                        config_json     VARCHAR(65535)  NOT NULL,
                        status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
                        registered_at   TIMESTAMP       NOT NULL DEFAULT NOW(),
                        updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
                        heartbeat_at    TIMESTAMP       NOT NULL DEFAULT NOW(),
                        node_id         VARCHAR(200),
                        CONSTRAINT pk_agent_registrations PRIMARY KEY (agent_id, tenant_id),
                        CONSTRAINT chk_agent_status CHECK (status IN ('ACTIVE','DEGRADED','INACTIVE','DEREGISTERED'))
                    )
                    """);
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS agent_capabilities (
                        agent_id    VARCHAR(200)    NOT NULL,
                        tenant_id   VARCHAR(100)    NOT NULL,
                        capability  VARCHAR(200)    NOT NULL,
                        added_at    TIMESTAMP       NOT NULL DEFAULT NOW(),
                        CONSTRAINT pk_agent_capabilities PRIMARY KEY (agent_id, tenant_id, capability),
                        CONSTRAINT fk_agent_cap_reg FOREIGN KEY (agent_id, tenant_id)
                            REFERENCES agent_registrations (agent_id, tenant_id) ON DELETE CASCADE
                    )
                    """);
            }
            return ds;
        }
    }
}
