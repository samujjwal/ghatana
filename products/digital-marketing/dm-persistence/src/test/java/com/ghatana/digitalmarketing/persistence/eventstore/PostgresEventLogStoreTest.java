package com.ghatana.digitalmarketing.persistence.eventstore;

import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.platform.domain.eventstore.TenantContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.types.identity.Offset;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@DisplayName("PostgresEventLogStore Integration Tests")
class PostgresEventLogStoreTest extends EventloopTestBase {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("dmos_test")
        .withUsername("dmos")
        .withPassword("dmos_secret");

    private static PostgresEventLogStore store;

    @BeforeAll
    static void setup() {
        Flyway flyway = Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .locations("filesystem:src/main/resources/db/migration")
            .load();
        flyway.migrate();

        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(postgres.getJdbcUrl());
        dataSource.setUser(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());

        Executor executor = Runnable::run;
        store = new PostgresEventLogStore(dataSource, executor);
    }

    @BeforeEach
    void cleanup() throws Exception {
        try (var connection = postgres.createConnection("")) {
            connection.createStatement().executeUpdate("DELETE FROM dmos_event_log");
            connection.createStatement().executeUpdate("DELETE FROM dmos_event_log_offsets");
        }
    }

    @Test
    @DisplayName("append should allocate monotonic tenant offsets")
    void appendShouldAllocateMonotonicTenantOffsets() {
        TenantContext tenant = TenantContext.of("tenant-a");

        Offset offset1 = runPromise(() -> store.append(tenant, entry("dmos.campaign.created", "{\"id\":\"c1\"}")));
        Offset offset2 = runPromise(() -> store.append(tenant, entry("dmos.campaign.launched", "{\"id\":\"c1\"}")));

        assertThat(offset1.value()).isEqualTo("1");
        assertThat(offset2.value()).isEqualTo("2");
    }

    @Test
    @DisplayName("read should return entries from requested offset and isolate tenants")
    void readShouldReturnEntriesAndIsolateTenants() {
        TenantContext tenantA = TenantContext.of("tenant-a");
        TenantContext tenantB = TenantContext.of("tenant-b");

        runPromise(() -> store.append(tenantA, entry("a-1", "one")));
        runPromise(() -> store.append(tenantA, entry("a-2", "two")));
        runPromise(() -> store.append(tenantB, entry("b-1", "three")));

        List<EventLogStore.EventEntry> tenantAEntries =
            runPromise(() -> store.read(tenantA, Offset.of("1"), 100));

        assertThat(tenantAEntries).hasSize(2);
        assertThat(new String(toBytes(tenantAEntries.get(0).payload()))).isEqualTo("one");
        assertThat(new String(toBytes(tenantAEntries.get(1).payload()))).isEqualTo("two");
    }

    @Test
    @DisplayName("readByType should filter by event type")
    void readByTypeShouldFilter() {
        TenantContext tenant = TenantContext.of("tenant-a");

        runPromise(() -> store.append(tenant, entry("dmos.campaign.created", "created")));
        runPromise(() -> store.append(tenant, entry("dmos.campaign.launched", "launched")));
        runPromise(() -> store.append(tenant, entry("dmos.campaign.created", "created-2")));

        List<EventLogStore.EventEntry> created =
            runPromise(() -> store.readByType(tenant, "dmos.campaign.created", Offset.of("1"), 10));

        assertThat(created).hasSize(2);
    }

    @Test
    @DisplayName("getLatestOffset should return zero for empty tenant")
    void latestOffsetShouldReturnZeroForEmptyTenant() {
        TenantContext tenant = TenantContext.of("tenant-a");
        Offset latest = runPromise(() -> store.getLatestOffset(tenant));
        assertThat(latest.value()).isEqualTo("0");
    }

    private static EventLogStore.EventEntry entry(String eventType, String body) {
        return new EventLogStore.EventEntry(
            UUID.randomUUID(),
            eventType,
            "1.0.0",
            Instant.now(),
            ByteBuffer.wrap(body.getBytes()),
            "application/json",
            Map.of("source", "test"),
            Optional.empty()
        );
    }

    private static byte[] toBytes(ByteBuffer payload) {
        ByteBuffer duplicate = payload.asReadOnlyBuffer();
        byte[] bytes = new byte[duplicate.remaining()];
        duplicate.get(bytes);
        return bytes;
    }
}
