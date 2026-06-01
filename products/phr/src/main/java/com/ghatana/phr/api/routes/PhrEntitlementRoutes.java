package com.ghatana.phr.api.routes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.cache.IdentityAwareBoundedCache;
import com.ghatana.platform.http.security.ProductRouteEntitlement;
import com.ghatana.platform.http.security.RouteEntitlementEvaluator;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpMethod;
import io.activej.http.HttpResponse;
import io.activej.http.RoutingServlet;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Route entitlement API for the PHR product.
 * <p>
 * Handles route/content entitlement requests with identity-aware caching.
 * </p>
 *
 * @doc.type class
 * @doc.purpose Route entitlement handlers for PHR
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 */
public final class PhrEntitlementRoutes {
    private static final String ROUTE_ENTITLEMENTS_PATH = "/api/v1/route-entitlements";


    private static final Logger LOG = LoggerFactory.getLogger(PhrEntitlementRoutes.class);
    private static final String CONTENT_JSON = "application/json";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Path ROUTE_CONTRACT_PATH = resolveRouteContractPath();

    private final Eventloop eventloop;
    private final RouteEntitlementEvaluator routeEntitlementEvaluator;
    private final IdentityAwareBoundedCache<String, Map<String, Object>> entitlementCache;
    private List<ProductRouteEntitlement.RouteEntitlement> cachedRoutes;
    private Map<String, Integer> roleOrder;
    private Map<String, Map<String, Object>> cachedRouteMeta;
    private volatile boolean contractLoaded = false;

    public PhrEntitlementRoutes(
            Eventloop eventloop,
            RouteEntitlementEvaluator routeEntitlementEvaluator,
            IdentityAwareBoundedCache<String, Map<String, Object>> entitlementCache) {
        this.eventloop = eventloop;
        this.routeEntitlementEvaluator = routeEntitlementEvaluator;
        this.entitlementCache = entitlementCache;
        loadRouteContract();
    }

    private static Path resolveRouteContractPath() {
        Path configuredPath = Path.of(System.getProperty(
            "phr.route.contract.path",
            "products/phr/config/phr-route-contract.json"
        ));
        if (configuredPath.isAbsolute()) {
            return configuredPath.normalize();
        }

        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(configuredPath).normalize();
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }

        return Path.of("").toAbsolutePath().resolve(configuredPath).normalize();
    }

    /**
     * Loads the route contract from the canonical JSON file.
     * This ensures web and backend use the same source of truth for routes.
     *
     * E-001, E-002, E-008: No fallback route list or role order - fail closed if contract missing/invalid.
     * E-003: Validate loaded route contract fields.
     */
    private void loadRouteContract() {
        try {
            Path routeContractPath = ROUTE_CONTRACT_PATH;
            if (!Files.exists(routeContractPath)) {
                LOG.error("Route contract file not found at {} - failing closed", routeContractPath);
                this.contractLoaded = false;
                return;
            }

            String json = Files.readString(routeContractPath);
            JsonNode root = OBJECT_MAPPER.readTree(json);

            // Validate required top-level fields
            if (!root.has("routes") || !root.has("roleOrder")) {
                LOG.error("Route contract missing required fields (routes, roleOrder) - failing closed");
                this.contractLoaded = false;
                return;
            }

            Map<String, Integer> loadedRoleOrder = new java.util.LinkedHashMap<>();
            JsonNode roleOrderNode = root.path("roleOrder");
            if (!roleOrderNode.isObject()) {
                LOG.error("Route contract roleOrder is not an object - failing closed");
                this.contractLoaded = false;
                return;
            }
            for (String allowedRole : PhrRouteSupport.ALLOWED_ROLES) {
                if (!roleOrderNode.has(allowedRole)) {
                    LOG.error("Route contract roleOrder missing role {} - failing closed", allowedRole);
                    this.contractLoaded = false;
                    return;
                }
                loadedRoleOrder.put(allowedRole, roleOrderNode.path(allowedRole).asInt());
            }

            // Load and validate routes
            JsonNode routesNode = root.path("routes");
            if (!routesNode.isArray()) {
                LOG.error("Route contract routes is not an array - failing closed");
                this.contractLoaded = false;
                return;
            }

            List<ProductRouteEntitlement.RouteEntitlement> loadedRoutes = new ArrayList<>();
            Map<String, Map<String, Object>> loadedMeta = new java.util.LinkedHashMap<>();
            for (JsonNode routeNode : routesNode) {
                if (!routeNode.has("path") || !routeNode.has("label") || !routeNode.has("minimumRole")
                        || !routeNode.has("stability")) {
                    LOG.error("Route missing required fields (path, label, minimumRole, stability) - failing closed");
                    this.contractLoaded = false;
                    return;
                }

                String path = routeNode.path("path").asText();
                String label = routeNode.path("label").asText();
                String minimumRole = routeNode.path("minimumRole").asText();
                String stability = routeNode.path("stability").asText();

                if (!PhrRouteSupport.isAllowedRole(minimumRole)) {
                    LOG.error("Route has unknown minimum role: {} - failing closed", minimumRole);
                    this.contractLoaded = false;
                    return;
                }
                if (!List.of("stable", "preview", "blocked", "hidden", "deferred", "removed").contains(stability)) {
                    LOG.error("Route {} has invalid stability {} - failing closed", path, stability);
                    this.contractLoaded = false;
                    return;
                }

                List<String> actions = new ArrayList<>();
                JsonNode actionsNode = routeNode.path("actions");
                if (!actionsNode.isArray()) {
                    LOG.error("Route actions is not an array - failing closed");
                    this.contractLoaded = false;
                    return;
                }
                for (JsonNode action : actionsNode) {
                    actions.add(action.asText());
                }

                List<String> cards = new ArrayList<>();
                JsonNode cardsNode = routeNode.path("cards");
                if (!cardsNode.isArray()) {
                    LOG.error("Route cards is not an array - failing closed");
                    this.contractLoaded = false;
                    return;
                }
                for (JsonNode card : cardsNode) {
                    cards.add(card.asText());
                }

                List<String> personas = new ArrayList<>();
                JsonNode personasNode = routeNode.path("personas");
                if (!personasNode.isArray()) {
                    LOG.error("Route {} personas is not an array - failing closed", path);
                    this.contractLoaded = false;
                    return;
                }
                for (JsonNode persona : personasNode) {
                    String personaValue = persona.asText();
                    if (!PhrRouteSupport.isAllowedRole(personaValue)) {
                        LOG.error("Route {} has unknown persona {} - failing closed", path, personaValue);
                        this.contractLoaded = false;
                        return;
                    }
                    personas.add(personaValue);
                }

                List<String> tiers = new ArrayList<>();
                JsonNode tiersNode = routeNode.path("tiers");
                if (!tiersNode.isArray()) {
                    LOG.error("Route {} tiers is not an array - failing closed", path);
                    this.contractLoaded = false;
                    return;
                }
                for (JsonNode tier : tiersNode) {
                    tiers.add(tier.asText());
                }

                if ("stable".equals(stability)) {
                    for (String field : List.of("apiEndpoint", "policyId", "testId")) {
                        if (!routeNode.has(field) || routeNode.path(field).asText().isBlank()) {
                            LOG.error("Stable route {} missing {} - failing closed", path, field);
                            this.contractLoaded = false;
                            return;
                        }
                    }
                }

                Map<String, Object> meta = new java.util.LinkedHashMap<>();
                meta.put("stability", stability);
                String apiEndpoint = routeNode.path("apiEndpoint").asText(null);
                String policyId = routeNode.path("policyId").asText(null);
                String testId = routeNode.path("testId").asText(null);
                String group = routeNode.path("group").asText(null);
                String description = routeNode.path("description").asText(null);
                String apiContractId = routeNode.path("apiContractId").asText(null);
                String dtoSchemaId = routeNode.path("dtoSchemaId").asText(null);
                String auditRequirement = routeNode.path("auditRequirement").asText(null);
                String phiSensitivity = routeNode.path("phiSensitivity").asText(null);
                String cachePolicy = routeNode.path("cachePolicy").asText(null);
                String offlinePolicy = routeNode.path("offlinePolicy").asText(null);
                if (apiEndpoint != null && !apiEndpoint.isBlank()) meta.put("apiEndpoint", apiEndpoint);
                if (policyId != null && !policyId.isBlank()) meta.put("policyId", policyId);
                if (testId != null && !testId.isBlank()) meta.put("testId", testId);
                if (group != null && !group.isBlank()) meta.put("group", group);
                if (description != null && !description.isBlank()) meta.put("description", description);
                if (apiContractId != null && !apiContractId.isBlank()) meta.put("apiContractId", apiContractId);
                if (dtoSchemaId != null && !dtoSchemaId.isBlank()) meta.put("dtoSchemaId", dtoSchemaId);
                if (auditRequirement != null && !auditRequirement.isBlank()) meta.put("auditRequirement", auditRequirement);
                if (phiSensitivity != null && !phiSensitivity.isBlank()) meta.put("phiSensitivity", phiSensitivity);
                if (cachePolicy != null && !cachePolicy.isBlank()) meta.put("cachePolicy", cachePolicy);
                if (offlinePolicy != null && !offlinePolicy.isBlank()) meta.put("offlinePolicy", offlinePolicy);
                JsonNode pluginDependenciesNode = routeNode.path("pluginDependencies");
                if (pluginDependenciesNode.isArray()) {
                    List<String> pluginDependencies = new ArrayList<>();
                    for (JsonNode pluginDependency : pluginDependenciesNode) {
                        String pluginDependencyValue = pluginDependency.asText();
                        if (!pluginDependencyValue.isBlank()) {
                            pluginDependencies.add(pluginDependencyValue);
                        }
                    }
                    if (!pluginDependencies.isEmpty()) {
                        meta.put("pluginDependencies", pluginDependencies);
                    }
                }
                loadedMeta.put(path, meta);

                if (!"stable".equals(stability)) {
                    continue;
                }
                if (!hasWebSurface(routeNode)) {
                    continue;
                }

                loadedRoutes.add(new ProductRouteEntitlement.RouteEntitlement(
                    path,
                    label,
                    minimumRole,
                    personas,
                    tiers,
                    actions,
                    cards,
                    stability
                ));
            }
            this.cachedRoutes = loadedRoutes;
            this.roleOrder = loadedRoleOrder;
            this.cachedRouteMeta = loadedMeta;
            this.contractLoaded = true;

            LOG.info("Loaded {} routes from contract file {}", loadedRoutes.size(), routeContractPath);
        } catch (Exception ex) {
            LOG.error("Failed to load route contract - failing closed", ex);
            this.contractLoaded = false;
        }
    }

    private static boolean hasWebSurface(JsonNode routeNode) {
        JsonNode surfaceNode = routeNode.path("surface");
        if (!surfaceNode.isArray()) {
            return false;
        }
        for (JsonNode surface : surfaceNode) {
            if ("web".equals(surface.asText())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the routing servlet for entitlement endpoints.
     *
     * @return routing servlet; never null
     */
    public AsyncServlet getServlet() {
        return RoutingServlet.builder(eventloop)
            .with(HttpMethod.GET, "/", this::handleRouteEntitlements)
            .build();
    }

    private Promise<HttpResponse> handleRouteEntitlements(io.activej.http.HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        // E-001, E-002: Fail closed if route contract not loaded
        if (!contractLoaded) {
            return PhrRouteSupport.errorResponse(503, "ROUTE_CONTRACT_NOT_LOADED",
                "Route contract not loaded or invalid - service unavailable",
                correlationId);
        }

        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, routeContextErrorCode(ex.getMessage()), ex.getMessage(), correlationId);
        }

        // E-005: Use tenant/principal/persona/tier in cache key
        String cacheKey = String.format("route-entitlements:%s:%s:%s:%s:%s",
            context.tenantId(), context.principalId(), context.role(), context.persona(), context.tier());

        // Try cache first
        java.util.Optional<Map<String, Object>> cached = entitlementCache.get(
            context.principalId(),
            context.tenantId(),
            ROUTE_ENTITLEMENTS_PATH,
            cacheKey
        );
        if (cached.isPresent()) {
            return jsonResponse(200, cached.get(), context.correlationId());
        }

        // Use loaded route contract with built-in role order
        List<ProductRouteEntitlement.RouteEntitlement> routes = routeEntitlementEvaluator.filterByRole(
            cachedRoutes, context.role(), roleOrder);
        List<ProductRouteEntitlement.ActionEntitlement> actions =
            routeEntitlementEvaluator.filterActionsByRole(routes, context.role(), roleOrder);
        List<ProductRouteEntitlement.CardEntitlement> cards =
            routeEntitlementEvaluator.filterCardsByRole(routes, context.role(), roleOrder);

        ProductRouteEntitlement entitlement = new ProductRouteEntitlement(
            "phr",
            context.principalId(),
            context.tenantId(),
            context.role(),
            context.persona(),
            context.tier(),
            context.correlationId(),
            routes,
            actions,
            cards
        );

        // E-007: Use platform contract serialization then augment with PHR-specific route metadata
        Map<String, Object> entitlementMap = new java.util.LinkedHashMap<>(entitlement.toMap());
        if (cachedRouteMeta != null && !cachedRouteMeta.isEmpty()) {
            Map<String, Map<String, Object>> filteredMeta = new java.util.LinkedHashMap<>();
            for (ProductRouteEntitlement.RouteEntitlement route : routes) {
                Map<String, Object> meta = cachedRouteMeta.get(route.path());
                if (meta != null) {
                    filteredMeta.put(route.path(), meta);
                }
            }
            entitlementMap.put("routeMeta", filteredMeta);
        }

        // Cache for 5 minutes (300 seconds)
        entitlementCache.put(context.principalId(), context.tenantId(), ROUTE_ENTITLEMENTS_PATH, cacheKey, entitlementMap);

        return jsonResponse(200, entitlementMap, context.correlationId());
    }


    private static Promise<HttpResponse> jsonResponse(int statusCode, Object body, String correlationId) {
        String json = com.ghatana.platform.core.util.JsonUtils.toJsonSafe(body);
        if (json == null) {
            json = "{\"error\":\"SERIALIZATION_ERROR\",\"message\":\"Failed to serialize response\"}";
            statusCode = 500;
        }
        return Promise.of(io.activej.http.HttpResponse.ofCode(statusCode)
                .withHeader(io.activej.http.HttpHeaders.CONTENT_TYPE, CONTENT_JSON)
                .withHeader(io.activej.http.HttpHeaders.of("X-Correlation-ID"), correlationId)
                .withJson(json)
                .build());
    }

    private static String routeContextErrorCode(String message) {
        if (message == null) {
            return "INVALID_ROUTE_CONTEXT";
        }
        if (message.startsWith("X-Principal")) {
            return "MISSING_PRINCIPAL";
        }
        if (message.startsWith("X-Tenant")) {
            return "MISSING_TENANT";
        }
        if (message.startsWith("X-Role")) {
            return "MISSING_ROLE";
        }
        if (message.startsWith("Unrecognised role")) {
            return "INVALID_ROLE";
        }
        return "INVALID_ROUTE_CONTEXT";
    }
}
