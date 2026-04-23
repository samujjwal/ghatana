package com.ghatana.platform.core.client;

import io.activej.promise.Promise;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ManagedAsyncClient")
class ManagedAsyncClientTest extends EventloopTestBase {

    @Test
    @DisplayName("tracks running state transitions")
    void tracksRunningStateTransitions() { // GH-90000
        TestManagedAsyncClient client = new TestManagedAsyncClient(); // GH-90000

        assertThat(client.isRunning()).isFalse(); // GH-90000
        assertThat(runPromise(client::start)).isNull(); // GH-90000
        assertThat(client.isRunning()).isTrue(); // GH-90000
        assertThat(runPromise(client::stop)).isNull(); // GH-90000
        assertThat(client.isRunning()).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("markStarted and markStopped are idempotent")
    void lifecycleMarkersAreIdempotent() { // GH-90000
        TestManagedAsyncClient client = new TestManagedAsyncClient(); // GH-90000

        assertThat(client.markStartedForTest()).isTrue(); // GH-90000
        assertThat(client.markStartedForTest()).isFalse(); // GH-90000
        assertThat(client.markStoppedForTest()).isTrue(); // GH-90000
        assertThat(client.markStoppedForTest()).isFalse(); // GH-90000
    }

    private static final class TestManagedAsyncClient extends ManagedAsyncClient {
        @Override
        public Promise<Void> start() { // GH-90000
            markStarted(); // GH-90000
            return Promise.complete(); // GH-90000
        }

        @Override
        public Promise<Void> stop() { // GH-90000
            markStopped(); // GH-90000
            return Promise.complete(); // GH-90000
        }

        boolean markStartedForTest() { // GH-90000
            return markStarted(); // GH-90000
        }

        boolean markStoppedForTest() { // GH-90000
            return markStopped(); // GH-90000
        }
    }
}
