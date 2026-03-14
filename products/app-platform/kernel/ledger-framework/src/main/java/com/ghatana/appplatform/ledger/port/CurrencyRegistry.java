/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.ledger.port;

import com.ghatana.appplatform.ledger.domain.Currency;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Port for the currency registry (K16-010).
 *
 * @doc.type interface
 * @doc.purpose Storage port for currency definitions and precision rules
 * @doc.layer core
 * @doc.pattern Repository
 */
public interface CurrencyRegistry {

    /**
     * Retrieves a currency by its ISO 4217 code.
     *
     * @param code currency code (e.g., "NPR", "USD")
     * @return Optional containing the currency, or empty if not registered
     */
    Promise<Optional<Currency>> getCurrency(String code);

    /**
     * Returns all active currencies in the registry.
     */
    Promise<List<Currency>> listActiveCurrencies();

    /**
     * Registers or updates a currency definition.
     *
     * @param currency the currency to register
     * @return the persisted currency
     */
    Promise<Currency> register(Currency currency);
}
