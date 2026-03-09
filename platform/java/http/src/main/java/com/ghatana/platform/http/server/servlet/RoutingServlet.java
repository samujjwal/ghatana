package com.ghatana.platform.http.server.servlet;

import io.activej.http.AsyncServlet;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Production-grade routing servlet mapping HTTP requests to handlers with path parameter support and ActiveJ Promise integration.
 *
 * <p><b>Purpose</b><br>
 * Provides RESTful routing with path parameter extraction (:param syntax), supporting both
 * synchronous and asynchronous handlers. Implements ActiveJ AsyncServlet for high-performance
 * request processing with automatic 404 handling and thread-safe route registration.
 *
 * <p><b>Architecture Role</b><br>
 * Routing servlet in core/http/servlet for HTTP request dispatching.
 * Used by:
 * - HttpServerBuilder - Create servers with routing
 * - API Endpoints - Map HTTP paths to handlers
 * - RESTful Services - Implement resource-oriented APIs
 * - Microservices - Route traffic based on paths/methods
 *
 * <p><b>Routing Features</b><br>
 * - <b>Path Parameters</b>: Extract parameters from paths (/users/:id)
 * - <b>HTTP Methods</b>: Method-based routing (GET, POST, PUT, DELETE, PATCH, etc.)
 * - <b>Async Support</b>: Promise-based handlers for non-blocking I/O
 * - <b>Sync Support</b>: Direct HttpResponse handlers for simple cases
 * - <b>404 Handling</b>: Automatic "Not Found" responses for unmatched routes
 * - <b>Thread Safety</b>: ConcurrentHashMap for concurrent route registration
 * - <b>Pattern Matching</b>: Regex-based path matching with parameter extraction
 * - <b>Exact Match First</b>: Performance optimization (exact match before pattern)
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // 1. Basic GET route with path parameter
 * RoutingServlet servlet = new RoutingServlet();
 * 
 * servlet.addRoute(HttpMethod.GET, "/users/:id", request -> {
 *     String id = extractPathParam(request.getPath(), "/users/:id", "id");
 *     User user = userService.getUser(id);
 *     return HttpResponse.ok200().withJson(user);
 * });
 *
 * // 2. Async POST route with Promise
 * servlet.addAsyncRoute(HttpMethod.POST, "/users", request ->
 *     request.loadBody()
 *         .map(body -> objectMapper.readValue(body.asArray(), User.class))
 *         .then(user -> userService.createUser(user))
 *         .map(created -> HttpResponse.ok200().withJson(created))
 * );
 *
 * // 3. RESTful CRUD routes
 * RoutingServlet crudServlet = new RoutingServlet();
 * 
 * // GET /users/:id
 * crudServlet.addAsyncRoute(HttpMethod.GET, "/users/:id", request ->
 *     userService.getUser(extractId(request))
 *         .map(user -> HttpResponse.ok200().withJson(user))
 *         .mapException(e -> HttpResponse.ofCode(404).withBody("User not found").build())
 * );
 * 
 * // POST /users
 * crudServlet.addAsyncRoute(HttpMethod.POST, "/users", request ->
 *     parseBody(request, User.class)
 *         .then(user -> userService.createUser(user))
 *         .map(created -> HttpResponse.ofCode(201)
 *             .withHeader("Location", "/users/" + created.getId())
 *             .withJson(created)
 *             .build())
 * );
 * 
 * // PUT /users/:id
 * crudServlet.addAsyncRoute(HttpMethod.PUT, "/users/:id", request ->
 *     parseBody(request, User.class)
 *         .then(user -> userService.updateUser(extractId(request), user))
 *         .map(updated -> HttpResponse.ok200().withJson(updated))
 * );
 * 
 * // DELETE /users/:id
 * crudServlet.addAsyncRoute(HttpMethod.DELETE, "/users/:id", request ->
 *     userService.deleteUser(extractId(request))
 *         .map(v -> HttpResponse.ofCode(204).build())
 * );
 *
 * // 4. Multi-segment path parameters
 * servlet.addRoute(HttpMethod.GET, "/users/:userId/orders/:orderId", request -> {
 *     String userId = extractPathParam(request.getPath(), 
 *         "/users/:userId/orders/:orderId", "userId");
 *     String orderId = extractPathParam(request.getPath(), 
 *         "/users/:userId/orders/:orderId", "orderId");
 *     Order order = orderService.getUserOrder(userId, orderId);
 *     return HttpResponse.ok200().withJson(order);
 * });
 *
 * // 5. Query parameter handling
 * servlet.addRoute(HttpMethod.GET, "/search", request -> {
 *     String query = request.getQueryParameter("q");
 *     int page = Integer.parseInt(request.getQueryParameter("page", "0"));
 *     int size = Integer.parseInt(request.getQueryParameter("size", "10"));
 *     
 *     SearchResults results = searchService.search(query, page, size);
 *     return HttpResponse.ok200().withJson(results);
 * });
 *
 * // 6. Mixed sync and async routes
 * RoutingServlet mixedServlet = new RoutingServlet();
 * 
 * // Sync health check
 * mixedServlet.addRoute(HttpMethod.GET, "/health", request ->
 *     HttpResponse.ok200().withPlainText("OK")
 * );
 * 
 * // Async data endpoint
 * mixedServlet.addAsyncRoute(HttpMethod.GET, "/data", request ->
 *     dataService.fetchData()
 *         .map(data -> HttpResponse.ok200().withJson(data))
 * );
 *
 * // 7. Error handling in routes
 * servlet.addAsyncRoute(HttpMethod.GET, "/protected/:id", request ->
 *     authService.checkAccess(extractId(request))
 *         .then(allowed -> {
 *             if (!allowed) {
 *                 return Promise.of(HttpResponse.ofCode(403)
 *                     .withBody("Access denied").build());
 *             }
 *             return dataService.getData(extractId(request))
 *                 .map(data -> HttpResponse.ok200().withJson(data));
 *         })
 *         .mapException(e -> {
 *             log.error("Request failed", e);
 *             return HttpResponse.ofCode(500).withBody("Internal error").build();
 *         })
 * );
 * }</pre>
 *
 * <p><b>Path Parameter Syntax</b><br>
 * Parameters use <code>:name</code> syntax:
 * <pre>
 * /users/:id                    → Matches /users/123 (id=123)
 * /users/:userId/orders/:oid    → Matches /users/123/orders/456
 * /files/:path                  → Matches /files/document.pdf
 * </pre>
 *
 * <p><b>HTTP Methods Supported</b><br>
 * All standard HTTP methods via ActiveJ HttpMethod enum:
 * - GET: Retrieve resources
 * - POST: Create resources
 * - PUT: Update resources (full)
 * - PATCH: Update resources (partial)
 * - DELETE: Delete resources
 * - HEAD: Get headers only
 * - OPTIONS: CORS/capability discovery
 *
 * <p><b>Route Matching Logic</b><br>
 * <pre>
 * 1. Try exact match first (performance optimization)
 * 2. If no exact match, iterate pattern routes
 * 3. Match method AND path pattern
 * 4. Extract path parameters if matched
 * 5. Return 404 if no route matches
 * </pre>
 *
 * <p><b>404 Response Format</b><br>
 * Automatic 404 for unmatched routes:
 * <pre>
 * Status: 404
 * Body: "Not Found: GET /unknown/path"
 * </pre>
 *
 * <p><b>Route Registration</b><br>
 * Thread-safe using ConcurrentHashMap:
 * <pre>{@code
 * servlet.addRoute(HttpMethod.GET, "/users", handler);       // Sync
 * servlet.addAsyncRoute(HttpMethod.POST, "/users", handler); // Async
 * }</pre>
 *
 * <p><b>Performance Characteristics</b><br>
 * - Exact match: O(1) HashMap lookup
 * - Pattern match: O(n) where n = number of routes
 * - Path parameter extraction: O(m) where m = number of parameters
 * - Optimization: Exact match checked before pattern matching
 *
 * <p><b>Best Practices</b><br>
 * - Use async routes for I/O operations (database, network, file)
 * - Use sync routes for in-memory operations (health checks, static content)
 * - Extract path parameters early in handler
 * - Handle Promise exceptions with .mapException()
 * - Return consistent response formats (use ResponseBuilder)
 * - Register exact match routes before pattern routes
 * - Use method-specific routes (avoid catch-all)
 *
 * <p><b>Limitations</b><br>
 * - Path parameters NOT automatically added to HttpRequest (manual extraction required)
 * - No wildcard routes (use custom pattern matching)
 * - No route ordering guarantees (except exact match first)
 * - No built-in query parameter parsing (use request.getQueryParameter())
 *
 * <p><b>Thread Safety</b><br>
 * Route registration is thread-safe (ConcurrentHashMap).
 * Request handling is thread-safe (each request processed independently).
 *
 * @see HttpServerBuilder
 * @see io.activej.http.AsyncServlet
 * @see io.activej.promise.Promise
 * @since 1.0.0
 * @doc.type class
 * @doc.purpose Routing servlet for HTTP request dispatching with path parameters
 * @doc.layer core
 * @doc.pattern Template Method
 */
@Slf4j
public class RoutingServlet implements AsyncServlet {
    
    private final Map<String, Route> routes = new ConcurrentHashMap<>();
    private static final Pattern PATH_PARAM_PATTERN = Pattern.compile(":([a-zA-Z][a-zA-Z0-9_]*)");
    
    /**
     * Adds a synchronous route handler.
     * 
     * @param method The HTTP method
     * @param pathPattern The path pattern (supports :param syntax)
     * @param handler The synchronous handler
     */
    public void addRoute(HttpMethod method, String pathPattern, Function<HttpRequest, HttpResponse> handler) {
        String key = routeKey(method, pathPattern);
        Route route = new Route(method, pathPattern, request -> Promise.of(handler.apply(request)));
        routes.put(key, route);
        log.debug("Added route: {} {}", method, pathPattern);
    }
    
    /**
     * Adds an asynchronous route handler.
     * 
     * @param method The HTTP method
     * @param pathPattern The path pattern (supports :param syntax)
     * @param handler The async handler returning a Promise
     */
    public void addAsyncRoute(HttpMethod method, String pathPattern, Function<HttpRequest, Promise<HttpResponse>> handler) {
        String key = routeKey(method, pathPattern);
        Route route = new Route(method, pathPattern, handler);
        routes.put(key, route);
        log.debug("Added async route: {} {}", method, pathPattern);
    }
    
    @Override
    public Promise<HttpResponse> serve(HttpRequest request) {
        String method = request.getMethod().name();
        String path = request.getPath();
        
        // Try exact match first
        String exactKey = routeKey(request.getMethod(), path);
        Route exactRoute = routes.get(exactKey);
        if (exactRoute != null) {
            return exactRoute.handler.apply(request);
        }
        
        // Try pattern matching
        for (Route route : routes.values()) {
            if (route.method == request.getMethod()) {
                Map<String, String> params = route.match(path);
                if (params != null) {
                    // Path parameters are matched but not added to request in ActiveJ 6.0
                    // Handler can extract them from the path directly if needed
                    return route.handler.apply(request);
                }
            }
        }
        
        // No route found
        log.debug("No route found for: {} {}", method, path);
        return Promise.of(HttpResponse.ofCode(404).withBody("Not Found: " + method + " " + path).build());
    }
    
    /**
     * Gets the number of registered routes.
     * 
     * @return The route count
     */
    public int getRouteCount() {
        return routes.size();
    }
    
    /**
     * Clears all registered routes.
     */
    public void clear() {
        routes.clear();
        log.debug("Cleared all routes");
    }
    
    /**
     * Merges routes from another RoutingServlet into this one.
     * 
     * <p>This allows combining multiple servlets' routes into a single servlet,
     * useful for modular route organization by feature/controller.
     * 
     * @param other The RoutingServlet whose routes to merge
     * @return This servlet (for fluent chaining)
     */
    public RoutingServlet merge(RoutingServlet other) {
        if (other == null) {
            return this;
        }
        
        // Copy all routes from the other servlet
        this.routes.putAll(other.routes);
        log.debug("Merged {} routes from another servlet", other.routes.size());
        
        return this;
    }
    
    private String routeKey(HttpMethod method, String path) {
        return method.name() + ":" + path;
    }
    
    /**
     * Internal route representation.
     */
    private static class Route {
        private final HttpMethod method;
        private final String pathPattern;
        private final Pattern compiledPattern;
        private final String[] paramNames;
        private final Function<HttpRequest, Promise<HttpResponse>> handler;
        
        Route(HttpMethod method, String pathPattern, Function<HttpRequest, Promise<HttpResponse>> handler) {
            this.method = method;
            this.pathPattern = pathPattern;
            this.handler = handler;
            
            // Extract parameter names and compile pattern
            Matcher matcher = PATH_PARAM_PATTERN.matcher(pathPattern);
            java.util.List<String> params = new java.util.ArrayList<>();
            
            String regex = pathPattern;
            while (matcher.find()) {
                params.add(matcher.group(1));
                regex = regex.replace(":" + matcher.group(1), "([^/]+)");
            }
            
            this.paramNames = params.toArray(new String[0]);
            this.compiledPattern = Pattern.compile("^" + regex + "$");
        }
        
        /**
         * Matches a path against this route's pattern.
         * 
         * @param path The path to match
         * @return Map of parameter names to values, or null if no match
         */
        Map<String, String> match(String path) {
            Matcher matcher = compiledPattern.matcher(path);
            if (!matcher.matches()) {
                return null;
            }
            
            Map<String, String> params = new HashMap<>();
            for (int i = 0; i < paramNames.length; i++) {
                params.put(paramNames[i], matcher.group(i + 1));
            }
            return params;
        }
    }
}
