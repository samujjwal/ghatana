/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.risk.service;

import com.ghatana.platform.core.event.EventBusPort;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.appplatform.risk.domain.*;
import com.ghatana.appplatform.risk.domain.RiskCheckResult.RiskStatus;
import com.ghatana.appplatform.risk.port.MarginStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link MarginSufficiencyService}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for atomic margin reservation and margin rates by instrument type (D06-001)
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MarginSufficiencyService — Unit Tests")
class MarginSufficiencyServiceTest extends EventloopTestBase {

    @Mock private MarginStore marginStore;
    @Mock private EventBusPort eventBusPort;

    private MarginSufficiencyService service;

    @BeforeEach
    void setUp() {
        doNothing().when(eventBusPort).publish(any());
        service = new MarginSufficiencyService(
                marginStore,
                Executors.newSingleThreadExecutor(),
                eventBusPort,
                new SimpleMeterRegistry()
        );
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private MarginRecord marginWith(BigDecimal deposited, BigDecimal used) {
        return new MarginRecord("client-1", "acc-1", deposited, used, Instant.now());
    }

    private RiskCheckRequest equityRequest(BigDecimal orderValue) {
        return new RiskCheckRequest("ord-1", "client-1", "acc-1",
                "NICA", MarginType.EQUITY, "BUY", 100L, new BigDecimal("250"), orderValue);
    }

    // ─── Margin rate tests ────────────────────────────────────────────────────

    @Test
    @DisplayName("EQUITY margin rate — 50% of order value is required")
    void equityMarginRate_fiftyPercent() {
        BigDecimal orderValue = new BigDecimal("10000");
        // required = 10000 × 0.50 = 5000
        MarginRecord margin = marginWith(new BigDecimal("8000"), BigDecimal.ZERO);
        when(marginStore.find("client-1", "acc-1")).thenReturn(Optional.of(margin));
        when(marginStore.reserveAtomic(eq("client-1"), eq("acc-1"), eq(new BigDecimal("5000.00"))))
                .thenReturn(Optional.of(margin.withReserved(new BigDecimal("5000.00"))));

        RiskCheckResult result = runPromise(() -> service.check(equityRequest(orderValue)));

        assertThat(result.status()).isEqualTo(RiskStatus.APPROVE);
        assertThat(result.requiredMargin()).isEqualByComparingTo("5000.00");
    }

    @Test
    @DisplayName("BOND margin rate — 10% of order value is required")
    void bondMarginRate_tenPercent() {
        BigDecimal orderValue = new BigDecimal("10000");
        // required = 10000 × 0.10 = 1000
        MarginRecord margin = marginWith(new BigDecimal("5000"), BigDecimal.ZERO);
        RiskCheckRequest request = new RiskCheckRequest("ord-1", "client-1", "acc-1",
                "NRB-BOND", MarginType.BOND, "BUY", 5L, new BigDecimal("2000"), orderValue);

        when(marginStore.find("client-1", "acc-1")).thenReturn(Optional.of(margin));
        when(marginStore.reserveAtomic(eq("client-1"), eq("acc-1"), eq(new BigDecimal("1000.00"))))
                .thenReturn(Optional.of(margin.withReserved(new BigDecimal("1000.00"))));

        RiskCheckResult result = runPromise(() -> service.check(request));

        assertThat(result.status()).isEqualTo(RiskStatus.APPROVE);
        assertThat(result.requiredMargin()).isEqualByComparingTo("1000.00");
    }

    @Test
    @DisplayName("ETF margin rate — 30% of order value is required")
    void etfMarginRate_thirtyPercent() {
        BigDecimal orderValue = new BigDecimal("10000");
        MarginRecord margin = marginWith(new BigDecimal("5000"), BigDecimal.ZERO);
        RiskCheckRequest request = new RiskCheckRequest("ord-1", "client-1", "acc-1",
                "NETF", MarginType.ETF, "BUY", 100L, new BigDecimal("100"), orderValue);

        when(marginStore.find("client-1", "acc-1")).thenReturn(Optional.of(margin));
        when(marginStore.reserveAtomic(eq("client-1"), eq("acc-1"), eq(new BigDecimal("3000.00"))))
                .thenReturn(Optional.of(margin.withReserved(new BigDecimal("3000.00"))));

        RiskCheckResult result = runPromise(() -> service.check(request));

        assertThat(result.status()).isEqualTo(RiskStatus.APPROVE);
        assertThat(result.requiredMargin()).isEqualByComparingTo("3000.00");
    }

    @Test
    @DisplayName("MONEY_MARKET margin rate — 5% of order value is required")
    void moneyMarketMarginRate_fivePercent() {
        BigDecimal orderValue = new BigDecimal("10000");
        MarginRecord margin = marginWith(new BigDecimal("1000"), BigDecimal.ZERO);
        RiskCheckRequest request = new RiskCheckRequest("ord-1", "client-1", "acc-1",
                "NMMF", MarginType.MONEY_MARKET, "BUY", 100L, new BigDecimal("100"), orderValue);

        when(marginStore.find("client-1", "acc-1")).thenReturn(Optional.of(margin));
        when(marginStore.reserveAtomic(eq("client-1"), eq("acc-1"), eq(new BigDecimal("500.00"))))
                .thenReturn(Optional.of(margin.withReserved(new BigDecimal("500.00"))));

        RiskCheckResult result = runPromise(() -> service.check(request));

        assertThat(result.status()).isEqualTo(RiskStatus.APPROVE);
        assertThat(result.requiredMargin()).isEqualByComparingTo("500.00");
    }

    // ─── Denial scenarios ─────────────────────────────────────────────────────

    @Test
    @DisplayName("no margin account found — DENY with descriptive message")
    void noMarginAccount_deny() {
        when(marginStore.find("client-1", "acc-1")).thenReturn(Optional.empty());

        RiskCheckResult result = runPromise(() -> service.check(equityRequest(new BigDecimal("10000"))));

        assertThat(result.status()).isEqualTo(RiskStatus.DENY);
        assertThat(result.reason()).contains("No margin account");
        verifyNoMoreInteractions(marginStore);
    }

    @Test
    @DisplayName("insufficient available margin — DENY with available/required in message")
    void insufficientMargin_deny() {
        BigDecimal orderValue = new BigDecimal("10000.00");
        // available = 4000, required = 5000 → deny
        MarginRecord margin = marginWith(new BigDecimal("6000"), new BigDecimal("2000")); // available=4000
        when(marginStore.find("client-1", "acc-1")).thenReturn(Optional.of(margin));

        RiskCheckResult result = runPromise(() -> service.check(equityRequest(orderValue)));

        assertThat(result.status()).isEqualTo(RiskStatus.DENY);
        assertThat(result.reason()).contains("Insufficient margin");
        assertThat(result.availableMargin()).isEqualByComparingTo("4000");
        verify(marginStore, never()).reserveAtomic(any(), any(), any());
    }

    @Test
    @DisplayName("margin reservation conflict (atomic CAS fails) — DENY with retry hint")
    void marginReservationConflict_deny() {
        BigDecimal orderValue = new BigDecimal("10000");
        MarginRecord margin = marginWith(new BigDecimal("10000"), BigDecimal.ZERO);
        when(marginStore.find("client-1", "acc-1")).thenReturn(Optional.of(margin));
        // simulate atomic CAS failure (concurrent update)
        when(marginStore.reserveAtomic(any(), any(), any())).thenReturn(Optional.empty());

        RiskCheckResult result = runPromise(() -> service.check(equityRequest(orderValue)));

        assertThat(result.status()).isEqualTo(RiskStatus.DENY);
        assertThat(result.reason()).containsIgnoringCase("conflict");
    }

    @Test
    @DisplayName("approved reservation — emits MarginReservedEvent")
    void approvedReservation_emitsEvent() {
        BigDecimal orderValue = new BigDecimal("10000");
        MarginRecord margin = marginWith(new BigDecimal("10000"), BigDecimal.ZERO);
        when(marginStore.find("client-1", "acc-1")).thenReturn(Optional.of(margin));
        when(marginStore.reserveAtomic(any(), any(), any()))
                .thenReturn(Optional.of(margin.withReserved(new BigDecimal("5000"))));

        runPromise(() -> service.check(equityRequest(orderValue)));

        verify(eventBusPort).publish(any(MarginSufficiencyService.MarginReservedEvent.class));
    }

    @Test
    @DisplayName("release — delegates to store and emits MarginReleasedEvent")
    void release_delegatesToStoreAndEmitsEvent() {
        doNothing().when(marginStore).release(any(), any(), any());

        runPromise(() -> service.release("client-1", "acc-1", new BigDecimal("5000"), "ord-1"));

        verify(marginStore).release("client-1", "acc-1", new BigDecimal("5000"));
        verify(eventBusPort).publish(any(MarginSufficiencyService.MarginReleasedEvent.class));
    }
}
