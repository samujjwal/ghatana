package com.ghatana.aep.platform.benchmark;

import com.ghatana.core.operator.OperatorId;
import com.ghatana.core.operator.OperatorResult;
import com.ghatana.core.operator.UnifiedOperator;
import com.ghatana.core.operator.catalog.DefaultOperatorCatalog;
import com.ghatana.core.pipeline.Pipeline;
import com.ghatana.core.pipeline.PipelineBuilder;
import com.ghatana.core.pipeline.PipelineExecutionContext;
import com.ghatana.core.pipeline.PipelineExecutionEngine;
import com.ghatana.platform.domain.domain.event.Event;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.openjdk.jmh.annotations.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class PipelineExecutionBenchmark {

    private PipelineExecutionEngine engine;
    private Pipeline pipeline;
    private PipelineExecutionContext context;
    private List<Event> eventBatch;
    private ExecutorService blockingExecutor;
    private Eventloop eventloop;

    @Setup(Level.Trial)
    public void setup() {
        engine = new PipelineExecutionEngine();
        DefaultOperatorCatalog catalog = new DefaultOperatorCatalog();
        
        OperatorId id = OperatorId.of("benchmark", "test", "noop", "1.0");
        catalog.register(new NoopOperator(id));

        pipeline = PipelineBuilder.create("benchmark-pipeline", "Test")
                .stage("stage1", id, "stage2")
                .stage("stage2", id, null)
                .build();

        context = PipelineExecutionContext.builder()
                .pipelineId(pipeline.getId())
                .tenantId("tenant-x")
                .executionId("exec-bench")
                .operatorCatalog(catalog)
                .deadline(Duration.ofSeconds(30))
                .build();

        // Simulate a real workload block
        eventBatch = IntStream.range(0, 100)
                .mapToObj(i -> Event.builder()
                        .type("benchmark.event")
                        .payload(Map.of("id", i))
                        .build())
                .collect(Collectors.toList());

        blockingExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    }

    @TearDown(Level.Trial)
    public void teardown() {
        blockingExecutor.shutdown();
    }

    @Benchmark
    public void testEventloopBoundedPipelineExecution() {
        eventloop = Eventloop.create().withCurrentThread();
        
        eventloop.post(() -> {
            Promise<List<Object>> compositePromise = Promises.toList(
                    eventBatch.stream()
                            .map(event -> engine.execute(pipeline, event, context))
                            .collect(Collectors.toList()) // Execute all in parallel using ActiveJ Promises
            );

            // Using Promise.ofBlocking effectively isolates IO
            Promise.ofBlocking(blockingExecutor, () -> {
                // Emulate some IO wrapping
                return null;
            }).then(v -> compositePromise)
              .whenComplete((res, e) -> eventloop.breakEventloop());
        });

        eventloop.run();
    }

    private static class NoopOperator implements UnifiedOperator {
        private final OperatorId id;
        
        NoopOperator(OperatorId id) {
            this.id = id;
        }

        @Override
        public OperatorId getId() {
            return id;
        }

        @Override
        public Promise<OperatorResult> process(Event event) {
            return Promise.of(OperatorResult.success(event));
        }
    }
}
