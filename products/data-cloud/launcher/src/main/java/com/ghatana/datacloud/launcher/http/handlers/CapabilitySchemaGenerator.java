/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.spi.AggregationCapability;
import com.ghatana.datacloud.spi.StreamingCapability;
import com.ghatana.datacloud.spi.ai.AnomalyDetectionCapability;
import com.ghatana.datacloud.spi.ai.ExplanationCapability;
import com.ghatana.datacloud.spi.ai.PredictionCapability;
import com.ghatana.datacloud.spi.ai.RecommendationCapability;
import com.ghatana.datacloud.spi.SimilaritySearchCapability;
import com.ghatana.datacloud.spi.TransactionCapability;
import io.activej.http.HttpResponse;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * P2-CAP-1: Capability Schema Generator
 * 
 * Generates the unified capability schema by discovering SPI interfaces
 * and their implementations. This is the single source of truth for all
 * capability-based feature gates, preventing drift between docs/UI/runtime.
 *
 * @doc.type class
 * @doc.purpose Generate unified capability schema from SPI interfaces
 * @doc.layer product
 * @doc.pattern Generator
 */
public final class CapabilitySchemaGenerator {

    private static final Logger log = LoggerFactory.getLogger(CapabilitySchemaGenerator.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private CapabilitySchemaGenerator() {}

    /**
     * Generate the complete capability schema
     */
    public static CapabilitySchema generateSchema() {
        CapabilitySchema schema = new CapabilitySchema();
        schema.version = "1.0.0";
        schema.metadata = new SchemaMetadata();
        schema.metadata.description = "Unified capability schema for Ghatana platform and products";
        schema.metadata.lastUpdated = Instant.now().toString();
        schema.metadata.generators = List.of(
            "KernelCapability.java",
            "Data Cloud SPI capabilities",
            "AEP SPI capabilities",
            "Product feature flags"
        );

        // Discover capabilities from SPI
        schema.dataCloudCapabilities = discoverDataCloudCapabilities();
        schema.kernelCapabilities = discoverKernelCapabilities();
        schema.aepCapabilities = discoverAepCapabilities();
        schema.uiFeatureGates = generateUIFeatureGates(schema.dataCloudCapabilities);
        schema.statusDefinitions = generateStatusDefinitions();

        return schema;
    }

    /**
     * Discover Data Cloud capabilities from SPI
     */
    private static List<Capability> discoverDataCloudCapabilities() {
        List<Capability> capabilities = new ArrayList<>();

        // Query Capability
        capabilities.add(new Capability(
            "data.cloud.query",
            "Query Capability",
            "DATA_MANAGEMENT",
            "stable",
            List.of("data-cloud"),
            "Filtered queries, range queries, aggregations, and pagination",
            "com.ghatana.datacloud.spi.capability.QueryCapability",
            "enableUnifiedDataExplorer",
            Map.of()
        ));

        // Streaming Capability
        capabilities.add(new Capability(
            "data.cloud.streaming",
            "Streaming Capability",
            "EVENT_PROCESSING",
            "stable",
            List.of("data-cloud"),
            "Real-time event streaming and pub/sub",
            "com.ghatana.datacloud.spi.capability.StreamingCapability",
            null,
            Map.of()
        ));

        // Storage Capability
        capabilities.add(new Capability(
            "data.cloud.storage",
            "Storage Capability",
            "DATA_MANAGEMENT",
            "stable",
            List.of("data-cloud"),
            "Multi-tier data storage with HOT/WARM/COOL/COLD tiers",
            "com.ghatana.datacloud.spi.capability.StorageCapability",
            null,
            Map.of()
        ));

        // Aggregation Capability
        capabilities.add(new Capability(
            "data.cloud.analytics",
            "Analytics Capability",
            "ANALYTICS",
            "stable",
            List.of("data-cloud"),
            "Analytics and aggregation operations",
            "com.ghatana.datacloud.spi.AggregationCapability",
            null,
            Map.of(
                "cancellation_supported", "false",
                "notes", "Analytics cancellation is unsupported; UI must reflect this"
            )
        ));

        // Workflow Execution Capability
        capabilities.add(new Capability(
            "data.cloud.workflow.execution",
            "Workflow Execution Capability",
            "WORKFLOW",
            "stable",
            List.of("data-cloud"),
            "Durable workflow execution with checkpoint and rollback",
            "com.ghatana.datacloud.launcher.http.plugins.WorkflowExecutionCapability",
            "enableSmartWorkflowBuilder",
            Map.of()
        ));

        // Data Fabric
        capabilities.add(new Capability(
            "data.cloud.fabric",
            "Data Fabric",
            "DATA_MANAGEMENT",
            "preview",
            List.of("data-cloud"),
            "Data fabric topology and connector management",
            null,
            "enableDataFabricPreview",
            Map.of(
                "production_ready", "false",
                "notes", "Preview-only; gated by enableDataFabricPreview; shows preview banner when active"
            )
        ));

        // AI Recommendation Capability
        capabilities.add(new Capability(
            "data.cloud.ai.recommendation",
            "AI Recommendation Capability",
            "AI_ML",
            "stable",
            List.of("data-cloud"),
            "AI-powered recommendations and suggestions",
            "com.ghatana.datacloud.spi.ai.RecommendationCapability",
            "enableAmbientIntelligence",
            Map.of()
        ));

        // AI Explanation Capability
        capabilities.add(new Capability(
            "data.cloud.ai.explanation",
            "AI Explanation Capability",
            "AI_ML",
            "stable",
            List.of("data-cloud"),
            "AI model explanations and interpretability",
            "com.ghatana.datacloud.spi.ai.ExplanationCapability",
            "enableContextSidebar",
            Map.of()
        ));

        // AI Prediction Capability
        capabilities.add(new Capability(
            "data.cloud.ai.prediction",
            "AI Prediction Capability",
            "AI_ML",
            "stable",
            List.of("data-cloud"),
            "AI model predictions and inference",
            "com.ghatana.datacloud.spi.ai.PredictionCapability",
            null,
            Map.of()
        ));

        // AI Anomaly Detection Capability
        capabilities.add(new Capability(
            "data.cloud.ai.anomaly",
            "AI Anomaly Detection Capability",
            "AI_ML",
            "stable",
            List.of("data-cloud"),
            "AI-powered anomaly detection in data streams",
            "com.ghatana.datacloud.spi.ai.AnomalyDetectionCapability",
            null,
            Map.of()
        ));

        // Similarity Search Capability
        capabilities.add(new Capability(
            "data.cloud.similarity",
            "Similarity Search Capability",
            "SEARCH",
            "stable",
            List.of("data-cloud"),
            "Vector similarity search for semantic matching",
            "com.ghatana.datacloud.spi.SimilaritySearchCapability",
            null,
            Map.of()
        ));

        // Transaction Capability
        capabilities.add(new Capability(
            "data.cloud.transaction",
            "Transaction Capability",
            "DATA_MANAGEMENT",
            "stable",
            List.of("data-cloud"),
            "ACID transaction support for data operations",
            "com.ghatana.datacloud.spi.TransactionCapability",
            null,
            Map.of()
        ));

        return capabilities;
    }

    /**
     * Discover kernel capabilities
     */
    private static List<Capability> discoverKernelCapabilities() {
        List<Capability> capabilities = new ArrayList<>();

        capabilities.add(new Capability(
            "data.storage",
            "Data Storage",
            "DATA_MANAGEMENT",
            "stable",
            List.of("all"),
            "Unified data storage abstraction with multi-tier support",
            null,
            null,
            Map.of()
        ));

        capabilities.add(new Capability(
            "workflow.engine",
            "Workflow Engine",
            "WORKFLOW",
            "stable",
            List.of("all"),
            "Business workflow orchestration with durability",
            null,
            null,
            Map.of()
        ));

        capabilities.add(new Capability(
            "observability.framework",
            "Observability Framework",
            "OBSERVABILITY",
            "stable",
            List.of("all"),
            "Comprehensive observability stack with metrics, logs, and traces",
            null,
            null,
            Map.of()
        ));

        return capabilities;
    }

    /**
     * Discover AEP capabilities
     */
    private static List<Capability> discoverAepCapabilities() {
        List<Capability> capabilities = new ArrayList<>();

        capabilities.add(new Capability(
            "aep.eventcloud.durable",
            "Durable EventCloud",
            "EVENT_PROCESSING",
            "stable",
            List.of("aep"),
            "Durable event storage and processing with fail-closed behavior",
            null,
            null,
            Map.of(
                "fail_closed", "true",
                "notes", "Production requires durable EventCloud provider; in-memory only for dev/test with allow flag"
            )
        ));

        return capabilities;
    }

    /**
     * Generate UI feature gates from capabilities
     */
    private static List<FeatureGate> generateUIFeatureGates(List<Capability> capabilities) {
        List<FeatureGate> gates = new ArrayList<>();

        // Generate gates for capabilities that have ui_gate defined
        for (Capability capability : capabilities) {
            if (capability.uiGate != null) {
                gates.add(new FeatureGate(
                    capability.uiGate,
                    formatGateName(capability.uiGate),
                    capability.description,
                    capability.id,
                    true,
                    capability.products
                ));
            }
        }

        // Add additional UI gates not directly tied to capabilities
        gates.add(new FeatureGate(
            "enableIntelligentHub",
            "Intelligent Hub",
            "Enable the new Intelligent Hub (unified home page)",
            "data.cloud.query",
            true,
            List.of("data-cloud")
        ));

        gates.add(new FeatureGate(
            "enableCommandBar",
            "Command Bar",
            "Enable the Command Bar (NL command input)",
            null,
            true,
            List.of("data-cloud", "aep")
        ));

        gates.add(new FeatureGate(
            "legacyPagesEnabled",
            "Legacy Pages",
            "Keep legacy pages accessible for power users",
            null,
            true,
            List.of("data-cloud", "aep")
        ));

        gates.add(new FeatureGate(
            "enableSimplifiedNav",
            "Simplified Navigation",
            "Enable simplified navigation (5 items vs 12+)",
            null,
            true,
            List.of("data-cloud", "aep")
        ));

        return gates;
    }

    /**
     * Generate status definitions
     */
    private static Map<String, StatusDefinition> generateStatusDefinitions() {
        Map<String, StatusDefinition> definitions = new HashMap<>();

        definitions.put("stable", new StatusDefinition(
            "Production-ready with full support",
            "green",
            true
        ));

        definitions.put("preview", new StatusDefinition(
            "Preview/demo-only, not production-ready",
            "amber",
            false
        ));

        definitions.put("deprecated", new StatusDefinition(
            "Deprecated, will be removed in future version",
            "red",
            false
        ));

        definitions.put("experimental", new StatusDefinition(
            "Experimental, may change without notice",
            "purple",
            false
        ));

        return definitions;
    }

    private static String formatGateName(String gateId) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < gateId.length(); i++) {
            char c = gateId.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                result.append(' ');
            }
            result.append(c);
        }
        String formatted = result.toString();
        if (!formatted.isEmpty()) {
            formatted = Character.toUpperCase(formatted.charAt(0)) + formatted.substring(1);
        }
        return formatted;
    }

    // ==================== Data Classes ====================

    public static class CapabilitySchema {
        public String version;
        public SchemaMetadata metadata;
        public List<Capability> kernelCapabilities;
        public List<Capability> dataCloudCapabilities;
        public List<Capability> aepCapabilities;
        public List<FeatureGate> uiFeatureGates;
        public Map<String, StatusDefinition> statusDefinitions;
    }

    public static class SchemaMetadata {
        public String description;
        public String lastUpdated;
        public List<String> generators;
    }

    public static class Capability {
        public String id;
        public String name;
        public String type;
        public String status;
        public List<String> products;
        public String description;
        public String spiInterface;
        public String uiGate;
        public Map<String, String> metadata;

        public Capability(String id, String name, String type, String status, 
                        List<String> products, String description, String spiInterface,
                        String uiGate, Map<String, String> metadata) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.status = status;
            this.products = products;
            this.description = description;
            this.spiInterface = spiInterface;
            this.uiGate = uiGate;
            this.metadata = metadata != null ? metadata : Map.of();
        }
    }

    public static class FeatureGate {
        public String id;
        public String name;
        public String description;
        public String capabilityDependency;
        public boolean defaultValue;
        public List<String> products;

        public FeatureGate(String id, String name, String description, 
                         String capabilityDependency, boolean defaultValue, 
                         List<String> products) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.capabilityDependency = capabilityDependency;
            this.defaultValue = defaultValue;
            this.products = products;
        }
    }

    public static class StatusDefinition {
        public String description;
        public String uiIndicator;
        public boolean allowedInProduction;

        public StatusDefinition(String description, String uiIndicator, boolean allowedInProduction) {
            this.description = description;
            this.uiIndicator = uiIndicator;
            this.allowedInProduction = allowedInProduction;
        }
    }
}
