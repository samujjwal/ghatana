package com.ghatana.appplatform.risk.port;

import com.ghatana.appplatform.risk.domain.MarginRecord;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * @doc.type    Port (Secondary)
 * @doc.purpose Defines the contract for reading and updating margin account state (D06-001).
 *              Implemented by {@code RedisMarginStore} (hot path) and optionally by a
 *              Postgres fallback for persistence.
 * @doc.layer   Domain Port
 * @doc.pattern Hexagonal Architecture — secondary port
 */
public interface MarginStore {

    /**
     * Retrieve the current margin record for a client+account.
     * Returns empty if no margin account has been set up.
     */
    Optional<MarginRecord> find(String clientId, String accountId);

    /**
     * Atomically reserve {@code amount} of margin.
     * Returns the updated record if sufficient margin exists, or empty if not.
     * Implementations must use a Redis WATCH/MULTI or Lua script to ensure atomicity.
     */
    Optional<MarginRecord> reserveAtomic(String clientId, String accountId, BigDecimal amount);

    /**
     * Release previously reserved margin back to available.
     * Called when an order is filled, cancelled, or rejected.
     */
    void release(String clientId, String accountId, BigDecimal amount);

    /** Persist or update the full margin record (source-of-truth write). */
    void upsert(MarginRecord record);
}
