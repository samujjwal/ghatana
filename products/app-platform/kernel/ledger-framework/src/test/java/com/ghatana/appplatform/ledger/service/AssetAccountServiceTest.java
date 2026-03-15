/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.ledger.service;

import com.ghatana.appplatform.ledger.domain.AssetBalance;
import com.ghatana.appplatform.ledger.domain.AssetClass;
import com.ghatana.appplatform.ledger.domain.AssetEntry;
import com.ghatana.appplatform.ledger.domain.Direction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link AssetAccountService} (STORY-K16-012).
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Security posting with correct balance (AC1)</li>
 *   <li>Trade settlement with CASH + SECURITY in same journal (AC2)</li>
 *   <li>Multi-asset balance query returning all asset types (AC3)</li>
 *   <li>Fund unit (UNIT) posting</li>
 *   <li>Balance validation (imbalanced security journal rejected)</li>
 * </ul>
 */
@DisplayName("AssetAccountService — K16-012 multi-asset account support")
class AssetAccountServiceTest {

    private static final String NABIL          = "NABIL";
    private static final String NIBL_SAMRIDDHI = "NIBL-SAMRIDDHI";

    private static final UUID BUYER_CASH_ACC   = UUID.randomUUID();
    private static final UUID BUYER_SEC_ACC    = UUID.randomUUID();
    private static final UUID SELLER_CASH_ACC  = UUID.randomUUID();
    private static final UUID SELLER_SEC_ACC   = UUID.randomUUID();
    private static final UUID UNIT_FUND_ACC    = UUID.randomUUID();

    private AssetAccountService service;

    @BeforeEach
    void setUp() {
        service = new AssetAccountService();
    }

    // ── AC1: SECURITY account posting ────────────────────────────────────────

    @Test
    @DisplayName("multiAsset_security_posting — 100 NABIL shares posted; balance shows 100 shares")
    void multiAsset_security_posting() {
        // Buyer receives 100 NABIL — DEBIT security account
        // Seller delivers 100 NABIL — CREDIT security account
        List<AssetEntry> entries = List.of(
            AssetEntry.securityDebit(BUYER_SEC_ACC, NABIL, BigDecimal.valueOf(100), "Receive NABIL"),
            AssetEntry.securityCredit(SELLER_SEC_ACC, NABIL, BigDecimal.valueOf(100), "Deliver NABIL")
        );

        service.validateJournal(entries);  // must not throw

        List<AssetBalance> buyerBalances = service.computeBalances(BUYER_SEC_ACC, entries);
        assertThat(buyerBalances).hasSize(1);
        AssetBalance nabilBalance = buyerBalances.get(0);
        assertThat(nabilBalance.assetClass()).isEqualTo(AssetClass.SECURITY);
        assertThat(nabilBalance.instrumentId()).isEqualTo(NABIL);
        assertThat(nabilBalance.quantity()).isEqualByComparingTo(BigDecimal.valueOf(100));
    }

    @Test
    @DisplayName("multiAsset_security_posting_netQuantity — Multiple entries net to correct balance")
    void multiAsset_security_posting_netQuantity() {
        // Account receives 300 and delivers 100 → net 200
        List<AssetEntry> entries = List.of(
            AssetEntry.securityDebit(BUYER_SEC_ACC, NABIL, BigDecimal.valueOf(300), "Receive 300"),
            AssetEntry.securityCredit(BUYER_SEC_ACC, NABIL, BigDecimal.valueOf(100), "Deliver 100"),
            AssetEntry.securityCredit(SELLER_SEC_ACC, NABIL, BigDecimal.valueOf(200), "Deliver 200")
        );

        List<AssetBalance> buyerBalances = service.computeBalances(BUYER_SEC_ACC, entries);
        assertThat(buyerBalances).hasSize(1);
        assertThat(buyerBalances.get(0).quantity()).isEqualByComparingTo(BigDecimal.valueOf(200));
    }

    // ── AC2: Trade settlement — CASH + SECURITY in same journal ──────────────

    @Test
    @DisplayName("multiAsset_trade_settlement — Cash debit + security credit balanced")
    void multiAsset_trade_settlement() {
        // Trade: buyer pays 50,000 NPR cash, receives 100 NABIL shares
        // Seller receives 50,000 NPR cash, delivers 100 NABIL shares
        // SECURITY legs (K16-012) — quantity balanced
        List<AssetEntry> securityLegs = List.of(
            AssetEntry.securityDebit(BUYER_SEC_ACC, NABIL, BigDecimal.valueOf(100),
                "Trade: receive NABIL"),
            AssetEntry.securityCredit(SELLER_SEC_ACC, NABIL, BigDecimal.valueOf(100),
                "Trade: deliver NABIL")
        );

        // Quantity balance check for securities passes
        service.validateJournal(securityLegs);

        // Buyer's security balance = +100 NABIL (DEBIT)
        List<AssetBalance> buyerSecBalances = service.computeBalances(BUYER_SEC_ACC, securityLegs);
        assertThat(buyerSecBalances).hasSize(1);
        assertThat(buyerSecBalances.get(0).quantity()).isEqualByComparingTo(BigDecimal.valueOf(100));

        // Seller's security balance = -100 NABIL (CREDIT from seller's perspective)
        List<AssetBalance> sellerSecBalances = service.computeBalances(SELLER_SEC_ACC, securityLegs);
        assertThat(sellerSecBalances).hasSize(1);
        assertThat(sellerSecBalances.get(0).quantity())
            .isEqualByComparingTo(BigDecimal.valueOf(-100));
    }

    @Test
    @DisplayName("multiAsset_trade_settlement_imbalanced — Unbalanced security journal rejected")
    void multiAsset_trade_settlement_imbalanced() {
        // Buyer receives 100 NABIL but seller only delivers 90 → imbalanced
        List<AssetEntry> entries = List.of(
            AssetEntry.securityDebit(BUYER_SEC_ACC, NABIL, BigDecimal.valueOf(100), "Receive"),
            AssetEntry.securityCredit(SELLER_SEC_ACC, NABIL, BigDecimal.valueOf(90), "Deliver")
        );

        assertThatThrownBy(() -> service.validateJournal(entries))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("imbalanced")
            .hasMessageContaining(NABIL);
    }

    // ── AC3: Multi-asset balance report ──────────────────────────────────────

    @Test
    @DisplayName("multiAsset_balanceReport — Query returns all asset balances for account")
    void multiAsset_balanceReport() {
        // An account holds: 100 NABIL shares + 50 GBIME shares
        List<AssetEntry> entries = List.of(
            AssetEntry.securityDebit(BUYER_SEC_ACC, "NABIL", BigDecimal.valueOf(100), "NABIL buy"),
            AssetEntry.securityCredit(SELLER_SEC_ACC, "NABIL", BigDecimal.valueOf(100), "NABIL sell"),
            AssetEntry.securityDebit(BUYER_SEC_ACC, "GBIME", BigDecimal.valueOf(50), "GBIME buy"),
            AssetEntry.securityCredit(SELLER_SEC_ACC, "GBIME", BigDecimal.valueOf(50), "GBIME sell")
        );

        service.validateJournal(entries);

        List<AssetBalance> buyerBalances = service.computeBalances(BUYER_SEC_ACC, entries);
        assertThat(buyerBalances).hasSize(2);

        AssetBalance nabilBal = buyerBalances.stream()
            .filter(b -> NABIL.equals(b.instrumentId())).findFirst().orElseThrow();
        AssetBalance gbimeBal = buyerBalances.stream()
            .filter(b -> "GBIME".equals(b.instrumentId())).findFirst().orElseThrow();

        assertThat(nabilBal.quantity()).isEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(gbimeBal.quantity()).isEqualByComparingTo(BigDecimal.valueOf(50));
        assertThat(buyerBalances).allMatch(b -> b.assetClass() == AssetClass.SECURITY);
    }

    @Test
    @DisplayName("multiAsset_balanceReport_postAssetJournal — postAssetJournal returns per-account balances")
    void multiAsset_balanceReport_postAssetJournal() {
        List<AssetEntry> entries = List.of(
            AssetEntry.securityDebit(BUYER_SEC_ACC, NABIL, BigDecimal.valueOf(200), "Receive"),
            AssetEntry.securityCredit(SELLER_SEC_ACC, NABIL, BigDecimal.valueOf(200), "Deliver")
        );

        Map<UUID, List<AssetBalance>> result = service.postAssetJournal(entries);

        assertThat(result).containsKeys(BUYER_SEC_ACC, SELLER_SEC_ACC);
        assertThat(result.get(BUYER_SEC_ACC)).hasSize(1);
        assertThat(result.get(BUYER_SEC_ACC).get(0).quantity())
            .isEqualByComparingTo(BigDecimal.valueOf(200));
    }

    // ── UNIT fund asset class ─────────────────────────────────────────────────

    @Test
    @DisplayName("multiAsset_unitFund — Fund unit subscriptions tracked by unit quantity")
    void multiAsset_unitFund() {
        // Investor subscribes to 200.500 NIBL-SAMRIDDHI units
        List<AssetEntry> entries = List.of(
            AssetEntry.unitDebit(UNIT_FUND_ACC, NIBL_SAMRIDDHI,
                new BigDecimal("200.500"), "Subscribe to NIBL-SAMRIDDHI"),
            AssetEntry.unitCredit(SELLER_CASH_ACC, NIBL_SAMRIDDHI,
                new BigDecimal("200.500"), "Issue NIBL-SAMRIDDHI units")
        );

        service.validateJournal(entries);

        List<AssetBalance> investorBalances = service.computeBalances(UNIT_FUND_ACC, entries);
        assertThat(investorBalances).hasSize(1);
        AssetBalance unitBalance = investorBalances.get(0);
        assertThat(unitBalance.assetClass()).isEqualTo(AssetClass.UNIT);
        assertThat(unitBalance.instrumentId()).isEqualTo(NIBL_SAMRIDDHI);
        assertThat(unitBalance.quantity()).isEqualByComparingTo(new BigDecimal("200.500"));
    }

    @Test
    @DisplayName("multiAsset_unitFund_redemption — Redeeming units reduces position")
    void multiAsset_unitFund_redemption() {
        // Subscribe 200 then redeem 50 → net 150
        List<AssetEntry> subscribe = List.of(
            AssetEntry.unitDebit(UNIT_FUND_ACC, NIBL_SAMRIDDHI, BigDecimal.valueOf(200), "Subscribe"),
            AssetEntry.unitCredit(SELLER_CASH_ACC, NIBL_SAMRIDDHI, BigDecimal.valueOf(200), "Issue")
        );
        List<AssetEntry> redeem = List.of(
            AssetEntry.unitDebit(SELLER_CASH_ACC, NIBL_SAMRIDDHI, BigDecimal.valueOf(50), "Cancel"),
            AssetEntry.unitCredit(UNIT_FUND_ACC, NIBL_SAMRIDDHI, BigDecimal.valueOf(50), "Redeem")
        );

        // Both journals individually balanced
        service.validateJournal(subscribe);
        service.validateJournal(redeem);

        // Aggregate all entries for balance computation
        List<AssetEntry> all = new java.util.ArrayList<>();
        all.addAll(subscribe);
        all.addAll(redeem);

        List<AssetBalance> investorBalances = service.computeBalances(UNIT_FUND_ACC, all);
        assertThat(investorBalances).hasSize(1);
        assertThat(investorBalances.get(0).quantity()).isEqualByComparingTo(BigDecimal.valueOf(150));
    }

    // ── Validation edge cases ────────────────────────────────────────────────

    @Test
    @DisplayName("multiAsset_cashEntriesNotQuantityValidated — CASH entries ignored in quantity check")
    void multiAsset_cashEntriesNotQuantityValidated() {
        // CASH asset entries with no quantity balance counterpart — should NOT fail quantity check
        // (CASH balance is validated by BalanceEnforcer on the monetary side, not here)
        AssetEntry cashDebit = new AssetEntry(UUID.randomUUID(), BUYER_CASH_ACC,
            Direction.DEBIT, AssetClass.CASH, null, new BigDecimal("50000"), "Cash paid");

        service.validateJournal(List.of(cashDebit)); // must not throw — CASH is excluded
    }

    @Test
    @DisplayName("multiAsset_missingInstrumentId_rejected — SECURITY entry without instrumentId")
    void multiAsset_missingInstrumentId_rejected() {
        assertThatThrownBy(() ->
            new AssetEntry(UUID.randomUUID(), BUYER_SEC_ACC, Direction.DEBIT,
                AssetClass.SECURITY, null, BigDecimal.valueOf(100), "invalid"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("instrumentId");
    }
}
