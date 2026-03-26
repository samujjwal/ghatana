package com.ghatana.datacloud.embedded;

import com.ghatana.datacloud.deployment.EmbeddedConfig;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for EmbeddableDataCloud.
 *
 * <p><b>Test Coverage</b><br>
 * <ul>
 *   <li>Factory methods (forTesting, forProduction, forEdge)</li>
 *   <li>Builder API</li>
 *   <li>Lifecycle (start, stop, isRunning)</li>
 *   <li>Service access (eventCloud, entityService, brain)</li>
 *   <li>Error handling</li>
 * </ul>
 *
 * @doc.type test
 * @doc.purpose Verify embedded Data-Cloud functionality
 * @doc.layer core
 */
@DisplayName("EmbeddableDataCloud Tests")
class EmbeddableDataCloudTest extends EventloopTestBase {

    @Nested
    @DisplayName("Factory Methods")
    class FactoryMethodsTests {

        @Test
        @DisplayName("Should create for testing with in-memory storage")
        void shouldCreateForTesting() {
            // WHEN
            EmbeddableDataCloud dataCloud = EmbeddableDataCloud.inMemory();

            // THEN
            assertThat(dataCloud).isNotNull();
            assertThat(dataCloud.isRunning()).isFalse();
        }

        @Test
        @DisplayName("Should create with custom config using builder")
        void shouldCreateWithCustomConfig() {
            // GIVEN
            EmbeddedConfig config = EmbeddedConfig.builder(EmbeddedConfig.EmbeddedStorageType.IN_MEMORY)
                .enableAI(false)
                .maxCacheEntries(5000)
                .enableMetrics(true)
                .build();

            // WHEN
            EmbeddableDataCloud dataCloud = EmbeddableDataCloud.create()
                    .withStorage(config.storageType())
                    .withAI(config.enableAI())
                    .build();

            // THEN
            assertThat(dataCloud).isNotNull();
        }
    }

    @Nested
    @DisplayName("Builder API")
    class BuilderTests {

        @Test
        @DisplayName("Should build with memory storage")
        void shouldBuildWithMemoryStorage() {
            // WHEN
            EmbeddableDataCloud dataCloud = EmbeddableDataCloud.create()
                .withStorage(EmbeddedConfig.EmbeddedStorageType.IN_MEMORY)
                .withAI(false)
                .build();

            // THEN
            assertThat(dataCloud).isNotNull();
        }

        @Test
        @DisplayName("Should build with RocksDB storage")
        void shouldBuildWithRocksDBStorage() {
            // WHEN
            EmbeddableDataCloud dataCloud = EmbeddableDataCloud.create()
                .withStorage(EmbeddedConfig.EmbeddedStorageType.ROCKS_DB)
                .withDataDirectory("/tmp/rocks-test")
                .withAI(true)
                .build();

            // THEN
            assertThat(dataCloud).isNotNull();
        }

        @Test
        @DisplayName("Should build with SQLite storage")
        void shouldBuildWithSQLiteStorage() {
            // WHEN
            EmbeddableDataCloud dataCloud = EmbeddableDataCloud.create()
                .withStorage(EmbeddedConfig.EmbeddedStorageType.SQLITE)
                .withDataDirectory("/tmp/sqlite-test")
                .build();

            // THEN
            assertThat(dataCloud).isNotNull();
        }
    }

    @Nested
    @DisplayName("Lifecycle")
    class LifecycleTests {

        @Test
        @DisplayName("Should start and stop successfully")
        void shouldStartAndStop() {
            // GIVEN
            EmbeddableDataCloud dataCloud = EmbeddableDataCloud.inMemory();

            // WHEN - Start
            runPromise(() -> dataCloud.start());

            // THEN
            assertThat(dataCloud.isRunning()).isTrue();

            // WHEN - Stop
            runPromise(() -> dataCloud.stop());

            // THEN
            assertThat(dataCloud.isRunning()).isFalse();
        }

        @Test
        @DisplayName("Should be idempotent on start")
        void shouldBeIdempotentOnStart() {
            // GIVEN
            EmbeddableDataCloud dataCloud = EmbeddableDataCloud.inMemory();
            runPromise(() -> dataCloud.start());

            // WHEN - Start again
            runPromise(() -> dataCloud.start());

            // THEN - No exception
            assertThat(dataCloud.isRunning()).isTrue();
        }

        @Test
        @DisplayName("Should be idempotent on stop")
        void shouldBeIdempotentOnStop() {
            // GIVEN
            EmbeddableDataCloud dataCloud = EmbeddableDataCloud.inMemory();
            runPromise(() -> dataCloud.start());
            runPromise(() -> dataCloud.stop());

            // WHEN - Stop again
            runPromise(() -> dataCloud.stop());

            // THEN - No exception
            assertThat(dataCloud.isRunning()).isFalse();
        }
    }

    @Nested
    @DisplayName("Service Access")
    class ServiceAccessTests {

        @Test
        @DisplayName("Should provide Store access when running")
        void shouldProvideStoreAccess() {
            // GIVEN
            EmbeddableDataCloud dataCloud = EmbeddableDataCloud.inMemory();
            runPromise(() -> dataCloud.start());

            // WHEN
            EmbeddableDataCloud.EmbeddedStore store = dataCloud.store();

            // THEN
            assertThat(store).isNotNull();
        }

        @Test
        @DisplayName("Should provide Query access when running")
        void shouldProvideQueryAccess() {
            // GIVEN
            EmbeddableDataCloud dataCloud = EmbeddableDataCloud.inMemory();
            runPromise(() -> dataCloud.start());

            // WHEN
            EmbeddableDataCloud.EmbeddedQuery query = dataCloud.query();

            // THEN
            assertThat(query).isNotNull();
        }

        @Test
        @DisplayName("Should provide EventStream access when running")
        void shouldProvideEventStreamAccess() {
            // GIVEN
            EmbeddableDataCloud dataCloud = EmbeddableDataCloud.create()
                .withStorage(EmbeddedConfig.EmbeddedStorageType.IN_MEMORY)
                .withAI(true)
                .build();
            runPromise(() -> dataCloud.start());

            // WHEN
            EmbeddableDataCloud.EmbeddedEventStream events = dataCloud.events();

            // THEN
            assertThat(events).isNotNull();
        }

        @Test
        @DisplayName("Should provide Coordinator access")
        void shouldProvideCoordinatorAccess() {
            // GIVEN
            EmbeddableDataCloud dataCloud = EmbeddableDataCloud.inMemory();
            runPromise(() -> dataCloud.start());

            // WHEN
            var coordinator = dataCloud.coordinator();

            // THEN
            assertThat(coordinator).isNotNull();
        }

        @Test
        @DisplayName("Should provide config access")
        void shouldProvideConfigAccess() {
            // GIVEN
            EmbeddableDataCloud dataCloud = EmbeddableDataCloud.inMemory();

            // WHEN
            var config = dataCloud.config();

            // THEN
            assertThat(config).isNotNull();
        }
    }

    @Nested
    @DisplayName("Configuration")
    class ConfigurationTests {

        @Test
        @DisplayName("Should validate storage type and data directory")
        void shouldValidateStorageTypeAndDataDirectory() {
            // WHEN / THEN - RocksDB requires data directory
            assertThatThrownBy(() -> 
                EmbeddedConfig.builder(EmbeddedConfig.EmbeddedStorageType.ROCKS_DB)
                    .dataDirectory((String) null)
                    .build()
            ).hasMessageContaining("dataDirectory");

            // WHEN / THEN - SQLite requires data directory
            assertThatThrownBy(() ->
                EmbeddedConfig.builder(EmbeddedConfig.EmbeddedStorageType.SQLITE)
                    .dataDirectory((String) null)
                    .build()
            ).hasMessageContaining("dataDirectory");
        }

        @Test
        @DisplayName("Should allow in-memory without data directory")
        void shouldAllowInMemoryWithoutDataDirectory() {
            // WHEN
            EmbeddedConfig config = EmbeddedConfig.builder(EmbeddedConfig.EmbeddedStorageType.IN_MEMORY)
                    .dataDirectory((String) null)
                    .build();

            // THEN
            assertThat(config).isNotNull();
            assertThat(config.storageType()).isEqualTo(EmbeddedConfig.EmbeddedStorageType.IN_MEMORY);
        }
    }
}