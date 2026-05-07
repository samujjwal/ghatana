package com.ghatana.datacloud.benchmarks;

import com.ghatana.datacloud.DataCloud;
import com.ghatana.datacloud.DataCloudClient;
import io.activej.eventloop.Eventloop;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @doc.type class
 * @doc.purpose Focused JMH benchmark suite for Data Cloud entity CRUD operations.
 * @doc.layer product
 * @doc.pattern Benchmark
 */
@BenchmarkMode({Mode.SampleTime, Mode.Throughput})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 4, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class EntityCrudBenchmark {

    @State(Scope.Thread)
    public static class ClientState {
        Eventloop eventloop;
        DataCloudClient client;
        List<String> seededIds;

        @Param({"100", "1000"})
        public int batchSize;

        @Setup(Level.Trial)
        public void setup() {
            eventloop = Eventloop.builder().build();
            client = DataCloud.forTesting();
            seededIds = new ArrayList<>();
            for (int index = 0; index < 200; index++) {
                int sequence = index;
                String[] savedId = {null};
                eventloop.submit(() -> client.save("bench-tenant", "orders", Map.of(
                    "orderId", "seed-" + sequence,
                    "amount", sequence,
                    "status", (sequence % 2 == 0) ? "open" : "closed"
                )).whenComplete((entity, exception) -> savedId[0] = entity.id()));
                eventloop.run();
                seededIds.add(savedId[0]);
            }
        }

        @TearDown(Level.Trial)
        public void tearDown() throws Exception {
            client.close();
        }
    }

    @Benchmark
    public void singleEntityCreate(ClientState state, Blackhole blackhole) {
        String[] savedId = {null};
        state.eventloop.submit(() -> state.client.save("bench-tenant", "orders", Map.of(
            "orderId", UUID.randomUUID().toString(),
            "amount", 123,
            "status", "open"
        )).whenComplete((entity, exception) -> savedId[0] = entity.id()));
        state.eventloop.run();
        blackhole.consume(savedId[0]);
    }

    @Benchmark
    public void batchUpsert(ClientState state, Blackhole blackhole) {
        List<Map<String, Object>> batch = new ArrayList<>(state.batchSize);
        for (int index = 0; index < state.batchSize; index++) {
            batch.add(Map.of(
                "id", "batch-" + index + '-' + UUID.randomUUID(),
                "orderId", "ORD-" + index,
                "amount", index,
                "status", (index % 2 == 0) ? "open" : "closed"
            ));
        }

        List<DataCloudClient.Entity> saved = new ArrayList<>(state.batchSize);
        state.eventloop.submit(() -> {
            for (Map<String, Object> entry : batch) {
                state.client.save("bench-tenant", "orders", entry).whenComplete((entity, exception) -> saved.add(entity));
            }
        });
        state.eventloop.run();
        blackhole.consume(saved.size());
    }

    @Benchmark
    public void queryById(ClientState state, Blackhole blackhole) {
        String entityId = state.seededIds.get(17);
        QueryByIdState result = new QueryByIdState();
        state.eventloop.submit(() -> state.client.findById("bench-tenant", "orders", entityId)
            .whenComplete((entity, exception) -> result.entity = entity));
        state.eventloop.run();
        blackhole.consume(result.entity);
    }

    @Benchmark
    public void queryByFilter(ClientState state, Blackhole blackhole) {
        QueryByFilterState result = new QueryByFilterState();
        state.eventloop.submit(() -> state.client.query("bench-tenant", "orders", DataCloudClient.Query.builder()
            .filter(DataCloudClient.Filter.eq("status", "open"))
            .limit(100)
            .build())
            .whenComplete((entities, exception) -> result.entities = entities));
        state.eventloop.run();
        blackhole.consume(result.entities.size());
    }

    private static final class QueryByIdState {
        private Optional<DataCloudClient.Entity> entity = Optional.empty();
    }

    private static final class QueryByFilterState {
        private List<DataCloudClient.Entity> entities = List.of();
    }
}