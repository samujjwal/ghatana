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

package com.ghatana.yappc.core.multirepo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Enhanced multi-repository orchestration service for cross-language coordination. Supports Rust,
 * Java, TypeScript, and C++ projects with unified workspace management.
 *
 * <p>Week 7 Day 35: Enhanced multi-repo orchestration with cross-language integration.
 *
 * @doc.type interface
 * @doc.purpose Enhanced multi-repository orchestration service for cross-language coordination. Supports Rust,
 * @doc.layer platform
 * @doc.pattern Component
 */
public interface EnhancedMultiRepoOrchestrator {

    /**
     * Generates a complete multi-repository workspace with coordinated services.
     *
     * @param spec The multi-repo workspace specification
     * @return The generated multi-repo workspace with all service configurations
     */
    GeneratedMultiRepoWorkspace generateWorkspace(MultiRepoWorkspaceSpec spec);

    /**
     * Validates a multi-repo workspace specification for consistency and compatibility.
     *
     * @param spec The workspace specification to validate
     * @return Validation result with cross-service compatibility analysis
     */
    MultiRepoValidationResult validateWorkspace(MultiRepoWorkspaceSpec spec);

    /**
     * Suggests improvements for multi-repo workspace architecture.
     *
     * @param spec The workspace specification
     * @return AI-powered improvement suggestions for cross-service optimization
     */
    MultiRepoImprovementSuggestions suggestImprovements(MultiRepoWorkspaceSpec spec);

    /**
     * Generates cross-service API contracts and client libraries.
     *
     * @param spec The workspace specification
     * @return Generated API contracts and client code for all supported languages
     */
    CrossServiceContractResult generateContracts(MultiRepoWorkspaceSpec spec);
}

/**
 * Default implementation of enhanced multi-repo orchestration. */
class DefaultEnhancedMultiRepoOrchestrator implements EnhancedMultiRepoOrchestrator {

    @Override
    public GeneratedMultiRepoWorkspace generateWorkspace(MultiRepoWorkspaceSpec spec) {
        var repositories = new ArrayList<String>();
        var serviceConfigs = new HashMap<String, String>();
        var buildConfigs = new HashMap<String, String>();
        var communicationConfigs = new HashMap<String, String>();
        var contracts = new ArrayList<String>();
        var dockerConfigs = new HashMap<String, String>();
        var ciCdConfigs = new HashMap<String, String>();

        for (var service : spec.services()) {
            String repoName = service.serviceName();
            repositories.add(repoName);

            // Generate service-specific configurations
            generateServiceConfiguration(service, serviceConfigs);
            generateBuildConfiguration(service, buildConfigs);
            generateCommunicationConfiguration(service, communicationConfigs);
            generateDockerConfiguration(service, dockerConfigs);
            generateCICDConfiguration(service, ciCdConfigs);

            // Generate contracts for service APIs
            if (service.apiContracts() != null && !service.apiContracts().isEmpty()) {
                contracts.addAll(service.apiContracts());
            }
        }

        // Generate workspace-level configurations
        generateWorkspaceConfiguration(spec, serviceConfigs, buildConfigs);

        return GeneratedMultiRepoWorkspace.builder()
                .spec(spec)
                .generatedRepositories(repositories)
                .serviceConfigurations(serviceConfigs)
                .buildConfigurations(buildConfigs)
                .communicationConfigurations(communicationConfigs)
                .crossServiceContracts(contracts)
                .dockerConfigurations(dockerConfigs)
                .ciCdConfigurations(ciCdConfigs)
                .orchestrationMetadata(
                        Map.of(
                                "totalServices", spec.services().size(),
                                "languagesUsed", extractLanguages(spec),
                                "buildSystemsUsed", extractBuildSystems(spec),
                                "communicationPatterns", spec.communicationPatterns()))
                .build();
    }

    private void generateServiceConfiguration(
            MultiRepoWorkspaceSpec.MultiRepoServiceSpec service,
            Map<String, String> serviceConfigs) {

        String config =
                switch (service.language().toLowerCase()) {
                    case "java" -> generateJavaServiceConfig(service);
                    case "rust" -> generateRustServiceConfig(service);
                    case "typescript" -> generateTypeScriptServiceConfig(service);
                    case "cpp" -> generateCppServiceConfig(service);
                    default -> generateGenericServiceConfig(service);
                };

        serviceConfigs.put(service.serviceName() + "/config", config);
    }

    private String generateJavaServiceConfig(MultiRepoWorkspaceSpec.MultiRepoServiceSpec service) {
        return """
            # Java service configuration for %s
            server:
              port: %d

            service:
              name: %s
              type: %s

            dependencies: %s
            """
                .formatted(
                        service.serviceName(),
                        service.port(),
                        service.serviceName(),
                        service.serviceType(),
                        String.join(", ", service.dependencies()));
    }

    private String generateRustServiceConfig(MultiRepoWorkspaceSpec.MultiRepoServiceSpec service) {
        return """
            # Rust service configuration for %s
            [service]
            name = "%s"
            type = "%s"
            port = %d

            [dependencies]
            %s
            """
                .formatted(
                        service.serviceName(),
                        service.serviceName(),
                        service.serviceType(),
                        service.port(),
                        String.join(
                                "\\n",
                                service.dependencies().stream().map(dep -> "# " + dep).toList()));
    }

    private String generateTypeScriptServiceConfig(
            MultiRepoWorkspaceSpec.MultiRepoServiceSpec service) {
        return """
            {
              "service": {
                "name": "%s",
                "type": "%s",
                "port": %d
              },
              "dependencies": %s
            }
            """
                .formatted(
                        service.serviceName(),
                        service.serviceType(),
                        service.port(),
                        "[\"" + String.join("\", \"", service.dependencies()) + "\"]");
    }

    private String generateCppServiceConfig(MultiRepoWorkspaceSpec.MultiRepoServiceSpec service) {
        return """
            # C++ service configuration for %s
            SERVICE_NAME=%s
            SERVICE_TYPE=%s
            SERVICE_PORT=%d
            """
                .formatted(
                        service.serviceName(),
                        service.serviceName(),
                        service.serviceType(),
                        service.port());
    }

    private String generateGenericServiceConfig(
            MultiRepoWorkspaceSpec.MultiRepoServiceSpec service) {
        return """
            # Generic service configuration for %s
            name: %s
            type: %s
            port: %d
            """
                .formatted(
                        service.serviceName(),
                        service.serviceName(),
                        service.serviceType(),
                        service.port());
    }

    private void generateBuildConfiguration(
            MultiRepoWorkspaceSpec.MultiRepoServiceSpec service, Map<String, String> buildConfigs) {

        String config =
                switch (service.buildSystem().toLowerCase()) {
                    case "gradle" -> generateGradleBuildConfig(service);
                    case "maven" -> generateMavenBuildConfig(service);
                    case "cargo" -> generateCargoBuildConfig(service);
                    case "make" -> generateMakeBuildConfig(service);
                    case "npm" -> generateNpmBuildConfig(service);
                    default -> generateGenericBuildConfig(service);
                };

        buildConfigs.put(service.serviceName() + "/build", config);
    }

    private String generateGradleBuildConfig(MultiRepoWorkspaceSpec.MultiRepoServiceSpec service) {
        return """
            plugins {
                id 'java'
                id 'org.springframework.boot' version '3.2.1'
            }

            group = 'com.example'
            version = '1.0.0'

            dependencies {
                implementation 'org.springframework.boot:spring-boot-starter-web'
                %s
            }
            """
                .formatted(
                        service.dependencies().stream()
                                .map(dep -> "    implementation '" + dep + "'")
                                .reduce("", (a, b) -> a + "\\n" + b));
    }

    private String generateMavenBuildConfig(MultiRepoWorkspaceSpec.MultiRepoServiceSpec service) {
        return """
            <project>
                <groupId>com.example</groupId>
                <artifactId>%s</artifactId>
                <version>1.0.0</version>
                <packaging>jar</packaging>

                <dependencies>
                    <!-- Service dependencies -->
                </dependencies>
            </project>
            """
                .formatted(service.serviceName());
    }

    private String generateCargoBuildConfig(MultiRepoWorkspaceSpec.MultiRepoServiceSpec service) {
        return """
            [package]
            name = "%s"
            version = "0.1.0"
            edition = "2021"

            [dependencies]
            tokio = { version = "1.0", features = ["full"] }
            axum = "0.7"
            """
                .formatted(service.serviceName());
    }

    private String generateMakeBuildConfig(MultiRepoWorkspaceSpec.MultiRepoServiceSpec service) {
        return """
            # Makefile for %s
            CC = gcc
            CFLAGS = -Wall -Wextra -std=c17
            TARGET = %s

            all: $(TARGET)

            $(TARGET): src/main.c
            \t$(CC) $(CFLAGS) -o $@ $^

            clean:
            \trm -f $(TARGET)
            """
                .formatted(service.serviceName(), service.serviceName());
    }

    private String generateNpmBuildConfig(MultiRepoWorkspaceSpec.MultiRepoServiceSpec service) {
        return """
            {
              "name": "%s",
              "version": "1.0.0",
              "main": "dist/index.js",
              "scripts": {
                "build": "tsc",
                "start": "node dist/index.js",
                "dev": "ts-node src/index.ts"
              },
              "dependencies": {
                "express": "^4.18.0",
                "typescript": "^5.0.0"
              }
            }
            """
                .formatted(service.serviceName());
    }

    private String generateGenericBuildConfig(MultiRepoWorkspaceSpec.MultiRepoServiceSpec service) {
        return "# Generic build configuration for " + service.serviceName();
    }

    private void generateCommunicationConfiguration(
            MultiRepoWorkspaceSpec.MultiRepoServiceSpec service,
            Map<String, String> communicationConfigs) {

        String config =
                """
            # Communication configuration for %s
            service_discovery:
              type: dns
              namespace: %s

            endpoints:
              health: /health
              metrics: /metrics
              api: /api/v1

            cross_service_deps: %s
            """
                        .formatted(
                                service.serviceName(),
                                service.serviceName(),
                                String.join(", ", service.crossServiceDependencies()));

        communicationConfigs.put(service.serviceName() + "/communication", config);
    }

    private void generateDockerConfiguration(
            MultiRepoWorkspaceSpec.MultiRepoServiceSpec service,
            Map<String, String> dockerConfigs) {

        String dockerfile =
                switch (service.language().toLowerCase()) {
                    case "java" -> generateJavaDockerfile(service);
                    case "rust" -> generateRustDockerfile(service);
                    case "typescript" -> generateNodeDockerfile(service);
                    case "cpp" -> generateCppDockerfile(service);
                    default -> generateGenericDockerfile(service);
                };

        dockerConfigs.put(service.serviceName() + "/Dockerfile", dockerfile);
    }

    private String generateJavaDockerfile(MultiRepoWorkspaceSpec.MultiRepoServiceSpec service) {
        return """
            FROM openjdk:21-jre-slim

            COPY build/libs/%s-*.jar app.jar

            EXPOSE %d

            ENTRYPOINT ["java", "-jar", "/app.jar"]
            """
                .formatted(service.serviceName(), service.port());
    }

    private String generateRustDockerfile(MultiRepoWorkspaceSpec.MultiRepoServiceSpec service) {
        return """
            FROM rust:1.75-slim as builder

            WORKDIR /app
            COPY . .
            RUN cargo build --release

            FROM debian:bookworm-slim
            COPY --from=builder /app/target/release/%s /usr/local/bin/

            EXPOSE %d

            CMD ["%s"]
            """
                .formatted(service.serviceName(), service.port(), service.serviceName());
    }

    private String generateNodeDockerfile(MultiRepoWorkspaceSpec.MultiRepoServiceSpec service) {
        return """
            FROM node:20-slim

            WORKDIR /app
            COPY package*.json ./
            RUN npm ci --only=production

            COPY dist/ ./dist/

            EXPOSE %d

            CMD ["node", "dist/index.js"]
            """
                .formatted(service.port());
    }

    private String generateCppDockerfile(MultiRepoWorkspaceSpec.MultiRepoServiceSpec service) {
        return """
            FROM gcc:13-slim as builder

            WORKDIR /app
            COPY . .
            RUN make

            FROM debian:bookworm-slim
            COPY --from=builder /app/%s /usr/local/bin/

            EXPOSE %d

            CMD ["%s"]
            """
                .formatted(service.serviceName(), service.port(), service.serviceName());
    }

    private String generateGenericDockerfile(MultiRepoWorkspaceSpec.MultiRepoServiceSpec service) {
        return """
            FROM alpine:latest

            # Generic Dockerfile for %s
            EXPOSE %d

            CMD ["echo", "Service not configured"]
            """
                .formatted(service.serviceName(), service.port());
    }

    private void generateCICDConfiguration(
            MultiRepoWorkspaceSpec.MultiRepoServiceSpec service, Map<String, String> ciCdConfigs) {

        String workflow =
                """
            name: CI/CD for %s

            on:
              push:
                branches: [main]
              pull_request:
                branches: [main]

            jobs:
              test:
                runs-on: ubuntu-latest
                steps:
                  - uses: actions/checkout@v4
                  - name: Setup %s
                    uses: %s
                  - name: Test
                    run: %s
                  - name: Build
                    run: %s
                  - name: Deploy
                    if: github.ref == 'refs/heads/main'
                    run: %s
            """
                        .formatted(
                                service.serviceName(),
                                service.language(),
                                getCISetupAction(service.language()),
                                getTestCommand(service.buildSystem()),
                                getBuildCommand(service.buildSystem()),
                                getDeployCommand(service.serviceName()));

        ciCdConfigs.put(service.serviceName() + "/.github/workflows/ci.yml", workflow);
    }

    private String getCISetupAction(String language) {
        return switch (language.toLowerCase()) {
            case "java" ->
                    "actions/setup-java@v4\\n          with:\\n            java-version: '21'";
            case "rust" ->
                    "actions-rs/toolchain@v1\\n          with:\\n            toolchain: stable";
            case "typescript" ->
                    "actions/setup-node@v4\\n          with:\\n            node-version: '20'";
            default -> "# Setup for " + language;
        };
    }

    private String getTestCommand(String buildSystem) {
        return switch (buildSystem.toLowerCase()) {
            case "gradle" -> "./gradlew test";
            case "maven" -> "mvn test";
            case "cargo" -> "cargo test";
            case "make" -> "make test";
            case "npm" -> "npm test";
            default -> "echo 'No test command defined'";
        };
    }

    private String getBuildCommand(String buildSystem) {
        return switch (buildSystem.toLowerCase()) {
            case "gradle" -> "./gradlew build";
            case "maven" -> "mvn package";
            case "cargo" -> "cargo build --release";
            case "make" -> "make";
            case "npm" -> "npm run build";
            default -> "echo 'No build command defined'";
        };
    }

    private String getDeployCommand(String serviceName) {
        return "docker build -t " + serviceName + " . && docker push " + serviceName;
    }

    private void generateWorkspaceConfiguration(
            MultiRepoWorkspaceSpec spec,
            Map<String, String> serviceConfigs,
            Map<String, String> buildConfigs) {

        // Generate docker-compose for the entire workspace
        StringBuilder dockerCompose = new StringBuilder();
        dockerCompose.append("version: '3.8'\\n");
        dockerCompose.append("services:\\n");

        for (var service : spec.services()) {
            dockerCompose.append("  ").append(service.serviceName()).append(":\\n");
            dockerCompose.append("    build: ./").append(service.serviceName()).append("\\n");
            dockerCompose.append("    ports:\\n");
            dockerCompose
                    .append("      - \"")
                    .append(service.port())
                    .append(":")
                    .append(service.port())
                    .append("\"\\n");

            if (!service.crossServiceDependencies().isEmpty()) {
                dockerCompose.append("    depends_on:\\n");
                for (String dep : service.crossServiceDependencies()) {
                    dockerCompose.append("      - ").append(dep).append("\\n");
                }
            }
        }

        serviceConfigs.put("docker-compose.yml", dockerCompose.toString());

        // Generate workspace README
        String readme =
                """
            # %s

            %s

            ## Services

            %s

            ## Getting Started

            1. Clone all repositories
            2. Run `docker-compose up` to start all services
            3. Access services at their respective ports

            ## Development

            Each service is in its own repository with individual build configurations.
            """
                        .formatted(
                                spec.workspaceName(),
                                spec.description(),
                                spec.services().stream()
                                        .map(
                                                s ->
                                                        "- **"
                                                                + s.serviceName()
                                                                + "** ("
                                                                + s.language()
                                                                + "): "
                                                                + s.serviceType())
                                        .reduce("", (a, b) -> a + "\\n" + b));

        serviceConfigs.put("README.md", readme);
    }

    private List<String> extractLanguages(MultiRepoWorkspaceSpec spec) {
        return spec.services().stream()
                .map(MultiRepoWorkspaceSpec.MultiRepoServiceSpec::language)
                .distinct()
                .toList();
    }

    private List<String> extractBuildSystems(MultiRepoWorkspaceSpec spec) {
        return spec.services().stream()
                .map(MultiRepoWorkspaceSpec.MultiRepoServiceSpec::buildSystem)
                .distinct()
                .toList();
    }

    @Override
    public MultiRepoValidationResult validateWorkspace(MultiRepoWorkspaceSpec spec) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (spec.services().isEmpty()) {
            errors.add("Workspace must contain at least one service");
        }

        // Validate port conflicts
        var portCounts = new HashMap<Integer, Long>();
        spec.services().stream()
                .map(MultiRepoWorkspaceSpec.MultiRepoServiceSpec::port)
                .forEach(port -> portCounts.merge(port, 1L, Long::sum));

        portCounts.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .forEach(
                        entry ->
                                errors.add(
                                        "Port "
                                                + entry.getKey()
                                                + " is used by multiple services"));

        // Validate cross-service dependencies
        var serviceNames =
                spec.services().stream()
                        .map(MultiRepoWorkspaceSpec.MultiRepoServiceSpec::serviceName)
                        .toList();

        for (var service : spec.services()) {
            for (String dep : service.crossServiceDependencies()) {
                if (!serviceNames.contains(dep)) {
                    errors.add(
                            "Service "
                                    + service.serviceName()
                                    + " depends on unknown service: "
                                    + dep);
                }
            }
        }

        return new MultiRepoValidationResult(errors.isEmpty(), errors, warnings);
    }

    @Override
    public MultiRepoImprovementSuggestions suggestImprovements(MultiRepoWorkspaceSpec spec) {
        var architectureOptimizations = new ArrayList<String>();
        var communicationImprovements = new ArrayList<String>();
        var buildSystemOptimizations = new ArrayList<String>();

        // Analyze architecture patterns
        if (spec.services().size() > 5) {
            architectureOptimizations.add(
                    "Consider implementing service mesh for large number of services");
        }

        if (spec.communicationPatterns().isEmpty()) {
            communicationImprovements.add(
                    "Define explicit communication patterns (REST, gRPC, message queues)");
        }

        // Analyze build systems
        var buildSystems = extractBuildSystems(spec);
        if (buildSystems.size() > 2) {
            buildSystemOptimizations.add(
                    "Consider standardizing on fewer build systems for easier maintenance");
        }

        return MultiRepoImprovementSuggestions.builder()
                .architectureOptimizations(architectureOptimizations)
                .communicationImprovements(communicationImprovements)
                .buildSystemOptimizations(buildSystemOptimizations)
                .improvementScore(0.8)
                .build();
    }

    @Override
    public CrossServiceContractResult generateContracts(MultiRepoWorkspaceSpec spec) {
        var contracts = new ArrayList<String>();
        var schemas = new HashMap<String, String>();
        var clientLibraries = new HashMap<String, String>();

        for (var service : spec.services()) {
            // Generate OpenAPI schema for REST services
            if (service.serviceType().equals("rest-api")) {
                String schema = generateOpenAPISchema(service);
                schemas.put(service.serviceName() + "-openapi.yml", schema);
                contracts.add(service.serviceName() + "-openapi.yml");

                // Generate client libraries for each language
                for (String language : extractLanguages(spec)) {
                    String client = generateClientLibrary(service, language);
                    clientLibraries.put(service.serviceName() + "-client-" + language, client);
                }
            }
        }

        return CrossServiceContractResult.builder()
                .generatedContracts(contracts)
                .apiSchemas(schemas)
                .clientLibraries(clientLibraries)
                .communicationProtocols(spec.communicationPatterns())
                .build();
    }

    private String generateOpenAPISchema(MultiRepoWorkspaceSpec.MultiRepoServiceSpec service) {
        return """
            openapi: 3.0.3
            info:
              title: %s API
              version: 1.0.0
              description: API for %s service

            servers:
              - url: http://localhost:%d

            paths:
              /health:
                get:
                  summary: Health check
                  responses:
                    '200':
                      description: Service is healthy
            """
                .formatted(service.serviceName(), service.serviceName(), service.port());
    }

    private String generateClientLibrary(
            MultiRepoWorkspaceSpec.MultiRepoServiceSpec service, String language) {
        return switch (language.toLowerCase()) {
            case "java" -> generateJavaClient(service);
            case "rust" -> generateRustClient(service);
            case "typescript" -> generateTypeScriptClient(service);
            default -> "// Client library for " + service.serviceName() + " in " + language;
        };
    }

    private String generateJavaClient(MultiRepoWorkspaceSpec.MultiRepoServiceSpec service) {
        return """
            public class %sClient {
                private final String baseUrl;

                public %sClient(String baseUrl) {
                    this.baseUrl = baseUrl;
                }

                public Promise<String> getHealth() {
                    // HTTP client implementation
                    return Promise.of("OK");
                }
            }
            """
                .formatted(capitalize(service.serviceName()), capitalize(service.serviceName()));
    }

    private String generateRustClient(MultiRepoWorkspaceSpec.MultiRepoServiceSpec service) {
        return """
            pub struct %sClient {
                base_url: String,
                client: reqwest::Client,
            }

            impl %sClient {
                pub fn new(base_url: String) -> Self {
                    Self {
                        base_url,
                        client: reqwest::Client::new(),
                    }
                }

                pub async fn get_health(&self) -> Result<String, reqwest::Error> {
                    let url = format!("{}/health", self.base_url);
                    self.client.get(&url).send().await?.text().await
                }
            }
            """
                .formatted(capitalize(service.serviceName()), capitalize(service.serviceName()));
    }

    private String generateTypeScriptClient(MultiRepoWorkspaceSpec.MultiRepoServiceSpec service) {
        return """
            export class %sClient {
                constructor(private baseUrl: string) {}

                async getHealth(): Promise<string> {
                    const response = await fetch(`${this.baseUrl}/health`);
                    return response.text();
                }
            }
            """
                .formatted(capitalize(service.serviceName()));
    }

    private String capitalize(String str) {
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
