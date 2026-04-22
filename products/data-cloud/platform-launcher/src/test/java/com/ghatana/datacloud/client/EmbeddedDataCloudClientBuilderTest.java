package com.ghatana.datacloud.client;

import com.ghatana.datacloud.entity.EntityInterface;
import com.ghatana.datacloud.entity.storage.QuerySpecInterface;
import com.ghatana.datacloud.spi.DataStorageOperations;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmbeddedDataCloudClientBuilderTest {

    @Test
    @DisplayName("memory mode builds without reflective storage plugin loading [GH-90000]")
    void memoryModeBuilds() { // GH-90000
        EmbeddedDataCloudClient client = EmbeddedDataCloudClient.builder() // GH-90000
                .withMemoryStorage() // GH-90000
                .build(); // GH-90000

        assertThat(client).isNotNull(); // GH-90000
        client.close(); // GH-90000
    }

    @Test
    @DisplayName("postgres mode fails fast without an explicit storage adapter [GH-90000]")
    void postgresModeRequiresExplicitAdapter() { // GH-90000
        assertThatThrownBy(() -> EmbeddedDataCloudClient.builder() // GH-90000
                .withPostgreSQLStorage(Map.of("jdbcUrl", "jdbc:postgresql://localhost/test")) // GH-90000
                .build()) // GH-90000
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("requires an explicit DataStorageOperations implementation [GH-90000]");
    }

    @Test
    @DisplayName("redis mode fails fast without an explicit storage adapter [GH-90000]")
    void redisModeRequiresExplicitAdapter() { // GH-90000
        assertThatThrownBy(() -> EmbeddedDataCloudClient.builder() // GH-90000
                .withRedisStorage(Map.of("host", "localhost")) // GH-90000
                .build()) // GH-90000
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("requires an explicit DataStorageOperations implementation [GH-90000]");
    }

    @Test
    @DisplayName("non-memory modes build when a real storage adapter is supplied explicitly [GH-90000]")
    void explicitStorageAdapterEnablesNonMemoryMode() { // GH-90000
        EmbeddedDataCloudClient client = EmbeddedDataCloudClient.builder() // GH-90000
                .withPostgreSQLStorage(Map.of("jdbcUrl", "jdbc:postgresql://localhost/test")) // GH-90000
                .withDataStoragePlugin(new NoopDataStorageOperations()) // GH-90000
                .build(); // GH-90000

        assertThat(client).isNotNull(); // GH-90000
        client.close(); // GH-90000
    }

    private static final class NoopDataStorageOperations implements DataStorageOperations {
        @Override
        public Promise<EntityInterface> create(String tenantId, String collectionName, Map<String, Object> data) { // GH-90000
            return Promise.ofException(new UnsupportedOperationException("not used in builder test [GH-90000]"));
        }

        @Override
        public Promise<Optional<EntityInterface>> read(String tenantId, String collectionName, UUID entityId) { // GH-90000
            return Promise.of(Optional.empty()); // GH-90000
        }

        @Override
        public Promise<EntityInterface> update( // GH-90000
                String tenantId,
                String collectionName,
                UUID entityId,
                Map<String, Object> updates) {
            return Promise.ofException(new UnsupportedOperationException("not used in builder test [GH-90000]"));
        }

        @Override
        public Promise<Void> delete(String tenantId, String collectionName, UUID entityId) { // GH-90000
            return Promise.complete(); // GH-90000
        }

        @Override
        public Promise<List<EntityInterface>> query( // GH-90000
                String tenantId,
                String collectionName,
                QuerySpecInterface query) {
            return Promise.of(List.of()); // GH-90000
        }

        @Override
        public Promise<Long> count(String tenantId, String collectionName, QuerySpecInterface query) { // GH-90000
            return Promise.of(0L); // GH-90000
        }
    }
}
