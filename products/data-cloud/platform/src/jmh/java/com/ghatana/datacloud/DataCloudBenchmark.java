/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud;

import com.ghatana.datacloud.entity.DataType;
import com.ghatana.datacloud.entity.MetaField;
import com.ghatana.datacloud.entity.validation.EntitySchemaValidator;
import com.ghatana.datacloud.entity.validation.ValidationResult;
import com.ghatana.datacloud.spi.EventLogStore;
import com.ghatana.datacloud.spi.TenantContext;
import com.ghatana.datacloud.spi.provider.InMemoryEventLogStoreProvider;
import io.activej.eventloop.Eventloop;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * JMH performance benchmarks for the Data-Cloud platform.
 *
 * <h2>Benchmark Suites</h2>
 * <ol>
 *   <li><b>Schema validation</b> — valid &amp; invalid entity paths (pure CPU)</li>
 *   <li><b>Entity save</b> — in-memory {@link DataCloudClient#save} throughput</li>
 *   <li><b>Entity findById</b> — in-memory lookup latency</li>
 *   <li><b>Event log append</b> — single-event append rate</li>
 *   <li><b>Event log batch</b> — 100-event appendBatch rate</li>
 * </ol>
 *
 * <h2>Running</h2>
 * <pre>{@code
 * ./gradlew :products:data-cloud:platform:jmh
 * # results at build/reports/jmh/results.json
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose JMH performance benchmarks for Data-Cloud entity and event operations
 * @doc.layer product
 * @doc.pattern Benchmark
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class DataCloudBenchmark {

    // =========================================================================
    // State: Schema validator (shared across all threads — ConcurrentHashMap)
    // =========================================================================

    /**
     * Shared schema validator with a pre-registered "users" collection schema.
     * Thread-safe; a single instance is shared by all JMH worker threads.
     */
    @State(Scope.Benchmark)
    public static class ValidatorState {

        EntitySchemaValidator validator;
        Map<String, Object> validUserData;
        Map<String, Object> invalidUserData;

        @Setup(Level.Trial)
        public void setup() {
            validator = EntitySchemaValidator.create();

            List<MetaField> userFields = List.of(
                MetaField.builder().name("name").type(DataType.STRING).required(true).build(),
                MetaField.builder().name("email").type(DataType.EMAIL).required(true).build(),
                MetaField.builder().name("age").type(DataType.NUMBER).required(false).build()
            );
            validator.registerSchema("bench-tenant", "users", userFields);

            validUserData = Map.of(
                "name", "Alice Wonderland",
                "email", "alice@wonderland.example",
                "age", 30
            );
            // Missing required name, malformed email, non-numeric age
            invalidUserData = Map.of(
                "email", "not-an-email",
                "age", "not-a-number"
            );
        }
    }

    // =========================================================================
    // State: Per-thread DataCloud client + Eventloop
    //
    // Scope.Thread gives each JMH worker its own Eventloop + client, avoiding
    // cross-thread contention on the eventloop itself while still measuring
    // DataCloudClient performance through the full DefaultDataCloudClient path.
    // =========================================================================

    @State(Scope.Thread)
    public static class ClientState {

        Eventloop eventloop;
        DataCloudClient client;

        @Setup(Level.Trial)
        public void setup() {
            eventloop = Eventloop.builder().build();
            client = DataCloud.forTesting();
        }
    }

    // =========================================================================
    // State: Per-thread DataCloud client with a pre-seeded entity for lookups
    // =========================================================================

    @State(Scope.Thread)
    public static class SeededClientState {

        Eventloop eventloop;
        DataCloudClient client;
        String seededId;

        @Setup(Level.Trial)
        public void setup() {
            eventloop = Eventloop.builder().build();
            client = DataCloud.forTesting();

            String[] saved = {null};
            eventloop.submit(() ->
                client.save("bench-tenant", "products",
                        Map.of("sku", "SKU-001", "price", 99.99, "stock", 250))
                    .whenComplete((entity, ex) -> saved[0] = entity != null ? entity.id() : null)
            );
            eventloop.run();
            seededId = saved[0];
        }
    }

    // =========================================================================
    // State: Per-thread in-memory event log store for raw append benchmarks
    // =========================================================================

    @State(Scope.Thread)
    public static class EventLogState {

        Eventloop eventloop;
        InMemoryEventLogStoreProvider eventLogStore;
        TenantContext tenant;
        EventLogStore.EventEntry singleEvent;

        @Setup(Level.Trial)
        public void setup() {
            eventloop = Eventloop.builder().build();
            eventLogStore = new InMemoryEventLogStoreProvider();
            tenant = TenantContext.of("bench-tenant");
            singleEvent = EventLogStore.EventEntry.builder()
                .eventType("entity.created")
                .payload(ByteBuffer.wrap("{\"id\":\"123\",\"type\":\"product\"}".getBytes(StandardCharsets.UTF_8)))
                .build();
        }
    }

    // =========================================================================
    // State: Pre-built 100-event batch for appendBatch benchmark
    // =========================================================================

    @State(Scope.Thread)
    public static class BatchEventState {

        Eventloop eventloop;
        InMemoryEventLogStoreProvider eventLogStore;
        TenantContext tenant;
        List<EventLogStore.EventEntry> batch;

        @Setup(Level.Trial)
        public void setup() {
            eventloop = Eventloop.builder().build();
            eventLogStore = new InMemoryEventLogStoreProvider();
            tenant = TenantContext.of("bench-tenant-batch");

            batch = new ArrayList<>(100);
            for (int i = 0; i < 100; i++) {
                batch.add(EventLogStore.EventEntry.builder()
                    .eventType("entity.updated")
                    .payload(("{\"seq\":" + i + ",\"value\":\"payload-" + i + "\"}").getBytes(StandardCharsets.UTF_8))
                    .build());
            }
        }
    }

    // =========================================================================
    // Benchmark 1 — Schema validation: valid entity (happy path)
    //
    // Measures: throughput of the full validation path for a schema-passing entity.
    // No I/O; pure computation on a ConcurrentHashMap lookup + field checks.
    // =========================================================================

    /**
     * Validates a well-formed "users" entity — tests the happy-path validation pipeline.
     */
    @Benchmark
    public ValidationResult validateValidEntity(ValidatorState state) {
        return state.validator.validate("bench-tenant", "users", state.validUserData);
    }

    // =========================================================================
    // Benchmark 2 — Schema validation: invalid entity (rejection path)
    //
    // Measures: throughput when multiple violations are accumulated and returned.
    // =========================================================================

    /**
     * Validates a malformed entity — exercices required-field and type-check branches.
     */
    @Benchmark
    public void validateInvalidEntity(ValidatorState state, Blackhole bh) {
        ValidationResult result = state.validator.validate("bench-tenant", "users", state.invalidUserData);
        bh.consume(result.violations());
        bh.consume(result.valid());
    }

    // =========================================================================
    // Benchmark 3 — Entity save (in-memory, full DataCloudClient path)
    //
    // Measures: throughput of DataCloudClient.save() → InMemoryEntityStore.
    // Uses submit()/run() to give proper ActiveJ Eventloop context.
    // =========================================================================

    /**
     * Saves a new entity through the full {@link DataCloudClient} path.
     */
    @Benchmark
    public void entitySave(ClientState state, Blackhole bh) {
        String[] savedId = {null};
        state.eventloop.submit(() ->
            state.client.save("bench-tenant", "orders",
                    Map.of("orderId", "ORD-" + Thread.currentThread().getId(),
                           "amount", 129.99,
                           "status", "pending"))
                .whenComplete((entity, ex) -> savedId[0] = entity != null ? entity.id() : null)
        );
        state.eventloop.run();
        bh.consume(savedId[0]);
    }

    // =========================================================================
    // Benchmark 4 — Entity findById (in-memory, full DataCloudClient path)
    //
    // Measures: latency of a point-lookup after the entity has been seeded.
    // =========================================================================

    /**
     * Looks up a pre-seeded entity by its ID through {@link DataCloudClient}.
     */
    @Benchmark
    public void entityFindById(SeededClientState state, Blackhole bh) {
        Object[] found = {null};
        state.eventloop.submit(() ->
            state.client.findById("bench-tenant", "products", state.seededId)
                .whenComplete((opt, ex) -> found[0] = opt)
        );
        state.eventloop.run();
        bh.consume(found[0]);
    }

    // =========================================================================
    // Benchmark 5 — Event log single-event append
    //
    // Measures: throughput of EventLogStore.append() for a single event.
    // Benchmarks the InMemoryEventLogStoreProvider directly (no client overhead).
    // =========================================================================

    /**
     * Appends one event to the in-memory event log via {@link InMemoryEventLogStoreProvider}.
     */
    @Benchmark
    public void eventLogAppend(EventLogState state, Blackhole bh) {
        long[] offset = {0L};
        state.eventloop.submit(() ->
            state.eventLogStore.append(state.tenant, state.singleEvent)
                .whenComplete((o, ex) -> offset[0] = ex == null ? Long.parseLong(o.value()) : -1L)
        );
        state.eventloop.run();
        bh.consume(offset[0]);
    }

    // =========================================================================
    // Benchmark 6 — Event log 100-event batch append
    //
    // Measures: throughput of EventLogStore.appendBatch() for 100 events.
    // Reports ops/ms (where one "op" = one 100-event batch).
    // =========================================================================

    /**
     * Appends a pre-built 100-event batch in a single {@code appendBatch()} call.
     */
    @Benchmark
    public void eventLogAppendBatch(BatchEventState state, Blackhole bh) {
        int[] count = {0};
        state.eventloop.submit(() ->
            state.eventLogStore.appendBatch(state.tenant, state.batch)
                .whenComplete((offsets, ex) -> count[0] = offsets != null ? offsets.size() : 0)
        );
        state.eventloop.run();
        bh.consume(count[0]);
    }
}
