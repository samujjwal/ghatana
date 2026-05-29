/*
 * Copyright (c) 2026 Ghatana Platform Contributors
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

package com.ghatana.yappc.kernel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.ghatana.contracts.kernel.ProductUnitIntentDocument;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Exports YAPPC project generation intent as a Kernel ProductUnitIntent.
 *
 * <p>This class converts YAPPC project/pack variables into a Kernel ProductUnitIntent payload
 * that can be consumed by the Kernel CLI or API to create lifecycle-governed ProductUnits.
 * The exporter validates required fields, includes provenance metadata, and never mutates
 * Kernel registry files directly.
 *
 * <p><b>Usage Example</b></p>
 * <pre>{@code
 * ProductUnitIntentExporter exporter = new ProductUnitIntentExporter();
 *
 * ProductUnitIntentExporter.Request request = ProductUnitIntentExporter.Request.builder()
 *     .projectId("my-project")
 *     .projectName("My Digital Marketing Campaign")
 *     .targetType("kernel-product-unit")
 *     .surfaces(List.of("backend-api", "web"))
 *     .runtimeProvider("ghatana-file-registry")
 *     .sourceProvider("ghatana-file-registry")
 *     .lifecycleProfile("standard-web-api-product")
 *     .workspaceId("workspace-123")
 *     .build();
 *
 * Path outputPath = exporter.export(request, Path.of(".yappc/product-unit-intent.yaml"));
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Exports YAPPC project generation intent as a Kernel ProductUnitIntent
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class ProductUnitIntentExporter {

    private static final Logger log = LoggerFactory.getLogger(ProductUnitIntentExporter.class);

    private static final String CONTRACT_RESOURCE = "/kernel-product-unit-contract.json";
    private static final String SCHEMA_VERSION = "1.0.0";
    private static final String PRODUCER_TYPE = "yappc";

    private final ObjectMapper yamlMapper;
    private final ObjectMapper jsonMapper;
    private final ProductUnitKernelContractRegistry contractRegistry;
    private final Clock clock;
    private final Supplier<String> intentIdSupplier;

    /**
     * Constructs a new ProductUnitIntentExporter with default YAML and JSON mappers.
     * Loads the Kernel ProductUnit contract from the classpath resource.
     */
    public ProductUnitIntentExporter() {
        this(loadContractRegistry());
    }

    /**
     * Loads the Kernel ProductUnit contract from the classpath resource.
     * This is the single source of truth for the contract DTO path.
     */
    private static ProductUnitKernelContractRegistry loadContractRegistry() {
        try (InputStream input = ProductUnitIntentExporter.class.getResourceAsStream(CONTRACT_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Missing Kernel ProductUnit contract resource: " + CONTRACT_RESOURCE);
            }
            ObjectMapper mapper = new ObjectMapper();
            ProductUnitKernelContractRegistry.KernelProductUnitContract contract =
                mapper.readValue(input, ProductUnitKernelContractRegistry.KernelProductUnitContract.class);
            return new ProductUnitKernelContractRegistry(contract);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load Kernel ProductUnit contract resource", e);
        }
    }

    /**
     * Constructs a new ProductUnitIntentExporter with an explicit Kernel contract registry.
     *
     * @param contractRegistry Kernel contract registry
     */
    public ProductUnitIntentExporter(@NotNull ProductUnitKernelContractRegistry contractRegistry) {
        this(contractRegistry, Clock.systemUTC(), () -> UUID.randomUUID().toString());
    }

    ProductUnitIntentExporter(
            @NotNull ProductUnitKernelContractRegistry contractRegistry,
            @NotNull Clock clock,
            @NotNull Supplier<String> intentIdSupplier) {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.yamlMapper.enable(SerializationFeature.INDENT_OUTPUT);
        
        this.jsonMapper = new ObjectMapper();
        this.jsonMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.contractRegistry = contractRegistry;
        this.clock = clock;
        this.intentIdSupplier = intentIdSupplier;
    }

    /**
     * Request parameters for ProductUnitIntent export.
     */
    public static final class Request {
        private final String projectId;
        private final String projectName;
        private final String targetType;
        private final List<String> surfaces;
        private final String runtimeProvider;
        private final String sourceProvider;
        private final String lifecycleProfile;
        private final String tenantId;
        private final String workspaceId;
        private final String sourcePhase;
        private final String correlationId;
        private final Map<String, Object> metadata;

        private Request(Builder builder) {
            this.projectId = builder.projectId;
            this.projectName = builder.projectName;
            this.targetType = builder.targetType;
            this.surfaces = builder.surfaces;
            this.runtimeProvider = builder.runtimeProvider;
            this.sourceProvider = builder.sourceProvider;
            this.lifecycleProfile = builder.lifecycleProfile;
            this.tenantId = builder.tenantId;
            this.workspaceId = builder.workspaceId;
            this.sourcePhase = builder.sourcePhase != null ? builder.sourcePhase : "generate";
            this.correlationId = builder.correlationId;
            this.metadata = builder.metadata != null ? builder.metadata : new HashMap<>();
        }

        public String projectId() { return projectId; }
        public String projectName() { return projectName; }
        public String targetType() { return targetType; }
        public List<String> surfaces() { return surfaces; }
        public String runtimeProvider() { return runtimeProvider; }
        public String sourceProvider() { return sourceProvider; }
        public String lifecycleProfile() { return lifecycleProfile; }
        public String tenantId() { return tenantId; }
        public String workspaceId() { return workspaceId; }
        public String sourcePhase() { return sourcePhase; }
        public String correlationId() { return correlationId; }
        public Map<String, Object> metadata() { return metadata; }

        /**
         * Builder for Request.
         */
        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private String projectId;
            private String projectName;
            private String targetType;
            private List<String> surfaces;
            private String runtimeProvider;
            private String sourceProvider;
            private String lifecycleProfile;
            private String tenantId;
            private String workspaceId;
            private String sourcePhase;
            private String correlationId;
            private Map<String, Object> metadata;

            public Builder projectId(String projectId) {
                this.projectId = projectId;
                return this;
            }

            public Builder projectName(String projectName) {
                this.projectName = projectName;
                return this;
            }

            public Builder targetType(String targetType) {
                this.targetType = targetType;
                return this;
            }

            public Builder surfaces(List<String> surfaces) {
                this.surfaces = surfaces;
                return this;
            }

            public Builder runtimeProvider(String runtimeProvider) {
                this.runtimeProvider = runtimeProvider;
                return this;
            }

            public Builder sourceProvider(String sourceProvider) {
                this.sourceProvider = sourceProvider;
                return this;
            }

            public Builder lifecycleProfile(String lifecycleProfile) {
                this.lifecycleProfile = lifecycleProfile;
                return this;
            }

            public Builder tenantId(String tenantId) {
                this.tenantId = tenantId;
                return this;
            }

            public Builder workspaceId(String workspaceId) {
                this.workspaceId = workspaceId;
                return this;
            }

            public Builder sourcePhase(String sourcePhase) {
                this.sourcePhase = sourcePhase;
                return this;
            }

            public Builder correlationId(String correlationId) {
                this.correlationId = correlationId;
                return this;
            }

            public Builder metadata(Map<String, Object> metadata) {
                this.metadata = metadata;
                return this;
            }

            public Request build() {
                return new Request(this);
            }
        }
    }

    /**
     * Export result containing the intent data and output path.
     */
    public static final class ExportResult {
        private final Map<String, Object> intent;
        private final Path outputPath;
        private final String intentId;

        public ExportResult(Map<String, Object> intent, Path outputPath, String intentId) {
            this.intent = intent;
            this.outputPath = outputPath;
            this.intentId = intentId;
        }

        public Map<String, Object> intent() { return intent; }
        public Path outputPath() { return outputPath; }
        public String intentId() { return intentId; }
    }

    /**
     * Exports a ProductUnitIntent from the given request and writes it to the specified path.
     *
     * @param request the export request parameters
     * @param outputPath the path where the intent file should be written
     * @return ExportResult containing the intent data and output path
     * @throws ExportException if validation fails or writing encounters an error
     */
    public ExportResult export(@NotNull Request request, @NotNull Path outputPath) throws ExportException {
        validateRequest(request);

        String intentId = generateIntentId();
        Map<String, Object> intent = buildIntent(request, intentId);

        try {
            if (outputPath.toString().endsWith(".yaml") || outputPath.toString().endsWith(".yml")) {
                yamlMapper.writeValue(outputPath.toFile(), intent);
            } else {
                jsonMapper.writeValue(outputPath.toFile(), intent);
            }
            log.info("Exported ProductUnitIntent to {}", outputPath);
        } catch (Exception e) {
            throw new ExportException("Failed to write ProductUnitIntent to " + outputPath, e);
        }

        return new ExportResult(intent, outputPath, intentId);
    }

    /**
     * Builds a ProductUnitIntent map from the request without writing to a file.
     *
     * @param request the export request parameters
     * @return ExportResult containing the intent data (outputPath will be null)
     * @throws ExportException if validation fails
     */
    public ExportResult buildIntent(@NotNull Request request) throws ExportException {
        validateRequest(request);
        String intentId = generateIntentId();
        Map<String, Object> intent = buildIntent(request, intentId);
        return new ExportResult(intent, null, intentId);
    }

    private void validateRequest(Request request) throws ExportException {
        if (request.projectId() == null || request.projectId().isBlank()) {
            throw new ExportException("projectId is required");
        }
        if (request.projectName() == null || request.projectName().isBlank()) {
            throw new ExportException("projectName is required");
        }
        if (request.targetType() == null || request.targetType().isBlank()) {
            throw new ExportException("targetType is required");
        }
        if (request.surfaces() == null || request.surfaces().isEmpty()) {
            throw new ExportException("surfaces must be non-empty");
        }
        for (String surface : request.surfaces()) {
            if (surface == null || surface.isBlank()) {
                throw new ExportException("surfaces must contain only non-empty values");
            }
            if (!contractRegistry.isSurfaceKnown(surface)) {
                throw new ExportException("Unknown ProductUnit surface: " + surface);
            }
        }
        if (request.runtimeProvider() == null || request.runtimeProvider().isBlank()) {
            throw new ExportException("runtimeProvider is required");
        }
        if (!contractRegistry.isProviderKnown(request.runtimeProvider())) {
            throw new ExportException("Unknown Kernel registry provider: " + request.runtimeProvider());
        }
        if (request.sourceProvider() == null || request.sourceProvider().isBlank()) {
            throw new ExportException("sourceProvider is required");
        }
        if (!contractRegistry.isSourceProviderKnown(request.sourceProvider())) {
            throw new ExportException("Unknown source provider: " + request.sourceProvider());
        }
        if (request.lifecycleProfile() == null || request.lifecycleProfile().isBlank()) {
            throw new ExportException("lifecycleProfile is required");
        }
        if (!contractRegistry.isLifecycleProfileKnown(request.lifecycleProfile())) {
            throw new ExportException("Unknown lifecycle profile: " + request.lifecycleProfile());
        }
        if (request.tenantId() == null || request.tenantId().isBlank()) {
            throw new ExportException("tenantId is required");
        }
        if (request.workspaceId() == null || request.workspaceId().isBlank()) {
            throw new ExportException("workspaceId is required");
        }

        String inferredKind = inferKindFromTargetType(request.targetType());
        if (inferredKind == null || !contractRegistry.isProductUnitKindKnown(inferredKind)) {
            throw new ExportException("Unknown ProductUnit kind inferred from targetType: " + request.targetType());
        }
        if (!contractRegistry.isImplementationStatusKnown("planned")) {
            throw new ExportException("Unknown ProductUnit implementation status: planned");
        }
    }

    private Map<String, Object> buildIntent(Request request, String intentId) {
        String correlationId = request.correlationId() == null || request.correlationId().isBlank()
            ? intentId
            : request.correlationId();
        Map<String, Object> metadata = new LinkedHashMap<>(request.metadata());
        metadata.put("producer", PRODUCER_TYPE);
        metadata.put("sourcePhase", request.sourcePhase());
        metadata.put("projectId", request.projectId());
        metadata.put("workspaceId", request.workspaceId());
        metadata.put("exportedAt", Instant.now(clock).toString());
        String inferredKind = inferKindFromTargetType(request.targetType());

        ProductUnitIntentDocument document = new ProductUnitIntentDocument(
                SCHEMA_VERSION,
                intentId,
                "create",
            new ProductUnitIntentDocument.ProductUnitScopeDocument(request.tenantId(), request.workspaceId(), request.projectId()),
            new ProductUnitIntentDocument.ProducerDocument(
                        "yappc:" + request.workspaceId(),
                        PRODUCER_TYPE,
                        "yappc-product-unit-intent-exporter",
                    correlationId),
                new ProductUnitIntentDocument.TargetProvidersDocument(request.runtimeProvider(), request.sourceProvider()),
            new ProductUnitIntentDocument.ProductUnitDraftDocument(
                        request.projectId(),
                        request.projectName(),
                        inferredKind,
                        request.surfaces().stream()
                    .map(surface -> new ProductUnitIntentDocument.ProductUnitSurfaceDocument(
                                        request.projectId() + "-" + surface,
                                        surface,
                                        "planned"))
                                .toList(),
                        request.lifecycleProfile(),
                        Collections.unmodifiableMap(metadata)),
            new ProductUnitIntentDocument.RequestedLifecycleDocument(request.lifecycleProfile(), true));

        @SuppressWarnings("unchecked")
        Map<String, Object> intent = jsonMapper.convertValue(document, Map.class);
        return intent;
    }

    private String generateIntentId() {
        return intentIdSupplier.get();
    }

    private String inferKindFromTargetType(String targetType) {
        return switch (targetType.toLowerCase()) {
            case "kernel-product-unit" -> "business-product";
            case "platform-provider" -> "platform-provider";
            case "shared-service" -> "shared-service";
            default -> null;
        };
    }

    /**
     * Exception thrown when ProductUnitIntent export fails.
     */
    public static final class ExportException extends Exception {
        public ExportException(String message) {
            super(message);
        }

        public ExportException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
