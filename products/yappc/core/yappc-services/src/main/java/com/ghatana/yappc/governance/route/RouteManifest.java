package com.ghatana.yappc.governance.route;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @doc.type class
 * @doc.purpose Represents the complete route manifest with routes grouped by server
 * @doc.layer governance
 * @doc.pattern Aggregate
 */
public class RouteManifest {
    private final Map<String, List<RouteEntry>> routesByServer;

    public RouteManifest() {
        this.routesByServer = new HashMap<>();
    }

    /**
     * Add a route entry for a specific server.
     * 
     * @param server The server name (e.g., "yappc-services", "yappc-api")
     * @param route The route entry
     */
    public void addRoute(String server, RouteEntry route) {
        route.validate();
        routesByServer.computeIfAbsent(server, k -> new ArrayList<>()).add(route);
    }

    /**
     * Get all routes for a specific server.
     * 
     * @param server The server name
     * @return List of route entries for the server, or empty list if server not found
     */
    public List<RouteEntry> getRoutesForServer(String server) {
        return routesByServer.getOrDefault(server, List.of());
    }

    /**
     * Get all routes across all servers.
     * 
     * @return List of all route entries
     */
    public List<RouteEntry> getAllRoutes() {
        return routesByServer.values().stream()
            .flatMap(List::stream)
            .toList();
    }

    /**
     * Get all server names in the manifest.
     * 
     * @return Set of server names
     */
    public List<String> getServers() {
        return new ArrayList<>(routesByServer.keySet());
    }

    /**
     * Get the total number of routes in the manifest.
     * 
     * @return Total route count
     */
    public int getRouteCount() {
        return getAllRoutes().size();
    }
}
