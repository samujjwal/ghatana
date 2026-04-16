/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.grpc;

import com.ghatana.agent.registry.InMemoryAgentRegistry;
import com.ghatana.contracts.agent.v1.AgentManagementServiceProtoGrpc;
import com.ghatana.contracts.agent.v1.AgentManifestProto;
import com.ghatana.contracts.agent.v1.CreateAgentRequestProto;
import com.ghatana.contracts.agent.v1.GetAgentRequestProto;
import com.ghatana.contracts.agent.v1.ListAgentsRequestProto;
import com.ghatana.contracts.agent.v1.ListAgentsResponseProto;
import com.ghatana.contracts.agent.v1.MetadataProto;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;

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
class AepGrpcServerTest {

    private AepGrpcServer server;
    private ManagedChannel channel;
    private AgentManagementServiceProtoGrpc.AgentManagementServiceProtoBlockingStub stub;

    @BeforeEach
    void setUp() throws Exception {
        int port = findFreePort();
        server = new AepGrpcServer(new InMemoryAgentRegistry(), port);
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
