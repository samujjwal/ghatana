package com.ghatana.appplatform.audit.adapter;

import com.ghatana.appplatform.audit.chain.HashChainService;
import com.ghatana.appplatform.audit.domain.AuditEntry;
import com.ghatana.appplatform.audit.domain.AuditReceipt;
import com.ghatana.appplatform.audit.domain.ChainVerificationResult;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link PostgresAuditTrailStore}.
 *
 * @doc.type class
 * @doc.purpose Integration tests for the PostgreSQL audit trail store
 * @doc.layer product
 * @doc.pattern Test
 */
@Testcontainers
@DisplayName("AuditTrailStore — Integration Tests")
class PostgresAuditTrailStoreTest extends EventloopTestBase {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("audit_test")
        .withUsername("test")
        .withPassword("test");

    private static HikariDataSource dataSource;
    private static PostgresAuditTrailStore store;

    @BeforeAll
    static void setUpStore() {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(PG.getJdbcUrl());
        cfg.setUsername(PG.getUsername());
        cfg.setPassword(PG.getPassword());
        cfg.setMaximumPoolSize(5);
        dataSource = new HikariDataSource(cfg);

        Flyway.configure()
            .dataSource(dataSource)
            .locations("filesystem:src/main/resources/db/migration")
            .load()
            .migrate();

        store = new PostgresAuditTrailStore(
            dataSource,
            Executors.newFixedThreadPool(4),
            new HashChainService());
    }

    @AfterAll
    static void tearDown() {
        if (dataSource != null) dataSource.close();
    }

    @BeforeEach
    void cleanTable() {
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM audit_logs");
        } catch (Exception e) {
            throw new RuntimeException("Failed to clean audit_logs before test", e);
        }
    }

    private AuditEntry sampleEntry(String tenantId, String action) {
        return AuditEntry.builder()
            .action(action)
            .actor(AuditEntry.Actor.of("user-1", "TRADER"))
            .resource(AuditEntry.Resource.of("Order", "order-99"))
            .outcome(AuditEntry.Outcome.SUCCESS)
            .tenantId(tenantId)
            .timestampGregorian(Instant.now())
            .build();
    }

    // =========================================================================
    // AuditStore log creates an immutable entry
    // =========================================================================

    @Nested
    @DisplayName("AuditStore log creates an immutable entry")
    class LogTests {

        @Test
        @DisplayName("sdk_logAction_creates — log call returns a receipt with hash proof")
        void logActionCreates() {
            AuditReceipt receipt = runPromise(() ->
                store.log(sampleEntry("tenant-1", "ORDER_PLACED")));

            assertThat(receipt.auditId()).isNotBlank();
            assertThat(receipt.sequenceNumber()).isEqualTo(0L);
            assertThat(receipt.previousHash()).isEqualTo(HashChainService.GENESIS_HASH);
            assertThat(receipt.currentHash()).hasSize(64).matches("[0-9a-f]{64}");
        }

        @Test
        @DisplayName("sdk_multipleEntries_sequenceIncrements — each entry advances seq by 1")
        void multipleEntriesIncrementSequence() {
            runPromise(() -> store.log(sampleEntry("t1", "E1")));
            AuditReceipt r2 = runPromise(() -> store.log(sampleEntry("t1", "E2")));
            AuditReceipt r3 = runPromise(() -> store.log(sampleEntry("t1", "E3")));

            assertThat(r2.sequenceNumber()).isEqualTo(1L);
            assertThat(r3.sequenceNumber()).isEqualTo(2L);
        }

        @Test
        @DisplayName("sdk_chainLinked — previousHash of entry N equals currentHash of entry N-1")
        void chainIsLinked() {
            AuditReceipt r1 = runPromise(() -> store.log(sampleEntry("t2", "E1")));
            AuditReceipt r2 = runPromise(() -> store.log(sampleEntry("t2", "E2")));

            assertThat(r2.previousHash()).isEqualTo(r1.currentHash());
        }
    }

    // =========================================================================
    // Audit log immutability triggers
    // =========================================================================

    @Nested
    @DisplayName("Audit log immutability triggers")
    class ImmutabilityTests {

        @Test
        @DisplayName("update_blocked — UPDATE trigger raises exception")
        void updateBlocked() {
            runPromise(() -> store.log(sampleEntry("t3", "LOGIN")));

            assertThatThrownBy(() -> {
                try (var conn = dataSource.getConnection();
                     var ps = conn.prepareStatement(
                         "UPDATE audit_logs SET action = 'HACKED' WHERE tenant_id = 't3'")) {
                    ps.executeUpdate();
                }
            }).isInstanceOf(java.sql.SQLException.class)
              .hasMessageContaining("immutable");
        }

        @Test
        @DisplayName("delete_blocked — DELETE trigger raises exception")
        void deleteBlocked() {
            runPromise(() -> store.log(sampleEntry("t4", "LOGOUT")));

            assertThatThrownBy(() -> {
                try (var conn = dataSource.getConnection();
                     var ps = conn.prepareStatement(
                         "DELETE FROM audit_logs WHERE tenant_id = 't4'")) {
                    ps.executeUpdate();
                }
            }).isInstanceOf(java.sql.SQLException.class)
              .hasMessageContaining("immutable");
        }
    }

    // =========================================================================
    // Hash chain verification
    // =========================================================================

    @Nested
    @DisplayName("Hash chain verification")
    class ChainVerificationTests {

        @Test
        @DisplayName("chainValid_intact_true — valid chain returns ChainVerificationResult{valid=true}")
        void intactChainIsValid() {
            runPromise(() -> store.log(sampleEntry("cv-tenant", "E1")));
            runPromise(() -> store.log(sampleEntry("cv-tenant", "E2")));
            runPromise(() -> store.log(sampleEntry("cv-tenant", "E3")));

            ChainVerificationResult result = runPromise(() ->
                store.verifyChain("cv-tenant", 0L, null));

            assertThat(result.valid()).isTrue();
            assertThat(result.violations()).isEmpty();
        }

        @Test
        @DisplayName("chainValid_emptyChain — empty chain is trivially valid")
        void emptyChainIsValid() {
            ChainVerificationResult result = runPromise(() ->
                store.verifyChain("unknown-tenant", 0L, null));

            assertThat(result.valid()).isTrue();
        }
    }
}
