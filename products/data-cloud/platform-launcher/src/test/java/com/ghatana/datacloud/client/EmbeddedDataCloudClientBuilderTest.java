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
    @DisplayName("memory mode builds without reflective storage plugin loading")
    void memoryModeBuilds() {
        EmbeddedDataCloudClient client = EmbeddedDataCloudClient.builder()
                .withMemoryStorage()
                .build();

        assertThat(client).isNotNull();
        client.close();
    }

    @Test
    @DisplayName("postgres mode fails fast without an explicit storage adapter")
    void postgresModeRequiresExplicitAdapter() {
        assertThatThrownBy(() -> EmbeddedDataCloudClient.builder()
                .withPostgreSQLStorage(Map.of("jdbcUrl", "jdbc:postgresql://localhost/test"))
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("requires an explicit DataStorageOperations implementation");
    }

    @Test
    @DisplayName("redis mode fails fast without an explicit storage adapter")
    void redisModeRequiresExplicitAdapter() {
        assertThatThrownBy(() -> EmbeddedDataCloudClient.builder()
                .withRedisStorage(Map.of("host", "localhost"))
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("requires an explicit DataStorageOperations implementation");
    }

    @Test
    @DisplayName("non-memory modes build when a real storage adapter is supplied explicitly")
    void explicitStorageAdapterEnablesNonMemoryMode() {
        EmbeddedDataCloudClient client = EmbeddedDataCloudClient.builder()
                .withPostgreSQLStorage(Map.of("jdbcUrl", "jdbc:postgresql://localhost/test"))
                .withDataStoragePlugin(new NoopDataStorageOperations())
                .build();

        assertThat(client).isNotNull();
        client.close();
    }

    private static final class NoopDataStorageOperations implements DataStorageOperations {
        @Override
        public Promise<EntityInterface> create(String tenantId, String collectionName, Map<String, Object> data) {
            return Promise.ofException(new UnsupportedOperationException("not used in builder test"));
        }

        @Override
        public Promise<Optional<EntityInterface>> read(String tenantId, String collectionName, UUID entityId) {
            return Promise.of(Optional.empty());
        }

        @Override
        public Promise<EntityInterface> update(
                String tenantId,
                String collectionName,
                UUID entityId,
                Map<String, Object> updates) {
            return Promise.ofException(new UnsupportedOperationException("not used in builder test"));
        }

        @Override
        public Promise<Void> delete(String tenantId, String collectionName, UUID entityId) {
            return Promise.complete();
        }

        @Override
        public Promise<List<EntityInterface>> query(
                String tenantId,
                String collectionName,
                QuerySpecInterface query) {
            return Promise.of(List.of());
        }

        @Override
        public Promise<Long> count(String tenantId, String collectionName, QuerySpecInterface query) {
            return Promise.of(0L);
        }
    }
}
