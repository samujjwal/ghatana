/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.products.finance.domains.compliance.service;

import com.ghatana.products.finance.domains.compliance.domain.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link KycValidationService}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for KYC status-based trading validation (D07-006)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("KycValidationService — Unit Tests")
class KycValidationServiceTest {

    private final KycValidationService service = new KycValidationService();

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private ComplianceCheckRequest requestWith(String kycStatus, String orderSide) {
        return new ComplianceCheckRequest(
                "ord-1", "client-1", "NICA", "acc-1",
                "NP", orderSide,
                new BigDecimal("100"), new BigDecimal("250"),
                new BigDecimal("25000"), kycStatus, 15
        );
    }

    // ─── Tests ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("VERIFIED status — PASS for BUY orders")
    void verified_buy_passes() {
        var detail = service.evaluate(requestWith("VERIFIED", "BUY"));

        assertThat(detail.result()).isEqualTo(ComplianceStatus.PASS);
        assertThat(detail.ruleId()).isEqualTo("KYC_CHECK");
    }

    @Test
    @DisplayName("VERIFIED status — PASS for SELL orders")
    void verified_sell_passes() {
        var detail = service.evaluate(requestWith("VERIFIED", "SELL"));

        assertThat(detail.result()).isEqualTo(ComplianceStatus.PASS);
    }

    @Test
    @DisplayName("EXPIRED status + BUY side — FAIL (new purchases blocked)")
    void expired_buy_fails() {
        var detail = service.evaluate(requestWith("EXPIRED", "BUY"));

        assertThat(detail.result()).isEqualTo(ComplianceStatus.FAIL);
        assertThat(detail.reason()).containsIgnoringCase("expired");
        assertThat(detail.reason()).containsIgnoringCase("purchase");
    }

    @Test
    @DisplayName("EXPIRED status + SELL side — PASS (position management allowed)")
    void expired_sell_passes() {
        var detail = service.evaluate(requestWith("EXPIRED", "SELL"));

        assertThat(detail.result()).isEqualTo(ComplianceStatus.PASS);
        assertThat(detail.reason()).containsIgnoringCase("sell allowed");
    }

    @Test
    @DisplayName("PENDING status — FAIL for all orders")
    void pending_fails() {
        var buy = service.evaluate(requestWith("PENDING", "BUY"));
        var sell = service.evaluate(requestWith("PENDING", "SELL"));

        assertThat(buy.result()).isEqualTo(ComplianceStatus.FAIL);
        assertThat(sell.result()).isEqualTo(ComplianceStatus.FAIL);
        assertThat(buy.reason()).containsIgnoringCase("pending");
    }

    @Test
    @DisplayName("REJECTED status — FAIL for all orders")
    void rejected_fails() {
        var detail = service.evaluate(requestWith("REJECTED", "BUY"));

        assertThat(detail.result()).isEqualTo(ComplianceStatus.FAIL);
        assertThat(detail.reason()).containsIgnoringCase("rejected");
    }

    @Test
    @DisplayName("SUSPENDED status — FAIL for all orders")
    void suspended_fails() {
        var detail = service.evaluate(requestWith("SUSPENDED", "SELL"));

        assertThat(detail.result()).isEqualTo(ComplianceStatus.FAIL);
        assertThat(detail.reason()).containsIgnoringCase("suspended");
    }

    @Test
    @DisplayName("unknown KYC status — FAIL with descriptive message")
    void unknownStatus_fails() {
        var detail = service.evaluate(requestWith("INVALID_STATUS_XYZ", "BUY"));

        assertThat(detail.result()).isEqualTo(ComplianceStatus.FAIL);
        assertThat(detail.reason()).containsIgnoringCase("Unknown KYC status");
        assertThat(detail.reason()).contains("INVALID_STATUS_XYZ");
    }

    @Test
    @DisplayName("ruleId is always KYC_CHECK for all branches")
    void ruleId_isAlwaysKycCheck() {
        for (String status : new String[]{"VERIFIED", "EXPIRED", "PENDING", "REJECTED", "SUSPENDED"}) {
            var detail = service.evaluate(requestWith(status, "BUY"));
            assertThat(detail.ruleId()).as("ruleId for status %s", status).isEqualTo("KYC_CHECK");
        }
    }
}
