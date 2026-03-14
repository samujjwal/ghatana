/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.ledger.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Distributes a {@link MonetaryAmount} into {@code n} equal parts, ensuring that
 * the sum of all parts exactly equals the original total (K16-019).
 *
 * <p>Algorithm — <em>largest-remainder</em> method:
 * <ol>
 *   <li>Compute an equal share by integer division at the currency's decimal scale.</li>
 *   <li>Compute the remainder ({@code total - n * share}).</li>
 *   <li>Add one cent to the first {@code remainderUnits} buckets.</li>
 * </ol>
 *
 * <p>This guarantees {@code sum(result) == total} for any total and any {@code n}.
 *
 * @doc.type class
 * @doc.purpose Proportional monetary amount allocation with remainder fairness (K16-019)
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public final class RoundingAllocator {

    private RoundingAllocator() {
        // utility - no instances
    }

    /**
     * Splits {@code total} into exactly {@code n} parts.
     *
     * <p>Parts are returned in order; buckets 0 through
     * {@code (total * scale mod n - 1)} receive one extra unit.
     *
     * @param total  amount to split (must be non-null, may be zero)
     * @param n      number of buckets (must be ≥ 1)
     * @return immutable list of n parts whose sum equals {@code total}
     * @throws IllegalArgumentException if {@code n < 1}
     */
    public static List<MonetaryAmount> allocate(MonetaryAmount total, int n) {
        Objects.requireNonNull(total, "total");
        if (n < 1) throw new IllegalArgumentException("Bucket count n must be >= 1");

        int scale = total.getCurrency().decimalPlaces();
        BigDecimal unscaled = total.getAmount().movePointRight(scale);   // work in integer units
        long totalUnits = unscaled.setScale(0, RoundingMode.HALF_UP).longValue();

        long baseUnits = totalUnits / n;
        int  remainder = (int) (totalUnits % n);

        List<MonetaryAmount> parts = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            long units = baseUnits + (i < remainder ? 1 : 0);
            BigDecimal amount = BigDecimal.valueOf(units).movePointLeft(scale);
            parts.add(MonetaryAmount.of(amount, total.getCurrency()));
        }
        return parts;
    }
}
