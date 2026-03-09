/*
 * YAPPC - Yet Another Project/Package Creator
 * Copyright (c) 2025 Ghatana
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

package com.ghatana.yappc.core.featurepack.api;

import com.ghatana.yappc.core.featurepack.FeaturePackSpec;
import com.ghatana.yappc.core.featurepack.FeaturePackType;
import java.util.List;
import java.util.Map;

/**
 * API framework feature pack specifications for cross-language API development. Provides
 * comprehensive REST, GraphQL, and gRPC support across build systems.
 *
 * <p>Week 7 Day 34: API feature packs with cross-build-system support.
 *
 * @doc.type class
 * @doc.purpose API framework feature pack specifications for cross-language API development. Provides
 * @doc.layer platform
 * @doc.pattern Component
 */
public final class APIFeaturePacks {

    private APIFeaturePacks() {
        // Utility class
    }

    /**
 * REST API feature pack with OpenAPI/Swagger support. */
    public static FeaturePackSpec restApi() {
        return FeaturePackSpec.builder()
                .name("rest-api")
                .version("1.0.0")
                .description("RESTful API framework with OpenAPI documentation and validation")
                .type(FeaturePackType.API)
                .supportedBuildSystems(List.of("gradle", "maven", "cargo", "make"))
                .supportedLanguages(List.of("java", "rust", "cpp", "typescript"))
                .dependencies(
                        Map.ofEntries(
                                // Java dependencies
                                Map.entry("org.springframework:spring-webmvc", "6.1.2"),
                                Map.entry(
                                        "org.springframework.boot:spring-boot-starter-web",
                                        "3.2.1"),
                                Map.entry(
                                        "org.springdoc:springdoc-openapi-starter-webmvc-ui",
                                        "2.3.0"),
                                Map.entry(
                                        "org.springframework.boot:spring-boot-starter-validation",
                                        "3.2.1"),
                                // Rust dependencies
                                Map.entry("axum", "0.7.4"),
                                Map.entry("tokio", "1.35.1"),
                                Map.entry("serde", "1.0.193"),
                                Map.entry("utoipa", "4.2.0"),
                                Map.entry("utoipa-swagger-ui", "6.0.0"),
                                // C++ dependencies
                                Map.entry("crow", "1.0.3"),
                                Map.entry("nlohmann-json", "3.11.3"),
                                Map.entry("openapi-generator", "7.2.0")))
                .devDependencies(
                        Map.of(
                                "org.springframework.boot:spring-boot-starter-test", "3.2.1",
                                "org.testcontainers:junit-jupiter", "1.19.3",
                                "tokio-test", "0.4.4"))
                .requiredFeatures(List.of("serialization", "validation", "documentation"))
                .optionalFeatures(
                        List.of("cors-support", "rate-limiting", "authentication", "metrics"))
                .configuration(
                        Map.of(
                                "server.port",
                                8080,
                                "server.servlet.context-path",
                                "/api",
                                "api.version",
                                "v1",
                                "swagger.enabled",
                                true,
                                "cors.enabled",
                                false,
                                "validation.enabled",
                                true))
                .templateFiles(
                        List.of(
                                "api/rest/java/RestController.java.hbs",
                                "api/rest/java/ApiConfiguration.java.hbs",
                                "api/rest/rust/handlers.rs.hbs",
                                "api/rest/rust/routes.rs.hbs",
                                "api/rest/cpp/endpoints.hpp.hbs",
                                "api/rest/openapi.yaml.hbs"))
                .configFiles(List.of("application.yml", "api.toml", "swagger.json"))
                .environment(
                        Map.of(
                                "SERVER_PORT", "{{server.port}}",
                                "API_VERSION", "{{api.version}}",
                                "SWAGGER_ENABLED", "{{swagger.enabled}}"))
                .build();
    }

    /**
 * GraphQL API feature pack with schema-first development. */
    public static FeaturePackSpec graphqlApi() {
        return FeaturePackSpec.builder()
                .name("graphql-api")
                .version("1.0.0")
                .description(
                        "GraphQL API framework with schema-first development and code generation")
                .type(FeaturePackType.API)
                .supportedBuildSystems(List.of("gradle", "maven", "cargo", "make"))
                .supportedLanguages(List.of("java", "rust", "cpp", "typescript"))
                .dependencies(
                        Map.ofEntries(
                                // Java dependencies
                                Map.entry("com.graphql-java:graphql-java", "21.3"),
                                Map.entry("com.graphql-java:graphql-java-tools", "5.2.4"),
                                Map.entry(
                                        "org.springframework.boot:spring-boot-starter-graphql",
                                        "3.2.1"),
                                // Rust dependencies
                                Map.entry("async-graphql", "7.0.6"),
                                Map.entry("async-graphql-axum", "7.0.6"),
                                Map.entry("tokio", "1.35.1"),
                                // C++ dependencies
                                Map.entry("graphqlparser", "0.7.0"),
                                Map.entry("cppgraphqlgen", "4.5.5")))
                .devDependencies(
                        Map.of(
                                "org.springframework:spring-webflux", "6.1.2",
                                "graphql-playground", "1.7.26"))
                .requiredFeatures(List.of("schema-generation", "resolvers", "subscriptions"))
                .optionalFeatures(List.of("federation", "caching", "batching", "playground"))
                .configuration(
                        Map.of(
                                "graphql.path",
                                "/graphql",
                                "graphql.playground.enabled",
                                true,
                                "graphql.websocket.path",
                                "/graphql-ws",
                                "schema.introspection",
                                true))
                .build();
    }

    /**
 * gRPC API feature pack with protobuf support. */
    public static FeaturePackSpec grpcApi() {
        return FeaturePackSpec.builder()
                .name("grpc-api")
                .version("1.0.0")
                .description("gRPC API framework with Protocol Buffers and streaming support")
                .type(FeaturePackType.API)
                .supportedBuildSystems(List.of("gradle", "maven", "cargo", "make"))
                .supportedLanguages(List.of("java", "rust", "cpp", "typescript"))
                .dependencies(
                        Map.ofEntries(
                                // Java dependencies
                                Map.entry("io.grpc:grpc-netty-shaded", "1.60.1"),
                                Map.entry("io.grpc:grpc-protobuf", "1.60.1"),
                                Map.entry("io.grpc:grpc-stub", "1.60.1"),
                                // Rust dependencies
                                Map.entry("tonic", "0.10.2"),
                                Map.entry("prost", "0.12.3"),
                                Map.entry("tokio", "1.35.1"),
                                Map.entry("tokio-stream", "0.1.14"),
                                // C++ dependencies
                                Map.entry("grpc++", "1.60.0"),
                                Map.entry("protobuf", "3.21.12")))
                .devDependencies(
                        Map.of(
                                "io.grpc:grpc-testing", "1.60.1",
                                "tonic-build", "0.10.2"))
                .requiredFeatures(List.of("protobuf-generation", "streaming", "reflection"))
                .optionalFeatures(List.of("load-balancing", "health-check", "metrics", "tracing"))
                .configuration(
                        Map.of(
                                "grpc.server.port", 9090,
                                "grpc.reflection.enabled", true,
                                "grpc.health.enabled", true,
                                "protobuf.validation", true))
                .templateFiles(
                        List.of(
                                "api/grpc/proto/service.proto.hbs",
                                "api/grpc/java/ServiceImpl.java.hbs",
                                "api/grpc/rust/service.rs.hbs",
                                "api/grpc/cpp/service.cc.hbs"))
                .build();
    }

    /**
 * WebSocket API feature pack for real-time communication. */
    public static FeaturePackSpec websocketApi() {
        return FeaturePackSpec.builder()
                .name("websocket-api")
                .version("1.0.0")
                .description("WebSocket API framework for real-time bidirectional communication")
                .type(FeaturePackType.API)
                .supportedBuildSystems(List.of("gradle", "maven", "cargo", "make"))
                .supportedLanguages(List.of("java", "rust", "cpp", "typescript"))
                .dependencies(
                        Map.of(
                                // Java dependencies
                                "org.springframework:spring-websocket", "6.1.2",
                                "org.springframework:spring-messaging", "6.1.2",
                                // Rust dependencies
                                "tokio-tungstenite", "0.21.0",
                                "futures-util", "0.3.30",
                                // C++ dependencies
                                "websocketpp", "0.8.2",
                                "boost", "1.84.0"))
                .requiredFeatures(
                        List.of("connection-management", "message-routing", "serialization"))
                .optionalFeatures(
                        List.of("authentication", "rate-limiting", "compression", "heartbeat"))
                .configuration(
                        Map.of(
                                "websocket.path",
                                "/ws",
                                "websocket.allowedOrigins",
                                "*",
                                "websocket.maxConnections",
                                1000,
                                "websocket.heartbeat.enabled",
                                true))
                .build();
    }

    /**
 * Returns all available API feature packs. */
    public static List<FeaturePackSpec> allAPIPacks() {
        return List.of(restApi(), graphqlApi(), grpcApi(), websocketApi());
    }
}
