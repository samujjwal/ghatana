package com.ghatana.appplatform.marketdata.service;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * @doc.type    Service (Application)
 * @doc.purpose Detects NEPSE circuit breaker triggers (D04-009).
 *              Rules:
 *                - Individual instrument: ±10% from previous close → HALT
 *                - Market-wide index:     ±5%  from previous close → MARKET_HALT
 *              On detection: emits {@link MarketHaltedEvent}, updates instrument status
 *              to HALTED in the in-memory instrument cache, and notifies OMS/EMS via K-05.
 *              Resumption: emits {@link MarketResumedEvent} when exchange signals restore.
 * @doc.layer   Application Service
 * @doc.pattern Hexagonal Architecture — application service; event-driven state change
 */
public class CircuitBreakerDetectionService {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerDetectionService.class);

    /** NEPSE individual instrument circuit breaker: ±10%. */
    private static final BigDecimal INDIVIDUAL_LIMIT = new BigDecimal("0.10");
    /** NEPSE index-wide circuit breaker: ±5%. */
    private static final BigDecimal INDEX_LIMIT = new BigDecimal("0.05");

    /** Previous-day closing prices per instrument. */
    private final Map<String, BigDecimal> previousCloses = new ConcurrentHashMap<>();
    /** Currently halted instruments. */
    private final Map<String, HaltRecord> haltedInstruments = new ConcurrentHashMap<>();

    private final HaltedInstrumentPort haltedInstrumentPort;
    private final Executor executor;
    private final Consumer<Object> eventPublisher;
    private final Counter haltCounter;

    public CircuitBreakerDetectionService(HaltedInstrumentPort haltedInstrumentPort,
                                           Executor executor,
                                           Consumer<Object> eventPublisher,
                                           MeterRegistry meterRegistry) {
        this.haltedInstrumentPort = haltedInstrumentPort;
        this.executor = executor;
        this.eventPublisher = eventPublisher;
        this.haltCounter = meterRegistry.counter("marketdata.circuit_breaker.halts");
    }

    /** Register baseline closing prices for circuit breaker calculation. */
    public void loadPreviousCloses(Map<String, BigDecimal> closes) {
        previousCloses.putAll(closes);
    }

    /**
     * Evaluate a new tick price against circuit breaker thresholds (D04-009).
     * If a breach is detected and not already halted, triggers a halt.
     */
    public Promise<Void> evaluate(String instrumentId, BigDecimal currentPrice, boolean isIndex) {
        return Promise.ofBlocking(executor, () -> {
            if (haltedInstruments.containsKey(instrumentId)) {
                return (Void) null; // Already halted — no double-emit
            }

            BigDecimal prevClose = previousCloses.get(instrumentId);
            if (prevClose == null || prevClose.compareTo(BigDecimal.ZERO) == 0) {
                return (Void) null;
            }

            BigDecimal changeRatio = currentPrice.subtract(prevClose)
                    .divide(prevClose, 6, java.math.RoundingMode.HALF_EVEN)
                    .abs();

            BigDecimal limit = isIndex ? INDEX_LIMIT : INDIVIDUAL_LIMIT;

            if (changeRatio.compareTo(limit) >= 0) {
                log.warn("Circuit breaker: instrumentId={} change={}% limit={}%",
                        instrumentId,
                        changeRatio.multiply(BigDecimal.valueOf(100)).setScale(2, java.math.RoundingMode.HALF_EVEN),
                        limit.multiply(BigDecimal.valueOf(100)).toPlainString());

                haltCounter.increment();
                var record = new HaltRecord(instrumentId, Instant.now(), changeRatio);
                haltedInstruments.put(instrumentId, record);
                haltedInstrumentPort.markHalted(instrumentId);

                eventPublisher.accept(isIndex
                        ? new MarketHaltedEvent(instrumentId, currentPrice, changeRatio, true)
                        : new MarketHaltedEvent(instrumentId, currentPrice, changeRatio, false));
            }
            return (Void) null;
        });
    }

    /** Restore an instrument from halted state (exchange signals resume). */
    public Promise<Void> resume(String instrumentId) {
        return Promise.ofBlocking(executor, () -> {
            HaltRecord removed = haltedInstruments.remove(instrumentId);
            if (removed != null) {
                haltedInstrumentPort.markActive(instrumentId);
                eventPublisher.accept(new MarketResumedEvent(instrumentId, Instant.now()));
                log.info("Circuit breaker resumed: instrumentId={}", instrumentId);
            }
            return (Void) null;
        });
    }

    public boolean isHalted(String instrumentId) {
        return haltedInstruments.containsKey(instrumentId);
    }

    // ─── Port ────────────────────────────────────────────────────────────────

    public interface HaltedInstrumentPort {
        void markHalted(String instrumentId);
        void markActive(String instrumentId);
    }

    // ─── Supporting Types ─────────────────────────────────────────────────────

    public record MarketHaltedEvent(String instrumentId, BigDecimal triggerPrice,
                                     BigDecimal changeRatio, boolean marketWide) {}

    public record MarketResumedEvent(String instrumentId, Instant resumedAt) {}

    private record HaltRecord(String instrumentId, Instant haltedAt, BigDecimal changeRatio) {}
}
