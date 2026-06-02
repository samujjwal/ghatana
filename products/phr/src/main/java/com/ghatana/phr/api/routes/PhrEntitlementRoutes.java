package com.ghatana.phr.api.routes;

import com.ghatana.platform.cache.IdentityAwareBoundedCache;
import com.ghatana.platform.http.contract.RouteContract;
import com.ghatana.platform.http.contract.RouteContractParser;
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
    private static final RouteContractParser ROUTE_CONTRACT_PARSER = new RouteContractParser();
    private static volatile Path ROUTE_CONTRACT_PATH;

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
     * Loads the route contract from the canonical JSON file using Kernel parser.
     * This ensures web and backend use the same source of truth for routes.
     *
     * E-001, E-002, E-008: No fallback route list or role order - fail closed if contract missing/invalid.
     * E-003: Validate loaded route contract fields.
     */
    private void loadRouteContract() {
        Path routeContractPath = resolveRouteContractPath();
        LOG.info("Loading route contract from path: {}", routeContractPath);
        try {
            if (!Files.exists(routeContractPath)) {
                LOG.error("Route contract file not found at {} - failing closed", routeContractPath);
                this.contractLoaded = false;
                return;
            }

            RouteContract contract = ROUTE_CONTRACT_PARSER.parse(routeContractPath);

            // Validate role order contains all required PHR roles
            Map<String, Integer> loadedRoleOrder = new java.util.LinkedHashMap<>(contract.roleOrder());
            for (String allowedRole : PhrRouteSupport.ALLOWED_ROLES) {
                if (!loadedRoleOrder.containsKey(allowedRole)) {
                    LOG.error("Route contract roleOrder missing role {} - failing closed", allowedRole);
                    this.contractLoaded = false;
                    return;
                }
            }

            // Load and validate routes
            List<ProductRouteEntitlement.RouteEntitlement> loadedRoutes = new ArrayList<>();
            Map<String, Map<String, Object>> loadedMeta = new java.util.LinkedHashMap<>();
            
            for (RouteContract.Route route : contract.routes()) {
                String path = route.path();
                String label = route.label();
                String minimumRole = route.minimumRole();
                String stability = route.stability().getValue();

                if (!PhrRouteSupport.isAllowedRole(minimumRole)) {
                    LOG.error("Route has unknown minimum role: {} - failing closed", minimumRole);
                    this.contractLoaded = false;
                    return;
                }

                List<String> actions = route.actions() != null ? new ArrayList<>(route.actions()) : new ArrayList<>();
                List<String> cards = route.cards() != null ? new ArrayList<>(route.cards()) : new ArrayList<>();
                List<String> personas = new ArrayList<>(route.personas());
                List<String> tiers = new ArrayList<>(route.tiers());

                // Validate personas
                for (String persona : personas) {
                    if (!PhrRouteSupport.isAllowedRole(persona)) {
                        LOG.error("Route {} has unknown persona {} - failing closed", path, persona);
                        this.contractLoaded = false;
                        return;
                    }
                }

                // Validate stable routes have required fields
                if ("stable".equals(stability)) {
                    if (route.apiEndpoint() == null || route.apiEndpoint().isBlank()) {
                        LOG.error("Stable route {} missing apiEndpoint - failing closed", path);
                        this.contractLoaded = false;
                        return;
                    }
                    if (route.policyId() == null || route.policyId().isBlank()) {
                        LOG.error("Stable route {} missing policyId - failing closed", path);
                        this.contractLoaded = false;
                        return;
                    }
                    if (route.testId() == null || route.testId().isBlank()) {
                        LOG.error("Stable route {} missing testId - failing closed", path);
                        this.contractLoaded = false;
                        return;
                    }
                }

                // Build route metadata
                Map<String, Object> meta = new java.util.LinkedHashMap<>();
                meta.put("stability", stability);
                if (route.apiEndpoint() != null && !route.apiEndpoint().isBlank()) {
                    meta.put("apiEndpoint", route.apiEndpoint());
                }
                if (route.policyId() != null && !route.policyId().isBlank()) {
                    meta.put("policyId", route.policyId());
                }
                if (route.testId() != null && !route.testId().isBlank()) {
                    meta.put("testId", route.testId());
                }
                if (route.group() != null && !route.group().isBlank()) {
                    meta.put("group", route.group());
                }
                if (route.description() != null && !route.description().isBlank()) {
                    meta.put("description", route.description());
                }
                loadedMeta.put(path, meta);

                // Only include stable routes with web surface in entitlements
                if (!"stable".equals(stability)) {
                    continue;
                }
                // Note: surface field not in Kernel contract model - assume all stable routes have web surface for now
                // This can be enhanced when surface is added to the contract schema

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

    /**
     * Returns whether the route contract was successfully loaded.
     * Used for testing to verify the contract is available before running tests.
     *
     * @return true if contract loaded successfully, false otherwise
     */
    public boolean isContractLoaded() {
        return contractLoaded;
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

        // E-005: Use tenant/principal/persona/tier/facility in cache key for server-authenticated context
        String cacheKey = String.format("route-entitlements:%s:%s:%s:%s:%s:%s",
            context.tenantId(), context.principalId(), context.role(), context.persona(), context.tier(),
            context.facilityId() != null ? context.facilityId() : "none");

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
