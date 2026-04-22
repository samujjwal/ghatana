package com.ghatana.datacloud.launcher.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

/**
 * @doc.type class
 * @doc.purpose Verifies standalone gRPC bootstrap startup behavior and typed failure wrapping
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudGrpcLauncherBootstrap [GH-90000]")
class DataCloudGrpcLauncherBootstrapTest {

    @Test
    @DisplayName("registers shutdown hook after successful startup [GH-90000]")
    void registersShutdownHookAfterSuccessfulStartup() { // GH-90000
        Logger log = mock(Logger.class); // GH-90000
        AtomicReference<Thread> registeredHook = new AtomicReference<>(); // GH-90000
        AtomicInteger stopCalls = new AtomicInteger(); // GH-90000

        DataCloudGrpcLauncherBootstrap.startTransport( // GH-90000
                log,
                () -> {}, // GH-90000
                stopCalls::incrementAndGet,
                registeredHook::set);

        assertThat(registeredHook.get()).isNotNull(); // GH-90000
    registeredHook.get().start(); // GH-90000
        try {
            registeredHook.get().join(); // GH-90000
        } catch (InterruptedException exception) { // GH-90000
            Thread.currentThread().interrupt(); // GH-90000
            throw new AssertionError("Interrupted while waiting for shutdown hook", exception); // GH-90000
        }
        assertThat(stopCalls.get()).isEqualTo(1); // GH-90000
        verify(log).info("Stopping gRPC server... [GH-90000]");
    }

    @Test
    @DisplayName("wraps startup failures in typed transport exception [GH-90000]")
    void wrapsStartupFailuresInTypedTransportException() { // GH-90000
        Logger log = mock(Logger.class); // GH-90000

        assertThatThrownBy(() -> // GH-90000
                DataCloudGrpcLauncherBootstrap.startTransport( // GH-90000
                        log,
                        () -> { // GH-90000
                            throw new IllegalStateException("boom [GH-90000]");
                        },
                        () -> {}, // GH-90000
                        hook -> {}))
                .isInstanceOf(com.ghatana.datacloud.launcher.DataCloudTransportStartupException.class) // GH-90000
                .hasMessage("Failed to start gRPC server [GH-90000]")
                .hasCauseInstanceOf(IllegalStateException.class); // GH-90000

        verify(log).error(eq("Failed to start gRPC server [GH-90000]"), any(IllegalStateException.class));
    }
}
