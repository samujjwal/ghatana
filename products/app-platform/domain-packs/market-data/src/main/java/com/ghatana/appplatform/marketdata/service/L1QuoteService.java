package com.ghatana.appplatform.marketdata.service;

import com.ghatana.appplatform.marketdata.domain.L1Quote;
import com.ghatana.appplatform.marketdata.domain.MarketTick;
import com.ghatana.appplatform.marketdata.port.L1Cache;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * @doc.type       Application Service
 * @doc.purpose    Maintains the Level-1 (top-of-book) view for each instrument.
 *                 On every non-anomalous ingested tick:
 *                   1. Updates the in-memory ConcurrentHashMap (for sub-millisecond reads).
 *                   2. Writes to Redis via L1Cache (for cross-instance sharing).
 *                   3. Publishes an L1Quote event to the Kafka topic
 *                      {@code siddhanta.marketdata.l1} (for WebSocket fan-out).
 *                 D04-004: l1_quote_updated, l1_redis_write, l1_kafka_publish.
 * @doc.layer      Application Service
 * @doc.pattern    In-Memory Cache / Write-through
 */
public class L1QuoteService {

    private static final Logger log = LoggerFactory.getLogger(L1QuoteService.class);

    private final L1Cache redisCache;
    private final Consumer<L1Quote> l1Publisher;   // wired to Kafka siddhanta.marketdata.l1
    private final Map<String, L1Quote> hotCache = new ConcurrentHashMap<>();

    public L1QuoteService(L1Cache redisCache,
                          Consumer<L1Quote> l1Publisher,
                          MeterRegistry metrics) {
        this.redisCache = redisCache;
        this.l1Publisher = l1Publisher;
        Gauge.builder("marketdata.l1.instruments", hotCache, Map::size)
             .description("Number of instruments with active L1 quotes")
             .register(metrics);
    }

    /**
     * Update the L1 quote from an ingested tick.
     * Anomalous ticks are excluded from L1 distribution so consumers always see
     * validated prices.
     */
    public Promise<Void> onTick(MarketTick tick) {
        if (tick.anomalyFlag()) {
            log.debug("marketdata.l1.skip.anomaly instrument={}", tick.instrumentId());
            return Promise.of(null);
        }

        L1Quote quote = new L1Quote(
                tick.instrumentId(),
                tick.bid(),
                tick.ask(),
                tick.last(),
                tick.volume(),
                Instant.now());

        hotCache.put(tick.instrumentId(), quote);

        return redisCache.updateL1(quote)
                .whenResult(ignored -> {
                    l1Publisher.accept(quote);
                    log.debug("marketdata.l1.updated instrument={} last={}",
                            tick.instrumentId(), tick.last());
                });
    }

    /**
     * Get the current L1 quote for one instrument.
     * Reads from the in-memory hot cache first; falls through to Redis if absent
     * (e.g. after a rolling restart).
     */
    public Promise<Optional<L1Quote>> getL1(String instrumentId) {
        L1Quote cached = hotCache.get(instrumentId);
        if (cached != null) return Promise.of(Optional.of(cached));
        return redisCache.getL1(instrumentId);
    }

    /**
     * Return all currently tracked L1 quotes (used for snapshot on WebSocket connect).
     */
    public Promise<List<L1Quote>> getAllL1() {
        if (!hotCache.isEmpty()) {
            return Promise.of(new ArrayList<>(hotCache.values()));
        }
        return redisCache.getAllL1();
    }
}
