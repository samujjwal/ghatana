package com.ghatana.platform.core.client;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract tests for {@link AsyncClient} implementations via {@link ManagedAsyncClient}.
 * Validates lifecycle state transitions, running-state flag correctness, and
 * concurrent start/stop safety guaranteed by the base class.
 *
 * @doc.type class
 * @doc.purpose Contract tests for AsyncClient lifecycle and state management
 * @doc.layer core
 * @doc.pattern Test
 */
@DisplayName("AsyncClient Contract — lifecycle and state management [GH-90000]")
class AsyncClientContractTest extends EventloopTestBase {

    // ── Concrete test double ──────────────────────────────────────────────────

    /**
     * Minimal concrete implementation for contract-level verification.
     */
    static class TestAsyncClient extends ManagedAsyncClient {

        private int startCount = 0;
        private int stopCount  = 0;

        TestAsyncClient() { // GH-90000
            super(); // GH-90000
        }

        TestAsyncClient(boolean initiallyRunning) { // GH-90000
            super(initiallyRunning); // GH-90000
        }

        @Override
        public Promise<Void> start() { // GH-90000
            startCount++;
            markStarted(); // GH-90000
            return Promise.complete(); // GH-90000
        }

        @Override
        public Promise<Void> stop() { // GH-90000
            stopCount++;
            markStopped(); // GH-90000
            return Promise.complete(); // GH-90000
        }

        @Override
        public Promise<Boolean> healthCheck() { // GH-90000
            return Promise.of(isRunning()); // GH-90000
        }

        int getStartCount() { return startCount; } // GH-90000
        int getStopCount()  { return stopCount; } // GH-90000
    }

    // ── Initial state ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("initial state [GH-90000]")
    class InitialState {

        @Test
        @DisplayName("client starts not running by default [GH-90000]")
        void clientStartsNotRunningByDefault() { // GH-90000
            TestAsyncClient client = new TestAsyncClient(); // GH-90000
            assertThat(client.isRunning()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("client can be constructed in running state [GH-90000]")
        void clientCanBeConstructedInRunningState() { // GH-90000
            TestAsyncClient client = new TestAsyncClient(true); // GH-90000
            assertThat(client.isRunning()).isTrue(); // GH-90000
        }
    }

    // ── Start lifecycle ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("start() lifecycle [GH-90000]")
    class StartLifecycle {

        @Test
        @DisplayName("start() transitions running flag to true [GH-90000]")
        void start_transitionsRunningFlagToTrue() { // GH-90000
            TestAsyncClient client = new TestAsyncClient(); // GH-90000

            runPromise(client::start); // GH-90000

            assertThat(client.isRunning()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("start() returns completed promise [GH-90000]")
        void start_returnsCompletedPromise() { // GH-90000
            TestAsyncClient client = new TestAsyncClient(); // GH-90000

            Void result = runPromise(client::start); // GH-90000

            assertThat(result).isNull(); // Void promise completes with null // GH-90000
            assertThat(client.isRunning()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("markStarted() returns true on first transition, false if already running [GH-90000]")
        void markStarted_idempotentStateTransition() { // GH-90000
            TestAsyncClient client = new TestAsyncClient(); // GH-90000

            // Manually transition - first should succeed
            boolean firstTransition = client.markStarted(); // GH-90000
            // second should fail (already running) // GH-90000
            boolean secondTransition = client.markStarted(); // GH-90000

            assertThat(firstTransition).isTrue(); // GH-90000
            assertThat(secondTransition).isFalse(); // GH-90000
            assertThat(client.isRunning()).isTrue(); // GH-90000
        }
    }

    // ── Stop lifecycle ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("stop() lifecycle [GH-90000]")
    class StopLifecycle {

        @Test
        @DisplayName("stop() transitions running flag to false [GH-90000]")
        void stop_transitionsRunningFlagToFalse() { // GH-90000
            TestAsyncClient client = new TestAsyncClient(true); // GH-90000

            runPromise(client::stop); // GH-90000

            assertThat(client.isRunning()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("markStopped() returns true on first transition, false if already stopped [GH-90000]")
        void markStopped_idempotentStateTransition() { // GH-90000
            TestAsyncClient client = new TestAsyncClient(true); // GH-90000

            boolean firstTransition  = client.markStopped(); // GH-90000
            boolean secondTransition = client.markStopped(); // GH-90000

            assertThat(firstTransition).isTrue(); // GH-90000
            assertThat(secondTransition).isFalse(); // GH-90000
            assertThat(client.isRunning()).isFalse(); // GH-90000
        }
    }

    // ── Health check ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("healthCheck() [GH-90000]")
    class HealthCheck {

        @Test
        @DisplayName("healthCheck() returns false when not running [GH-90000]")
        void healthCheck_returnsFalseWhenNotRunning() { // GH-90000
            TestAsyncClient client = new TestAsyncClient(); // GH-90000

            boolean healthy = runPromise(client::healthCheck); // GH-90000

            assertThat(healthy).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("healthCheck() returns true when running [GH-90000]")
        void healthCheck_returnsTrueWhenRunning() { // GH-90000
            TestAsyncClient client = new TestAsyncClient(true); // GH-90000

            boolean healthy = runPromise(client::healthCheck); // GH-90000

            assertThat(healthy).isTrue(); // GH-90000
        }
    }

    // ── Start → Stop → Start roundtrip ────────────────────────────────────────

    @Nested
    @DisplayName("start → stop → restart roundtrip [GH-90000]")
    class StartStopRestart {

        @Test
        @DisplayName("client can be restarted after stopping [GH-90000]")
        void clientCanBeRestartedAfterStopping() { // GH-90000
            TestAsyncClient client = new TestAsyncClient(); // GH-90000

            runPromise(client::start); // GH-90000
            assertThat(client.isRunning()).isTrue(); // GH-90000

            runPromise(client::stop); // GH-90000
            assertThat(client.isRunning()).isFalse(); // GH-90000

            runPromise(client::start); // GH-90000
            assertThat(client.isRunning()).isTrue(); // GH-90000

            assertThat(client.getStartCount()).isEqualTo(2); // GH-90000
            assertThat(client.getStopCount()).isEqualTo(1); // GH-90000
        }
    }

    // ── setRunning() force override ─────────────────────────────────────────── // GH-90000

    @Test
    @DisplayName("setRunning() forces state unconditionally [GH-90000]")
    void setRunning_forcesStateUnconditionally() { // GH-90000
        TestAsyncClient client = new TestAsyncClient(); // GH-90000

        client.setRunning(true); // GH-90000
        assertThat(client.isRunning()).isTrue(); // GH-90000

        client.setRunning(false); // GH-90000
        assertThat(client.isRunning()).isFalse(); // GH-90000
    }
}
