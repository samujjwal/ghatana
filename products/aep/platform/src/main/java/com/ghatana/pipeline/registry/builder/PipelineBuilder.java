package com.ghatana.pipeline.registry.builder;

import com.ghatana.aep.domain.pipeline.PipelineSpec;
import com.ghatana.aep.domain.pipeline.PipelineStageSpec;
import com.ghatana.platform.domain.domain.pipeline.ConnectorSpec;
import com.ghatana.platform.domain.domain.pipeline.ConnectorSpec.ConnectorType;
import com.ghatana.pipeline.registry.model.*;
import com.ghatana.platform.domain.auth.TenantId;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Fluent builder for constructing pipelines with both legacy and structured formats.
 *
 * <p><b>Purpose</b><br>
 * Provides programmatic API for building pipelines that generates both:
 * <ul>
 *   <li>Legacy PipelineSpec (for backward compatibility)</li>
 *   <li>New structured Pipeline with stages (preferred)</li>
 * </ul>
 *
 * <p><b>Dual Format Support</b><br>
 * During migration period, builder generates both formats automatically.
 * New code uses {@link #buildStructured()} for new structured format.
 * Legacy code can use {@link #build()} for PipelineSpec format.
 *
 * <p><b>Usage</b><br>
 * <pre>
 * // New structured format (recommended)
 * Pipeline pipeline = PipelineBuilder.create("fraud-detection")
 *     .withTenant(tenantId)
 *     .stage("filter")
 *         .withOperator("high-value-filter")
 *         .withConfig("threshold", "10000")
 *         .build()
 *     .stage("enrich")
 *         .withOperator("customer-enricher")
 *         .dependsOn("filter")
 *         .build()
 *     .buildStructured();
 *
 * // Legacy format (backward compatible)
 * PipelineSpec spec = PipelineBuilder.create("fraud-detection")
 *     .stage("ingestion")
 *         .withConnector(connectorId)
 *         .build()
 *     .build();
 * </pre>
 *
 * @doc.type class
 * @doc.purpose Fluent builder for pipeline specifications with dual format support
 * @doc.layer product
 * @doc.pattern Builder, Factory
 */
@Slf4j
public class PipelineBuilder {

    private String pipelineId;
    private String name;
    private String description;
    private TenantId tenantId;
    private final List<PipelineStageSpec> legacyStages = new ArrayList<>();
    private final List<StructuredStageBuilder> structuredStages = new ArrayList<>();
    private StageBuilder currentStageBuilder;
    private StructuredStageBuilder currentStructuredBuilder;
    private List<String> inputs = new ArrayList<>();
    private List<String> outputs = new ArrayList<>();
    private Map<String, String> parameters = new HashMap<>();
    private ErrorHandling errorHandling;
    private RetryConfig retryConfig;
    private ResourceRequirements resources;

    /**
     * Creates a new pipeline builder with given name.
     *
     * @param pipelineId unique identifier for pipeline
     * @return new builder instance
     */
    public static PipelineBuilder create(String pipelineId) {
        return new PipelineBuilder(pipelineId);
    }

    private PipelineBuilder(String pipelineId) {
        this.pipelineId = pipelineId;
        this.name = pipelineId;
    }

    /**
     * Set pipeline name.
     */
    public PipelineBuilder named(String name) {
        this.name = name;
        return this;
    }

    /**
     * Set pipeline description.
     */
    public PipelineBuilder withDescription(String description) {
        this.description = description;
        return this;
    }

    /**
     * Set tenant ID.
     */
    public PipelineBuilder withTenant(TenantId tenantId) {
        this.tenantId = tenantId;
        return this;
    }

    /**
     * Add input stream.
     */
    public PipelineBuilder addInput(String input) {
        this.inputs.add(input);
        return this;
    }

    /**
     * Add output stream.
     */
    public PipelineBuilder addOutput(String output) {
        this.outputs.add(output);
        return this;
    }

    /**
     * Add parameter.
     */
    public PipelineBuilder addParameter(String key, String value) {
        this.parameters.put(key, value);
        return this;
    }

    /**
     * Set error handling strategy.
     */
    public PipelineBuilder withErrorHandling(ErrorHandling errorHandling) {
        this.errorHandling = errorHandling;
        return this;
    }

    /**
     * Set retry configuration.
     */
    public PipelineBuilder withRetryConfig(RetryConfig retryConfig) {
        this.retryConfig = retryConfig;
        return this;
    }

    /**
     * Set resource requirements.
     */
    public PipelineBuilder withResourceRequirements(ResourceRequirements resources) {
        this.resources = resources;
        return this;
    }

    /**
     * Adds a new stage to the pipeline (legacy format).
     *
     * @param stageName name of stage
     * @return stage builder for fluent stage configuration
     */
    public StageBuilder stage(String stageName) {
        // Complete previous legacy stage if exists
        if (currentStageBuilder != null) {
            legacyStages.add(currentStageBuilder.build());
        }

        currentStageBuilder = new StageBuilder(this, stageName);
        return currentStageBuilder;
    }

    /**
     * Adds a new structured stage to the pipeline.
     *
     * @param stageName name of stage
     * @return structured stage builder
     */
    public StructuredStageBuilder stageStructured(String stageName) {
        // Complete previous structured stage if exists
        if (currentStructuredBuilder != null) {
            structuredStages.add(currentStructuredBuilder);
        }

        currentStructuredBuilder = new StructuredStageBuilder(this, stageName);
        return currentStructuredBuilder;
    }

    /**
     * Completes legacy pipeline construction.
     *
     * @return constructed PipelineSpec
     */
    public PipelineSpec build() {
        // Complete last legacy stage if exists
        if (currentStageBuilder != null) {
            legacyStages.add(currentStageBuilder.build());
        }

        return PipelineSpec.builder()
                .stages(new ArrayList<>(legacyStages))
                .build();
    }

    /**
     * Completes structured pipeline construction.
     *
     * @return constructed Pipeline with structured config
     */
    public Pipeline buildStructured() {
        // Complete last structured stage if exists
        if (currentStructuredBuilder != null) {
            structuredStages.add(currentStructuredBuilder);
        }

        // Build structured config
        PipelineConfig config = PipelineConfig.builder()
                .inputs(new ArrayList<>(inputs))
                .outputs(new ArrayList<>(outputs))
                .parameters(new HashMap<>(parameters))
                .errorHandling(errorHandling)
                .retryConfig(retryConfig)
                .resources(resources)
                .build();

        // Build stages
        List<PipelineStage> stages = structuredStages.stream()
                .map(StructuredStageBuilder::buildStage)
                .toList();

        // Build pipeline
        return Pipeline.builder()
                .id(pipelineId)
                .name(name)
                .description(description)
                .tenantId(tenantId)
                .version(1)
                .active(true)
                .structuredConfig(config)
                .stages(stages)
                .build();
    }

    /**
     * Stage builder for fluent stage configuration.
     */
    public static class StageBuilder {

        private final PipelineBuilder pipelineBuilder;
        private final String stageName;
        private final List<String> connectorIds = new ArrayList<>();
        private final List<ConnectorSpec> connectors = new ArrayList<>();

        private StageBuilder(PipelineBuilder pipelineBuilder, String stageName) {
            this.pipelineBuilder = pipelineBuilder;
            this.stageName = stageName;
        }

        /**
         * Adds connector reference to stage by ID.
         *
         * @param connectorId identifier of registered connector
         * @return this builder
         */
        public StageBuilder withConnector(String connectorId) {
            if (connectorId != null && !connectorId.trim().isEmpty()) {
                connectorIds.add(connectorId);
                log.debug("Added connector reference '{}' to stage '{}'", connectorId, stageName);
            }
            return this;
        }

        /**
         * Adds inline connector specification to stage.
         *
         * @param connector connector spec to embed
         * @return this builder
         */
        public StageBuilder withConnectorSpec(ConnectorSpec connector) {
            if (connector != null) {
                connectors.add(connector);
                log.debug("Added connector spec '{}' (type: {}) to stage '{}'",
                        connector.getId(), connector.getType(), stageName);
            }
            return this;
        }

        /**
         * Adds queue source connector reference.
         *
         * @param connectorId connector ID
         * @return this builder
         */
        public StageBuilder withQueueSource(String connectorId) {
            return withConnector(connectorId);
        }

        /**
         * Adds queue sink connector reference.
         *
         * @param connectorId connector ID
         * @return this builder
         */
        public StageBuilder withQueueSink(String connectorId) {
            return withConnector(connectorId);
        }

        /**
         * Adds HTTP ingress connector reference.
         *
         * @param connectorId connector ID
         * @return this builder
         */
        public StageBuilder withHttpIngress(String connectorId) {
            return withConnector(connectorId);
        }

        /**
         * Adds HTTP egress connector reference.
         *
         * @param connectorId connector ID
         * @return this builder
         */
        public StageBuilder withHttpEgress(String connectorId) {
            return withConnector(connectorId);
        }

        /**
         * Adds EventCloud source connector reference.
         *
         * @param connectorId connector ID
         * @return this builder
         */
        public StageBuilder withEventCloudSource(String connectorId) {
            return withConnector(connectorId);
        }

        /**
         * Adds EventCloud sink connector reference.
         *
         * @param connectorId connector ID
         * @return this builder
         */
        public StageBuilder withEventCloudSink(String connectorId) {
            return withConnector(connectorId);
        }

        /**
         * Adds inline HTTP ingress connector.
         *
         * @param targetPath HTTP path for ingress
         * @param port HTTP port
         * @return this builder
         */
        public StageBuilder withHttpIngressSpec(String targetPath, int port) {
            ConnectorSpec spec = new ConnectorSpec();
            spec.setId(UUID.randomUUID().toString());
            spec.setType(ConnectorType.HTTP_INGRESS);
            spec.setEndpoint(targetPath);
            spec.setTenantId("default");
            spec.setProperties(Map.of(
                    "name", "http-ingress-" + stageName,
                    "port", String.valueOf(port),
                    "maxPayloadSizeBytes", "1048576",
                    "timeout_ms", "30000"
            ));
            return withConnectorSpec(spec);
        }

        /**
         * Adds inline HTTP egress connector.
         *
         * @param targetUrl target URL for delivery
         * @param method HTTP method (GET, POST, PUT, DELETE)
         * @return this builder
         */
        public StageBuilder withHttpEgressSpec(String targetUrl, String method) {
            ConnectorSpec spec = new ConnectorSpec();
            spec.setId(UUID.randomUUID().toString());
            spec.setType(ConnectorType.HTTP_EGRESS);
            spec.setEndpoint(targetUrl);
            spec.setTenantId("default");
            spec.setProperties(Map.of(
                    "name", "http-egress-" + stageName,
                    "method", method,
                    "timeout_ms", "30000",
                    "retryMaxAttempts", "3",
                    "retryBackoffMs", "1000"
            ));
            return withConnectorSpec(spec);
        }

        /**
         * Completes stage and returns to pipeline builder.
         *
         * @return pipeline builder for continued building
         */
        public PipelineBuilder endStage() {
            pipelineBuilder.legacyStages.add(build());
            pipelineBuilder.currentStageBuilder = null;
            return pipelineBuilder;
        }

        /**
         * Builds the stage specification.
         *
         * @return constructed PipelineStageSpec
         */
        private PipelineStageSpec build() {
            return PipelineStageSpec.builder()
                    .name(stageName)
                    .connectorIds(!connectorIds.isEmpty() ? new ArrayList<>(connectorIds) : null)
                    .connectors(!connectors.isEmpty() ? new ArrayList<>(connectors) : null)
                    .stageType(!connectors.isEmpty() || !connectorIds.isEmpty() ? "CONNECTOR" : "STREAM")
                    .build();
        }
    }

    /**
     * Builder for structured pipeline stages.
     */
    public static class StructuredStageBuilder {

        private final PipelineBuilder pipelineBuilder;
        private final String stageName;
        private String stageId;
        private PipelineStage.StageType stageType = PipelineStage.StageType.CUSTOM;
        private String operatorId;
        private final Map<String, String> config = new HashMap<>();
        private final List<String> dependencies = new ArrayList<>();
        private Duration timeout;
        private boolean enabled = true;

        private StructuredStageBuilder(PipelineBuilder pipelineBuilder, String stageName) {
            this.pipelineBuilder = pipelineBuilder;
            this.stageName = stageName;
            this.stageId = "stage-" + UUID.randomUUID().toString().substring(0, 8);
        }

        /**
         * Set stage ID.
         */
        public StructuredStageBuilder withId(String id) {
            this.stageId = id;
            return this;
        }

        /**
         * Set stage type.
         */
        public StructuredStageBuilder ofType(PipelineStage.StageType type) {
            this.stageType = type;
            return this;
        }

        /**
         * Set operator ID.
         */
        public StructuredStageBuilder withOperator(String operatorId) {
            this.operatorId = operatorId;
            return this;
        }

        /**
         * Add stage configuration parameter.
         */
        public StructuredStageBuilder withConfig(String key, String value) {
            this.config.put(key, value);
            return this;
        }

        /**
         * Set stage timeout.
         */
        public StructuredStageBuilder withTimeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Add dependency on another stage.
         */
        public StructuredStageBuilder dependsOn(String stageId) {
            this.dependencies.add(stageId);
            return this;
        }

        /**
         * Disable this stage.
         */
        public StructuredStageBuilder disabled() {
            this.enabled = false;
            return this;
        }

        /**
         * Complete stage and return to pipeline builder.
         */
        public PipelineBuilder build() {
            pipelineBuilder.structuredStages.add(this);
            pipelineBuilder.currentStructuredBuilder = null;
            return pipelineBuilder;
        }

        /**
         * Builds the structured stage.
         *
         * @return constructed PipelineStage
         */
        private PipelineStage buildStage() {
            return PipelineStage.builder()
                    .id(stageId)
                    .name(stageName)
                    .type(stageType)
                    .operatorId(operatorId)
                    .config(new HashMap<>(config))
                    .dependsOn(new ArrayList<>(dependencies))
                    .timeout(timeout)
                    .enabled(enabled)
                    .build();
        }
    }
}




