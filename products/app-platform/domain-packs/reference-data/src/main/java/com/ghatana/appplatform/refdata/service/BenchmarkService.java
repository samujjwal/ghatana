package com.ghatana.appplatform.refdata.service;

import com.ghatana.appplatform.refdata.domain.Benchmark;
import com.ghatana.appplatform.refdata.domain.BenchmarkConstituent;
import com.ghatana.appplatform.refdata.domain.BenchmarkValue;
import com.ghatana.appplatform.refdata.port.BenchmarkStore;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type       Application Service
 * @doc.purpose    Benchmark and index management (D11-006, D11-007).
 *                 Handles benchmark definitions, constituent weights (must sum
 *                 to 1.0), and daily value series ingestion.
 * @doc.layer      Application Service
 * @doc.pattern    Hexagonal Application Service
 */
public class BenchmarkService {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkService.class);

    private final BenchmarkStore store;
    private final Executor executor;

    public BenchmarkService(BenchmarkStore store, Executor executor) {
        this.store = store;
        this.executor = executor;
    }

    /** Create or replace a benchmark definition. */
    public Promise<Void> saveBenchmark(Benchmark benchmark) {
        return Promise.ofBlocking(executor, () -> {
            store.saveBenchmark(benchmark).get();
            log.info("benchmark.saved id={} name={}", benchmark.id(), benchmark.name());
        });
    }

    public Promise<Optional<Benchmark>> findBenchmarkById(UUID id) {
        return Promise.ofBlocking(executor, () -> store.findBenchmarkById(id).get());
    }

    public Promise<List<Benchmark>> listBenchmarks() {
        return Promise.ofBlocking(executor, () -> store.listBenchmarks().get());
    }

    /**
     * Replace the current constituent set for a benchmark.
     * Validates that weights sum to 1.0 (within ±0.001 tolerance).
     * D11-006: constituents_sumTo1 acceptance criterion.
     */
    public Promise<Void> setConstituents(UUID benchmarkId,
                                         List<BenchmarkConstituent> constituents) {
        return Promise.ofBlocking(executor, () -> {
            store.findBenchmarkById(benchmarkId).get()
                    .orElseThrow(() -> new BenchmarkNotFoundException(benchmarkId));

            BigDecimal total = constituents.stream()
                    .map(BenchmarkConstituent::weight)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (total.subtract(BigDecimal.ONE).abs().compareTo(new BigDecimal("0.001")) > 0) {
                throw new InvalidConstituentWeightsException(benchmarkId, total);
            }
            store.saveConstituents(constituents).get();
        });
    }

    public Promise<List<BenchmarkConstituent>> getConstituents(UUID benchmarkId) {
        return Promise.ofBlocking(executor, () -> store.findCurrentConstituents(benchmarkId).get());
    }

    /**
     * Ingest a benchmark value for a trading day.
     * Returns the value enriched with computed dailyReturn if a previous close
     * is available in the store.
     * D11-007: values_manual_entry + values_return_calculation.
     */
    public Promise<BenchmarkValue> ingestValue(BenchmarkValue value) {
        return Promise.ofBlocking(executor, () -> {
            // Look up most recent prior day's value for return calculation
            LocalDate oneDayBefore = value.dateUtc().minusDays(1);
            List<BenchmarkValue> prior = store.findValues(
                    value.benchmarkId(), oneDayBefore, value.dateUtc()).get();

            BigDecimal dailyReturn = BigDecimal.ZERO;
            if (!prior.isEmpty()) {
                BigDecimal prevClose = prior.get(prior.size() - 1).closeValue();
                if (prevClose.compareTo(BigDecimal.ZERO) != 0) {
                    dailyReturn = value.closeValue()
                            .divide(prevClose, 8, java.math.RoundingMode.HALF_UP)
                            .subtract(BigDecimal.ONE);
                }
            }

            BenchmarkValue enriched = new BenchmarkValue(
                    value.benchmarkId(), value.dateUtc(), value.dateBs(),
                    value.openValue(), value.highValue(), value.lowValue(),
                    value.closeValue(), value.volume(), dailyReturn);
            store.saveValue(enriched).get();
            return enriched;
        });
    }

    public Promise<List<BenchmarkValue>> getValues(UUID benchmarkId,
                                                   LocalDate from, LocalDate to) {
        return Promise.ofBlocking(executor, () -> store.findValues(benchmarkId, from, to).get());
    }

    // ── Exceptions ────────────────────────────────────────────────────────────

    public static final class BenchmarkNotFoundException extends RuntimeException {
        public BenchmarkNotFoundException(UUID id) {
            super("Benchmark not found: " + id);
        }
    }

    public static final class InvalidConstituentWeightsException extends RuntimeException {
        public InvalidConstituentWeightsException(UUID benchmarkId, BigDecimal total) {
            super("Constituent weights sum to " + total + " for benchmark " + benchmarkId
                    + "; must be 1.0 (±0.001)");
        }
    }
}
