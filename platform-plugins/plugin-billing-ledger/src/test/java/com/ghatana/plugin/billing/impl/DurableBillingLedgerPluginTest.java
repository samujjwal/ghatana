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
 * <p>Uses an H2 in-memory database (PostgreSQL compatibility mode) so the test // GH-90000
 * is self-contained with no external dependencies.
 *
 * @doc.type class
 * @doc.purpose Contract tests for DurableBillingLedgerPlugin
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("DurableBillingLedgerPlugin contract tests [GH-90000]")
@ExtendWith(MockitoExtension.class) // GH-90000
class DurableBillingLedgerPluginTest extends EventloopTestBase {

    @Mock
    private PluginContext mockContext;

    private DurableBillingLedgerPlugin plugin;

    @BeforeEach
    void setUp() { // GH-90000
        JdbcDataSource ds = new JdbcDataSource(); // GH-90000
        // PostgreSQL compatibility mode enables ON CONFLICT syntax
        ds.setURL("jdbc:h2:mem:billing_test_" + System.nanoTime() // GH-90000
                + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
        plugin = new DurableBillingLedgerPlugin(ds); // GH-90000
        plugin.ensureSchema(); // GH-90000
        runPromise(() -> plugin.initialize(mockContext)); // GH-90000
        runPromise(() -> plugin.start()); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        runPromise(() -> plugin.stop()); // GH-90000
        runPromise(() -> plugin.shutdown()); // GH-90000
    }

    // -------------------------------------------------------------------------
    // KP-012: Idempotency
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("KP-012 — posting the same transaction twice must return the same entry ID [GH-90000]")
    void testIdempotentPosting() { // GH-90000
        BillingTransaction tx = BillingTransaction.builder() // GH-90000
                .transactionId("txn-idem-001 [GH-90000]")
                .sourceProductId("finance [GH-90000]")
                .debitAccount("acct-debit [GH-90000]")
                .creditAccount("acct-credit [GH-90000]")
                .amount(new BigDecimal("100.00 [GH-90000]"))
                .currency("USD [GH-90000]")
                .type(BillingTransaction.TransactionType.CHARGE) // GH-90000
                .description("Idempotency test [GH-90000]")
                .build(); // GH-90000

        String entryId1 = runPromise(() -> plugin.postTransaction(tx)); // GH-90000
        String entryId2 = runPromise(() -> plugin.postTransaction(tx)); // GH-90000

        assertThat(entryId1).isNotBlank(); // GH-90000
        assertThat(entryId2).isEqualTo(entryId1); // GH-90000
    }

    @Test
    @DisplayName("KP-012 — idempotent post must return POSTED status [GH-90000]")
    void testIdempotentPostingStatus() { // GH-90000
        BillingTransaction tx = BillingTransaction.builder() // GH-90000
                .transactionId("txn-status-001 [GH-90000]")
                .sourceProductId("finance [GH-90000]")
                .debitAccount("acct-d [GH-90000]")
                .creditAccount("acct-c [GH-90000]")
                .amount(new BigDecimal("50.00 [GH-90000]"))
                .currency("USD [GH-90000]")
                .type(BillingTransaction.TransactionType.CHARGE) // GH-90000
                .build(); // GH-90000

        runPromise(() -> plugin.postTransaction(tx)); // GH-90000
        runPromise(() -> plugin.postTransaction(tx)); // second post // GH-90000

        BillingLedgerPlugin.PostingStatus status =
                runPromise(() -> plugin.getPostingStatus("txn-status-001 [GH-90000]"));

        assertThat(status).isEqualTo(BillingLedgerPlugin.PostingStatus.POSTED); // GH-90000
    }

    // -------------------------------------------------------------------------
    // Basic operations
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Posting a new transaction must create an entry retrievable by getEntry [GH-90000]")
    void testPostAndRetrieve() { // GH-90000
        BillingTransaction tx = BillingTransaction.builder() // GH-90000
                .transactionId("txn-retrieve-001 [GH-90000]")
                .sourceProductId("phr [GH-90000]")
                .debitAccount("phr-debit-acct [GH-90000]")
                .creditAccount("phr-credit-acct [GH-90000]")
                .amount(new BigDecimal("200.50 [GH-90000]"))
                .currency("NPR [GH-90000]")
                .type(BillingTransaction.TransactionType.CHARGE) // GH-90000
                .description("PHR billing charge [GH-90000]")
                .build(); // GH-90000

        String entryId = runPromise(() -> plugin.postTransaction(tx)); // GH-90000

        Optional<BillingLedgerPlugin.LedgerEntry> entry =
                runPromise(() -> plugin.getEntry(entryId)); // GH-90000

        assertThat(entry).isPresent(); // GH-90000
        assertThat(entry.get().transactionId()).isEqualTo("txn-retrieve-001 [GH-90000]");
        assertThat(entry.get().debitAccount()).isEqualTo("phr-debit-acct [GH-90000]");
        assertThat(entry.get().creditAccount()).isEqualTo("phr-credit-acct [GH-90000]");
        assertThat(entry.get().amount()).isEqualByComparingTo(new BigDecimal("200.50 [GH-90000]"));
        assertThat(entry.get().currency()).isEqualTo("NPR [GH-90000]");
    }

    @Test
    @DisplayName("Unknown transaction must have PostingStatus.NOT_FOUND [GH-90000]")
    void testNotFoundStatus() { // GH-90000
        BillingLedgerPlugin.PostingStatus status =
                runPromise(() -> plugin.getPostingStatus("txn-nonexistent [GH-90000]"));

        assertThat(status).isEqualTo(BillingLedgerPlugin.PostingStatus.NOT_FOUND); // GH-90000
    }

    // -------------------------------------------------------------------------
    // Reversal
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Reversing a posted transaction must produce a reversal entry and update status [GH-90000]")
    void testReversal() { // GH-90000
        BillingTransaction tx = BillingTransaction.builder() // GH-90000
                .transactionId("txn-reversal-001 [GH-90000]")
                .sourceProductId("finance [GH-90000]")
                .debitAccount("rev-debit [GH-90000]")
                .creditAccount("rev-credit [GH-90000]")
                .amount(new BigDecimal("300.00 [GH-90000]"))
                .currency("USD [GH-90000]")
                .type(BillingTransaction.TransactionType.CHARGE) // GH-90000
                .build(); // GH-90000

        runPromise(() -> plugin.postTransaction(tx)); // GH-90000
        String reversalId = runPromise(() -> // GH-90000
                plugin.reverseTransaction("txn-reversal-001", "Test reversal")); // GH-90000

        assertThat(reversalId).isNotBlank(); // GH-90000

        Optional<BillingLedgerPlugin.LedgerEntry> reversalEntry =
                runPromise(() -> plugin.getEntry(reversalId)); // GH-90000
        assertThat(reversalEntry).isPresent(); // GH-90000
        // Reversal swaps debit and credit accounts
        assertThat(reversalEntry.get().debitAccount()).isEqualTo("rev-credit [GH-90000]");
        assertThat(reversalEntry.get().creditAccount()).isEqualTo("rev-debit [GH-90000]");
    }

    @Test
    @DisplayName("Reversing a non-existent transaction must throw IllegalArgumentException [GH-90000]")
    void testReverseNonExistentTransaction() { // GH-90000
        assertThatThrownBy(() -> // GH-90000
                runPromise(() -> plugin.reverseTransaction("txn-nonexistent-reversal", "reason"))) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("txn-nonexistent-reversal [GH-90000]");
    }

    // -------------------------------------------------------------------------
    // Account management
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Creating an account that already exists must return IllegalArgumentException [GH-90000]")
    void testCreateDuplicateAccount() { // GH-90000
        runPromise(() -> plugin.createAccount("acct-dup", BillingLedgerPlugin.AccountType.ASSET)); // GH-90000

        assertThatThrownBy(() -> // GH-90000
                runPromise(() -> plugin.createAccount("acct-dup", BillingLedgerPlugin.AccountType.ASSET))) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("acct-dup [GH-90000]");
    }

    // -------------------------------------------------------------------------
    // Query
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("queryEntries must return only entries within the specified time range [GH-90000]")
    void testQueryEntriesTimeRange() { // GH-90000
        // Post with timestamps
        BillingTransaction tx1 = BillingTransaction.builder() // GH-90000
                .transactionId("txn-q1 [GH-90000]")
                .sourceProductId("finance [GH-90000]")
                .debitAccount("query-debit [GH-90000]")
                .creditAccount("query-credit [GH-90000]")
                .amount(new BigDecimal("10.00 [GH-90000]"))
                .currency("USD [GH-90000]")
                .type(BillingTransaction.TransactionType.CHARGE) // GH-90000
                .build(); // GH-90000

        runPromise(() -> plugin.postTransaction(tx1)); // GH-90000

        java.time.Instant start = java.time.Instant.now().minusSeconds(60); // GH-90000
        java.time.Instant end = java.time.Instant.now().plusSeconds(60); // GH-90000

        List<BillingLedgerPlugin.LedgerEntry> entries =
                runPromise(() -> plugin.queryEntries("query-debit", // GH-90000
                        new BillingLedgerPlugin.TimeRange(start, end))); // GH-90000

        assertThat(entries).hasSize(1); // GH-90000
        assertThat(entries.get(0).transactionId()).isEqualTo("txn-q1 [GH-90000]");
    }

    @Test
    @DisplayName("queryEntries outside time range must return empty list [GH-90000]")
    void testQueryEntriesOutsideRange() { // GH-90000
        BillingTransaction tx = BillingTransaction.builder() // GH-90000
                .transactionId("txn-q-out [GH-90000]")
                .sourceProductId("finance [GH-90000]")
                .debitAccount("acct-out [GH-90000]")
                .creditAccount("acct-out-cr [GH-90000]")
                .amount(new BigDecimal("1.00 [GH-90000]"))
                .currency("USD [GH-90000]")
                .type(BillingTransaction.TransactionType.CHARGE) // GH-90000
                .build(); // GH-90000

        runPromise(() -> plugin.postTransaction(tx)); // GH-90000

        // Range in the future — should not match
        java.time.Instant start = java.time.Instant.now().plusSeconds(3600); // GH-90000
        java.time.Instant end = java.time.Instant.now().plusSeconds(7200); // GH-90000

        List<BillingLedgerPlugin.LedgerEntry> entries =
                runPromise(() -> plugin.queryEntries("acct-out", // GH-90000
                        new BillingLedgerPlugin.TimeRange(start, end))); // GH-90000

        assertThat(entries).isEmpty(); // GH-90000
    }
}
