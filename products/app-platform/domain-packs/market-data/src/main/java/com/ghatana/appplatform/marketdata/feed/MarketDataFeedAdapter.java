package com.ghatana.appplatform.marketdata.feed;

import com.ghatana.appplatform.marketdata.domain.MarketTick;

import java.util.List;
import java.util.function.Consumer;

/**
 * @doc.type       Port (driving / plugin interface)
 * @doc.purpose    T3 plugin contract for real-time market data feed adapters.
 *                 Concrete adapters (e.g. NepseWebSocketAdapter, BloombergAdapter)
 *                 implement this interface and are registered in
 *                 MarketDataFeedAdapterRegistry.
 *                 D04-002: multi_source_normalization — each adapter is responsible
 *                 for normalising its source's wire format into MarketTick domain
 *                 records before invoking the onTick callback.
 * @doc.layer      Port (Plugin Interface)
 * @doc.pattern    T3 Plugin / Strategy
 */
public interface MarketDataFeedAdapter {

    /** Unique stable identifier for this adapter (e.g. "NEPSE_WS", "BLOOMBERG"). */
    String adapterId();

    /** URL of the upstream feed endpoint (used for health monitoring). */
    String feedUrl();

    /**
     * Open the connection to the upstream feed.
     * Implementations must be idempotent: calling connect() on an already-connected
     * adapter must be a no-op.
     */
    void connect();

    /**
     * Subscribe to live ticks for the given instrument ids.
     * May be called multiple times as the instrument universe grows.
     */
    void subscribe(List<String> instrumentIds);

    /**
     * Register the downstream tick consumer.  The adapter must call
     * {@code callback.accept(tick)} for every normalised MarketTick it receives
     * from its upstream feed.
     */
    void onTick(Consumer<MarketTick> callback);

    /**
     * Gracefully disconnect from the upstream feed.
     * After this returns, the adapter must no longer invoke the tick callback.
     */
    void disconnect();
}
