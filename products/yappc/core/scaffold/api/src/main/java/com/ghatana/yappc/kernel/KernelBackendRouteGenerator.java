package com.ghatana.yappc.kernel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

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
    String packageName,
    String routeSupportClass,
    String requestContextClass,
    String policyEvaluatorClass
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
 * Generates Java ActiveJ route handler skeletons from Kernel product route contracts.
 *
 * @doc.type class
 * @doc.purpose Generates backend route skeletons from generic Kernel product contracts
 * @doc.layer product
 * @doc.pattern Generator
 */
public class KernelBackendRouteGenerator {

    /**
     * Generates backend route skeletons for all routes with API endpoints.
     */
    public List<GeneratedBackendRoute> generateRouteSkeletons(KernelProductContractImporter.ImportedKernelProduct imported) {
        List<GeneratedBackendRoute> routes = new ArrayList<>();
        
        for (KernelProductContractImporter.ProductRoute route : imported.routes()) {
            if (shouldGenerateRoute(route)) {
                routes.add(generateRoute(imported.product(), route));
            }
        }
        
        return routes;
    }

    /**
     * Determines if a backend route should be generated for the route.
     */
    private boolean shouldGenerateRoute(KernelProductContractImporter.ProductRoute route) {
        String apiEndpoint = route.apiEndpoint();
        if (apiEndpoint == null && route.metadata() != null) {
            apiEndpoint = (String) route.metadata().get("apiEndpoint");
        }
        
        return apiEndpoint != null && apiEndpoint.startsWith("/api/");
    }

    /**
     * Generates a single backend route skeleton.
     */
    private GeneratedBackendRoute generateRoute(String productId, KernelProductContractImporter.ProductRoute route) {
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
        String packageSegment = toPackageSegment(productId);
        String typePrefix = toJavaTypePrefix(productId);
        String packageName = "com.ghatana." + packageSegment + ".api.routes";
        String filePath = "src/main/java/com/ghatana/" + packageSegment + "/api/routes/" + className + ".java";

        BackendRouteConfig config = new BackendRouteConfig(
            route.path(),
            route.label(),
            apiEndpoint,
            policyId,
            httpMethod,
            className,
            packageName,
            typePrefix + "RouteSupport",
            typePrefix + "RequestContext",
            typePrefix + "PolicyEvaluator"
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

    private String toPackageSegment(String productId) {
        String normalized = productId == null ? "" : productId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("productId must include at least one package-safe character");
        }
        return normalized;
    }

    private String toJavaTypePrefix(String productId) {
        String normalized = productId == null ? "" : productId.replaceAll("[^A-Za-z0-9]+", " ");
        StringBuilder builder = new StringBuilder();
        for (String part : normalized.split(" ")) {
            if (part.isBlank()) {
                continue;
            }
            builder.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        if (builder.isEmpty()) {
            throw new IllegalArgumentException("productId must include at least one type-safe character");
        }
        return builder.toString();
    }

    /**
     * Generates Java ActiveJ route handler code.
     */
    private String generateRouteCode(BackendRouteConfig config) {
        String method = config.httpMethod();
        String endpoint = config.apiEndpoint();

        return String.format("""
package %s;

import %s.%s;
import %s.%s.%s;
import %s.%s;
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
 * Auto-generated from Kernel route contract.
 * Generated routes fail closed until product-owned business logic is added.
 *
 * @doc.type class
 * @doc.purpose Route handler for %s
 * @doc.layer product
 * @doc.pattern Route Handler
 */
public final class %s {

    private final %s policyEvaluator;

    public %s(%s policyEvaluator) {
        this.policyEvaluator = Objects.requireNonNull(policyEvaluator, "policyEvaluator must not be null");
    }

    /**
     * Handles %s request for %s.
     *
     * @param request the HTTP request
     * @return Promise containing the HTTP response
     */
    public Promise<HttpResponse> handle%s(HttpRequest request) {
        %s context;
        try {
            context = %s.requireContext(request);
        } catch (IllegalArgumentException e) {
            return %s.errorResponse(400, "INVALID_REQUEST", e.getMessage());
        }

        return policyEvaluator.canAccessResourceAsync(
                context,
                context.principalId(),
                "%s",
                "%s",
                context.tenantId(),
                context.facilityId())
            .then(decision -> {
                if (!decision.isAllowed()) {
                    return %s.policyDenialResponse(
                        403,
                        context.correlationId(),
                        decision.getReasonCode());
                }

                return %s.errorResponse(
                    501,
                    "ROUTE_NOT_IMPLEMENTED",
                    "Generated route skeleton requires product-owned implementation",
                    context.correlationId());
            });
    }
}
""",
                config.packageName(),
                config.packageName(),
                config.routeSupportClass(),
                config.packageName(),
                config.routeSupportClass(),
                config.requestContextClass(),
                config.packageName().replace(".api.routes", ".security"),
                config.policyEvaluatorClass(),
                config.routeLabel(),
                config.routePath(),
                endpoint,
                config.policyId(),
                method,
                config.routeLabel(),
                config.className(),
                config.policyEvaluatorClass(),
                config.className(),
                config.policyEvaluatorClass(),
                method,
                config.routeLabel(),
                method,
                config.requestContextClass(),
                config.routeSupportClass(),
                config.routeSupportClass(),
                config.policyId(),
                method,
                config.routeSupportClass(),
                config.routeSupportClass());
    }
}
