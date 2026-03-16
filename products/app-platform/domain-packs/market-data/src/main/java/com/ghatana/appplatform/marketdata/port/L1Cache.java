package com.ghatana.appplatform.marketdata.port;

import com.ghatana.appplatform.marketdata.domain.L1Quote;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * @doc.type       Port (driven / secondary)
 * @doc.purpose    Read/write port for the L1 top-of-book quote cache.
 *                 Primary adapter: RedisL1Cache (Jedis).
 *                 D04-004: L1 cache.
 * @doc.layer      Port
 * @doc.pattern    Hexagonal / Cache Port
 */
public interface L1Cache {

    /** Store or refresh the L1 quote for one instrument. */
    Promise<Void> updateL1(L1Quote quote);

    /** Retrieve the current L1 quote for one instrument. */
    Promise<Optional<L1Quote>> getL1(String instrumentId);

    /** Retrieve all cached L1 quotes (used for snapshot on WebSocket connect). */
    Promise<List<L1Quote>> getAllL1();
}
