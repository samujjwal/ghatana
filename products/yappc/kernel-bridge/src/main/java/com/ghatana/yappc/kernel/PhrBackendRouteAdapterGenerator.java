package com.ghatana.yappc.kernel;

import io.activej.promise.Promise;

/**
 * G10-004: PHR Backend Route Adapter Skeleton Generator
 *
 * Generates PHR backend route adapter skeletons from Kernel route contract.
 * This extends YAPPC's generic code generation for PHR-specific backend scaffolding.
 *
 * @doc.type class
 * @doc.purpose Generate PHR backend route adapter skeletons from Kernel route contract
 * @doc.layer integration
 * @doc.pattern Generator
 */
public class PhrBackendRouteAdapterGenerator {

    /**
     * Generate backend route adapter skeleton for a PHR route
     */
    public Promise<BackendRouteAdapterSkeleton> generateAdapterSkeleton(PhrIntelligenceArtifactImporter.PhrRoute route) {
        return Promise.ofBlocking(() -> {
            BackendRouteAdapterSkeleton skeleton = new BackendRouteAdapterSkeleton();
            
            skeleton.setRouteId(route.getId());
            skeleton.setPath(route.getPath());
            skeleton.setStability(route.getStability());
            skeleton.setVisibility(route.getVisibility());
            
            // Generate adapter file path
            String adapterFilePath = generateAdapterFilePath(route);
            skeleton.setAdapterFilePath(adapterFilePath);
            
            // Generate handler file path
            String handlerFilePath = generateHandlerFilePath(route);
            skeleton.setHandlerFilePath(handlerFilePath);
            
            // Generate adapter code
            String adapterCode = generateAdapterCode(route);
            skeleton.setAdapterCode(adapterCode);
            
            // Generate handler code
            String handlerCode = generateHandlerCode(route);
            skeleton.setHandlerCode(handlerCode);
            
            return skeleton;
        });
    }

    /**
     * Generate adapter file path
     */
    private String generateAdapterFilePath(PhrIntelligenceArtifactImporter.PhrRoute route) {
        return "products/phr/backend/src/main/java/com/ghatana/phr/backend/adapters/" + toPascalCase(route.getId()) + "RouteAdapter.java";
    }

    /**
     * Generate handler file path
     */
    private String generateHandlerFilePath(PhrIntelligenceArtifactImporter.PhrRoute route) {
        return "products/phr/backend/src/main/java/com/ghatana/phr/backend/handlers/" + toPascalCase(route.getId()) + "Handler.java";
    }

    /**
     * Generate adapter code
     */
    private String generateAdapterCode(PhrIntelligenceArtifactImporter.PhrRoute route) {
        return String.format("""
package com.ghatana.phr.backend.adapters;

import com.ghatana.phr.backend.handlers.%sHandler;
import com.ghatana.platform.http.HttpRequest;
import com.ghatana.platform.http.HttpResponse;
import com.ghatana.platform.http.RouteAdapter;
import com.ghatana.platform.security.SessionContext;
import io.activej.promise.Promise;

/**
 * Route adapter for %s
 *
 * Route: %s
 * Stability: %s
 * Visibility: %s
 *
 * @doc.type class
 * @doc.purpose Route adapter for %s
 * @doc.layer backend
 * @doc.pattern Adapter
 */
public class %sRouteAdapter implements RouteAdapter {

    private final %sHandler handler;

    public %sRouteAdapter(%sHandler handler) {
        this.handler = handler;
    }

    @Override
    public Promise<HttpResponse> handle(HttpRequest request, SessionContext session) {
        // TODO: Add role checks for: %s
        // TODO: Add feature checks for: %s
        // TODO: Add audit logging
        
        return handler.handle(request, session);
    }
}
""",
            toPascalCase(route.getId()),
            route.getId(),
            route.getPath(),
            route.getStability(),
            route.getVisibility(),
            route.getId(),
            toPascalCase(route.getId()),
            toPascalCase(route.getId()),
            toPascalCase(route.getId()),
            toPascalCase(route.getId()),
            String.join(", ", route.getRoles()),
            String.join(", ", route.getRequiredFeatures())
        );
    }

    /**
     * Generate handler code
     */
    private String generateHandlerCode(PhrIntelligenceArtifactImporter.PhrRoute route) {
        return String.format("""
package com.ghatana.phr.backend.handlers;

import com.ghatana.platform.http.HttpRequest;
import com.ghatana.platform.http.HttpResponse;
import com.ghatana.platform.security.SessionContext;
import io.activej.promise.Promise;

/**
 * Handler for %s
 *
 * Route: %s
 *
 * @doc.type class
 * @doc.purpose Handler for %s
 * @doc.layer backend
 * @doc.pattern Handler
 */
public class %sHandler {

    public Promise<HttpResponse> handle(HttpRequest request, SessionContext session) {
        // TODO: Implement %s handler logic
        // TODO: Add request validation
        // TODO: Add business logic
        // TODO: Add response formatting
        // TODO: Add error handling
        
        return Promise.of(HttpResponse.ok(200, "{\"status\":\"TODO\"}"));
    }
}
""",
            route.getId(),
            route.getPath(),
            route.getId(),
            toPascalCase(route.getId()),
            route.getId()
        );
    }

    /**
     * Convert to PascalCase
     */
    private String toPascalCase(String input) {
        String[] parts = input.split("(?=[A-Z])");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                result.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    result.append(part.substring(1).toLowerCase());
                }
            }
        }
        return result.toString();
    }

    /**
     * Backend Route Adapter Skeleton model
     */
    public static class BackendRouteAdapterSkeleton {
        private String routeId;
        private String path;
        private String stability;
        private String visibility;
        private String adapterFilePath;
        private String handlerFilePath;
        private String adapterCode;
        private String handlerCode;

        public String getRouteId() { return routeId; }
        public void setRouteId(String routeId) { this.routeId = routeId; }

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }

        public String getStability() { return stability; }
        public void setStability(String stability) { this.stability = stability; }

        public String getVisibility() { return visibility; }
        public void setVisibility(String visibility) { this.visibility = visibility; }

        public String getAdapterFilePath() { return adapterFilePath; }
        public void setAdapterFilePath(String adapterFilePath) { this.adapterFilePath = adapterFilePath; }

        public String getHandlerFilePath() { return handlerFilePath; }
        public void setHandlerFilePath(String handlerFilePath) { this.handlerFilePath = handlerFilePath; }

        public String getAdapterCode() { return adapterCode; }
        public void setAdapterCode(String adapterCode) { this.adapterCode = adapterCode; }

        public String getHandlerCode() { return handlerCode; }
        public void setHandlerCode(String handlerCode) { this.handlerCode = handlerCode; }
    }
}
