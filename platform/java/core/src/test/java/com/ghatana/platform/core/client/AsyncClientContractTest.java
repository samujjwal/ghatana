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
@DisplayName("AsyncClient Contract — lifecycle and state management")
class AsyncClientContractTest extends EventloopTestBase {

    // ── Concrete test double ──────────────────────────────────────────────────

    /**
     * Minimal concrete implementation for contract-level verification.
     */
    static class TestAsyncClient extends ManagedAsyncClient {

        private int startCount = 0;
        private int stopCount  = 0;

        TestAsyncClient() { 
            super(); 
        }

        TestAsyncClient(boolean initiallyRunning) { 
            super(initiallyRunning); 
        }

        @Override
        public Promise<Void> start() { 
            startCount++;
            markStarted(); 
            return Promise.complete(); 
        }

        @Override
        public Promise<Void> stop() { 
            stopCount++;
            markStopped(); 
            return Promise.complete(); 
        }

        @Override
        public Promise<Boolean> healthCheck() { 
            return Promise.of(isRunning()); 
        }

        int getStartCount() { return startCount; } 
        int getStopCount()  { return stopCount; } 
    }

    // ── Initial state ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("initial state")
    class InitialState {

        @Test
        @DisplayName("client starts not running by default")
        void clientStartsNotRunningByDefault() { 
            TestAsyncClient client = new TestAsyncClient(); 
            assertThat(client.isRunning()).isFalse(); 
        }

        @Test
        @DisplayName("client can be constructed in running state")
        void clientCanBeConstructedInRunningState() { 
            TestAsyncClient client = new TestAsyncClient(true); 
            assertThat(client.isRunning()).isTrue(); 
        }
    }

    // ── Start lifecycle ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("start() lifecycle")
    class StartLifecycle {

        @Test
        @DisplayName("start() transitions running flag to true")
        void start_transitionsRunningFlagToTrue() { 
            TestAsyncClient client = new TestAsyncClient(); 

            runPromise(client::start); 

            assertThat(client.isRunning()).isTrue(); 
        }

        @Test
        @DisplayName("start() returns completed promise")
        void start_returnsCompletedPromise() { 
            TestAsyncClient client = new TestAsyncClient(); 

            Void result = runPromise(client::start); 

            assertThat(result).isNull(); // Void promise completes with null 
            assertThat(client.isRunning()).isTrue(); 
        }

        @Test
        @DisplayName("markStarted() returns true on first transition, false if already running")
        void markStarted_idempotentStateTransition() { 
            TestAsyncClient client = new TestAsyncClient(); 

            // Manually transition - first should succeed
            boolean firstTransition = client.markStarted(); 
            // second should fail (already running) 
            boolean secondTransition = client.markStarted(); 

            assertThat(firstTransition).isTrue(); 
            assertThat(secondTransition).isFalse(); 
            assertThat(client.isRunning()).isTrue(); 
        }
    }

    // ── Stop lifecycle ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("stop() lifecycle")
    class StopLifecycle {

        @Test
        @DisplayName("stop() transitions running flag to false")
        void stop_transitionsRunningFlagToFalse() { 
            TestAsyncClient client = new TestAsyncClient(true); 

            runPromise(client::stop); 

            assertThat(client.isRunning()).isFalse(); 
        }

        @Test
        @DisplayName("markStopped() returns true on first transition, false if already stopped")
        void markStopped_idempotentStateTransition() { 
            TestAsyncClient client = new TestAsyncClient(true); 

            boolean firstTransition  = client.markStopped(); 
            boolean secondTransition = client.markStopped(); 

            assertThat(firstTransition).isTrue(); 
            assertThat(secondTransition).isFalse(); 
            assertThat(client.isRunning()).isFalse(); 
        }
    }

    // ── Health check ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("healthCheck()")
    class HealthCheck {

        @Test
        @DisplayName("healthCheck() returns false when not running")
        void healthCheck_returnsFalseWhenNotRunning() { 
            TestAsyncClient client = new TestAsyncClient(); 

            boolean healthy = runPromise(client::healthCheck); 

            assertThat(healthy).isFalse(); 
        }

        @Test
        @DisplayName("healthCheck() returns true when running")
        void healthCheck_returnsTrueWhenRunning() { 
            TestAsyncClient client = new TestAsyncClient(true); 

            boolean healthy = runPromise(client::healthCheck); 

            assertThat(healthy).isTrue(); 
        }
    }

    // ── Start → Stop → Start roundtrip ────────────────────────────────────────

    @Nested
    @DisplayName("start → stop → restart roundtrip")
    class StartStopRestart {

        @Test
        @DisplayName("client can be restarted after stopping")
        void clientCanBeRestartedAfterStopping() { 
            TestAsyncClient client = new TestAsyncClient(); 

            runPromise(client::start); 
            assertThat(client.isRunning()).isTrue(); 

            runPromise(client::stop); 
            assertThat(client.isRunning()).isFalse(); 

            runPromise(client::start); 
            assertThat(client.isRunning()).isTrue(); 

            assertThat(client.getStartCount()).isEqualTo(2); 
            assertThat(client.getStopCount()).isEqualTo(1); 
        }
    }

    // ── setRunning() force override ─────────────────────────────────────────── 

    @Test
    @DisplayName("setRunning() forces state unconditionally")
    void setRunning_forcesStateUnconditionally() { 
        TestAsyncClient client = new TestAsyncClient(); 

        client.setRunning(true); 
        assertThat(client.isRunning()).isTrue(); 

        client.setRunning(false); 
        assertThat(client.isRunning()).isFalse(); 
    }
}
