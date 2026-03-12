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
package com.ghatana.aep.launcher.grpc;

import com.ghatana.agent.registry.AgentFrameworkRegistry;
import com.ghatana.orchestrator.grpc.AgentGrpcService;
import com.ghatana.platform.governance.security.TenantGrpcInterceptor;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Lifecycle wrapper for the AEP gRPC server.
 *
 * <p>Starts a single-port gRPC server exposing:
 * <ul>
 *   <li>{@link AgentGrpcService.ManagementService} — {@code AgentManagementServiceProto}
 *       (create, get, update, delete, list agents)</li>
 *   <li>{@link AgentGrpcService.ExecutionService} — {@code AgentExecutionServiceProto}
 *       (execute agents, stream results)</li>
 * </ul>
 *
 * <p>Multi-tenant isolation is enforced by {@link TenantGrpcInterceptor#lenient()},
 * which extracts {@code x-tenant-id}, {@code x-principal}, and {@code x-roles}
 * from request metadata and stores them in {@link com.ghatana.platform.governance.security.TenantContext}.
 *
 * <p>The port is resolved from the {@code AEP_GRPC_PORT} environment variable,
 * defaulting to {@value #DEFAULT_PORT}.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * AgentFrameworkRegistry registry = new InMemoryAgentFrameworkRegistry();
 * AepGrpcServer server = new AepGrpcServer(registry);
 * server.start();
 * server.awaitTermination();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose gRPC server bootstrap for AEP (agent management + execution) with tenant isolation
 * @doc.layer product
 * @doc.pattern Service
 * @since 2.0.0
 */
public final class AepGrpcServer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AepGrpcServer.class);

    /** Default gRPC port for AEP. Override via {@code AEP_GRPC_PORT} env var. */
    static final int DEFAULT_PORT = 9091;

    private final Server server;

    /**
     * Constructs the gRPC server bound to the specified port, backed by the given registry.
     *
     * <p>{@link TenantGrpcInterceptor#lenient()} is applied globally — calls
     * without {@code x-tenant-id} metadata are allowed but will have no tenant context.
     *
     * @param agentRegistry registry for agent storage and lookup
     * @param port           TCP port to bind
     */
    public AepGrpcServer(AgentFrameworkRegistry agentRegistry, int port) {
        Objects.requireNonNull(agentRegistry, "agentRegistry");
        AgentGrpcService grpcService = new AgentGrpcService(agentRegistry);
        this.server = ServerBuilder.forPort(port)
                .intercept(TenantGrpcInterceptor.lenient())
                .addService(grpcService.getManagementService())
                .addService(grpcService.getExecutionService())
                .build();
    }

    /**
     * Constructs the gRPC server using the port from {@code AEP_GRPC_PORT} or {@value #DEFAULT_PORT}.
     *
     * @param agentRegistry registry for agent storage and lookup
     */
    public AepGrpcServer(AgentFrameworkRegistry agentRegistry) {
        this(agentRegistry, resolvePort());
    }

    /**
     * Starts the gRPC server.
     *
     * @throws IOException if the port cannot be bound
     */
    public void start() throws IOException {
        server.start();
        log.info("AEP gRPC server started on port {}", server.getPort());
    }

    /**
     * Returns the port on which the server listens (after {@link #start()} has been called).
     *
     * @return the actual bound port
     */
    public int getPort() {
        return server.getPort();
    }

    /**
     * Initiates a graceful shutdown, waiting up to 30 seconds for in-flight RPCs to complete.
     */
    @Override
    public void close() {
        log.info("Stopping AEP gRPC server...");
        server.shutdown();
        try {
            if (!server.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("AEP gRPC server did not terminate within 30 s — forcing shutdown");
                server.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            server.shutdownNow();
        }
        log.info("AEP gRPC server stopped");
    }

    /**
     * Blocks the calling thread until the server shuts down (useful for main-thread keep-alive).
     *
     * @throws InterruptedException if interrupted while waiting
     */
    public void awaitTermination() throws InterruptedException {
        server.awaitTermination();
    }

    // ─────────────────────────────────────────────────────────────────────────

    private static int resolvePort() {
        String env = System.getenv("AEP_GRPC_PORT");
        if (env != null && !env.isBlank()) {
            try {
                return Integer.parseInt(env.trim());
            } catch (NumberFormatException e) {
                log.warn("Invalid AEP_GRPC_PORT='{}', using default {}", env, DEFAULT_PORT);
            }
        }
        return DEFAULT_PORT;
    }
}
