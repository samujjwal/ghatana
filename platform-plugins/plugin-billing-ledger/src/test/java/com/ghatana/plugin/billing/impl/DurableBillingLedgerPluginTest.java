package com.ghatana.plugin.billing.impl;

import com.ghatana.platform.plugin.PluginContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.plugin.billing.BillingLedgerPlugin;
import com.ghatana.plugin.billing.BillingTransaction;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Contract tests for {@link DurableBillingLedgerPlugin}.
 *
 * <p>Covers:</p>
 * <ul>
 *   <li>KP-012: idempotency — posting the same transaction twice must return the same entry ID</li>
 *   <li>Double-entry: debit/credit accounts updated symmetrically</li>
 *   <li>Reversal: reversed entries get PostingStatus.REVERSED</li>
 *   <li>Query: entries within a time range are returned correctly</li>
 * </ul>
 *
 * <p>Uses an H2 in-memory database (PostgreSQL compatibility mode) so the test
 * is self-contained with no external dependencies.
 *
 * @doc.type class
 * @doc.purpose Contract tests for DurableBillingLedgerPlugin
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("DurableBillingLedgerPlugin contract tests")
@ExtendWith(MockitoExtension.class)
class DurableBillingLedgerPluginTest extends EventloopTestBase {

    @Mock
    private PluginContext mockContext;

    private DurableBillingLedgerPlugin plugin;

    @BeforeEach
    void setUp() {
        JdbcDataSource ds = new JdbcDataSource();
        // PostgreSQL compatibility mode enables ON CONFLICT syntax
        ds.setURL("jdbc:h2:mem:billing_test_" + System.nanoTime()
                + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
        plugin = new DurableBillingLedgerPlugin(ds);
        plugin.ensureSchema();
        runPromise(() -> plugin.initialize(mockContext));
        runPromise(() -> plugin.start());
    }

    @AfterEach
    void tearDown() {
        runPromise(() -> plugin.stop());
        runPromise(() -> plugin.shutdown());
    }

    // -------------------------------------------------------------------------
    // KP-012: Idempotency
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("KP-012 — posting the same transaction twice must return the same entry ID")
    void testIdempotentPosting() {
        BillingTransaction tx = BillingTransaction.builder()
                .transactionId("txn-idem-001")
                .sourceProductId("finance")
                .debitAccount("acct-debit")
                .creditAccount("acct-credit")
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .type(BillingTransaction.TransactionType.CHARGE)
                .description("Idempotency test")
                .build();

        String entryId1 = runPromise(() -> plugin.postTransaction(tx));
        String entryId2 = runPromise(() -> plugin.postTransaction(tx));

        assertThat(entryId1).isNotBlank();
        assertThat(entryId2).isEqualTo(entryId1);
    }

    @Test
    @DisplayName("KP-012 — idempotent post must return POSTED status")
    void testIdempotentPostingStatus() {
        BillingTransaction tx = BillingTransaction.builder()
                .transactionId("txn-status-001")
                .sourceProductId("finance")
                .debitAccount("acct-d")
                .creditAccount("acct-c")
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .type(BillingTransaction.TransactionType.CHARGE)
                .build();

        runPromise(() -> plugin.postTransaction(tx));
        runPromise(() -> plugin.postTransaction(tx)); // second post

        BillingLedgerPlugin.PostingStatus status =
                runPromise(() -> plugin.getPostingStatus("txn-status-001"));

        assertThat(status).isEqualTo(BillingLedgerPlugin.PostingStatus.POSTED);
    }

    // -------------------------------------------------------------------------
    // Basic operations
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Posting a new transaction must create an entry retrievable by getEntry")
    void testPostAndRetrieve() {
        BillingTransaction tx = BillingTransaction.builder()
                .transactionId("txn-retrieve-001")
                .sourceProductId("phr")
                .debitAccount("phr-debit-acct")
                .creditAccount("phr-credit-acct")
                .amount(new BigDecimal("200.50"))
                .currency("NPR")
                .type(BillingTransaction.TransactionType.CHARGE)
                .description("PHR billing charge")
                .build();

        String entryId = runPromise(() -> plugin.postTransaction(tx));

        Optional<BillingLedgerPlugin.LedgerEntry> entry =
                runPromise(() -> plugin.getEntry(entryId));

        assertThat(entry).isPresent();
        assertThat(entry.get().transactionId()).isEqualTo("txn-retrieve-001");
        assertThat(entry.get().debitAccount()).isEqualTo("phr-debit-acct");
        assertThat(entry.get().creditAccount()).isEqualTo("phr-credit-acct");
        assertThat(entry.get().amount()).isEqualByComparingTo(new BigDecimal("200.50"));
        assertThat(entry.get().currency()).isEqualTo("NPR");
    }

    @Test
    @DisplayName("Unknown transaction must have PostingStatus.NOT_FOUND")
    void testNotFoundStatus() {
        BillingLedgerPlugin.PostingStatus status =
                runPromise(() -> plugin.getPostingStatus("txn-nonexistent"));

        assertThat(status).isEqualTo(BillingLedgerPlugin.PostingStatus.NOT_FOUND);
    }

    // -------------------------------------------------------------------------
    // Reversal
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Reversing a posted transaction must produce a reversal entry and update status")
    void testReversal() {
        BillingTransaction tx = BillingTransaction.builder()
                .transactionId("txn-reversal-001")
                .sourceProductId("finance")
                .debitAccount("rev-debit")
                .creditAccount("rev-credit")
                .amount(new BigDecimal("300.00"))
                .currency("USD")
                .type(BillingTransaction.TransactionType.CHARGE)
                .build();

        runPromise(() -> plugin.postTransaction(tx));
        String reversalId = runPromise(() ->
                plugin.reverseTransaction("txn-reversal-001", "Test reversal"));

        assertThat(reversalId).isNotBlank();

        Optional<BillingLedgerPlugin.LedgerEntry> reversalEntry =
                runPromise(() -> plugin.getEntry(reversalId));
        assertThat(reversalEntry).isPresent();
        // Reversal swaps debit and credit accounts
        assertThat(reversalEntry.get().debitAccount()).isEqualTo("rev-credit");
        assertThat(reversalEntry.get().creditAccount()).isEqualTo("rev-debit");
    }

    @Test
    @DisplayName("Reversing a non-existent transaction must throw IllegalArgumentException")
    void testReverseNonExistentTransaction() {
        assertThatThrownBy(() ->
                runPromise(() -> plugin.reverseTransaction("txn-nonexistent-reversal", "reason")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("txn-nonexistent-reversal");
    }

    // -------------------------------------------------------------------------
    // Account management
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Creating an account that already exists must return IllegalArgumentException")
    void testCreateDuplicateAccount() {
        runPromise(() -> plugin.createAccount("acct-dup", BillingLedgerPlugin.AccountType.ASSET));

        assertThatThrownBy(() ->
                runPromise(() -> plugin.createAccount("acct-dup", BillingLedgerPlugin.AccountType.ASSET)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("acct-dup");
    }

    // -------------------------------------------------------------------------
    // Query
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("queryEntries must return only entries within the specified time range")
    void testQueryEntriesTimeRange() {
        // Post with timestamps
        BillingTransaction tx1 = BillingTransaction.builder()
                .transactionId("txn-q1")
                .sourceProductId("finance")
                .debitAccount("query-debit")
                .creditAccount("query-credit")
                .amount(new BigDecimal("10.00"))
                .currency("USD")
                .type(BillingTransaction.TransactionType.CHARGE)
                .build();

        runPromise(() -> plugin.postTransaction(tx1));

        java.time.Instant start = java.time.Instant.now().minusSeconds(60);
        java.time.Instant end = java.time.Instant.now().plusSeconds(60);

        List<BillingLedgerPlugin.LedgerEntry> entries =
                runPromise(() -> plugin.queryEntries("query-debit",
                        new BillingLedgerPlugin.TimeRange(start, end)));

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).transactionId()).isEqualTo("txn-q1");
    }

    @Test
    @DisplayName("queryEntries outside time range must return empty list")
    void testQueryEntriesOutsideRange() {
        BillingTransaction tx = BillingTransaction.builder()
                .transactionId("txn-q-out")
                .sourceProductId("finance")
                .debitAccount("acct-out")
                .creditAccount("acct-out-cr")
                .amount(new BigDecimal("1.00"))
                .currency("USD")
                .type(BillingTransaction.TransactionType.CHARGE)
                .build();

        runPromise(() -> plugin.postTransaction(tx));

        // Range in the future — should not match
        java.time.Instant start = java.time.Instant.now().plusSeconds(3600);
        java.time.Instant end = java.time.Instant.now().plusSeconds(7200);

        List<BillingLedgerPlugin.LedgerEntry> entries =
                runPromise(() -> plugin.queryEntries("acct-out",
                        new BillingLedgerPlugin.TimeRange(start, end)));

        assertThat(entries).isEmpty();
    }
}
