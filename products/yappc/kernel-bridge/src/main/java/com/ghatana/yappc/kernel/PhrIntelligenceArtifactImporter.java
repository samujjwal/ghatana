package com.ghatana.yappc.kernel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.governance.security.TenantContext;
import io.activej.promise.Promise;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * G10-001: PHR Intelligence Artifact Importer
 *
 * Reads PHR route contract and use case baseline files to generate
 * intelligence artifacts for YAPPC code generation.
 *
 * @doc.type class
 * @doc.purpose Import PHR IA from route contract and use case baseline
 * @doc.layer integration
 * @doc.pattern Importer
 */
public class PhrIntelligenceArtifactImporter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Import PHR route contract from JSON file
     */
    public Promise<PhrRouteContract> importRouteContract(Path contractPath) {
        return Promise.ofBlocking(() -> {
            try {
                JsonNode root = OBJECT_MAPPER.readTree(contractPath.toFile());
                return parseRouteContract(root);
            } catch (Exception e) {
                throw new RuntimeException("Failed to import PHR route contract: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Import PHR use case baseline from JSON file
     */
    public Promise<PhrUseCaseBaseline> importUseCaseBaseline(Path baselinePath) {
        return Promise.ofBlocking(() -> {
            try {
                JsonNode root = OBJECT_MAPPER.readTree(baselinePath.toFile());
                return parseUseCaseBaseline(root);
            } catch (Exception e) {
                throw new RuntimeException("Failed to import PHR use case baseline: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Parse route contract from JSON node
     */
    private PhrRouteContract parseRouteContract(JsonNode root) {
        PhrRouteContract contract = new PhrRouteContract();
        
        contract.setProductId(root.path("productId").asText());
        contract.setVersion(root.path("version").asText());
        contract.setGeneratedAt(root.path("generatedAt").asText());
        
        JsonNode routesNode = root.path("routes");
        List<PhrRoute> routes = new ArrayList<>();
        if (routesNode.isArray()) {
            for (JsonNode routeNode : routesNode) {
                routes.add(parseRoute(routeNode));
            }
        }
        contract.setRoutes(routes);
        
        return contract;
    }

    /**
     * Parse individual route from JSON node
     */
    private PhrRoute parseRoute(JsonNode routeNode) {
        PhrRoute route = new PhrRoute();
        route.setId(routeNode.path("id").asText());
        route.setPath(routeNode.path("path").asText());
        route.setStability(routeNode.path("stability").asText());
        route.setVisibility(routeNode.path("visibility").asText());
        route.setComponent(routeNode.path("component").asText());
        route.setRoles(parseStringArray(routeNode.path("roles")));
        route.setRequiredFeatures(parseStringArray(routeNode.path("requiredFeatures")));
        return route;
    }

    /**
     * Parse use case baseline from JSON node
     */
    private PhrUseCaseBaseline parseUseCaseBaseline(JsonNode root) {
        PhrUseCaseBaseline baseline = new PhrUseCaseBaseline();
        
        baseline.setProductId(root.path("productId").asText());
        baseline.setVersion(root.path("version").asText());
        
        JsonNode useCasesNode = root.path("useCases");
        List<PhrUseCase> useCases = new ArrayList<>();
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
    private PhrUseCase parseUseCase(JsonNode ucNode) {
        PhrUseCase useCase = new PhrUseCase();
        useCase.setId(ucNode.path("id").asText());
        useCase.setName(ucNode.path("name").asText());
        useCase.setDescription(ucNode.path("description").asText());
        useCase.setFlow(ucNode.path("flow").asText());
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

    /**
     * PHR Route Contract model
     */
    public static class PhrRouteContract {
        private String productId;
        private String version;
        private String generatedAt;
        private List<PhrRoute> routes;

        public String getProductId() { return productId; }
        public void setProductId(String productId) { this.productId = productId; }
        
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        
        public String getGeneratedAt() { return generatedAt; }
        public void setGeneratedAt(String generatedAt) { this.generatedAt = generatedAt; }
        
        public List<PhrRoute> getRoutes() { return routes; }
        public void setRoutes(List<PhrRoute> routes) { this.routes = routes; }
    }

    /**
     * PHR Route model
     */
    public static class PhrRoute {
        private String id;
        private String path;
        private String stability;
        private String visibility;
        private String component;
        private List<String> roles;
        private List<String> requiredFeatures;

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
     * PHR Use Case Baseline model
     */
    public static class PhrUseCaseBaseline {
        private String productId;
        private String version;
        private List<PhrUseCase> useCases;

        public String getProductId() { return productId; }
        public void setProductId(String productId) { this.productId = productId; }
        
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        
        public List<PhrUseCase> getUseCases() { return useCases; }
        public void setUseCases(List<PhrUseCase> useCases) { this.useCases = useCases; }
    }

    /**
     * PHR Use Case model
     */
    public static class PhrUseCase {
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
