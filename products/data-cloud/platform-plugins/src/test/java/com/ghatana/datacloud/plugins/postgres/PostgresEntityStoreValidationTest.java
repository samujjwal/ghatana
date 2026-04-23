package com.ghatana.datacloud.plugins.postgres;

import com.ghatana.datacloud.spi.EntityStore;
import com.ghatana.datacloud.spi.TenantContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PostgresEntityStore Validation Tests")
class PostgresEntityStoreValidationTest extends EventloopTestBase {

    @AfterEach
    void clearSystemPropertyOverrides() { // GH-90000
        System.clearProperty("datacloud.db.url");
        System.clearProperty("datacloud.db.user");
        System.clearProperty("datacloud.db.password");
        System.clearProperty("datacloud.db.poolMaxSize");
        System.clearProperty("datacloud.db.poolMinIdle");
        System.clearProperty("datacloud.db.connectionTimeoutMs");
        System.clearProperty("datacloud.db.idleTimeoutMs");
        System.clearProperty("datacloud.db.maxLifetimeMs");
    }

    @Test
    @DisplayName("config normalizes blanks and applies defaults")
    void configNormalizesBlanksAndAppliesDefaults() { // GH-90000
        PostgresEntityStoreConfig config = new PostgresEntityStoreConfig( // GH-90000
            " jdbc:postgresql://localhost:5432/test ",
            " user ",
            " secret ",
            0,
            -1,
            0,
            0,
            0
        );

        assertThat(config.jdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5432/test");
        assertThat(config.username()).isEqualTo("user");
        assertThat(config.password()).isEqualTo("secret");
        assertThat(config.maxPoolSize()).isEqualTo(16); // GH-90000
        assertThat(config.minIdle()).isEqualTo(2); // GH-90000
        assertThat(config.connectionTimeoutMs()).isEqualTo(30_000L); // GH-90000
        assertThat(config.idleTimeoutMs()).isEqualTo(600_000L); // GH-90000
        assertThat(config.maxLifetimeMs()).isEqualTo(1_800_000L); // GH-90000
        assertThat(config.isConfigured()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("config fails fast when datasource cannot connect")
    void configFailsFastWhenDatasourceCannotConnect() { // GH-90000
        PostgresEntityStoreConfig config = new PostgresEntityStoreConfig( // GH-90000
            "jdbc:postgresql://localhost:5432/test",
            "user",
            "secret",
            4,
            10,
            5_000L,
            15_000L,
            60_000L
        );

        assertThatThrownBy(config::createDataSource) // GH-90000
            .isInstanceOf(RuntimeException.class) // GH-90000
            .hasMessageContaining("Failed to initialize pool");
    }

    @Test
    @DisplayName("config rejects missing database credentials")
    void configRejectsMissingDatabaseCredentials() { // GH-90000
        PostgresEntityStoreConfig config = new PostgresEntityStoreConfig(null, " ", null, 1, 0, 1, 1, 1); // GH-90000

        assertThat(config.isConfigured()).isFalse(); // GH-90000
        assertThatThrownBy(config::createDataSource) // GH-90000
            .isInstanceOf(IllegalStateException.class) // GH-90000
            .hasMessageContaining("PostgresEntityStore requires DATACLOUD_DB_URL");
    }

    @Test
    @DisplayName("config resolves embedded provider settings from system properties")
    void configResolvesEmbeddedProviderSettingsFromSystemProperties() { // GH-90000
        System.setProperty("datacloud.db.url", "jdbc:postgresql://localhost:5432/property-db"); // GH-90000
        System.setProperty("datacloud.db.user", "property-user"); // GH-90000
        System.setProperty("datacloud.db.password", "property-secret"); // GH-90000
        System.setProperty("datacloud.db.poolMaxSize", "9"); // GH-90000
        System.setProperty("datacloud.db.poolMinIdle", "3"); // GH-90000
        System.setProperty("datacloud.db.connectionTimeoutMs", "1200"); // GH-90000
        System.setProperty("datacloud.db.idleTimeoutMs", "2400"); // GH-90000
        System.setProperty("datacloud.db.maxLifetimeMs", "3600"); // GH-90000

        PostgresEntityStoreConfig config = PostgresEntityStoreConfig.fromEnvironmentIfPresent().orElseThrow(); // GH-90000

        assertThat(config.jdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5432/property-db");
        assertThat(config.username()).isEqualTo("property-user");
        assertThat(config.password()).isEqualTo("property-secret");
        assertThat(config.maxPoolSize()).isEqualTo(9); // GH-90000
        assertThat(config.minIdle()).isEqualTo(3); // GH-90000
        assertThat(config.connectionTimeoutMs()).isEqualTo(1200L); // GH-90000
        assertThat(config.idleTimeoutMs()).isEqualTo(2400L); // GH-90000
        assertThat(config.maxLifetimeMs()).isEqualTo(3600L); // GH-90000
    }

    @Test
    @DisplayName("store validates required arguments")
    void storeValidatesRequiredArguments() { // GH-90000
        ExecutorService executor = Executors.newSingleThreadExecutor(); // GH-90000
        try (PostgresEntityStore store = new PostgresEntityStore(null, executor)) { // GH-90000
            TenantContext tenant = TenantContext.of("tenant-a");
            EntityStore.Entity entity = EntityStore.Entity.builder() // GH-90000
                .collection("orders")
                .id("order-1")
                .data(Map.of("amount", 1)) // GH-90000
                .build(); // GH-90000
            EntityStore.QuerySpec query = EntityStore.QuerySpec.builder().collection("orders").build();
            EntityStore.EntityId id = EntityStore.EntityId.of("00000000-0000-0000-0000-000000000001");

            assertThatThrownBy(() -> store.save(null, entity)).isInstanceOf(NullPointerException.class); // GH-90000
            assertThatThrownBy(() -> store.save(tenant, null)).isInstanceOf(NullPointerException.class); // GH-90000
            assertThatThrownBy(() -> store.saveBatch(null, List.of(entity))).isInstanceOf(NullPointerException.class); // GH-90000
            assertThatThrownBy(() -> store.saveBatch(tenant, null)).isInstanceOf(NullPointerException.class); // GH-90000
            assertThatThrownBy(() -> store.findById(null, id)).isInstanceOf(NullPointerException.class); // GH-90000
            assertThatThrownBy(() -> store.findById(tenant, null)).isInstanceOf(NullPointerException.class); // GH-90000
            assertThatThrownBy(() -> store.findByIds(null, List.of(id))).isInstanceOf(NullPointerException.class); // GH-90000
            assertThatThrownBy(() -> store.findByIds(tenant, null)).isInstanceOf(NullPointerException.class); // GH-90000
            assertThatThrownBy(() -> store.query(null, query)).isInstanceOf(NullPointerException.class); // GH-90000
            assertThatThrownBy(() -> store.query(tenant, null)).isInstanceOf(NullPointerException.class); // GH-90000
            assertThatThrownBy(() -> store.delete(null, id)).isInstanceOf(NullPointerException.class); // GH-90000
            assertThatThrownBy(() -> store.delete(tenant, null)).isInstanceOf(NullPointerException.class); // GH-90000
            assertThatThrownBy(() -> store.deleteBatch(null, List.of(id))).isInstanceOf(NullPointerException.class); // GH-90000
            assertThatThrownBy(() -> store.deleteBatch(tenant, null)).isInstanceOf(NullPointerException.class); // GH-90000
            assertThatThrownBy(() -> store.count(null, query)).isInstanceOf(NullPointerException.class); // GH-90000
            assertThatThrownBy(() -> store.count(tenant, null)).isInstanceOf(NullPointerException.class); // GH-90000
            assertThatThrownBy(() -> store.exists(null, id)).isInstanceOf(NullPointerException.class); // GH-90000
            assertThatThrownBy(() -> store.exists(tenant, null)).isInstanceOf(NullPointerException.class); // GH-90000
        }
    }

    @Test
    @DisplayName("store returns empty results for empty id list without opening a datasource")
    void storeReturnsEmptyResultsForEmptyIdList() { // GH-90000
        ExecutorService executor = Executors.newSingleThreadExecutor(); // GH-90000
        try (PostgresEntityStore store = new PostgresEntityStore(null, executor)) { // GH-90000

            List<EntityStore.Entity> entities = runPromise(() -> store.findByIds(TenantContext.of("tenant-a"), List.of()));

            assertThat(entities).isEmpty(); // GH-90000
        }
    }

    @Test
    @DisplayName("store reports missing database configuration when execution reaches datasource creation")
    void storeReportsMissingDatabaseConfiguration() { // GH-90000
        ExecutorService executor = Executors.newSingleThreadExecutor(); // GH-90000
        try (PostgresEntityStore store = new PostgresEntityStore((PostgresEntityStoreConfig) null, executor)) { // GH-90000

            assertThatThrownBy(() -> runPromise(() -> store.count( // GH-90000
                TenantContext.of("tenant-a"),
                EntityStore.QuerySpec.builder().collection("orders").build()
            )))
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("no database configuration was provided");
        }
    }

    @Test
    @DisplayName("store constructor requires an executor")
    void storeConstructorRequiresExecutor() { // GH-90000
        assertThatThrownBy(() -> new PostgresEntityStore(null, null)) // GH-90000
            .isInstanceOf(NullPointerException.class) // GH-90000
            .hasMessageContaining("blockingExecutor required");
    }
}
