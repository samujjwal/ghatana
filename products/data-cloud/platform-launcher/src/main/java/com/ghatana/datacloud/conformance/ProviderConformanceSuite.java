package com.ghatana.datacloud.conformance;

import com.ghatana.datacloud.spi.*;
import com.ghatana.platform.types.identity.Offset;
import io.activej.promise.Promise;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.*;

/**
 * Provider conformance suite for validating EntityStore and EventLogStore SPI implementations (P3.1).
 *
 * <p>Tests required behaviors for durable HA providers including:
 * <ul>
 *   <li>CRUD operations</li>
 *   <li>Batch processing</li>
 *   <li>Query and filtering</li>
 *   <li>Event append and read</li>
 *   <li>Tenant isolation</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Validate provider conformance against DataCloud SPI contracts
 * @doc.layer product
 * @doc.pattern Test Suite
 */
public final class ProviderConformanceSuite {

    private ProviderConformanceSuite() {}

    /**
     * Run all conformance tests against an EntityStore implementation.
     */
    public static List<ConformanceResult> testEntityStore(EntityStore store) {
        List<ConformanceResult> results = new ArrayList<>();
        TenantContext tenant = TenantContext.of("conformance-test-tenant");

        // Test 1: save + getById
        results.add(test("entity_save_and_get", () -> {
            EntityStore.Entity e = EntityStore.Entity.builder()
                .id(UUID.randomUUID().toString())
                .collection("ConformanceTest")
                .data(Map.of("key", "value"))
                .metadata(EntityStore.EntityMetadata.empty())
                .build();
            EntityStore.Entity saved = block(store.save(tenant, e));
            if (saved == null || saved.id() == null) throw new AssertionError("Save returned null");
            Optional<EntityStore.Entity> retrieved = block(store.findById(tenant, saved.id()));
            if (retrieved.isEmpty()) throw new AssertionError("GetById returned empty");
            if (!retrieved.get().id().equals(saved.id())) throw new AssertionError("ID mismatch");
        }));

        // Test 2: batch save
        results.add(test("entity_batch_save", () -> {
            List<EntityStore.Entity> batch = List.of(
                EntityStore.Entity.builder().id(UUID.randomUUID().toString()).collection("BatchTest").data(Map.of("k1", "v1")).metadata(EntityStore.EntityMetadata.empty()).build(),
                EntityStore.Entity.builder().id(UUID.randomUUID().toString()).collection("BatchTest").data(Map.of("k2", "v2")).metadata(EntityStore.EntityMetadata.empty()).build()
            );
            BatchResult<String> result = block(store.saveBatch(tenant, batch));
            if (result == null || !result.isFullySuccessful()) throw new AssertionError("Batch save failed");
        }));

        // Test 3: query
        results.add(test("entity_query", () -> {
            EntityStore.QueryResult queryResult = block(store.query(tenant, EntityStore.QuerySpec.builder().collection("ConformanceTest").limit(10).build()));
            List<EntityStore.Entity> found = queryResult.entities();
            if (found == null) throw new AssertionError("Query returned null");
        }));

        // Test 4: delete
        results.add(test("entity_delete", () -> {
            EntityStore.Entity e = EntityStore.Entity.builder()
                .id(UUID.randomUUID().toString())
                .collection("DeleteTest")
                .data(Map.of())
                .metadata(EntityStore.EntityMetadata.empty())
                .build();
            EntityStore.Entity saved = block(store.save(tenant, e));
            block(store.delete(tenant, saved.id()));
            Optional<EntityStore.Entity> afterDelete = block(store.findById(tenant, saved.id()));
            if (afterDelete.isPresent()) throw new AssertionError("Entity still exists after delete");
        }));

        // Test 5: tenant isolation
        results.add(test("entity_tenant_isolation", () -> {
            TenantContext other = TenantContext.of("other-tenant");
            EntityStore.Entity e = EntityStore.Entity.builder()
                .id(UUID.randomUUID().toString())
                .collection("IsolationTest")
                .data(Map.of("tenant", tenant.tenantId()))
                .metadata(EntityStore.EntityMetadata.empty())
                .build();
            EntityStore.Entity saved = block(store.save(tenant, e));
            Optional<EntityStore.Entity> crossTenant = block(store.findById(other, saved.id()));
            if (crossTenant.isPresent()) throw new AssertionError("Cross-tenant access allowed");
        }));

        return results;
    }

    /**
     * Run all conformance tests against an EventLogStore implementation.
     */
    public static List<ConformanceResult> testEventLogStore(EventLogStore store) {
        List<ConformanceResult> results = new ArrayList<>();
        TenantContext tenant = TenantContext.of("conformance-test-tenant");

        // Test 1: append + read
        results.add(test("event_append_and_read", () -> {
            EventLogStore.EventEntry entry = new EventLogStore.EventEntry(
                UUID.randomUUID(), "conformance.test", "1.0.0", Instant.now(),
                ByteBuffer.wrap("{\"test\":\"true\"}".getBytes()), "application/json", Map.of(), Optional.empty());
            Offset offset = block(store.append(tenant, entry));
            if (offset == null || Long.parseLong(offset.value()) < 0) throw new AssertionError("Append returned invalid offset");
            List<EventLogStore.EventEntry> read = block(store.read(tenant, Offset.zero(), 100));
            if (read.isEmpty()) throw new AssertionError("Read returned empty after append");
        }));

        // Test 2: batch append
        results.add(test("event_batch_append", () -> {
            List<EventLogStore.EventEntry> batch = List.of(
                new EventLogStore.EventEntry(UUID.randomUUID(), "batch.1", "1.0.0", Instant.now(),
                    ByteBuffer.wrap("{}".getBytes()), "application/json", Map.of(), Optional.empty()),
                new EventLogStore.EventEntry(UUID.randomUUID(), "batch.2", "1.0.0", Instant.now(),
                    ByteBuffer.wrap("{}".getBytes()), "application/json", Map.of(), Optional.empty())
            );
            List<Offset> offsets = block(store.appendBatch(tenant, batch));
            if (offsets == null || offsets.size() != 2) throw new AssertionError("Batch append failed");
        }));

        // Test 3: readByType
        results.add(test("event_read_by_type", () -> {
            EventLogStore.EventEntry entry = new EventLogStore.EventEntry(
                UUID.randomUUID(), "typed.test", "1.0.0", Instant.now(),
                ByteBuffer.wrap("{\"key\":\"value\"}".getBytes()), "application/json", Map.of(), Optional.empty());
            block(store.append(tenant, entry));
            List<EventLogStore.EventEntry> found = block(store.readByType(tenant, "typed.test", Offset.zero(), 100));
            if (found.stream().noneMatch(e -> e.eventType().equals("typed.test"))) throw new AssertionError("ReadByType did not find expected event");
        }));

        // Test 4: readByTimeRange
        results.add(test("event_read_by_time_range", () -> {
            Instant now = Instant.now();
            EventLogStore.EventEntry entry = new EventLogStore.EventEntry(
                UUID.randomUUID(), "timerange.test", "1.0.0", now,
                ByteBuffer.wrap("{}".getBytes()), "application/json", Map.of(), Optional.empty());
            block(store.append(tenant, entry));
            List<EventLogStore.EventEntry> found = block(store.readByTimeRange(tenant, now.minusSeconds(60), now.plusSeconds(60), 100));
            if (found.stream().noneMatch(e -> e.eventType().equals("timerange.test"))) throw new AssertionError("ReadByTimeRange did not find expected event");
        }));

        // Test 5: tenant isolation
        results.add(test("event_tenant_isolation", () -> {
            TenantContext other = TenantContext.of("other-tenant");
            EventLogStore.EventEntry entry = new EventLogStore.EventEntry(
                UUID.randomUUID(), "isolation.test", "1.0.0", Instant.now(),
                ByteBuffer.wrap("{}".getBytes()), "application/json", Map.of(), Optional.empty());
            block(store.append(tenant, entry));
            List<EventLogStore.EventEntry> crossTenant = block(store.read(other, Offset.zero(), 100));
            if (crossTenant.stream().anyMatch(e -> e.eventType().equals("isolation.test"))) throw new AssertionError("Cross-tenant event access allowed");
        }));

        return results;
    }

    /**
     * Block on a promise to get its result synchronously.
     * This is a simplified blocking wrapper for conformance tests.
     */
    private static <T> T block(Promise<T> promise) {
        return promise.toCompletableFuture().join();
    }

    private static ConformanceResult test(String name, TestFn fn) {
        try {
            fn.run();
            return new ConformanceResult(name, true, null);
        } catch (Exception e) {
            return new ConformanceResult(name, false, e.getMessage());
        }
    }

    @FunctionalInterface
    private interface TestFn {
        void run() throws Exception;
    }

    /**
     * Result of a single conformance test.
     */
    public record ConformanceResult(String testName, boolean passed, String error) {}
}
