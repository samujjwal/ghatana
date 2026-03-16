package com.ghatana.appplatform.refdata.port;

import com.ghatana.appplatform.refdata.domain.Benchmark;
import com.ghatana.appplatform.refdata.domain.BenchmarkConstituent;
import com.ghatana.appplatform.refdata.domain.BenchmarkValue;
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
