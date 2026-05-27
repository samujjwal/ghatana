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
    private static final Path ROUTE_CONTRACT_PATH = Path.of("products/phr/config/phr-route-contract.json");

    private final Eventloop eventloop;
    private final RouteEntitlementEvaluator routeEntitlementEvaluator;
    private final IdentityAwareBoundedCache<String, Map<String, Object>> entitlementCache;
    private Map<String, Integer> roleOrder;
    private List<ProductRouteEntitlement.RouteEntitlement> cachedRoutes;

    public PhrEntitlementRoutes(
            Eventloop eventloop,
            RouteEntitlementEvaluator routeEntitlementEvaluator,
            IdentityAwareBoundedCache<String, Map<String, Object>> entitlementCache) {
        this.eventloop = eventloop;
        this.routeEntitlementEvaluator = routeEntitlementEvaluator;
        this.entitlementCache = entitlementCache;
        loadRouteContract();
    }

    /**
     * Loads the route contract from the canonical JSON file.
     * This ensures web and backend use the same source of truth for routes.
     */
    private void loadRouteContract() {
        try {
            if (!Files.exists(ROUTE_CONTRACT_PATH)) {
                LOG.warn("Route contract file not found at {}, using fallback", ROUTE_CONTRACT_PATH);
                this.roleOrder = phrRoleOrder();
                this.cachedRoutes = phrRoutesFor("*", this.roleOrder);
                return;
            }

            String json = Files.readString(ROUTE_CONTRACT_PATH);
            JsonNode root = OBJECT_MAPPER.readTree(json);

            // Load role order
            JsonNode roleOrderNode = root.path("roleOrder");
            Map<String, Integer> loadedRoleOrder = new java.util.HashMap<>();
            roleOrderNode.fields().forEachRemaining(entry -> {
                loadedRoleOrder.put(entry.getKey(), entry.getValue().asInt());
            });
            this.roleOrder = loadedRoleOrder;

            // Load routes
            JsonNode routesNode = root.path("routes");
            List<ProductRouteEntitlement.RouteEntitlement> loadedRoutes = new ArrayList<>();
            for (JsonNode routeNode : routesNode) {
                String path = routeNode.path("path").asText();
                String label = routeNode.path("label").asText();
                String minimumRole = routeNode.path("minimumRole").asText();
                
                // Parse actions
                List<String> actions = new ArrayList<>();
                JsonNode actionsNode = routeNode.path("actions");
                if (actionsNode.isArray()) {
                    for (JsonNode action : actionsNode) {
                        actions.add(action.asText());
                    }
                }
                
                // Parse cards
                List<String> cards = new ArrayList<>();
                JsonNode cardsNode = routeNode.path("cards");
                if (cardsNode.isArray()) {
                    for (JsonNode card : cardsNode) {
                        cards.add(card.asText());
                    }
                }
                
                // Parse personas (default to all roles if not specified)
                List<String> personas = new ArrayList<>();
                JsonNode personasNode = routeNode.path("personas");
                if (personasNode.isArray()) {
                    for (JsonNode persona : personasNode) {
                        personas.add(persona.asText());
                    }
                } else {
                    personas = List.of("patient", "caregiver", "clinician", "admin");
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

                loadedRoutes.add(new ProductRouteEntitlement.RouteEntitlement(
                    path,
                    label,
                    minimumRole,
                    personas,
                    tiers,
                    actions,
                    cards,
                    null
                ));
            }
            this.cachedRoutes = loadedRoutes;
            
            LOG.info("Loaded {} routes from contract file {}", loadedRoutes.size(), ROUTE_CONTRACT_PATH);
        } catch (Exception ex) {
            LOG.error("Failed to load route contract from {}, using fallback", ROUTE_CONTRACT_PATH, ex);
            this.roleOrder = phrRoleOrder();
            this.cachedRoutes = phrRoutesFor("*", this.roleOrder);
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
        // Extract and validate authentication context from request headers
        // Fail closed: require explicit identity headers for production route entitlement
        String principalId = request.getHeader(io.activej.http.HttpHeaders.of("X-Principal-Id"));
        String tenantId = request.getHeader(io.activej.http.HttpHeaders.of("X-Tenant-Id"));
        String role = request.getHeader(io.activej.http.HttpHeaders.of("X-Role"));
        String persona = request.getHeader(io.activej.http.HttpHeaders.of("X-Persona"));
        String tier = request.getHeader(io.activej.http.HttpHeaders.of("X-Tier"));

        // Validate required headers - fail closed for missing identity
        if (principalId == null || principalId.isBlank()) {
            return PhrRouteSupport.errorResponse(400, "MISSING_PRINCIPAL", "X-Principal-Id header is required");
        }
        if (tenantId == null || tenantId.isBlank()) {
            return PhrRouteSupport.errorResponse(400, "MISSING_TENANT", "X-Tenant-Id header is required");
        }
        if (role == null || role.isBlank()) {
            return PhrRouteSupport.errorResponse(400, "MISSING_ROLE", "X-Role header is required");
        }

        // Normalize role to lower-case
        String normalizedRole = role.strip().toLowerCase();
        if (!PhrRouteSupport.ALLOWED_ROLES.contains(normalizedRole)) {
            return PhrRouteSupport.errorResponse(400, "INVALID_ROLE", "Unrecognised role: " + role);
        }

        // Set sensible defaults for optional headers
        if (persona == null || persona.isBlank()) {
            persona = normalizedRole;
        }
        if (tier == null || tier.isBlank()) {
            tier = "core";
        }

        String cacheKey = "route-entitlements:" + normalizedRole;

        // Try cache first
        java.util.Optional<Map<String, Object>> cached = entitlementCache.get(
            principalId,
            tenantId,
            "/route-entitlements",
            cacheKey
        );
        if (cached.isPresent()) {
            return jsonResponse(200, cached.get());
        }

        // Use loaded route contract
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
            null,  // correlationId can be null
            routes,
            actions,
            cards
        );

        // Build map manually to handle null correlationId
        // (ProductRouteEntitlement.toMap() doesn't handle null values well)
        Map<String, Object> entitlementMap = new java.util.HashMap<>();
        entitlementMap.put("product", entitlement.product());
        entitlementMap.put("principalId", entitlement.principalId());
        entitlementMap.put("tenantId", entitlement.tenantId());
        entitlementMap.put("role", entitlement.role());
        entitlement.persona().ifPresent(p -> entitlementMap.put("persona", p));
        entitlement.tier().ifPresent(t -> entitlementMap.put("tier", t));
        entitlement.correlationId().ifPresent(c -> entitlementMap.put("correlationId", c));
        entitlementMap.put("routes", entitlement.routes().stream().map(ProductRouteEntitlement.RouteEntitlement::toMap).toList());
        entitlementMap.put("actions", entitlement.actions().stream().map(ProductRouteEntitlement.ActionEntitlement::toMap).toList());
        entitlementMap.put("cards", entitlement.cards().stream().map(ProductRouteEntitlement.CardEntitlement::toMap).toList());
        
        // Cache for 5 minutes (300 seconds)
        entitlementCache.put(principalId, tenantId, "/route-entitlements", cacheKey, entitlementMap);
        
        return jsonResponse(200, entitlementMap);
    }

    private static Map<String, Integer> phrRoleOrder() {
        return Map.of(
            "patient", 1,
            "caregiver", 2,
            "clinician", 3,
            "admin", 4
        );
    }

    private static List<ProductRouteEntitlement.RouteEntitlement> phrRoutesFor(
            String tier,
            Map<String, Integer> roleOrder) {
        List<String> supportedTiers = "*".equals(tier)
            ? List.of("core", "plus", "premium")
            : List.of(tier);

        return List.of(
            new ProductRouteEntitlement.RouteEntitlement(
                "/dashboard",
                "Dashboard",
                "patient",
                List.copyOf(roleOrder.keySet()),
                supportedTiers,
                List.of(),
                List.of(),
                null
            ),
            new ProductRouteEntitlement.RouteEntitlement(
                "/records",
                "Health Records",
                "caregiver",
                List.copyOf(roleOrder.keySet()),
                supportedTiers,
                List.of(),
                List.of(),
                null
            ),
            new ProductRouteEntitlement.RouteEntitlement(
                "/admin",
                "Administration",
                "admin",
                List.copyOf(roleOrder.keySet()),
                supportedTiers,
                List.of(),
                List.of(),
                null
            )
        );
    }

    private static Promise<HttpResponse> jsonResponse(int statusCode, Object body) {
        String json = com.ghatana.platform.core.util.JsonUtils.toJsonSafe(body);
        if (json == null) {
            json = "{\"error\":\"SERIALIZATION_ERROR\",\"message\":\"Failed to serialize response\"}";
            statusCode = 500;
        }
        return Promise.of(io.activej.http.HttpResponse.ofCode(statusCode)
                .withHeader(io.activej.http.HttpHeaders.CONTENT_TYPE, CONTENT_JSON)
                .withJson(json)
                .build());
    }
}
