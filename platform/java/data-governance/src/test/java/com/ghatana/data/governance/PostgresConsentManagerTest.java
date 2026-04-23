/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
    void setUp() throws Exception { // GH-90000
        HikariConfig config = new HikariConfig(); // GH-90000
        config.setJdbcUrl(POSTGRES.getJdbcUrl()); // GH-90000
        config.setUsername(POSTGRES.getUsername()); // GH-90000
        config.setPassword(POSTGRES.getPassword()); // GH-90000
        config.setMaximumPoolSize(5); // GH-90000
        dataSource = new HikariDataSource(config); // GH-90000

        initSchema(); // GH-90000
        manager = new PostgresConsentManager(dataSource); // GH-90000
    }

    @AfterEach
    void tearDown() throws Exception { // GH-90000
        try (Connection conn = dataSource.getConnection(); // GH-90000
             Statement stmt = conn.createStatement()) { // GH-90000
            stmt.execute("DROP TABLE IF EXISTS consent_records");
        }
        dataSource.close(); // GH-90000
    }

    @Test
    @DisplayName("recordConsent then hasConsent returns true")
    void recordConsent_thenHasConsent_returnsTrue() { // GH-90000
        runPromise(() -> manager.recordConsent("tenant-1", "subject-1", "analytics")); // GH-90000

        boolean result = runPromise(() -> manager.hasConsent("tenant-1", "subject-1", "analytics")); // GH-90000

        assertThat(result).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("hasConsent for absent record returns false")
    void hasConsent_notRecorded_returnsFalse() { // GH-90000
        boolean result = runPromise(() -> manager.hasConsent("tenant-x", "subject-x", "marketing")); // GH-90000

        assertThat(result).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("withdrawConsent after record makes hasConsent return false")
    void withdrawConsent_afterRecord_returnsFalse() { // GH-90000
        runPromise(() -> manager.recordConsent("tenant-2", "subject-2", "gdpr_data_processing")); // GH-90000
        runPromise(() -> manager.withdrawConsent("tenant-2", "subject-2", "gdpr_data_processing")); // GH-90000

        boolean result = runPromise(() -> manager.hasConsent("tenant-2", "subject-2", "gdpr_data_processing")); // GH-90000

        assertThat(result).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("enforceConsent passes when consent is present")
    void enforceConsent_withConsent_passes() { // GH-90000
        runPromise(() -> manager.recordConsent("tenant-3", "subject-3", "profiling")); // GH-90000

        assertThatCode(() -> runPromise(() -> manager.enforceConsent("tenant-3", "subject-3", "profiling"))) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
    }

    @Test
    @DisplayName("enforceConsent throws ConsentRequiredException when consent is absent")
    void enforceConsent_withoutConsent_throws() { // GH-90000
        assertThatThrownBy(() -> runPromise(() -> manager.enforceConsent("tenant-4", "subject-4", "email_marketing"))) // GH-90000
                .isInstanceOf(ConsentRequiredException.class); // GH-90000
    }

    @Test
    @DisplayName("re-recording consent after withdrawal restores consent")
    void reRecord_afterWithdrawal_restoresConsent() { // GH-90000
        runPromise(() -> manager.recordConsent("tenant-5", "subject-5", "personalization")); // GH-90000
        runPromise(() -> manager.withdrawConsent("tenant-5", "subject-5", "personalization")); // GH-90000
        runPromise(() -> manager.recordConsent("tenant-5", "subject-5", "personalization")); // GH-90000

        boolean result = runPromise(() -> manager.hasConsent("tenant-5", "subject-5", "personalization")); // GH-90000

        assertThat(result).isTrue(); // GH-90000
    }

    // ---- Schema helpers ----------------------------------------------------

    private void initSchema() throws Exception { // GH-90000
        try (Connection conn = dataSource.getConnection(); // GH-90000
             Statement stmt = conn.createStatement()) { // GH-90000
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
