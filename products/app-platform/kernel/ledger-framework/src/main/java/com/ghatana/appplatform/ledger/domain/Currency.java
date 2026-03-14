/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.ledger.domain;

import java.math.RoundingMode;
import java.util.Objects;

/**
 * Currency definition from the currency registry (K16-010).
 *
 * <p>Captures the ISO 4217 currency code, precision (decimal places), and
 * rounding mode. All monetary amounts validated against their currency's
 * precision before posting.
 *
 * @doc.type record
 * @doc.purpose Currency definition with precision and rounding rules
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public record Currency(
        String code,            // ISO 4217 (NPR, USD, BTC, JPY)
        String name,
        String symbol,
        int decimalPlaces,      // NPR=2, BTC=8, JPY=0
        RoundingMode roundingMode
) {
    public Currency {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
        if (decimalPlaces < 0 || decimalPlaces > 18)
            throw new IllegalArgumentException("decimalPlaces must be 0..18, got " + decimalPlaces);
        Objects.requireNonNull(roundingMode, "roundingMode");
        code = code.toUpperCase();
    }

    // Well-known currencies as constants for testing and seeding
    public static final Currency NPR = new Currency("NPR", "Nepalese Rupee", "रू", 2, RoundingMode.HALF_UP);
    public static final Currency USD = new Currency("USD", "US Dollar", "$", 2, RoundingMode.HALF_UP);
    public static final Currency BTC = new Currency("BTC", "Bitcoin", "₿", 8, RoundingMode.HALF_UP);
    public static final Currency JPY = new Currency("JPY", "Japanese Yen", "¥", 0, RoundingMode.HALF_UP);
}
