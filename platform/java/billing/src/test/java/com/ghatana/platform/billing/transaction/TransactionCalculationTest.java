package com.ghatana.platform.billing.transaction;

import com.ghatana.platform.billing.BillingTransaction;
import com.ghatana.platform.billing.TransactionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for billing transaction calculation — validates amount precision,
 * currency correctness, transaction type semantics, and reversal mechanics.
 *
 * @doc.type class
 * @doc.purpose Tests for billing transaction creation, validation, and amount correctness
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Transaction Calculation Tests")
class TransactionCalculationTest {

    private BillingTransaction.Builder baseBuilder() {
        return BillingTransaction.builder()
                .transactionId(UUID.randomUUID().toString())
                .sourceProductId("phr")
                .debitAccount("patient-acct")
                .creditAccount("provider-acct")
                .currency("USD")
                .type(TransactionType.CHARGE)
                .description("Office visit charge")
                .tenantId("tenant-001");
    }

    // ── Amount correctness ────────────────────────────────────────────────────

    @Nested
    @DisplayName("amount correctness")
    class AmountCorrectness {

        @Test
        @DisplayName("valid positive amount is stored with full precision")
        void validPositiveAmount_storedWithFullPrecision() {
            BillingTransaction tx = baseBuilder()
                    .amount(new BigDecimal("123.45"))
                    .build();

            assertThat(tx.getAmount()).isEqualByComparingTo(new BigDecimal("123.45"));
        }

        @Test
        @DisplayName("zero amount throws IllegalArgumentException")
        void zeroAmount_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> baseBuilder()
                    .amount(BigDecimal.ZERO)
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("amount must be positive");
        }

        @Test
        @DisplayName("negative amount throws IllegalArgumentException")
        void negativeAmount_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> baseBuilder()
                    .amount(new BigDecimal("-50.00"))
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("amount must be positive");
        }

        @Test
        @DisplayName("amount with high precision (8 decimal places) is stored exactly")
        void highPrecisionAmount_storedExactly() {
            BigDecimal precise = new BigDecimal("0.00000001");
            BillingTransaction tx = baseBuilder().amount(precise).build();

            assertThat(tx.getAmount()).isEqualByComparingTo(precise);
        }
    }

    // ── Account validation ────────────────────────────────────────────────────

    @Nested
    @DisplayName("account validation")
    class AccountValidation {

        @Test
        @DisplayName("same debit and credit account throws IllegalArgumentException")
        void sameDebitAndCreditAccount_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> BillingTransaction.builder()
                    .transactionId(UUID.randomUUID().toString())
                    .sourceProductId("phr")
                    .debitAccount("same-account")
                    .creditAccount("same-account")
                    .amount(new BigDecimal("100.00"))
                    .currency("USD")
                    .type(TransactionType.CHARGE)
                    .description("desc")
                    .tenantId("tenant-001")
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("debitAccount and creditAccount must differ");
        }

        @Test
        @DisplayName("missing required field throws NullPointerException")
        void missingRequiredField_throwsNullPointerException() {
            assertThatThrownBy(() -> BillingTransaction.builder()
                    .sourceProductId("phr")
                    .debitAccount("a")
                    .creditAccount("b")
                    .amount(new BigDecimal("100.00"))
                    .currency("USD")
                    .type(TransactionType.CHARGE)
                    .description("desc")
                    .tenantId("tenant-001")
                    .build()) // transactionId is null
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("transactionId");
        }
    }

    // ── Transaction type semantics ────────────────────────────────────────────

    @Nested
    @DisplayName("transaction type semantics")
    class TransactionTypeSemantics {

        @Test
        @DisplayName("CHARGE transaction reflects service fee from patient to provider")
        void chargeTransaction_reflectsServiceFee() {
            BillingTransaction tx = baseBuilder()
                    .type(TransactionType.CHARGE)
                    .amount(new BigDecimal("250.00"))
                    .build();

            assertThat(tx.getType()).isEqualTo(TransactionType.CHARGE);
            assertThat(tx.getDebitAccount()).isEqualTo("patient-acct");
            assertThat(tx.getCreditAccount()).isEqualTo("provider-acct");
        }

        @Test
        @DisplayName("REFUND transaction reverses a prior charge")
        void refundTransaction_reversesACharge() {
            BillingTransaction tx = baseBuilder()
                    .type(TransactionType.REFUND)
                    .debitAccount("provider-acct")
                    .creditAccount("patient-acct")
                    .amount(new BigDecimal("250.00"))
                    .description("Refund for cancelled visit")
                    .build();

            assertThat(tx.getType()).isEqualTo(TransactionType.REFUND);
            assertThat(tx.getDebitAccount()).isEqualTo("provider-acct");
            assertThat(tx.getCreditAccount()).isEqualTo("patient-acct");
        }

        @Test
        @DisplayName("INSURANCE_SETTLEMENT transaction credit goes to provider")
        void insuranceSettlement_creditGoesToProvider() {
            BillingTransaction tx = baseBuilder()
                    .type(TransactionType.INSURANCE_SETTLEMENT)
                    .debitAccount("insurer-acct")
                    .creditAccount("provider-acct")
                    .amount(new BigDecimal("180.00"))
                    .description("Medicare Part B settlement")
                    .build();

            assertThat(tx.getType()).isEqualTo(TransactionType.INSURANCE_SETTLEMENT);
            assertThat(tx.getAmount()).isEqualByComparingTo(new BigDecimal("180.00"));
        }

        @Test
        @DisplayName("CO_PAYMENT transaction captures patient co-pay amount")
        void coPaymentTransaction_capturesCoPayAmount() {
            BillingTransaction tx = baseBuilder()
                    .type(TransactionType.CO_PAYMENT)
                    .amount(new BigDecimal("25.00"))
                    .description("Patient co-pay")
                    .build();

            assertThat(tx.getType()).isEqualTo(TransactionType.CO_PAYMENT);
            assertThat(tx.getAmount()).isEqualByComparingTo(new BigDecimal("25.00"));
        }
    }

    // ── Currency ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("currency")
    class Currency {

        @Test
        @DisplayName("USD currency is stored and returned correctly")
        void usdCurrency_storedAndReturnedCorrectly() {
            BillingTransaction tx = baseBuilder().amount(new BigDecimal("100.00")).build();
            assertThat(tx.getCurrency()).isEqualTo("USD");
        }

        @Test
        @DisplayName("non-USD currency (EUR) is stored correctly")
        void nonUsdCurrency_storedCorrectly() {
            BillingTransaction tx = baseBuilder()
                    .currency("EUR")
                    .amount(new BigDecimal("100.00"))
                    .build();

            assertThat(tx.getCurrency()).isEqualTo("EUR");
        }
    }
}
