/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.ledger.service;

import com.ghatana.appplatform.ledger.domain.Currency;
import com.ghatana.appplatform.ledger.domain.Direction;
import com.ghatana.appplatform.ledger.domain.Journal;
import com.ghatana.appplatform.ledger.domain.JournalEntry;
import com.ghatana.appplatform.ledger.domain.MonetaryAmount;
import com.ghatana.appplatform.ledger.exception.UnbalancedJournalException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("BalanceEnforcer — double-entry balance verification")
class BalanceEnforcerTest {

    private static final Currency NPR = Currency.NPR;
    private static final Currency USD = Currency.USD;
    private static final UUID ASSETS_ACCT = UUID.randomUUID();
    private static final UUID INCOME_ACCT  = UUID.randomUUID();
    private static final UUID EXPENSE_ACCT  = UUID.randomUUID();
    private static final UUID CASH_ACCT = UUID.randomUUID();
    private static final UUID TENANT = UUID.randomUUID();

    @Test
    @DisplayName("balanced two-leg journal passes without exception")
    void enforce_balancedTwoLeg_passes() {
        List<JournalEntry> entries = List.of(
                JournalEntry.debit(ASSETS_ACCT, MonetaryAmount.of("500", NPR), "Asset purchase"),
                JournalEntry.credit(INCOME_ACCT, MonetaryAmount.of("500", NPR), "Revenue")
        );

        assertThatCode(() -> BalanceEnforcer.enforce("REF-001", entries))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("balanced multi-leg journal passes")
    void enforce_balancedMultiLeg_passes() {
        List<JournalEntry> entries = List.of(
                JournalEntry.debit(EXPENSE_ACCT,  MonetaryAmount.of("300", NPR), "Salary expense"),
                JournalEntry.debit(EXPENSE_ACCT,  MonetaryAmount.of("200", NPR), "Bonus expense"),
                JournalEntry.credit(CASH_ACCT, MonetaryAmount.of("500", NPR), "Cash paid")
        );

        assertThatCode(() -> BalanceEnforcer.enforce("REF-002", entries))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("multi-currency journal balanced per-currency passes")
    void enforce_multiCurrencyBalancedPerCurrency_passes() {
        UUID usdAsset = UUID.randomUUID();
        UUID usdLiab = UUID.randomUUID();
        List<JournalEntry> entries = List.of(
                JournalEntry.debit(ASSETS_ACCT, MonetaryAmount.of("500", NPR), "NPR"),
                JournalEntry.credit(CASH_ACCT, MonetaryAmount.of("500", NPR), "NPR out"),
                JournalEntry.debit(usdAsset, MonetaryAmount.of("100.00", USD), "USD in"),
                JournalEntry.credit(usdLiab, MonetaryAmount.of("100.00", USD), "USD out")
        );

        assertThatCode(() -> BalanceEnforcer.enforce("REF-003", entries))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("unbalanced journal throws UnbalancedJournalException")
    void enforce_unbalancedJournal_throws() {
        List<JournalEntry> entries = List.of(
                JournalEntry.debit(ASSETS_ACCT, MonetaryAmount.of("500", NPR), "Asset"),
                JournalEntry.credit(INCOME_ACCT, MonetaryAmount.of("400", NPR), "Revenue mismatch")
        );

        assertThatThrownBy(() -> BalanceEnforcer.enforce("REF-UNBAL", entries))
                .isInstanceOf(UnbalancedJournalException.class)
                .hasMessageContaining("NPR");
    }

    @Test
    @DisplayName("single-currency with debit excess throws")
    void enforce_debitExcess_throws() {
        List<JournalEntry> entries = List.of(
                JournalEntry.debit(ASSETS_ACCT, MonetaryAmount.of("1000", NPR), "Over-debit"),
                JournalEntry.credit(INCOME_ACCT, MonetaryAmount.of("700", NPR), "Credit 1"),
                JournalEntry.credit(CASH_ACCT, MonetaryAmount.of("200", NPR), "Credit 2")
                // 1000 debit vs 900 credit → unbalanced
        );

        assertThatThrownBy(() -> BalanceEnforcer.enforce("REF-DEBIT-EXCESS", entries))
                .isInstanceOf(UnbalancedJournalException.class);
    }

    @Test
    @DisplayName("enforce(Journal) delegates to enforce(reference, entries)")
    void enforce_journal_delegates() {
        Journal journal = Journal.of("REF-J", "Test journal", TENANT, List.of(
                JournalEntry.debit(ASSETS_ACCT, MonetaryAmount.of("100", NPR), "D"),
                JournalEntry.credit(INCOME_ACCT, MonetaryAmount.of("100", NPR), "C")
        ));

        assertThatCode(() -> BalanceEnforcer.enforce(journal))
                .doesNotThrowAnyException();
    }
}
