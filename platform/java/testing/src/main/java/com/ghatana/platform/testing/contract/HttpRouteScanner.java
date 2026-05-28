package com.ghatana.platform.testing.contract;

import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @doc.type class
 * @doc.purpose Discovers HTTP routes from ActiveJ RoutingServlet implementations via reflection
 * @doc.layer platform
 * @doc.pattern Route discovery utility
 */
public final class HttpRouteScanner {
    private static final Logger LOG = Logger.getLogger(HttpRouteScanner.class.getName());
    private static final Pattern ROUTE_WITH_METHOD = Pattern.compile(
        "\\.with\\(\\s*HttpMethod\\.(GET|POST|PUT|DELETE|PATCH|OPTIONS|HEAD)\\s*,\\s*\"([^\"]+)\"");
    private static final Pattern ROUTE_FIELD_MOUNT = Pattern.compile(
        "\\.with\\(\\s*\"([^\"]+)\"\\s*,\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\.([a-zA-Z_][a-zA-Z0-9_]*)\\(\\)");
    private static final Pattern ROUTE_METHOD_MOUNT = Pattern.compile(
        "\\.with\\(\\s*\"([^\"]+)\"\\s*,\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\(\\)");
    private static final Pattern SERVLET_METHOD = Pattern.compile(
        "(?:public|private|protected)\\s+AsyncServlet\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(");

    private HttpRouteScanner() {}

    /**
     * Scan an HTTP server class for declared routes.
     * Discovers routes by inspecting the RoutingServlet.builder() construction
     * and extracting route definitions from source code or reflection metadata.
     *
     * @param httpServerClass class containing getServlet() method (e.g., PhrHttpServer, FinanceHttpServer)
     * @return set of discovered routes in OpenAPI format (e.g., "/fhir/{resourceType}")
     */
    public static Set<RouteDefinition> scanRoutes(Class<?> httpServerClass) {
        Set<RouteDefinition> routes = new HashSet<>();

        try {
            // Look for getServlet() method
            Method getServletMethod = httpServerClass.getMethod("getServlet");

            // Extract route information from method implementation
            // This uses reflection to find RoutingServlet.with() calls
            Set<RouteDefinition> discoveredRoutes = extractRoutesFromSource(httpServerClass, new HashSet<>());
            routes.addAll(discoveredRoutes);

            LOG.fine(() -> String.format("Discovered %d routes from %s", routes.size(), httpServerClass.getSimpleName()));
        } catch (NoSuchMethodException e) {
            LOG.warning(() -> String.format("No getServlet() method found in %s", httpServerClass.getName()));
        }

        return routes;
    }

    /**
     * Extract routes by parsing source code or using bytecode inspection.
     * Falls back to manual route definition if automated discovery is insufficient.
     */
    private static Set<RouteDefinition> extractRoutesFromSource(Class<?> httpServerClass, Set<Class<?>> visited) {
        Set<RouteDefinition> routes = new HashSet<>();

        if (!visited.add(httpServerClass)) {
            return routes;
        }

        if (ApiContractDefiner.class.isAssignableFrom(httpServerClass)) {
            try {
                ApiContractDefiner definer = (ApiContractDefiner) httpServerClass.getDeclaredConstructor().newInstance();
                routes.addAll(definer.defineRoutes());
            } catch (Exception e) {
                LOG.warning(() -> String.format("Failed to instantiate contract definer: %s", e.getMessage()));
            }
        }

        routes.addAll(extractRoutesFromMethod(httpServerClass, "getServlet", visited, new HashSet<>()));

        return routes;
    }

    private static Set<RouteDefinition> extractRoutesFromMethod(
            Class<?> routeClass,
            String methodName,
            Set<Class<?>> classVisited,
            Set<MethodVisit> methodVisited) {
        Set<RouteDefinition> routes = new HashSet<>();
        MethodVisit visit = new MethodVisit(routeClass, methodName);
        if (!methodVisited.add(visit)) {
            return routes;
        }

        Optional<String> source = readSource(routeClass);
        if (source.isEmpty()) {
            return routes;
        }

        Optional<String> methodBody = extractMethodBody(source.get(), methodName);
        if (methodBody.isEmpty()) {
            return routes;
        }

        Matcher directRouteMatcher = ROUTE_WITH_METHOD.matcher(methodBody.get());
        while (directRouteMatcher.find()) {
            routes.add(new RouteDefinition(
                io.activej.http.HttpMethod.valueOf(directRouteMatcher.group(1)),
                directRouteMatcher.group(2)
            ));
        }

        Matcher selfMountMatcher = ROUTE_METHOD_MOUNT.matcher(methodBody.get());
        while (selfMountMatcher.find()) {
            String mountPath = selfMountMatcher.group(1);
            String mountedMethodName = selfMountMatcher.group(2);
            Set<RouteDefinition> mountedRoutes = extractRoutesFromMethod(
                routeClass,
                mountedMethodName,
                classVisited,
                methodVisited
            );
            for (RouteDefinition route : mountedRoutes) {
                routes.add(new RouteDefinition(route.getMethod(), combinePaths(mountPath, route.getPath())));
            }
        }

        Map<String, Class<?>> routeFields = routeFieldTypes(routeClass);
        Matcher fieldMountMatcher = ROUTE_FIELD_MOUNT.matcher(methodBody.get());
        while (fieldMountMatcher.find()) {
            String mountPath = fieldMountMatcher.group(1);
            String fieldName = fieldMountMatcher.group(2);
            String mountedMethodName = fieldMountMatcher.group(3);
            Class<?> mountedClass = routeFields.get(fieldName);
            if (mountedClass == null) {
                continue;
            }

            Set<RouteDefinition> mountedRoutes = extractRoutesFromMethod(
                mountedClass,
                mountedMethodName,
                classVisited,
                new HashSet<>()
            );
            for (RouteDefinition route : mountedRoutes) {
                routes.add(new RouteDefinition(route.getMethod(), combinePaths(mountPath, route.getPath())));
            }
        }

        return routes;
    }

    private static Optional<String> extractMethodBody(String source, String methodName) {
        Matcher methodMatcher = SERVLET_METHOD.matcher(source);
        while (methodMatcher.find()) {
            if (!methodMatcher.group(1).equals(methodName)) {
                continue;
            }

            int braceStart = source.indexOf('{', methodMatcher.end());
            if (braceStart < 0) {
                return Optional.empty();
            }
            int braceDepth = 0;
            for (int index = braceStart; index < source.length(); index++) {
                char current = source.charAt(index);
                if (current == '{') {
                    braceDepth++;
                } else if (current == '}') {
                    braceDepth--;
                    if (braceDepth == 0) {
                        return Optional.of(source.substring(braceStart + 1, index));
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static Map<String, Class<?>> routeFieldTypes(Class<?> httpServerClass) {
        Map<String, Class<?>> fields = new HashMap<>();
        for (Field field : httpServerClass.getDeclaredFields()) {
            fields.put(field.getName(), field.getType());
        }
        return fields;
    }

    private static Optional<String> readSource(Class<?> httpServerClass) {
        Path relativeSourcePath = Path.of(httpServerClass.getName().replace('.', '/') + ".java");
        List<Path> roots = List.of(
            Path.of("src/main/java"),
            Path.of("platform/java/testing/src/main/java"),
            Path.of("products/phr/src/main/java"),
            Path.of("platform/java/http/src/main/java"),
            Path.of("platform/java/core/src/main/java")
        );

        for (Path root : roots) {
            Path sourcePath = root.resolve(relativeSourcePath);
            if (Files.isRegularFile(sourcePath)) {
                try {
                    return Optional.of(Files.readString(sourcePath, StandardCharsets.UTF_8));
                } catch (Exception ex) {
                    LOG.warning(() -> String.format(
                        "Failed to read source for %s from %s: %s",
                        httpServerClass.getName(),
                        sourcePath,
                        ex.getMessage()
                    ));
                }
            }
        }
        return Optional.empty();
    }

    private static String combinePaths(String mountPath, String childPath) {
        String normalizedMount = mountPath.endsWith("/*")
            ? mountPath.substring(0, mountPath.length() - 2)
            : mountPath;
        String normalizedChild = childPath.equals("/") ? "" : childPath;
        if (normalizedChild.isEmpty()) {
            return normalizedMount.isBlank() ? "/" : normalizedMount;
        }

        String combined = (normalizedMount + "/" + stripLeadingSlash(normalizedChild)).replaceAll("/{2,}", "/");
        return combined.isBlank() ? "/" : combined;
    }

    private static String stripLeadingSlash(String path) {
        String result = path;
        while (result.startsWith("/")) {
            result = result.substring(1);
        }
        return result;
    }

    private record MethodVisit(Class<?> routeClass, String methodName) {}

    /**
     * Normalize OpenAPI path parameter syntax to standard format.
     * Converts between formats: {id} -> :id (ActiveJ) and vice versa
     */
    public static String normalizePathFormat(String path, boolean toOpenApiFormat) {
        if (toOpenApiFormat) {
            // Convert ActiveJ :id format to OpenAPI {id} format
            return path.replaceAll(":([a-zA-Z_][a-zA-Z0-9_]*)", "{$1}");
        } else {
            // Convert OpenAPI {id} format to ActiveJ :id format
            return path.replaceAll("\\{([a-zA-Z_][a-zA-Z0-9_]*)\\}", ":$1");
        }
    }

    /**
     * Represents a single HTTP route with method and path.
     */
    public static final class RouteDefinition {
        private final io.activej.http.HttpMethod method;
        private final String path;

        public RouteDefinition(io.activej.http.HttpMethod method, String path) {
            this.method = method;
            this.path = path;
        }

        public io.activej.http.HttpMethod getMethod() {
            return method;
        }

        public String getPath() {
            return path;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof RouteDefinition)) return false;
            RouteDefinition that = (RouteDefinition) o;
            return method == that.method && Objects.equals(path, that.path);
        }

        @Override
        public int hashCode() {
            return Objects.hash(method, path);
        }

        @Override
        public String toString() {
            return method + " " + path;
        }
    }
}

/**
 * Interface that HTTP server implementations can use to explicitly declare their routes.
 */
interface ApiContractDefiner {
    Set<HttpRouteScanner.RouteDefinition> defineRoutes();
}

