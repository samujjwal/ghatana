/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
@Tag("integration [GH-90000]")
@Testcontainers
@DisplayName("PostgresPolicyEngine — integration tests [GH-90000]")
class PostgresPolicyEngineTest extends EventloopTestBase {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15-alpine [GH-90000]")
                    .withDatabaseName("aep_pac_test [GH-90000]")
                    .withUsername("aep_test [GH-90000]")
                    .withPassword("aep_test [GH-90000]");

    private HikariDataSource dataSource;
    private PostgresPolicyEngine engine;

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        HikariConfig config = new HikariConfig(); // GH-90000
        config.setJdbcUrl(POSTGRES.getJdbcUrl()); // GH-90000
        config.setUsername(POSTGRES.getUsername()); // GH-90000
        config.setPassword(POSTGRES.getPassword()); // GH-90000
        config.setMaximumPoolSize(5); // GH-90000
        dataSource = new HikariDataSource(config); // GH-90000

        try (Connection conn = dataSource.getConnection(); // GH-90000
             Statement stmt = conn.createStatement()) { // GH-90000
            stmt.execute(""" // GH-90000
                    CREATE TABLE IF NOT EXISTS policy_rules ( // GH-90000
                        id          BIGSERIAL    PRIMARY KEY,
                        tenant_id   VARCHAR(255) NOT NULL, // GH-90000
                        policy_name VARCHAR(512) NOT NULL, // GH-90000
                        condition   JSONB        NOT NULL DEFAULT '{}',
                        effect      VARCHAR(10)  NOT NULL DEFAULT 'DENY', // GH-90000
                        risk_score  SMALLINT     NOT NULL DEFAULT 50,
                        reason      TEXT         NOT NULL DEFAULT '',
                        enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
                        created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW() // GH-90000
                    )
                    """);
        }

        engine = new PostgresPolicyEngine(dataSource); // GH-90000
    }

    @AfterEach
    void tearDown() throws Exception { // GH-90000
        try (Connection conn = dataSource.getConnection(); // GH-90000
             Statement stmt = conn.createStatement()) { // GH-90000
            stmt.execute("DROP TABLE IF EXISTS policy_rules [GH-90000]");
        }
        dataSource.close(); // GH-90000
    }

    private void insertRule(String tenantId, String policyName, String effect, String reason) throws Exception { // GH-90000
        try (Connection conn = dataSource.getConnection(); // GH-90000
             PreparedStatement ps = conn.prepareStatement(""" // GH-90000
                     INSERT INTO policy_rules (tenant_id, policy_name, condition, effect, reason) // GH-90000
                     VALUES (?, ?, '{}', ?, ?) // GH-90000
                     """)) {
            ps.setString(1, tenantId); // GH-90000
            ps.setString(2, policyName); // GH-90000
            ps.setString(3, effect); // GH-90000
            ps.setString(4, reason); // GH-90000
            ps.executeUpdate(); // GH-90000
        }
    }

    @Test
    @DisplayName("evaluate with no rules defaults to ALLOW (open-world semantics) [GH-90000]")
    void evaluate_noRules_defaultsToAllow() { // GH-90000
        PolicyEvalResult result = runPromise(() -> // GH-90000
                engine.evaluate("tenant-1", "tool_execution_policy", Map.of())); // GH-90000
        assertThat(result.allowed()).isTrue(); // GH-90000
        assertThat(result.riskScore()).isZero(); // GH-90000
    }

    @Test
    @DisplayName("evaluate with a DENY rule returns denied with the reason [GH-90000]")
    void evaluate_denyRule_returnsDenied() throws Exception { // GH-90000
        insertRule("tenant-2", "tool_execution_policy", "DENY", "blocked by policy"); // GH-90000

        PolicyEvalResult result = runPromise(() -> // GH-90000
                engine.evaluate("tenant-2", "tool_execution_policy", Map.of())); // GH-90000

        assertThat(result.allowed()).isFalse(); // GH-90000
        assertThat(result.reasons()).contains("blocked by policy [GH-90000]");
    }

    @Test
    @DisplayName("evaluate with an ALLOW rule returns allowed [GH-90000]")
    void evaluate_allowRule_returnsAllowed() throws Exception { // GH-90000
        insertRule("tenant-3", "tool_execution_policy", "ALLOW", "explicitly allowed"); // GH-90000

        PolicyEvalResult result = runPromise(() -> // GH-90000
                engine.evaluate("tenant-3", "tool_execution_policy", Map.of())); // GH-90000

        assertThat(result.allowed()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("global rule applies to any tenant [GH-90000]")
    void evaluate_globalRule_appliesAcrossTenants() throws Exception { // GH-90000
        insertRule("global", "data_access_policy", "DENY", "global data protection rule"); // GH-90000

        PolicyEvalResult result = runPromise(() -> // GH-90000
                engine.evaluate("any-tenant", "data_access_policy", Map.of())); // GH-90000

        assertThat(result.allowed()).isFalse(); // GH-90000
        assertThat(result.reasons()).contains("global data protection rule [GH-90000]");
    }

    @Test
    @DisplayName("disabled rules are ignored during evaluation [GH-90000]")
    void evaluate_disabledRule_isIgnored() throws Exception { // GH-90000
        try (Connection conn = dataSource.getConnection(); // GH-90000
             PreparedStatement ps = conn.prepareStatement(""" // GH-90000
                     INSERT INTO policy_rules (tenant_id, policy_name, condition, effect, reason, enabled) // GH-90000
                     VALUES (?, ?, '{}', ?, ?, FALSE) // GH-90000
                     """)) {
            ps.setString(1, "tenant-disabled"); // GH-90000
            ps.setString(2, "tool_execution_policy"); // GH-90000
            ps.setString(3, "DENY"); // GH-90000
            ps.setString(4, "should be ignored"); // GH-90000
            ps.executeUpdate(); // GH-90000
        }

        PolicyEvalResult result = runPromise(() -> // GH-90000
                engine.evaluate("tenant-disabled", "tool_execution_policy", Map.of())); // GH-90000

        // Disabled rule → no active rules → open-world default ALLOW
        assertThat(result.allowed()).isTrue(); // GH-90000
    }
}
