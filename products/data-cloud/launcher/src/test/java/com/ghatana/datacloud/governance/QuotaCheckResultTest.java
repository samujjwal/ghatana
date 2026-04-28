/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.governance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Tests for QuotaCheckResult
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("QuotaCheckResult")
class QuotaCheckResultTest {

    @Test
    @DisplayName("permit returns allowed result")
    void permit_returnsAllowedResult() {
        QuotaCheckResult result = QuotaCheckResult.permit();
        assertThat(result.allowed()).isTrue();
        assertThat(result.message()).isNull();
        assertThat(result.quotaValue()).isEqualTo(0);
        assertThat(result.usedAmount()).isEqualTo(0);
    }

    @Test
    @DisplayName("reject returns disallowed result")
    void reject_returnsDisallowedResult() {
        QuotaCheckResult result = QuotaCheckResult.reject("Quota exceeded", 1000, 950);
        assertThat(result.allowed()).isFalse();
        assertThat(result.message()).isEqualTo("Quota exceeded");
        assertThat(result.quotaValue()).isEqualTo(1000);
        assertThat(result.usedAmount()).isEqualTo(950);
    }

    @Test
    @DisplayName("isAllowed returns true for permit")
    void isAllowed_returnsTrueForPermit() {
        QuotaCheckResult result = QuotaCheckResult.permit();
        assertThat(result.isAllowed()).isTrue();
    }

    @Test
    @DisplayName("isAllowed returns false for reject")
    void isAllowed_returnsFalseForReject() {
        QuotaCheckResult result = QuotaCheckResult.reject("Quota exceeded", 1000, 950);
        assertThat(result.isAllowed()).isFalse();
    }
}
