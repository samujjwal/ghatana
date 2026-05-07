package com.ghatana.digitalmarketing.domain.localization;

import java.util.Currency;
import java.util.Locale;
import java.util.Objects;

/**
 * Currency configuration for a locale/region.
 *
 * @doc.type class
 * @doc.purpose Currency configuration with locale-specific formatting (P3-005)
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public final class CurrencyConfig {

    private final String currencyCode;
    private final String localeCode;
    private final String symbol;
    private final int decimalPlaces;
    private final String decimalSeparator;
    private final String thousandsSeparator;
    private final String symbolPosition;

    private CurrencyConfig(Builder builder) {
        this.currencyCode = Objects.requireNonNull(builder.currencyCode, "currencyCode must not be null");
        this.localeCode = Objects.requireNonNull(builder.localeCode, "localeCode must not be null");
        this.symbol = builder.symbol != null ? builder.symbol : Currency.getInstance(currencyCode).getSymbol(Locale.forLanguageTag(localeCode));
        this.decimalPlaces = builder.decimalPlaces;
        this.decimalSeparator = builder.decimalSeparator;
        this.thousandsSeparator = builder.thousandsSeparator;
        this.symbolPosition = builder.symbolPosition;
    }

    public String getCurrencyCode() { return currencyCode; }
    public String getLocaleCode() { return localeCode; }
    public String getSymbol() { return symbol; }
    public int getDecimalPlaces() { return decimalPlaces; }
    public String getDecimalSeparator() { return decimalSeparator; }
    public String getThousandsSeparator() { return thousandsSeparator; }
    public String getSymbolPosition() { return symbolPosition; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CurrencyConfig)) return false;
        return currencyCode.equals(((CurrencyConfig) o).currencyCode) && localeCode.equals(((CurrencyConfig) o).localeCode);
    }

    @Override
    public int hashCode() { return currencyCode.hashCode() ^ localeCode.hashCode(); }

    @Override
    public String toString() {
        return "CurrencyConfig{currencyCode='" + currencyCode + "', localeCode='" + localeCode + "'}";
    }

    public Builder toBuilder() {
        return new Builder()
            .currencyCode(currencyCode)
            .localeCode(localeCode)
            .symbol(symbol)
            .decimalPlaces(decimalPlaces)
            .decimalSeparator(decimalSeparator)
            .thousandsSeparator(thousandsSeparator)
            .symbolPosition(symbolPosition);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String currencyCode;
        private String localeCode;
        private String symbol;
        private int decimalPlaces = 2;
        private String decimalSeparator = ".";
        private String thousandsSeparator = ",";
        private String symbolPosition = "before";

        public Builder currencyCode(String v) { this.currencyCode = v; return this; }
        public Builder localeCode(String v) { this.localeCode = v; return this; }
        public Builder symbol(String v) { this.symbol = v; return this; }
        public Builder decimalPlaces(int v) { this.decimalPlaces = v; return this; }
        public Builder decimalSeparator(String v) { this.decimalSeparator = v; return this; }
        public Builder thousandsSeparator(String v) { this.thousandsSeparator = v; return this; }
        public Builder symbolPosition(String v) { this.symbolPosition = v; return this; }

        public CurrencyConfig build() {
            if (currencyCode == null || currencyCode.isBlank()) throw new IllegalArgumentException("currencyCode must not be blank");
            if (localeCode == null || localeCode.isBlank()) throw new IllegalArgumentException("localeCode must not be blank");
            return new CurrencyConfig(this);
        }
    }
}
