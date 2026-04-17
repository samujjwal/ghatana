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

import com.ghatana.agent.AgentConfig;
import com.ghatana.agent.AgentDescriptor;
import com.ghatana.agent.AgentResult;
import com.ghatana.agent.AgentType;
import com.ghatana.agent.TypedAgent;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.spi.AgentRegistry;
import com.ghatana.contracts.agent.v1.AgentManagementServiceProtoGrpc;
import com.ghatana.contracts.agent.v1.AgentManifestProto;
import com.ghatana.contracts.agent.v1.CreateAgentRequestProto;
import com.ghatana.contracts.agent.v1.DeleteAgentRequestProto;
import com.ghatana.contracts.agent.v1.GetAgentRequestProto;
import com.ghatana.contracts.agent.v1.ListAgentsRequestProto;
import com.ghatana.contracts.agent.v1.ListAgentsResponseProto;
import com.ghatana.contracts.agent.v1.MetadataProto;
import com.google.protobuf.Empty;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.activej.promise.Promises;
import com.ghatana.platform.health.HealthStatus;
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
    static final String EXECUTABLE_METADATA_KEY = "executable";
    static final String REGISTRATION_MODE_METADATA_KEY = "registrationMode";
    static final String REGISTRATION_MODE_MANIFEST_ONLY = "manifest-only";

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
            if (!request.hasAgent()) {
                responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("request.agent is required").asRuntimeException());
                return;
            }
            AgentManifestProto manifest   = request.getAgent();
            MetadataProto      metadata   = manifest.hasMetadata() ? manifest.getMetadata()
                : MetadataProto.getDefaultInstance();

            String agentId  = metadata.getId().isBlank()
                ? java.util.UUID.randomUUID().toString() : metadata.getId();
            String name     = metadata.getName().isBlank() ? agentId : metadata.getName();
            String version  = metadata.getVersion().isBlank() ? "1.0.0" : metadata.getVersion();

            AgentDescriptor descriptor = AgentDescriptor.builder()
                .agentId(agentId)
                .name(name)
                .version(version)
                .description(metadata.getDescription())
                .type(AgentType.PROBABILISTIC)
                .metadata(java.util.Map.of(
                    EXECUTABLE_METADATA_KEY, false,
                    REGISTRATION_MODE_METADATA_KEY, REGISTRATION_MODE_MANIFEST_ONLY
                ))
                .build();

            AgentConfig config = AgentConfig.builder()
                .agentId(agentId)
                .type(AgentType.PROBABILISTIC)
                .version(version)
                .build();

            // Placeholder TypedAgent — satisfies the registry contract;
            // actual processing is dispatched via AgentExecutionService.
            TypedAgent<Object, Object> placeholderAgent = new PlaceholderAgent(descriptor);

            registry.register(placeholderAgent, config)
                .whenResult(v -> {
                    // Build the response manifest with the assigned ID stamped back
                    AgentManifestProto response = AgentManifestProto.newBuilder()
                        .setApiVersion("ghatana.contracts/agent/v1")
                        .setKind("AgentManifest")
                        .setMetadata(MetadataProto.newBuilder()
                            .setId(agentId)
                            .setName(name)
                            .setVersion(version)
                            .setDescription(metadata.getDescription())
                            .build())
                        .build();
                    responseObserver.onNext(response);
                    responseObserver.onCompleted();
                    log.info("[grpc] Created agent agentId={}", agentId);
                })
                .whenException(e -> responseObserver.onError(
                    Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException()));
        }

        @Override
        public void getAgent(GetAgentRequestProto request,
                             StreamObserver<AgentManifestProto> responseObserver) {
            String agentId = request.getId();
            registry.resolve(agentId)
                .whenResult(optAgent -> {
                    if (optAgent.isPresent()) {
                        responseObserver.onNext(toManifest(optAgent.get()));
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
                .then(ids -> Promises.toList(ids.stream()
                    .sorted()
                    .map(id -> registry.resolve(id)
                        .map(optAgent -> optAgent.map(AgentManagementService::toManifest).orElse(null)))
                    .toList()))
                .whenResult(manifests -> {
                    ListAgentsResponseProto.Builder builder = ListAgentsResponseProto.newBuilder();
                    manifests.stream()
                        .filter(Objects::nonNull)
                        .forEach(builder::addAgents);
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

        private static AgentManifestProto toManifest(TypedAgent<?, ?> agent) {
            AgentDescriptor descriptor = agent.descriptor();
            MetadataProto.Builder metadata = MetadataProto.newBuilder()
                .setId(defaultString(descriptor.getAgentId()))
                .setName(defaultString(descriptor.getName()))
                .setVersion(defaultString(descriptor.getVersion()));

            if (descriptor.getDescription() != null) {
                metadata.setDescription(descriptor.getDescription());
            }

            return AgentManifestProto.newBuilder()
                .setApiVersion("ghatana.contracts/agent/v1")
                .setKind("AgentManifest")
                .setMetadata(metadata)
                .build();
        }

        private static String defaultString(String value) {
            return value == null ? "" : value;
        }
    }

    /**
     * Minimal placeholder {@link TypedAgent} created on-the-fly when a manifest is
     * submitted via gRPC. Actual execution is dispatched by {@code AgentExecutionService}.
     */
    private static final class PlaceholderAgent implements TypedAgent<Object, Object> {

        private final AgentDescriptor agentDescriptor;

        PlaceholderAgent(AgentDescriptor descriptor) {
            this.agentDescriptor = descriptor;
        }

        @Override
        public AgentDescriptor descriptor() { return agentDescriptor; }

        @Override
        public io.activej.promise.Promise<Void> initialize(AgentConfig config) {
            return io.activej.promise.Promise.complete();
        }

        @Override
        public io.activej.promise.Promise<Void> shutdown() {
            return io.activej.promise.Promise.complete();
        }

        @Override
        public io.activej.promise.Promise<HealthStatus> healthCheck() {
            return io.activej.promise.Promise.of(HealthStatus.healthy("Placeholder agent is healthy"));
        }

        @Override
        public io.activej.promise.Promise<AgentResult<Object>> process(AgentContext ctx, Object input) {
            return io.activej.promise.Promise.ofException(
                new UnsupportedOperationException(
                    "PlaceholderAgent cannot be executed directly. Use AgentExecutionService."));
        }
    }
}
