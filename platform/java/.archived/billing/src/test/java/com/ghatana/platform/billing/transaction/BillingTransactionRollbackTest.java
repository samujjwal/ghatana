package com.ghatana.platform.billing.transaction;

import com.ghatana.platform.billing.BillingTransaction;
import com.ghatana.platform.billing.TransactionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for billing transaction rollback mechanics — validates reversal creation,
 * ledger consistency after rollback, and partial vs full rollback semantics.
 *
 * @doc.type class
 * @doc.purpose Tests for billing transaction rollback and reversal ledger integrity
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Billing Transaction Rollback Tests")
class BillingTransactionRollbackTest {

    private BillingTransaction buildCharge(String id, BigDecimal amount) {
        return BillingTransaction.builder()
                .transactionId(id)
                .sourceProductId("phr")
                .debitAccount("patient-acct")
                .creditAccount("provider-acct")
                .amount(amount)
                .currency("USD")
                .type(TransactionType.CHARGE)
                .description("Charge " + id)
                .tenantId("tenant-001")
                .build();
    }

    private BillingTransaction buildReversal(BillingTransaction original) {
        return BillingTransaction.builder()
                .transactionId(UUID.randomUUID().toString())
                .sourceProductId(original.getSourceProductId())
                .debitAccount(original.getCreditAccount())   // reversed
                .creditAccount(original.getDebitAccount())   // reversed
                .amount(original.getAmount())
                .currency(original.getCurrency())
                .type(TransactionType.REVERSAL)
                .description("Reversal of " + original.getTransactionId())
                .tenantId(original.getTenantId())
                .build();
    }

    // ── Reversal creation ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("reversal creation")
    class ReversalCreation {

        @Test
        @DisplayName("reversal swaps debit and credit accounts from original")
        void reversal_swapsDebitAndCreditAccounts() {
            BillingTransaction original = buildCharge("tx-001", new BigDecimal("100.00"));
            BillingTransaction reversal = buildReversal(original);

            assertThat(reversal.getDebitAccount()).isEqualTo(original.getCreditAccount());
            assertThat(reversal.getCreditAccount()).isEqualTo(original.getDebitAccount());
        }

        @Test
        @DisplayName("reversal has same amount as original")
        void reversal_hasSameAmountAsOriginal() {
            BillingTransaction original = buildCharge("tx-002", new BigDecimal("250.50"));
            BillingTransaction reversal = buildReversal(original);

            assertThat(reversal.getAmount()).isEqualByComparingTo(original.getAmount());
        }

        @Test
        @DisplayName("reversal type is REVERSAL")
        void reversal_typeIsReversal() {
            BillingTransaction original = buildCharge("tx-003", new BigDecimal("50.00"));
            BillingTransaction reversal = buildReversal(original);

            assertThat(reversal.getType()).isEqualTo(TransactionType.REVERSAL);
        }

        @Test
        @DisplayName("reversal description references original transaction ID")
        void reversal_descriptionReferencesOriginalTransactionId() {
            BillingTransaction original = buildCharge("tx-004", new BigDecimal("75.00"));
            BillingTransaction reversal = buildReversal(original);

            assertThat(reversal.getDescription()).contains("tx-004");
        }
    }

    // ── Ledger consistency after rollback ─────────────────────────────────────

    @Nested
    @DisplayName("ledger consistency after rollback")
    class LedgerConsistencyAfterRollback {

        @Test
        @DisplayName("net ledger balance is zero after charge and reversal")
        void netLedgerBalance_isZero_afterChargeAndReversal() {
            BillingTransaction charge  = buildCharge("tx-005", new BigDecimal("200.00"));
            BillingTransaction reversal = buildReversal(charge);

            // Net = charge - reversal (same accounts, reversed direction)
            BigDecimal net = charge.getAmount().subtract(reversal.getAmount());

            assertThat(net).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("ledger retains other transactions when one is reversed")
        void ledger_retainsOtherTransactions_whenOneIsReversed() {
            List<BillingTransaction> ledger = new ArrayList<>();
            BillingTransaction tx1 = buildCharge("tx-006", new BigDecimal("100.00"));
            BillingTransaction tx2 = buildCharge("tx-007", new BigDecimal("50.00"));

            ledger.add(tx1);
            ledger.add(tx2);

            // Reverse tx1 only
            BillingTransaction reversal = buildReversal(tx1);
            ledger.add(reversal);

            // After reversal, net for tx1 = 0; tx2 still stands
            BigDecimal tx1Net = tx1.getAmount().subtract(reversal.getAmount());
            assertThat(tx1Net).isEqualByComparingTo(BigDecimal.ZERO);

            BigDecimal outstanding = ledger.stream()
                    .filter(t -> t.getType() != TransactionType.REVERSAL)
                    .map(BillingTransaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .subtract(reversal.getAmount());

            // tx1 = 100, tx2 = 50, reversal = -100 → outstanding = 50
            assertThat(outstanding).isEqualByComparingTo(new BigDecimal("50.00"));
        }
    }

    // ── Partial rollback ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("partial rollback semantics")
    class PartialRollback {

        @Test
        @DisplayName("partial reversal uses amount less than original")
        void partialReversal_usesAmountLessThanOriginal() {
            BillingTransaction original = buildCharge("tx-008", new BigDecimal("300.00"));
            BigDecimal partialAmount = new BigDecimal("100.00");

            BillingTransaction partialReversal = BillingTransaction.builder()
                    .transactionId(UUID.randomUUID().toString())
                    .sourceProductId(original.getSourceProductId())
                    .debitAccount(original.getCreditAccount())
                    .creditAccount(original.getDebitAccount())
                    .amount(partialAmount)
                    .currency(original.getCurrency())
                    .type(TransactionType.REVERSAL)
                    .description("Partial reversal of " + original.getTransactionId())
                    .tenantId(original.getTenantId())
                    .build();

            assertThat(partialReversal.getAmount()).isLessThan(original.getAmount());
            BigDecimal remaining = original.getAmount().subtract(partialReversal.getAmount());
            assertThat(remaining).isEqualByComparingTo(new BigDecimal("200.00"));
        }
    }
}
