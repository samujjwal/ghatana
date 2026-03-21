package com.ghatana.products.finance.domains.referencedata.port;

import com.ghatana.products.finance.domains.referencedata.domain.Benchmark;
import com.ghatana.products.finance.domains.referencedata.domain.BenchmarkConstituent;
import com.ghatana.products.finance.domains.referencedata.domain.BenchmarkValue;
import io.activej.promise.Promise;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * @doc.type       Port (Secondary)
 * @doc.purpose    Persistence port for benchmark definitions, constituents,
 *                 and historical value series.
 * @doc.layer      Application / Port
 * @doc.pattern    Repository Port (Hexagonal)
 */
public interface BenchmarkStore {

    Promise<Void> saveBenchmark(Benchmark benchmark);

    Promise<Optional<Benchmark>> findBenchmarkById(UUID id);

    Promise<List<Benchmark>> listBenchmarks();

    Promise<Void> saveConstituents(List<BenchmarkConstituent> constituents);

    Promise<List<BenchmarkConstituent>> findCurrentConstituents(UUID benchmarkId);

    Promise<Void> saveValue(BenchmarkValue value);

    Promise<List<BenchmarkValue>> findValues(UUID benchmarkId, LocalDate from, LocalDate to);
}
