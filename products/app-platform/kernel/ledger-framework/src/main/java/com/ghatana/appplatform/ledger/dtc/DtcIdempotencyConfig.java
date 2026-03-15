/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.ledger.dtc;

import com.ghatana.appplatform.eventstore.idempotency.IdempotencyGuard;
import com.ghatana.appplatform.eventstore.idempotency.IdempotencyStore;

import java.util.Objects;

/**
 * DTC-specific configuration and factory for idempotency enforcement
 * (STORY-K17-011).
 *
 * <p>Extends the K-05 idempotency infrastructure with DTC-specific settings:
 * <ul>
 *   <li>Namespace prefix {@value #NAMESPACE} isolates DTC command keys from
 *       other platform consumers of the shared idempotency store.</li>
 *   <li>Settlement commands use a 7-day TTL ({@value #SETTLEMENT_TTL_SECONDS}s)
 *       to cover T+2 settlement windows, overriding the platform default 24 h.</li>
 *   <li>Keys are formatted as {@code dtc:{commandType}:{commandId}} using
 *       {@link #buildKey(String, String)}.</li>
 * </ul>
 *
 * <p>DTC does <em>not</em> create a separate Redis cluster. It registers its
 * commands inside the shared K-05 Redis idempotency store with a discriminating
 * namespace prefix.
 *
 * @doc.type class
 * @doc.purpose DTC idempotency namespace config and guard factory (K17-011)
 * @doc.layer product
 * @doc.pattern Service
 */
public final class DtcIdempotencyConfig {

    /** Namespace prefix for all DTC command idempotency keys. */
    public static final String NAMESPACE = "dtc";

    /**
     * 7-day TTL in seconds: covers T+2 settlement windows plus buffer.
     * Settlement commands must survive across a weekend + bank holiday.
     */
    public static final int SETTLEMENT_TTL_SECONDS = 7 * 24 * 3600; // 604 800 s

    /** Default TTL for non-settlement DTC commands (24 hours). */
    public static final int DEFAULT_TTL_SECONDS = 24 * 3600;

    private DtcIdempotencyConfig() {
        // utility class
    }

    /**
     * Build a namespaced DTC idempotency key.
     *
     * <p>Format: {@code dtc:{commandType}:{commandId}}
     * <br>Examples:
     * <ul>
     *   <li>{@code dtc:settle:dvp-00123}</li>
     *   <li>{@code dtc:cancel:tx-abc}</li>
     * </ul>
     *
     * @param commandType  short command type identifier (e.g. "settle", "cancel")
     * @param commandId    the unique command/request identifier (from X-DTC-Command-Id header)
     * @return fully-qualified namespaced key
     */
    public static String buildKey(String commandType, String commandId) {
        Objects.requireNonNull(commandType, "commandType");
        Objects.requireNonNull(commandId, "commandId");
        return NAMESPACE + ":" + commandType + ":" + commandId;
    }

    /**
     * Create an {@link IdempotencyGuard} configured for DTC settlement commands
     * (7-day TTL).
     *
     * @param store the K-05 shared idempotency store (Redis-backed in production)
     * @return guard pre-configured with settlement TTL
     */
    public static IdempotencyGuard settlementGuard(IdempotencyStore store) {
        Objects.requireNonNull(store, "store");
        return new IdempotencyGuard(store, SETTLEMENT_TTL_SECONDS);
    }

    /**
     * Create an {@link IdempotencyGuard} configured for general DTC command
     * endpoints (24-hour TTL).
     *
     * @param store the K-05 shared idempotency store
     * @return guard pre-configured with default TTL
     */
    public static IdempotencyGuard defaultGuard(IdempotencyStore store) {
        Objects.requireNonNull(store, "store");
        return new IdempotencyGuard(store, DEFAULT_TTL_SECONDS);
    }
}
