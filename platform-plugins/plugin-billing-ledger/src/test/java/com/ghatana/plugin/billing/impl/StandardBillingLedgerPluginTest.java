package com.ghatana.plugin.billing.impl;

import com.ghatana.platform.plugin.PluginContext;
import com.ghatana.platform.plugin.PluginState;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.plugin.billing.BillingLedgerPlugin;
import com.ghatana.plugin.billing.BillingTransaction;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * @doc.type class
 * @doc.purpose Comprehensive tests for StandardBillingLedgerPlugin
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("StandardBillingLedgerPlugin Tests [GH-90000]")
@ExtendWith(MockitoExtension.class) // GH-90000
class StandardBillingLedgerPluginTest extends EventloopTestBase {

    @Mock
    private PluginContext mockContext;

    private StandardBillingLedgerPlugin billingPlugin;

    @BeforeEach
    void setUp() { // GH-90000
        billingPlugin = new StandardBillingLedgerPlugin(); // GH-90000
    }

    @Nested
    @DisplayName("Plugin Lifecycle Tests [GH-90000]")
    class LifecycleTests {

        @Test
        @DisplayName("Should initialize billing plugin with correct state [GH-90000]")
        void testInitialize() { // GH-90000
            // Given: Fresh plugin instance
            assertThat(billingPlugin.getState()).isEqualTo(PluginState.UNLOADED); // GH-90000

            // When: Initialize plugin
            Promise<Void> result = billingPlugin.initialize(mockContext); // GH-90000
            runPromise(() -> result); // GH-90000

            // Then: Plugin should be initialized
            assertThat(billingPlugin.getState()).isEqualTo(PluginState.INITIALIZED); // GH-90000
        }

        @Test
        @DisplayName("Should start billing plugin [GH-90000]")
        void testStart() { // GH-90000
            // Given: Initialized plugin
            runPromise(() -> billingPlugin.initialize(mockContext)); // GH-90000

            // When: Start plugin
            Promise<Void> result = billingPlugin.start(); // GH-90000
            runPromise(() -> result); // GH-90000

            // Then: Plugin should be started
            assertThat(billingPlugin.getState()).isEqualTo(PluginState.STARTED); // GH-90000
        }

        @Test
        @DisplayName("Should return correct metadata [GH-90000]")
        void testMetadata() { // GH-90000
            // When: Get metadata
            var metadata = billingPlugin.metadata(); // GH-90000

            // Then: Metadata should be complete
            assertThat(metadata.id()).isEqualTo("billing-ledger-plugin [GH-90000]");
            assertThat(metadata.name()).isEqualTo("Billing Ledger Plugin [GH-90000]");
            assertThat(metadata.version()).isEqualTo("1.0.0 [GH-90000]");
        }
    }

    @Nested
    @DisplayName("Account Management Tests [GH-90000]")
    class AccountManagementTests {

        @BeforeEach
        void setUp() { // GH-90000
            runPromise(() -> billingPlugin.initialize(mockContext) // GH-90000
                    .then(v -> billingPlugin.start())); // GH-90000
        }

        @Test
        @DisplayName("Should create asset account [GH-90000]")
        void testCreateAccount_Asset() { // GH-90000
            // When: Create asset account
            Promise<BillingLedgerPlugin.LedgerAccount> result =
                    billingPlugin.createAccount("ASSET_001", BillingLedgerPlugin.AccountType.ASSET); // GH-90000
            BillingLedgerPlugin.LedgerAccount account = runPromise(() -> result); // GH-90000

            // Then: Account should be created correctly
            assertThat(account.accountId()).isEqualTo("ASSET_001 [GH-90000]");
            assertThat(account.type()).isEqualTo(BillingLedgerPlugin.AccountType.ASSET); // GH-90000
            assertThat(account.currency()).isEqualTo("USD [GH-90000]");
            assertThat(account.balance()).isEqualByComparingTo(BigDecimal.ZERO); // GH-90000
            assertThat(account.createdAt()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("Should create different account types [GH-90000]")
        void testCreateAccount_DifferentTypes() { // GH-90000
            // When: Create different account types
            BillingLedgerPlugin.LedgerAccount assetAccount = runPromise(() -> // GH-90000
                    billingPlugin.createAccount("asset", BillingLedgerPlugin.AccountType.ASSET)); // GH-90000

            BillingLedgerPlugin.LedgerAccount liabilityAccount = runPromise(() -> // GH-90000
                    billingPlugin.createAccount("liability", BillingLedgerPlugin.AccountType.LIABILITY)); // GH-90000

            BillingLedgerPlugin.LedgerAccount equityAccount = runPromise(() -> // GH-90000
                    billingPlugin.createAccount("equity", BillingLedgerPlugin.AccountType.EQUITY)); // GH-90000

            // Then: All account types should be created
            assertThat(assetAccount.type()).isEqualTo(BillingLedgerPlugin.AccountType.ASSET); // GH-90000
            assertThat(liabilityAccount.type()).isEqualTo(BillingLedgerPlugin.AccountType.LIABILITY); // GH-90000
            assertThat(equityAccount.type()).isEqualTo(BillingLedgerPlugin.AccountType.EQUITY); // GH-90000
        }

        @Test
        @DisplayName("Should prevent duplicate account creation [GH-90000]")
        void testCreateAccount_Duplicate() { // GH-90000
            // Given: Account already created
            runPromise(() -> billingPlugin.createAccount("DUP_001", BillingLedgerPlugin.AccountType.ASSET)); // GH-90000

            // When: Try to create duplicate
            Promise<BillingLedgerPlugin.LedgerAccount> result =
                    billingPlugin.createAccount("DUP_001", BillingLedgerPlugin.AccountType.ASSET); // GH-90000

            // Then: Should throw exception
            assertThatThrownBy(() -> runPromise(() -> result)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("already exists [GH-90000]");
        }
    }

    @Nested
    @DisplayName("Transaction Posting Tests [GH-90000]")
    class TransactionPostingTests {

        @BeforeEach
        void setUp() { // GH-90000
            runPromise(() -> billingPlugin.initialize(mockContext) // GH-90000
                    .then(v -> billingPlugin.start()) // GH-90000
                    .then(v -> billingPlugin.createAccount("DEBIT_ACC", BillingLedgerPlugin.AccountType.ASSET)) // GH-90000
                    .then(v -> billingPlugin.createAccount("CREDIT_ACC", BillingLedgerPlugin.AccountType.LIABILITY))); // GH-90000
        }

        @Test
        @DisplayName("Should post simple transaction [GH-90000]")
        void testPostTransaction_Simple() { // GH-90000
            // Given: Transaction
            BillingTransaction transaction = BillingTransaction.builder() // GH-90000
                    .transactionId("TXN_001 [GH-90000]")
                    .sourceProductId("product1 [GH-90000]")
                    .debitAccount("DEBIT_ACC [GH-90000]")
                    .creditAccount("CREDIT_ACC [GH-90000]")
                    .amount(new BigDecimal("100.00 [GH-90000]"))
                    .currency("USD [GH-90000]")
                    .type(BillingTransaction.TransactionType.CHARGE) // GH-90000
                    .description("Test charge [GH-90000]")
                    .build(); // GH-90000

            // When: Post transaction
            Promise<String> result = billingPlugin.postTransaction(transaction); // GH-90000
            String entryId = runPromise(() -> result); // GH-90000

            // Then: Entry should be created
            assertThat(entryId).isNotNull(); // GH-90000

            // And: Entry should be retrievable
            Promise<Optional<BillingLedgerPlugin.LedgerEntry>> entryResult =
                    billingPlugin.getEntry(entryId); // GH-90000
            Optional<BillingLedgerPlugin.LedgerEntry> entry = runPromise(() -> entryResult); // GH-90000

            assertThat(entry).isPresent(); // GH-90000
            assertThat(entry.get().transactionId()).isEqualTo("TXN_001 [GH-90000]");
            assertThat(entry.get().amount()).isEqualByComparingTo(new BigDecimal("100.00 [GH-90000]"));
        }

        @Test
        @DisplayName("Should enforce idempotency for transaction posting [GH-90000]")
        void testPostTransaction_Idempotent() { // GH-90000
            // Given: Transaction
            BillingTransaction transaction = BillingTransaction.builder() // GH-90000
                    .transactionId("IDEMPOTENT_TXN [GH-90000]")
                    .sourceProductId("product1 [GH-90000]")
                    .debitAccount("DEBIT_ACC [GH-90000]")
                    .creditAccount("CREDIT_ACC [GH-90000]")
                    .amount(new BigDecimal("50.00 [GH-90000]"))
                    .currency("USD [GH-90000]")
                    .type(BillingTransaction.TransactionType.CHARGE) // GH-90000
                    .build(); // GH-90000

            // When: Post transaction twice
            String firstEntryId = runPromise(() -> billingPlugin.postTransaction(transaction)); // GH-90000
            String secondEntryId = runPromise(() -> billingPlugin.postTransaction(transaction)); // GH-90000

            // Then: Should return same entry ID
            assertThat(firstEntryId).isEqualTo(secondEntryId); // GH-90000
        }

        @Test
        @DisplayName("Should support different transaction types [GH-90000]")
        void testPostTransaction_DifferentTypes() { // GH-90000
            // Given: Different transaction types
            BillingTransaction chargeTransaction = BillingTransaction.builder() // GH-90000
                    .transactionId("CHARGE_TXN [GH-90000]")
                    .sourceProductId("product1 [GH-90000]")
                    .debitAccount("DEBIT_ACC [GH-90000]")
                    .creditAccount("CREDIT_ACC [GH-90000]")
                    .amount(new BigDecimal("100.00 [GH-90000]"))
                    .currency("USD [GH-90000]")
                    .type(BillingTransaction.TransactionType.CHARGE) // GH-90000
                    .build(); // GH-90000

            BillingTransaction refundTransaction = BillingTransaction.builder() // GH-90000
                    .transactionId("REFUND_TXN [GH-90000]")
                    .sourceProductId("product1 [GH-90000]")
                    .debitAccount("CREDIT_ACC [GH-90000]")
                    .creditAccount("DEBIT_ACC [GH-90000]")
                    .amount(new BigDecimal("50.00 [GH-90000]"))
                    .currency("USD [GH-90000]")
                    .type(BillingTransaction.TransactionType.REFUND) // GH-90000
                    .build(); // GH-90000

            // When: Post both transactions
            String chargeId = runPromise(() -> billingPlugin.postTransaction(chargeTransaction)); // GH-90000
            String refundId = runPromise(() -> billingPlugin.postTransaction(refundTransaction)); // GH-90000

            // Then: Both should be posted
            assertThat(chargeId).isNotNull(); // GH-90000
            assertThat(refundId).isNotNull(); // GH-90000
            assertThat(chargeId).isNotEqualTo(refundId); // GH-90000
        }
    }

    @Nested
    @DisplayName("Transaction Reversal Tests [GH-90000]")
    class TransactionReversalTests {

        @BeforeEach
        void setUp() { // GH-90000
            runPromise(() -> billingPlugin.initialize(mockContext) // GH-90000
                    .then(v -> billingPlugin.start()) // GH-90000
                    .then(v -> billingPlugin.createAccount("DEBIT_ACC", BillingLedgerPlugin.AccountType.ASSET)) // GH-90000
                    .then(v -> billingPlugin.createAccount("CREDIT_ACC", BillingLedgerPlugin.AccountType.LIABILITY))); // GH-90000
        }

        @Test
        @DisplayName("Should reverse posted transaction [GH-90000]")
        void testReverseTransaction() { // GH-90000
            // Given: Posted transaction
            BillingTransaction transaction = BillingTransaction.builder() // GH-90000
                    .transactionId("TXN_REVERSE_001 [GH-90000]")
                    .sourceProductId("product1 [GH-90000]")
                    .debitAccount("DEBIT_ACC [GH-90000]")
                    .creditAccount("CREDIT_ACC [GH-90000]")
                    .amount(new BigDecimal("100.00 [GH-90000]"))
                    .currency("USD [GH-90000]")
                    .type(BillingTransaction.TransactionType.CHARGE) // GH-90000
                    .build(); // GH-90000

            String originalEntryId = runPromise(() -> billingPlugin.postTransaction(transaction)); // GH-90000

            // When: Reverse transaction
            Promise<String> reversalResult = billingPlugin.reverseTransaction( // GH-90000
                    "TXN_REVERSE_001", "Customer request");
            String reversalEntryId = runPromise(() -> reversalResult); // GH-90000

            // Then: Reversal should be created
            assertThat(reversalEntryId).isNotNull(); // GH-90000
            assertThat(reversalEntryId).isNotEqualTo(originalEntryId); // GH-90000

            // And: Reversal entry should have swapped accounts
            Promise<Optional<BillingLedgerPlugin.LedgerEntry>> reversalEntry =
                    billingPlugin.getEntry(reversalEntryId); // GH-90000
            Optional<BillingLedgerPlugin.LedgerEntry> reversal = runPromise(() -> reversalEntry); // GH-90000

            assertThat(reversal).isPresent(); // GH-90000
            assertThat(reversal.get().debitAccount()).isEqualTo("CREDIT_ACC [GH-90000]");
            assertThat(reversal.get().creditAccount()).isEqualTo("DEBIT_ACC [GH-90000]");
            assertThat(reversal.get().amount()).isEqualByComparingTo(new BigDecimal("100.00 [GH-90000]"));
        }

        @Test
        @DisplayName("Should fail to reverse non-existent transaction [GH-90000]")
        void testReverseTransaction_NotFound() { // GH-90000
            // When: Try to reverse non-existent transaction
            Promise<String> result = billingPlugin.reverseTransaction( // GH-90000
                    "NONEXISTENT_TXN", "Why not");

            // Then: Should throw exception
            assertThatThrownBy(() -> runPromise(() -> result)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("not found [GH-90000]");
        }
    }

    @Nested
    @DisplayName("Posting Status Tests [GH-90000]")
    class PostingStatusTests {

        @BeforeEach
        void setUp() { // GH-90000
            runPromise(() -> billingPlugin.initialize(mockContext) // GH-90000
                    .then(v -> billingPlugin.start()) // GH-90000
                    .then(v -> billingPlugin.createAccount("DEBIT_ACC", BillingLedgerPlugin.AccountType.ASSET)) // GH-90000
                    .then(v -> billingPlugin.createAccount("CREDIT_ACC", BillingLedgerPlugin.AccountType.LIABILITY))); // GH-90000
        }

        @Test
        @DisplayName("Should return NOT_FOUND for non-existent transaction [GH-90000]")
        void testGetPostingStatus_NotFound() { // GH-90000
            // When: Get status of non-existent transaction
            Promise<BillingLedgerPlugin.PostingStatus> result =
                    billingPlugin.getPostingStatus("NONEXISTENT [GH-90000]");
            BillingLedgerPlugin.PostingStatus status = runPromise(() -> result); // GH-90000

            // Then: Should be NOT_FOUND
            assertThat(status).isEqualTo(BillingLedgerPlugin.PostingStatus.NOT_FOUND); // GH-90000
        }

        @Test
        @DisplayName("Should return POSTED for posted transaction [GH-90000]")
        void testGetPostingStatus_Posted() { // GH-90000
            // Given: Posted transaction
            BillingTransaction transaction = BillingTransaction.builder() // GH-90000
                    .transactionId("STATUS_TXN_001 [GH-90000]")
                    .sourceProductId("product1 [GH-90000]")
                    .debitAccount("DEBIT_ACC [GH-90000]")
                    .creditAccount("CREDIT_ACC [GH-90000]")
                    .amount(new BigDecimal("100.00 [GH-90000]"))
                    .currency("USD [GH-90000]")
                    .type(BillingTransaction.TransactionType.CHARGE) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> billingPlugin.postTransaction(transaction)); // GH-90000

            // When: Get status
            Promise<BillingLedgerPlugin.PostingStatus> result =
                    billingPlugin.getPostingStatus("STATUS_TXN_001 [GH-90000]");
            BillingLedgerPlugin.PostingStatus status = runPromise(() -> result); // GH-90000

            // Then: Should be POSTED
            assertThat(status).isEqualTo(BillingLedgerPlugin.PostingStatus.POSTED); // GH-90000
        }

        @Test
        @DisplayName("Should return REVERSED for reversed transaction [GH-90000]")
        void testGetPostingStatus_Reversed() { // GH-90000
            // Given: Posted and reversed transaction
            BillingTransaction transaction = BillingTransaction.builder() // GH-90000
                    .transactionId("STATUS_TXN_002 [GH-90000]")
                    .sourceProductId("product1 [GH-90000]")
                    .debitAccount("DEBIT_ACC [GH-90000]")
                    .creditAccount("CREDIT_ACC [GH-90000]")
                    .amount(new BigDecimal("100.00 [GH-90000]"))
                    .currency("USD [GH-90000]")
                    .type(BillingTransaction.TransactionType.CHARGE) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> billingPlugin.postTransaction(transaction)); // GH-90000
            runPromise(() -> billingPlugin.reverseTransaction("STATUS_TXN_002", "Testing")); // GH-90000

            // When: Get status
            Promise<BillingLedgerPlugin.PostingStatus> result =
                    billingPlugin.getPostingStatus("STATUS_TXN_002 [GH-90000]");
            BillingLedgerPlugin.PostingStatus status = runPromise(() -> result); // GH-90000

            // Then: Should be REVERSED
            assertThat(status).isEqualTo(BillingLedgerPlugin.PostingStatus.REVERSED); // GH-90000
        }
    }

    @Nested
    @DisplayName("Entry Retrieval Tests [GH-90000]")
    class EntryRetrievalTests {

        @BeforeEach
        void setUp() { // GH-90000
            runPromise(() -> billingPlugin.initialize(mockContext) // GH-90000
                    .then(v -> billingPlugin.start()) // GH-90000
                    .then(v -> billingPlugin.createAccount("DEBIT_ACC", BillingLedgerPlugin.AccountType.ASSET)) // GH-90000
                    .then(v -> billingPlugin.createAccount("CREDIT_ACC", BillingLedgerPlugin.AccountType.LIABILITY))); // GH-90000
        }

        @Test
        @DisplayName("Should retrieve entry by ID [GH-90000]")
        void testGetEntry() { // GH-90000
            // Given: Posted transaction
            BillingTransaction transaction = BillingTransaction.builder() // GH-90000
                    .transactionId("ENTRY_TXN [GH-90000]")
                    .sourceProductId("product1 [GH-90000]")
                    .debitAccount("DEBIT_ACC [GH-90000]")
                    .creditAccount("CREDIT_ACC [GH-90000]")
                    .amount(new BigDecimal("75.50 [GH-90000]"))
                    .currency("USD [GH-90000]")
                    .type(BillingTransaction.TransactionType.CHARGE) // GH-90000
                    .description("Test entry [GH-90000]")
                    .build(); // GH-90000

            String entryId = runPromise(() -> billingPlugin.postTransaction(transaction)); // GH-90000

            // When: Retrieve entry
            Promise<Optional<BillingLedgerPlugin.LedgerEntry>> result =
                    billingPlugin.getEntry(entryId); // GH-90000
            Optional<BillingLedgerPlugin.LedgerEntry> entry = runPromise(() -> result); // GH-90000

            // Then: Entry should contain correct data
            assertThat(entry).isPresent(); // GH-90000
            assertThat(entry.get().entryId()).isEqualTo(entryId); // GH-90000
            assertThat(entry.get().transactionId()).isEqualTo("ENTRY_TXN [GH-90000]");
            assertThat(entry.get().amount()).isEqualByComparingTo(new BigDecimal("75.50 [GH-90000]"));
            assertThat(entry.get().description()).isEqualTo("Test entry [GH-90000]");
        }

        @Test
        @DisplayName("Should return empty Optional for non-existent entry [GH-90000]")
        void testGetEntry_NotFound() { // GH-90000
            // When: Get non-existent entry
            Promise<Optional<BillingLedgerPlugin.LedgerEntry>> result =
                    billingPlugin.getEntry("NONEXISTENT_ENTRY [GH-90000]");
            Optional<BillingLedgerPlugin.LedgerEntry> entry = runPromise(() -> result); // GH-90000

            // Then: Should return empty
            assertThat(entry).isEmpty(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Query Tests [GH-90000]")
    class QueryTests {

        @BeforeEach
        void setUp() { // GH-90000
            runPromise(() -> billingPlugin.initialize(mockContext) // GH-90000
                    .then(v -> billingPlugin.start()) // GH-90000
                    .then(v -> billingPlugin.createAccount("DEBIT_ACC", BillingLedgerPlugin.AccountType.ASSET)) // GH-90000
                    .then(v -> billingPlugin.createAccount("CREDIT_ACC", BillingLedgerPlugin.AccountType.LIABILITY)) // GH-90000
                    .then(v -> billingPlugin.createAccount("OTHER_ACC", BillingLedgerPlugin.AccountType.REVENUE))); // GH-90000
        }

        @Test
        @DisplayName("Should query entries for account within time range [GH-90000]")
        void testQueryEntries() { // GH-90000
            // Given: Multiple transactions
            Instant now = Instant.now(); // GH-90000
            Instant oneHourAgo = now.minusSeconds(3600); // GH-90000
            Instant twoHoursAgo = now.minusSeconds(7200); // GH-90000

            BillingTransaction txn1 = BillingTransaction.builder() // GH-90000
                    .transactionId("QUERY_TXN_001 [GH-90000]")
                    .sourceProductId("product1 [GH-90000]")
                    .debitAccount("DEBIT_ACC [GH-90000]")
                    .creditAccount("CREDIT_ACC [GH-90000]")
                    .amount(new BigDecimal("100.00 [GH-90000]"))
                    .currency("USD [GH-90000]")
                    .type(BillingTransaction.TransactionType.CHARGE) // GH-90000
                    .occurredAt(twoHoursAgo) // GH-90000
                    .build(); // GH-90000

            BillingTransaction txn2 = BillingTransaction.builder() // GH-90000
                    .transactionId("QUERY_TXN_002 [GH-90000]")
                    .sourceProductId("product1 [GH-90000]")
                    .debitAccount("DEBIT_ACC [GH-90000]")
                    .creditAccount("CREDIT_ACC [GH-90000]")
                    .amount(new BigDecimal("50.00 [GH-90000]"))
                    .currency("USD [GH-90000]")
                    .type(BillingTransaction.TransactionType.REFUND) // GH-90000
                    .occurredAt(oneHourAgo) // GH-90000
                    .build(); // GH-90000

            BillingTransaction txn3 = BillingTransaction.builder() // GH-90000
                    .transactionId("QUERY_TXN_003 [GH-90000]")
                    .sourceProductId("product1 [GH-90000]")
                    .debitAccount("OTHER_ACC [GH-90000]")
                    .creditAccount("CREDIT_ACC [GH-90000]")
                    .amount(new BigDecimal("25.00 [GH-90000]"))
                    .currency("USD [GH-90000]")
                    .type(BillingTransaction.TransactionType.CHARGE) // GH-90000
                    .occurredAt(oneHourAgo) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> billingPlugin.postTransaction(txn1) // GH-90000
                    .then(v -> billingPlugin.postTransaction(txn2)) // GH-90000
                    .then(v -> billingPlugin.postTransaction(txn3))); // GH-90000

            // When: Query entries for DEBIT_ACC within time range
            var timeRange = new BillingLedgerPlugin.TimeRange(twoHoursAgo, now); // GH-90000
            Promise<List<BillingLedgerPlugin.LedgerEntry>> result =
                    billingPlugin.queryEntries("DEBIT_ACC", timeRange); // GH-90000
            List<BillingLedgerPlugin.LedgerEntry> entries = runPromise(() -> result); // GH-90000

            // Then: Should return only relevant entries
            assertThat(entries).hasSize(2); // GH-90000
            assertThat(entries.get(0).transactionId()).isEqualTo("QUERY_TXN_001 [GH-90000]");
            assertThat(entries.get(1).transactionId()).isEqualTo("QUERY_TXN_002 [GH-90000]");
        }

        @Test
        @DisplayName("Should return empty list for account with no entries [GH-90000]")
        void testQueryEntries_Empty() { // GH-90000
            // When: Query for account with no entries
            var timeRange = new BillingLedgerPlugin.TimeRange( // GH-90000
                    Instant.now().minusSeconds(3600), // GH-90000
                    Instant.now()); // GH-90000
            Promise<List<BillingLedgerPlugin.LedgerEntry>> result =
                    billingPlugin.queryEntries("UNUSED_ACC", timeRange); // GH-90000
            List<BillingLedgerPlugin.LedgerEntry> entries = runPromise(() -> result); // GH-90000

            // Then: Should return empty
            assertThat(entries).isEmpty(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Edge Cases and Concurrency [GH-90000]")
    class EdgeCasesTests {

        @BeforeEach
        void setUp() { // GH-90000
            runPromise(() -> billingPlugin.initialize(mockContext) // GH-90000
                    .then(v -> billingPlugin.start()) // GH-90000
                    .then(v -> billingPlugin.createAccount("DEBIT_ACC", BillingLedgerPlugin.AccountType.ASSET)) // GH-90000
                    .then(v -> billingPlugin.createAccount("CREDIT_ACC", BillingLedgerPlugin.AccountType.LIABILITY))); // GH-90000
        }

        @Test
        @DisplayName("Should handle multiple transactions to same accounts [GH-90000]")
        void testMultipleTransactions() { // GH-90000
            // When: Post multiple transactions
            for (int i = 0; i < 10; i++) { // GH-90000
                final int index = i;
                BillingTransaction transaction = BillingTransaction.builder() // GH-90000
                        .transactionId("MULTI_TXN_" + index) // GH-90000
                        .sourceProductId("product1 [GH-90000]")
                        .debitAccount("DEBIT_ACC [GH-90000]")
                        .creditAccount("CREDIT_ACC [GH-90000]")
                        .amount(new BigDecimal(String.valueOf(index + 1))) // GH-90000
                        .currency("USD [GH-90000]")
                        .type(BillingTransaction.TransactionType.CHARGE) // GH-90000
                        .build(); // GH-90000

                runPromise(() -> billingPlugin.postTransaction(transaction)); // GH-90000
            }

            // Then: All should be posted
            for (int i = 0; i < 10; i++) { // GH-90000
                final int index = i;
                Promise<BillingLedgerPlugin.PostingStatus> statusResult =
                        billingPlugin.getPostingStatus("MULTI_TXN_" + index); // GH-90000
                BillingLedgerPlugin.PostingStatus status = runPromise(() -> statusResult); // GH-90000
                assertThat(status).isEqualTo(BillingLedgerPlugin.PostingStatus.POSTED); // GH-90000
            }
        }

        @Test
        @DisplayName("Should handle large amounts [GH-90000]")
        void testLargeAmounts() { // GH-90000
            // When: Post transaction with large amount
            BillingTransaction transaction = BillingTransaction.builder() // GH-90000
                    .transactionId("LARGE_AMOUNT [GH-90000]")
                    .sourceProductId("product1 [GH-90000]")
                    .debitAccount("DEBIT_ACC [GH-90000]")
                    .creditAccount("CREDIT_ACC [GH-90000]")
                    .amount(new BigDecimal("999999999.99 [GH-90000]"))
                    .currency("USD [GH-90000]")
                    .type(BillingTransaction.TransactionType.CHARGE) // GH-90000
                    .build(); // GH-90000

            String entryId = runPromise(() -> billingPlugin.postTransaction(transaction)); // GH-90000

            // Then: Should be posted correctly
            Promise<Optional<BillingLedgerPlugin.LedgerEntry>> result =
                    billingPlugin.getEntry(entryId); // GH-90000
            Optional<BillingLedgerPlugin.LedgerEntry> entry = runPromise(() -> result); // GH-90000

            assertThat(entry).isPresent(); // GH-90000
            assertThat(entry.get().amount()).isEqualByComparingTo(new BigDecimal("999999999.99 [GH-90000]"));
        }
    }
}
