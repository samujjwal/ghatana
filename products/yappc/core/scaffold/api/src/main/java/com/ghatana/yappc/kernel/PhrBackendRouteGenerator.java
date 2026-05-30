/**
 * YAPPC-T05: PHR Backend ActiveJ Route Skeleton Generator
 * 
 * Generates PHR backend ActiveJ route skeletons using PhrRouteSupport and PhrPolicyEvaluator.
 * Produces Java route handler code with proper structure, policy evaluation, and error handling.
 */

package com.ghatana.yappc.kernel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Configuration for generating a backend route skeleton.
 */
record BackendRouteConfig(
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
record GeneratedBackendRoute(
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
        String[] segments = Arrays.stream(apiEndpoint.split("/"))
            .filter(segment -> segment.length() > 0 && !segment.equals("api") && !segment.equals("v1"))
            .toArray(String[]::new);
        
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

        return String.format("""
package %s;

import com.ghatana.phr.api.routes.PhrRouteSupport;
import com.ghatana.phr.api.routes.PhrRouteSupport.PhrRequestContext;
import com.ghatana.phr.security.PhrPolicyEvaluator;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;

import java.util.Objects;

/**
 * %s Route Handler
 *
 * Route: %s
 * Endpoint: %s
 * Policy: %s
 * Method: %s
 *
 * Auto-generated from PHR route contract.
 * Generated routes fail closed until product-owned business logic is added.
 *
 * @doc.type class
 * @doc.purpose Route handler for %s
 * @doc.layer product
 * @doc.pattern Route Handler
 */
public final class %s {

    private final PhrPolicyEvaluator policyEvaluator;

    public %s(PhrPolicyEvaluator policyEvaluator) {
        this.policyEvaluator = Objects.requireNonNull(policyEvaluator, "policyEvaluator must not be null");
    }

    /**
     * Handles %s request for %s.
     *
     * @param request the HTTP request
     * @return Promise containing the HTTP response
     */
    public Promise<HttpResponse> handle%s(HttpRequest request) {
        PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException e) {
            return PhrRouteSupport.errorResponse(400, "INVALID_REQUEST", e.getMessage());
        }

        return policyEvaluator.canAccessPhiResourceAsync(
                context,
                context.principalId(),
                "%s",
                "%s",
                context.tenantId(),
                context.facilityId())
            .then(decision -> {
                if (!decision.isAllowed()) {
                    return PhrRouteSupport.policyDenialResponse(
                        403,
                        context.correlationId(),
                        decision.getReasonCode());
                }

                return PhrRouteSupport.errorResponse(
                    501,
                    "ROUTE_NOT_IMPLEMENTED",
                    "Generated route skeleton requires product-owned implementation",
                    context.correlationId());
            });
    }
}
""", config.packageName(), config.routeLabel(), config.routePath(), endpoint, config.policyId(), method, config.routeLabel(), config.className(), config.className(), method, config.routeLabel(), method, config.routeLabel(), config.policyId(), method);
    }
}
