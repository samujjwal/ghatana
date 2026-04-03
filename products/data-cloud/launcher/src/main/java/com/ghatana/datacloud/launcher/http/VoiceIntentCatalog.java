package com.ghatana.datacloud.launcher.http;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Catalogue of operational voice intents handled by Data-Cloud.
 *
 * <p>Each {@link VoiceIntent} maps a spoken intent name to:
 * <ul>
 *   <li>The canonical backend HTTP method + path template.</li>
 *   <li>Required and optional parameters that must be extracted from the utterance.</li>
 *   <li>A minimal description used for routing confidence decisions.</li>
 *   <li>The {@link EndpointSensitivity} of the target route.</li>
 * </ul>
 *
 * <h2>Security</h2>
 * The catalog does not store API keys or credentials.  Each resolved route is
 * validated by the same {@link DataCloudSecurityFilter} that governs text-based
 * requests, ensuring voice and keyboard channels have policy parity.
 *
 * <h2>Extensibility</h2>
 * New intents are registered by adding entries to {@link #ALL}.  The
 * {@link VoiceGatewayHandler} resolves intents by exact name match or similarity
 * scoring when an LLM classifier is unavailable.
 *
 * @doc.type class
 * @doc.purpose Operational voice intent catalog for Data-Cloud
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public final class VoiceIntentCatalog {

    private VoiceIntentCatalog() {}

    /**
     * All registered intents in priority order (most-used first).
     */
    public static final List<VoiceIntent> ALL = List.of(

        // ── Entity operations ───────────────────────────────────────────────
        new VoiceIntent(
            "query_entities",
            "GET", "/api/v1/entities/:collection",
            "List or filter entities in a collection",
            List.of("collection"),
            List.of("limit", "offset", "filter"),
            EndpointSensitivity.INTERNAL),

        new VoiceIntent(
            "get_entity",
            "GET", "/api/v1/entities/:collection/:id",
            "Fetch a single entity by ID",
            List.of("collection", "id"),
            List.of(),
            EndpointSensitivity.INTERNAL),

        new VoiceIntent(
            "create_entity",
            "POST", "/api/v1/entities/:collection",
            "Create a new entity in a collection",
            List.of("collection"),
            List.of(),
            EndpointSensitivity.SENSITIVE),

        new VoiceIntent(
            "delete_entity",
            "DELETE", "/api/v1/entities/:collection/:id",
            "Delete an entity by ID",
            List.of("collection", "id"),
            List.of(),
            EndpointSensitivity.CRITICAL),

        // ── Event operations ────────────────────────────────────────────────
        new VoiceIntent(
            "query_events",
            "GET", "/api/v1/events",
            "Query event log with optional filters",
            List.of(),
            List.of("tenantId", "type", "limit", "since"),
            EndpointSensitivity.INTERNAL),

        new VoiceIntent(
            "append_event",
            "POST", "/api/v1/events",
            "Append a new event to the event log",
            List.of("type"),
            List.of("payload"),
            EndpointSensitivity.SENSITIVE),

        // ── Pipeline operations ─────────────────────────────────────────────
        new VoiceIntent(
            "list_pipelines",
            "GET", "/api/v1/pipelines",
            "List all pipelines for a tenant",
            List.of(),
            List.of("tenantId", "limit"),
            EndpointSensitivity.INTERNAL),

        new VoiceIntent(
            "get_pipeline_status",
            "GET", "/api/v1/pipelines/:pipelineId",
            "Get the current status of a pipeline",
            List.of("pipelineId"),
            List.of(),
            EndpointSensitivity.INTERNAL),

        new VoiceIntent(
            "create_pipeline",
            "POST", "/api/v1/pipelines",
            "Create or register a new pipeline",
            List.of("name"),
            List.of("description", "steps"),
            EndpointSensitivity.SENSITIVE),

        // ── Analytics operations ────────────────────────────────────────────
        new VoiceIntent(
            "run_analytics_query",
            "POST", "/api/v1/analytics/query",
            "Execute an analytics query",
            List.of("query"),
            List.of("parameters", "timeout"),
            EndpointSensitivity.SENSITIVE),

        new VoiceIntent(
            "get_analytics_result",
            "GET", "/api/v1/analytics/query/:queryId",
            "Retrieve the result of a previously submitted query",
            List.of("queryId"),
            List.of(),
            EndpointSensitivity.INTERNAL),

        // ── Brain / workspace ───────────────────────────────────────────────
        new VoiceIntent(
            "get_workspace_spotlight",
            "GET", "/api/v1/brain/workspace",
            "Get current brain workspace spotlight items",
            List.of(),
            List.of("tenantId", "limit"),
            EndpointSensitivity.SENSITIVE),

        new VoiceIntent(
            "get_brain_status",
            "GET", "/api/v1/brain/health",
            "Check the health of the brain service",
            List.of(),
            List.of(),
            EndpointSensitivity.INTERNAL),

        // ── Memory operations ───────────────────────────────────────────────
        new VoiceIntent(
            "search_agent_memory",
            "POST", "/api/v1/memory/:agentId/search",
            "Search agent memory by semantic query",
            List.of("agentId", "query"),
            List.of("tier", "limit"),
            EndpointSensitivity.SENSITIVE),

        new VoiceIntent(
            "get_agent_memory",
            "GET", "/api/v1/memory/:agentId",
            "Retrieve all memory records for an agent",
            List.of("agentId"),
            List.of("tier"),
            EndpointSensitivity.SENSITIVE),

        // ── Learning operations ──────────────────────────────────────────────
        new VoiceIntent(
            "get_learning_status",
            "GET", "/api/v1/learning/status",
            "Check the status of the learning pipeline",
            List.of(),
            List.of("tenantId"),
            EndpointSensitivity.INTERNAL),

        new VoiceIntent(
            "trigger_learning",
            "POST", "/api/v1/learning/trigger",
            "Trigger a learning cycle",
            List.of(),
            List.of("scope"),
            EndpointSensitivity.SENSITIVE),

        // ── AI/ML operations ────────────────────────────────────────────────
        new VoiceIntent(
            "list_models",
            "GET", "/api/v1/models",
            "List registered ML models and their promotion status",
            List.of(),
            List.of("tenantId", "status"),
            EndpointSensitivity.INTERNAL)
    );

    /**
     * Find an intent by its exact name.
     *
     * @param intentName the intent name (case-insensitive)
     * @return the matching intent, or empty if not found
     */
    public static Optional<VoiceIntent> findByName(String intentName) {
        if (intentName == null) return Optional.empty();
        String lower = intentName.toLowerCase().replace('-', '_').replace(' ', '_');
        return ALL.stream()
            .filter(i -> i.name().equalsIgnoreCase(lower))
            .findFirst();
    }

    /**
     * Find candidate intents whose keywords match the given utterance.
     *
     * <p>This is a lightweight keyword-overlap heuristic used as a fallback
     * when the LLM classifier is unavailable.
     *
     * @param utterance normalised utterance text
     * @return ordered list of candidate intents (highest-match first)
     */
    public static List<VoiceIntent> findCandidates(String utterance) {
        if (utterance == null || utterance.isBlank()) return List.of();
        String lower = utterance.toLowerCase();
        return ALL.stream()
            .filter(i -> {
                // Simple keyword overlap: intent name tokens in utterance
                String[] tokens = i.name().split("_");
                int overlap = 0;
                for (String t : tokens) {
                    if (lower.contains(t)) overlap++;
                }
                return overlap >= Math.max(1, tokens.length / 2);
            })
            .limit(3)
            .toList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // VoiceIntent value object
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Immutable descriptor for a single voice intent.
     *
     * @param name             unique intent identifier (snake_case)
     * @param httpMethod       target HTTP method
     * @param pathTemplate     target path with {@code :param} placeholders
     * @param description      human-readable description
     * @param requiredParams   parameters that must be present
     * @param optionalParams   optional parameters
     * @param sensitivity      endpoint sensitivity level
     */
    public record VoiceIntent(
        String name,
        String httpMethod,
        String pathTemplate,
        String description,
        List<String> requiredParams,
        List<String> optionalParams,
        EndpointSensitivity sensitivity
    ) {
        public VoiceIntent {
            requiredParams = List.copyOf(requiredParams);
            optionalParams = List.copyOf(optionalParams);
        }

        /**
         * Resolves the concrete path from a parameter map.
         *
         * @param params map of parameter names to values
         * @return resolved path string
         */
        public String resolvePath(Map<String, String> params) {
            String path = pathTemplate;
            for (Map.Entry<String, String> e : params.entrySet()) {
                path = path.replace(":" + e.getKey(), e.getValue());
            }
            return path;
        }

        /**
         * Validates that all required parameters are present in the given map.
         *
         * @param params parameter map
         * @return list of missing required parameter names (empty means valid)
         */
        public List<String> missingRequiredParams(Map<String, String> params) {
            return requiredParams.stream()
                .filter(p -> !params.containsKey(p) || params.get(p).isBlank())
                .toList();
        }
    }
}
