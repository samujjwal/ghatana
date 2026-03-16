package com.ghatana.aep.performance;

import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class AepEventProcessingBenchmark {

    private Eventloop eventloop;

    @Setup(Level.Iteration)
    public void setup() {
        eventloop = Eventloop.create();
    }

    @Benchmark
    public void benchmarkSimplePromiseChain() {
        eventloop.submit(() -> Promise.of("event-data")
            .map(data -> data + "-processed")
            .map(data -> data + "-enriched")
            .map(data -> data.toUpperCase())
        );
        eventloop.run();
    }

    // Standard JUnit runner for JMH
    @org.junit.jupiter.api.Test
    public void runJmh() throws Exception {
        Options opt = new OptionsBuilder()
                .include(AepEventProcessingBenchmark.class.getSimpleName())
                .warmupIterations(1)
                .warmupTime(TimeValue.seconds(1))
                .measurementIterations(1)
                .measurementTime(TimeValue.seconds(1))
                .forks(1)
                .shouldFailOnError(true)
                .build();

        new Runner(opt).run();
    }
}
