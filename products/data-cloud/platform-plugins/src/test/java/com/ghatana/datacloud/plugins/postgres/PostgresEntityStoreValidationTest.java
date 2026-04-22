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

@DisplayName("PostgresEntityStore Validation Tests [GH-90000]")
class PostgresEntityStoreValidationTest extends EventloopTestBase {

    @AfterEach
    void clearSystemPropertyOverrides() { // GH-90000
        System.clearProperty("datacloud.db.url [GH-90000]");
        System.clearProperty("datacloud.db.user [GH-90000]");
        System.clearProperty("datacloud.db.password [GH-90000]");
        System.clearProperty("datacloud.db.poolMaxSize [GH-90000]");
        System.clearProperty("datacloud.db.poolMinIdle [GH-90000]");
        System.clearProperty("datacloud.db.connectionTimeoutMs [GH-90000]");
        System.clearProperty("datacloud.db.idleTimeoutMs [GH-90000]");
        System.clearProperty("datacloud.db.maxLifetimeMs [GH-90000]");
    }

    @Test
    @DisplayName("config normalizes blanks and applies defaults [GH-90000]")
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

        assertThat(config.jdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5432/test [GH-90000]");
        assertThat(config.username()).isEqualTo("user [GH-90000]");
        assertThat(config.password()).isEqualTo("secret [GH-90000]");
        assertThat(config.maxPoolSize()).isEqualTo(16); // GH-90000
        assertThat(config.minIdle()).isEqualTo(2); // GH-90000
        assertThat(config.connectionTimeoutMs()).isEqualTo(30_000L); // GH-90000
        assertThat(config.idleTimeoutMs()).isEqualTo(600_000L); // GH-90000
        assertThat(config.maxLifetimeMs()).isEqualTo(1_800_000L); // GH-90000
        assertThat(config.isConfigured()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("config fails fast when datasource cannot connect [GH-90000]")
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
            .hasMessageContaining("Failed to initialize pool [GH-90000]");
    }

    @Test
    @DisplayName("config rejects missing database credentials [GH-90000]")
    void configRejectsMissingDatabaseCredentials() { // GH-90000
        PostgresEntityStoreConfig config = new PostgresEntityStoreConfig(null, " ", null, 1, 0, 1, 1, 1); // GH-90000

        assertThat(config.isConfigured()).isFalse(); // GH-90000
        assertThatThrownBy(config::createDataSource) // GH-90000
            .isInstanceOf(IllegalStateException.class) // GH-90000
            .hasMessageContaining("PostgresEntityStore requires DATACLOUD_DB_URL [GH-90000]");
    }

    @Test
    @DisplayName("config resolves embedded provider settings from system properties [GH-90000]")
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

        assertThat(config.jdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5432/property-db [GH-90000]");
        assertThat(config.username()).isEqualTo("property-user [GH-90000]");
        assertThat(config.password()).isEqualTo("property-secret [GH-90000]");
        assertThat(config.maxPoolSize()).isEqualTo(9); // GH-90000
        assertThat(config.minIdle()).isEqualTo(3); // GH-90000
        assertThat(config.connectionTimeoutMs()).isEqualTo(1200L); // GH-90000
        assertThat(config.idleTimeoutMs()).isEqualTo(2400L); // GH-90000
        assertThat(config.maxLifetimeMs()).isEqualTo(3600L); // GH-90000
    }

    @Test
    @DisplayName("store validates required arguments [GH-90000]")
    void storeValidatesRequiredArguments() { // GH-90000
        ExecutorService executor = Executors.newSingleThreadExecutor(); // GH-90000
        try (PostgresEntityStore store = new PostgresEntityStore(null, executor)) { // GH-90000
            TenantContext tenant = TenantContext.of("tenant-a [GH-90000]");
            EntityStore.Entity entity = EntityStore.Entity.builder() // GH-90000
                .collection("orders [GH-90000]")
                .id("order-1 [GH-90000]")
                .data(Map.of("amount", 1)) // GH-90000
                .build(); // GH-90000
            EntityStore.QuerySpec query = EntityStore.QuerySpec.builder().collection("orders [GH-90000]").build();
            EntityStore.EntityId id = EntityStore.EntityId.of("00000000-0000-0000-0000-000000000001 [GH-90000]");

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
    @DisplayName("store returns empty results for empty id list without opening a datasource [GH-90000]")
    void storeReturnsEmptyResultsForEmptyIdList() { // GH-90000
        ExecutorService executor = Executors.newSingleThreadExecutor(); // GH-90000
        try (PostgresEntityStore store = new PostgresEntityStore(null, executor)) { // GH-90000

            List<EntityStore.Entity> entities = runPromise(() -> store.findByIds(TenantContext.of("tenant-a [GH-90000]"), List.of()));

            assertThat(entities).isEmpty(); // GH-90000
        }
    }

    @Test
    @DisplayName("store reports missing database configuration when execution reaches datasource creation [GH-90000]")
    void storeReportsMissingDatabaseConfiguration() { // GH-90000
        ExecutorService executor = Executors.newSingleThreadExecutor(); // GH-90000
        try (PostgresEntityStore store = new PostgresEntityStore((PostgresEntityStoreConfig) null, executor)) { // GH-90000

            assertThatThrownBy(() -> runPromise(() -> store.count( // GH-90000
                TenantContext.of("tenant-a [GH-90000]"),
                EntityStore.QuerySpec.builder().collection("orders [GH-90000]").build()
            )))
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("no database configuration was provided [GH-90000]");
        }
    }

    @Test
    @DisplayName("store constructor requires an executor [GH-90000]")
    void storeConstructorRequiresExecutor() { // GH-90000
        assertThatThrownBy(() -> new PostgresEntityStore(null, null)) // GH-90000
            .isInstanceOf(NullPointerException.class) // GH-90000
            .hasMessageContaining("blockingExecutor required [GH-90000]");
    }
}
