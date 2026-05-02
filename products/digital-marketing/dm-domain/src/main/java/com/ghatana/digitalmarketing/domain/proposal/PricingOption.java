package com.ghatana.digitalmarketing.domain.proposal;

import java.util.Objects;

/**
 * A pricing option included in a DMOS proposal.
 *
 * <p>Represents one structured pricing choice: its type, amount, currency, and
 * a human-readable description of what is included.</p>
 *
 * @param pricingType short identifier for the pricing model
 *                    (e.g. {@code "MONTHLY_RETAINER"}, {@code "ONE_TIME_SETUP"}, {@code "PROJECT_FEE"})
 * @param amount      monetary amount (must be &gt;= 0)
 * @param currency    ISO 4217 currency code (e.g. {@code "USD"})
 * @param description human-readable description of what this price covers
 *
 * @doc.type class
 * @doc.purpose Pricing option value object for DMOS proposals
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record PricingOption(
        String pricingType,
        double amount,
        String currency,
        String description) {

    /**
     * Compact constructor — validates all fields.
     */
    public PricingOption {
        Objects.requireNonNull(pricingType, "pricingType must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        Objects.requireNonNull(description, "description must not be null");
        if (pricingType.isBlank()) {
            throw new IllegalArgumentException("pricingType must not be blank");
        }
        if (currency.isBlank()) {
            throw new IllegalArgumentException("currency must not be blank");
        }
        if (description.isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }
        if (amount < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
    }
}
