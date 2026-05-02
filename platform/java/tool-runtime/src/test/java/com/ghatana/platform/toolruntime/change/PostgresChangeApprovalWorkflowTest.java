/*
 * Copyright (c) 2026 Ghatana Inc. 
 * All rights reserved.
 */
package com.ghatana.platform.toolruntime.change;

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
import java.sql.Statement;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Integration tests for {@link PostgresChangeApprovalWorkflow} against a real PostgreSQL container.
 *
 * @doc.type class
 * @doc.purpose Integration tests for PostgresChangeApprovalWorkflow with Testcontainers
 * @doc.layer platform
 * @doc.pattern Test
 */
@Tag("integration")
@Testcontainers
@DisplayName("PostgresChangeApprovalWorkflow — integration tests")
class PostgresChangeApprovalWorkflowTest extends EventloopTestBase {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("aep_change_test")
                    .withUsername("aep_test")
                    .withPassword("aep_test");

    private HikariDataSource dataSource;
    private PostgresChangeApprovalWorkflow workflow;

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
                    CREATE TABLE IF NOT EXISTS change_requests (
                        change_id         UUID         PRIMARY KEY,
                        tenant_id         VARCHAR(255) NOT NULL,
                        requesting_agent  VARCHAR(512) NOT NULL,
                        change_type       VARCHAR(100) NOT NULL,
                        description       TEXT         NOT NULL,
                        metadata          JSONB        NOT NULL DEFAULT '{}',
                        status            VARCHAR(50)  NOT NULL DEFAULT 'PENDING_REVIEW',
                        risk_score        SMALLINT     NOT NULL,
                        reviewer_id       VARCHAR(512),
                        review_notes      TEXT,
                        submitted_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                        reviewed_at       TIMESTAMPTZ
                    )
                    """);
        }

        // Default threshold of 60: risk < 60 → auto-approve, risk >= 60 → pending review
        workflow = new PostgresChangeApprovalWorkflow(dataSource); 
    }

    @AfterEach
    void tearDown() throws Exception { 
        try (Connection conn = dataSource.getConnection(); 
             Statement stmt = conn.createStatement()) { 
            stmt.execute("DROP TABLE IF EXISTS change_requests");
        }
        dataSource.close(); 
    }

    @Test
    @DisplayName("low-risk change (FEATURE_FLAG, risk=20) is auto-approved")
    void submitChange_featureFlag_autoApproved() { 
        ChangeRequest req = runPromise(() -> workflow.submitChange( 
                "tenant-1", "agent-1", ChangeType.FEATURE_FLAG,
                "Enable new feature", Map.of())); 

        assertThat(req.status()).isEqualTo(ChangeStatus.APPROVED); 
        assertThat(req.riskScore()).isEqualTo(20); 
        assertThat(req.changeId()).isNotNull(); 
    }

    @Test
    @DisplayName("high-risk change (PERMISSION_GRANT, risk=80) is placed in PENDING_REVIEW")
    void submitChange_permissionGrant_pendingReview() { 
        ChangeRequest req = runPromise(() -> workflow.submitChange( 
                "tenant-2", "agent-2", ChangeType.PERMISSION_GRANT,
                "Grant admin role", Map.of("role", "admin"))); 

        assertThat(req.status()).isEqualTo(ChangeStatus.PENDING_REVIEW); 
        assertThat(req.riskScore()).isGreaterThanOrEqualTo(60); 
        assertThat(req.reviewerId()).isNull(); 
    }

    @Test
    @DisplayName("submitChange completes without error and returns a non-null changeId")
    void submitChange_returnsNonNullChangeId() { 
        ChangeRequest req = runPromise(() -> workflow.submitChange( 
                "tenant-3", "agent-3", ChangeType.CONFIG_CHANGE,
                "Change timeout setting", Map.of("timeout", "30s"))); 

        assertThat(req.changeId()).isNotNull().isNotBlank(); 
        assertThat(req.submittedAt()).isNotNull(); 
    }

    @Test
    @DisplayName("invalid autoApproveThreshold throws IllegalArgumentException")
    void constructor_invalidThreshold_throws() { 
        assertThatCode(() -> new PostgresChangeApprovalWorkflow(dataSource, Runnable::run, 150)) 
                .isInstanceOf(IllegalArgumentException.class) 
                .hasMessageContaining("[0, 100]");
    }

    @Test
    @DisplayName("multiple changes for the same tenant are stored independently")
    void submitMultipleChanges_storedIndependently() { 
        ChangeRequest req1 = runPromise(() -> workflow.submitChange( 
                "tenant-multi", "agent-1", ChangeType.FEATURE_FLAG, "Change 1", Map.of())); 
        ChangeRequest req2 = runPromise(() -> workflow.submitChange( 
                "tenant-multi", "agent-1", ChangeType.CONFIG_CHANGE, "Change 2", Map.of())); 

        assertThat(req1.changeId()).isNotEqualTo(req2.changeId()); 
    }
}
