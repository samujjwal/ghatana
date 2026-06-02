/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.ghatana.datacloud.launcher.http.RouteSecurityMetadata;
import com.ghatana.datacloud.launcher.http.RouteSecurityRegistry;
import com.ghatana.datacloud.launcher.http.RouteSurfaceMapping;
import com.ghatana.datacloud.spi.AggregationCapability;
import com.ghatana.datacloud.spi.StreamingCapability;
import com.ghatana.datacloud.spi.ai.AnomalyDetectionCapability;
import com.ghatana.datacloud.spi.ai.ExplanationCapability;
import com.ghatana.datacloud.spi.ai.PredictionCapability;
import com.ghatana.datacloud.spi.ai.RecommendationCapability;
import com.ghatana.datacloud.spi.SimilaritySearchCapability;
import com.ghatana.datacloud.spi.TransactionCapability;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.activej.http.HttpResponse;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * P2-CAP-1: Surface Schema Generator
 * 
 * Generates the unified surface schema by discovering SPI interfaces
 * and their implementations. This is the single source of truth for all
 * surface-based feature gates, preventing drift between docs/UI/runtime.
 *
 * @doc.type class
 * @doc.purpose Generate unified surface schema from SPI interfaces
 * @doc.layer product
 * @doc.pattern Generator
 */
public final class SurfaceSchemaGenerator {

    private static final Logger log = LoggerFactory.getLogger(SurfaceSchemaGenerator.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());
    private static final String AUDIO_VIDEO_CAPABILITIES_CLASSPATH = "capabilities/audio-video-capabilities.yaml";
    private static final String AUDIO_VIDEO_CAPABILITIES_REPO_PATH =
        "products/data-cloud/planes/action/agent-catalog/capabilities/audio-video-capabilities.yaml";

    private SurfaceSchemaGenerator() {}

    /**
     * Generate the complete surface schema
     */
    public static SurfaceSchema generateSchema() {
        SurfaceSchema schema = new SurfaceSchema();
        schema.version = "1.0.0";
        schema.metadata = new SchemaMetadata();
        schema.metadata.description = "Unified surface schema for Ghatana platform and products, derived from runtime truth registries";
        schema.metadata.lastUpdated = Instant.now().toString();
        schema.metadata.generators = List.of(
            "RouteSecurityRegistry",
            "RouteSurfaceMapping",
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
        // WS1: Add UI-related enum definitions
        schema.lifecycleDefinitions = generateLifecycleDefinitions();
        schema.previewAudienceDefinitions = generatePreviewAudienceDefinitions();
        schema.shellRoleDefinitions = generateShellRoleDefinitions();
        schema.routeTruth = generateRouteTruthSnapshot();

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
        Map<String, Capability> capabilities = new TreeMap<>();

        capabilities.put("aep.eventlog.durable", new Capability(
            "aep.eventlog.durable",
            "Durable EventLog",
            "EVENT_PROCESSING",
            "stable",
            List.of("aep"),
            "Durable event storage and processing with fail-closed behavior",
            null,
            null,
            Map.of(
                "fail_closed", "true",
                "notes", "Production requires durable EventLog provider; in-memory only for dev/test with allow flag"
            )
        ));

        discoverRouteBackedActionPlaneCapabilities(capabilities);
        discoverAudioVideoCapabilities(capabilities);

        return List.copyOf(capabilities.values());
    }

    private static void discoverRouteBackedActionPlaneCapabilities(Map<String, Capability> capabilities) {
        List<RouteCapabilityDescriptor> descriptors = List.of(
            new RouteCapabilityDescriptor(
                "aep.pipelines",
                "Pipelines",
                "ACTION_PLANE",
                "stable",
                List.of("data-cloud", "aep"),
                "Pipeline lifecycle and execution surfaces exposed through the canonical Action Plane routes.",
                "action-plane-routes",
                null,
                metadata -> metadata.canonicalPath().startsWith("/api/v1/action/pipelines")
                    || metadata.canonicalPath().startsWith("/api/v1/pipelines")
            ),
            new RouteCapabilityDescriptor(
                "aep.runs",
                "Runs",
                "ACTION_PLANE",
                "stable",
                List.of("data-cloud", "aep"),
                "Execution and run-ledger surfaces for Action Plane operations.",
                "action-plane-routes",
                null,
                metadata -> metadata.canonicalPath().startsWith("/api/v1/action/executions")
                    || metadata.canonicalPath().startsWith("/api/v1/runs")
            ),
            new RouteCapabilityDescriptor(
                "aep.reviews",
                "Reviews",
                "ACTION_PLANE",
                "stable",
                List.of("data-cloud", "aep"),
                "Human-in-the-loop review queues and review decisions.",
                "action-plane-routes",
                null,
                metadata -> metadata.canonicalPath().contains("/learning/review")
                    || metadata.canonicalPath().startsWith("/api/v1/hitl")
            ),
            new RouteCapabilityDescriptor(
                "aep.learning",
                "Learning",
                "ACTION_PLANE",
                "stable",
                List.of("data-cloud", "aep"),
                "Learning status, trigger, and policy-backed feedback surfaces.",
                "action-plane-routes",
                null,
                metadata -> metadata.canonicalPath().startsWith("/api/v1/action/learning")
                    || metadata.canonicalPath().startsWith("/api/v1/learning")
            ),
            new RouteCapabilityDescriptor(
                "aep.agents",
                "Agents",
                "ACTION_PLANE",
                "stable",
                List.of("data-cloud", "aep"),
                "Agent catalog, runtime, and memory access surfaces.",
                "action-plane-routes",
                null,
                metadata -> metadata.canonicalPath().startsWith("/api/v1/action/agents")
                    || metadata.canonicalPath().startsWith("/api/v1/agents")
                    || metadata.canonicalPath().startsWith("/api/v1/action/memory")
            ),
            new RouteCapabilityDescriptor(
                "aep.patterns",
                "Patterns",
                "ACTION_PLANE",
                "stable",
                List.of("data-cloud", "aep"),
                "Pattern registry and detection surfaces.",
                "action-plane-routes",
                null,
                metadata -> metadata.canonicalPath().startsWith("/api/v1/patterns")
            ),
            new RouteCapabilityDescriptor(
                "aep.deployments",
                "Deployments",
                "ACTION_PLANE",
                "stable",
                List.of("data-cloud", "aep"),
                "Deployment management surfaces for Action Plane assets.",
                "action-plane-routes",
                null,
                metadata -> metadata.canonicalPath().startsWith("/api/v1/deployments")
            ),
            new RouteCapabilityDescriptor(
                "aep.reports",
                "Reports",
                "ACTION_PLANE",
                "stable",
                List.of("data-cloud", "aep"),
                "Operational report generation and retrieval surfaces.",
                "action-plane-routes",
                null,
                metadata -> metadata.canonicalPath().startsWith("/api/v1/reports")
            )
        );

        List<RouteSecurityMetadata> routes = new ArrayList<>(RouteSecurityRegistry.allRoutesIncludingDynamic().values());
        for (RouteCapabilityDescriptor descriptor : descriptors) {
            List<RouteSecurityMetadata> matchingRoutes = routes.stream()
                .filter(descriptor.routePredicate())
                .toList();
            if (matchingRoutes.isEmpty()) {
                continue;
            }

            Map<String, String> metadata = new LinkedHashMap<>();
            metadata.put("route_count", Integer.toString(matchingRoutes.size()));
            metadata.put(
                "required_access_levels",
                matchingRoutes.stream()
                    .map(route -> String.valueOf(route.requiredAccess()))
                    .distinct()
                    .sorted()
                    .reduce((left, right) -> left + "," + right)
                    .orElse("")
            );
            metadata.put(
                "surface_ids",
                matchingRoutes.stream()
                    .map(route -> RouteSurfaceMapping.getSurfaceId(route.method(), route.canonicalPath()))
                    .filter(surfaceId -> surfaceId != null && !surfaceId.isBlank())
                    .distinct()
                    .sorted()
                    .reduce((left, right) -> left + "," + right)
                    .orElse("")
            );
            metadata.put(
                "canonical_routes",
                matchingRoutes.stream()
                    .map(route -> route.method() + " " + route.canonicalPath())
                    .distinct()
                    .sorted()
                    .reduce((left, right) -> left + ";" + right)
                    .orElse("")
            );

            capabilities.put(descriptor.id(), new Capability(
                descriptor.id(),
                descriptor.name(),
                descriptor.type(),
                descriptor.status(),
                descriptor.products(),
                descriptor.description(),
                descriptor.spiInterface(),
                descriptor.uiGate(),
                metadata
            ));
        }
    }

    private static void discoverAudioVideoCapabilities(Map<String, Capability> capabilities) {
        Optional<JsonNode> root = loadAudioVideoCapabilitiesRoot();
        if (root.isEmpty()) {
            return;
        }

        JsonNode tools = root.get().path("tools");
        if (!tools.isArray()) {
            log.warn("Audio-video capabilities catalog does not contain a tools array");
            return;
        }

        for (JsonNode tool : tools) {
            String toolId = tool.path("toolId").asText("");
            if (toolId.isBlank()) {
                continue;
            }

            JsonNode policyTags = tool.path("policyTags");
            boolean highRisk = containsTag(policyTags, "pii-risk") || containsTag(policyTags, "biometric-risk");
            Map<String, String> metadata = new LinkedHashMap<>();
            metadata.put("category", tool.path("category").asText(""));
            metadata.put("transport", tool.path("transport").asText(""));
            metadata.put("remote_endpoint", tool.path("remoteEndpoint").asText(""));
            metadata.put("timeout", tool.path("timeout").asText(""));
            metadata.put("retryable", tool.path("retryable").asText("false"));
            metadata.put("contract_path", tool.path("contractPath").asText(""));
            metadata.put("policy_tags", joinArrayValues(policyTags));
            metadata.put("required_roles", joinArrayValues(tool.path("accessPolicy").path("requiredRoles")));
            metadata.put("blocked_action_classes", joinArrayValues(tool.path("accessPolicy").path("blockedActionClasses")));
            metadata.put("consent_required", Boolean.toString(highRisk));
            metadata.put("endpoint_health", "runtime-probe-required");

            capabilities.put(toolId, new Capability(
                toolId,
                tool.path("name").asText(toolId),
                "ACTION_PLANE_TOOL",
                "stable",
                List.of("data-cloud", "audio-video", "aep"),
                "Audio-video tool capability registered in the Action Plane catalog.",
                tool.path("handlerClass").asText(null),
                null,
                metadata
            ));
        }
    }

    private static Optional<JsonNode> loadAudioVideoCapabilitiesRoot() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader != null) {
            try (InputStream inputStream = classLoader.getResourceAsStream(AUDIO_VIDEO_CAPABILITIES_CLASSPATH)) {
                if (inputStream != null) {
                    return Optional.ofNullable(YAML_MAPPER.readTree(inputStream));
                }
            } catch (Exception exception) {
                log.warn("Failed to load audio-video capabilities catalog from classpath: {}", exception.getMessage());
            }
        }

        Path repoRoot = resolveRepoRoot();
        if (repoRoot == null) {
            log.warn("Unable to resolve repository root while loading audio-video capabilities catalog");
            return Optional.empty();
        }

        Path capabilitiesPath = repoRoot.resolve(AUDIO_VIDEO_CAPABILITIES_REPO_PATH);
        if (!Files.exists(capabilitiesPath)) {
            log.warn("Audio-video capabilities catalog not found at {}", capabilitiesPath);
            return Optional.empty();
        }

        try {
            return Optional.ofNullable(YAML_MAPPER.readTree(capabilitiesPath.toFile()));
        } catch (Exception exception) {
            log.warn("Failed to parse audio-video capabilities catalog {}: {}", capabilitiesPath, exception.getMessage());
            return Optional.empty();
        }
    }

    private static Path resolveRepoRoot() {
        Path current = Paths.get("").toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("gradlew"))) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    private static String joinArrayValues(JsonNode values) {
        if (!values.isArray()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (JsonNode value : values) {
            String text = value.asText("");
            if (!text.isBlank()) {
                parts.add(text);
            }
        }
        return String.join(",", parts);
    }

    private static boolean containsTag(JsonNode tags, String expected) {
        if (!tags.isArray()) {
            return false;
        }
        for (JsonNode tag : tags) {
            if (expected.equals(tag.asText())) {
                return true;
            }
        }
        return false;
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

    /**
     * WS1: Generate lifecycle definitions for UI surface lifecycle field
     */
    private static Map<String, LifecycleDefinition> generateLifecycleDefinitions() {
        Map<String, LifecycleDefinition> definitions = new HashMap<>();

        definitions.put("stable", new LifecycleDefinition(
            "Production-ready, fully supported",
            "green",
            true,
            true
        ));

        definitions.put("preview", new LifecycleDefinition(
            "Preview/demo-only, not production-ready",
            "amber",
            false,
            false
        ));

        definitions.put("deprecated", new LifecycleDefinition(
            "Deprecated, will be removed in future version",
            "red",
            false,
            false
        ));

        definitions.put("experimental", new LifecycleDefinition(
            "Experimental, may change without notice",
            "purple",
            false,
            false
        ));

        return definitions;
    }

    /**
     * WS1: Generate preview audience definitions
     */
    private static Map<String, PreviewAudienceDefinition> generatePreviewAudienceDefinitions() {
        Map<String, PreviewAudienceDefinition> definitions = new HashMap<>();

        definitions.put("operator-preview", new PreviewAudienceDefinition(
            "Preview available to operators only",
            "admin"
        ));

        definitions.put("beta-users", new PreviewAudienceDefinition(
            "Preview available to beta users",
            "viewer"
        ));

        definitions.put("internal", new PreviewAudienceDefinition(
            "Preview available to internal users only",
            "admin"
        ));

        return definitions;
    }

    /**
     * WS1: Generate shell role definitions
     */
    private static Map<String, ShellRoleDefinition> generateShellRoleDefinitions() {
        Map<String, ShellRoleDefinition> definitions = new HashMap<>();

        definitions.put("viewer", new ShellRoleDefinition(
            "Read-only access to most surfaces",
            10
        ));

        definitions.put("editor", new ShellRoleDefinition(
            "Read and write access to non-critical surfaces",
            50
        ));

        definitions.put("admin", new ShellRoleDefinition(
            "Full access to all surfaces including critical operations",
            100
        ));

        return definitions;
    }

    private static RouteTruthSnapshot generateRouteTruthSnapshot() {
        RouteTruthSnapshot snapshot = new RouteTruthSnapshot();
        snapshot.generatedAt = Instant.now().toString();

        Map<String, Integer> routesByLegacyStatus = new TreeMap<>();
        Map<String, Integer> routesByRuntimeTruthSurface = new TreeMap<>();
        Map<String, Integer> routesBySurfaceId = new TreeMap<>();
        Set<String> surfaceIds = new TreeSet<>(RouteSurfaceMapping.getAllSurfaceIds());

        int mappedRouteCount = 0;
        for (RouteSecurityMetadata route : RouteSecurityRegistry.allRoutesIncludingDynamic().values()) {
            increment(routesByLegacyStatus, route.legacyStatus());
            increment(routesByRuntimeTruthSurface, route.runtimeTruthSurface());

            String surfaceId = RouteSurfaceMapping.getSurfaceId(route.method(), route.canonicalPath());
            if (surfaceId != null) {
                mappedRouteCount++;
                increment(routesBySurfaceId, surfaceId);
            }
        }

        snapshot.routeCount = RouteSecurityRegistry.sizeIncludingDynamic();
        snapshot.mappedRouteCount = mappedRouteCount;
        snapshot.unmappedRouteCount = snapshot.routeCount - mappedRouteCount;
        snapshot.routesByLegacyStatus = routesByLegacyStatus;
        snapshot.routesByRuntimeTruthSurface = routesByRuntimeTruthSurface;
        snapshot.routesBySurfaceId = routesBySurfaceId;
        snapshot.surfaceIds = List.copyOf(surfaceIds);
        return snapshot;
    }

    private static void increment(Map<String, Integer> counts, String key) {
        String normalizedKey = key == null || key.isBlank() ? "unknown" : key;
        counts.merge(normalizedKey, 1, Integer::sum);
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

    @SuppressFBWarnings(
        value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD",
        justification = "Public fields are intentionally serialized by Jackson as the route surface schema contract.")
    public static class SurfaceSchema {
        public String version;
        public SchemaMetadata metadata;
        public List<Capability> kernelCapabilities;
        public List<Capability> dataCloudCapabilities;
        public List<Capability> aepCapabilities;
        public List<FeatureGate> uiFeatureGates;
        public Map<String, StatusDefinition> statusDefinitions;
        // WS1: UI-related enum definitions
        public Map<String, LifecycleDefinition> lifecycleDefinitions;
        public Map<String, PreviewAudienceDefinition> previewAudienceDefinitions;
        public Map<String, ShellRoleDefinition> shellRoleDefinitions;
        public RouteTruthSnapshot routeTruth;
    }

    @SuppressFBWarnings(
        value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD",
        justification = "Public fields are intentionally serialized by Jackson as the route surface schema contract.")
    public static class SchemaMetadata {
        public String description;
        public String lastUpdated;
        public List<String> generators;
    }

    @SuppressFBWarnings(
        value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD",
        justification = "Public fields are intentionally serialized by Jackson as the route surface schema contract.")
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

    @SuppressFBWarnings(
        value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD",
        justification = "Public fields are intentionally serialized by Jackson as the route surface schema contract.")
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

    @SuppressFBWarnings(
        value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD",
        justification = "Public fields are intentionally serialized by Jackson as the route surface schema contract.")
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

    @SuppressFBWarnings(
        value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD",
        justification = "Public fields are intentionally serialized by Jackson as the route surface schema contract.")
    public static class LifecycleDefinition {
        public String description;
        public String uiIndicator;
        public boolean allowedInProduction;
        public boolean discoverable;

        public LifecycleDefinition(String description, String uiIndicator, boolean allowedInProduction, boolean discoverable) {
            this.description = description;
            this.uiIndicator = uiIndicator;
            this.allowedInProduction = allowedInProduction;
            this.discoverable = discoverable;
        }
    }

    @SuppressFBWarnings(
        value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD",
        justification = "Public fields are intentionally serialized by Jackson as the route surface schema contract.")
    public static class PreviewAudienceDefinition {
        public String description;
        public String minimumRole;

        public PreviewAudienceDefinition(String description, String minimumRole) {
            this.description = description;
            this.minimumRole = minimumRole;
        }
    }

    @SuppressFBWarnings(
        value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD",
        justification = "Public fields are intentionally serialized by Jackson as the route surface schema contract.")
    public static class ShellRoleDefinition {
        public String description;
        public int privilegeLevel;

        public ShellRoleDefinition(String description, int privilegeLevel) {
            this.description = description;
            this.privilegeLevel = privilegeLevel;
        }
    }

    @SuppressFBWarnings(
        value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD",
        justification = "Public fields are intentionally serialized by Jackson as the route surface schema contract.")
    public static class RouteTruthSnapshot {
        public String generatedAt;
        public int routeCount;
        public int mappedRouteCount;
        public int unmappedRouteCount;
        public List<String> surfaceIds;
        public Map<String, Integer> routesByLegacyStatus;
        public Map<String, Integer> routesByRuntimeTruthSurface;
        public Map<String, Integer> routesBySurfaceId;
    }

    private record RouteCapabilityDescriptor(
        String id,
        String name,
        String type,
        String status,
        List<String> products,
        String description,
        String spiInterface,
        String uiGate,
        Predicate<RouteSecurityMetadata> routePredicate
    ) {}
}
