package com.ghatana.datacloud.plugins;

import com.ghatana.platform.plugin.*;
import com.ghatana.platform.health.HealthStatus;
import com.ghatana.platform.plugin.impl.DefaultPluginContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive tests for the unified Plugin SPI after migration from
 * fragmented data-cloud/event-cloud Plugin interfaces to the canonical
 * platform Plugin SPI.
 *
 * <p>Validates:
 * <ul>
 *   <li>Platform Plugin lifecycle (init → start → stop → shutdown)</li> // GH-90000
 *   <li>PluginMetadata builder and record semantics</li>
 *   <li>PluginState transitions and helper methods</li>
 *   <li>HealthStatus factory methods</li>
 *   <li>PluginRegistry CRUD, discovery, and aggregation</li>
 *   <li>PluginProvider ServiceLoader SPI contract</li>
 *   <li>PluginContext default method contracts</li>
 *   <li>Data-cloud sub-interfaces extend platform Plugin correctly</li>
 * </ul>
 *
 * @doc.type test
 * @doc.purpose Verify unified Plugin SPI after migration
 * @doc.layer test
 * @doc.pattern Test
 */
class UnifiedPluginSpiTest extends EventloopTestBase {

    private PluginRegistry registry;
    private PluginContext context;

    @BeforeEach
    void setUp() { // GH-90000
        registry = new PluginRegistry(); // GH-90000
        context = new DefaultPluginContext(registry, Map.of()); // GH-90000
    }

    // ========================================================================
    // Plugin Lifecycle Tests
    // ========================================================================

    @Nested
    @DisplayName("Plugin Lifecycle [GH-90000]")
    class PluginLifecycleTests {

        @Test
        @DisplayName("should transition through full lifecycle: UNLOADED → INITIALIZED → STARTED → STOPPED [GH-90000]")
        void fullLifecycle() { // GH-90000
            TestPlugin plugin = new TestPlugin("lifecycle-test [GH-90000]");

            assertThat(plugin.getState()).isEqualTo(PluginState.UNLOADED); // GH-90000

            runPromise(() -> plugin.initialize(context)); // GH-90000
            assertThat(plugin.getState()).isEqualTo(PluginState.INITIALIZED); // GH-90000

            runPromise(() -> plugin.start()); // GH-90000
            assertThat(plugin.getState()).isEqualTo(PluginState.RUNNING); // GH-90000

            runPromise(() -> plugin.stop()); // GH-90000
            assertThat(plugin.getState()).isEqualTo(PluginState.STOPPED); // GH-90000
        }

        @Test
        @DisplayName("shutdown() should default to stop() [GH-90000]")
        void shutdownDelegatesToStop() { // GH-90000
            TestPlugin plugin = new TestPlugin("shutdown-test [GH-90000]");
            runPromise(() -> plugin.initialize(context)); // GH-90000
            runPromise(() -> plugin.start()); // GH-90000

            runPromise(() -> plugin.shutdown()); // GH-90000
            assertThat(plugin.getState()).isEqualTo(PluginState.STOPPED); // GH-90000
        }

        @Test
        @DisplayName("healthCheck() default returns OK [GH-90000]")
        void defaultHealthCheckReturnsOk() { // GH-90000
            DefaultHealthPlugin plugin = new DefaultHealthPlugin("health-default [GH-90000]");
            HealthStatus status = runPromise(() -> plugin.healthCheck()); // GH-90000

            assertThat(status.isHealthy()).isTrue(); // GH-90000
            assertThat(status.getMessage()).isEqualTo("OK [GH-90000]");
        }

        @Test
        @DisplayName("getCapabilities() default returns empty set [GH-90000]")
        void defaultCapabilitiesEmpty() { // GH-90000
            TestPlugin plugin = new TestPlugin("caps-test [GH-90000]");
            assertThat(plugin.getCapabilities()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("getCapability() returns empty for unknown type [GH-90000]")
        void getCapabilityReturnsEmpty() { // GH-90000
            TestPlugin plugin = new TestPlugin("cap-test [GH-90000]");
            assertThat(plugin.getCapability(PluginCapability.class)).isEmpty(); // GH-90000
        }
    }

    // ========================================================================
    // PluginMetadata Tests
    // ========================================================================

    @Nested
    @DisplayName("PluginMetadata [GH-90000]")
    class PluginMetadataTests {

        @Test
        @DisplayName("builder should create metadata with all fields [GH-90000]")
        void builderCreatesFullMetadata() { // GH-90000
            PluginMetadata metadata = PluginMetadata.builder() // GH-90000
                    .id("test-plugin [GH-90000]")
                    .name("Test Plugin [GH-90000]")
                    .version("2.1.0 [GH-90000]")
                    .description("A test plugin [GH-90000]")
                    .type(PluginType.STORAGE) // GH-90000
                    .author("Test Author [GH-90000]")
                    .license("Apache-2.0 [GH-90000]")
                    .tags(Set.of("test", "storage")) // GH-90000
                    .capabilities(Set.of("read", "write")) // GH-90000
                    .properties(Map.of("key", "value")) // GH-90000
                    .build(); // GH-90000

            assertThat(metadata.id()).isEqualTo("test-plugin [GH-90000]");
            assertThat(metadata.name()).isEqualTo("Test Plugin [GH-90000]");
            assertThat(metadata.version()).isEqualTo("2.1.0 [GH-90000]");
            assertThat(metadata.description()).isEqualTo("A test plugin [GH-90000]");
            assertThat(metadata.type()).isEqualTo(PluginType.STORAGE); // GH-90000
            assertThat(metadata.author()).isEqualTo("Test Author [GH-90000]");
            assertThat(metadata.vendor()).isEqualTo("Test Author [GH-90000]"); // alias
            assertThat(metadata.license()).isEqualTo("Apache-2.0 [GH-90000]");
            assertThat(metadata.tags()).containsExactlyInAnyOrder("test", "storage"); // GH-90000
            assertThat(metadata.capabilities()).containsExactlyInAnyOrder("read", "write"); // GH-90000
            assertThat(metadata.properties()).containsEntry("key", "value"); // GH-90000
        }

        @Test
        @DisplayName("builder should apply defaults for optional fields [GH-90000]")
        void builderAppliesDefaults() { // GH-90000
            PluginMetadata metadata = PluginMetadata.builder() // GH-90000
                    .id("minimal [GH-90000]")
                    .build(); // GH-90000

            assertThat(metadata.id()).isEqualTo("minimal [GH-90000]");
            assertThat(metadata.name()).isEqualTo("minimal [GH-90000]"); // defaults to id
            assertThat(metadata.version()).isEqualTo("1.0.0 [GH-90000]");
            assertThat(metadata.description()).isEmpty(); // GH-90000
            assertThat(metadata.type()).isEqualTo(PluginType.CUSTOM); // GH-90000
            assertThat(metadata.author()).isEqualTo("Ghatana [GH-90000]");
            assertThat(metadata.license()).isEqualTo("Proprietary [GH-90000]");
            assertThat(metadata.tags()).isEmpty(); // GH-90000
            assertThat(metadata.capabilities()).isEmpty(); // GH-90000
            assertThat(metadata.properties()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("hasCapability returns true for declared capabilities [GH-90000]")
        void hasCapabilityCheck() { // GH-90000
            PluginMetadata metadata = PluginMetadata.builder() // GH-90000
                    .id("cap-test [GH-90000]")
                    .capabilities(Set.of("streaming", "batch")) // GH-90000
                    .build(); // GH-90000

            assertThat(metadata.hasCapability("streaming [GH-90000]")).isTrue();
            assertThat(metadata.hasCapability("batch [GH-90000]")).isTrue();
            assertThat(metadata.hasCapability("unknown [GH-90000]")).isFalse();
        }

        @Test
        @DisplayName("vendor() is alias for author() [GH-90000]")
        void vendorAlias() { // GH-90000
            PluginMetadata metadata = PluginMetadata.builder() // GH-90000
                    .id("vendor-test [GH-90000]")
                    .vendor("MyCompany [GH-90000]")
                    .build(); // GH-90000

            assertThat(metadata.author()).isEqualTo("MyCompany [GH-90000]");
            assertThat(metadata.vendor()).isEqualTo("MyCompany [GH-90000]");
        }

        @Test
        @DisplayName("builder requires non-blank id [GH-90000]")
        void builderRequiresId() { // GH-90000
            assertThatThrownBy(() -> PluginMetadata.builder().build()) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("id [GH-90000]");
        }
    }

    // ========================================================================
    // PluginState Tests
    // ========================================================================

    @Nested
    @DisplayName("PluginState [GH-90000]")
    class PluginStateTests {

        @Test
        @DisplayName("isActive() returns true for active states [GH-90000]")
        void isActive() { // GH-90000
            assertThat(PluginState.INITIALIZED.isActive()).isTrue(); // GH-90000
            assertThat(PluginState.STARTING.isActive()).isTrue(); // GH-90000
            assertThat(PluginState.STARTED.isActive()).isTrue(); // GH-90000
            assertThat(PluginState.RUNNING.isActive()).isTrue(); // GH-90000

            assertThat(PluginState.UNLOADED.isActive()).isFalse(); // GH-90000
            assertThat(PluginState.STOPPED.isActive()).isFalse(); // GH-90000
            assertThat(PluginState.ERROR.isActive()).isFalse(); // GH-90000
            assertThat(PluginState.FAILED.isActive()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("isTerminal() returns true for terminal states [GH-90000]")
        void isTerminal() { // GH-90000
            assertThat(PluginState.STOPPED.isTerminal()).isTrue(); // GH-90000
            assertThat(PluginState.ERROR.isTerminal()).isTrue(); // GH-90000
            assertThat(PluginState.FAILED.isTerminal()).isTrue(); // GH-90000

            assertThat(PluginState.RUNNING.isTerminal()).isFalse(); // GH-90000
            assertThat(PluginState.UNLOADED.isTerminal()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("isError() returns true for error states [GH-90000]")
        void isError() { // GH-90000
            assertThat(PluginState.ERROR.isError()).isTrue(); // GH-90000
            assertThat(PluginState.FAILED.isError()).isTrue(); // GH-90000

            assertThat(PluginState.STOPPED.isError()).isFalse(); // GH-90000
            assertThat(PluginState.RUNNING.isError()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("all expected states exist in the enum [GH-90000]")
        void allStatesExist() { // GH-90000
            Set<String> expected = Set.of( // GH-90000
                    "UNLOADED", "DISCOVERED", "INITIALIZED", "STARTING",
                    "STARTED", "RUNNING", "STOPPING", "STOPPED",
                    "ERROR", "FAILED"
            );
            Set<String> actual = new HashSet<>(); // GH-90000
            for (PluginState s : PluginState.values()) { // GH-90000
                actual.add(s.name()); // GH-90000
            }
            assertThat(actual).containsAll(expected); // GH-90000
        }
    }

    // ========================================================================
    // HealthStatus Tests
    // ========================================================================

    @Nested
    @DisplayName("HealthStatus [GH-90000]")
    class HealthStatusTests {

        @Test
        @DisplayName("ok() creates healthy status with default message [GH-90000]")
        void okDefault() { // GH-90000
            HealthStatus status = HealthStatus.ok(); // GH-90000
            assertThat(status.isHealthy()).isTrue(); // GH-90000
            assertThat(status.getMessage()).isEqualTo("OK [GH-90000]");
            assertThat(status.getDetails()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("ok(message) creates healthy status with custom message [GH-90000]")
        void okWithMessage() { // GH-90000
            HealthStatus status = HealthStatus.ok("All good [GH-90000]");
            assertThat(status.isHealthy()).isTrue(); // GH-90000
            assertThat(status.getMessage()).isEqualTo("All good [GH-90000]");
        }

        @Test
        @DisplayName("ok(message, details) creates healthy status with details [GH-90000]")
        void okWithDetails() { // GH-90000
            HealthStatus status = HealthStatus.ok("Healthy", Map.of("latency", 5)); // GH-90000
            assertThat(status.isHealthy()).isTrue(); // GH-90000
            assertThat(status.getDetails()).containsEntry("latency", 5); // GH-90000
        }

        @Test
        @DisplayName("error(message) creates unhealthy status [GH-90000]")
        void errorWithMessage() { // GH-90000
            HealthStatus status = HealthStatus.error("Connection refused [GH-90000]");
            assertThat(status.isHealthy()).isFalse(); // GH-90000
            assertThat(status.getMessage()).isEqualTo("Connection refused [GH-90000]");
        }

        @Test
        @DisplayName("error(message, throwable) includes exception details [GH-90000]")
        void errorWithThrowable() { // GH-90000
            Exception ex = new RuntimeException("boom [GH-90000]");
            HealthStatus status = HealthStatus.error("Failed", ex); // GH-90000
            assertThat(status.isHealthy()).isFalse(); // GH-90000
            assertThat(status.getDetails()).containsEntry("error", "RuntimeException"); // GH-90000
            assertThat(status.getDetails()).containsEntry("errorMessage", "boom"); // GH-90000
        }

        @Test
        @DisplayName("unhealthy(message) creates unhealthy status [GH-90000]")
        void unhealthyWithMessage() { // GH-90000
            HealthStatus status = HealthStatus.unhealthy("Degraded [GH-90000]");
            assertThat(status.isHealthy()).isFalse(); // GH-90000
            assertThat(status.getMessage()).isEqualTo("Degraded [GH-90000]");
        }
    }

    // ========================================================================
    // PluginRegistry Tests
    // ========================================================================

    @Nested
    @DisplayName("PluginRegistry [GH-90000]")
    class PluginRegistryTests {

        @Test
        @DisplayName("register and retrieve plugin by id [GH-90000]")
        void registerAndGet() { // GH-90000
            TestPlugin plugin = new TestPlugin("my-plugin [GH-90000]");
            registry.register(plugin); // GH-90000

            Optional<Plugin> found = registry.getPlugin("my-plugin [GH-90000]");
            assertThat(found).isPresent(); // GH-90000
            assertThat(found.get()).isSameAs(plugin); // GH-90000
        }

        @Test
        @DisplayName("getPlugin returns empty for unknown id [GH-90000]")
        void getPluginNotFound() { // GH-90000
            assertThat(registry.getPlugin("nonexistent [GH-90000]")).isEmpty();
        }

        @Test
        @DisplayName("register prevents duplicates by throwing [GH-90000]")
        void preventDuplicates() { // GH-90000
            TestPlugin p1 = new TestPlugin("dup-plugin [GH-90000]");
            TestPlugin p2 = new TestPlugin("dup-plugin [GH-90000]");

            registry.register(p1); // GH-90000
            assertThatThrownBy(() -> registry.register(p2)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("dup-plugin [GH-90000]");

            // Original stays registered
            assertThat(registry.getPlugin("dup-plugin [GH-90000]").orElse(null)).isSameAs(p1);
        }

        @Test
        @DisplayName("unregister removes plugin and returns it [GH-90000]")
        void unregisterPlugin() { // GH-90000
            TestPlugin plugin = new TestPlugin("removable [GH-90000]");
            registry.register(plugin); // GH-90000

            Optional<Plugin> removed = registry.unregister("removable [GH-90000]");
            assertThat(removed).isPresent(); // GH-90000
            assertThat(removed.get()).isSameAs(plugin); // GH-90000
            assertThat(registry.getPlugin("removable [GH-90000]")).isEmpty();
        }

        @Test
        @DisplayName("unregister returns empty for unknown id [GH-90000]")
        void unregisterNotFound() { // GH-90000
            assertThat(registry.unregister("ghost [GH-90000]")).isEmpty();
        }

        @Test
        @DisplayName("size tracks registered plugins [GH-90000]")
        void sizeTracking() { // GH-90000
            assertThat(registry.size()).isEqualTo(0); // GH-90000

            registry.register(new TestPlugin("a [GH-90000]"));
            registry.register(new TestPlugin("b [GH-90000]"));
            assertThat(registry.size()).isEqualTo(2); // GH-90000

            registry.unregister("a [GH-90000]");
            assertThat(registry.size()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("getAllPlugins returns all registered plugins [GH-90000]")
        void getAllPlugins() { // GH-90000
            registry.register(new TestPlugin("x [GH-90000]"));
            registry.register(new TestPlugin("y [GH-90000]"));

            assertThat(registry.getAllPlugins()).hasSize(2); // GH-90000
        }

        @Test
        @DisplayName("isRegistered returns correct status [GH-90000]")
        void isRegistered() { // GH-90000
            TestPlugin plugin = new TestPlugin("check-me [GH-90000]");
            assertThat(registry.isRegistered("check-me [GH-90000]")).isFalse();

            registry.register(plugin); // GH-90000
            assertThat(registry.isRegistered("check-me [GH-90000]")).isTrue();
        }

        @Test
        @DisplayName("findByType returns matching plugins [GH-90000]")
        void findByType() { // GH-90000
            registry.register(new TestPlugin("storage-1", PluginType.STORAGE)); // GH-90000
            registry.register(new TestPlugin("stream-1", PluginType.STREAMING)); // GH-90000
            registry.register(new TestPlugin("storage-2", PluginType.STORAGE)); // GH-90000

            List<Plugin> storagePlugins = registry.findByType(PluginType.STORAGE); // GH-90000
            assertThat(storagePlugins).hasSize(2); // GH-90000
        }

        @Test
        @DisplayName("findByStringCapability returns matching plugins [GH-90000]")
        void findByStringCapability() { // GH-90000
            registry.register(new TestPlugin("p1", Set.of("streaming", "batch"))); // GH-90000
            registry.register(new TestPlugin("p2", Set.of("batch [GH-90000]")));
            registry.register(new TestPlugin("p3", Set.of("realtime [GH-90000]")));

            List<Plugin> batchPlugins = registry.findByStringCapability("batch [GH-90000]");
            assertThat(batchPlugins).hasSize(2); // GH-90000

            List<Plugin> realtimePlugins = registry.findByStringCapability("realtime [GH-90000]");
            assertThat(realtimePlugins).hasSize(1); // GH-90000
        }

        @Test
        @DisplayName("find with predicate returns matching plugins [GH-90000]")
        void findWithPredicate() { // GH-90000
            registry.register(new TestPlugin("a-plugin [GH-90000]"));
            registry.register(new TestPlugin("b-plugin [GH-90000]"));
            registry.register(new TestPlugin("a-other [GH-90000]"));

            List<Plugin> result = registry.find(p -> p.metadata().id().startsWith("a- [GH-90000]"));
            assertThat(result).hasSize(2); // GH-90000
        }

        @Test
        @DisplayName("clear removes all plugins [GH-90000]")
        void clearAll() { // GH-90000
            registry.register(new TestPlugin("one [GH-90000]"));
            registry.register(new TestPlugin("two [GH-90000]"));
            assertThat(registry.size()).isEqualTo(2); // GH-90000

            registry.clear(); // GH-90000
            assertThat(registry.size()).isEqualTo(0); // GH-90000
        }

        @Test
        @DisplayName("aggregateHealth returns aggregate health of all plugins [GH-90000]")
        void aggregateHealth() { // GH-90000
            registry.register(new TestPlugin("healthy-1 [GH-90000]"));
            registry.register(new TestPlugin("healthy-2 [GH-90000]"));

            HealthStatus aggregate = runPromise(() -> registry.aggregateHealth()); // GH-90000
            assertThat(aggregate.isHealthy()).isTrue(); // GH-90000
        }
    }

    // ========================================================================
    // PluginContext Tests
    // ========================================================================

    @Nested
    @DisplayName("PluginContext [GH-90000]")
    class PluginContextTests {

        @Test
        @DisplayName("default getConfig(key, default) returns default [GH-90000]")
        void defaultConfigReturnsDefault() { // GH-90000
            PluginContext ctx = new MinimalContext(); // GH-90000
            assertThat(ctx.getConfig("missing.key", "fallback")).isEqualTo("fallback [GH-90000]");
        }

        @Test
        @DisplayName("default getConfigMap() returns empty [GH-90000]")
        void defaultConfigMapEmpty() { // GH-90000
            PluginContext ctx = new MinimalContext(); // GH-90000
            assertThat(ctx.getConfigMap()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("default getEnvironment() returns production [GH-90000]")
        void defaultEnvironmentProduction() { // GH-90000
            PluginContext ctx = new MinimalContext(); // GH-90000
            assertThat(ctx.getEnvironment()).isEqualTo("production [GH-90000]");
        }

        @Test
        @DisplayName("default getTenantId() returns null [GH-90000]")
        void defaultTenantIdNull() { // GH-90000
            PluginContext ctx = new MinimalContext(); // GH-90000
            assertThat(ctx.getTenantId()).isNull(); // GH-90000
        }

        @Test
        @DisplayName("DefaultPluginContext finds registered plugins [GH-90000]")
        void defaultContextFindsPlugins() { // GH-90000
            TestPlugin p = new TestPlugin("findable [GH-90000]");
            registry.register(p); // GH-90000

            Optional<Plugin> found = context.findPlugin("findable [GH-90000]");
            assertThat(found).isPresent(); // GH-90000
        }
    }

    // ========================================================================
    // PluginProvider Tests
    // ========================================================================

    @Nested
    @DisplayName("PluginProvider [GH-90000]")
    class PluginProviderTests {

        @Test
        @DisplayName("provider creates plugin and returns metadata [GH-90000]")
        void providerContract() { // GH-90000
            PluginProvider provider = new TestPluginProvider(); // GH-90000

            Plugin plugin = provider.createPlugin(); // GH-90000
            assertThat(plugin).isNotNull(); // GH-90000
            assertThat(plugin.metadata().id()).isEqualTo("test-provider-plugin [GH-90000]");

            PluginMetadata meta = provider.getMetadata(); // GH-90000
            assertThat(meta.id()).isEqualTo("test-provider-plugin [GH-90000]");
            assertThat(meta.type()).isEqualTo(PluginType.PROCESSING); // GH-90000
        }

        @Test
        @DisplayName("provider defaults: priority=1000, enabled=true [GH-90000]")
        void providerDefaults() { // GH-90000
            PluginProvider provider = new TestPluginProvider(); // GH-90000
            assertThat(provider.priority()).isEqualTo(1000); // GH-90000
            assertThat(provider.isEnabled()).isTrue(); // GH-90000
        }
    }

    // ========================================================================
    // PluginType Tests
    // ========================================================================

    @Nested
    @DisplayName("PluginType [GH-90000]")
    class PluginTypeTests {

        @Test
        @DisplayName("all required plugin types exist [GH-90000]")
        void allTypesExist() { // GH-90000
            Set<String> expected = Set.of( // GH-90000
                    "STORAGE", "PROCESSING", "STREAMING", "AI", "GOVERNANCE",
                    "INTEGRATION", "OBSERVABILITY", "ENTERPRISE",
                    "ROUTING", "ARCHIVE", "ANALYTICS", "SCHEMA", "AUTH", "CUSTOM"
            );
            Set<String> actual = new HashSet<>(); // GH-90000
            for (PluginType t : PluginType.values()) { // GH-90000
                actual.add(t.name()); // GH-90000
            }
            assertThat(actual).containsAll(expected); // GH-90000
        }
    }

    // ========================================================================
    // Data-Cloud Sub-interface Extension Tests
    // ========================================================================

    @Nested
    @DisplayName("Data-Cloud sub-interfaces extend platform Plugin [GH-90000]")
    class SubInterfaceTests {

        @Test
        @DisplayName("event.spi.StoragePlugin extends platform Plugin [GH-90000]")
        void storagePluginExtendsPlatformPlugin() { // GH-90000
            assertThat(Plugin.class.isAssignableFrom( // GH-90000
                    com.ghatana.datacloud.event.spi.StoragePlugin.class)).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("event.spi.StreamingPlugin extends platform Plugin [GH-90000]")
        void streamingPluginExtendsPlatformPlugin() { // GH-90000
            assertThat(Plugin.class.isAssignableFrom( // GH-90000
                    com.ghatana.datacloud.event.spi.StreamingPlugin.class)).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("event.spi.RoutingPlugin extends platform Plugin [GH-90000]")
        void routingPluginExtendsPlatformPlugin() { // GH-90000
            assertThat(Plugin.class.isAssignableFrom( // GH-90000
                    com.ghatana.datacloud.event.spi.RoutingPlugin.class)).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("event.spi.ArchivePlugin extends platform Plugin [GH-90000]")
        void archivePluginExtendsPlatformPlugin() { // GH-90000
            assertThat(Plugin.class.isAssignableFrom( // GH-90000
                    com.ghatana.datacloud.event.spi.ArchivePlugin.class)).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("spi.DataStoragePlugin extends platform Plugin [GH-90000]")
        void dataStoragePluginExtendsPlatformPlugin() { // GH-90000
            assertThat(Plugin.class.isAssignableFrom( // GH-90000
                    com.ghatana.datacloud.spi.DataStoragePlugin.class)).isTrue(); // GH-90000
        }
    }

    // ========================================================================
    // Compliance & Lineage Plugin Tests (migrated implementations) // GH-90000
    // ========================================================================

    @Nested
    @DisplayName("Migrated Plugin Implementations [GH-90000]")
    class MigratedPluginTests {

        @Test
        @DisplayName("CompliancePlugin uses platform types throughout lifecycle [GH-90000]")
        void compliancePluginLifecycle() { // GH-90000
            var plugin = new com.ghatana.datacloud.plugins.compliance.CompliancePlugin(); // GH-90000

            assertThat(plugin.getState()).isEqualTo(PluginState.UNLOADED); // GH-90000
            assertThat(plugin.metadata()).isNotNull(); // GH-90000
            assertThat(plugin.metadata().id()).isEqualTo("compliance-plugin [GH-90000]");
            assertThat(plugin.metadata().type()).isEqualTo(PluginType.PROCESSING); // GH-90000
            assertThat(plugin.metadata().capabilities()) // GH-90000
                    .contains("pii-detection", "gdpr-compliance"); // GH-90000

            runPromise(() -> plugin.initialize(context)); // GH-90000
            assertThat(plugin.getState()).isEqualTo(PluginState.INITIALIZED); // GH-90000

            runPromise(() -> plugin.start()); // GH-90000
            assertThat(plugin.getState()).isEqualTo(PluginState.STARTED); // GH-90000

            HealthStatus health = runPromise(() -> plugin.healthCheck()); // GH-90000
            assertThat(health.isHealthy()).isTrue(); // GH-90000

            runPromise(() -> plugin.stop()); // GH-90000
            assertThat(plugin.getState()).isEqualTo(PluginState.STOPPED); // GH-90000

            // After stop, health check should report unhealthy
            HealthStatus stoppedHealth = runPromise(() -> plugin.healthCheck()); // GH-90000
            assertThat(stoppedHealth.isHealthy()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("LineagePlugin uses platform types throughout lifecycle [GH-90000]")
        void lineagePluginLifecycle() { // GH-90000
            var plugin = new com.ghatana.datacloud.plugins.lineage.LineagePlugin(); // GH-90000

            assertThat(plugin.getState()).isEqualTo(PluginState.UNLOADED); // GH-90000
            assertThat(plugin.metadata().id()).isEqualTo("lineage-plugin [GH-90000]");
            assertThat(plugin.metadata().type()).isEqualTo(PluginType.PROCESSING); // GH-90000
            assertThat(plugin.metadata().capabilities()) // GH-90000
                    .contains("lineage-tracking", "impact-analysis"); // GH-90000

            runPromise(() -> plugin.initialize(context)); // GH-90000
            assertThat(plugin.getState()).isEqualTo(PluginState.INITIALIZED); // GH-90000

            runPromise(() -> plugin.start()); // GH-90000
            assertThat(plugin.getState()).isEqualTo(PluginState.STARTED); // GH-90000

            HealthStatus health = runPromise(() -> plugin.healthCheck()); // GH-90000
            assertThat(health.isHealthy()).isTrue(); // GH-90000

            runPromise(() -> plugin.shutdown()); // GH-90000
            assertThat(plugin.getState()).isEqualTo(PluginState.STOPPED); // GH-90000
        }
    }

    // ========================================================================
    // Test Helpers
    // ========================================================================

    /**
     * Simple test plugin implementing the canonical platform Plugin interface.
     */
    static class TestPlugin implements Plugin {
        private final String id;
        private final PluginType type;
        private final Set<String> capabilities;
        private final AtomicReference<PluginState> state = new AtomicReference<>(PluginState.UNLOADED); // GH-90000

        TestPlugin(String id) { // GH-90000
            this(id, PluginType.CUSTOM, Set.of()); // GH-90000
        }

        TestPlugin(String id, PluginType type) { // GH-90000
            this(id, type, Set.of()); // GH-90000
        }

        TestPlugin(String id, Set<String> capabilities) { // GH-90000
            this(id, PluginType.CUSTOM, capabilities); // GH-90000
        }

        TestPlugin(String id, PluginType type, Set<String> capabilities) { // GH-90000
            this.id = id;
            this.type = type;
            this.capabilities = capabilities;
        }

        @Override
        public @NotNull PluginMetadata metadata() { // GH-90000
            return PluginMetadata.builder() // GH-90000
                    .id(id) // GH-90000
                    .name(id) // GH-90000
                    .type(type) // GH-90000
                    .capabilities(capabilities) // GH-90000
                    .build(); // GH-90000
        }

        @Override
        public @NotNull PluginState getState() { // GH-90000
            return state.get(); // GH-90000
        }

        @Override
        public @NotNull Promise<Void> initialize(@NotNull PluginContext context) { // GH-90000
            state.set(PluginState.INITIALIZED); // GH-90000
            return Promise.complete(); // GH-90000
        }

        @Override
        public @NotNull Promise<Void> start() { // GH-90000
            state.set(PluginState.RUNNING); // GH-90000
            return Promise.complete(); // GH-90000
        }

        @Override
        public @NotNull Promise<Void> stop() { // GH-90000
            state.set(PluginState.STOPPED); // GH-90000
            return Promise.complete(); // GH-90000
        }
    }

    /**
     * Plugin that exercises the default healthCheck() implementation. // GH-90000
     */
    static class DefaultHealthPlugin extends TestPlugin {
        DefaultHealthPlugin(String id) { // GH-90000
            super(id); // GH-90000
        }
        // Inherits default healthCheck() which returns HealthStatus.ok() // GH-90000
    }

    /**
     * Minimal PluginContext for testing default method behavior.
     */
    static class MinimalContext implements PluginContext {
        @Override
        public <T> T getConfig(@NotNull Class<T> configType) { // GH-90000
            return null;
        }

        @Override
        public @NotNull <T extends Plugin> Optional<T> findPlugin(@NotNull String pluginId) { // GH-90000
            return Optional.empty(); // GH-90000
        }

        @Override
        public @NotNull List<Plugin> findPluginsByCapability(@NotNull Class<? extends PluginCapability> capability) { // GH-90000
            return List.of(); // GH-90000
        }

        @Override
        public @NotNull PluginInteractionBus getInteractionBus() { // GH-90000
            throw new UnsupportedOperationException("No bus in minimal context [GH-90000]");
        }
    }

    /**
     * Test PluginProvider for verifying provider contract.
     */
    static class TestPluginProvider implements PluginProvider {
        @Override
        public @NotNull Plugin createPlugin() { // GH-90000
            return new TestPlugin("test-provider-plugin", PluginType.PROCESSING); // GH-90000
        }

        @Override
        public @NotNull PluginMetadata getMetadata() { // GH-90000
            return PluginMetadata.builder() // GH-90000
                    .id("test-provider-plugin [GH-90000]")
                    .name("Test Provider Plugin [GH-90000]")
                    .type(PluginType.PROCESSING) // GH-90000
                    .build(); // GH-90000
        }
    }
}
