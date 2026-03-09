/*
 * Copyright (c) 2025 Ghatana Platform Contributors
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

package com.ghatana.yappc.api.grpc;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.protobuf.services.ProtoReflectionService;
import com.ghatana.yappc.api.YappcApi;
import com.ghatana.yappc.api.YappcConfig;
import com.ghatana.yappc.api.grpc.service.*;
import com.ghatana.yappc.api.grpc.PluginManagementService;
import com.ghatana.yappc.core.plugin.PluginManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * YAPPC gRPC Server.
 *
 * @doc.type class
 * @doc.purpose gRPC server for YAPPC operations
 * @doc.layer platform
 * @doc.pattern Server
 */
public final class YappcGrpcServer {

    private static final Logger LOG = LoggerFactory.getLogger(YappcGrpcServer.class);

    private final YappcGrpcServerConfig config;
    private final YappcApi api;
    private Server server;

    private YappcGrpcServer(YappcGrpcServerConfig config) {
        this.config = config;
        this.api = YappcApi.create(YappcConfig.builder()
                .packsPath(config.getPacksPath())
                .workspacePath(config.getWorkspacePath())
                .build());
    }

    /**
     * Create a new gRPC server with default configuration.
     */
    public static YappcGrpcServer create() {
        return create(YappcGrpcServerConfig.defaults());
    }

    /**
     * Create a new gRPC server with custom configuration.
     */
    public static YappcGrpcServer create(YappcGrpcServerConfig config) {
        return new YappcGrpcServer(config);
    }

    /**
     * Start the gRPC server.
     */
    public YappcGrpcServer start() throws IOException {
        ServerBuilder<?> builder = ServerBuilder.forPort(config.getPort());

        // Register services
        builder.addService(new PackServiceImpl(api));
        builder.addService(new ProjectServiceImpl(api));
        builder.addService(new TemplateServiceImpl(api));
        builder.addService(new DependencyServiceImpl(api));
        // Plugin management over gRPC
        builder.addService(new PluginManagementService(new PluginManager()));

        // Enable reflection for tools like grpcurl
        if (config.isEnableReflection()) {
            builder.addService(ProtoReflectionService.newInstance());
        }

        // Configure server
        builder.maxInboundMessageSize(config.getMaxMessageSize());

        server = builder.build().start();

        LOG.info("YAPPC gRPC server started on port {}", config.getPort());

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Shutting down gRPC server...");
            try {
                stop();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));

        return this;
    }

    /**
     * Stop the gRPC server.
     */
    public void stop() throws InterruptedException {
        if (server != null) {
            server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
            LOG.info("YAPPC gRPC server stopped");
        }
        api.shutdown();
    }

    /**
     * Wait for the server to terminate.
     */
    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    /**
     * Get the port the server is running on.
     */
    public int getPort() {
        return server != null ? server.getPort() : config.getPort();
    }

    /**
     * Check if the server is running.
     */
    public boolean isRunning() {
        return server != null && !server.isShutdown();
    }

    /**
     * Get the underlying API instance.
     */
    public YappcApi getApi() {
        return api;
    }

    /**
     * Main entry point for standalone server.
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        int port = 50051;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }

        YappcGrpcServerConfig config = YappcGrpcServerConfig.builder()
                .port(port)
                .enableReflection(true)
                .build();

        YappcGrpcServer server = YappcGrpcServer.create(config).start();
        server.blockUntilShutdown();
    }
}
