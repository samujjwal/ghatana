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
    void clearSystemPropertyOverrides() { 
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
    void configNormalizesBlanksAndAppliesDefaults() { 
        PostgresEntityStoreConfig config = new PostgresEntityStoreConfig( 
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
        assertThat(config.maxPoolSize()).isEqualTo(16); 
        assertThat(config.minIdle()).isEqualTo(2); 
        assertThat(config.connectionTimeoutMs()).isEqualTo(30_000L); 
        assertThat(config.idleTimeoutMs()).isEqualTo(600_000L); 
        assertThat(config.maxLifetimeMs()).isEqualTo(1_800_000L); 
        assertThat(config.isConfigured()).isTrue(); 
    }

    @Test
    @DisplayName("config fails fast when datasource cannot connect")
    void configFailsFastWhenDatasourceCannotConnect() { 
        PostgresEntityStoreConfig config = new PostgresEntityStoreConfig( 
            "jdbc:postgresql://localhost:5432/test",
            "user",
            "secret",
            4,
            10,
            5_000L,
            15_000L,
            60_000L
        );

        assertThatThrownBy(config::createDataSource) 
            .isInstanceOf(RuntimeException.class) 
            .hasMessageContaining("Failed to initialize pool");
    }

    @Test
    @DisplayName("config rejects missing database credentials")
    void configRejectsMissingDatabaseCredentials() { 
        PostgresEntityStoreConfig config = new PostgresEntityStoreConfig(null, " ", null, 1, 0, 1, 1, 1); 

        assertThat(config.isConfigured()).isFalse(); 
        assertThatThrownBy(config::createDataSource) 
            .isInstanceOf(IllegalStateException.class) 
            .hasMessageContaining("PostgresEntityStore requires DATACLOUD_DB_URL");
    }

    @Test
    @DisplayName("config resolves embedded provider settings from system properties")
    void configResolvesEmbeddedProviderSettingsFromSystemProperties() { 
        System.setProperty("datacloud.db.url", "jdbc:postgresql://localhost:5432/property-db"); 
        System.setProperty("datacloud.db.user", "property-user"); 
        System.setProperty("datacloud.db.password", "property-secret"); 
        System.setProperty("datacloud.db.poolMaxSize", "9"); 
        System.setProperty("datacloud.db.poolMinIdle", "3"); 
        System.setProperty("datacloud.db.connectionTimeoutMs", "1200"); 
        System.setProperty("datacloud.db.idleTimeoutMs", "2400"); 
        System.setProperty("datacloud.db.maxLifetimeMs", "3600"); 

        PostgresEntityStoreConfig config = PostgresEntityStoreConfig.fromEnvironmentIfPresent().orElseThrow(); 

        assertThat(config.jdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5432/property-db");
        assertThat(config.username()).isEqualTo("property-user");
        assertThat(config.password()).isEqualTo("property-secret");
        assertThat(config.maxPoolSize()).isEqualTo(9); 
        assertThat(config.minIdle()).isEqualTo(3); 
        assertThat(config.connectionTimeoutMs()).isEqualTo(1200L); 
        assertThat(config.idleTimeoutMs()).isEqualTo(2400L); 
        assertThat(config.maxLifetimeMs()).isEqualTo(3600L); 
    }

    @Test
    @DisplayName("store validates required arguments")
    void storeValidatesRequiredArguments() { 
        ExecutorService executor = Executors.newSingleThreadExecutor(); 
        try (PostgresEntityStore store = new PostgresEntityStore(null, executor)) { 
            TenantContext tenant = TenantContext.of("tenant-a");
            EntityStore.Entity entity = EntityStore.Entity.builder() 
                .collection("orders")
                .id("order-1")
                .data(Map.of("amount", 1)) 
                .build(); 
            EntityStore.QuerySpec query = EntityStore.QuerySpec.builder().collection("orders").build();
            EntityStore.EntityId id = EntityStore.EntityId.of("00000000-0000-0000-0000-000000000001");

            assertThatThrownBy(() -> store.save(null, entity)).isInstanceOf(NullPointerException.class); 
            assertThatThrownBy(() -> store.save(tenant, null)).isInstanceOf(NullPointerException.class); 
            assertThatThrownBy(() -> store.saveBatch(null, List.of(entity))).isInstanceOf(NullPointerException.class); 
            assertThatThrownBy(() -> store.saveBatch(tenant, null)).isInstanceOf(NullPointerException.class); 
            assertThatThrownBy(() -> store.findById(null, id)).isInstanceOf(NullPointerException.class); 
            assertThatThrownBy(() -> store.findById(tenant, null)).isInstanceOf(NullPointerException.class); 
            assertThatThrownBy(() -> store.findByIds(null, List.of(id))).isInstanceOf(NullPointerException.class); 
            assertThatThrownBy(() -> store.findByIds(tenant, null)).isInstanceOf(NullPointerException.class); 
            assertThatThrownBy(() -> store.query(null, query)).isInstanceOf(NullPointerException.class); 
            assertThatThrownBy(() -> store.query(tenant, null)).isInstanceOf(NullPointerException.class); 
            assertThatThrownBy(() -> store.delete(null, id)).isInstanceOf(NullPointerException.class); 
            assertThatThrownBy(() -> store.delete(tenant, null)).isInstanceOf(NullPointerException.class); 
            assertThatThrownBy(() -> store.deleteBatch(null, List.of(id))).isInstanceOf(NullPointerException.class); 
            assertThatThrownBy(() -> store.deleteBatch(tenant, null)).isInstanceOf(NullPointerException.class); 
            assertThatThrownBy(() -> store.count(null, query)).isInstanceOf(NullPointerException.class); 
            assertThatThrownBy(() -> store.count(tenant, null)).isInstanceOf(NullPointerException.class); 
            assertThatThrownBy(() -> store.exists(null, id)).isInstanceOf(NullPointerException.class); 
            assertThatThrownBy(() -> store.exists(tenant, null)).isInstanceOf(NullPointerException.class); 
        }
    }

    @Test
    @DisplayName("store returns empty results for empty id list without opening a datasource")
    void storeReturnsEmptyResultsForEmptyIdList() { 
        ExecutorService executor = Executors.newSingleThreadExecutor(); 
        try (PostgresEntityStore store = new PostgresEntityStore(null, executor)) { 

            List<EntityStore.Entity> entities = runPromise(() -> store.findByIds(TenantContext.of("tenant-a"), List.of()));

            assertThat(entities).isEmpty(); 
        }
    }

    @Test
    @DisplayName("store reports missing database configuration when execution reaches datasource creation")
    void storeReportsMissingDatabaseConfiguration() { 
        ExecutorService executor = Executors.newSingleThreadExecutor(); 
        try (PostgresEntityStore store = new PostgresEntityStore((PostgresEntityStoreConfig) null, executor)) { 

            assertThatThrownBy(() -> runPromise(() -> store.count( 
                TenantContext.of("tenant-a"),
                EntityStore.QuerySpec.builder().collection("orders").build()
            )))
                .isInstanceOf(IllegalStateException.class) 
                .hasMessageContaining("no database configuration was provided");
        }
    }

    @Test
    @DisplayName("store constructor requires an executor")
    void storeConstructorRequiresExecutor() { 
        assertThatThrownBy(() -> new PostgresEntityStore(null, null)) 
            .isInstanceOf(NullPointerException.class) 
            .hasMessageContaining("blockingExecutor required");
    }
}
