package com.ghatana.yappc.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.platform.governance.security.Principal;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * YAPPC-005: Product onboarding flow API.
 * Implements the archetype → reusable assets → ProductUnitIntent → lifecycle → evidence
 * onboarding pipeline for new products.
 *
 * @doc.type class
 * @doc.purpose Orchestrate new product onboarding through guided archetype selection,
 *              reusable asset discovery, ProductUnitIntent generation, and lifecycle setup
 * @doc.layer api
 * @doc.pattern Controller
 */
public final class ProductOnboardingController {

    private static final Logger log = LoggerFactory.getLogger(ProductOnboardingController.class);

    private static final String ONBOARDING_COLLECTION = "yappc_product_onboarding";
    private static final String ASSET_COLLECTION = "product_family_assets";

    private final DataCloudClient dataCloudClient;
    private final ObjectMapper objectMapper;
    private final Path repoRoot;

    public ProductOnboardingController(
            @NotNull DataCloudClient dataCloudClient,
            @NotNull ObjectMapper objectMapper) {
        this(dataCloudClient, objectMapper, Path.of("."));
    }

    ProductOnboardingController(
            @NotNull DataCloudClient dataCloudClient,
            @NotNull ObjectMapper objectMapper,
            @NotNull Path repoRoot) {
        this.dataCloudClient = dataCloudClient;
        this.objectMapper = objectMapper;
        this.repoRoot = repoRoot.toAbsolutePath().normalize();
    }

    /**
     * YAPPC-005: Initiates product onboarding flow.
     * POST /api/v1/yappc/onboarding
     */
    public Promise<HttpResponse> initiateOnboarding(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        if (tenantId == null) {
            return Promise.of(HttpResponse.ofCode(400)
                .withJson("{\"error\":\"tenant context required\"}")
                .build());
        }

        Principal principal = request.getAttachment(Principal.class);
        String actorId = principal != null && principal.getName() != null
            ? principal.getName()
            : "system";

        return request.loadBody().then(body -> {
            try {
                String json = body.asString(java.nio.charset.StandardCharsets.UTF_8);
                OnboardingRequest onboardingRequest = objectMapper.readValue(json, OnboardingRequest.class);

                // Validate request
                List<String> validationErrors = validateOnboardingRequest(onboardingRequest);
                if (!validationErrors.isEmpty()) {
                    return Promise.of(HttpResponse.ofCode(422)
                        .withJson(objectMapper.writeValueAsString(Map.of(
                            "error", "validation failed",
                            "violations", validationErrors)))
                        .build());
                }

                // Create onboarding record
                String onboardingId = UUID.randomUUID().toString();
                Map<String, Object> record = createOnboardingRecord(
                    onboardingId,
                    onboardingRequest,
                    actorId,
                    tenantId
                );

                return dataCloudClient.save(tenantId, ONBOARDING_COLLECTION, record)
                    .map(saved -> Map.of(
                        "onboardingId", onboardingId,
                        "status", "INITIATED",
                        "productId", onboardingRequest.productId(),
                        "currentStep", "archetype-selection",
                        "nextSteps", List.of(
                            "Select archetype",
                            "Review recommended reusable assets",
                            "Generate ProductUnitIntent",
                            "Initialize lifecycle configuration",
                            "Create initial evidence pack"
                        ),
                        "createdAt", saved.data().get("created_at")))
                    .map(this::jsonResponse);

            } catch (Exception e) {
                log.error("Failed to initiate onboarding", e);
                return Promise.of(HttpResponse.ofCode(400)
                    .withJson("{\"error\":\"Invalid onboarding request\"}")
                    .build());
            }
        });
    }

    /**
     * YAPPC-005: Gets recommended archetypes for new product.
     * GET /api/v1/yappc/onboarding/:onboardingId/archetypes
     */
    public Promise<HttpResponse> getArchetypeRecommendations(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        if (tenantId == null) {
            return Promise.of(HttpResponse.ofCode(400)
                .withJson("{\"error\":\"tenant context required\"}")
                .build());
        }

        String onboardingId = request.getPathParameter("onboardingId");
        if (onboardingId == null || onboardingId.isBlank()) {
            return Promise.of(HttpResponse.ofCode(400)
                .withJson("{\"error\":\"onboardingId is required\"}")
                .build());
        }

        return findOnboarding(tenantId, onboardingId)
            .then(found -> {
                if (found.isEmpty()) {
                    return Promise.of(HttpResponse.ofCode(404)
                        .withJson("{\"error\":\"onboarding not found\"}")
                        .build());
                }

                Map<String, Object> archetypes = Map.of(
                    "archetypes", List.of(
                        Map.of(
                            "id", "standard-web-api",
                            "name", "Standard Web + API Product",
                            "description", "Web frontend with Java backend API",
                            "surfaces", List.of("web", "backend-api"),
                            "complexity", "medium",
                            "estimatedSetupTime", "2 hours"
                        ),
                        Map.of(
                            "id", "mobile-plus-api",
                            "name", "Mobile + API Product",
                            "description", "iOS/Android apps with backend API",
                            "surfaces", List.of("mobile-ios", "mobile-android", "backend-api"),
                            "complexity", "high",
                            "estimatedSetupTime", "4 hours"
                        ),
                        Map.of(
                            "id", "polyglot-service",
                            "name", "Polyglot Service",
                            "description", "Multi-language service with Java, TypeScript, Rust, or Python",
                            "surfaces", List.of("backend-api"),
                            "complexity", "medium",
                            "estimatedSetupTime", "3 hours"
                        )
                    ),
                    "recommendedArchetype", "standard-web-api",
                    "recommendationReason", "Most common pattern with best support"
                );

                return Promise.of(jsonResponse(archetypes));
            });
    }

    /**
     * YAPPC-005: Gets reusable asset recommendations for onboarding product.
     * GET /api/v1/yappc/onboarding/:onboardingId/recommended-assets
     */
    public Promise<HttpResponse> getRecommendedAssets(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        if (tenantId == null) {
            return Promise.of(HttpResponse.ofCode(400)
                .withJson("{\"error\":\"tenant context required\"}")
                .build());
        }

        String onboardingId = request.getPathParameter("onboardingId");

        return findOnboarding(tenantId, onboardingId)
            .then(found -> {
                if (found.isEmpty()) {
                    return Promise.of(HttpResponse.ofCode(404)
                        .withJson("{\"error\":\"onboarding not found\"}")
                        .build());
                }

                // Query available hardened/production assets from Data Cloud
                DataCloudClient.Query query = DataCloudClient.Query.builder()
                    .filter(DataCloudClient.Filter.in("maturity", List.of("hardened", "production")))
                    .limit(50)
                    .build();

                return dataCloudClient.query(tenantId, ASSET_COLLECTION, query)
                    .map(records -> {
                        List<Map<String, Object>> assets = records.stream()
                            .map(DataCloudClient.Entity::data)
                            .map(this::normalizeAssetForRecommendation)
                            .toList();

                        return Map.of(
                            "status", "READY",
                            "recommendedAssets", assets,
                            "recommendedFoundation", List.of(
                                "kernel",
                                "dataCloud",
                                "platformJava",
                                "platformTypeScript"
                            ),
                            "guidance", "Start with kernel and dataCloud foundations, then add product-specific assets"
                        );
                    })
                    .map(this::jsonResponse);
            });
    }

    /**
     * YAPPC-005: Generates ProductUnitIntent for onboarding product.
     * POST /api/v1/yappc/onboarding/:onboardingId/generate-intent
     */
    public Promise<HttpResponse> generateProductUnitIntent(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        if (tenantId == null) {
            return Promise.of(HttpResponse.ofCode(400)
                .withJson("{\"error\":\"tenant context required\"}")
                .build());
        }

        String onboardingId = request.getPathParameter("onboardingId");
        Principal principal = request.getAttachment(Principal.class);
        String actorId = principal != null && principal.getName() != null
            ? principal.getName()
            : "system";

        return findOnboarding(tenantId, onboardingId)
            .then(found -> {
                if (found.isEmpty()) {
                    return Promise.of(HttpResponse.ofCode(404)
                        .withJson("{\"error\":\"onboarding not found\"}")
                        .build());
                }

                Map<String, Object> onboarding = found.get().data();
                String productId = String.valueOf(onboarding.get("product_id"));
                String archetype = String.valueOf(onboarding.getOrDefault("selected_archetype", "standard-web-api"));

                // Generate ProductUnitIntent structure
                Map<String, Object> intent = Map.of(
                    "schemaVersion", "1.0.0",
                    "productId", productId,
                    "archetype", archetype,
                    "surfaces", generateSurfacesForArchetype(archetype),
                    "phases", generatePhasesForArchetype(archetype),
                    "foundationUsage", Map.of(
                        "kernel", "required",
                        "dataCloud", "required",
                        "platformJava", "as-needed",
                        "platformTypeScript", "as-needed"
                    ),
                    "generatedAt", Instant.now().toString(),
                    "generatedBy", actorId,
                    "nextAction", "Review and customize intent, then submit to lifecycle"
                );

                // Update onboarding record
                Map<String, Object> updated = new LinkedHashMap<>(onboarding);
                updated.put("generated_intent", intent);
                updated.put("current_step", "intent-generated");
                updated.put("updated_at", Instant.now().toString());

                return dataCloudClient.save(tenantId, ONBOARDING_COLLECTION, updated)
                    .map(saved -> Map.of(
                        "onboardingId", onboardingId,
                        "status", "INTENT_GENERATED",
                        "productUnitIntent", intent,
                        "evidencePath", "products/" + productId + "/kernel-product.yaml"
                    ))
                    .map(this::jsonResponse);
            });
    }

    /**
     * YAPPC-005: Completes onboarding and creates initial evidence pack.
     * POST /api/v1/yappc/onboarding/:onboardingId/complete
     */
    public Promise<HttpResponse> completeOnboarding(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        if (tenantId == null) {
            return Promise.of(HttpResponse.ofCode(400)
                .withJson("{\"error\":\"tenant context required\"}")
                .build());
        }

        String onboardingId = request.getPathParameter("onboardingId");

        return findOnboarding(tenantId, onboardingId)
            .then(found -> {
                if (found.isEmpty()) {
                    return Promise.of(HttpResponse.ofCode(404)
                        .withJson("{\"error\":\"onboarding not found\"}")
                        .build());
                }

                Map<String, Object> onboarding = found.get().data();
                String productId = String.valueOf(onboarding.get("product_id"));

                // Create initial evidence pack structure
                Map<String, Object> evidencePack = Map.of(
                    "schemaVersion", "1.0.0",
                    "productId", productId,
                    "generatedAt", Instant.now().toString(),
                    "evidenceType", "onboarding-initial",
                    "archetype", onboarding.getOrDefault("selected_archetype", "unknown"),
                    "foundationAssets", onboarding.getOrDefault("selected_assets", List.of()),
                    "lifecycleConfigured", false,
                    "nextSteps", List.of(
                        "Run: pnpm kernel product " + productId + " plan validate",
                        "Create: products/" + productId + "/lifecycle/readiness-evidence.yaml",
                        "Create: .kernel/evidence/" + productId + "/foundation-usage-profile.json"
                    )
                );

                // Update onboarding record
                Map<String, Object> updated = new LinkedHashMap<>(onboarding);
                updated.put("status", "COMPLETED");
                updated.put("current_step", "completed");
                updated.put("initial_evidence_pack", evidencePack);
                updated.put("completed_at", Instant.now().toString());

                return dataCloudClient.save(tenantId, ONBOARDING_COLLECTION, updated)
                    .map(saved -> Map.of(
                        "onboardingId", onboardingId,
                        "status", "COMPLETED",
                        "productId", productId,
                        "initialEvidencePack", evidencePack,
                        "message", "Product onboarding complete. Follow nextSteps to configure lifecycle."
                    ))
                    .map(this::jsonResponse);
            });
    }

    // Helper methods

    private Promise<Optional<DataCloudClient.Entity>> findOnboarding(String tenantId, String onboardingId) {
        return dataCloudClient.findById(tenantId, ONBOARDING_COLLECTION, onboardingId)
            .then(found -> {
                if (found.isPresent()) {
                    return Promise.of(found);
                }
                // Try query by onboarding_id field
                DataCloudClient.Query query = DataCloudClient.Query.builder()
                    .filter(DataCloudClient.Filter.eq("onboarding_id", onboardingId))
                    .limit(1)
                    .build();
                return dataCloudClient.query(tenantId, ONBOARDING_COLLECTION, query)
                    .map(records -> records.stream().findFirst());
            });
    }

    private List<String> validateOnboardingRequest(OnboardingRequest request) {
        List<String> errors = new java.util.ArrayList<>();

        if (request.productId() == null || request.productId().isBlank()) {
            errors.add("productId is required");
        } else if (!request.productId().matches("^[a-z][a-z0-9-]*$")) {
            errors.add("productId must be kebab-case (lowercase, hyphens, start with letter)");
        }

        if (request.productName() == null || request.productName().isBlank()) {
            errors.add("productName is required");
        }

        if (request.owner() == null || request.owner().isBlank()) {
            errors.add("owner is required");
        }

        // Check product doesn't already exist
        Path productPath = repoRoot.resolve("products").resolve(request.productId());
        if (Files.exists(productPath)) {
            errors.add("Product path already exists: products/" + request.productId());
        }

        return errors;
    }

    private Map<String, Object> createOnboardingRecord(
            String onboardingId,
            OnboardingRequest request,
            String actorId,
            String tenantId) {

        Map<String, Object> record = new LinkedHashMap<>();
        record.put("id", onboardingId);
        record.put("onboarding_id", onboardingId);
        record.put("tenant_id", tenantId);
        record.put("product_id", request.productId());
        record.put("product_name", request.productName());
        record.put("description", request.description());
        record.put("owner", request.owner());
        record.put("archetype", request.archetype());
        record.put("status", "INITIATED");
        record.put("current_step", "archetype-selection");
        record.put("created_by", actorId);
        record.put("created_at", Instant.now().toString());
        record.put("updated_at", Instant.now().toString());

        return Map.copyOf(record);
    }

    private Map<String, Object> normalizeAssetForRecommendation(Map<String, Object> asset) {
        return Map.of(
            "assetId", String.valueOf(asset.getOrDefault("asset_id", "")),
            "assetName", String.valueOf(asset.getOrDefault("display_name", "")),
            "type", String.valueOf(asset.getOrDefault("asset_type", "")),
            "sourceProduct", String.valueOf(asset.getOrDefault("source_product", "")),
            "maturity", String.valueOf(asset.getOrDefault("maturity", "")),
            "description", String.valueOf(asset.getOrDefault("description", "")),
            "owner", String.valueOf(asset.getOrDefault("owner", ""))
        );
    }

    private List<Map<String, Object>> generateSurfacesForArchetype(String archetype) {
        return switch (archetype) {
            case "mobile-plus-api" -> List.of(
                Map.of("type", "backend-api", "adapter", "gradle-java-service"),
                Map.of("type", "mobile-ios", "adapter", "xcode-ios"),
                Map.of("type", "mobile-android", "adapter", "gradle-android")
            );
            case "polyglot-service" -> List.of(
                Map.of("type", "backend-api", "adapter", "varies-by-language")
            );
            default -> List.of(
                Map.of("type", "backend-api", "adapter", "gradle-java-service"),
                Map.of("type", "web", "adapter", "pnpm-vite-react")
            );
        };
    }

    private List<Map<String, Object>> generatePhasesForArchetype(String archetype) {
        List<String> phases = List.of("dev", "validate", "test", "build", "package", "deploy");
        return phases.stream()
            .map(phase -> Map.<String, Object>of(
                "name", phase,
                "mode", "parallel",
                "description", "Standard " + phase + " phase"
            ))
            .toList();
    }

    private String resolveTenantId(HttpRequest request) {
        String header = request.getHeader(io.activej.http.HttpHeaders.of("X-Tenant-ID"));
        if (header != null && !header.isBlank()) {
            return header;
        }
        // Fallback to principal tenant
        Principal principal = request.getAttachment(Principal.class);
        if (principal != null) {
            return principal.getTenantId();
        }
        return null;
    }

    private HttpResponse jsonResponse(Object body) {
        try {
            return HttpResponse.ok200()
                .withJson(objectMapper.writeValueAsString(body))
                .build();
        } catch (Exception e) {
            return HttpResponse.ofCode(500)
                .withJson("{\"error\":\"Failed to serialize response\"}")
                .build();
        }
    }

    // Record classes for JSON deserialization
    public record OnboardingRequest(
        String productId,
        String productName,
        String description,
        String owner,
        String archetype
    ) {}
}
