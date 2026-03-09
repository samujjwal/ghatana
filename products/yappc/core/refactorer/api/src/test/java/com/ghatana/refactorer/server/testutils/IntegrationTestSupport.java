package com.ghatana.refactorer.server.testutils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.refactorer.api.v1.PolyfixServiceGrpc;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/**
 * Base class for integration tests that require both HTTP and gRPC access to the service-server.
 
 * @doc.type class
 * @doc.purpose Handles integration test support operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public abstract class IntegrationTestSupport extends EventloopTestBase {

    protected ServerTestHarness harness;
    protected HttpClient httpClient;
    protected ManagedChannel grpcChannel;
    protected PolyfixServiceGrpc.PolyfixServiceBlockingStub grpcBlockingStub;
    protected ObjectMapper objectMapper;

    @BeforeEach
    void bootHarness() throws Exception {
        harness = new ServerTestHarness().start();
        httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        grpcChannel =
                ManagedChannelBuilder.forTarget(harness.getGrpcAddress()).usePlaintext().build();
        grpcBlockingStub = PolyfixServiceGrpc.newBlockingStub(grpcChannel);
        objectMapper = new ObjectMapper();
        additionalSetUp();
    }

    @AfterEach
    void shutdownHarness() throws Exception {
        try {
            additionalTearDown();
        } finally {
            if (grpcChannel != null) {
                grpcChannel.shutdown();
                grpcChannel.awaitTermination(5, TimeUnit.SECONDS);
            }
            if (harness != null) {
                harness.close();
            }
        }
    }

    /** Hook for subclasses needing extra setup. */
    protected void additionalSetUp() throws Exception {}

    /** Hook for subclasses needing extra teardown. */
    protected void additionalTearDown() throws Exception {}
}
