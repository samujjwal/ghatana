package com.ghatana.yappc.kernel;

import io.activej.promise.Promise;

/**
 * G10-004: Product Backend Route Adapter Skeleton Generator
 *
 * Generates product backend route adapter skeletons from Kernel route contract.
 *
 * @doc.type class
 * @doc.purpose Generate product backend route adapter skeletons from Kernel route contract
 * @doc.layer integration
 * @doc.pattern Generator
 */
public class BackendRouteAdapterGenerator {

    /**
     * Generate backend route adapter skeleton for a product route
     */
    public Promise<BackendRouteAdapterSkeleton> generateAdapterSkeleton(ProductIntelligenceArtifactImporter.ProductRoute route) {
        return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> {
            BackendRouteAdapterSkeleton skeleton = new BackendRouteAdapterSkeleton();
            
            skeleton.setRouteId(route.getId());
            skeleton.setPath(route.getPath());
            skeleton.setStability(route.getStability());
            skeleton.setVisibility(route.getVisibility());
            
            String adapterFilePath = generateAdapterFilePath(route);
            skeleton.setAdapterFilePath(adapterFilePath);
            
            String handlerFilePath = generateHandlerFilePath(route);
            skeleton.setHandlerFilePath(handlerFilePath);
            
            String adapterCode = generateAdapterCode(route);
            skeleton.setAdapterCode(adapterCode);
            
            String handlerCode = generateHandlerCode(route);
            skeleton.setHandlerCode(handlerCode);
            
            return skeleton;
        });
    }

    /**
     * Generate adapter file path
     */
    private String generateAdapterFilePath(ProductIntelligenceArtifactImporter.ProductRoute route) {
        return "products/" + productPathSegment(route) + "/backend/src/main/java/com/ghatana/"
            + ProductGenerationNaming.packageSegment(route.getProductId()) + "/backend/adapters/"
            + ProductGenerationNaming.pascalCase(route.getId()) + "RouteAdapter.java";
    }

    /**
     * Generate handler file path
     */
    private String generateHandlerFilePath(ProductIntelligenceArtifactImporter.ProductRoute route) {
        return "products/" + productPathSegment(route) + "/backend/src/main/java/com/ghatana/"
            + ProductGenerationNaming.packageSegment(route.getProductId()) + "/backend/handlers/"
            + ProductGenerationNaming.pascalCase(route.getId()) + "Handler.java";
    }

    /**
     * Generate adapter code
     */
    private String generateAdapterCode(ProductIntelligenceArtifactImporter.ProductRoute route) {
        String packageSegment = ProductGenerationNaming.packageSegment(route.getProductId());
        String routeType = ProductGenerationNaming.pascalCase(route.getId());
        return String.format("""
package com.ghatana.%s.backend.adapters;

import com.ghatana.%s.backend.handlers.%sHandler;
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
    private static final java.util.Set<String> REQUIRED_ROLES = java.util.Set.of(%s);
    private static final java.util.Set<String> REQUIRED_FEATURES = java.util.Set.of(%s);

    public %sRouteAdapter(%sHandler handler) {
        this.handler = handler;
    }

    @Override
    public Promise<HttpResponse> handle(HttpRequest request, SessionContext session) {
        return handler.handle(request, session);
    }
}
""",
            packageSegment,
            packageSegment,
            routeType,
            route.getId(),
            route.getPath(),
            route.getStability(),
            route.getVisibility(),
            route.getId(),
            routeType,
            routeType,
            quoteStringList(route.getRoles()),
            quoteStringList(route.getRequiredFeatures()),
            routeType,
            routeType
        );
    }

    /**
     * Generate handler code
     */
    private String generateHandlerCode(ProductIntelligenceArtifactImporter.ProductRoute route) {
        String packageSegment = ProductGenerationNaming.packageSegment(route.getProductId());
        String routeType = ProductGenerationNaming.pascalCase(route.getId());
        return String.format("""
package com.ghatana.%s.backend.handlers;

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
        throw new UnsupportedOperationException("%s handler must be bound to product domain logic before registration");
    }
}
""",
            packageSegment,
            route.getId(),
            route.getPath(),
            route.getId(),
            routeType,
            route.getId()
        );
    }

    private String productPathSegment(ProductIntelligenceArtifactImporter.ProductRoute route) {
        return ProductGenerationNaming.productPathSegment(route.getProductId());
    }

    private String quoteStringList(java.util.List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return values.stream()
            .map(value -> "\"" + value.replace("\"", "\\\"") + "\"")
            .collect(java.util.stream.Collectors.joining(", "));
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
