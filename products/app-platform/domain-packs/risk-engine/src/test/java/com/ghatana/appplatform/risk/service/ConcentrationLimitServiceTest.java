/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.risk.service;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.appplatform.risk.domain.*;
import com.ghatana.appplatform.risk.domain.RiskCheckResult.RiskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ConcentrationLimitService}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for portfolio concentration limit enforcement (D06-003)
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ConcentrationLimitService — Unit Tests")
class ConcentrationLimitServiceTest extends EventloopTestBase {

    @Mock private ConcentrationLimitService.PortfolioValuePort portfolioValuePort;

    private ConcentrationLimitService service;

    private static final String CLIENT   = "client-001";
    private static final String ACCOUNT  = "acct-001";
    private static final String INSTR    = "instr-NEPSE-NICA";

    @BeforeEach
    void setUp() {
        service = new ConcentrationLimitService(portfolioValuePort,
                Executors.newSingleThreadExecutor());
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private RiskCheckRequest request(long qty) {
        return new RiskCheckRequest("order-1", CLIENT, ACCOUNT, INSTR,
                MarginType.EQUITY, "BUY", qty, new BigDecimal("200.00"),
                new BigDecimal("200.00").multiply(BigDecimal.valueOf(qty)));
    }

    // ─── Empty portfolio (first trade) ────────────────────────────────────────

    @Test
    @DisplayName("zero total portfolio value (first trade) — APPROVE without concentration check")
    void emptyPortfolio_approveFirstTrade() {
        when(portfolioValuePort.getTotalPortfolioValue(eq(CLIENT), eq(ACCOUNT)))
                .thenReturn(BigDecimal.ZERO);

        RiskCheckResult result = runPromise(() ->
                service.check(request(100), new BigDecimal("200.00")));

        assertThat(result.status()).isEqualTo(RiskStatus.APPROVE);
    }

    // ─── Within limit tests ───────────────────────────────────────────────────

    @Test
    @DisplayName("projected concentration below 10% default limit — APPROVE")
    void projectedConcentrationBelowLimit_approve() {
        // portfolio=100_000, instrument_value=0, buy qty=100 @ 200 → adding 20_000
        // projected conc = 20_000 / 120_000 ≈ 0.166 → should DENY (above 10%)
        // Use small order: qty=5 @ 200 = 1000 → conc = 1000/101000 ≈ 0.99% < 10%
        when(portfolioValuePort.getTotalPortfolioValue(eq(CLIENT), eq(ACCOUNT)))
                .thenReturn(new BigDecimal("100000.00"));
        when(portfolioValuePort.getInstrumentValue(eq(CLIENT), eq(ACCOUNT), eq(INSTR)))
                .thenReturn(new BigDecimal("0.00"));
        when(portfolioValuePort.getMaxConcentration(eq(CLIENT), eq(INSTR)))
                .thenReturn(Optional.empty()); // use default 10%

        RiskCheckResult result = runPromise(() ->
                service.check(request(5), new BigDecimal("200.00")));

        assertThat(result.status()).isEqualTo(RiskStatus.APPROVE);
    }

    @Test
    @DisplayName("projected concentration exactly at 10% — APPROVE (boundary)")
    void projectedConcentrationExactlyAtLimit_approve() {
        // portfolio=90_000, instrument_value=0, adding 10_000 → total=100_000, conc=10% exactly
        when(portfolioValuePort.getTotalPortfolioValue(eq(CLIENT), eq(ACCOUNT)))
                .thenReturn(new BigDecimal("90000.00"));
        when(portfolioValuePort.getInstrumentValue(eq(CLIENT), eq(ACCOUNT), eq(INSTR)))
                .thenReturn(new BigDecimal("0.00"));
        when(portfolioValuePort.getMaxConcentration(eq(CLIENT), eq(INSTR)))
                .thenReturn(Optional.empty()); // default 10%

        // qty=50, price=200 → adding 10_000 to 90_000 portfolio → conc = 10000/100000 = exactly 10%
        RiskCheckResult result = runPromise(() ->
                service.check(request(50), new BigDecimal("200.00")));

        assertThat(result.status()).isEqualTo(RiskStatus.APPROVE);
    }

    // ─── Breach tests ────────────────────────────────────────────────────────

    @Test
    @DisplayName("projected concentration above 10% default limit — DENY with breach message")
    void projectedConcentrationAboveLimit_deny() {
        // portfolio=50_000, instrument_value=0, adding 10_000 → conc = 10_000/60_000 ≈ 16.7%
        when(portfolioValuePort.getTotalPortfolioValue(eq(CLIENT), eq(ACCOUNT)))
                .thenReturn(new BigDecimal("50000.00"));
        when(portfolioValuePort.getInstrumentValue(eq(CLIENT), eq(ACCOUNT), eq(INSTR)))
                .thenReturn(new BigDecimal("0.00"));
        when(portfolioValuePort.getMaxConcentration(eq(CLIENT), eq(INSTR)))
                .thenReturn(Optional.empty()); // default 10%

        RiskCheckResult result = runPromise(() ->
                service.check(request(50), new BigDecimal("200.00")));

        assertThat(result.status()).isEqualTo(RiskStatus.DENY);
        assertThat(result.reason()).isNotBlank();
        assertThat(result.reason()).containsIgnoringCase("concentration");
    }

    @Test
    @DisplayName("existing position plus new order exceeds limit — DENY")
    void existingPositionPlusNew_exceedsLimit_deny() {
        // portfolio=100_000, instrument already at 8_000, adding 5_000 → projected=13% > 10%
        when(portfolioValuePort.getTotalPortfolioValue(eq(CLIENT), eq(ACCOUNT)))
                .thenReturn(new BigDecimal("100000.00"));
        when(portfolioValuePort.getInstrumentValue(eq(CLIENT), eq(ACCOUNT), eq(INSTR)))
                .thenReturn(new BigDecimal("8000.00"));
        when(portfolioValuePort.getMaxConcentration(eq(CLIENT), eq(INSTR)))
                .thenReturn(Optional.empty());

        // qty=25 @ 200 = 5_000 additional → projected instr = 13_000, total = 105_000 → 12.38%
        RiskCheckResult result = runPromise(() ->
                service.check(request(25), new BigDecimal("200.00")));

        assertThat(result.status()).isEqualTo(RiskStatus.DENY);
    }

    // ─── Custom limit tests ───────────────────────────────────────────────────

    @Test
    @DisplayName("custom 20% concentration limit per client — within custom limit is APPROVE")
    void customConcentrationLimit_withinCustomLimit_approve() {
        // portfolio=50_000, instrument_value=0, adding 10_000 → conc=16.7% < custom 20%
        when(portfolioValuePort.getTotalPortfolioValue(eq(CLIENT), eq(ACCOUNT)))
                .thenReturn(new BigDecimal("50000.00"));
        when(portfolioValuePort.getInstrumentValue(eq(CLIENT), eq(ACCOUNT), eq(INSTR)))
                .thenReturn(new BigDecimal("0.00"));
        when(portfolioValuePort.getMaxConcentration(eq(CLIENT), eq(INSTR)))
                .thenReturn(Optional.of(new BigDecimal("0.20"))); // 20% custom

        RiskCheckResult result = runPromise(() ->
                service.check(request(50), new BigDecimal("200.00")));

        assertThat(result.status()).isEqualTo(RiskStatus.APPROVE);
    }

    @Test
    @DisplayName("result includes projected concentration ratio as double")
    void result_includesConcentrationRatio() {
        when(portfolioValuePort.getTotalPortfolioValue(eq(CLIENT), eq(ACCOUNT)))
                .thenReturn(new BigDecimal("100000.00"));
        when(portfolioValuePort.getInstrumentValue(eq(CLIENT), eq(ACCOUNT), eq(INSTR)))
                .thenReturn(new BigDecimal("0.00"));
        when(portfolioValuePort.getMaxConcentration(eq(CLIENT), eq(INSTR)))
                .thenReturn(Optional.empty());

        // qty=5 @ 200 = 1000 → conc = 1000/101000 ≈ 0.0099
        RiskCheckResult result = runPromise(() ->
                service.check(request(5), new BigDecimal("200.00")));

        assertThat(result.marginUtilization()).isGreaterThan(0.0);
        assertThat(result.marginUtilization()).isLessThan(0.02);
    }
}
