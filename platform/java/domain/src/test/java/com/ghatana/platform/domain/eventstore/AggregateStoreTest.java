package com.ghatana.platform.domain.eventstore;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.domain.eventstore.AggregateStore.AggregateEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for AggregateStore contract.
 *
 * @doc.type class
 * @doc.purpose AggregateStore contract tests (KERNEL-P1)
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("AggregateStore Tests")
class AggregateStoreTest extends EventloopTestBase {

    private final InMemoryAggregateStore store = new InMemoryAggregateStore(Executors.newVirtualThreadPerTaskExecutor());

    @Test
    @DisplayName("Should append event and increment version")
    void shouldAppendEventAndIncrementVersion() {
        TenantContext tenant = TenantContext.of("tenant-1");
        String aggregateId = "aggregate-1";
        byte[] payload = "{\"test\":\"data\"}".getBytes();

        Long version = runPromise(() -> store.appendEvent(
            tenant,
            aggregateId,
            "test.event",
            "1.0.0",
            payload,
            0L
        ));

        assertThat(version).isEqualTo(1L);

        Optional<Long> currentVersion = runPromise(() -> store.getAggregateVersion(tenant, aggregateId));
        assertThat(currentVersion).hasValue(1L);
    }

    @Test
    @DisplayName("Should append multiple events and increment version")
    void shouldAppendMultipleEventsAndIncrementVersion() {
        TenantContext tenant = TenantContext.of("tenant-1");
        String aggregateId = "aggregate-1";

        Long version1 = runPromise(() -> store.appendEvent(tenant, aggregateId, "test.event", "1.0.0", "{\"v\":1}".getBytes(), 0L));
        Long version2 = runPromise(() -> store.appendEvent(tenant, aggregateId, "test.event", "1.0.0", "{\"v\":2}".getBytes(), version1));
        Long version3 = runPromise(() -> store.appendEvent(tenant, aggregateId, "test.event", "1.0.0", "{\"v\":3}".getBytes(), version2));

        assertThat(version1).isEqualTo(1L);
        assertThat(version2).isEqualTo(2L);
        assertThat(version3).isEqualTo(3L);

        Optional<Long> currentVersion = runPromise(() -> store.getAggregateVersion(tenant, aggregateId));
        assertThat(currentVersion).hasValue(3L);
    }

    @Test
    @DisplayName("Should fail optimistic concurrency when version mismatch")
    void shouldFailOptimisticConcurrencyWhenVersionMismatch() {
        TenantContext tenant = TenantContext.of("tenant-1");
        String aggregateId = "aggregate-1";

        Long version1 = runPromise(() -> store.appendEvent(tenant, aggregateId, "test.event", "1.0.0", "{\"v\":1}".getBytes(), 0L));

        assertThatThrownBy(() -> runPromise(() -> store.appendEvent(
            tenant,
            aggregateId,
            "test.event",
            "1.0.0",
            "{\"v\":2}".getBytes(),
            0L // Wrong expected version
        )))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Optimistic concurrency violation");
    }

    @Test
    @DisplayName("Should read all aggregate events")
    void shouldReadAllAggregateEvents() {
        TenantContext tenant = TenantContext.of("tenant-1");
        String aggregateId = "aggregate-1";

        runPromise(() -> store.appendEvent(tenant, aggregateId, "test.event", "1.0.0", "{\"v\":1}".getBytes(), 0L));
        runPromise(() -> store.appendEvent(tenant, aggregateId, "test.event", "1.0.0", "{\"v\":2}".getBytes(), 1L));
        runPromise(() -> store.appendEvent(tenant, aggregateId, "test.event", "1.0.0", "{\"v\":3}".getBytes(), 2L));

        List<AggregateEvent> events = runPromise(() -> store.readAggregateEvents(tenant, aggregateId));

        assertThat(events).hasSize(3);
        assertThat(events.get(0).version()).isEqualTo(1L);
        assertThat(events.get(1).version()).isEqualTo(2L);
        assertThat(events.get(2).version()).isEqualTo(3L);
    }

    @Test
    @DisplayName("Should read aggregate events from version")
    void shouldReadAggregateEventsFromVersion() {
        TenantContext tenant = TenantContext.of("tenant-1");
        String aggregateId = "aggregate-1";

        runPromise(() -> store.appendEvent(tenant, aggregateId, "test.event", "1.0.0", "{\"v\":1}".getBytes(), 0L));
        runPromise(() -> store.appendEvent(tenant, aggregateId, "test.event", "1.0.0", "{\"v\":2}".getBytes(), 1L));
        runPromise(() -> store.appendEvent(tenant, aggregateId, "test.event", "1.0.0", "{\"v\":3}".getBytes(), 2L));

        List<AggregateEvent> events = runPromise(() -> store.readAggregateEventsFromVersion(tenant, aggregateId, 2L));

        assertThat(events).hasSize(2);
        assertThat(events.get(0).version()).isEqualTo(2L);
        assertThat(events.get(1).version()).isEqualTo(3L);
    }

    @Test
    @DisplayName("Should return empty list for non-existent aggregate")
    void shouldReturnEmptyListForNonExistentAggregate() {
        TenantContext tenant = TenantContext.of("tenant-1");
        String aggregateId = "non-existent";

        List<AggregateEvent> events = runPromise(() -> store.readAggregateEvents(tenant, aggregateId));

        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("Should check aggregate existence")
    void shouldCheckAggregateExistence() {
        TenantContext tenant = TenantContext.of("tenant-1");
        String aggregateId = "aggregate-1";

        boolean existsBefore = runPromise(() -> store.aggregateExists(tenant, aggregateId));
        assertThat(existsBefore).isFalse();

        runPromise(() -> store.appendEvent(tenant, aggregateId, "test.event", "1.0.0", "{\"v\":1}".getBytes(), 0L));

        boolean existsAfter = runPromise(() -> store.aggregateExists(tenant, aggregateId));
        assertThat(existsAfter).isTrue();
    }

    @Test
    @DisplayName("Should delete aggregate")
    void shouldDeleteAggregate() {
        TenantContext tenant = TenantContext.of("tenant-1");
        String aggregateId = "aggregate-1";

        runPromise(() -> store.appendEvent(tenant, aggregateId, "test.event", "1.0.0", "{\"v\":1}".getBytes(), 0L));

        boolean existsBefore = runPromise(() -> store.aggregateExists(tenant, aggregateId));
        assertThat(existsBefore).isTrue();

        runPromise(() -> store.deleteAggregate(tenant, aggregateId));

        boolean existsAfter = runPromise(() -> store.aggregateExists(tenant, aggregateId));
        assertThat(existsAfter).isFalse();

        List<AggregateEvent> events = runPromise(() -> store.readAggregateEvents(tenant, aggregateId));
        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("Should isolate aggregates by tenant")
    void shouldIsolateAggregatesByTenant() {
        String aggregateId = "aggregate-1";

        TenantContext tenant1 = TenantContext.of("tenant-1");
        TenantContext tenant2 = TenantContext.of("tenant-2");

        runPromise(() -> store.appendEvent(tenant1, aggregateId, "test.event", "1.0.0", "{\"v\":1}".getBytes(), 0L));
        runPromise(() -> store.appendEvent(tenant2, aggregateId, "test.event", "1.0.0", "{\"v\":2}".getBytes(), 0L));

        List<AggregateEvent> events1 = runPromise(() -> store.readAggregateEvents(tenant1, aggregateId));
        List<AggregateEvent> events2 = runPromise(() -> store.readAggregateEvents(tenant2, aggregateId));

        assertThat(events1).hasSize(1);
        assertThat(events2).hasSize(1);
        assertThat(events1.get(0).payload()).isNotEqualTo(events2.get(0).payload());
    }
}
