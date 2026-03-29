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
    void tracksRunningStateTransitions() {
        TestManagedAsyncClient client = new TestManagedAsyncClient();

        assertThat(client.isRunning()).isFalse();
        assertThat(runPromise(client::start)).isNull();
        assertThat(client.isRunning()).isTrue();
        assertThat(runPromise(client::stop)).isNull();
        assertThat(client.isRunning()).isFalse();
    }

    @Test
    @DisplayName("markStarted and markStopped are idempotent")
    void lifecycleMarkersAreIdempotent() {
        TestManagedAsyncClient client = new TestManagedAsyncClient();

        assertThat(client.markStartedForTest()).isTrue();
        assertThat(client.markStartedForTest()).isFalse();
        assertThat(client.markStoppedForTest()).isTrue();
        assertThat(client.markStoppedForTest()).isFalse();
    }

    private static final class TestManagedAsyncClient extends ManagedAsyncClient {
        @Override
        public Promise<Void> start() {
            markStarted();
            return Promise.complete();
        }

        @Override
        public Promise<Void> stop() {
            markStopped();
            return Promise.complete();
        }

        boolean markStartedForTest() {
            return markStarted();
        }

        boolean markStoppedForTest() {
            return markStopped();
        }
    }
}