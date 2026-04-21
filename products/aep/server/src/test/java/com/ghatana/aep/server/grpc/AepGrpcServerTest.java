/*
 * Copyright (c) 2026 Ghatana Inc.
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
    void setUp() throws Exception {
        int port = findFreePort();
        agentRegistry = new InMemoryAgentRegistry();
        server = new AepGrpcServer(agentRegistry, port);
        server.start();
        channel = ManagedChannelBuilder.forAddress("127.0.0.1", port)
            .usePlaintext()
            .build();
        stub = AgentManagementServiceProtoGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void tearDown() {
        if (channel != null) {
            channel.shutdownNow();
        }
        if (server != null) {
            server.close();
        }
    }

    @Test
    @DisplayName("getAgent returns the registered agent manifest fields")
    void getAgentReturnsRegisteredManifest() {
        AgentManifestProto created = stub.createAgent(CreateAgentRequestProto.newBuilder()
            .setAgent(agentManifest("agent-alpha", "Agent Alpha", "2.3.4", "tests gRPC getAgent"))
            .build());

        AgentManifestProto fetched = stub.getAgent(GetAgentRequestProto.newBuilder()
            .setId("agent-alpha")
            .build());

        assertThat(fetched.getMetadata().getId()).isEqualTo(created.getMetadata().getId());
        assertThat(fetched.getMetadata().getName()).isEqualTo("Agent Alpha");
        assertThat(fetched.getMetadata().getVersion()).isEqualTo("2.3.4");
        assertThat(fetched.getMetadata().getDescription()).isEqualTo("tests gRPC getAgent");
    }

    @Test
    @DisplayName("listAgents returns actual manifest data for all registered agents")
    void listAgentsReturnsActualManifestData() {
        stub.createAgent(CreateAgentRequestProto.newBuilder()
            .setAgent(agentManifest("agent-zulu", "Agent Zulu", "1.0.1", "zulu description"))
            .build());
        stub.createAgent(CreateAgentRequestProto.newBuilder()
            .setAgent(agentManifest("agent-bravo", "Agent Bravo", "1.2.0", "bravo description"))
            .build());

        ListAgentsResponseProto response = stub.listAgents(ListAgentsRequestProto.getDefaultInstance());

        assertThat(response.getAgentsList())
            .extracting(agent -> agent.getMetadata().getId())
            .containsExactly("agent-bravo", "agent-zulu");
        assertThat(response.getAgentsList())
            .extracting(agent -> agent.getMetadata().getName())
            .containsExactly("Agent Bravo", "Agent Zulu");
        assertThat(response.getAgentsList())
            .extracting(agent -> agent.getMetadata().getDescription())
            .containsExactly("bravo description", "zulu description");
    }

    @Test
    @DisplayName("createAgent registers manifest-only placeholder agents as non-executable")
    void createAgentRegistersNonExecutablePlaceholderMetadata() {
        stub.createAgent(CreateAgentRequestProto.newBuilder()
            .setAgent(agentManifest("agent-shadow", "Agent Shadow", "1.0.0", "manifest only"))
            .build());

        Optional<TypedAgent<Object, Object>> resolved = awaitResolvedAgent("agent-shadow");

        assertThat(resolved).isPresent();
        assertThat(resolved.get().descriptor().getMetadata())
            .containsEntry(AepGrpcServer.EXECUTABLE_METADATA_KEY, false)
            .containsEntry(
                AepGrpcServer.REGISTRATION_MODE_METADATA_KEY,
                AepGrpcServer.REGISTRATION_MODE_MANIFEST_ONLY
            );
    }

    @Test
    @DisplayName("createAgent returns correlation and trace metadata headers")
    void createAgentReturnsCorrelationAndTraceHeaders() {
        AtomicReference<Metadata> responseHeaders = new AtomicReference<>();
        AtomicReference<Metadata> responseTrailers = new AtomicReference<>();

        AgentManagementServiceProtoGrpc.AgentManagementServiceProtoBlockingStub tracedStub =
            AgentManagementServiceProtoGrpc.newBlockingStub(
                ClientInterceptors.intercept(
                    channel,
                    MetadataUtils.newCaptureMetadataInterceptor(responseHeaders, responseTrailers)));

        tracedStub.createAgent(CreateAgentRequestProto.newBuilder()
            .setAgent(agentManifest("agent-trace", "Agent Trace", "1.0.0", "trace test"))
            .build());

        assertThat(responseHeaders.get().get(Metadata.Key.of("x-correlation-id", Metadata.ASCII_STRING_MARSHALLER)))
            .isNotBlank();
        assertThat(responseHeaders.get().get(Metadata.Key.of("traceparent", Metadata.ASCII_STRING_MARSHALLER)))
            .matches("00-[0-9a-f]{32}-[0-9a-f]{16}-0[01]");
    }

    @Test
    @DisplayName("createAgent preserves inbound correlation id and trace id")
    void createAgentPreservesInboundCorrelationAndTraceId() {
        Metadata requestHeaders = new Metadata();
        AtomicReference<Metadata> responseHeaders = new AtomicReference<>();
        AtomicReference<Metadata> responseTrailers = new AtomicReference<>();
        String traceId = "fedcba9876543210fedcba9876543210";

        requestHeaders.put(Metadata.Key.of("x-correlation-id", Metadata.ASCII_STRING_MARSHALLER), "corr-grpc-456");
        requestHeaders.put(Metadata.Key.of("traceparent", Metadata.ASCII_STRING_MARSHALLER),
            "00-" + traceId + "-1111222233334444-01");
        requestHeaders.put(Metadata.Key.of("tracestate", Metadata.ASCII_STRING_MARSHALLER), "vendor=grpc");

        AgentManagementServiceProtoGrpc.AgentManagementServiceProtoBlockingStub tracedStub =
            AgentManagementServiceProtoGrpc.newBlockingStub(
                ClientInterceptors.intercept(
                    channel,
                    MetadataUtils.newAttachHeadersInterceptor(requestHeaders),
                    MetadataUtils.newCaptureMetadataInterceptor(responseHeaders, responseTrailers)));

        tracedStub.createAgent(CreateAgentRequestProto.newBuilder()
            .setAgent(agentManifest("agent-trace-2", "Agent Trace Two", "1.0.1", "trace roundtrip"))
            .build());

        assertThat(responseHeaders.get().get(Metadata.Key.of("x-correlation-id", Metadata.ASCII_STRING_MARSHALLER)))
            .isEqualTo("corr-grpc-456");
        assertThat(responseHeaders.get().get(Metadata.Key.of("traceparent", Metadata.ASCII_STRING_MARSHALLER)))
            .startsWith("00-" + traceId + "-");
        assertThat(responseHeaders.get().get(Metadata.Key.of("tracestate", Metadata.ASCII_STRING_MARSHALLER)))
            .isEqualTo("vendor=grpc");
    }

    @SuppressWarnings("unchecked")
    private Optional<TypedAgent<Object, Object>> awaitResolvedAgent(String agentId) {
        return (Optional<TypedAgent<Object, Object>>) (Optional<?>) runPromise(() -> agentRegistry.resolve(agentId));
    }

    private static AgentManifestProto agentManifest(String id, String name, String version, String description) {
        return AgentManifestProto.newBuilder()
            .setApiVersion("ghatana.contracts/agent/v1")
            .setKind("AgentManifest")
            .setMetadata(MetadataProto.newBuilder()
                .setId(id)
                .setName(name)
                .setVersion(version)
                .setDescription(description)
                .build())
            .build();
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket ss = new ServerSocket(0)) {
            return ss.getLocalPort();
        }
    }
}
