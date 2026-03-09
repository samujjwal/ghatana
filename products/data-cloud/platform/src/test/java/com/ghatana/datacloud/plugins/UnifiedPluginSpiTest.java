package com.ghatana.datacloud.plugins;

import com.ghatana.platform.plugin.*;
import com.ghatana.platform.plugin.impl.DefaultPluginContext;
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
 *   <li>Platform Plugin lifecycle (init → start → stop → shutdown)</li>
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
class UnifiedPluginSpiTest {

    private PluginRegistry registry;
    private PluginContext context;

    @BeforeEach
    void setUp() {
        registry = new PluginRegistry();
        context = new DefaultPluginContext(registry, Map.of());
    }

    // ========================================================================
    // Plugin Lifecycle Tests
    // ========================================================================

    @Nested
    @DisplayName("Plugin Lifecycle")
    class PluginLifecycleTests {

        @Test
        @DisplayName("should transition through full lifecycle: UNLOADED → INITIALIZED → STARTED → STOPPED")
        void fullLifecycle() {
            TestPlugin plugin = new TestPlugin("lifecycle-test");

            assertThat(plugin.getState()).isEqualTo(PluginState.UNLOADED);

            plugin.initialize(context).getResult();
            assertThat(plugin.getState()).isEqualTo(PluginState.INITIALIZED);

            plugin.start().getResult();
            assertThat(plugin.getState()).isEqualTo(PluginState.RUNNING);

            plugin.stop().getResult();
            assertThat(plugin.getState()).isEqualTo(PluginState.STOPPED);
        }

        @Test
        @DisplayName("shutdown() should default to stop()")
        void shutdownDelegatesToStop() {
            TestPlugin plugin = new TestPlugin("shutdown-test");
            plugin.initialize(context).getResult();
            plugin.start().getResult();

            plugin.shutdown().getResult();
            assertThat(plugin.getState()).isEqualTo(PluginState.STOPPED);
        }

        @Test
        @DisplayName("healthCheck() default returns OK")
        void defaultHealthCheckReturnsOk() {
            DefaultHealthPlugin plugin = new DefaultHealthPlugin("health-default");
            HealthStatus status = plugin.healthCheck().getResult();

            assertThat(status.healthy()).isTrue();
            assertThat(status.message()).isEqualTo("OK");
        }

        @Test
        @DisplayName("getCapabilities() default returns empty set")
        void defaultCapabilitiesEmpty() {
            TestPlugin plugin = new TestPlugin("caps-test");
            assertThat(plugin.getCapabilities()).isEmpty();
        }

        @Test
        @DisplayName("getCapability() returns empty for unknown type")
        void getCapabilityReturnsEmpty() {
            TestPlugin plugin = new TestPlugin("cap-test");
            assertThat(plugin.getCapability(PluginCapability.class)).isEmpty();
        }
    }

    // ========================================================================
    // PluginMetadata Tests
    // ========================================================================

    @Nested
    @DisplayName("PluginMetadata")
    class PluginMetadataTests {

        @Test
        @DisplayName("builder should create metadata with all fields")
        void builderCreatesFullMetadata() {
            PluginMetadata metadata = PluginMetadata.builder()
                    .id("test-plugin")
                    .name("Test Plugin")
                    .version("2.1.0")
                    .description("A test plugin")
                    .type(PluginType.STORAGE)
                    .author("Test Author")
                    .license("Apache-2.0")
                    .tags(Set.of("test", "storage"))
                    .capabilities(Set.of("read", "write"))
                    .properties(Map.of("key", "value"))
                    .build();

            assertThat(metadata.id()).isEqualTo("test-plugin");
            assertThat(metadata.name()).isEqualTo("Test Plugin");
            assertThat(metadata.version()).isEqualTo("2.1.0");
            assertThat(metadata.description()).isEqualTo("A test plugin");
            assertThat(metadata.type()).isEqualTo(PluginType.STORAGE);
            assertThat(metadata.author()).isEqualTo("Test Author");
            assertThat(metadata.vendor()).isEqualTo("Test Author"); // alias
            assertThat(metadata.license()).isEqualTo("Apache-2.0");
            assertThat(metadata.tags()).containsExactlyInAnyOrder("test", "storage");
            assertThat(metadata.capabilities()).containsExactlyInAnyOrder("read", "write");
            assertThat(metadata.properties()).containsEntry("key", "value");
        }

        @Test
        @DisplayName("builder should apply defaults for optional fields")
        void builderAppliesDefaults() {
            PluginMetadata metadata = PluginMetadata.builder()
                    .id("minimal")
                    .build();

            assertThat(metadata.id()).isEqualTo("minimal");
            assertThat(metadata.name()).isEqualTo("minimal"); // defaults to id
            assertThat(metadata.version()).isEqualTo("1.0.0");
            assertThat(metadata.description()).isEmpty();
            assertThat(metadata.type()).isEqualTo(PluginType.CUSTOM);
            assertThat(metadata.author()).isEqualTo("Ghatana");
            assertThat(metadata.license()).isEqualTo("Proprietary");
            assertThat(metadata.tags()).isEmpty();
            assertThat(metadata.capabilities()).isEmpty();
            assertThat(metadata.properties()).isEmpty();
        }

        @Test
        @DisplayName("hasCapability returns true for declared capabilities")
        void hasCapabilityCheck() {
            PluginMetadata metadata = PluginMetadata.builder()
                    .id("cap-test")
                    .capabilities(Set.of("streaming", "batch"))
                    .build();

            assertThat(metadata.hasCapability("streaming")).isTrue();
            assertThat(metadata.hasCapability("batch")).isTrue();
            assertThat(metadata.hasCapability("unknown")).isFalse();
        }

        @Test
        @DisplayName("vendor() is alias for author()")
        void vendorAlias() {
            PluginMetadata metadata = PluginMetadata.builder()
                    .id("vendor-test")
                    .vendor("MyCompany")
                    .build();

            assertThat(metadata.author()).isEqualTo("MyCompany");
            assertThat(metadata.vendor()).isEqualTo("MyCompany");
        }

        @Test
        @DisplayName("builder requires non-blank id")
        void builderRequiresId() {
            assertThatThrownBy(() -> PluginMetadata.builder().build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("id");
        }
    }

    // ========================================================================
    // PluginState Tests
    // ========================================================================

    @Nested
    @DisplayName("PluginState")
    class PluginStateTests {

        @Test
        @DisplayName("isActive() returns true for active states")
        void isActive() {
            assertThat(PluginState.INITIALIZED.isActive()).isTrue();
            assertThat(PluginState.STARTING.isActive()).isTrue();
            assertThat(PluginState.STARTED.isActive()).isTrue();
            assertThat(PluginState.RUNNING.isActive()).isTrue();

            assertThat(PluginState.UNLOADED.isActive()).isFalse();
            assertThat(PluginState.STOPPED.isActive()).isFalse();
            assertThat(PluginState.ERROR.isActive()).isFalse();
            assertThat(PluginState.FAILED.isActive()).isFalse();
        }

        @Test
        @DisplayName("isTerminal() returns true for terminal states")
        void isTerminal() {
            assertThat(PluginState.STOPPED.isTerminal()).isTrue();
            assertThat(PluginState.ERROR.isTerminal()).isTrue();
            assertThat(PluginState.FAILED.isTerminal()).isTrue();

            assertThat(PluginState.RUNNING.isTerminal()).isFalse();
            assertThat(PluginState.UNLOADED.isTerminal()).isFalse();
        }

        @Test
        @DisplayName("isError() returns true for error states")
        void isError() {
            assertThat(PluginState.ERROR.isError()).isTrue();
            assertThat(PluginState.FAILED.isError()).isTrue();

            assertThat(PluginState.STOPPED.isError()).isFalse();
            assertThat(PluginState.RUNNING.isError()).isFalse();
        }

        @Test
        @DisplayName("all expected states exist in the enum")
        void allStatesExist() {
            Set<String> expected = Set.of(
                    "UNLOADED", "DISCOVERED", "INITIALIZED", "STARTING",
                    "STARTED", "RUNNING", "STOPPING", "STOPPED",
                    "ERROR", "FAILED"
            );
            Set<String> actual = new HashSet<>();
            for (PluginState s : PluginState.values()) {
                actual.add(s.name());
            }
            assertThat(actual).containsAll(expected);
        }
    }

    // ========================================================================
    // HealthStatus Tests
    // ========================================================================

    @Nested
    @DisplayName("HealthStatus")
    class HealthStatusTests {

        @Test
        @DisplayName("ok() creates healthy status with default message")
        void okDefault() {
            HealthStatus status = HealthStatus.ok();
            assertThat(status.healthy()).isTrue();
            assertThat(status.message()).isEqualTo("OK");
            assertThat(status.details()).isEmpty();
        }

        @Test
        @DisplayName("ok(message) creates healthy status with custom message")
        void okWithMessage() {
            HealthStatus status = HealthStatus.ok("All good");
            assertThat(status.healthy()).isTrue();
            assertThat(status.message()).isEqualTo("All good");
        }

        @Test
        @DisplayName("ok(message, details) creates healthy status with details")
        void okWithDetails() {
            HealthStatus status = HealthStatus.ok("Healthy", Map.of("latency", 5));
            assertThat(status.healthy()).isTrue();
            assertThat(status.details()).containsEntry("latency", 5);
        }

        @Test
        @DisplayName("error(message) creates unhealthy status")
        void errorWithMessage() {
            HealthStatus status = HealthStatus.error("Connection refused");
            assertThat(status.healthy()).isFalse();
            assertThat(status.message()).isEqualTo("Connection refused");
        }

        @Test
        @DisplayName("error(message, throwable) includes exception details")
        void errorWithThrowable() {
            Exception ex = new RuntimeException("boom");
            HealthStatus status = HealthStatus.error("Failed", ex);
            assertThat(status.healthy()).isFalse();
            assertThat(status.details()).containsEntry("error", "RuntimeException");
            assertThat(status.details()).containsEntry("errorMessage", "boom");
        }

        @Test
        @DisplayName("unhealthy(message) creates unhealthy status")
        void unhealthyWithMessage() {
            HealthStatus status = HealthStatus.unhealthy("Degraded");
            assertThat(status.healthy()).isFalse();
            assertThat(status.message()).isEqualTo("Degraded");
        }
    }

    // ========================================================================
    // PluginRegistry Tests
    // ========================================================================

    @Nested
    @DisplayName("PluginRegistry")
    class PluginRegistryTests {

        @Test
        @DisplayName("register and retrieve plugin by id")
        void registerAndGet() {
            TestPlugin plugin = new TestPlugin("my-plugin");
            registry.register(plugin);

            Optional<Plugin> found = registry.getPlugin("my-plugin");
            assertThat(found).isPresent();
            assertThat(found.get()).isSameAs(plugin);
        }

        @Test
        @DisplayName("getPlugin returns empty for unknown id")
        void getPluginNotFound() {
            assertThat(registry.getPlugin("nonexistent")).isEmpty();
        }

        @Test
        @DisplayName("register prevents duplicates by throwing")
        void preventDuplicates() {
            TestPlugin p1 = new TestPlugin("dup-plugin");
            TestPlugin p2 = new TestPlugin("dup-plugin");

            registry.register(p1);
            assertThatThrownBy(() -> registry.register(p2))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("dup-plugin");

            // Original stays registered
            assertThat(registry.getPlugin("dup-plugin").orElse(null)).isSameAs(p1);
        }

        @Test
        @DisplayName("unregister removes plugin and returns it")
        void unregisterPlugin() {
            TestPlugin plugin = new TestPlugin("removable");
            registry.register(plugin);

            Optional<Plugin> removed = registry.unregister("removable");
            assertThat(removed).isPresent();
            assertThat(removed.get()).isSameAs(plugin);
            assertThat(registry.getPlugin("removable")).isEmpty();
        }

        @Test
        @DisplayName("unregister returns empty for unknown id")
        void unregisterNotFound() {
            assertThat(registry.unregister("ghost")).isEmpty();
        }

        @Test
        @DisplayName("size tracks registered plugins")
        void sizeTracking() {
            assertThat(registry.size()).isEqualTo(0);

            registry.register(new TestPlugin("a"));
            registry.register(new TestPlugin("b"));
            assertThat(registry.size()).isEqualTo(2);

            registry.unregister("a");
            assertThat(registry.size()).isEqualTo(1);
        }

        @Test
        @DisplayName("getAllPlugins returns all registered plugins")
        void getAllPlugins() {
            registry.register(new TestPlugin("x"));
            registry.register(new TestPlugin("y"));

            assertThat(registry.getAllPlugins()).hasSize(2);
        }

        @Test
        @DisplayName("isRegistered returns correct status")
        void isRegistered() {
            TestPlugin plugin = new TestPlugin("check-me");
            assertThat(registry.isRegistered("check-me")).isFalse();

            registry.register(plugin);
            assertThat(registry.isRegistered("check-me")).isTrue();
        }

        @Test
        @DisplayName("findByType returns matching plugins")
        void findByType() {
            registry.register(new TestPlugin("storage-1", PluginType.STORAGE));
            registry.register(new TestPlugin("stream-1", PluginType.STREAMING));
            registry.register(new TestPlugin("storage-2", PluginType.STORAGE));

            List<Plugin> storagePlugins = registry.findByType(PluginType.STORAGE);
            assertThat(storagePlugins).hasSize(2);
        }

        @Test
        @DisplayName("findByStringCapability returns matching plugins")
        void findByStringCapability() {
            registry.register(new TestPlugin("p1", Set.of("streaming", "batch")));
            registry.register(new TestPlugin("p2", Set.of("batch")));
            registry.register(new TestPlugin("p3", Set.of("realtime")));

            List<Plugin> batchPlugins = registry.findByStringCapability("batch");
            assertThat(batchPlugins).hasSize(2);

            List<Plugin> realtimePlugins = registry.findByStringCapability("realtime");
            assertThat(realtimePlugins).hasSize(1);
        }

        @Test
        @DisplayName("find with predicate returns matching plugins")
        void findWithPredicate() {
            registry.register(new TestPlugin("a-plugin"));
            registry.register(new TestPlugin("b-plugin"));
            registry.register(new TestPlugin("a-other"));

            List<Plugin> result = registry.find(p -> p.metadata().id().startsWith("a-"));
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("clear removes all plugins")
        void clearAll() {
            registry.register(new TestPlugin("one"));
            registry.register(new TestPlugin("two"));
            assertThat(registry.size()).isEqualTo(2);

            registry.clear();
            assertThat(registry.size()).isEqualTo(0);
        }

        @Test
        @DisplayName("aggregateHealth returns aggregate health of all plugins")
        void aggregateHealth() {
            registry.register(new TestPlugin("healthy-1"));
            registry.register(new TestPlugin("healthy-2"));

            HealthStatus aggregate = registry.aggregateHealth().getResult();
            assertThat(aggregate.healthy()).isTrue();
        }
    }

    // ========================================================================
    // PluginContext Tests
    // ========================================================================

    @Nested
    @DisplayName("PluginContext")
    class PluginContextTests {

        @Test
        @DisplayName("default getConfig(key, default) returns default")
        void defaultConfigReturnsDefault() {
            PluginContext ctx = new MinimalContext();
            assertThat(ctx.getConfig("missing.key", "fallback")).isEqualTo("fallback");
        }

        @Test
        @DisplayName("default getConfigMap() returns empty")
        void defaultConfigMapEmpty() {
            PluginContext ctx = new MinimalContext();
            assertThat(ctx.getConfigMap()).isEmpty();
        }

        @Test
        @DisplayName("default getEnvironment() returns production")
        void defaultEnvironmentProduction() {
            PluginContext ctx = new MinimalContext();
            assertThat(ctx.getEnvironment()).isEqualTo("production");
        }

        @Test
        @DisplayName("default getTenantId() returns null")
        void defaultTenantIdNull() {
            PluginContext ctx = new MinimalContext();
            assertThat(ctx.getTenantId()).isNull();
        }

        @Test
        @DisplayName("DefaultPluginContext finds registered plugins")
        void defaultContextFindsPlugins() {
            TestPlugin p = new TestPlugin("findable");
            registry.register(p);

            Optional<Plugin> found = context.findPlugin("findable");
            assertThat(found).isPresent();
        }
    }

    // ========================================================================
    // PluginProvider Tests
    // ========================================================================

    @Nested
    @DisplayName("PluginProvider")
    class PluginProviderTests {

        @Test
        @DisplayName("provider creates plugin and returns metadata")
        void providerContract() {
            PluginProvider provider = new TestPluginProvider();

            Plugin plugin = provider.createPlugin();
            assertThat(plugin).isNotNull();
            assertThat(plugin.metadata().id()).isEqualTo("test-provider-plugin");

            PluginMetadata meta = provider.getMetadata();
            assertThat(meta.id()).isEqualTo("test-provider-plugin");
            assertThat(meta.type()).isEqualTo(PluginType.PROCESSING);
        }

        @Test
        @DisplayName("provider defaults: priority=1000, enabled=true")
        void providerDefaults() {
            PluginProvider provider = new TestPluginProvider();
            assertThat(provider.priority()).isEqualTo(1000);
            assertThat(provider.isEnabled()).isTrue();
        }
    }

    // ========================================================================
    // PluginType Tests
    // ========================================================================

    @Nested
    @DisplayName("PluginType")
    class PluginTypeTests {

        @Test
        @DisplayName("all required plugin types exist")
        void allTypesExist() {
            Set<String> expected = Set.of(
                    "STORAGE", "PROCESSING", "STREAMING", "AI", "GOVERNANCE",
                    "INTEGRATION", "OBSERVABILITY", "ENTERPRISE",
                    "ROUTING", "ARCHIVE", "ANALYTICS", "SCHEMA", "AUTH", "CUSTOM"
            );
            Set<String> actual = new HashSet<>();
            for (PluginType t : PluginType.values()) {
                actual.add(t.name());
            }
            assertThat(actual).containsAll(expected);
        }
    }

    // ========================================================================
    // Data-Cloud Sub-interface Extension Tests
    // ========================================================================

    @Nested
    @DisplayName("Data-Cloud sub-interfaces extend platform Plugin")
    class SubInterfaceTests {

        @Test
        @DisplayName("event.spi.StoragePlugin extends platform Plugin")
        void storagePluginExtendsPlatformPlugin() {
            assertThat(Plugin.class.isAssignableFrom(
                    com.ghatana.datacloud.event.spi.StoragePlugin.class)).isTrue();
        }

        @Test
        @DisplayName("event.spi.StreamingPlugin extends platform Plugin")
        void streamingPluginExtendsPlatformPlugin() {
            assertThat(Plugin.class.isAssignableFrom(
                    com.ghatana.datacloud.event.spi.StreamingPlugin.class)).isTrue();
        }

        @Test
        @DisplayName("event.spi.RoutingPlugin extends platform Plugin")
        void routingPluginExtendsPlatformPlugin() {
            assertThat(Plugin.class.isAssignableFrom(
                    com.ghatana.datacloud.event.spi.RoutingPlugin.class)).isTrue();
        }

        @Test
        @DisplayName("event.spi.ArchivePlugin extends platform Plugin")
        void archivePluginExtendsPlatformPlugin() {
            assertThat(Plugin.class.isAssignableFrom(
                    com.ghatana.datacloud.event.spi.ArchivePlugin.class)).isTrue();
        }

        @Test
        @DisplayName("spi.DataStoragePlugin extends platform Plugin")
        void dataStoragePluginExtendsPlatformPlugin() {
            assertThat(Plugin.class.isAssignableFrom(
                    com.ghatana.datacloud.spi.DataStoragePlugin.class)).isTrue();
        }
    }

    // ========================================================================
    // Compliance & Lineage Plugin Tests (migrated implementations)
    // ========================================================================

    @Nested
    @DisplayName("Migrated Plugin Implementations")
    class MigratedPluginTests {

        @Test
        @DisplayName("CompliancePlugin uses platform types throughout lifecycle")
        void compliancePluginLifecycle() {
            var plugin = new com.ghatana.datacloud.plugins.compliance.CompliancePlugin();

            assertThat(plugin.getState()).isEqualTo(PluginState.UNLOADED);
            assertThat(plugin.metadata()).isNotNull();
            assertThat(plugin.metadata().id()).isEqualTo("compliance-plugin");
            assertThat(plugin.metadata().type()).isEqualTo(PluginType.PROCESSING);
            assertThat(plugin.metadata().capabilities())
                    .contains("pii-detection", "gdpr-compliance");

            plugin.initialize(context).getResult();
            assertThat(plugin.getState()).isEqualTo(PluginState.INITIALIZED);

            plugin.start().getResult();
            assertThat(plugin.getState()).isEqualTo(PluginState.STARTED);

            HealthStatus health = plugin.healthCheck().getResult();
            assertThat(health.healthy()).isTrue();

            plugin.stop().getResult();
            assertThat(plugin.getState()).isEqualTo(PluginState.STOPPED);

            // After stop, health check should report unhealthy
            HealthStatus stoppedHealth = plugin.healthCheck().getResult();
            assertThat(stoppedHealth.healthy()).isFalse();
        }

        @Test
        @DisplayName("LineagePlugin uses platform types throughout lifecycle")
        void lineagePluginLifecycle() {
            var plugin = new com.ghatana.datacloud.plugins.lineage.LineagePlugin();

            assertThat(plugin.getState()).isEqualTo(PluginState.UNLOADED);
            assertThat(plugin.metadata().id()).isEqualTo("lineage-plugin");
            assertThat(plugin.metadata().type()).isEqualTo(PluginType.PROCESSING);
            assertThat(plugin.metadata().capabilities())
                    .contains("lineage-tracking", "impact-analysis");

            plugin.initialize(context).getResult();
            assertThat(plugin.getState()).isEqualTo(PluginState.INITIALIZED);

            plugin.start().getResult();
            assertThat(plugin.getState()).isEqualTo(PluginState.STARTED);

            HealthStatus health = plugin.healthCheck().getResult();
            assertThat(health.healthy()).isTrue();

            plugin.shutdown().getResult();
            assertThat(plugin.getState()).isEqualTo(PluginState.STOPPED);
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
        private final AtomicReference<PluginState> state = new AtomicReference<>(PluginState.UNLOADED);

        TestPlugin(String id) {
            this(id, PluginType.CUSTOM, Set.of());
        }

        TestPlugin(String id, PluginType type) {
            this(id, type, Set.of());
        }

        TestPlugin(String id, Set<String> capabilities) {
            this(id, PluginType.CUSTOM, capabilities);
        }

        TestPlugin(String id, PluginType type, Set<String> capabilities) {
            this.id = id;
            this.type = type;
            this.capabilities = capabilities;
        }

        @Override
        public @NotNull PluginMetadata metadata() {
            return PluginMetadata.builder()
                    .id(id)
                    .name(id)
                    .type(type)
                    .capabilities(capabilities)
                    .build();
        }

        @Override
        public @NotNull PluginState getState() {
            return state.get();
        }

        @Override
        public @NotNull Promise<Void> initialize(@NotNull PluginContext context) {
            state.set(PluginState.INITIALIZED);
            return Promise.complete();
        }

        @Override
        public @NotNull Promise<Void> start() {
            state.set(PluginState.RUNNING);
            return Promise.complete();
        }

        @Override
        public @NotNull Promise<Void> stop() {
            state.set(PluginState.STOPPED);
            return Promise.complete();
        }
    }

    /**
     * Plugin that exercises the default healthCheck() implementation.
     */
    static class DefaultHealthPlugin extends TestPlugin {
        DefaultHealthPlugin(String id) {
            super(id);
        }
        // Inherits default healthCheck() which returns HealthStatus.ok()
    }

    /**
     * Minimal PluginContext for testing default method behavior.
     */
    static class MinimalContext implements PluginContext {
        @Override
        public <T> T getConfig(@NotNull Class<T> configType) {
            return null;
        }

        @Override
        public @NotNull <T extends Plugin> Optional<T> findPlugin(@NotNull String pluginId) {
            return Optional.empty();
        }

        @Override
        public @NotNull List<Plugin> findPluginsByCapability(@NotNull Class<? extends PluginCapability> capability) {
            return List.of();
        }

        @Override
        public @NotNull PluginInteractionBus getInteractionBus() {
            throw new UnsupportedOperationException("No bus in minimal context");
        }
    }

    /**
     * Test PluginProvider for verifying provider contract.
     */
    static class TestPluginProvider implements PluginProvider {
        @Override
        public @NotNull Plugin createPlugin() {
            return new TestPlugin("test-provider-plugin", PluginType.PROCESSING);
        }

        @Override
        public @NotNull PluginMetadata getMetadata() {
            return PluginMetadata.builder()
                    .id("test-provider-plugin")
                    .name("Test Provider Plugin")
                    .type(PluginType.PROCESSING)
                    .build();
        }
    }
}
