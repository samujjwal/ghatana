package com.ghatana.appplatform.marketdata.service;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * @doc.type    Service (Application)
 * @doc.purpose End-of-day reconciliation (D04-013).
 *              Compares internally stored OHLCV aggregates against official exchange
 *              EOD data (closing price, volume, OHLC). Flags discrepancies above
 *              a configurable tolerance (default 0.01% price deviation).
 *              Emits {@link ReconciliationCompletedEvent} with per-instrument results.
 * @doc.layer   Application Service
 * @doc.pattern Hexagonal Architecture — application service; comparison pipeline
 */
public class EodReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(EodReconciliationService.class);
    /** Default price-match tolerance: 0.01% of expected value. */
    private static final BigDecimal DEFAULT_TOLERANCE = new BigDecimal("0.0001");

    private final StoredOhlcvPort storedOhlcvPort;
    private final ExchangeEodPort exchangeEodPort;
    private final Executor executor;
    private final Consumer<Object> eventPublisher;

    public EodReconciliationService(StoredOhlcvPort storedOhlcvPort,
                                     ExchangeEodPort exchangeEodPort,
                                     Executor executor,
                                     Consumer<Object> eventPublisher) {
        this.storedOhlcvPort = storedOhlcvPort;
        this.exchangeEodPort = exchangeEodPort;
        this.executor = executor;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Run end-of-day reconciliation for all instruments on the specified date (D04-013).
     *
     * @param date  The trading date to reconcile (AD date).
     * @return List of reconciliation results, one per instrument.
     */
    public Promise<List<ReconciliationResult>> reconcile(LocalDate date) {
        return Promise.ofBlocking(executor, () -> {
            var exchangeData = exchangeEodPort.fetchEodData(date);
            var results = new ArrayList<ReconciliationResult>();

            for (var eod : exchangeData) {
                var stored = storedOhlcvPort.findOhlcv(eod.instrumentId(), date);
                if (stored == null) {
                    results.add(ReconciliationResult.missing(eod.instrumentId(), date));
                    log.warn("EOD recon: no stored data for instrument={} date={}", eod.instrumentId(), date);
                    continue;
                }

                var breaks = new ArrayList<String>();
                checkField("close", stored.close(), eod.close(), breaks);
                checkField("volume", BigDecimal.valueOf(stored.volume()),
                        BigDecimal.valueOf(eod.volume()), breaks);
                checkField("open",  stored.open(),  eod.open(),  breaks);
                checkField("high",  stored.high(),  eod.high(),  breaks);
                checkField("low",   stored.low(),   eod.low(),   breaks);

                var result = new ReconciliationResult(eod.instrumentId(), date,
                        breaks.isEmpty(), List.copyOf(breaks));
                results.add(result);

                if (!breaks.isEmpty()) {
                    log.warn("EOD recon break: instrument={} breaks={}", eod.instrumentId(), breaks);
                }
            }

            eventPublisher.accept(new ReconciliationCompletedEvent(date, results));
            log.info("EOD reconciliation complete: date={} total={} breaks={}",
                    date, results.size(), results.stream().filter(r -> !r.matched()).count());
            return results;
        });
    }

    private void checkField(String field, BigDecimal stored, BigDecimal expected,
                             List<String> breaks) {
        if (expected == null || expected.compareTo(BigDecimal.ZERO) == 0) return;
        BigDecimal deviation = stored.subtract(expected).abs()
                .divide(expected, 8, RoundingMode.HALF_EVEN);
        if (deviation.compareTo(DEFAULT_TOLERANCE) > 0) {
            breaks.add(field + ": stored=" + stored + " expected=" + expected
                    + " deviation=" + deviation.multiply(BigDecimal.valueOf(100))
                            .setScale(4, RoundingMode.HALF_EVEN) + "%");
        }
    }

    // ─── Ports ────────────────────────────────────────────────────────────────

    public interface StoredOhlcvPort {
        OhlcvRecord findOhlcv(String instrumentId, LocalDate date);

        record OhlcvRecord(String instrumentId, LocalDate date,
                           BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close,
                           long volume) {}
    }

    public interface ExchangeEodPort {
        List<EodRecord> fetchEodData(LocalDate date);

        record EodRecord(String instrumentId, BigDecimal open, BigDecimal high,
                         BigDecimal low, BigDecimal close, long volume) {}
    }

    // ─── Supporting Types ─────────────────────────────────────────────────────

    public record ReconciliationResult(String instrumentId, LocalDate date,
                                        boolean matched, List<String> breaks) {
        public static ReconciliationResult missing(String instrumentId, LocalDate date) {
            return new ReconciliationResult(instrumentId, date, false,
                    List.of("No stored data found"));
        }
    }

    public record ReconciliationCompletedEvent(LocalDate date, List<ReconciliationResult> results) {}
}
