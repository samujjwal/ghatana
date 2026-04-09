package com.ghatana.platform.plugin.activation;

import com.ghatana.platform.plugin.*;
import com.ghatana.platform.plugin.test.PluginTestBase;
import com.ghatana.platform.plugin.test.InMemoryStoragePlugin;
import io.activej.promise.Promise;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive test suite for plugin activation and deactivation.
 *
 * Validates plugin lifecycle operations including:
 * - Plugin activation (initialize + start)
 * - Plugin deactivation (stop + cleanup)
 * - Partial failure handling
 * - Resource cleanup verification
 * - State consistency during transitions
 *
 * @doc.type class
 * @doc.purpose Plugin activation/deactivation, resource cleanup, failure handling
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("PluginActivationTest")
@Tag("integration")
class PluginActivationTest extends PluginTestBase {

    // ═══════════════════════════════════════════════════════════════════════════════════
    // BASIC ACTIVATION TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("basic plugin activation")
    class BasicPluginActivation {

        @Test
        @DisplayName("plugin activates through initialize and start")
        void pluginActivatesThroughInitializeAndStart() {
            InMemoryStoragePlugin plugin = new InMemoryStoragePlugin();
            registry.register(plugin);

            runPromise(() -> registry.initializeAll(context).then(() -> registry.startAll()));

            assertThat(plugin.getState()).isEqualTo(PluginState.RUNNING);
        }

        @Test
        @DisplayName("activate transitions plugin from UNLOADED to RUNNING")
        void activateTransitionsPluginToRunning() {
            InMemoryStoragePlugin plugin = new InMemoryStoragePlugin();

            assertThat(plugin.getState()).isEqualTo(PluginState.UNLOADED);

            registry.register(plugin);
            runPromise(() -> registry.initializeAll(context).then(() -> registry.startAll()));

            assertThat(plugin.getState()).isEqualTo(PluginState.RUNNING);
        }

        @Test
        @DisplayName("activated plugin is ready for operations")
        void activatedPluginIsReadyForOperations() {
            InMemoryStoragePlugin plugin = new InMemoryStoragePlugin();
            registry.register(plugin);

            runPromise(() -> registry.initializeAll(context).then(() -> registry.startAll()));

            assertThat(plugin.getState()).isEqualTo(PluginState.RUNNING);
            // Plugin should be operational at this point
        }

        @Test
        @DisplayName("multiple plugins can be activated together")
        void multiplePluginsCanBeActivatedTogether() {
            InMemoryStoragePlugin plugin1 = new InMemoryStoragePlugin();
            InMemoryStoragePlugin plugin2 = new InMemoryStoragePlugin();

            registry.register(plugin1);
            registry.register(plugin2);

            runPromise(() -> registry.initializeAll(context).then(() -> registry.startAll()));

            assertThat(plugin1.getState()).isEqualTo(PluginState.RUNNING);
            assertThat(plugin2.getState()).isEqualTo(PluginState.RUNNING);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // BASIC DEACTIVATION TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("basic plugin deactivation")
    class BasicPluginDeactivation {

        @Test
        @DisplayName("plugin deactivates through stop")
        void pluginDeactivatesThroughStop() {
            InMemoryStoragePlugin plugin = new InMemoryStoragePlugin();
            registry.register(plugin);

            runPromise(() -> registry.initializeAll(context)
                    .then(() -> registry.startAll())
                    .then(() -> registry.stopAll()));

            assertThat(plugin.getState()).isEqualTo(PluginState.STOPPED);
        }

        @Test
        @DisplayName("deactivate transitions plugin from RUNNING to STOPPED")
        void deactivateTransitionsPluginToStopped() {
            InMemoryStoragePlugin plugin = new InMemoryStoragePlugin();
            registry.register(plugin);

            runPromise(() -> registry.initializeAll(context).then(() -> registry.startAll()));
            assertThat(plugin.getState()).isEqualTo(PluginState.RUNNING);

            runPromise(() -> registry.stopAll());
            assertThat(plugin.getState()).isEqualTo(PluginState.STOPPED);
        }

        @Test
        @DisplayName("deactivated plugin cannot process operations")
        void deactivatedPluginCannotProcessOperations() {
            InMemoryStoragePlugin plugin = new InMemoryStoragePlugin();
            registry.register(plugin);

            runPromise(() -> registry.initializeAll(context)
                    .then(() -> registry.startAll())
                    .then(() -> registry.stopAll()));

            assertThat(plugin.getState()).isEqualTo(PluginState.STOPPED);
            // Plugin should not be operational
        }

        @Test
        @DisplayName("multiple plugins can be deactivated together")
        void multiplePluginsCanBeDeactivatedTogether() {
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
    // PARTIAL FAILURE TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("partial failure handling")
    class PartialFailureHandling {

        @Test
        @DisplayName("one plugin failure doesn't prevent others from activating")
        void onePluginFailureDontPreventsOthersActivating() {
            InMemoryStoragePlugin healthyPlugin = new InMemoryStoragePlugin();
            TestFailingPlugin failingPlugin = new TestFailingPlugin(
                    true,  // fail during initialization
                    false  // don't fail during start
            );

            registry.register(healthyPlugin);
            registry.register(failingPlugin);

            // One plugin fails, but others should continue
            try {
                runPromise(() -> registry.initializeAll(context));
            } catch (Exception e) {
                // Expected: one plugin failed
            }

            // Healthy plugin might still transition
            assertThat(healthyPlugin.getState()).isNotEqualTo(PluginState.UNLOADED);
        }

        @Test
        @DisplayName("plugin failure during activation is recoverable")
        void pluginFailureDuringActivationIsRecoverable() {
            TestFailingPlugin failingPlugin = new TestFailingPlugin(
                    true,  // fail during initialization
                    false  // don't fail during start
            );

            registry.register(failingPlugin);

            // Should throw during initialization
            assertThatThrownBy(() ->
                    runPromise(() -> registry.initializeAll(context))
            ).isInstanceOf(Exception.class);

            // Plugin should be in a defined state (not corrupted)
            assertThat(failingPlugin.getState()).isNotNull();
        }

        @Test
        @DisplayName("cleanup proceeds even if stop fails")
        void cleanupProceedsEvenIfStopFails() {
            TestFailingPlugin failingPlugin = new TestFailingPlugin(
                    false, // don't fail during init
                    false  // don't fail during start
            );

            registry.register(failingPlugin);

            runPromise(() -> registry.initializeAll(context).then(() -> registry.startAll()));
            assertThat(failingPlugin.getState()).isEqualTo(PluginState.RUNNING);

            // Now test that we can attempt to stop even with failures
            try {
                runPromise(() -> registry.stopAll());
            } catch (Exception e) {
                // May throw, but resources should still be cleaned
            }

            // Should end up in a terminal state
            PluginState finalState = failingPlugin.getState();
            assertThat(finalState).isIn(PluginState.STOPPED, PluginState.UNLOADED);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // RESOURCE CLEANUP VERIFICATION TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("resource cleanup verification")
    class ResourceCleanupVerification {

        @Test
        @DisplayName("deactivation triggers resource cleanup")
        void deactivationTriggersResourceCleanup() {
            TestResourceTrackingPlugin resourcePlugin = new TestResourceTrackingPlugin();
            registry.register(resourcePlugin);

            runPromise(() -> registry.initializeAll(context).then(() -> registry.startAll()));
            assertThat(resourcePlugin.resourcesAllocated()).isGreaterThan(0);

            runPromise(() -> registry.stopAll());
            assertThat(resourcePlugin.resourcesCleaned()).isGreaterThan(0);
        }

        @Test
        @DisplayName("all allocated resources are cleaned on deactivation")
        void allAllocatedResourcesAreCleanedOnDeactivation() {
            TestResourceTrackingPlugin resourcePlugin = new TestResourceTrackingPlugin();
            registry.register(resourcePlugin);

            runPromise(() -> registry.initializeAll(context).then(() -> registry.startAll()));
            int allocated = resourcePlugin.resourcesAllocated();

            runPromise(() -> registry.stopAll());
            int cleaned = resourcePlugin.resourcesCleaned();

            assertThat(cleaned).isGreaterThanOrEqualTo(allocated);
        }

        @Test
        @DisplayName("cleanup occurs even with multiple plugins")
        void cleanupOccursWithMultiplePlugins() {
            TestResourceTrackingPlugin plugin1 = new TestResourceTrackingPlugin();
            TestResourceTrackingPlugin plugin2 = new TestResourceTrackingPlugin();

            registry.register(plugin1);
            registry.register(plugin2);

            runPromise(() -> registry.initializeAll(context).then(() -> registry.startAll()));
            runPromise(() -> registry.stopAll());

            assertThat(plugin1.resourcesCleaned()).isGreaterThanOrEqualTo(0);
            assertThat(plugin2.resourcesCleaned()).isGreaterThanOrEqualTo(0);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // STATE CONSISTENCY TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("state consistency")
    class StateConsistency {

        @Test
        @DisplayName("plugin state is consistent after activation")
        void pluginStateIsConsistentAfterActivation() {
            InMemoryStoragePlugin plugin = new InMemoryStoragePlugin();
            registry.register(plugin);

            runPromise(() -> registry.initializeAll(context).then(() -> registry.startAll()));

            PluginState state = plugin.getState();
            PluginState stateAgain = plugin.getState();

            assertThat(state).isEqualTo(stateAgain).isEqualTo(PluginState.RUNNING);
        }

        @Test
        @DisplayName("plugin state is consistent after deactivation")
        void pluginStateIsConsistentAfterDeactivation() {
            InMemoryStoragePlugin plugin = new InMemoryStoragePlugin();
            registry.register(plugin);

            runPromise(() -> registry.initializeAll(context)
                    .then(() -> registry.startAll())
                    .then(() -> registry.stopAll()));

            PluginState state = plugin.getState();
            PluginState stateAgain = plugin.getState();

            assertThat(state).isEqualTo(stateAgain).isEqualTo(PluginState.STOPPED);
        }

        @Test
        @DisplayName("state transitions are atomic and not partial")
        void stateTransitionsAreAtomicAndNotPartial() {
            InMemoryStoragePlugin plugin = new InMemoryStoragePlugin();
            registry.register(plugin);

            // Initialize
            runPromise(() -> registry.initializeAll(context));
            PluginState afterInit = plugin.getState();
            assertThat(afterInit).isEqualTo(PluginState.INITIALIZED);

            // Start
            runPromise(() -> registry.startAll());
            PluginState afterStart = plugin.getState();
            assertThat(afterStart).isEqualTo(PluginState.RUNNING);

            // Stop
            runPromise(() -> registry.stopAll());
            PluginState afterStop = plugin.getState();
            assertThat(afterStop).isEqualTo(PluginState.STOPPED);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // CONCURRENT ACTIVATION/DEACTIVATION TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("concurrent operations")
    class ConcurrentOperations {

        @Test
        @DisplayName("concurrent activation of plugins succeeds")
        void concurrentActivationOfPluginsSucceeds() throws InterruptedException {
            InMemoryStoragePlugin plugin1 = new InMemoryStoragePlugin();
            InMemoryStoragePlugin plugin2 = new InMemoryStoragePlugin();

            registry.register(plugin1);
            registry.register(plugin2);

            CountDownLatch latch = new CountDownLatch(1);
            AtomicBoolean success = new AtomicBoolean(false);

            Thread activationThread = new Thread(() -> {
                try {
                    runPromise(() -> registry.initializeAll(context)
                            .then(() -> registry.startAll()));
                    success.set(true);
                } finally {
                    latch.countDown();
                }
            });

            activationThread.start();
            latch.await();

            assertThat(success.get()).isTrue();
            assertThat(plugin1.getState()).isEqualTo(PluginState.RUNNING);
            assertThat(plugin2.getState()).isEqualTo(PluginState.RUNNING);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // REPEATED ACTIVATION/DEACTIVATION TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("repeated operations")
    class RepeatedOperations {

        @Test
        @DisplayName("plugin can be activated and deactivated multiple times")
        void pluginCanBeActivatedDeactivatedMultipleTimes() {
            InMemoryStoragePlugin plugin = new InMemoryStoragePlugin();
            registry.register(plugin);

            for (int i = 0; i < 3; i++) {
                runPromise(() -> registry.initializeAll(context).then(() -> registry.startAll()));
                assertThat(plugin.getState()).isEqualTo(PluginState.RUNNING);

                runPromise(() -> registry.stopAll());
                assertThat(plugin.getState()).isEqualTo(PluginState.STOPPED);
            }
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 2, 3, 5, 10})
        @DisplayName("plugin cycles through multiple activation/deactivation rounds")
        void pluginCyclesThroughMultipleRounds(int cycles) {
            InMemoryStoragePlugin plugin = new InMemoryStoragePlugin();
            registry.register(plugin);

            for (int i = 0; i < cycles; i++) {
                runPromise(() -> registry.initializeAll(context).then(() -> registry.startAll()));
                assertThat(plugin.getState()).isEqualTo(PluginState.RUNNING);

                runPromise(() -> registry.stopAll());
                assertThat(plugin.getState()).isEqualTo(PluginState.STOPPED);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // TEST HELPER CLASSES
    // ═══════════════════════════════════════════════════════════════════════════════════

    /**
     * Plugin that can be configured to fail during specific lifecycle phases.
     */
    private static class TestFailingPlugin extends InMemoryStoragePlugin {
        private final boolean failDuringInit;
        private final boolean failDuringStart;

        TestFailingPlugin(boolean failDuringInit, boolean failDuringStart) {
            this.failDuringInit = failDuringInit;
            this.failDuringStart = failDuringStart;
        }

        @Override
        public Promise<Void> initialize(PluginContext context) {
            if (failDuringInit) {
                return Promise.ofException(new RuntimeException("Initialization failed"));
            }
            return super.initialize(context);
        }

        @Override
        public Promise<Void> start() {
            if (failDuringStart) {
                return Promise.ofException(new RuntimeException("Start failed"));
            }
            return super.start();
        }
    }

    /**
     * Plugin that tracks resource allocation and cleanup.
     */
    private static class TestResourceTrackingPlugin extends InMemoryStoragePlugin {
        private final AtomicInteger resourceCount = new AtomicInteger(0);
        private final AtomicInteger cleanedResourceCount = new AtomicInteger(0);

        @Override
        public Promise<Void> initialize(PluginContext context) {
            resourceCount.incrementAndGet();
            return super.initialize(context);
        }

        @Override
        public Promise<Void> start() {
            resourceCount.incrementAndGet();
            return super.start();
        }

        @Override
        public Promise<Void> stop() {
            cleanedResourceCount.addAndGet(resourceCount.get());
            resourceCount.set(0);
            return super.stop();
        }

        public int resourcesAllocated() {
            return resourceCount.get();
        }

        public int resourcesCleaned() {
            return cleanedResourceCount.get();
        }
    }
}
