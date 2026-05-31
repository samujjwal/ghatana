package com.ghatana.yappc.kernel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.promise.Promise;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * G10-001: Product Intelligence Artifact Importer
 *
 * Reads product route contract and use case baseline files to generate
 * intelligence artifacts for YAPPC code generation.
 *
 * @doc.type class
 * @doc.purpose Import product IA from route contract and use case baseline
 * @doc.layer integration
 * @doc.pattern Importer
 */
public class ProductIntelligenceArtifactImporter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Import product route contract from JSON file
     */
    public Promise<ProductRouteContract> importRouteContract(Path contractPath) {
        return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> {
            try {
                JsonNode root = OBJECT_MAPPER.readTree(contractPath.toFile());
                return parseRouteContract(root);
            } catch (Exception e) {
                throw new RuntimeException("Failed to import product route contract: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Import product use case baseline from JSON file
     */
    public Promise<ProductUseCaseBaseline> importUseCaseBaseline(Path baselinePath) {
        return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> {
            try {
                JsonNode root = OBJECT_MAPPER.readTree(baselinePath.toFile());
                return parseUseCaseBaseline(root);
            } catch (Exception e) {
                throw new RuntimeException("Failed to import product use case baseline: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Parse route contract from JSON node
     */
    private ProductRouteContract parseRouteContract(JsonNode root) {
        ProductRouteContract contract = new ProductRouteContract();
        
        String productId = requiredText(root, "productId");
        contract.setProductId(productId);
        contract.setVersion(requiredText(root, "version"));
        contract.setGeneratedAt(requiredText(root, "generatedAt"));
        
        JsonNode routesNode = root.path("routes");
        List<ProductRoute> routes = new ArrayList<>();
        if (routesNode.isArray()) {
            for (JsonNode routeNode : routesNode) {
                routes.add(parseRoute(productId, routeNode));
            }
        }
        contract.setRoutes(routes);
        
        return contract;
    }

    /**
     * Parse individual route from JSON node
     */
    private ProductRoute parseRoute(String productId, JsonNode routeNode) {
        ProductRoute route = new ProductRoute();
        route.setProductId(productId);
        route.setId(requiredText(routeNode, "id"));
        route.setPath(requiredText(routeNode, "path"));
        route.setStability(requiredText(routeNode, "stability"));
        route.setVisibility(requiredText(routeNode, "visibility"));
        route.setComponent(requiredText(routeNode, "component"));
        route.setRoles(parseStringArray(routeNode.path("roles")));
        route.setRequiredFeatures(parseStringArray(routeNode.path("requiredFeatures")));
        return route;
    }

    /**
     * Parse use case baseline from JSON node
     */
    private ProductUseCaseBaseline parseUseCaseBaseline(JsonNode root) {
        ProductUseCaseBaseline baseline = new ProductUseCaseBaseline();
        
        baseline.setProductId(requiredText(root, "productId"));
        baseline.setVersion(requiredText(root, "version"));
        
        JsonNode useCasesNode = root.path("useCases");
        List<ProductUseCase> useCases = new ArrayList<>();
        if (useCasesNode.isArray()) {
            for (JsonNode ucNode : useCasesNode) {
                useCases.add(parseUseCase(ucNode));
            }
        }
        baseline.setUseCases(useCases);
        
        return baseline;
    }

    /**
     * Parse individual use case from JSON node
     */
    private ProductUseCase parseUseCase(JsonNode ucNode) {
        ProductUseCase useCase = new ProductUseCase();
        useCase.setId(requiredText(ucNode, "id"));
        useCase.setName(requiredText(ucNode, "name"));
        useCase.setDescription(requiredText(ucNode, "description"));
        useCase.setFlow(requiredText(ucNode, "flow"));
        useCase.setActors(parseStringArray(ucNode.path("actors")));
        useCase.setPreconditions(parseStringArray(ucNode.path("preconditions")));
        useCase.setPostconditions(parseStringArray(ucNode.path("postconditions")));
        return useCase;
    }

    /**
     * Parse string array from JSON node
     */
    private List<String> parseStringArray(JsonNode node) {
        List<String> result = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                result.add(item.asText());
            }
        }
        return result;
    }

    private String requiredText(JsonNode node, String fieldName) {
        String value = node.path(fieldName).asText();
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must be provided");
        }
        return value;
    }

    /**
     * Product Route Contract model
     */
    public static class ProductRouteContract {
        private String productId;
        private String version;
        private String generatedAt;
        private List<ProductRoute> routes;

        public String getProductId() { return productId; }
        public void setProductId(String productId) { this.productId = productId; }
        
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        
        public String getGeneratedAt() { return generatedAt; }
        public void setGeneratedAt(String generatedAt) { this.generatedAt = generatedAt; }
        
        public List<ProductRoute> getRoutes() { return routes; }
        public void setRoutes(List<ProductRoute> routes) { this.routes = routes; }
    }

    /**
     * Product Route model
     */
    public static class ProductRoute {
        private String productId;
        private String id;
        private String path;
        private String stability;
        private String visibility;
        private String component;
        private List<String> roles;
        private List<String> requiredFeatures;

        public String getProductId() { return productId; }
        public void setProductId(String productId) { this.productId = productId; }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        
        public String getStability() { return stability; }
        public void setStability(String stability) { this.stability = stability; }
        
        public String getVisibility() { return visibility; }
        public void setVisibility(String visibility) { this.visibility = visibility; }
        
        public String getComponent() { return component; }
        public void setComponent(String component) { this.component = component; }
        
        public List<String> getRoles() { return roles; }
        public void setRoles(List<String> roles) { this.roles = roles; }
        
        public List<String> getRequiredFeatures() { return requiredFeatures; }
        public void setRequiredFeatures(List<String> requiredFeatures) { this.requiredFeatures = requiredFeatures; }
    }

    /**
     * Product Use Case Baseline model
     */
    public static class ProductUseCaseBaseline {
        private String productId;
        private String version;
        private List<ProductUseCase> useCases;

        public String getProductId() { return productId; }
        public void setProductId(String productId) { this.productId = productId; }
        
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        
        public List<ProductUseCase> getUseCases() { return useCases; }
        public void setUseCases(List<ProductUseCase> useCases) { this.useCases = useCases; }
    }

    /**
     * Product Use Case model
     */
    public static class ProductUseCase {
        private String id;
        private String name;
        private String description;
        private String flow;
        private List<String> actors;
        private List<String> preconditions;
        private List<String> postconditions;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public String getFlow() { return flow; }
        public void setFlow(String flow) { this.flow = flow; }
        
        public List<String> getActors() { return actors; }
        public void setActors(List<String> actors) { this.actors = actors; }
        
        public List<String> getPreconditions() { return preconditions; }
        public void setPreconditions(List<String> preconditions) { this.preconditions = preconditions; }
        
        public List<String> getPostconditions() { return postconditions; }
        public void setPostconditions(List<String> postconditions) { this.postconditions = postconditions; }
    }
}
