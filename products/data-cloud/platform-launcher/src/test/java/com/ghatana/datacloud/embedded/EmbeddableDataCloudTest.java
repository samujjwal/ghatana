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
 *   <li>Factory methods (forTesting, forProduction, forEdge)</li> // GH-90000
 *   <li>Builder API</li>
 *   <li>Lifecycle (start, stop, isRunning)</li> // GH-90000
 *   <li>Service access (eventCloud, entityService, brain)</li> // GH-90000
 *   <li>Error handling</li>
 * </ul>
 *
 * @doc.type test
 * @doc.purpose Verify embedded Data-Cloud functionality
 * @doc.layer core
 */
@DisplayName("EmbeddableDataCloud Tests [GH-90000]")
class EmbeddableDataCloudTest extends EventloopTestBase {

    @Nested
    @DisplayName("Factory Methods [GH-90000]")
    class FactoryMethodsTests {

        @Test
        @DisplayName("Should create for testing with in-memory storage [GH-90000]")
        void shouldCreateForTesting() { // GH-90000
            // WHEN
            EmbeddableDataCloud dataCloud = EmbeddableDataCloud.inMemory(); // GH-90000

            // THEN
            assertThat(dataCloud).isNotNull(); // GH-90000
            assertThat(dataCloud.isRunning()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("Should create with custom config using builder [GH-90000]")
        void shouldCreateWithCustomConfig() { // GH-90000
            // GIVEN
            EmbeddedConfig config = EmbeddedConfig.builder(EmbeddedConfig.EmbeddedStorageType.IN_MEMORY) // GH-90000
                .enableAI(false) // GH-90000
                .maxCacheEntries(5000) // GH-90000
                .enableMetrics(true) // GH-90000
                .build(); // GH-90000

            // WHEN
            EmbeddableDataCloud dataCloud = EmbeddableDataCloud.create() // GH-90000
                    .withStorage(config.storageType()) // GH-90000
                    .withAI(config.enableAI()) // GH-90000
                    .build(); // GH-90000

            // THEN
            assertThat(dataCloud).isNotNull(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Builder API [GH-90000]")
    class BuilderTests {

        @Test
        @DisplayName("Should build with memory storage [GH-90000]")
        void shouldBuildWithMemoryStorage() { // GH-90000
            // WHEN
            EmbeddableDataCloud dataCloud = EmbeddableDataCloud.create() // GH-90000
                .withStorage(EmbeddedConfig.EmbeddedStorageType.IN_MEMORY) // GH-90000
                .withAI(false) // GH-90000
                .build(); // GH-90000

            // THEN
            assertThat(dataCloud).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("Should build with RocksDB storage [GH-90000]")
        void shouldBuildWithRocksDBStorage() { // GH-90000
            // WHEN
            EmbeddableDataCloud dataCloud = EmbeddableDataCloud.create() // GH-90000
                .withStorage(EmbeddedConfig.EmbeddedStorageType.ROCKS_DB) // GH-90000
                .withDataDirectory("/tmp/rocks-test [GH-90000]")
                .withAI(true) // GH-90000
                .build(); // GH-90000

            // THEN
            assertThat(dataCloud).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("Should build with SQLite storage [GH-90000]")
        void shouldBuildWithSQLiteStorage() { // GH-90000
            // WHEN
            EmbeddableDataCloud dataCloud = EmbeddableDataCloud.create() // GH-90000
                .withStorage(EmbeddedConfig.EmbeddedStorageType.SQLITE) // GH-90000
                .withDataDirectory("/tmp/sqlite-test [GH-90000]")
                .build(); // GH-90000

            // THEN
            assertThat(dataCloud).isNotNull(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Lifecycle [GH-90000]")
    class LifecycleTests {

        @Test
        @DisplayName("Should start and stop successfully [GH-90000]")
        void shouldStartAndStop() { // GH-90000
            // GIVEN
            EmbeddableDataCloud dataCloud = EmbeddableDataCloud.inMemory(); // GH-90000

            // WHEN - Start
            runPromise(() -> dataCloud.start()); // GH-90000

            // THEN
            assertThat(dataCloud.isRunning()).isTrue(); // GH-90000

            // WHEN - Stop
            runPromise(() -> dataCloud.stop()); // GH-90000

            // THEN
            assertThat(dataCloud.isRunning()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("Should be idempotent on start [GH-90000]")
        void shouldBeIdempotentOnStart() { // GH-90000
            // GIVEN
            EmbeddableDataCloud dataCloud = EmbeddableDataCloud.inMemory(); // GH-90000
            runPromise(() -> dataCloud.start()); // GH-90000

            // WHEN - Start again
            runPromise(() -> dataCloud.start()); // GH-90000

            // THEN - No exception
            assertThat(dataCloud.isRunning()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("Should be idempotent on stop [GH-90000]")
        void shouldBeIdempotentOnStop() { // GH-90000
            // GIVEN
            EmbeddableDataCloud dataCloud = EmbeddableDataCloud.inMemory(); // GH-90000
            runPromise(() -> dataCloud.start()); // GH-90000
            runPromise(() -> dataCloud.stop()); // GH-90000

            // WHEN - Stop again
            runPromise(() -> dataCloud.stop()); // GH-90000

            // THEN - No exception
            assertThat(dataCloud.isRunning()).isFalse(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Service Access [GH-90000]")
    class ServiceAccessTests {

        @Test
        @DisplayName("Should provide Store access when running [GH-90000]")
        void shouldProvideStoreAccess() { // GH-90000
            // GIVEN
            EmbeddableDataCloud dataCloud = EmbeddableDataCloud.inMemory(); // GH-90000
            runPromise(() -> dataCloud.start()); // GH-90000

            // WHEN
            EmbeddableDataCloud.EmbeddedStore store = dataCloud.store(); // GH-90000

            // THEN
            assertThat(store).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("Should provide Query access when running [GH-90000]")
        void shouldProvideQueryAccess() { // GH-90000
            // GIVEN
            EmbeddableDataCloud dataCloud = EmbeddableDataCloud.inMemory(); // GH-90000
            runPromise(() -> dataCloud.start()); // GH-90000

            // WHEN
            EmbeddableDataCloud.EmbeddedQuery query = dataCloud.query(); // GH-90000

            // THEN
            assertThat(query).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("Should provide EventStream access when running [GH-90000]")
        void shouldProvideEventStreamAccess() { // GH-90000
            // GIVEN
            EmbeddableDataCloud dataCloud = EmbeddableDataCloud.create() // GH-90000
                .withStorage(EmbeddedConfig.EmbeddedStorageType.IN_MEMORY) // GH-90000
                .withAI(true) // GH-90000
                .build(); // GH-90000
            runPromise(() -> dataCloud.start()); // GH-90000

            // WHEN
            EmbeddableDataCloud.EmbeddedEventStream events = dataCloud.events(); // GH-90000

            // THEN
            assertThat(events).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("Should provide Coordinator access [GH-90000]")
        void shouldProvideCoordinatorAccess() { // GH-90000
            // GIVEN
            EmbeddableDataCloud dataCloud = EmbeddableDataCloud.inMemory(); // GH-90000
            runPromise(() -> dataCloud.start()); // GH-90000

            // WHEN
            var coordinator = dataCloud.coordinator(); // GH-90000

            // THEN
            assertThat(coordinator).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("Should provide config access [GH-90000]")
        void shouldProvideConfigAccess() { // GH-90000
            // GIVEN
            EmbeddableDataCloud dataCloud = EmbeddableDataCloud.inMemory(); // GH-90000

            // WHEN
            var config = dataCloud.config(); // GH-90000

            // THEN
            assertThat(config).isNotNull(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Configuration [GH-90000]")
    class ConfigurationTests {

        @Test
        @DisplayName("Should validate storage type and data directory [GH-90000]")
        void shouldValidateStorageTypeAndDataDirectory() { // GH-90000
            // WHEN / THEN - RocksDB requires data directory
            assertThatThrownBy(() -> // GH-90000
                EmbeddedConfig.builder(EmbeddedConfig.EmbeddedStorageType.ROCKS_DB) // GH-90000
                    .dataDirectory((String) null) // GH-90000
                    .build() // GH-90000
            ).hasMessageContaining("dataDirectory [GH-90000]");

            // WHEN / THEN - SQLite requires data directory
            assertThatThrownBy(() -> // GH-90000
                EmbeddedConfig.builder(EmbeddedConfig.EmbeddedStorageType.SQLITE) // GH-90000
                    .dataDirectory((String) null) // GH-90000
                    .build() // GH-90000
            ).hasMessageContaining("dataDirectory [GH-90000]");
        }

        @Test
        @DisplayName("Should allow in-memory without data directory [GH-90000]")
        void shouldAllowInMemoryWithoutDataDirectory() { // GH-90000
            // WHEN
            EmbeddedConfig config = EmbeddedConfig.builder(EmbeddedConfig.EmbeddedStorageType.IN_MEMORY) // GH-90000
                    .dataDirectory((String) null) // GH-90000
                    .build(); // GH-90000

            // THEN
            assertThat(config).isNotNull(); // GH-90000
            assertThat(config.storageType()).isEqualTo(EmbeddedConfig.EmbeddedStorageType.IN_MEMORY); // GH-90000
        }
    }
}
