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
    void bootHarness() throws Exception { // GH-90000
        harness = new ServerTestHarness().start(); // GH-90000
        httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(); // GH-90000
        grpcChannel =
                ManagedChannelBuilder.forTarget(harness.getGrpcAddress()).usePlaintext().build(); // GH-90000
        grpcBlockingStub = PolyfixServiceGrpc.newBlockingStub(grpcChannel); // GH-90000
        objectMapper = new ObjectMapper(); // GH-90000
        additionalSetUp(); // GH-90000
    }

    @AfterEach
    void shutdownHarness() throws Exception { // GH-90000
        try {
            additionalTearDown(); // GH-90000
        } finally {
            if (grpcChannel != null) { // GH-90000
                grpcChannel.shutdown(); // GH-90000
                grpcChannel.awaitTermination(5, TimeUnit.SECONDS); // GH-90000
            }
            if (harness != null) { // GH-90000
                harness.close(); // GH-90000
            }
        }
    }

    /** Hook for subclasses needing extra setup. */
    protected void additionalSetUp() throws Exception {} // GH-90000

    /** Hook for subclasses needing extra teardown. */
    protected void additionalTearDown() throws Exception {} // GH-90000
}
