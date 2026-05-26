package com.ghatana.phr.api.routes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.RoutingServlet;
import io.activej.promise.Promise;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Runtime-truth release readiness endpoint for the PHR cockpit.
 *
 * @doc.type class
 * @doc.purpose ActiveJ route adapter exposing PHR release evidence with runtime-truth proof sections
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 */
public final class PhrReleaseReadinessRoutes {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final List<String> REQUIRED_SECTIONS = List.of(
        "evidenceFreshness",
        "fhirRuntime",
        "consentCache",
        "deployment",
        "rollback",
        "dataCloudRuntime"
    );

    private final Eventloop eventloop;
    private final Path workspaceRoot;

    public PhrReleaseReadinessRoutes(Eventloop eventloop) {
        this(eventloop, discoverWorkspaceRoot());
    }

    public PhrReleaseReadinessRoutes(Eventloop eventloop, Path workspaceRoot) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
        this.workspaceRoot = Objects.requireNonNull(workspaceRoot, "workspaceRoot must not be null");
    }

    /**
     * Returns the routing servlet for release readiness endpoints.
     *
     * @return routing servlet; never null
     */
    public AsyncServlet getServlet() {
        return RoutingServlet.builder(eventloop)
            .with(HttpMethod.GET, "/", this::handleReadiness)
            .build();
    }

    private Promise<HttpResponse> handleReadiness(HttpRequest request) {
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }
        if (!PhrRouteSupport.isPrivileged(context)) {
            return PhrRouteSupport.errorResponse(403, "PHR_RELEASE_READINESS_FORBIDDEN",
                "Release readiness evidence requires an admin principal");
        }

        String environment = normalizeEnvironment(request.getQueryParameter("environment"));
        try {
            JsonNode phrEvidence = readRequired(".kernel/evidence/phr/phr-release-readiness.json");
            JsonNode providerEvidence = readOptional(".kernel/evidence/data-cloud/platform-provider-readiness.json");
            JsonNode runtimeProfile = readOptional(".kernel/evidence/data-cloud-release-runtime-profile.json");
            return PhrRouteSupport.jsonResponse(200, buildResponse(context, environment, phrEvidence, providerEvidence, runtimeProfile));
        } catch (IOException ex) {
            return PhrRouteSupport.errorResponse(503, "PHR_RELEASE_READINESS_UNAVAILABLE", ex.getMessage());
        }
    }

    private Map<String, Object> buildResponse(
            PhrRouteSupport.PhrRequestContext context,
            String environment,
            JsonNode phrEvidence,
            JsonNode providerEvidence,
            JsonNode runtimeProfile) {
        Map<String, Object> sections = new LinkedHashMap<>();
        sections.put("evidenceFreshness", evidenceFreshnessSection(phrEvidence));
        sections.put("fhirRuntime", fhirSection(phrEvidence));
        sections.put("consentCache", categorySection(phrEvidence, "cache", "Consent cache proof"));
        sections.put("deployment", environmentSection(phrEvidence, environment));
        sections.put("rollback", categorySection(phrEvidence, "rollback", "Rollback proof"));
        sections.put("dataCloudRuntime", dataCloudRuntimeSection(providerEvidence, runtimeProfile));

        boolean blocked = sections.values().stream()
            .filter(Map.class::isInstance)
            .map(Map.class::cast)
            .anyMatch(PhrReleaseReadinessRoutes::isBlockingSection);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("product", "phr");
        response.put("tenantId", context.tenantId());
        response.put("principalId", context.principalId());
        response.put("role", context.role());
        response.put("environment", environment);
        response.put("generatedAt", text(phrEvidence.path("generatedAt"), text(phrEvidence.path("checkedAt"), Instant.now().toString())));
        response.put("targetCommitSha", text(phrEvidence.path("targetCommitSha"), text(phrEvidence.path("sourceCommitSha"), "")));
        response.put("releaseReadiness", JSON.convertValue(phrEvidence.path("releaseReadiness"), Map.class));
        response.put("runtimeTruthBlocked", blocked);
        response.put("requiredSections", REQUIRED_SECTIONS);
        response.put("sections", sections);
        return response;
    }

    private Map<String, Object> evidenceFreshnessSection(JsonNode evidence) {
        String validationStatus = text(evidence.path("validationStatus"), "missing");
        String targetCommit = text(evidence.path("targetCommitSha"), text(evidence.path("sourceCommitSha"), ""));
        String evidenceCommit = text(evidence.path("evidenceRun").path("commit"), "");
        String expiresAt = text(evidence.path("expiresAt"), "");
        boolean pass = isPassingStatus(validationStatus)
            && !targetCommit.isBlank()
            && targetCommit.equals(evidenceCommit)
            && !expiresAt.isBlank();
        return section(
            "Evidence freshness",
            pass ? "passed" : "blocked",
            pass,
            pass ? "Evidence commit, target commit, and expiry are bound." : "Evidence is stale, expired, or not bound to the target commit.",
            Map.of(
                "validationStatus", validationStatus,
                "evidenceCommit", evidenceCommit,
                "targetCommitSha", targetCommit,
                "expiresAt", expiresAt
            )
        );
    }

    private Map<String, Object> fhirSection(JsonNode evidence) {
        JsonNode fhir = evidence.path("evidenceCategories").path("fhir");
        JsonNode resources = fhir.path("compliance").path("resourcesSupported");
        boolean pass = isPassingStatus(text(fhir.path("status"), "missing")) && resources.isArray() && resources.size() > 0;
        return section(
            "FHIR runtime registry",
            pass ? "passed" : "blocked",
            pass,
            pass ? "Runtime-supported FHIR resources are present in release evidence." : "FHIR runtime support evidence is missing or blocked.",
            Map.of(
                "resourcesSupported", JSON.convertValue(resources, List.class),
                "fhirVersion", text(fhir.path("compliance").path("fhirVersion"), "unknown"),
                "validationPassed", fhir.path("compliance").path("validationPassed").asBoolean(false)
            )
        );
    }

    private Map<String, Object> categorySection(JsonNode evidence, String categoryId, String label) {
        JsonNode category = evidence.path("evidenceCategories").path(categoryId);
        String status = text(category.path("status"), "missing");
        boolean pass = isPassingStatus(status);
        return section(
            label,
            pass ? status : "blocked",
            pass,
            pass ? label + " is runtime-proven." : label + " is missing, partial, or blocked.",
            JSON.convertValue(category, Map.class)
        );
    }

    private Map<String, Object> environmentSection(JsonNode evidence, String environment) {
        JsonNode deployment = evidence.path("evidenceCategories").path("deployment");
        JsonNode env = deployment.path("environments").path(environment);
        String deploymentStatus = text(deployment.path("status"), "missing");
        String envStatus = text(env.path("status"), "missing");
        boolean pass = isPassingStatus(deploymentStatus) && isPassingStatus(envStatus);
        return section(
            "Deployment proof",
            pass ? envStatus : "blocked",
            pass,
            pass ? environment + " deployment proof is ready." : environment + " deployment proof is missing, partial, or blocked.",
            Map.of(
                "deploymentStatus", deploymentStatus,
                "environmentStatus", envStatus,
                "environment", environment,
                "details", JSON.convertValue(env, Map.class)
            )
        );
    }

    private Map<String, Object> dataCloudRuntimeSection(JsonNode providerEvidence, JsonNode runtimeProfile) {
        String providerStatus = text(providerEvidence.path("status"), "missing");
        boolean runtimePass = runtimeProfile.path("pass").asBoolean(false);
        boolean pass = isPassingStatus(providerStatus) && runtimePass;
        return section(
            "Data Cloud runtime truth",
            pass ? "passed" : "blocked",
            pass,
            pass ? "Provider and runtime profile proof are passing." : "Data Cloud provider/runtime proof is missing or blocked.",
            Map.of(
                "providerStatus", providerStatus,
                "runtimeProfilePass", runtimePass,
                "providerGeneratedAt", text(providerEvidence.path("generatedAt"), ""),
                "runtimeGeneratedAt", text(runtimeProfile.path("generatedAt"), "")
            )
        );
    }

    private static Map<String, Object> section(
            String label,
            String status,
            boolean runtimeProven,
            String message,
            Object details) {
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("label", label);
        section.put("status", status);
        section.put("runtimeProven", runtimeProven);
        section.put("message", message);
        section.put("details", details);
        return section;
    }

    private static boolean isBlockingSection(Map<?, ?> section) {
        Object runtimeProven = section.get("runtimeProven");
        String status = String.valueOf(section.get("status"));
        return !Boolean.TRUE.equals(runtimeProven) || !isPassingStatus(status);
    }

    private static boolean isPassingStatus(String status) {
        String normalized = status == null ? "" : status.toLowerCase(Locale.ROOT);
        return normalized.equals("pass")
            || normalized.equals("passed")
            || normalized.equals("ready")
            || normalized.equals("validated")
            || normalized.equals("current");
    }

    private JsonNode readRequired(String relativePath) throws IOException {
        Path path = workspaceRoot.resolve(relativePath).normalize();
        if (!Files.exists(path)) {
            throw new IOException("Required evidence file is missing: " + relativePath);
        }
        return JSON.readTree(Files.readString(path));
    }

    private static Path discoverWorkspaceRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.isDirectory(current.resolve(".kernel"))) {
                return current;
            }
            current = current.getParent();
        }
        return Path.of("").toAbsolutePath();
    }

    private JsonNode readOptional(String relativePath) throws IOException {
        Path path = workspaceRoot.resolve(relativePath).normalize();
        if (!Files.exists(path)) {
            return JSON.createObjectNode();
        }
        return JSON.readTree(Files.readString(path));
    }

    private static String normalizeEnvironment(String value) {
        if (value == null || value.isBlank()) {
            return "staging";
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "local", "dev", "staging", "prod" -> normalized;
            default -> "staging";
        };
    }

    private static String text(JsonNode node, String fallback) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return fallback;
        }
        String value = node.asText();
        return value == null || value.isBlank() ? fallback : value;
    }
}
