/*
 * Copyright (c) 2026 Ghatana Inc. 
 * All rights reserved.
 */
package com.ghatana.platform.pac;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link PostgresPolicyEngine} against a real PostgreSQL container.
 *
 * @doc.type class
 * @doc.purpose Integration tests for PostgresPolicyEngine with Testcontainers
 * @doc.layer platform
 * @doc.pattern Test
 */
@Tag("integration")
@Tag("infrastructure-backed")
@Testcontainers
@DisplayName("PostgresPolicyEngine — integration tests")
class PostgresPolicyEngineTest extends EventloopTestBase {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("aep_pac_test")
                    .withUsername("aep_test")
                    .withPassword("aep_test");

    private HikariDataSource dataSource;
    private PostgresPolicyEngine engine;

    @BeforeEach
    void setUp() throws Exception { 
        HikariConfig config = new HikariConfig(); 
        config.setJdbcUrl(POSTGRES.getJdbcUrl()); 
        config.setUsername(POSTGRES.getUsername()); 
        config.setPassword(POSTGRES.getPassword()); 
        config.setMaximumPoolSize(5); 
        dataSource = new HikariDataSource(config); 

        try (Connection conn = dataSource.getConnection(); 
             Statement stmt = conn.createStatement()) { 
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS policy_rules (
                        id          BIGSERIAL    PRIMARY KEY,
                        tenant_id   VARCHAR(255) NOT NULL,
                        policy_name VARCHAR(512) NOT NULL,
                        condition   JSONB        NOT NULL DEFAULT '{}',
                        effect      VARCHAR(10)  NOT NULL DEFAULT 'DENY',
                        risk_score  SMALLINT     NOT NULL DEFAULT 50,
                        reason      TEXT         NOT NULL DEFAULT '',
                        enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
                        created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
                    )
                    """);
        }

        engine = new PostgresPolicyEngine(dataSource); 
    }

    @AfterEach
    void tearDown() throws Exception { 
        try (Connection conn = dataSource.getConnection(); 
             Statement stmt = conn.createStatement()) { 
            stmt.execute("DROP TABLE IF EXISTS policy_rules");
        }
        dataSource.close(); 
    }

    private void insertRule(String tenantId, String policyName, String effect, String reason) throws Exception { 
        try (Connection conn = dataSource.getConnection(); 
                 PreparedStatement ps = conn.prepareStatement("""
                     INSERT INTO policy_rules (tenant_id, policy_name, condition, effect, reason)
                     VALUES (?, ?, '{}', ?, ?)
                     """)) {
            ps.setString(1, tenantId); 
            ps.setString(2, policyName); 
            ps.setString(3, effect); 
            ps.setString(4, reason); 
            ps.executeUpdate(); 
        }
    }

    @Test
    @DisplayName("evaluate with no rules defaults to ALLOW (open-world semantics)")
    void evaluate_noRules_defaultsToAllow() { 
        PolicyEvalResult result = runPromise(() -> 
                engine.evaluate("tenant-1", "tool_execution_policy", Map.of())); 
        assertThat(result.allowed()).isTrue(); 
        assertThat(result.riskScore()).isZero(); 
    }

    @Test
    @DisplayName("evaluate with a DENY rule returns denied with the reason")
    void evaluate_denyRule_returnsDenied() throws Exception { 
        insertRule("tenant-2", "tool_execution_policy", "DENY", "blocked by policy"); 

        PolicyEvalResult result = runPromise(() -> 
                engine.evaluate("tenant-2", "tool_execution_policy", Map.of())); 

        assertThat(result.allowed()).isFalse(); 
        assertThat(result.reasons()).contains("blocked by policy");
    }

    @Test
    @DisplayName("evaluate with an ALLOW rule returns allowed")
    void evaluate_allowRule_returnsAllowed() throws Exception { 
        insertRule("tenant-3", "tool_execution_policy", "ALLOW", "explicitly allowed"); 

        PolicyEvalResult result = runPromise(() -> 
                engine.evaluate("tenant-3", "tool_execution_policy", Map.of())); 

        assertThat(result.allowed()).isTrue(); 
    }

    @Test
    @DisplayName("global rule applies to any tenant")
    void evaluate_globalRule_appliesAcrossTenants() throws Exception { 
        insertRule("global", "data_access_policy", "DENY", "global data protection rule"); 

        PolicyEvalResult result = runPromise(() -> 
                engine.evaluate("any-tenant", "data_access_policy", Map.of())); 

        assertThat(result.allowed()).isFalse(); 
        assertThat(result.reasons()).contains("global data protection rule");
    }

    @Test
    @DisplayName("disabled rules are ignored during evaluation")
    void evaluate_disabledRule_isIgnored() throws Exception { 
        try (Connection conn = dataSource.getConnection(); 
                 PreparedStatement ps = conn.prepareStatement("""
                     INSERT INTO policy_rules (tenant_id, policy_name, condition, effect, reason, enabled)
                     VALUES (?, ?, '{}', ?, ?, FALSE)
                     """)) {
            ps.setString(1, "tenant-disabled"); 
            ps.setString(2, "tool_execution_policy"); 
            ps.setString(3, "DENY"); 
            ps.setString(4, "should be ignored"); 
            ps.executeUpdate(); 
        }

        PolicyEvalResult result = runPromise(() -> 
                engine.evaluate("tenant-disabled", "tool_execution_policy", Map.of())); 

        // Disabled rule → no active rules → open-world default ALLOW
        assertThat(result.allowed()).isTrue(); 
    }
}
