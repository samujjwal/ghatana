package com.ghatana.appplatform.marketdata.service;

import com.ghatana.appplatform.marketdata.domain.MarketTick;
import com.ghatana.appplatform.marketdata.domain.TickSource;
import com.ghatana.appplatform.marketdata.port.MarketDataStore;
import com.ghatana.appplatform.refdata.domain.Instrument;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.function.Consumer;

/**
 * @doc.type       Application Service
 * @doc.purpose    Validates an incoming market tick before storage.
 *                 Rules applied:
 *                   1. Timestamp must not be in the future (max 30 s clock skew).
 *                   2. Timestamp must not be stale (more than 5 minutes old).
 *                   3. Price (bid/ask/last) must be within ±20 % of the previous
 *                      session close — anomalous ticks are stored with anomalyFlag=true
 *                      and an AnomalyDetected event is emitted.
 *                   4. Volume must be ≥ 0.
 *                 D04-003: price_range_check, volume_check, timestamp_staleness,
 *                          anomaly_event_emitted.
 * @doc.layer      Application Service
 * @doc.pattern    Validator / Guard
 */
public class TickValidationService {

    private static final Logger log = LoggerFactory.getLogger(TickValidationService.class);

    /** Maximum allowed drift between tick timestamp and server wall clock. */
    private static final long MAX_FUTURE_SECONDS  = 30;
    /** Ticks older than this are considered stale but are still stored. */
    private static final long MAX_STALE_MINUTES   = 5;
    /** Price must sit within ±PRICE_BAND_PCT of the previous close. */
    private static final double PRICE_BAND_PCT     = 0.20;

    private final MarketDataStore store;
    private final Consumer<AnomalyDetectedEvent> anomalyPublisher;
    private final Counter anomalyCounter;

    public TickValidationService(MarketDataStore store,
                                 Consumer<AnomalyDetectedEvent> anomalyPublisher,
                                 MeterRegistry metrics) {
        this.store = store;
        this.anomalyPublisher = anomalyPublisher;
        this.anomalyCounter = metrics.counter("marketdata.ticks.anomaly");
    }

    /**
     * Validate the tick and return a (possibly annotated) copy.
     *
     * @throws FutureDateTickException if the timestamp is more than 30 s in the future.
     * @throws NegativeVolumeException if volume is negative.
     */
    public Promise<MarketTick> validate(MarketTick tick, Instrument referenceInstrument) {
        Instant now = Instant.now();

        // 1. Reject ticks from the future
        if (tick.timestampUtc().isAfter(now.plusSeconds(MAX_FUTURE_SECONDS))) {
            return Promise.ofException(new FutureDateTickException(tick.timestampUtc()));
        }

        // 2. Reject negative volume
        if (tick.volume() < 0) {
            return Promise.ofException(new NegativeVolumeException(tick.volume()));
        }

        boolean anomaly = false;
        AnomalyReason reason = null;

        // 3. Staleness — log warn but store; set anomalyFlag
        if (tick.timestampUtc().isBefore(now.minus(MAX_STALE_MINUTES, ChronoUnit.MINUTES))) {
            log.warn("marketdata.tick.stale instrument={} lag_ms={}",
                    tick.instrumentId(),
                    now.toEpochMilli() - tick.timestampUtc().toEpochMilli());
            anomaly = true;
            reason = AnomalyReason.STALE_TIMESTAMP;
        }

        // 4. Price band check against reference close
        if (!anomaly && tick.last() != null && referenceInstrument.tickSize() != null) {
            BigDecimal prevClose = tick.close();  // current session close reference
            if (prevClose != null && prevClose.compareTo(BigDecimal.ZERO) > 0) {
                double ratio = tick.last().divide(prevClose, 8, java.math.RoundingMode.HALF_UP)
                                   .doubleValue();
                if (ratio < (1.0 - PRICE_BAND_PCT) || ratio > (1.0 + PRICE_BAND_PCT)) {
                    anomaly = true;
                    reason = AnomalyReason.PRICE_BAND_VIOLATION;
                    log.warn("marketdata.tick.anomaly instrument={} last={} prevClose={} ratio={}",
                            tick.instrumentId(), tick.last(), prevClose, ratio);
                }
            }
        }

        final boolean finalAnomaly = anomaly;
        final AnomalyReason finalReason = reason;

        MarketTick validated = new MarketTick(
                tick.instrumentId(), tick.timestampUtc(), tick.calendarDate(),
                tick.bid(), tick.ask(), tick.last(), tick.volume(),
                tick.open(), tick.high(), tick.low(), tick.close(),
                tick.source(), tick.sequence(), finalAnomaly);

        if (finalAnomaly) {
            anomalyCounter.increment();
            anomalyPublisher.accept(new AnomalyDetectedEvent(
                    tick.instrumentId(), tick.timestampUtc(), finalReason));
        }

        return Promise.of(validated);
    }

    // -----------------------------------------------------------------------
    // Checked exceptions
    // -----------------------------------------------------------------------

    public static final class FutureDateTickException extends RuntimeException {
        public FutureDateTickException(Instant ts) {
            super("Tick timestamp is in the future: " + ts);
        }
    }

    public static final class NegativeVolumeException extends RuntimeException {
        public NegativeVolumeException(long volume) {
            super("Tick volume must be ≥ 0, got: " + volume);
        }
    }

    // -----------------------------------------------------------------------
    // Events / value objects
    // -----------------------------------------------------------------------

    public enum AnomalyReason {
        PRICE_BAND_VIOLATION,
        STALE_TIMESTAMP
    }

    public record AnomalyDetectedEvent(
            String instrumentId,
            Instant occurredAt,
            AnomalyReason reason
    ) {}
}
