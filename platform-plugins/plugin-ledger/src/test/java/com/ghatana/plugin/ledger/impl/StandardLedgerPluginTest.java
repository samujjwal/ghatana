package com.ghatana.plugin.ledger.impl;

import com.ghatana.platform.plugin.PluginContext;
import com.ghatana.platform.plugin.PluginState;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.plugin.ledger.LedgerPlugin;
import com.ghatana.plugin.ledger.LedgerTransaction;
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
 * @doc.purpose Comprehensive tests for StandardLedgerPlugin
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("StandardLedgerPlugin Tests")
@ExtendWith(MockitoExtension.class) 
class StandardLedgerPluginTest extends EventloopTestBase {

    @Mock
    private PluginContext mockContext;

    private StandardLedgerPlugin billingPlugin;

    @BeforeEach
    void setUp() { 
        billingPlugin = new StandardLedgerPlugin(); 
    }

    @Nested
    @DisplayName("Plugin Lifecycle Tests")
    class LifecycleTests {

        @Test
        @DisplayName("Should initialize billing plugin with correct state")
        void testInitialize() { 
            // Given: Fresh plugin instance
            assertThat(billingPlugin.getState()).isEqualTo(PluginState.UNLOADED); 

            // When: Initialize plugin
            Promise<Void> result = billingPlugin.initialize(mockContext); 
            runPromise(() -> result); 

            // Then: Plugin should be initialized
            assertThat(billingPlugin.getState()).isEqualTo(PluginState.INITIALIZED); 
        }

        @Test
        @DisplayName("Should start billing plugin")
        void testStart() { 
            // Given: Initialized plugin
            runPromise(() -> billingPlugin.initialize(mockContext)); 

            // When: Start plugin
            Promise<Void> result = billingPlugin.start(); 
            runPromise(() -> result); 

            // Then: Plugin should be started
            assertThat(billingPlugin.getState()).isEqualTo(PluginState.STARTED); 
        }

        @Test
        @DisplayName("Should return correct metadata")
        void testMetadata() { 
            // When: Get metadata
            var metadata = billingPlugin.metadata(); 

            // Then: Metadata should be complete
            assertThat(metadata.id()).isEqualTo("ledger-plugin");
            assertThat(metadata.name()).isEqualTo("Ledger Plugin");
            assertThat(metadata.version()).isEqualTo("1.0.0");
        }
    }

    @Nested
    @DisplayName("Account Management Tests")
    class AccountManagementTests {

        @BeforeEach
        void setUp() { 
            runPromise(() -> billingPlugin.initialize(mockContext) 
                    .then(v -> billingPlugin.start())); 
        }

        @Test
        @DisplayName("Should create asset account")
        void testCreateAccount_Asset() { 
            // When: Create asset account
            Promise<LedgerPlugin.LedgerAccount> result =
                    billingPlugin.createAccount("ASSET_001", LedgerPlugin.AccountType.ASSET); 
            LedgerPlugin.LedgerAccount account = runPromise(() -> result); 

            // Then: Account should be created correctly
            assertThat(account.accountId()).isEqualTo("ASSET_001");
            assertThat(account.type()).isEqualTo(LedgerPlugin.AccountType.ASSET); 
            assertThat(account.currency()).isEqualTo("USD");
            assertThat(account.balance()).isEqualByComparingTo(BigDecimal.ZERO); 
            assertThat(account.createdAt()).isNotNull(); 
        }

        @Test
        @DisplayName("Should create different account types")
        void testCreateAccount_DifferentTypes() { 
            // When: Create different account types
            LedgerPlugin.LedgerAccount assetAccount = runPromise(() -> 
                    billingPlugin.createAccount("asset", LedgerPlugin.AccountType.ASSET)); 

            LedgerPlugin.LedgerAccount liabilityAccount = runPromise(() -> 
                    billingPlugin.createAccount("liability", LedgerPlugin.AccountType.LIABILITY)); 

            LedgerPlugin.LedgerAccount equityAccount = runPromise(() -> 
                    billingPlugin.createAccount("equity", LedgerPlugin.AccountType.EQUITY)); 

            // Then: All account types should be created
            assertThat(assetAccount.type()).isEqualTo(LedgerPlugin.AccountType.ASSET); 
            assertThat(liabilityAccount.type()).isEqualTo(LedgerPlugin.AccountType.LIABILITY); 
            assertThat(equityAccount.type()).isEqualTo(LedgerPlugin.AccountType.EQUITY); 
        }

        @Test
        @DisplayName("Should prevent duplicate account creation")
        void testCreateAccount_Duplicate() { 
            // Given: Account already created
            runPromise(() -> billingPlugin.createAccount("DUP_001", LedgerPlugin.AccountType.ASSET)); 

            // When: Try to create duplicate
            Promise<LedgerPlugin.LedgerAccount> result =
                    billingPlugin.createAccount("DUP_001", LedgerPlugin.AccountType.ASSET); 

            // Then: Should throw exception
            assertThatThrownBy(() -> runPromise(() -> result)) 
                    .isInstanceOf(IllegalArgumentException.class) 
                    .hasMessageContaining("already exists");
        }
    }

    @Nested
    @DisplayName("Transaction Posting Tests")
    class TransactionPostingTests {

        @BeforeEach
        void setUp() { 
            runPromise(() -> billingPlugin.initialize(mockContext) 
                    .then(v -> billingPlugin.start()) 
                    .then(v -> billingPlugin.createAccount("DEBIT_ACC", LedgerPlugin.AccountType.ASSET)) 
                    .then(v -> billingPlugin.createAccount("CREDIT_ACC", LedgerPlugin.AccountType.LIABILITY))); 
        }

        @Test
        @DisplayName("Should post simple transaction")
        void testPostTransaction_Simple() { 
            // Given: Transaction
            LedgerTransaction transaction = LedgerTransaction.builder() 
                    .transactionId("TXN_001")
                    .sourceId("product1")
                    .debitAccount("DEBIT_ACC")
                    .creditAccount("CREDIT_ACC")
                    .amount(new BigDecimal("100.00"))
                    .currency("USD")
                    .type(LedgerTransaction.TransactionType.CHARGE) 
                    .description("Test charge")
                    .build(); 

            // When: Post transaction
            Promise<String> result = billingPlugin.postTransaction(transaction); 
            String entryId = runPromise(() -> result); 

            // Then: Entry should be created
            assertThat(entryId).isNotNull(); 

            // And: Entry should be retrievable
            Promise<Optional<LedgerPlugin.LedgerEntry>> entryResult =
                    billingPlugin.getEntry(entryId); 
            Optional<LedgerPlugin.LedgerEntry> entry = runPromise(() -> entryResult); 

            assertThat(entry).isPresent(); 
            assertThat(entry.get().transactionId()).isEqualTo("TXN_001");
            assertThat(entry.get().amount()).isEqualByComparingTo(new BigDecimal("100.00"));
        }

        @Test
        @DisplayName("Should enforce idempotency for transaction posting")
        void testPostTransaction_Idempotent() { 
            // Given: Transaction
            LedgerTransaction transaction = LedgerTransaction.builder() 
                    .transactionId("IDEMPOTENT_TXN")
                    .sourceId("product1")
                    .debitAccount("DEBIT_ACC")
                    .creditAccount("CREDIT_ACC")
                    .amount(new BigDecimal("50.00"))
                    .currency("USD")
                    .type(LedgerTransaction.TransactionType.CHARGE) 
                    .build(); 

            // When: Post transaction twice
            String firstEntryId = runPromise(() -> billingPlugin.postTransaction(transaction)); 
            String secondEntryId = runPromise(() -> billingPlugin.postTransaction(transaction)); 

            // Then: Should return same entry ID
            assertThat(firstEntryId).isEqualTo(secondEntryId); 
        }

        @Test
        @DisplayName("Should support different transaction types")
        void testPostTransaction_DifferentTypes() { 
            // Given: Different transaction types
            LedgerTransaction chargeTransaction = LedgerTransaction.builder() 
                    .transactionId("CHARGE_TXN")
                    .sourceId("product1")
                    .debitAccount("DEBIT_ACC")
                    .creditAccount("CREDIT_ACC")
                    .amount(new BigDecimal("100.00"))
                    .currency("USD")
                    .type(LedgerTransaction.TransactionType.CHARGE) 
                    .build(); 

            LedgerTransaction refundTransaction = LedgerTransaction.builder() 
                    .transactionId("REFUND_TXN")
                    .sourceId("product1")
                    .debitAccount("CREDIT_ACC")
                    .creditAccount("DEBIT_ACC")
                    .amount(new BigDecimal("50.00"))
                    .currency("USD")
                    .type(LedgerTransaction.TransactionType.REFUND) 
                    .build(); 

            // When: Post both transactions
            String chargeId = runPromise(() -> billingPlugin.postTransaction(chargeTransaction)); 
            String refundId = runPromise(() -> billingPlugin.postTransaction(refundTransaction)); 

            // Then: Both should be posted
            assertThat(chargeId).isNotNull(); 
            assertThat(refundId).isNotNull(); 
            assertThat(chargeId).isNotEqualTo(refundId); 
        }
    }

    @Nested
    @DisplayName("Transaction Reversal Tests")
    class TransactionReversalTests {

        @BeforeEach
        void setUp() { 
            runPromise(() -> billingPlugin.initialize(mockContext) 
                    .then(v -> billingPlugin.start()) 
                    .then(v -> billingPlugin.createAccount("DEBIT_ACC", LedgerPlugin.AccountType.ASSET)) 
                    .then(v -> billingPlugin.createAccount("CREDIT_ACC", LedgerPlugin.AccountType.LIABILITY))); 
        }

        @Test
        @DisplayName("Should reverse posted transaction")
        void testReverseTransaction() { 
            // Given: Posted transaction
            LedgerTransaction transaction = LedgerTransaction.builder() 
                    .transactionId("TXN_REVERSE_001")
                    .sourceId("product1")
                    .debitAccount("DEBIT_ACC")
                    .creditAccount("CREDIT_ACC")
                    .amount(new BigDecimal("100.00"))
                    .currency("USD")
                    .type(LedgerTransaction.TransactionType.CHARGE) 
                    .build(); 

            String originalEntryId = runPromise(() -> billingPlugin.postTransaction(transaction)); 

            // When: Reverse transaction
            Promise<String> reversalResult = billingPlugin.reverseTransaction( 
                    "TXN_REVERSE_001", "Customer request");
            String reversalEntryId = runPromise(() -> reversalResult); 

            // Then: Reversal should be created
            assertThat(reversalEntryId).isNotNull(); 
            assertThat(reversalEntryId).isNotEqualTo(originalEntryId); 

            // And: Reversal entry should have swapped accounts
            Promise<Optional<LedgerPlugin.LedgerEntry>> reversalEntry =
                    billingPlugin.getEntry(reversalEntryId); 
            Optional<LedgerPlugin.LedgerEntry> reversal = runPromise(() -> reversalEntry); 

            assertThat(reversal).isPresent(); 
            assertThat(reversal.get().debitAccount()).isEqualTo("CREDIT_ACC");
            assertThat(reversal.get().creditAccount()).isEqualTo("DEBIT_ACC");
            assertThat(reversal.get().amount()).isEqualByComparingTo(new BigDecimal("100.00"));
        }

        @Test
        @DisplayName("Should fail to reverse non-existent transaction")
        void testReverseTransaction_NotFound() { 
            // When: Try to reverse non-existent transaction
            Promise<String> result = billingPlugin.reverseTransaction( 
                    "NONEXISTENT_TXN", "Why not");

            // Then: Should throw exception
            assertThatThrownBy(() -> runPromise(() -> result)) 
                    .isInstanceOf(IllegalArgumentException.class) 
                    .hasMessageContaining("not found");
        }
    }

    @Nested
    @DisplayName("Posting Status Tests")
    class PostingStatusTests {

        @BeforeEach
        void setUp() { 
            runPromise(() -> billingPlugin.initialize(mockContext) 
                    .then(v -> billingPlugin.start()) 
                    .then(v -> billingPlugin.createAccount("DEBIT_ACC", LedgerPlugin.AccountType.ASSET)) 
                    .then(v -> billingPlugin.createAccount("CREDIT_ACC", LedgerPlugin.AccountType.LIABILITY))); 
        }

        @Test
        @DisplayName("Should return NOT_FOUND for non-existent transaction")
        void testGetPostingStatus_NotFound() { 
            // When: Get status of non-existent transaction
            Promise<LedgerPlugin.PostingStatus> result =
                    billingPlugin.getPostingStatus("NONEXISTENT");
            LedgerPlugin.PostingStatus status = runPromise(() -> result); 

            // Then: Should be NOT_FOUND
            assertThat(status).isEqualTo(LedgerPlugin.PostingStatus.NOT_FOUND); 
        }

        @Test
        @DisplayName("Should return POSTED for posted transaction")
        void testGetPostingStatus_Posted() { 
            // Given: Posted transaction
            LedgerTransaction transaction = LedgerTransaction.builder() 
                    .transactionId("STATUS_TXN_001")
                    .sourceId("product1")
                    .debitAccount("DEBIT_ACC")
                    .creditAccount("CREDIT_ACC")
                    .amount(new BigDecimal("100.00"))
                    .currency("USD")
                    .type(LedgerTransaction.TransactionType.CHARGE) 
                    .build(); 

            runPromise(() -> billingPlugin.postTransaction(transaction)); 

            // When: Get status
            Promise<LedgerPlugin.PostingStatus> result =
                    billingPlugin.getPostingStatus("STATUS_TXN_001");
            LedgerPlugin.PostingStatus status = runPromise(() -> result); 

            // Then: Should be POSTED
            assertThat(status).isEqualTo(LedgerPlugin.PostingStatus.POSTED); 
        }

        @Test
        @DisplayName("Should return REVERSED for reversed transaction")
        void testGetPostingStatus_Reversed() { 
            // Given: Posted and reversed transaction
            LedgerTransaction transaction = LedgerTransaction.builder() 
                    .transactionId("STATUS_TXN_002")
                    .sourceId("product1")
                    .debitAccount("DEBIT_ACC")
                    .creditAccount("CREDIT_ACC")
                    .amount(new BigDecimal("100.00"))
                    .currency("USD")
                    .type(LedgerTransaction.TransactionType.CHARGE) 
                    .build(); 

            runPromise(() -> billingPlugin.postTransaction(transaction)); 
            runPromise(() -> billingPlugin.reverseTransaction("STATUS_TXN_002", "Testing")); 

            // When: Get status
            Promise<LedgerPlugin.PostingStatus> result =
                    billingPlugin.getPostingStatus("STATUS_TXN_002");
            LedgerPlugin.PostingStatus status = runPromise(() -> result); 

            // Then: Should be REVERSED
            assertThat(status).isEqualTo(LedgerPlugin.PostingStatus.REVERSED); 
        }
    }

    @Nested
    @DisplayName("Entry Retrieval Tests")
    class EntryRetrievalTests {

        @BeforeEach
        void setUp() { 
            runPromise(() -> billingPlugin.initialize(mockContext) 
                    .then(v -> billingPlugin.start()) 
                    .then(v -> billingPlugin.createAccount("DEBIT_ACC", LedgerPlugin.AccountType.ASSET)) 
                    .then(v -> billingPlugin.createAccount("CREDIT_ACC", LedgerPlugin.AccountType.LIABILITY))); 
        }

        @Test
        @DisplayName("Should retrieve entry by ID")
        void testGetEntry() { 
            // Given: Posted transaction
            LedgerTransaction transaction = LedgerTransaction.builder() 
                    .transactionId("ENTRY_TXN")
                    .sourceId("product1")
                    .debitAccount("DEBIT_ACC")
                    .creditAccount("CREDIT_ACC")
                    .amount(new BigDecimal("75.50"))
                    .currency("USD")
                    .type(LedgerTransaction.TransactionType.CHARGE) 
                    .description("Test entry")
                    .build(); 

            String entryId = runPromise(() -> billingPlugin.postTransaction(transaction)); 

            // When: Retrieve entry
            Promise<Optional<LedgerPlugin.LedgerEntry>> result =
                    billingPlugin.getEntry(entryId); 
            Optional<LedgerPlugin.LedgerEntry> entry = runPromise(() -> result); 

            // Then: Entry should contain correct data
            assertThat(entry).isPresent(); 
            assertThat(entry.get().entryId()).isEqualTo(entryId); 
            assertThat(entry.get().transactionId()).isEqualTo("ENTRY_TXN");
            assertThat(entry.get().amount()).isEqualByComparingTo(new BigDecimal("75.50"));
            assertThat(entry.get().description()).isEqualTo("Test entry");
        }

        @Test
        @DisplayName("Should return empty Optional for non-existent entry")
        void testGetEntry_NotFound() { 
            // When: Get non-existent entry
            Promise<Optional<LedgerPlugin.LedgerEntry>> result =
                    billingPlugin.getEntry("NONEXISTENT_ENTRY");
            Optional<LedgerPlugin.LedgerEntry> entry = runPromise(() -> result); 

            // Then: Should return empty
            assertThat(entry).isEmpty(); 
        }
    }

    @Nested
    @DisplayName("Query Tests")
    class QueryTests {

        @BeforeEach
        void setUp() { 
            runPromise(() -> billingPlugin.initialize(mockContext) 
                    .then(v -> billingPlugin.start()) 
                    .then(v -> billingPlugin.createAccount("DEBIT_ACC", LedgerPlugin.AccountType.ASSET)) 
                    .then(v -> billingPlugin.createAccount("CREDIT_ACC", LedgerPlugin.AccountType.LIABILITY)) 
                    .then(v -> billingPlugin.createAccount("OTHER_ACC", LedgerPlugin.AccountType.REVENUE))); 
        }

        @Test
        @DisplayName("Should query entries for account within time range")
        void testQueryEntries() { 
            // Given: Multiple transactions
            Instant now = Instant.now(); 
            Instant oneHourAgo = now.minusSeconds(3600); 
            Instant twoHoursAgo = now.minusSeconds(7200); 

            LedgerTransaction txn1 = LedgerTransaction.builder() 
                    .transactionId("QUERY_TXN_001")
                    .sourceId("product1")
                    .debitAccount("DEBIT_ACC")
                    .creditAccount("CREDIT_ACC")
                    .amount(new BigDecimal("100.00"))
                    .currency("USD")
                    .type(LedgerTransaction.TransactionType.CHARGE) 
                    .occurredAt(twoHoursAgo) 
                    .build(); 

            LedgerTransaction txn2 = LedgerTransaction.builder() 
                    .transactionId("QUERY_TXN_002")
                    .sourceId("product1")
                    .debitAccount("DEBIT_ACC")
                    .creditAccount("CREDIT_ACC")
                    .amount(new BigDecimal("50.00"))
                    .currency("USD")
                    .type(LedgerTransaction.TransactionType.REFUND) 
                    .occurredAt(oneHourAgo) 
                    .build(); 

            LedgerTransaction txn3 = LedgerTransaction.builder() 
                    .transactionId("QUERY_TXN_003")
                    .sourceId("product1")
                    .debitAccount("OTHER_ACC")
                    .creditAccount("CREDIT_ACC")
                    .amount(new BigDecimal("25.00"))
                    .currency("USD")
                    .type(LedgerTransaction.TransactionType.CHARGE) 
                    .occurredAt(oneHourAgo) 
                    .build(); 

            runPromise(() -> billingPlugin.postTransaction(txn1) 
                    .then(v -> billingPlugin.postTransaction(txn2)) 
                    .then(v -> billingPlugin.postTransaction(txn3))); 

            // When: Query entries for DEBIT_ACC within time range
            var timeRange = new LedgerPlugin.TimeRange(twoHoursAgo, now); 
            Promise<List<LedgerPlugin.LedgerEntry>> result =
                    billingPlugin.queryEntries("DEBIT_ACC", timeRange); 
            List<LedgerPlugin.LedgerEntry> entries = runPromise(() -> result); 

            // Then: Should return only relevant entries
            assertThat(entries).hasSize(2); 
            assertThat(entries.get(0).transactionId()).isEqualTo("QUERY_TXN_001");
            assertThat(entries.get(1).transactionId()).isEqualTo("QUERY_TXN_002");
        }

        @Test
        @DisplayName("Should return empty list for account with no entries")
        void testQueryEntries_Empty() { 
            // When: Query for account with no entries
            var timeRange = new LedgerPlugin.TimeRange( 
                    Instant.now().minusSeconds(3600), 
                    Instant.now()); 
            Promise<List<LedgerPlugin.LedgerEntry>> result =
                    billingPlugin.queryEntries("UNUSED_ACC", timeRange); 
            List<LedgerPlugin.LedgerEntry> entries = runPromise(() -> result); 

            // Then: Should return empty
            assertThat(entries).isEmpty(); 
        }
    }

    @Nested
    @DisplayName("Edge Cases and Concurrency")
    class EdgeCasesTests {

        @BeforeEach
        void setUp() { 
            runPromise(() -> billingPlugin.initialize(mockContext) 
                    .then(v -> billingPlugin.start()) 
                    .then(v -> billingPlugin.createAccount("DEBIT_ACC", LedgerPlugin.AccountType.ASSET)) 
                    .then(v -> billingPlugin.createAccount("CREDIT_ACC", LedgerPlugin.AccountType.LIABILITY))); 
        }

        @Test
        @DisplayName("Should handle multiple transactions to same accounts")
        void testMultipleTransactions() { 
            // When: Post multiple transactions
            for (int i = 0; i < 10; i++) { 
                final int index = i;
                LedgerTransaction transaction = LedgerTransaction.builder() 
                        .transactionId("MULTI_TXN_" + index) 
                        .sourceId("product1")
                        .debitAccount("DEBIT_ACC")
                        .creditAccount("CREDIT_ACC")
                        .amount(new BigDecimal(String.valueOf(index + 1))) 
                        .currency("USD")
                        .type(LedgerTransaction.TransactionType.CHARGE) 
                        .build(); 

                runPromise(() -> billingPlugin.postTransaction(transaction)); 
            }

            // Then: All should be posted
            for (int i = 0; i < 10; i++) { 
                final int index = i;
                Promise<LedgerPlugin.PostingStatus> statusResult =
                        billingPlugin.getPostingStatus("MULTI_TXN_" + index); 
                LedgerPlugin.PostingStatus status = runPromise(() -> statusResult); 
                assertThat(status).isEqualTo(LedgerPlugin.PostingStatus.POSTED); 
            }
        }

        @Test
        @DisplayName("Should handle large amounts")
        void testLargeAmounts() { 
            // When: Post transaction with large amount
            LedgerTransaction transaction = LedgerTransaction.builder() 
                    .transactionId("LARGE_AMOUNT")
                    .sourceId("product1")
                    .debitAccount("DEBIT_ACC")
                    .creditAccount("CREDIT_ACC")
                    .amount(new BigDecimal("999999999.99"))
                    .currency("USD")
                    .type(LedgerTransaction.TransactionType.CHARGE) 
                    .build(); 

            String entryId = runPromise(() -> billingPlugin.postTransaction(transaction)); 

            // Then: Should be posted correctly
            Promise<Optional<LedgerPlugin.LedgerEntry>> result =
                    billingPlugin.getEntry(entryId); 
            Optional<LedgerPlugin.LedgerEntry> entry = runPromise(() -> result); 

            assertThat(entry).isPresent(); 
            assertThat(entry.get().amount()).isEqualByComparingTo(new BigDecimal("999999999.99"));
        }
    }
}
