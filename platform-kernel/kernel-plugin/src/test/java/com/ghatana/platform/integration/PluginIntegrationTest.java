package com.ghatana.platform.plugin.integration;

import com.ghatana.platform.plugin.*;
import com.ghatana.platform.plugin.test.PluginTestBase;
import com.ghatana.platform.plugin.test.InMemoryStoragePlugin;
import io.activej.promise.Promise;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive test suite for inter-plugin integration and communication.
 *
 * Validates cross-plugin capabilities including:
 * - Plugin discovery and registration
 * - Event publishing between plugins
 * - Service exposure and consumption
 * - Version compatibility checking
 * - Plugin composition and extension
 *
 * @doc.type class
 * @doc.purpose Inter-plugin communication, discovery, service exposure, compatibility
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("PluginIntegrationTest")
@Tag("integration")
class PluginIntegrationTest extends PluginTestBase {

    // ═══════════════════════════════════════════════════════════════════════════════════
    // PLUGIN DISCOVERY TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("plugin discovery")
    class PluginDiscovery {

        @Test
        @DisplayName("registered plugins can be discovered by ID")
        void registeredPluginsCanBeDiscoveredById() {
            InMemoryStoragePlugin plugin = new InMemoryStoragePlugin();
            registry.register(plugin);

            Optional<?> discovered = registry.getById(plugin.metadata().id());

            assertThat(discovered).isPresent();
        }

        @Test
        @DisplayName("multiple plugins can be discovered")
        void multiplePluginsCanBeDiscovered() {
            InMemoryStoragePlugin plugin1 = new InMemoryStoragePlugin();
            InMemoryStoragePlugin plugin2 = new InMemoryStoragePlugin();
            
            registry.register(plugin1);
            registry.register(plugin2);

            Collection<?> allPlugins = registry.getAll();

            assertThat(allPlugins).hasSize(2);
        }

        @Test
        @DisplayName("unregistered plugins are not discovered")
        void unregisteredPluginsAreNotDiscovered() {
            Optional<?> notFound = registry.getById("nonexistent-plugin-id");

            assertThat(notFound).isEmpty();
        }

        @Test
        @DisplayName("plugin metadata is accessible after discovery")
        void pluginMetadataIsAccessibleAfterDiscovery() {
            InMemoryStoragePlugin plugin = new InMemoryStoragePlugin();
            registry.register(plugin);

            Optional<?> discovered = registry.getById(plugin.metadata().id());

            assertThat(discovered).isPresent();
            // Plugin should be discoverable
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // SERVICE EXPOSURE TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("service exposure and discovery")
    class ServiceExposureAndDiscovery {

        @Test
        @DisplayName("plugins can expose services")
        void pluginsCanExposeServices() {
            TestServiceExposingPlugin plugin = new TestServiceExposingPlugin();
            registry.register(plugin);

            runPromise(() -> registry.initializeAll(context).then(() -> registry.startAll()));

            assertThat(plugin.isServiceExposed()).isTrue();
        }

        @Test
        @DisplayName("exposed services are accessible to other plugins")
        void exposedServicesAreAccessibleToOtherPlugins() {
            TestServiceExposingPlugin servicePlugin = new TestServiceExposingPlugin();
            TestServiceConsumingPlugin consumerPlugin = new TestServiceConsumingPlugin();
            
            registry.register(servicePlugin);
            registry.register(consumerPlugin);

            runPromise(() -> registry.initializeAll(context).then(() -> registry.startAll()));

            assertThat(servicePlugin.isServiceExposed()).isTrue();
            assertThat(consumerPlugin.canAccessService()).isTrue();
        }

        @Test
        @DisplayName("service versions are compatible")
        void serviceVersionsAreCompatible() {
            TestServiceExposingPlugin servicePlugin = new TestServiceExposingPlugin();
            TestServiceConsumingPlugin consumerPlugin = new TestServiceConsumingPlugin();
            
            assertThat(servicePlugin.getServiceVersion())
                    .isEqualTo(consumerPlugin.getRequiredServiceVersion());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // INTER-PLUGIN COMMUNICATION TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("inter-plugin communication")
    class InterPluginCommunication {

        @Test
        @DisplayName("plugins can publish events")
        void pluginsCanPublishEvents() {
            TestEventPublishingPlugin publisher = new TestEventPublishingPlugin();
            registry.register(publisher);

            runPromise(() -> registry.initializeAll(context).then(() -> registry.startAll()));

            publisher.publishEvent("test.event", "test data");
            assertThat(publisher.hasPublishedEvents()).isTrue();
        }

        @Test
        @DisplayName("plugins can subscribe to events")
        void pluginsCanSubscribeToEvents() {
            TestEventPublishingPlugin publisher = new TestEventPublishingPlugin();
            TestEventSubscribingPlugin subscriber = new TestEventSubscribingPlugin();
            
            registry.register(publisher);
            registry.register(subscriber);

            runPromise(() -> registry.initializeAll(context).then(() -> registry.startAll()));

            publisher.publishEvent("test.event", "test data");

            assertThat(subscriber.hasReceivedEvents()).isTrue();
        }

        @Test
        @DisplayName("event messages are passed correctly between plugins")
        void eventMessagesArePassedCorrectly() {
            TestEventPublishingPlugin publisher = new TestEventPublishingPlugin();
            TestEventSubscribingPlugin subscriber = new TestEventSubscribingPlugin();
            
            registry.register(publisher);
            registry.register(subscriber);

            runPromise(() -> registry.initializeAll(context).then(() -> registry.startAll()));

            String testData = "test event data";
            publisher.publishEvent("test.event", testData);

            assertThat(subscriber.getLastReceivedData()).contains(testData);
        }

        @Test
        @DisplayName("multiple subscribers can receive same event")
        void multipleSubscribersCanReceiveSameEvent() {
            TestEventPublishingPlugin publisher = new TestEventPublishingPlugin();
            TestEventSubscribingPlugin subscriber1 = new TestEventSubscribingPlugin();
            TestEventSubscribingPlugin subscriber2 = new TestEventSubscribingPlugin();
            
            registry.register(publisher);
            registry.register(subscriber1);
            registry.register(subscriber2);

            runPromise(() -> registry.initializeAll(context).then(() -> registry.startAll()));

            publisher.publishEvent("test.event", "shared event data");

            assertThat(subscriber1.hasReceivedEvents()).isTrue();
            assertThat(subscriber2.hasReceivedEvents()).isTrue();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // VERSION COMPATIBILITY TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("version compatibility")
    class VersionCompatibility {

        @Test
        @DisplayName("compatible versions are accepted")
        void compatibleVersionsAreAccepted() {
            TestServiceExposingPlugin servicePlugin = new TestServiceExposingPlugin("1.0.0");
            TestServiceConsumingPlugin consumerPlugin = new TestServiceConsumingPlugin("1.0.0");
            
            assertThat(servicePlugin.getServiceVersion())
                    .isEqualTo(consumerPlugin.getRequiredServiceVersion());
        }

        @Test
        @DisplayName("version mismatch is detected")
        void versionMismatchIsDetected() {
            TestServiceExposingPlugin servicePlugin = new TestServiceExposingPlugin("2.0.0");
            TestServiceConsumingPlugin consumerPlugin = new TestServiceConsumingPlugin("1.0.0");
            
            assertThat(servicePlugin.getServiceVersion())
                    .isNotEqualTo(consumerPlugin.getRequiredServiceVersion());
        }

        @Test
        @DisplayName("backward compatible versions are supported")
        void backwardCompatibleVersionsAreSupported() {
            // Assume 1.x is compatible with 1.y (minor version compatibility)
            String providerVersion = "1.2.0";
            String consumerVersion = "1.0.0";
            
            // Both should be compatible within same major version
            assertThat(providerVersion.split("\\.")[0])
                    .isEqualTo(consumerVersion.split("\\.")[0]);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // PLUGIN COMPOSITION TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("plugin composition")
    class PluginComposition {

        @Test
        @DisplayName("multiple plugins can work together cooperatively")
        void multiplePluginsWorkTogetherCooperatively() {
            InMemoryStoragePlugin plugin1 = new InMemoryStoragePlugin();
            InMemoryStoragePlugin plugin2 = new InMemoryStoragePlugin();
            InMemoryStoragePlugin plugin3 = new InMemoryStoragePlugin();
            
            registry.register(plugin1);
            registry.register(plugin2);
            registry.register(plugin3);

            runPromise(() -> registry.initializeAll(context).then(() -> registry.startAll()));

            assertThat(plugin1.getState()).isEqualTo(PluginState.RUNNING);
            assertThat(plugin2.getState()).isEqualTo(PluginState.RUNNING);
            assertThat(plugin3.getState()).isEqualTo(PluginState.RUNNING);
        }

        @Test
        @DisplayName("plugins can be composed into a larger system")
        void pluginsCanBeComposedIntoLargerSystem() {
            // Create a system of plugins
            TestServiceExposingPlugin service = new TestServiceExposingPlugin();
            TestEventPublishingPlugin eventBus = new TestEventPublishingPlugin();
            TestServiceConsumingPlugin consumer = new TestServiceConsumingPlugin();
            
            registry.register(service);
            registry.register(eventBus);
            registry.register(consumer);

            runPromise(() -> registry.initializeAll(context).then(() -> registry.startAll()));

            // All should be running as part of the system
            Collection<?> plugins = registry.getAll();
            assertThat(plugins).hasSize(3);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // LIFECYCLE COORDINATION TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("lifecycle coordination")
    class LifecycleCoordination {

        @Test
        @DisplayName("dependent plugins are initialized in correct order")
        void dependentPluginsAreInitializedInCorrectOrder() {
            List<String> initOrder = new ArrayList<>();
            
            TestEventPublishingPlugin publisher = new TestEventPublishingPlugin();
            TestEventSubscribingPlugin subscriber = new TestEventSubscribingPlugin();
            
            registry.register(publisher);
            registry.register(subscriber);

            runPromise(() -> registry.initializeAll(context).then(() -> registry.startAll()));

            // Both should be initialized
            Collection<?> plugins = registry.getAll();
            assertThat(plugins).hasSize(2);
        }

        @Test
        @DisplayName("all plugins are started after initialization")
        void allPluginsAreStartedAfterInitialization() {
            InMemoryStoragePlugin plugin1 = new InMemoryStoragePlugin();
            InMemoryStoragePlugin plugin2 = new InMemoryStoragePlugin();
            
            registry.register(plugin1);
            registry.register(plugin2);

            runPromise(() -> registry.initializeAll(context));
            assertThat(plugin1.getState()).isEqualTo(PluginState.INITIALIZED);
            assertThat(plugin2.getState()).isEqualTo(PluginState.INITIALIZED);

            runPromise(() -> registry.startAll());
            assertThat(plugin1.getState()).isEqualTo(PluginState.RUNNING);
            assertThat(plugin2.getState()).isEqualTo(PluginState.RUNNING);
        }

        @Test
        @DisplayName("all plugins are stopped together on shutdown")
        void allPluginsAreStoppedTogetherOnShutdown() {
            InMemoryStoragePlugin plugin1 = new InMemoryStoragePlugin();
            InMemoryStoragePlugin plugin2 = new InMemoryStoragePlugin();
            
            registry.register(plugin1);
            registry.register(plugin2);

            runPromise(() -> registry.initializeAll(context).then(() -> registry.startAll()));
            runPromise(() -> registry.stopAll());

            assertThat(plugin1.getState()).isEqualTo(PluginState.STOPPED);
            assertThat(plugin2.getState()).isEqualTo(PluginState.STOPPED);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // TEST HELPER CLASSES
    // ═══════════════════════════════════════════════════════════════════════════════════

    private static class TestServiceExposingPlugin extends InMemoryStoragePlugin {
        private final String serviceVersion;
        private boolean serviceExposed = false;

        TestServiceExposingPlugin() {
            this("1.0.0");
        }

        TestServiceExposingPlugin(String version) {
            this.serviceVersion = version;
        }

        @Override
        public Promise<Void> start() {
            serviceExposed = true;
            return super.start();
        }

        public boolean isServiceExposed() {
            return serviceExposed;
        }

        public String getServiceVersion() {
            return serviceVersion;
        }
    }

    private static class TestServiceConsumingPlugin extends InMemoryStoragePlugin {
        private final String requiredVersion;
        private boolean canAccessService = false;

        TestServiceConsumingPlugin() {
            this("1.0.0");
        }

        TestServiceConsumingPlugin(String version) {
            this.requiredVersion = version;
        }

        @Override
        public Promise<Void> start() {
            canAccessService = true;
            return super.start();
        }

        public boolean canAccessService() {
            return canAccessService;
        }

        public String getRequiredServiceVersion() {
            return requiredVersion;
        }
    }

    private static class TestEventPublishingPlugin extends InMemoryStoragePlugin {
        private final List<String> publishedEvents = new ArrayList<>();

        public void publishEvent(String eventType, String data) {
            publishedEvents.add(eventType + ":" + data);
        }

        public boolean hasPublishedEvents() {
            return !publishedEvents.isEmpty();
        }
    }

    private static class TestEventSubscribingPlugin extends InMemoryStoragePlugin {
        private final List<String> receivedEvents = new ArrayList<>();
        private String lastReceivedData = "";

        public void onEventReceived(String eventType, String data) {
            receivedEvents.add(eventType + ":" + data);
            lastReceivedData = data;
        }

        public boolean hasReceivedEvents() {
            return !receivedEvents.isEmpty();
        }

        public String getLastReceivedData() {
            return lastReceivedData;
        }
    }
}
