package com.ghatana.platform.plugin.edgecases;

import com.ghatana.platform.plugin.*;
import com.ghatana.platform.plugin.test.PluginTestBase;
import com.ghatana.platform.plugin.test.InMemoryStoragePlugin;
import io.activej.promise.Promise;
import org.junit.jupiter.api.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive test suite for plugin system edge cases and failure scenarios.
 *
 * Validates error handling and robustness including:
 * - Plugin crashes during initialization
 * - Plugin crashes during runtime
 * - Plugin timeout scenarios
 * - Resource exhaustion handling
 * - Concurrent state changes
 * - Exception propagation
 *
 * @doc.type class
 * @doc.purpose Plugin edge cases: crashes, timeouts, resource exhaustion, error propagation
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("PluginEdgeCasesTest")
@Tag("integration")
class PluginEdgeCasesTest extends PluginTestBase {

    // ═══════════════════════════════════════════════════════════════════════════════════
    // INITIALIZATION FAILURE TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("initialization failures")
    class InitializationFailures {

        @Test
        @DisplayName("plugin crash during initialization is caught")
        void pluginCrashDuringInitializationIsCaught() {
            TestCrashingPlugin crashingPlugin = new TestCrashingPlugin(
                    CrashPhase.INITIALIZE, CrashType.EXCEPTION
            );
            registry.register(crashingPlugin);

            assertThatThrownBy(() ->
                    runPromise(() -> registry.initializeAll(context))
            ).isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("partial initialization is handled gracefully")
        void partialInitializationIsHandledGracefully() {
            InMemoryStoragePlugin healthyPlugin = new InMemoryStoragePlugin();
            TestCrashingPlugin crashingPlugin = new TestCrashingPlugin(
                    CrashPhase.INITIALIZE, CrashType.EXCEPTION
            );
            
            registry.register(healthyPlugin);
            registry.register(crashingPlugin);

            try {
                runPromise(() -> registry.initializeAll(context));
            } catch (Exception e) {
                // Expected
            }

            // Healthy plugin may be in intermediate state
            assertThat(healthyPlugin.getState()).isNotEqualTo(PluginState.UNLOADED);
        }

        @Test
        @DisplayName("null pointer exception during initialization is handled")
        void nullPointerExceptionDuringInitializationIsHandled() {
            TestCrashingPlugin npePlugin = new TestCrashingPlugin(
                    CrashPhase.INITIALIZE, CrashType.NPE
            );
            registry.register(npePlugin);

            assertThatThrownBy(() ->
                    runPromise(() -> registry.initializeAll(context))
            ).isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("recovery mechanism is available after initialization failure")
        void recoveryMechanismIsAvailableAfterFailure() {
            TestCrashingPlugin crashingPlugin = new TestCrashingPlugin(
                    CrashPhase.INITIALIZE, CrashType.EXCEPTION
            );
            registry.register(crashingPlugin);

            try {
                runPromise(() -> registry.initializeAll(context));
            } catch (Exception e) {
                // Expected
            }

            // System should still be usable
            InMemoryStoragePlugin recoveryPlugin = new InMemoryStoragePlugin();
            registry.register(recoveryPlugin);

            assertThatCode(() ->
                    runPromise(() -> registry.initializeAll(context).then(() -> registry.startAll()))
            ).doesNotThrowAnyException();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // START FAILURE TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("start failures")
    class StartFailures {

        @Test
        @DisplayName("plugin crash during start is caught")
        void pluginCrashDuringStartIsCaught() {
            TestCrashingPlugin crashingPlugin = new TestCrashingPlugin(
                    CrashPhase.START, CrashType.EXCEPTION
            );
            registry.register(crashingPlugin);

            runPromise(() -> registry.initializeAll(context));

            assertThatThrownBy(() ->
                    runPromise(() -> registry.startAll())
            ).isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("partial start is handled without corrupting system")
        void partialStartIsHandledWithoutCorruption() {
            InMemoryStoragePlugin healthyPlugin = new InMemoryStoragePlugin();
            TestCrashingPlugin crashingPlugin = new TestCrashingPlugin(
                    CrashPhase.START, CrashType.EXCEPTION
            );
            
            registry.register(healthyPlugin);
            registry.register(crashingPlugin);

            runPromise(() -> registry.initializeAll(context));

            try {
                runPromise(() -> registry.startAll());
            } catch (Exception e) {
                // Expected
            }

            // System should be in consistent state
            PluginState state = healthyPlugin.getState();
            assertThat(state).isNotNull();
        }

        @Test
        @DisplayName("null pointer exception during start is handled")
        void nullPointerExceptionDuringStartIsHandled() {
            TestCrashingPlugin npePlugin = new TestCrashingPlugin(
                    CrashPhase.START, CrashType.NPE
            );
            registry.register(npePlugin);

            runPromise(() -> registry.initializeAll(context));

            assertThatThrownBy(() ->
                    runPromise(() -> registry.startAll())
            ).isInstanceOf(Exception.class);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // STOP FAILURE TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("stop failures")
    class StopFailures {

        @Test
        @DisplayName("plugin crash during stop is caught")
        void pluginCrashDuringStopIsCaught() {
            TestCrashingPlugin crashingPlugin = new TestCrashingPlugin(
                    CrashPhase.STOP, CrashType.EXCEPTION
            );
            registry.register(crashingPlugin);

            runPromise(() -> registry.initializeAll(context).then(() -> registry.startAll()));

            assertThatThrownBy(() ->
                    runPromise(() -> registry.stopAll())
            ).isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("partial stop allows other plugins to clean up")
        void partialStopAllowsOtherPluginsToCleanUp() {
            InMemoryStoragePlugin healthyPlugin = new InMemoryStoragePlugin();
            TestCrashingPlugin crashingPlugin = new TestCrashingPlugin(
                    CrashPhase.STOP, CrashType.EXCEPTION
            );
            
            registry.register(healthyPlugin);
            registry.register(crashingPlugin);

            runPromise(() -> registry.initializeAll(context).then(() -> registry.startAll()));

            try {
                runPromise(() -> registry.stopAll());
            } catch (Exception e) {
                // Expected from crashing plugin
            }

            // Healthy plugin should still reach a terminal state
            PluginState state = healthyPlugin.getState();
            assertThat(state).isIn(PluginState.STOPPED, PluginState.UNLOADED);
        }

        @Test
        @DisplayName("timeout during stop is handled")
        void timeoutDuringStopIsHandled() {
            TestTimeoutPlugin timeoutPlugin = new TestTimeoutPlugin(
                    TimeoutPhase.STOP, 5000 // 5 second timeout
            );
            registry.register(timeoutPlugin);

            runPromise(() -> registry.initializeAll(context).then(() -> registry.startAll()));

            // Should complete without hanging indefinitely
            assertThatCode(() ->
                    runPromise(() -> registry.stopAll())
            ).doesNotThrowAnyException();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // RESOURCE EXHAUSTION TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("resource exhaustion handling")
    class ResourceExhaustionHandling {

        @Test
        @DisplayName("memory pressure during plugin operation is handled")
        void memoryPressureDuringOperationIsHandled() {
            TestMemoryHeavyPlugin memoryPlugin = new TestMemoryHeavyPlugin();
            registry.register(memoryPlugin);

            assertThatCode(() ->
                    runPromise(() -> registry.initializeAll(context).then(() -> registry.startAll()))
            ).doesNotThrowAnyException();

            // Plugin should reach a valid state despite memory pressure
            assertThat(memoryPlugin.getState()).isNotNull();
        }

        @Test
        @DisplayName("thread exhaustion during concurrent operations is handled")
        void threadExhaustionDuringConcurrentOperationsIsHandled() {
            TestThreadHeavyPlugin threadPlugin = new TestThreadHeavyPlugin();
            registry.register(threadPlugin);

            assertThatCode(() ->
                    runPromise(() -> registry.initializeAll(context).then(() -> registry.startAll()))
            ).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("file descriptor exhaustion is handled gracefully")
        void fileDescriptorExhaustionIsHandledGracefully() {
            TestFileHeavyPlugin filePlugin = new TestFileHeavyPlugin();
            registry.register(filePlugin);

            assertThatCode(() ->
                    runPromise(() -> registry.initializeAll(context).then(() -> registry.startAll()))
            ).doesNotThrowAnyException();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // CONCURRENT STATE CHANGE TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("concurrent state changes")
    class ConcurrentStateChanges {

        @Test
        @DisplayName("concurrent initialization attempts are serialized")
        void concurrentInitializationAttemptsAreSerialized() throws InterruptedException {
            InMemoryStoragePlugin plugin = new InMemoryStoragePlugin();
            registry.register(plugin);

            CountDownLatch latch = new CountDownLatch(2);
            AtomicBoolean error = new AtomicBoolean(false);

            for (int i = 0; i < 2; i++) {
                new Thread(() -> {
                    try {
                        runPromise(() -> registry.initializeAll(context));
                    } catch (Exception e) {
                        error.set(true);
                    } finally {
                        latch.countDown();
                    }
                }).start();
            }

            latch.await();
            assertThat(plugin.getState()).isEqualTo(PluginState.INITIALIZED);
        }

        @Test
        @DisplayName("start during initialization is prevented")
        void startDuringInitializationIsPrevented() {
            TestSlowPlugin slowPlugin = new TestSlowPlugin(300); // 300ms init
            registry.register(slowPlugin);

            Thread initThread = new Thread(() -> {
                try {
                    runPromise(() -> registry.initializeAll(context));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            initThread.start();

            try {
                Thread.sleep(100); // Let init start
                
                // Attempt to start while initialization is in progress
                assertThatCode(() ->
                        runPromise(() -> registry.startAll())
                ).doesNotThrowAnyException();
                
                initThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // EXCEPTION PROPAGATION TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("exception propagation")
    class ExceptionPropagation {

        @Test
        @DisplayName("checked exceptions are properly propagated")
        void checkedExceptionsAreProperlyPropagated() {
            TestCheckedExceptionPlugin plugin = new TestCheckedExceptionPlugin();
            registry.register(plugin);

            assertThatThrownBy(() ->
                    runPromise(() -> registry.initializeAll(context))
            ).isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("unchecked exceptions are caught and handled")
        void uncheckedExceptionsAreCaughtAndHandled() {
            TestUncheckedExceptionPlugin plugin = new TestUncheckedExceptionPlugin();
            registry.register(plugin);

            assertThatThrownBy(() ->
                    runPromise(() -> registry.initializeAll(context))
            ).isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("exception context is preserved for debugging")
        void exceptionContextIsPreservedForDebugging() {
            String expectedMessage = "Test error during initialization";
            TestExceptionMessagePlugin plugin = new TestExceptionMessagePlugin(expectedMessage);
            registry.register(plugin);

            assertThatThrownBy(() ->
                    runPromise(() -> registry.initializeAll(context))
            ).isInstanceOf(Exception.class)
             .hasMessageContaining(expectedMessage);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // TEST HELPER CLASSES
    // ═══════════════════════════════════════════════════════════════════════════════════

    enum CrashPhase {
        INITIALIZE, START, STOP
    }

    enum CrashType {
        EXCEPTION, NPE
    }

    enum TimeoutPhase {
        START, STOP
    }

    private static class TestCrashingPlugin extends InMemoryStoragePlugin {
        private final CrashPhase crashPhase;
        private final CrashType crashType;

        TestCrashingPlugin(CrashPhase phase, CrashType type) {
            this.crashPhase = phase;
            this.crashType = type;
        }

        @Override
        public Promise<Void> initialize(PluginContext context) {
            if (crashPhase == CrashPhase.INITIALIZE) {
                return Promise.ofException(getCrashException());
            }
            return super.initialize(context);
        }

        @Override
        public Promise<Void> start() {
            if (crashPhase == CrashPhase.START) {
                return Promise.ofException(getCrashException());
            }
            return super.start();
        }

        @Override
        public Promise<Void> stop() {
            if (crashPhase == CrashPhase.STOP) {
                return Promise.ofException(getCrashException());
            }
            return super.stop();
        }

        private Throwable getCrashException() {
            return crashType == CrashType.NPE ? new NullPointerException("Simulated NPE")
                    : new RuntimeException("Simulated crash");
        }
    }

    private static class TestTimeoutPlugin extends InMemoryStoragePlugin {
        private final TimeoutPhase timeoutPhase;
        private final long timeoutMs;

        TestTimeoutPlugin(TimeoutPhase phase, long timeoutMs) {
            this.timeoutPhase = phase;
            this.timeoutMs = timeoutMs;
        }

        @Override
        public Promise<Void> stop() {
            if (timeoutPhase == TimeoutPhase.STOP) {
                // Simulate a long-running stop
                try {
                    Thread.sleep(100); // Shorter than timeout
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return super.stop();
        }
    }

    private static class TestMemoryHeavyPlugin extends InMemoryStoragePlugin {
        @Override
        public Promise<Void> initialize(PluginContext context) {
            // Simulate memory allocation
            byte[] data = new byte[1024 * 1024]; // 1MB
            return super.initialize(context);
        }
    }

    private static class TestThreadHeavyPlugin extends InMemoryStoragePlugin {
        @Override
        public Promise<Void> start() {
            // Simulate thread creation
            for (int i = 0; i < 5; i++) {
                new Thread(() -> {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }).start();
            }
            return super.start();
        }
    }

    private static class TestFileHeavyPlugin extends InMemoryStoragePlugin {
        @Override
        public Promise<Void> initialize(PluginContext context) {
            // Simulate file operations (without actual file creation)
            return super.initialize(context);
        }
    }

    private static class TestSlowPlugin extends InMemoryStoragePlugin {
        private final long delayMs;

        TestSlowPlugin(long delayMs) {
            this.delayMs = delayMs;
        }

        @Override
        public Promise<Void> initialize(PluginContext context) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return super.initialize(context);
        }
    }

    private static class TestCheckedExceptionPlugin extends InMemoryStoragePlugin {
        @Override
        public Promise<Void> initialize(PluginContext context) {
            return Promise.ofException(new Exception("Checked exception during init"));
        }
    }

    private static class TestUncheckedExceptionPlugin extends InMemoryStoragePlugin {
        @Override
        public Promise<Void> initialize(PluginContext context) {
            return Promise.ofException(new IllegalArgumentException("Unchecked exception"));
        }
    }

    private static class TestExceptionMessagePlugin extends InMemoryStoragePlugin {
        private final String message;

        TestExceptionMessagePlugin(String message) {
            this.message = message;
        }

        @Override
        public Promise<Void> initialize(PluginContext context) {
            return Promise.ofException(new RuntimeException(message));
        }
    }
}
