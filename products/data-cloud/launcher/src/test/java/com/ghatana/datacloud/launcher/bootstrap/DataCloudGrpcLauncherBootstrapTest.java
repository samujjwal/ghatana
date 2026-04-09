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
@DisplayName("DataCloudGrpcLauncherBootstrap")
class DataCloudGrpcLauncherBootstrapTest {

    @Test
    @DisplayName("registers shutdown hook after successful startup")
    void registersShutdownHookAfterSuccessfulStartup() {
        Logger log = mock(Logger.class);
        AtomicReference<Thread> registeredHook = new AtomicReference<>();
        AtomicInteger stopCalls = new AtomicInteger();

        DataCloudGrpcLauncherBootstrap.startTransport(
                log,
                () -> {},
                stopCalls::incrementAndGet,
                registeredHook::set);

        assertThat(registeredHook.get()).isNotNull();
        registeredHook.get().run();
        assertThat(stopCalls.get()).isEqualTo(1);
        verify(log).info("Stopping gRPC server...");
    }

    @Test
    @DisplayName("wraps startup failures in typed transport exception")
    void wrapsStartupFailuresInTypedTransportException() {
        Logger log = mock(Logger.class);

        assertThatThrownBy(() ->
                DataCloudGrpcLauncherBootstrap.startTransport(
                        log,
                        () -> {
                            throw new IllegalStateException("boom");
                        },
                        () -> {},
                        hook -> {}))
                .isInstanceOf(com.ghatana.datacloud.launcher.DataCloudTransportStartupException.class)
                .hasMessage("Failed to start gRPC server")
                .hasCauseInstanceOf(IllegalStateException.class);

        verify(log).error(eq("Failed to start gRPC server"), any(IllegalStateException.class));
    }
}
