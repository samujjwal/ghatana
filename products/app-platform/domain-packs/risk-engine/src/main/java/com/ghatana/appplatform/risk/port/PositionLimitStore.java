package com.ghatana.appplatform.risk.port;

import java.util.Optional;

/**
 * @doc.type    Port (Secondary)
 * @doc.purpose Defines the contract for reading position quantity limits (D06-002).
 *              Limits prevent over-concentration in any single instrument and prohibit short selling.
 * @doc.layer   Domain Port
 * @doc.pattern Hexagonal Architecture — secondary port
 */
public interface PositionLimitStore {

    /**
     * Return the maximum LONG position (unit count) a client may hold for an instrument.
     * Returns empty when no specific limit is configured (use platform default).
     */
    Optional<Long> findMaxLong(String clientId, String instrumentId);

    /**
     * Return the current held quantity for a client+instrument+account.
     * Used to check whether a BUY would exceed max position or a SELL would go short.
     */
    long findCurrentQuantity(String clientId, String instrumentId, String accountId);
}
