/*
 * Copyright (c) 2026 Ghatana Inc. 
 * All rights reserved.
 */
package com.ghatana.data.governance;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link PostgresConsentManager} against a real
 * PostgreSQL container.
 *
 * @doc.type class
 * @doc.purpose Integration tests for PostgresConsentManager with Testcontainers
 * @doc.layer platform
 * @doc.pattern Test
 */
@Tag("integration")
@Tag("infrastructure-backed")
@Testcontainers
@DisplayName("PostgresConsentManager — integration tests")
class PostgresConsentManagerTest extends EventloopTestBase {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("aep_consent_test")
                    .withUsername("aep_test")
                    .withPassword("aep_test");

    private HikariDataSource dataSource;
    private PostgresConsentManager manager;

    @BeforeEach
    void setUp() throws Exception { 
        HikariConfig config = new HikariConfig(); 
        config.setJdbcUrl(POSTGRES.getJdbcUrl()); 
        config.setUsername(POSTGRES.getUsername()); 
        config.setPassword(POSTGRES.getPassword()); 
        config.setMaximumPoolSize(5); 
        dataSource = new HikariDataSource(config); 

        initSchema(); 
        manager = new PostgresConsentManager(dataSource); 
    }

    @AfterEach
    void tearDown() throws Exception { 
        try (Connection conn = dataSource.getConnection(); 
             Statement stmt = conn.createStatement()) { 
            stmt.execute("DROP TABLE IF EXISTS consent_records");
        }
        dataSource.close(); 
    }

    @Test
    @DisplayName("recordConsent then hasConsent returns true")
    void recordConsent_thenHasConsent_returnsTrue() { 
        runPromise(() -> manager.recordConsent("tenant-1", "subject-1", "analytics")); 

        boolean result = runPromise(() -> manager.hasConsent("tenant-1", "subject-1", "analytics")); 

        assertThat(result).isTrue(); 
    }

    @Test
    @DisplayName("hasConsent for absent record returns false")
    void hasConsent_notRecorded_returnsFalse() { 
        boolean result = runPromise(() -> manager.hasConsent("tenant-x", "subject-x", "marketing")); 

        assertThat(result).isFalse(); 
    }

    @Test
    @DisplayName("withdrawConsent after record makes hasConsent return false")
    void withdrawConsent_afterRecord_returnsFalse() { 
        runPromise(() -> manager.recordConsent("tenant-2", "subject-2", "gdpr_data_processing")); 
        runPromise(() -> manager.withdrawConsent("tenant-2", "subject-2", "gdpr_data_processing")); 

        boolean result = runPromise(() -> manager.hasConsent("tenant-2", "subject-2", "gdpr_data_processing")); 

        assertThat(result).isFalse(); 
    }

    @Test
    @DisplayName("enforceConsent passes when consent is present")
    void enforceConsent_withConsent_passes() { 
        runPromise(() -> manager.recordConsent("tenant-3", "subject-3", "profiling")); 

        assertThatCode(() -> runPromise(() -> manager.enforceConsent("tenant-3", "subject-3", "profiling"))) 
                .doesNotThrowAnyException(); 
    }

    @Test
    @DisplayName("enforceConsent throws ConsentRequiredException when consent is absent")
    void enforceConsent_withoutConsent_throws() { 
        assertThatThrownBy(() -> runPromise(() -> manager.enforceConsent("tenant-4", "subject-4", "email_marketing"))) 
                .isInstanceOf(ConsentRequiredException.class); 
    }

    @Test
    @DisplayName("re-recording consent after withdrawal restores consent")
    void reRecord_afterWithdrawal_restoresConsent() { 
        runPromise(() -> manager.recordConsent("tenant-5", "subject-5", "personalization")); 
        runPromise(() -> manager.withdrawConsent("tenant-5", "subject-5", "personalization")); 
        runPromise(() -> manager.recordConsent("tenant-5", "subject-5", "personalization")); 

        boolean result = runPromise(() -> manager.hasConsent("tenant-5", "subject-5", "personalization")); 

        assertThat(result).isTrue(); 
    }

    // ---- Schema helpers ----------------------------------------------------

    private void initSchema() throws Exception { 
        try (Connection conn = dataSource.getConnection(); 
             Statement stmt = conn.createStatement()) { 
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS consent_records (
                    id          BIGSERIAL   PRIMARY KEY,
                    tenant_id   TEXT        NOT NULL,
                    subject_id  TEXT        NOT NULL,
                    purpose     TEXT        NOT NULL,
                    granted     BOOLEAN     NOT NULL DEFAULT TRUE,
                    granted_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                    revoked_at  TIMESTAMPTZ,
                    UNIQUE (tenant_id, subject_id, purpose)
                )
                """);
        }
    }
}
