package com.ghatana.kernel.release;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.promise.Promise;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * File-based implementation of release readiness runtime service.
 *
 * <p>This implementation reads release readiness evidence from
 * .kernel/evidence files, providing backward compatibility with
 * existing file-based evidence storage while abstracting the
 * parsing logic behind the Kernel runtime API.</p>
 *
 * @doc.type class
 * @doc.purpose File-based implementation of release readiness runtime service
 * @doc.layer platform
 * @doc.pattern Service
 */
public final class FileBasedReleaseReadinessRuntimeService implements ReleaseReadinessRuntimeService {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    private final Path workspaceRoot;

    public FileBasedReleaseReadinessRuntimeService() {
        this(discoverWorkspaceRoot());
    }

    public FileBasedReleaseReadinessRuntimeService(Path workspaceRoot) {
        this.workspaceRoot = Objects.requireNonNull(workspaceRoot, "workspaceRoot must not be null");
    }

    @Override
    public Promise<Map<String, Object>> getReleaseReadiness(String productId, String environment) {
        try {
            String phrEvidencePath = getEvidencePath(productId);
            JsonNode phrEvidence = readRequired(phrEvidencePath);
            JsonNode providerEvidence = readOptional(".kernel/evidence/data-cloud/platform-provider-readiness.json");
            JsonNode runtimeProfile = readOptional(".kernel/evidence/data-cloud-release-runtime-profile.json");
            
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("productId", productId);
            response.put("environment", environment);
            response.put("generatedAt", text(phrEvidence.path("generatedAt"), text(phrEvidence.path("checkedAt"), "")));
            response.put("targetCommitSha", text(phrEvidence.path("targetCommitSha"), text(phrEvidence.path("sourceCommitSha"), "")));
            response.put("releaseReadiness", JSON.convertValue(phrEvidence.path("releaseReadiness"), Map.class));
            response.put("evidenceCategories", JSON.convertValue(phrEvidence.path("evidenceCategories"), Map.class));
            response.put("providerEvidence", JSON.convertValue(providerEvidence, Map.class));
            response.put("runtimeProfile", JSON.convertValue(runtimeProfile, Map.class));
            
            return Promise.of(response);
        } catch (IOException ex) {
            return Promise.ofException(new RuntimeException("Release readiness evidence unavailable: " + ex.getMessage(), ex));
        }
    }

    @Override
    public Promise<Map<String, Object>> getReleaseReadinessSection(String productId, String environment, String sectionId) {
        try {
            String phrEvidencePath = getEvidencePath(productId);
            JsonNode phrEvidence = readRequired(phrEvidencePath);
            
            JsonNode category = phrEvidence.path("evidenceCategories").path(sectionId);
            if (category.isMissingNode()) {
                return Promise.ofException(new IllegalArgumentException("Section not found: " + sectionId));
            }
            
            Map<String, Object> sectionData = new LinkedHashMap<>();
            sectionData.put("productId", productId);
            sectionData.put("environment", environment);
            sectionData.put("sectionId", sectionId);
            sectionData.put("section", JSON.convertValue(category, Map.class));
            sectionData.put("evidenceLinks", Map.of(
                "phrReleaseReadiness", phrEvidencePath,
                "categoryPath", phrEvidencePath + "#/evidenceCategories/" + sectionId,
                "categoryStatus", text(category.path("status"), "unknown")
            ));
            
            return Promise.of(sectionData);
        } catch (IOException ex) {
            return Promise.ofException(new RuntimeException("Release readiness evidence unavailable: " + ex.getMessage(), ex));
        }
    }

    private String getEvidencePath(String productId) {
        return ".kernel/evidence/" + productId + "/" + productId + "-release-readiness.json";
    }

    private JsonNode readRequired(String relativePath) throws IOException {
        Path path = workspaceRoot.resolve(relativePath).normalize();
        if (!Files.exists(path)) {
            throw new IOException("Required evidence file is missing: " + relativePath);
        }
        return JSON.readTree(Files.readString(path));
    }

    private JsonNode readOptional(String relativePath) throws IOException {
        Path path = workspaceRoot.resolve(relativePath).normalize();
        if (!Files.exists(path)) {
            return JSON.createObjectNode();
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

    private static String text(JsonNode node, String fallback) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return fallback;
        }
        String value = node.asText();
        return value == null || value.isBlank() ? fallback : value;
    }
}
