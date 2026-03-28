package com.ghatana.products.finance.domains.referencedata.service;

import com.ghatana.products.finance.domains.referencedata.domain.Benchmark;
import com.ghatana.products.finance.domains.referencedata.domain.BenchmarkConstituent;
import com.ghatana.products.finance.domains.referencedata.domain.BenchmarkValue;
import com.ghatana.products.finance.domains.referencedata.port.BenchmarkStore;
import com.ghatana.platform.core.exception.ResourceNotFoundException;
import com.ghatana.platform.core.exception.ValidationException;
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
            store.saveBenchmark(benchmark).getResult();
            log.info("benchmark.saved id={} name={}", benchmark.id(), benchmark.name());
        });
    }

    public Promise<Optional<Benchmark>> findBenchmarkById(UUID id) {
        return Promise.ofBlocking(executor, () -> store.findBenchmarkById(id).getResult());
    }

    public Promise<List<Benchmark>> listBenchmarks() {
        return Promise.ofBlocking(executor, () -> store.listBenchmarks().getResult());
    }

    /**
     * Replace the current constituent set for a benchmark.
     * Validates that weights sum to 1.0 (within ±0.001 tolerance).
     * D11-006: constituents_sumTo1 acceptance criterion.
     */
    public Promise<Void> setConstituents(UUID benchmarkId,
                                         List<BenchmarkConstituent> constituents) {
        return Promise.ofBlocking(executor, () -> {
            store.findBenchmarkById(benchmarkId).getResult()
                    .orElseThrow(() -> new BenchmarkNotFoundException(benchmarkId));

            BigDecimal total = constituents.stream()
                    .map(BenchmarkConstituent::weight)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (total.subtract(BigDecimal.ONE).abs().compareTo(new BigDecimal("0.001")) > 0) {
                throw new InvalidConstituentWeightsException(benchmarkId, total);
            }
            store.saveConstituents(constituents).getResult();
        });
    }

    public Promise<List<BenchmarkConstituent>> getConstituents(UUID benchmarkId) {
        return Promise.ofBlocking(executor, () -> store.findCurrentConstituents(benchmarkId).getResult());
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
                    value.benchmarkId(), oneDayBefore, value.dateUtc()).getResult();

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
            store.saveValue(enriched).getResult();
            return enriched;
        });
    }

    public Promise<List<BenchmarkValue>> getValues(UUID benchmarkId,
                                                   LocalDate from, LocalDate to) {
        return Promise.ofBlocking(executor, () -> store.findValues(benchmarkId, from, to).getResult());
    }

    // ── Exceptions ────────────────────────────────────────────────────────────

    public static final class BenchmarkNotFoundException extends ResourceNotFoundException {
        public BenchmarkNotFoundException(UUID id) {
            super("Benchmark not found: " + id);
        }
    }

    public static final class InvalidConstituentWeightsException extends ValidationException {
        public InvalidConstituentWeightsException(UUID benchmarkId, BigDecimal total) {
            super("Constituent weights sum to " + total + " for benchmark " + benchmarkId
                    + "; must be 1.0 (±0.001)");
        }
    }
}
