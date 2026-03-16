package com.ghatana.aep.platform.simulation;

import com.ghatana.core.operator.OperatorId;
import com.ghatana.core.operator.OperatorResult;
import com.ghatana.core.operator.UnifiedOperator;
import com.ghatana.core.operator.catalog.DefaultOperatorCatalog;
import com.ghatana.core.pipeline.Pipeline;
import com.ghatana.core.pipeline.PipelineBuilder;
import com.ghatana.core.pipeline.PipelineExecutionContext;
import com.ghatana.core.pipeline.PipelineExecutionEngine;
import com.ghatana.core.pipeline.PipelineExecutionResult;
import com.ghatana.platform.domain.domain.event.Event;
import com.ghatana.core.operator.OperatorConfig;
import com.ghatana.core.operator.OperatorState;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Simulates load testing for AEP PipelineExecutionEngine without stubbing production components.
 * @doc.layer core
 * @doc.pattern Service
 */
@DisplayName("Phase 1.1: AEP Pipeline Execution Load Simulation")
class PipelineExecutionSimulationTest extends EventloopTestBase {

    private PipelineExecutionEngine engine;
    private DefaultOperatorCatalog catalog;

    private final ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    @BeforeEach
    void setUp() {
        engine = new PipelineExecutionEngine();
        catalog = new DefaultOperatorCatalog();
        
        OperatorId id = OperatorId.of("test", "simulation", "load-op", "1.0");
        catalog.register(new DummyOperator(id));
    }

    @Test
    void shouldSimulateHighVolumeExecutionUnderLoad() {
        // GIVEN
        int simulatedLoad = 5_000;
        
        Pipeline pipeline = Pipeline.builder("load-test-pipeline", "1.0.0")
                .name("Test Pipeline")
                .stage("stage1", OperatorId.of("test", "simulation", "load-op", "1.0"))
                .stage("stage2", OperatorId.of("test", "simulation", "load-op", "1.0"))
                .edge("stage1", "stage2")
                .build();

        PipelineExecutionContext context = PipelineExecutionContext.builder()
                .pipelineId(pipeline.getId())
                .tenantId("tenant-x")
                .executionId("exec-load-1")
                .operatorCatalog(catalog)
                .deadline(Duration.ofSeconds(30))
                .build();

        List<Event> events = IntStream.range(0, simulatedLoad)
                .mapToObj(i -> Event.builder()
                        .type("test.event")
                        .payload(Map.of("id", i))
                        .build())
                .collect(Collectors.toList());

        // WHEN
        Promise<List<PipelineExecutionResult>> aggregateResults = Promises.toList(
                events.stream()
                      .map(event -> engine.execute(pipeline, event, context))
                      .collect(Collectors.toList())
        );

        List<PipelineExecutionResult> results = runPromise(() -> aggregateResults);

        // THEN
        assertThat(results).hasSize(simulatedLoad);
        assertThat(results).allMatch(PipelineExecutionResult::isSuccess);
        
        // Assert all events passed through 2 stages and resulted in final event output
        assertThat(results.get(0).outputEvents()).hasSize(1);
    }
    
    // Using a minimal operator to test the engine routing without Mockito overhead
    private static class DummyOperator implements UnifiedOperator {
        private final OperatorId id;
        
        DummyOperator(OperatorId id) {
            this.id = id;
        }

        @Override
        public OperatorId getId() {
            return id;
        }

        @Override
        public Map<String, String> getMetadata() {
            return Map.of("type", "simulation");
        }

        @Override
        public Promise<OperatorResult> process(Event event) {
            // Emulate async IO boundary without blocking activej thread
            return Promise.ofBlocking(Executors.newSingleThreadExecutor(), () -> {
                try {
                    Thread.sleep(1); // minimal delay simulating IO
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return OperatorResult.builder().success().addEvent(Event.builder()
                            .id(event.getId())
                            .type(event.getType() + ".processed")
                            .payload(Map.of("status", "processed"))
                            .build()).build();
            });
        }

        @Override
        public OperatorConfig getConfig() {
            return OperatorConfig.empty();
        }

        @Override
        public Map<String, Object> getInternalState() {
            return Map.of();
        }

        @Override
        public Map<String, Object> getMetrics() {
            return Map.of();
        }

        @Override
        public Event toEvent() {
            return Event.builder().type("operator.dummy").build();
        }

        @Override
        public OperatorState getState() {
            return OperatorState.CREATED;
        }

        @Override
        public String getName() { return "dummy"; }
        @Override
        public com.ghatana.core.operator.OperatorType getType() { return com.ghatana.core.operator.OperatorType.STREAM; }
        @Override
        public String getVersion() { return "1.0"; }
        @Override
        public String getDescription() { return "dummy"; }
        @Override
        public List<String> getCapabilities() { return List.of(); }
        @Override
        public Promise<Void> initialize(OperatorConfig config) { return Promise.complete(); }
        @Override
        public Promise<Void> start() { return Promise.complete(); }
        @Override
        public Promise<Void> stop() { return Promise.complete(); }

        public boolean isHealthy() {
            return true;
        }
    }
}
