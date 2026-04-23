/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.aep.server.grpc;

import com.ghatana.agent.registry.InMemoryAgentRegistry;
import com.ghatana.agent.TypedAgent;
import com.ghatana.contracts.agent.v1.AgentManagementServiceProtoGrpc;
import com.ghatana.contracts.agent.v1.AgentManifestProto;
import com.ghatana.contracts.agent.v1.CreateAgentRequestProto;
import com.ghatana.contracts.agent.v1.GetAgentRequestProto;
import com.ghatana.contracts.agent.v1.ListAgentsRequestProto;
import com.ghatana.contracts.agent.v1.ListAgentsResponseProto;
import com.ghatana.contracts.agent.v1.MetadataProto;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.grpc.ClientInterceptors;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the AEP gRPC agent management server.
 *
 * @doc.type class
 * @doc.purpose Verify gRPC agent manifest responses are populated from the registry
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@DisplayName("AepGrpcServer")
class AepGrpcServerTest extends EventloopTestBase {

    private AepGrpcServer server;
    private InMemoryAgentRegistry agentRegistry;
    private ManagedChannel channel;
    private AgentManagementServiceProtoGrpc.AgentManagementServiceProtoBlockingStub stub;

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        int port = findFreePort(); // GH-90000
        agentRegistry = new InMemoryAgentRegistry(); // GH-90000
        server = new AepGrpcServer(agentRegistry, port); // GH-90000
        server.start(); // GH-90000
        channel = ManagedChannelBuilder.forAddress("127.0.0.1", port) // GH-90000
            .usePlaintext() // GH-90000
            .build(); // GH-90000
        stub = AgentManagementServiceProtoGrpc.newBlockingStub(channel); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (channel != null) { // GH-90000
            channel.shutdownNow(); // GH-90000
        }
        if (server != null) { // GH-90000
            server.close(); // GH-90000
        }
    }

    @Test
    @DisplayName("getAgent returns the registered agent manifest fields")
    void getAgentReturnsRegisteredManifest() { // GH-90000
        AgentManifestProto created = stub.createAgent(CreateAgentRequestProto.newBuilder() // GH-90000
            .setAgent(agentManifest("agent-alpha", "Agent Alpha", "2.3.4", "tests gRPC getAgent")) // GH-90000
            .build()); // GH-90000

        AgentManifestProto fetched = stub.getAgent(GetAgentRequestProto.newBuilder() // GH-90000
            .setId("agent-alpha")
            .build()); // GH-90000

        assertThat(fetched.getMetadata().getId()).isEqualTo(created.getMetadata().getId()); // GH-90000
        assertThat(fetched.getMetadata().getName()).isEqualTo("Agent Alpha");
        assertThat(fetched.getMetadata().getVersion()).isEqualTo("2.3.4");
        assertThat(fetched.getMetadata().getDescription()).isEqualTo("tests gRPC getAgent");
    }

    @Test
    @DisplayName("listAgents returns actual manifest data for all registered agents")
    void listAgentsReturnsActualManifestData() { // GH-90000
        stub.createAgent(CreateAgentRequestProto.newBuilder() // GH-90000
            .setAgent(agentManifest("agent-zulu", "Agent Zulu", "1.0.1", "zulu description")) // GH-90000
            .build()); // GH-90000
        stub.createAgent(CreateAgentRequestProto.newBuilder() // GH-90000
            .setAgent(agentManifest("agent-bravo", "Agent Bravo", "1.2.0", "bravo description")) // GH-90000
            .build()); // GH-90000

        ListAgentsResponseProto response = stub.listAgents(ListAgentsRequestProto.getDefaultInstance()); // GH-90000

        assertThat(response.getAgentsList()) // GH-90000
            .extracting(agent -> agent.getMetadata().getId()) // GH-90000
            .containsExactly("agent-bravo", "agent-zulu"); // GH-90000
        assertThat(response.getAgentsList()) // GH-90000
            .extracting(agent -> agent.getMetadata().getName()) // GH-90000
            .containsExactly("Agent Bravo", "Agent Zulu"); // GH-90000
        assertThat(response.getAgentsList()) // GH-90000
            .extracting(agent -> agent.getMetadata().getDescription()) // GH-90000
            .containsExactly("bravo description", "zulu description"); // GH-90000
    }

    @Test
    @DisplayName("createAgent registers manifest-only placeholder agents as non-executable")
    void createAgentRegistersNonExecutablePlaceholderMetadata() { // GH-90000
        stub.createAgent(CreateAgentRequestProto.newBuilder() // GH-90000
            .setAgent(agentManifest("agent-shadow", "Agent Shadow", "1.0.0", "manifest only")) // GH-90000
            .build()); // GH-90000

        Optional<TypedAgent<Object, Object>> resolved = awaitResolvedAgent("agent-shadow");

        assertThat(resolved).isPresent(); // GH-90000
        assertThat(resolved.get().descriptor().getMetadata()) // GH-90000
            .containsEntry(AepGrpcServer.EXECUTABLE_METADATA_KEY, false) // GH-90000
            .containsEntry( // GH-90000
                AepGrpcServer.REGISTRATION_MODE_METADATA_KEY,
                AepGrpcServer.REGISTRATION_MODE_MANIFEST_ONLY
            );
    }

    @Test
    @DisplayName("createAgent returns correlation and trace metadata headers")
    void createAgentReturnsCorrelationAndTraceHeaders() { // GH-90000
        AtomicReference<Metadata> responseHeaders = new AtomicReference<>(); // GH-90000
        AtomicReference<Metadata> responseTrailers = new AtomicReference<>(); // GH-90000

        AgentManagementServiceProtoGrpc.AgentManagementServiceProtoBlockingStub tracedStub =
            AgentManagementServiceProtoGrpc.newBlockingStub( // GH-90000
                ClientInterceptors.intercept( // GH-90000
                    channel,
                    MetadataUtils.newCaptureMetadataInterceptor(responseHeaders, responseTrailers))); // GH-90000

        tracedStub.createAgent(CreateAgentRequestProto.newBuilder() // GH-90000
            .setAgent(agentManifest("agent-trace", "Agent Trace", "1.0.0", "trace test")) // GH-90000
            .build()); // GH-90000

        assertThat(responseHeaders.get().get(Metadata.Key.of("x-correlation-id", Metadata.ASCII_STRING_MARSHALLER))) // GH-90000
            .isNotBlank(); // GH-90000
        assertThat(responseHeaders.get().get(Metadata.Key.of("traceparent", Metadata.ASCII_STRING_MARSHALLER))) // GH-90000
            .matches("00-[0-9a-f]{32}-[0-9a-f]{16}-0[01]");
    }

    @Test
    @DisplayName("createAgent preserves inbound correlation id and trace id")
    void createAgentPreservesInboundCorrelationAndTraceId() { // GH-90000
        Metadata requestHeaders = new Metadata(); // GH-90000
        AtomicReference<Metadata> responseHeaders = new AtomicReference<>(); // GH-90000
        AtomicReference<Metadata> responseTrailers = new AtomicReference<>(); // GH-90000
        String traceId = "fedcba9876543210fedcba9876543210";

        requestHeaders.put(Metadata.Key.of("x-correlation-id", Metadata.ASCII_STRING_MARSHALLER), "corr-grpc-456"); // GH-90000
        requestHeaders.put(Metadata.Key.of("traceparent", Metadata.ASCII_STRING_MARSHALLER), // GH-90000
            "00-" + traceId + "-1111222233334444-01");
        requestHeaders.put(Metadata.Key.of("tracestate", Metadata.ASCII_STRING_MARSHALLER), "vendor=grpc"); // GH-90000

        AgentManagementServiceProtoGrpc.AgentManagementServiceProtoBlockingStub tracedStub =
            AgentManagementServiceProtoGrpc.newBlockingStub( // GH-90000
                ClientInterceptors.intercept( // GH-90000
                    channel,
                    MetadataUtils.newAttachHeadersInterceptor(requestHeaders), // GH-90000
                    MetadataUtils.newCaptureMetadataInterceptor(responseHeaders, responseTrailers))); // GH-90000

        tracedStub.createAgent(CreateAgentRequestProto.newBuilder() // GH-90000
            .setAgent(agentManifest("agent-trace-2", "Agent Trace Two", "1.0.1", "trace roundtrip")) // GH-90000
            .build()); // GH-90000

        assertThat(responseHeaders.get().get(Metadata.Key.of("x-correlation-id", Metadata.ASCII_STRING_MARSHALLER))) // GH-90000
            .isEqualTo("corr-grpc-456");
        assertThat(responseHeaders.get().get(Metadata.Key.of("traceparent", Metadata.ASCII_STRING_MARSHALLER))) // GH-90000
            .startsWith("00-" + traceId + "-"); // GH-90000
        assertThat(responseHeaders.get().get(Metadata.Key.of("tracestate", Metadata.ASCII_STRING_MARSHALLER))) // GH-90000
            .isEqualTo("vendor=grpc");
    }

    @SuppressWarnings("unchecked")
    private Optional<TypedAgent<Object, Object>> awaitResolvedAgent(String agentId) { // GH-90000
        return (Optional<TypedAgent<Object, Object>>) (Optional<?>) runPromise(() -> agentRegistry.resolve(agentId)); // GH-90000
    }

    private static AgentManifestProto agentManifest(String id, String name, String version, String description) { // GH-90000
        return AgentManifestProto.newBuilder() // GH-90000
            .setApiVersion("ghatana.contracts/agent/v1")
            .setKind("AgentManifest")
            .setMetadata(MetadataProto.newBuilder() // GH-90000
                .setId(id) // GH-90000
                .setName(name) // GH-90000
                .setVersion(version) // GH-90000
                .setDescription(description) // GH-90000
                .build()) // GH-90000
            .build(); // GH-90000
    }

    private static int findFreePort() throws IOException { // GH-90000
        try (ServerSocket ss = new ServerSocket(0)) { // GH-90000
            return ss.getLocalPort(); // GH-90000
        }
    }
}
