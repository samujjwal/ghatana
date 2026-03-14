/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.ledger.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Monetary amount value object combining an amount with its currency (K16-018).
 *
 * <p>Prevents floating-point errors by using {@link BigDecimal} throughout.
 * Arithmetic operations use the currency's precision and rounding mode.
 * Cross-currency arithmetic throws {@link IllegalArgumentException} rather than
 * silently corrupting values.
 *
 * <p>Usage:
 * <pre>{@code
 * MonetaryAmount a = MonetaryAmount.of("100.50", Currency.NPR);
 * MonetaryAmount b = MonetaryAmount.of("49.50", Currency.NPR);
 * MonetaryAmount sum = a.add(b);  // MonetaryAmount{150.00, NPR}
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Monetary value with currency and decimal precision enforcement
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public final class MonetaryAmount {

    private final BigDecimal amount;
    private final Currency currency;

    private MonetaryAmount(BigDecimal amount, Currency currency) {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        this.currency = currency;
        // Scale to the currency's declared decimal places
        this.amount = amount.setScale(currency.decimalPlaces(), currency.roundingMode());
    }

    /**
     * Creates a monetary amount, scaling to the currency's precision.
     *
     * @param amount   string representation of amount (e.g., "100.50")
     * @param currency target currency
     * @return MonetaryAmount
     * @throws NumberFormatException if amount string is invalid
     */
    public static MonetaryAmount of(String amount, Currency currency) {
        return new MonetaryAmount(new BigDecimal(Objects.requireNonNull(amount)), currency);
    }

    /**
     * Creates a monetary amount from an existing BigDecimal.
     */
    public static MonetaryAmount of(BigDecimal amount, Currency currency) {
        return new MonetaryAmount(Objects.requireNonNull(amount), currency);
    }

    /**
     * Creates a zero amount in the given currency.
     */
    public static MonetaryAmount zero(Currency currency) {
        return new MonetaryAmount(BigDecimal.ZERO, currency);
    }

    /**
     * Adds two monetary amounts. Both must be the same currency.
     *
     * @throws IllegalArgumentException if currencies differ
     */
    public MonetaryAmount add(MonetaryAmount other) {
        requireSameCurrency(other);
        return new MonetaryAmount(amount.add(other.amount), currency);
    }

    /**
     * Subtracts another amount. Both must be the same currency.
     *
     * @throws IllegalArgumentException if currencies differ
     */
    public MonetaryAmount subtract(MonetaryAmount other) {
        requireSameCurrency(other);
        return new MonetaryAmount(amount.subtract(other.amount), currency);
    }

    /**
     * Multiplies by a scalar factor (e.g., for fee calculations).
     */
    public MonetaryAmount multiply(BigDecimal factor) {
        return new MonetaryAmount(amount.multiply(factor), currency);
    }

    /**
     * Divides by a scalar divisor using the currency's rounding mode.
     */
    public MonetaryAmount divide(BigDecimal divisor) {
        if (divisor.compareTo(BigDecimal.ZERO) == 0)
            throw new ArithmeticException("Cannot divide MonetaryAmount by zero");
        BigDecimal result = amount.divide(divisor, currency.decimalPlaces(), currency.roundingMode());
        return new MonetaryAmount(result, currency);
    }

    /**
     * True if this amount is greater than the other.
     *
     * @throws IllegalArgumentException if currencies differ
     */
    public boolean isGreaterThan(MonetaryAmount other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount) > 0;
    }

    /**
     * True if this amount is positive (> 0).
     */
    public boolean isPositive() {
        return amount.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * True if this amount is zero.
     */
    public boolean isZero() {
        return amount.compareTo(BigDecimal.ZERO) == 0;
    }

    /**
     * Returns the underlying BigDecimal at the currency's precision.
     */
    public BigDecimal getAmount() {
        return amount;
    }

    public Currency getCurrency() {
        return currency;
    }

    /**
     * Currency code shortcut.
     */
    public String currencyCode() {
        return currency.code();
    }

    private void requireSameCurrency(MonetaryAmount other) {
        if (!currency.code().equals(other.currency.code())) {
            throw new IllegalArgumentException(
                    "Cannot operate on different currencies: " + currency.code() + " vs " + other.currency.code());
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof MonetaryAmount other)) return false;
        return currency.code().equals(other.currency.code()) &&
               amount.compareTo(other.amount) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(currency.code(), amount.stripTrailingZeros());
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency.code();
    }
}
