package com.ghatana.appplatform.marketdata.port;

import com.ghatana.appplatform.marketdata.domain.MarketTick;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * @doc.type       Port (driven / secondary)
 * @doc.purpose    Write/read API for the market tick timeseries store.
 *                 The primary adapter is TimescaleMarketDataStore (TimescaleDB hypertable).
 *                 D04-001: ingest path.
 * @doc.layer      Port
 * @doc.pattern    Hexagonal / Repository Port
 */
public interface MarketDataStore {

    /** Persist a single tick. */
    Promise<Void> ingestTick(MarketTick tick);

    /**
     * Retrieve all ticks for an instrument in the given UTC time range (inclusive).
     * Results are ordered by timestampUtc ASC.
     */
    Promise<List<MarketTick>> findTicks(String instrumentId, Instant from, Instant to);

    /**
     * Get the most recent tick for the given instrument.
     * Returns empty if no ticks have been received yet.
     */
    Promise<Optional<MarketTick>> findLatestTick(String instrumentId);
}
