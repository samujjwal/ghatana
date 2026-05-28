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
import java.util.Optional;

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

    private static final Logger LOG = LoggerFactory.getLogger(PhrEntitlementRoutes.class);
    private static final String CONTENT_JSON = "application/json";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Path ROUTE_CONTRACT_PATH = resolveRouteContractPath();

    private final Eventloop eventloop;
    private final RouteEntitlementEvaluator routeEntitlementEvaluator;
    private final IdentityAwareBoundedCache<String, Map<String, Object>> entitlementCache;
    private List<ProductRouteEntitlement.RouteEntitlement> cachedRoutes;
    private Map<String, Integer> roleOrder;
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
            for (JsonNode routeNode : routesNode) {
                // Validate required route fields
                if (!routeNode.has("path") || !routeNode.has("label") || !routeNode.has("minimumRole")) {
                    LOG.error("Route missing required fields (path, label, minimumRole) - failing closed");
                    this.contractLoaded = false;
                    return;
                }

                String path = routeNode.path("path").asText();
                String label = routeNode.path("label").asText();
                String minimumRole = routeNode.path("minimumRole").asText();

                // Validate minimum role
                if (!PhrRouteSupport.ALLOWED_ROLES.contains(minimumRole)) {
                    LOG.error("Route has unknown minimum role: {} - failing closed", minimumRole);
                    this.contractLoaded = false;
                    return;
                }

                // Parse actions (required array)
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
                
                // Parse cards (required array)
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
                
                // Parse personas (default to all roles if not specified)
                List<String> personas = new ArrayList<>();
                JsonNode personasNode = routeNode.path("personas");
                if (personasNode.isArray()) {
                    for (JsonNode persona : personasNode) {
                        personas.add(persona.asText());
                    }
                } else {
                    personas = List.of("patient", "caregiver", "clinician", "admin", "fchv");
                }
                
                // Parse tiers (default to core if not specified)
                List<String> tiers = new ArrayList<>();
                JsonNode tiersNode = routeNode.path("tiers");
                if (tiersNode.isArray()) {
                    for (JsonNode tier : tiersNode) {
                        tiers.add(tier.asText());
                    }
                } else {
                    tiers = List.of("core");
                }

                // E-004: Parse stability/visibility
                String stability = routeNode.has("stability") ? routeNode.path("stability").asText() : "stable";

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
            this.contractLoaded = true;
            
            LOG.info("Loaded {} routes from contract file {}", loadedRoutes.size(), routeContractPath);
        } catch (Exception ex) {
            LOG.error("Failed to load route contract - failing closed", ex);
            this.contractLoaded = false;
        }
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
        // E-001, E-002: Fail closed if route contract not loaded
        if (!contractLoaded) {
            return PhrRouteSupport.errorResponse(503, "ROUTE_CONTRACT_NOT_LOADED", 
                "Route contract not loaded or invalid - service unavailable");
        }

        // Extract and validate authentication context from request headers
        // Fail closed: require explicit identity headers for production route entitlement
        String principalId = request.getHeader(io.activej.http.HttpHeaders.of("X-Principal-Id"));
        String tenantId = request.getHeader(io.activej.http.HttpHeaders.of("X-Tenant-Id"));
        String role = request.getHeader(io.activej.http.HttpHeaders.of("X-Role"));
        String persona = request.getHeader(io.activej.http.HttpHeaders.of("X-Persona"));
        String tier = request.getHeader(io.activej.http.HttpHeaders.of("X-Tier"));
        String correlationId = request.getHeader(io.activej.http.HttpHeaders.of("X-Correlation-ID"));

        // E-006: Generate correlation ID if not provided
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = java.util.UUID.randomUUID().toString();
        }

        // Validate required headers - fail closed for missing identity
        if (principalId == null || principalId.isBlank()) {
            return PhrRouteSupport.errorResponse(400, "MISSING_PRINCIPAL", "X-Principal-Id header is required", correlationId);
        }
        if (tenantId == null || tenantId.isBlank()) {
            return PhrRouteSupport.errorResponse(400, "MISSING_TENANT", "X-Tenant-Id header is required", correlationId);
        }
        if (role == null || role.isBlank()) {
            return PhrRouteSupport.errorResponse(400, "MISSING_ROLE", "X-Role header is required", correlationId);
        }

        // Normalize role to lower-case
        String normalizedRole = role.strip().toLowerCase();
        if (!PhrRouteSupport.ALLOWED_ROLES.contains(normalizedRole)) {
            return PhrRouteSupport.errorResponse(400, "INVALID_ROLE", "Unrecognised role: " + role, correlationId);
        }

        // Set sensible defaults for optional headers
        if (persona == null || persona.isBlank()) {
            persona = normalizedRole;
        }
        if (tier == null || tier.isBlank()) {
            tier = "core";
        }

        // E-005: Use tenant/principal/persona/tier in cache key
        String cacheKey = String.format("route-entitlements:%s:%s:%s:%s:%s", 
            tenantId, principalId, normalizedRole, persona, tier);

        // Try cache first
        java.util.Optional<Map<String, Object>> cached = entitlementCache.get(
            principalId,
            tenantId,
            "/route-entitlements",
            cacheKey
        );
        if (cached.isPresent()) {
            return jsonResponse(200, cached.get(), correlationId);
        }

        // Use loaded route contract with built-in role order
        List<ProductRouteEntitlement.RouteEntitlement> routes = routeEntitlementEvaluator.filterByRole(
            cachedRoutes, normalizedRole, roleOrder);
        List<ProductRouteEntitlement.ActionEntitlement> actions =
            routeEntitlementEvaluator.filterActionsByRole(routes, normalizedRole, roleOrder);
        List<ProductRouteEntitlement.CardEntitlement> cards =
            routeEntitlementEvaluator.filterCardsByRole(routes, normalizedRole, roleOrder);

        ProductRouteEntitlement entitlement = new ProductRouteEntitlement(
            "phr",
            principalId,
            tenantId,
            normalizedRole,
            persona,
            tier,
            correlationId,
            routes,
            actions,
            cards
        );

        // E-007: Use platform contract serialization
        Map<String, Object> entitlementMap = entitlement.toMap();
        
        // Cache for 5 minutes (300 seconds)
        entitlementCache.put(principalId, tenantId, "/route-entitlements", cacheKey, entitlementMap);
        
        return jsonResponse(200, entitlementMap, correlationId);
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
}
