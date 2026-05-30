/**
 * YAPPC-T05: PHR Backend ActiveJ Route Skeleton Generator
 * 
 * Generates PHR backend ActiveJ route skeletons using PhrRouteSupport and PhrPolicyEvaluator.
 * Produces Java route handler code with proper structure, policy evaluation, and error handling.
 */

package com.ghatana.yappc.kernel;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for generating a backend route skeleton.
 */
public record BackendRouteConfig(
    String routePath,
    String routeLabel,
    String apiEndpoint,
    String policyId,
    String httpMethod,
    String className,
    String packageName
) {}

/**
 * Generated backend route skeleton with file path and code.
 */
public record GeneratedBackendRoute(
    String filePath,
    String className,
    String code
) {}

/**
 * Generates Java ActiveJ route handler skeletons from PHR route contract.
 * Uses PhrRouteSupport and PhrPolicyEvaluator for proper integration.
 */
public class PhrBackendRouteGenerator {

    /**
     * Generates backend route skeletons for all routes with API endpoints.
     */
    public List<GeneratedBackendRoute> generateRouteSkeletons(PhrProductContractImporter.ImportedPhrProduct imported) {
        List<GeneratedBackendRoute> routes = new ArrayList<>();
        
        for (PhrProductContractImporter.PhrRoute route : imported.routes()) {
            if (shouldGenerateRoute(route)) {
                routes.add(generateRoute(route));
            }
        }
        
        return routes;
    }

    /**
     * Determines if a backend route should be generated for the route.
     */
    private boolean shouldGenerateRoute(PhrProductContractImporter.PhrRoute route) {
        String apiEndpoint = route.apiEndpoint();
        if (apiEndpoint == null && route.metadata() != null) {
            apiEndpoint = (String) route.metadata().get("apiEndpoint");
        }
        
        return apiEndpoint != null && apiEndpoint.startsWith("/api/");
    }

    /**
     * Generates a single backend route skeleton.
     */
    private GeneratedBackendRoute generateRoute(PhrProductContractImporter.PhrRoute route) {
        String apiEndpoint = route.apiEndpoint();
        if (apiEndpoint == null && route.metadata() != null) {
            apiEndpoint = (String) route.metadata().get("apiEndpoint");
        }
        
        String policyId = route.policyId();
        if (policyId == null && route.metadata() != null) {
            policyId = (String) route.metadata().get("policyId");
        }
        
        String httpMethod = inferHttpMethod(apiEndpoint, route.path());
        String className = endpointToClassName(apiEndpoint);
        String packageName = "com.ghatana.phr.api.routes";
        String filePath = "src/main/java/com/ghatana/phr/api/routes/" + className + ".java";

        BackendRouteConfig config = new BackendRouteConfig(
            route.path(),
            route.label(),
            apiEndpoint,
            policyId,
            httpMethod,
            className,
            packageName
        );

        String code = generateRouteCode(config);
        
        return new GeneratedBackendRoute(filePath, className, code);
    }

    /**
     * Infers HTTP method from endpoint pattern.
     */
    private String inferHttpMethod(String apiEndpoint, String routePath) {
        if (apiEndpoint.contains("/create") || apiEndpoint.contains("/add")) {
            return "POST";
        }
        if (apiEndpoint.contains("/update") || apiEndpoint.contains("/edit")) {
            return "PUT";
        }
        if (apiEndpoint.contains("/delete") || apiEndpoint.contains("/remove")) {
            return "DELETE";
        }
        return "GET";
    }

    /**
     * Converts API endpoint to class name.
     */
    private String endpointToClassName(String apiEndpoint) {
        String[] segments = apiEndpoint
            .split("/")
            .filter(segment -> segment.length() > 0 && !segment.equals("api") && !segment.equals("v1"));
        
        StringBuilder className = new StringBuilder();
        for (String segment : segments) {
            String[] words = segment.split("-");
            for (String word : words) {
                if (word.length() > 0) {
                    className.append(Character.toUpperCase(word.charAt(0)))
                            .append(word.substring(1));
                }
            }
        }
        
        return className.append("Routes").toString();
    }

    /**
     * Generates Java ActiveJ route handler code.
     */
    private String generateRouteCode(BackendRouteConfig config) {
        String method = config.httpMethod();
        String endpoint = config.apiEndpoint();
        boolean hasBody = method.equals("POST") || method.equals("PUT");

        return String.format("""
package %s;

import com.ghatana.phr.api.routes.PhrRouteSupport;
import com.ghatana.phr.api.routes.PhrRouteSupport.PhrRequestContext;
import com.ghatana.phr.security.PhrPolicyEvaluator;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;

/**
 * %s Route Handler
 *
 * Route: %s
 * Endpoint: %s
 * Policy: %s
 * Method: %s
 *
 * Auto-generated from PHR route contract.
 * Do not edit manually - regenerate from contract.
 *
 * @doc.type class
 * @doc.purpose Route handler for %s
 * @doc.layer product
 * @doc.pattern Route Handler
 */
public final class %s {

    private final PhrPolicyEvaluator policyEvaluator;

    public %s(PhrPolicyEvaluator policyEvaluator) {
        this.policyEvaluator = policyEvaluator;
    }

    /**
     * Handles %s request for %s.
     *
     * @param request the HTTP request
     * @return Promise containing the HTTP response
     */
    public Promise<HttpResponse> handle%s(HttpRequest request) {
        try {
            // Extract and validate request context
            PhrRequestContext context = PhrRouteSupport.requireContext(request);
            
            // TODO: Implement policy evaluation using policyEvaluator
            // TODO: Implement business logic for %s
            
            return PhrRouteSupport.jsonResponse(200, new Object());
        } catch (IllegalArgumentException e) {
            return PhrRouteSupport.errorResponse(400, "INVALID_REQUEST", e.getMessage());
        } catch (Exception e) {
            return PhrRouteSupport.errorResponse(500, "INTERNAL_ERROR", "An unexpected error occurred");
        }
    }
}
""", config.packageName(), config.routeLabel(), config.routePath(), endpoint, config.policyId(), method, config.routeLabel(), config.className(), config.className(), method, config.routeLabel(), method, config.routeLabel());
    }
}
