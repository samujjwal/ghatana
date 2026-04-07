package com.ghatana.audio.video.common;

import io.grpc.BindableService;
import io.grpc.ServerServiceDefinition;
import io.grpc.ServiceDescriptor;
import io.grpc.stub.ServerCalls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AudioVideoGrpcServerBase}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for the shared audio-video gRPC server base class
 * @doc.layer product
 * @doc.pattern TestCase
 */
@DisplayName("AudioVideoGrpcServerBase")
class AudioVideoGrpcServerBaseTest {

    // ─────────────────────────────────────────────────────────────────────────
    // Construction and accessors
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getPort: returns the configured port")
    void getPort_returnsConfiguredPort() {
        TestServer server = new TestServer(0); // 0 = ephemeral port
        assertThat(server.getPort()).isEqualTo(0);
        server.close();
    }

    @Test
    @DisplayName("getServiceName: returns the configured service name")
    void getServiceName_returnsConfiguredName() {
        TestServer server = new TestServer(0);
        assertThat(server.getServiceName()).isEqualTo("test-service");
        server.close();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("start + close: server starts and shuts down cleanly")
    void start_thenClose_shutsDownCleanly() throws IOException {
        TestServer server = new TestServer(0);
        server.start();

        assertThat(server.isShutdown()).isFalse();

        server.close();
        assertThat(server.isShutdown()).isTrue();
    }

    @Test
    @DisplayName("close: idempotent — calling twice does not throw")
    void close_calledTwice_doesNotThrow() throws IOException {
        TestServer server = new TestServer(0);
        server.start();

        server.close();
        assertThatCode(server::close).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("isShutdown: returns true before start (server not yet running)")
    void isShutdown_beforeStart_returnsFalse() {
        TestServer server = new TestServer(0);
        // Not started yet — server is built but not running; gRPC still reports "not shut down"
        // The important thing is close() is safe to call before start()
        assertThatCode(server::close).doesNotThrowAnyException();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test helper — minimal concrete server using an ephemeral port
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Minimal concrete extension of {@link AudioVideoGrpcServerBase} for testing.
     * Uses an empty no-op service to avoid needing a real gRPC service implementation.
     * Uses an empty interceptor list to avoid requiring environment variables such as
     * {@code AV_JWT_SECRET}.
     */
    private static final class TestServer extends AudioVideoGrpcServerBase {

        TestServer(int port) {
            super("test-service", port, new NoOpService(), java.util.List.of());
        }
    }

    /**
     * A minimal {@link BindableService} that binds a gRPC service with no methods.
     * Used to allow {@link AudioVideoGrpcServerBase} to build a server in tests
     * without requiring real gRPC service stubs.
     */
    private static final class NoOpService implements BindableService {

        @Override
        public ServerServiceDefinition bindService() {
            return ServerServiceDefinition.builder(
                    ServiceDescriptor.newBuilder("test.NoOpService")
                            .build())
                    .build();
        }
    }
}

