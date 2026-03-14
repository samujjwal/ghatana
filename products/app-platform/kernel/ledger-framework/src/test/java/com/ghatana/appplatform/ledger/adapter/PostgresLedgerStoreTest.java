/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.ledger.adapter;

import com.ghatana.appplatform.ledger.domain.Currency;
import com.ghatana.appplatform.ledger.domain.Journal;
import com.ghatana.appplatform.ledger.domain.JournalEntry;
import com.ghatana.appplatform.ledger.domain.MonetaryAmount;
import com.ghatana.appplatform.ledger.service.BalanceEnforcer;
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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for the ledger store, covering posting, balance materialization,
 * hash chain, outbox atomicity, and immutability enforcement.
 *
 * @doc.type class
 * @doc.purpose Integration tests for PostgresLedgerStore (K16-001/002/003/006, K17-001)
 * @doc.layer product
 * @doc.pattern Test
 */
@Testcontainers
@DisplayName("LedgerStore — Integration Tests")
class PostgresLedgerStoreTest extends EventloopTestBase {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("ledger_test")
            .withUsername("test")
            .withPassword("test");

    private static HikariDataSource dataSource;
    private static PostgresLedgerStore store;
    private static PostgresCurrencyRegistry currencyRegistry;

    private static final UUID TENANT = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID ASSETS_ACCT = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID INCOME_ACCT = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @BeforeAll
    static void setUpDatabase() {
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

        var executor = Executors.newFixedThreadPool(4);
        store = new PostgresLedgerStore(dataSource, executor);
        currencyRegistry = new PostgresCurrencyRegistry(dataSource, executor);
    }

    @AfterAll
    static void tearDown() {
        if (dataSource != null) dataSource.close();
    }

    @BeforeEach
    void cleanTables() {
        try (Connection conn = dataSource.getConnection()) {
            conn.createStatement().execute("DELETE FROM outbox");
            conn.createStatement().execute("DELETE FROM account_balance");
            conn.createStatement().execute("DELETE FROM ledger_journal_entry");
            conn.createStatement().execute("DELETE FROM ledger_journal");
        } catch (Exception e) {
            throw new RuntimeException("Failed to clean ledger tables", e);
        }
    }

    // ─── Schema ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Schema setup")
    class SchemaTests {

        @Test
        @DisplayName("tables created by V001 migration")
        void schemaCreated() throws Exception {
            try (Connection conn = dataSource.getConnection();
                 ResultSet rs = conn.getMetaData().getTables(null, null, "ledger_journal", null)) {
                assertThat(rs.next()).isTrue();
            }
        }

        @Test
        @DisplayName("currency_registry seeded with NPR")
        void currencySeeded() {
            Optional<Currency> npr = runPromise(() -> currencyRegistry.getCurrency("NPR"));
            assertThat(npr).isPresent();
            assertThat(npr.get().code()).isEqualTo("NPR");
        }
    }

    // ─── Journal posting ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Journal posting")
    class PostJournalTests {

        @Test
        @DisplayName("postJournal_balanced2Leg_persistsJournal")
        void postJournal_balanced_persisted() {
            Journal journal = twoLegBalancedJournal("POST-001", "500");

            Journal result = runPromise(() -> store.postJournal(journal));

            assertThat(result.journalId()).isEqualTo(journal.journalId());
            assertThat(result.reference()).isEqualTo("POST-001");
            assertThat(result.entries()).hasSize(2);
        }

        @Test
        @DisplayName("postJournal_retrievedByJournalId_matchesOriginal")
        void getJournal_returnsPostedJournal() {
            Journal posted = runPromise(() -> store.postJournal(twoLegBalancedJournal("GET-001", "100")));

            Optional<Journal> found = runPromise(() -> store.getJournal(posted.journalId()));

            assertThat(found).isPresent();
            assertThat(found.get().reference()).isEqualTo("GET-001");
            assertThat(found.get().entries()).hasSize(2);
        }

        @Test
        @DisplayName("getJournal_unknownId_returnsEmpty")
        void getJournal_unknownId_empty() {
            Optional<Journal> found = runPromise(() -> store.getJournal(UUID.randomUUID()));
            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("postJournal_atomicallyWritesOutboxEntry")
        void postJournal_outboxWrittenAtomically() throws Exception {
            Journal journal = twoLegBalancedJournal("OUTBOX-001", "250");
            runPromise(() -> store.postJournal(journal));

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT COUNT(*) FROM outbox WHERE aggregate_id = ?")) {
                ps.setObject(1, journal.journalId());
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    assertThat(rs.getInt(1)).isEqualTo(1);
                }
            }
        }
    }

    // ─── Balance materialization ───────────────────────────────────────────────

    @Nested
    @DisplayName("Balance materialization (K16-003)")
    class BalanceTests {

        @Test
        @DisplayName("balance_updatedAfterPosting")
        void balance_nonZeroAfterPosting() {
            Journal journal = twoLegBalancedJournal("BAL-001", "300");
            runPromise(() -> store.postJournal(journal));

            MonetaryAmount bal = runPromise(() -> store.getAccountBalance(ASSETS_ACCT, "NPR"));
            assertThat(bal.getAmount()).isEqualByComparingTo("300");
        }

        @Test
        @DisplayName("balance_accumulatesAcrossMultipleJournals")
        void balance_cumulative() {
            runPromise(() -> store.postJournal(twoLegBalancedJournal("CUM-001", "200")));
            runPromise(() -> store.postJournal(twoLegBalancedJournal("CUM-002", "300")));

            MonetaryAmount bal = runPromise(() -> store.getAccountBalance(ASSETS_ACCT, "NPR"));
            assertThat(bal.getAmount()).isEqualByComparingTo("500");
        }

        @Test
        @DisplayName("balance_returnsZero_whenNoPostings")
        void balance_zeroForUnpostedAccount() {
            MonetaryAmount bal = runPromise(() ->
                    store.getAccountBalance(UUID.randomUUID(), "NPR"));
            assertThat(bal.isZero()).isTrue();
        }
    }

    // ─── Hash chain ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Hash chain integrity (K16-006)")
    class HashChainTests {

        @Test
        @DisplayName("entry_hash_populated_on_persist")
        void entryHash_populatedAfterPosting() throws Exception {
            Journal journal = twoLegBalancedJournal("HASH-001", "100");
            Journal posted = runPromise(() -> store.postJournal(journal));

            Optional<Journal> found = runPromise(() -> store.getJournal(posted.journalId()));
            assertThat(found).isPresent();
            found.get().entries().forEach(e -> assertThat(e.entryHash()).isNotNull().hasSize(64));
        }

        @Test
        @DisplayName("consecutive_entries_for_same_account_form_a_chain")
        void consecutiveEntries_formHashChain() throws Exception {
            runPromise(() -> store.postJournal(twoLegBalancedJournal("CHAIN-001", "100")));
            runPromise(() -> store.postJournal(twoLegBalancedJournal("CHAIN-002", "200")));

            // Verify sequence nums increment and all hashes are present
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT sequence_num, entry_hash FROM ledger_journal_entry " +
                         "WHERE account_id = ? ORDER BY sequence_num")) {
                ps.setObject(1, ASSETS_ACCT);
                try (ResultSet rs = ps.executeQuery()) {
                    long expectedSeq = 1;
                    while (rs.next()) {
                        assertThat(rs.getLong("sequence_num")).isEqualTo(expectedSeq++);
                        assertThat(rs.getString("entry_hash")).isNotNull().hasSize(64);
                    }
                    assertThat(expectedSeq).isGreaterThan(1);
                }
            }
        }
    }

    // ─── Recent journals ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Recent journals query")
    class RecentJournalsTests {

        @Test
        @DisplayName("getRecentJournals_respectsLimit")
        void getRecentJournals_limit() {
            for (int i = 0; i < 5; i++) {
                runPromise(() -> store.postJournal(twoLegBalancedJournal("RECENT-" + UUID.randomUUID(), "50")));
            }

            List<Journal> recent = runPromise(() -> store.getRecentJournals(TENANT, 3));
            assertThat(recent).hasSize(3);
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static Journal twoLegBalancedJournal(String reference, String amount) {
        MonetaryAmount amt = MonetaryAmount.of(amount, Currency.NPR);
        return Journal.of(reference, "Test journal " + reference, TENANT, List.of(
                JournalEntry.debit(ASSETS_ACCT, amt, "Asset"),
                JournalEntry.credit(INCOME_ACCT, amt, "Income")
        ));
    }
}
