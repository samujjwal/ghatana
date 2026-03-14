/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.ledger.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("MonetaryAmount value object")
class MonetaryAmountTest {

    private static final Currency NPR = Currency.NPR;
    private static final Currency USD = Currency.USD;
    private static final Currency BTC = Currency.BTC;

    @Test
    @DisplayName("adds two NPR amounts with correct precision")
    void add_sameNprCurrency_correct() {
        MonetaryAmount a = MonetaryAmount.of("100.50", NPR);
        MonetaryAmount b = MonetaryAmount.of("49.50", NPR);

        MonetaryAmount sum = a.add(b);

        assertThat(sum.getAmount()).isEqualByComparingTo("150.00");
        assertThat(sum.currencyCode()).isEqualTo("NPR");
    }

    @Test
    @DisplayName("subtracts two USD amounts")
    void subtract_sameUsdCurrency_correct() {
        MonetaryAmount a = MonetaryAmount.of("200.00", USD);
        MonetaryAmount b = MonetaryAmount.of("75.25", USD);

        MonetaryAmount result = a.subtract(b);

        assertThat(result.getAmount()).isEqualByComparingTo("124.75");
    }

    @Test
    @DisplayName("multiplies by a scalar factor")
    void multiply_scalar_correct() {
        MonetaryAmount price = MonetaryAmount.of("50.00", NPR);

        MonetaryAmount total = price.multiply(new BigDecimal("3"));

        assertThat(total.getAmount()).isEqualByComparingTo("150.00");
    }

    @Test
    @DisplayName("divides with correct rounding")
    void divide_withRounding_correct() {
        MonetaryAmount total = MonetaryAmount.of("10.00", NPR);

        MonetaryAmount each = total.divide(new BigDecimal("3"));

        // 10/3 = 3.333... rounded HALF_UP to 2 decimals = 3.33
        assertThat(each.getAmount()).isEqualByComparingTo("3.33");
    }

    @Test
    @DisplayName("throws ArithmeticException on divide by zero")
    void divide_byZero_throws() {
        MonetaryAmount amount = MonetaryAmount.of("100.00", NPR);

        assertThatThrownBy(() -> amount.divide(BigDecimal.ZERO))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    @DisplayName("throws IllegalArgumentException on cross-currency addition")
    void add_differentCurrency_throws() {
        MonetaryAmount npm = MonetaryAmount.of("100.00", NPR);
        MonetaryAmount usd = MonetaryAmount.of("100.00", USD);

        assertThatThrownBy(() -> npm.add(usd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NPR")
                .hasMessageContaining("USD");
    }

    @Test
    @DisplayName("BTC has 8 decimal places")
    void btcPrecision_8decimals() {
        MonetaryAmount btc = MonetaryAmount.of("0.12345678", BTC);

        assertThat(btc.getAmount().scale()).isEqualTo(8);
    }

    @Test
    @DisplayName("zero returns amount equal to 0")
    void zero_returnsZeroAmount() {
        MonetaryAmount zero = MonetaryAmount.zero(NPR);

        assertThat(zero.isZero()).isTrue();
        assertThat(zero.isPositive()).isFalse();
    }

    @Test
    @DisplayName("equality uses compareTo so trailing zeros are equal")
    void equality_trailingZeros_equal() {
        MonetaryAmount a = MonetaryAmount.of("100.00", NPR);
        MonetaryAmount b = MonetaryAmount.of("100", NPR);

        assertThat(a).isEqualTo(b);
    }

    @Test
    @DisplayName("isGreaterThan compares amounts correctly")
    void isGreaterThan_correct() {
        assertThat(MonetaryAmount.of("200", NPR).isGreaterThan(MonetaryAmount.of("100", NPR))).isTrue();
        assertThat(MonetaryAmount.of("50", NPR).isGreaterThan(MonetaryAmount.of("100", NPR))).isFalse();
    }

    @Test
    @DisplayName("Japanese Yen has 0 decimal places")
    void jpyPrecision_0decimals() {
        Currency jpy = new Currency("JPY", "Japanese Yen", "¥", 0, RoundingMode.HALF_UP);
        MonetaryAmount amount = MonetaryAmount.of("1000", jpy);

        assertThat(amount.getAmount().scale()).isEqualTo(0);
        assertThat(amount.getAmount()).isEqualByComparingTo("1000");
    }
}
