/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ghatana.aep.server.grpc;

import com.ghatana.agent.spi.AgentRegistry;
import com.ghatana.contracts.agent.v1.AgentManagementServiceProtoGrpc;
import com.ghatana.contracts.agent.v1.AgentManifestProto;
import com.ghatana.contracts.agent.v1.CreateAgentRequestProto;
import com.ghatana.contracts.agent.v1.DeleteAgentRequestProto;
import com.ghatana.contracts.agent.v1.GetAgentRequestProto;
import com.ghatana.contracts.agent.v1.ListAgentsRequestProto;
import com.ghatana.contracts.agent.v1.ListAgentsResponseProto;
import com.google.protobuf.Empty;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * AEP gRPC server that exposes agent management operations.
 *
 * <p>Binds an {@link AgentManagementServiceProtoGrpc.AgentManagementServiceProtoImplBase}
 * backed by the provided {@link AgentRegistry} and starts a Netty gRPC server.
 *
 * @doc.type class
 * @doc.purpose gRPC server for AEP agent management
 * @doc.layer product
 * @doc.pattern Service
 */
public class AepGrpcServer {

    private static final Logger log = LoggerFactory.getLogger(AepGrpcServer.class);

    static final int DEFAULT_PORT = 9090;

    private final AgentRegistry agentRegistry;
    private final int port;
    private Server server;

    /**
     * Creates a gRPC server on the default port ({@value DEFAULT_PORT}).
     *
     * @param agentRegistry the registry backing agent management operations; never {@code null}
     */
    public AepGrpcServer(AgentRegistry agentRegistry) {
        this(agentRegistry, DEFAULT_PORT);
    }

    /**
     * Creates a gRPC server on the given port.
     *
     * @param agentRegistry the registry backing agent management operations; never {@code null}
     * @param port          the port to listen on
     */
    public AepGrpcServer(AgentRegistry agentRegistry, int port) {
        this.agentRegistry = Objects.requireNonNull(agentRegistry, "agentRegistry");
        this.port = port;
    }

    /**
     * Starts the gRPC server. Non-blocking — returns after the server has started listening.
     *
     * @throws IOException if the server cannot bind to the port
     */
    public void start() throws IOException {
        server = ServerBuilder.forPort(port)
            .addService(new AgentManagementService(agentRegistry))
            .build()
            .start();
        log.info("[grpc] AEP gRPC server started on port {}", port);
    }

    /**
     * Returns the port this server is listening on.
     */
    public int getPort() {
        return port;
    }

    /**
     * Shuts down the gRPC server gracefully.
     */
    public void close() {
        if (server != null) {
            try {
                server.shutdown().awaitTermination(10, TimeUnit.SECONDS);
                log.info("[grpc] AEP gRPC server stopped");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                server.shutdownNow();
            }
        }
    }

    /**
     * Minimal agent management service implementation backed by {@link AgentRegistry}.
     */
    private static final class AgentManagementService
            extends AgentManagementServiceProtoGrpc.AgentManagementServiceProtoImplBase {

        private final AgentRegistry registry;

        AgentManagementService(AgentRegistry registry) {
            this.registry = registry;
        }

        @Override
        public void createAgent(CreateAgentRequestProto request,
                                StreamObserver<AgentManifestProto> responseObserver) {
            responseObserver.onError(Status.UNIMPLEMENTED
                .withDescription("createAgent not yet implemented").asRuntimeException());
        }

        @Override
        public void getAgent(GetAgentRequestProto request,
                             StreamObserver<AgentManifestProto> responseObserver) {
            String agentId = request.getId();
            registry.resolve(agentId)
                .whenResult(optAgent -> {
                    if (optAgent.isPresent()) {
                        responseObserver.onNext(AgentManifestProto.getDefaultInstance());
                        responseObserver.onCompleted();
                    } else {
                        responseObserver.onError(Status.NOT_FOUND
                            .withDescription("Agent not found: " + agentId).asRuntimeException());
                    }
                })
                .whenException(e -> responseObserver.onError(
                    Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException()));
        }

        @Override
        public void listAgents(ListAgentsRequestProto request,
                               StreamObserver<ListAgentsResponseProto> responseObserver) {
            registry.listAgentIds()
                .whenResult(ids -> {
                    ListAgentsResponseProto.Builder builder = ListAgentsResponseProto.newBuilder();
                    ids.forEach(id -> builder.addAgents(AgentManifestProto.getDefaultInstance()));
                    responseObserver.onNext(builder.build());
                    responseObserver.onCompleted();
                })
                .whenException(e -> responseObserver.onError(
                    Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException()));
        }

        @Override
        public void deleteAgent(DeleteAgentRequestProto request,
                                StreamObserver<Empty> responseObserver) {
            registry.deregister(request.getId())
                .whenResult(v -> {
                    responseObserver.onNext(Empty.getDefaultInstance());
                    responseObserver.onCompleted();
                })
                .whenException(e -> responseObserver.onError(
                    Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException()));
        }
    }
}
