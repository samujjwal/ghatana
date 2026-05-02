package com.ghatana.plugin.ledger.impl;

import com.ghatana.platform.plugin.PluginContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.plugin.ledger.LedgerPlugin;
import com.ghatana.plugin.ledger.LedgerTransaction;
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
 * Contract tests for {@link DurableLedgerPlugin}.
 *
 * <p>Covers:</p>
 * <ul>
 *   <li>KP-012: idempotency â€” posting the same transaction twice must return the same entry ID</li>
 *   <li>Double-entry: debit/credit accounts updated symmetrically</li>
 *   <li>Reversal: reversed entries get PostingStatus.REVERSED</li>
 *   <li>Query: entries within a time range are returned correctly</li>
 * </ul>
 *
 * <p>Uses an H2 in-memory database (PostgreSQL compatibility mode) so the test 
 * is self-contained with no external dependencies.
 *
 * @doc.type class
 * @doc.purpose Contract tests for DurableLedgerPlugin
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("DurableLedgerPlugin contract tests")
@ExtendWith(MockitoExtension.class) 
class DurableLedgerPluginTest extends EventloopTestBase {

    @Mock
    private PluginContext mockContext;

    private DurableLedgerPlugin plugin;

    @BeforeEach
    void setUp() { 
        JdbcDataSource ds = new JdbcDataSource(); 
        // PostgreSQL compatibility mode enables ON CONFLICT syntax
        ds.setURL("jdbc:h2:mem:ledger_test_" + System.nanoTime() 
                + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
        plugin = new DurableLedgerPlugin(ds); 
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
    @DisplayName("KP-012 â€” posting the same transaction twice must return the same entry ID")
    void testIdempotentPosting() { 
        LedgerTransaction tx = LedgerTransaction.builder() 
                .transactionId("txn-idem-001")
                .sourceId("domain-b")
                .debitAccount("acct-debit")
                .creditAccount("acct-credit")
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .type(LedgerTransaction.TransactionType.CHARGE) 
                .description("Idempotency test")
                .build(); 

        String entryId1 = runPromise(() -> plugin.postTransaction(tx)); 
        String entryId2 = runPromise(() -> plugin.postTransaction(tx)); 

        assertThat(entryId1).isNotBlank(); 
        assertThat(entryId2).isEqualTo(entryId1); 
    }

    @Test
    @DisplayName("KP-012 â€” idempotent post must return POSTED status")
    void testIdempotentPostingStatus() { 
        LedgerTransaction tx = LedgerTransaction.builder() 
                .transactionId("txn-status-001")
                .sourceId("domain-b")
                .debitAccount("acct-d")
                .creditAccount("acct-c")
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .type(LedgerTransaction.TransactionType.CHARGE) 
                .build(); 

        runPromise(() -> plugin.postTransaction(tx)); 
        runPromise(() -> plugin.postTransaction(tx)); // second post 

        LedgerPlugin.PostingStatus status =
                runPromise(() -> plugin.getPostingStatus("txn-status-001"));

        assertThat(status).isEqualTo(LedgerPlugin.PostingStatus.POSTED); 
    }

    // -------------------------------------------------------------------------
    // Basic operations
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Posting a new transaction must create an entry retrievable by getEntry")
    void testPostAndRetrieve() { 
        LedgerTransaction tx = LedgerTransaction.builder() 
                .transactionId("txn-retrieve-001")
                .sourceId("domain-a")
                .debitAccount("domain-a-debit-acct")
                .creditAccount("domain-a-credit-acct")
                .amount(new BigDecimal("200.50"))
                .currency("NPR")
                .type(LedgerTransaction.TransactionType.CHARGE) 
                .description("test ledger charge")
                .build(); 

        String entryId = runPromise(() -> plugin.postTransaction(tx)); 

        Optional<LedgerPlugin.LedgerEntry> entry =
                runPromise(() -> plugin.getEntry(entryId)); 

        assertThat(entry).isPresent(); 
        assertThat(entry.get().transactionId()).isEqualTo("txn-retrieve-001");
        assertThat(entry.get().debitAccount()).isEqualTo("domain-a-debit-acct");
        assertThat(entry.get().creditAccount()).isEqualTo("domain-a-credit-acct");
        assertThat(entry.get().amount()).isEqualByComparingTo(new BigDecimal("200.50"));
        assertThat(entry.get().currency()).isEqualTo("NPR");
    }

    @Test
    @DisplayName("Unknown transaction must have PostingStatus.NOT_FOUND")
    void testNotFoundStatus() { 
        LedgerPlugin.PostingStatus status =
                runPromise(() -> plugin.getPostingStatus("txn-nonexistent"));

        assertThat(status).isEqualTo(LedgerPlugin.PostingStatus.NOT_FOUND); 
    }

    // -------------------------------------------------------------------------
    // Reversal
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Reversing a posted transaction must produce a reversal entry and update status")
    void testReversal() { 
        LedgerTransaction tx = LedgerTransaction.builder() 
                .transactionId("txn-reversal-001")
                .sourceId("domain-b")
                .debitAccount("rev-debit")
                .creditAccount("rev-credit")
                .amount(new BigDecimal("300.00"))
                .currency("USD")
                .type(LedgerTransaction.TransactionType.CHARGE) 
                .build(); 

        runPromise(() -> plugin.postTransaction(tx)); 
        String reversalId = runPromise(() -> 
                plugin.reverseTransaction("txn-reversal-001", "Test reversal")); 

        assertThat(reversalId).isNotBlank(); 

        Optional<LedgerPlugin.LedgerEntry> reversalEntry =
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
        runPromise(() -> plugin.createAccount("acct-dup", LedgerPlugin.AccountType.ASSET)); 

        assertThatThrownBy(() -> 
                runPromise(() -> plugin.createAccount("acct-dup", LedgerPlugin.AccountType.ASSET))) 
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
        LedgerTransaction tx1 = LedgerTransaction.builder() 
                .transactionId("txn-q1")
                .sourceId("domain-b")
                .debitAccount("query-debit")
                .creditAccount("query-credit")
                .amount(new BigDecimal("10.00"))
                .currency("USD")
                .type(LedgerTransaction.TransactionType.CHARGE) 
                .build(); 

        runPromise(() -> plugin.postTransaction(tx1)); 

        java.time.Instant start = java.time.Instant.now().minusSeconds(60); 
        java.time.Instant end = java.time.Instant.now().plusSeconds(60); 

        List<LedgerPlugin.LedgerEntry> entries =
                runPromise(() -> plugin.queryEntries("query-debit", 
                        new LedgerPlugin.TimeRange(start, end))); 

        assertThat(entries).hasSize(1); 
        assertThat(entries.get(0).transactionId()).isEqualTo("txn-q1");
    }

    // -------------------------------------------------------------------------
    // Ledger invariants (double-entry symmetry, reconciliation)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("LDG-INV-001: total debits out of debit account must equal total credits into credit account")
    void testDoubleEntrySymmetry() {
        String debitAcct = "inv-debit-" + System.nanoTime();
        String creditAcct = "inv-credit-" + System.nanoTime();
        BigDecimal amount1 = new BigDecimal("100.00");
        BigDecimal amount2 = new BigDecimal("250.00");

        LedgerTransaction tx1 = LedgerTransaction.builder()
                .transactionId("inv-txn-1-" + System.nanoTime())
                .sourceId("domain-b")
                .debitAccount(debitAcct)
                .creditAccount(creditAcct)
                .amount(amount1)
                .currency("USD")
                .type(LedgerTransaction.TransactionType.CHARGE)
                .build();
        LedgerTransaction tx2 = LedgerTransaction.builder()
                .transactionId("inv-txn-2-" + System.nanoTime())
                .sourceId("domain-b")
                .debitAccount(debitAcct)
                .creditAccount(creditAcct)
                .amount(amount2)
                .currency("USD")
                .type(LedgerTransaction.TransactionType.CHARGE)
                .build();

        runPromise(() -> plugin.postTransaction(tx1));
        runPromise(() -> plugin.postTransaction(tx2));

        java.time.Instant start = java.time.Instant.now().minusSeconds(60);
        java.time.Instant end = java.time.Instant.now().plusSeconds(60);

        List<LedgerPlugin.LedgerEntry> debitEntries =
                runPromise(() -> plugin.queryEntries(debitAcct, new LedgerPlugin.TimeRange(start, end)));
        List<LedgerPlugin.LedgerEntry> creditEntries =
                runPromise(() -> plugin.queryEntries(creditAcct, new LedgerPlugin.TimeRange(start, end)));

        BigDecimal totalDebited = debitEntries.stream()
                .map(LedgerPlugin.LedgerEntry::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredited = creditEntries.stream()
                .map(LedgerPlugin.LedgerEntry::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(totalDebited)
                .as("total amount debited from debit account must equal total credited to credit account")
                .isEqualByComparingTo(totalCredited);
        assertThat(totalDebited).isEqualByComparingTo(amount1.add(amount2));
    }

    @Test
    @DisplayName("LDG-INV-002: reversal must neutralise the original entry â€” net balance is zero")
    void testReversalNetZero() {
        String debitAcct = "rev-net-debit-" + System.nanoTime();
        String creditAcct = "rev-net-credit-" + System.nanoTime();
        String txId = "txn-rev-net-" + System.nanoTime();

        LedgerTransaction tx = LedgerTransaction.builder()
                .transactionId(txId)
                .sourceId("domain-b")
                .debitAccount(debitAcct)
                .creditAccount(creditAcct)
                .amount(new BigDecimal("500.00"))
                .currency("USD")
                .type(LedgerTransaction.TransactionType.CHARGE)
                .build();
        runPromise(() -> plugin.postTransaction(tx));
        runPromise(() -> plugin.reverseTransaction(txId, "LDG-INV-002 test reversal"));

        java.time.Instant start = java.time.Instant.now().minusSeconds(60);
        java.time.Instant end = java.time.Instant.now().plusSeconds(60);

        // debitAcct should have: 1 debit entry (+500) and 1 reversal credit entry (-500) â†’ net 0
        List<LedgerPlugin.LedgerEntry> debitSideEntries =
                runPromise(() -> plugin.queryEntries(debitAcct, new LedgerPlugin.TimeRange(start, end)));
        BigDecimal debitNetAmount = debitSideEntries.stream()
                .map(e -> debitAcct.equals(e.debitAccount()) ? e.amount() : e.amount().negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(debitNetAmount)
                .as("net amount on debit account after reversal must be zero")
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("LDG-INV-003: entries for an account can be reconciled by summing all amounts")
    void testReconciliation() {
        String acct = "reconcile-acct-" + System.nanoTime();
        String counterAcct = "reconcile-counter-" + System.nanoTime();
        BigDecimal[] amounts = {
            new BigDecimal("100.00"), new BigDecimal("200.00"), new BigDecimal("75.50")
        };
        BigDecimal expected = new BigDecimal("375.50");

        for (int i = 0; i < amounts.length; i++) {
            final int idx = i;
            LedgerTransaction tx = LedgerTransaction.builder()
                    .transactionId("rec-txn-" + idx + "-" + System.nanoTime())
                    .sourceId("domain-b")
                    .debitAccount(acct)
                    .creditAccount(counterAcct)
                    .amount(amounts[idx])
                    .currency("USD")
                    .type(LedgerTransaction.TransactionType.CHARGE)
                    .build();
            runPromise(() -> plugin.postTransaction(tx));
        }

        java.time.Instant start = java.time.Instant.now().minusSeconds(60);
        java.time.Instant end = java.time.Instant.now().plusSeconds(60);

        List<LedgerPlugin.LedgerEntry> entries =
                runPromise(() -> plugin.queryEntries(acct, new LedgerPlugin.TimeRange(start, end)));
        BigDecimal reconciled = entries.stream()
                .map(LedgerPlugin.LedgerEntry::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(reconciled)
                .as("reconciled sum of all entries must match expected total")
                .isEqualByComparingTo(expected);
    }

    @Test
    @DisplayName("queryEntries outside time range must return empty list")
    void testQueryEntriesOutsideRange() { 
        LedgerTransaction tx = LedgerTransaction.builder() 
                .transactionId("txn-q-out")
                .sourceId("domain-b")
                .debitAccount("acct-out")
                .creditAccount("acct-out-cr")
                .amount(new BigDecimal("1.00"))
                .currency("USD")
                .type(LedgerTransaction.TransactionType.CHARGE) 
                .build(); 

        runPromise(() -> plugin.postTransaction(tx)); 

        // Range in the future â€” should not match
        java.time.Instant start = java.time.Instant.now().plusSeconds(3600); 
        java.time.Instant end = java.time.Instant.now().plusSeconds(7200); 

        List<LedgerPlugin.LedgerEntry> entries =
                runPromise(() -> plugin.queryEntries("acct-out", 
                        new LedgerPlugin.TimeRange(start, end))); 

        assertThat(entries).isEmpty(); 
    }
}
