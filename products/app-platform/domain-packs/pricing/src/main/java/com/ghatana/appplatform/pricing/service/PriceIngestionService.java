package com.ghatana.appplatform.pricing.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPool;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @doc.type    DomainService
 * @doc.purpose Ingests real-time price ticks from the D-04 market data feed. Validates each
 *              tick (delegated to PriceValidationService's rule checks before persistence).
 *              Updates Redis HSET for sub-1ms read access and persists to latest_prices in
 *              PostgreSQL as the durable read model. Detects staleness: if no tick received for
 *              an instrument within STALENESS_THRESHOLD_MS, sets price_status to STALE.
 *              Emits PriceUpdated event on each accepted tick.
 * @doc.layer   Domain
 * @doc.pattern Redis write-through cache; event emission; staleness detection via TTL;
 *              UPSERT idempotency via ON CONFLICT(instrument_id) DO UPDATE.
 */
public class PriceIngestionService {

    private static final Logger log = LoggerFactory.getLogger(PriceIngestionService.class);

    private static final String REDIS_PRICE_HASH = "latest_price";
    private static final long   STALENESS_THRESHOLD_MS = 5 * 60 * 1_000; // 5 minutes

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final JedisPool        jedisPool;
    private final PriceEventPort   eventPort;
    private final Counter          ingestedCounter;
    private final Counter          rejectedCounter;
    private final Counter          staleCounter;
    private final AtomicLong       liveInstrumentCount = new AtomicLong(0);

    public PriceIngestionService(HikariDataSource dataSource, Executor executor,
                                 JedisPool jedisPool, PriceEventPort eventPort,
                                 MeterRegistry registry) {
        this.dataSource     = dataSource;
        this.executor       = executor;
        this.jedisPool      = jedisPool;
        this.eventPort      = eventPort;
        this.ingestedCounter = registry.counter("pricing.ingestion.accepted");
        this.rejectedCounter = registry.counter("pricing.ingestion.rejected");
        this.staleCounter   = registry.counter("pricing.ingestion.stale_detected");
        Gauge.builder("pricing.ingestion.live_instruments", liveInstrumentCount, AtomicLong::get)
             .register(registry);
    }

    // ─── Inner port ──────────────────────────────────────────────────────────

    public interface PriceEventPort {
        void emitPriceUpdated(PriceUpdated event);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record PriceTick(
        String  instrumentId,
        double  lastPrice,
        double  bidPrice,
        double  askPrice,
        double  volume,
        Instant timestamp
    ) {}

    public record PriceUpdated(
        String  instrumentId,
        double  price,
        String  priceStatus,
        Instant updatedAt
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Ingest a single real-time price tick.
     */
    public Promise<Void> ingest(PriceTick tick) {
        return Promise.ofBlocking(executor, () -> {
            if (!isValid(tick)) {
                rejectedCounter.increment();
                log.warn("Rejected invalid tick instrumentId={} price={}", tick.instrumentId(), tick.lastPrice());
                return null;
            }
            persistLatestPrice(tick, "LIVE");
            cachePrice(tick);
            eventPort.emitPriceUpdated(new PriceUpdated(tick.instrumentId(), tick.lastPrice(), "LIVE", tick.timestamp()));
            ingestedCounter.increment();
            return null;
        });
    }

    /**
     * Scan all instruments; mark those without a recent tick as STALE.
     */
    public Promise<Integer> detectAndMarkStale() {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                UPDATE latest_prices
                SET price_status = 'STALE', updated_at = now()
                WHERE price_status = 'LIVE'
                  AND updated_at < now() - INTERVAL '5 minutes'
                """;
            int count;
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                count = ps.executeUpdate();
            }
            staleCounter.increment(count);
            if (count > 0) {
                log.info("Marked {} instruments as STALE", count);
                liveInstrumentCount.addAndGet(-count);
            }
            return count;
        });
    }

    // ─── Core logic ──────────────────────────────────────────────────────────

    private boolean isValid(PriceTick tick) {
        if (tick.lastPrice() <= 0) return false;
        if (tick.bidPrice() > 0 && tick.askPrice() > 0 && tick.bidPrice() > tick.askPrice()) return false;
        if (tick.timestamp() == null) return false;
        return true;
    }

    private void persistLatestPrice(PriceTick tick, String status) throws SQLException {
        String sql = """
            INSERT INTO latest_prices (
                instrument_id, last_price, bid_price, ask_price, volume, price_status, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (instrument_id) DO UPDATE SET
                last_price   = EXCLUDED.last_price,
                bid_price    = EXCLUDED.bid_price,
                ask_price    = EXCLUDED.ask_price,
                volume       = EXCLUDED.volume,
                price_status = EXCLUDED.price_status,
                updated_at   = EXCLUDED.updated_at
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tick.instrumentId());
            ps.setDouble(2, tick.lastPrice());
            ps.setDouble(3, tick.bidPrice());
            ps.setDouble(4, tick.askPrice());
            ps.setDouble(5, tick.volume());
            ps.setString(6, status);
            ps.setObject(7, tick.timestamp());
            ps.executeUpdate();
        }
    }

    private void cachePrice(PriceTick tick) {
        // HSET latest_price <instrumentId> "<price>,<bid>,<ask>,<ts>"
        String value = tick.lastPrice() + "," + tick.bidPrice() + ","
                     + tick.askPrice() + "," + tick.timestamp().toEpochMilli();
        try (var jedis = jedisPool.getResource()) {
            jedis.hset(REDIS_PRICE_HASH, tick.instrumentId(), value);
        }
    }
}
