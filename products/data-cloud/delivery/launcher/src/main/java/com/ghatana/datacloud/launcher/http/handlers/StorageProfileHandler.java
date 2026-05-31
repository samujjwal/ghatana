package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.DataCloudClient.Query;
import com.ghatana.datacloud.launcher.http.security.RequestContext;
import com.ghatana.datacloud.launcher.http.security.RequestContextResolver;
import com.ghatana.platform.audit.AuditEvent;
import com.ghatana.platform.audit.AuditService;
import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.security.annotation.RequiresRole;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * HTTP handler for storage profile registry (H2).
 *
 * <p>Manages storage backend configurations as metadata entities in the
 * {@code dc_storage_profiles} collection. Each profile stores configuration
 * for cloud storage, databases, or local storage backends.
 *
 * <p>Routes served:
 * <ul>
 *   <li>{@code GET  /api/v1/storage-profiles}                       — list all profiles for tenant</li>
 *   <li>{@code POST /api/v1/storage-profiles}                       — create a new profile</li>
 *   <li>{@code GET  /api/v1/storage-profiles/:profileId}            — get profile by ID</li>
 *   <li>{@code PUT  /api/v1/storage-profiles/:profileId}            — update profile</li>
 *   <li>{@code DELETE /api/v1/storage-profiles/:profileId}            — delete profile</li>
 *   <li>{@code POST /api/v1/storage-profiles/:profileId/set-default} — set as default profile</li>
 *   <li>{@code GET  /api/v1/storage-profiles/:profileId/metrics}    — get profile metrics</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose HTTP surface for storage profile registry
 * @doc.layer product
 * @doc.pattern Handler
 */
@RequiresRole("ADMIN")
public final class StorageProfileHandler {

    private static final Logger log = LoggerFactory.getLogger(StorageProfileHandler.class);
    private static final String DC_STORAGE_PROFILES = "dc_storage_profiles";
    private static final String CREDENTIALS_KEY = "credentials";
    private static final String SECRET_REFERENCE_KEY = "secretRef";
    private static final Set<String> VALID_TYPES = Set.of(
        "S3", "AZURE_BLOB", "GCS", "POSTGRESQL", "MYSQL", "MONGODB", "HDFS", "LOCAL"
    );
    private static final Set<String> VALID_ENCRYPTION_TYPES = Set.of(
        "NONE", "AES256", "AWS_KMS", "GCP_KMS", "AZURE_KEY_VAULT"
    );
    private static final Set<String> VALID_COMPRESSION_TYPES = Set.of(
        "NONE", "GZIP", "SNAPPY", "ZSTD"
    );

    private final DataCloudClient client;
    private final HttpHandlerSupport http;
    private final AuditService auditService;
    private final String deploymentProfile;

    /**
     * @param client entity store client for persisting profile metadata
     * @param http shared HTTP helper
     * @param auditService optional audit service; when null audit emissions are skipped
     * @param deploymentProfile deployment profile (e.g., "local", "sovereign", "staging", "production")
     */
    public StorageProfileHandler(DataCloudClient client, HttpHandlerSupport http,
                                 AuditService auditService, String deploymentProfile) {
        this.client = client;
        this.http = http;
        this.auditService = auditService;
        this.deploymentProfile = deploymentProfile != null ? deploymentProfile : "local";
    }

    /**
     * GET /api/v1/storage-profiles — list all storage profiles for tenant
     */
    public Promise<HttpResponse> listProfiles(HttpRequest request) {
        RequestContextResolver.ResolutionResult contextResult = http.requireRequestContext(request);
        if (!contextResult.isSuccess()) {
            return Promise.of(http.errorResponse(contextResult.errorCode(), contextResult.errorMessage()));
        }
        String tenantId = contextResult.context().map(RequestContext::tenantId).orElse(null);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "Tenant ID required"));
        }
        log.info("Listing storage profiles for tenant: {}", tenantId);

        try {
            List<DataCloudClient.Entity> result = client.query(tenantId, DC_STORAGE_PROFILES, Query.builder()
                .filter(DataCloudClient.Filter.eq("isActive", true))
                .build()).getResult();
            // Redact sensitive fields and convert to data maps
            List<Map<String, Object>> dataList = result.stream()
                .map(entity -> {
                    Map<String, Object> data = new java.util.HashMap<>(entity.data());
                    redactSensitiveFields(data);
                    return data;
                })
                .toList();
            return Promise.of(http.jsonResponse(Map.of("storageProfiles", dataList, "count", dataList.size())));
        } catch (Exception e) {
            log.error("Failed to list storage profiles", e);
            return Promise.of(http.errorResponse(500, "Failed to list storage profiles"));
        }
    }

    /**
     * POST /api/v1/storage-profiles — create a new storage profile
     */
    public Promise<HttpResponse> createProfile(HttpRequest request) {
        RequestContextResolver.ResolutionResult contextResult = http.requireRequestContext(request);
        if (!contextResult.isSuccess()) {
            return Promise.of(http.errorResponse(contextResult.errorCode(), contextResult.errorMessage()));
        }
        String tenantId = contextResult.context().map(RequestContext::tenantId).orElse(null);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "Tenant ID required"));
        }
        
        Map<String, Object> body;
        try {
            body = http.parseJsonBody(request);
        } catch (Exception e) {
            return Promise.of(http.errorResponse(400, "Failed to parse request body"));
        }
        
        // Validate required fields
        String name = (String) body.get("name");
        String type = (String) body.get("type");
        String storageUri = (String) body.get("storageUri");

        if (name == null || name.isBlank()) {
            return Promise.of(http.errorResponse(400, "Profile name is required"));
        }
        if (type == null || !VALID_TYPES.contains(type)) {
            return Promise.of(http.errorResponse(400, "Invalid storage type: " + type));
        }
        if (storageUri == null || storageUri.isBlank()) {
            return Promise.of(http.errorResponse(400, "Storage URI is required"));
        }

        // Validate encryption and compression types if provided
        if (body.containsKey("encryptionType")) {
            String encryptionType = (String) body.get("encryptionType");
            if (!VALID_ENCRYPTION_TYPES.contains(encryptionType)) {
                return Promise.of(http.errorResponse(400, "Invalid encryption type: " + encryptionType));
            }
        }
        if (body.containsKey("compressionType")) {
            String compressionType = (String) body.get("compressionType");
            if (!VALID_COMPRESSION_TYPES.contains(compressionType)) {
                return Promise.of(http.errorResponse(400, "Invalid compression type: " + compressionType));
            }
        }

        // Set tenant and timestamps
        body.put("tenantId", tenantId);
        body.put("isActive", true);
        body.put("isDefault", false);
        body.put("createdAt", Instant.now().toString());
        body.put("updatedAt", Instant.now().toString());

        log.info("Creating storage profile '{}' for tenant: {}", name, tenantId);

        try {
            DataCloudClient.Entity created = client.save(tenantId, DC_STORAGE_PROFILES, body).getResult();
            emitAuditEvent(tenantId, principalName(contextResult, "system"), "storage_profile.created", created.data());
            // Redact sensitive fields in response
            redactSensitiveFields(created.data());
            return Promise.of(http.jsonResponse(201, Map.of("id", created.id(), "data", created.data())));
        } catch (Exception e) {
            log.error("Failed to create storage profile", e);
            return Promise.of(http.errorResponse(500, "Failed to create storage profile"));
        }
    }

    /**
     * GET /api/v1/storage-profiles/:profileId — get profile by ID
     */
    public Promise<HttpResponse> getProfile(HttpRequest request) {
        String profileId = request.getPathParameter("profileId");
        RequestContextResolver.ResolutionResult contextResult = http.requireRequestContext(request);
        if (!contextResult.isSuccess()) {
            return Promise.of(http.errorResponse(contextResult.errorCode(), contextResult.errorMessage()));
        }
        String tenantId = contextResult.context().map(RequestContext::tenantId).orElse(null);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "Tenant ID required"));
        }
        log.info("Getting storage profile {} for tenant: {}", profileId, tenantId);

        try {
            DataCloudClient.Entity profile = client.findById(tenantId, DC_STORAGE_PROFILES, profileId).getResult()
                .orElseThrow(() -> new IllegalArgumentException("Profile not found: " + profileId));
            // Verify tenant ownership
            if (!tenantId.equals(profile.data().get("tenantId"))) {
                return Promise.of(http.forbiddenResponse("Access denied to profile", http.resolveCorrelationId(request)));
            }
            // Redact sensitive fields
            Map<String, Object> data = new java.util.HashMap<>(profile.data());
            redactSensitiveFields(data);
            return Promise.of(http.jsonResponse(data));
        } catch (Exception e) {
            log.error("Failed to get storage profile", e);
            return Promise.of(http.errorResponse(500, "Failed to get storage profile"));
        }
    }

    /**
     * PUT /api/v1/storage-profiles/:profileId — update profile
     */
    public Promise<HttpResponse> updateProfile(HttpRequest request) {
        String profileId = request.getPathParameter("profileId");
        RequestContextResolver.ResolutionResult contextResult = http.requireRequestContext(request);
        if (!contextResult.isSuccess()) {
            return Promise.of(http.errorResponse(contextResult.errorCode(), contextResult.errorMessage()));
        }
        String tenantId = contextResult.context().map(RequestContext::tenantId).orElse(null);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "Tenant ID required"));
        }
        
        Map<String, Object> body;
        try {
            body = http.parseJsonBody(request);
        } catch (Exception e) {
            return Promise.of(http.errorResponse(400, "Failed to parse request body"));
        }
        
        log.info("Updating storage profile {} for tenant: {}", profileId, tenantId);

        try {
            DataCloudClient.Entity existing = client.findById(tenantId, DC_STORAGE_PROFILES, profileId).getResult()
                .orElseThrow(() -> new IllegalArgumentException("Profile not found: " + profileId));
            // Verify tenant ownership
            if (!tenantId.equals(existing.data().get("tenantId"))) {
                return Promise.of(http.forbiddenResponse("Access denied to profile", http.resolveCorrelationId(request)));
            }

            // Validate type if provided
            if (body.containsKey("type")) {
                String type = (String) body.get("type");
                if (!VALID_TYPES.contains(type)) {
                    return Promise.of(http.errorResponse(400, "Invalid storage type: " + type));
                }
            }

            // Validate encryption and compression types if provided
            if (body.containsKey("encryptionType")) {
                String encryptionType = (String) body.get("encryptionType");
                if (!VALID_ENCRYPTION_TYPES.contains(encryptionType)) {
                    return Promise.of(http.errorResponse(400, "Invalid encryption type: " + encryptionType));
                }
            }
            if (body.containsKey("compressionType")) {
                String compressionType = (String) body.get("compressionType");
                if (!VALID_COMPRESSION_TYPES.contains(compressionType)) {
                    return Promise.of(http.errorResponse(400, "Invalid compression type: " + compressionType));
                }
            }

            // Update timestamp
            body.put("updatedAt", Instant.now().toString());

            DataCloudClient.Entity updated = client.save(tenantId, DC_STORAGE_PROFILES, body).getResult();
            emitAuditEvent(tenantId, principalName(contextResult, "system"), "storage_profile.updated", updated.data());
            // Redact sensitive fields in response
            Map<String, Object> data = new java.util.HashMap<>(updated.data());
            redactSensitiveFields(data);
            return Promise.of(http.jsonResponse(data));
        } catch (Exception e) {
            log.error("Failed to update storage profile", e);
            return Promise.of(http.errorResponse(500, "Failed to update storage profile"));
        }
    }

    /**
     * DELETE /api/v1/storage-profiles/:profileId — delete profile
     */
    public Promise<HttpResponse> deleteProfile(HttpRequest request) {
        String profileId = request.getPathParameter("profileId");
        RequestContextResolver.ResolutionResult contextResult = http.requireRequestContext(request);
        if (!contextResult.isSuccess()) {
            return Promise.of(http.errorResponse(contextResult.errorCode(), contextResult.errorMessage()));
        }
        String tenantId = contextResult.context().map(RequestContext::tenantId).orElse(null);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "Tenant ID required"));
        }
        log.info("Deleting storage profile {} for tenant: {}", profileId, tenantId);

        try {
            DataCloudClient.Entity existing = client.findById(tenantId, DC_STORAGE_PROFILES, profileId).getResult()
                .orElseThrow(() -> new IllegalArgumentException("Profile not found: " + profileId));
            // Verify tenant ownership
            if (!tenantId.equals(existing.data().get("tenantId"))) {
                return Promise.of(http.forbiddenResponse("Access denied to profile", http.resolveCorrelationId(request)));
            }

            // Prevent deletion of default profile
            if (Boolean.TRUE.equals(existing.data().get("isDefault"))) {
                return Promise.of(http.errorResponse(400, "Cannot delete default storage profile"));
            }

            client.delete(tenantId, DC_STORAGE_PROFILES, profileId).getResult();
            emitAuditEvent(tenantId, principalName(contextResult, "system"), "storage_profile.deleted", existing.data());
            return Promise.of(http.jsonResponse(Map.of("message", "Storage profile deleted")));
        } catch (Exception e) {
            log.error("Failed to delete storage profile", e);
            return Promise.of(http.errorResponse(500, "Failed to delete storage profile"));
        }
    }

    /**
     * POST /api/v1/storage-profiles/:profileId/set-default — set as default profile
     */
    public Promise<HttpResponse> setDefault(HttpRequest request) {
        String profileId = request.getPathParameter("profileId");
        RequestContextResolver.ResolutionResult contextResult = http.requireRequestContext(request);
        if (!contextResult.isSuccess()) {
            return Promise.of(http.errorResponse(contextResult.errorCode(), contextResult.errorMessage()));
        }
        String tenantId = contextResult.context().map(RequestContext::tenantId).orElse(null);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "Tenant ID required"));
        }
        log.info("Setting storage profile {} as default for tenant: {}", profileId, tenantId);

        try {
            DataCloudClient.Entity profile = client.findById(tenantId, DC_STORAGE_PROFILES, profileId).getResult()
                .orElseThrow(() -> new IllegalArgumentException("Profile not found: " + profileId));
            // Verify tenant ownership
            if (!tenantId.equals(profile.data().get("tenantId"))) {
                return Promise.of(http.forbiddenResponse("Access denied to profile", http.resolveCorrelationId(request)));
            }

            // Unset current default
            List<DataCloudClient.Entity> defaultProfiles = client.query(tenantId, DC_STORAGE_PROFILES, Query.builder()
                .filter(DataCloudClient.Filter.eq("isDefault", true))
                .build()).getResult();
            for (DataCloudClient.Entity dp : defaultProfiles) {
                if (dp.data().containsKey("id")) {
                    String dpId = (String) dp.data().get("id");
                    client.save(tenantId, DC_STORAGE_PROFILES, Map.of("id", dpId, "isDefault", false)).getResult();
                }
            }
            // Set new default
            Map<String, Object> update = Map.of("isDefault", true, "updatedAt", Instant.now().toString());
            DataCloudClient.Entity updated = client.save(tenantId, DC_STORAGE_PROFILES, update).getResult();
            emitAuditEvent(tenantId, principalName(contextResult, "system"), "storage_profile.set_default", updated.data());
            // Redact sensitive fields in response
            Map<String, Object> data = new java.util.HashMap<>(updated.data());
            redactSensitiveFields(data);
            return Promise.of(http.jsonResponse(data));
        } catch (Exception e) {
            log.error("Failed to set default storage profile", e);
            return Promise.of(http.errorResponse(500, "Failed to set default storage profile"));
        }
    }

    public Promise<HttpResponse> setDefaultProfile(HttpRequest request) {
        return setDefault(request);
    }

    /**
     * GET /api/v1/storage-profiles/:profileId/metrics — get profile metrics
     */
    public Promise<HttpResponse> getMetrics(HttpRequest request) {
        String profileId = request.getPathParameter("profileId");
        RequestContextResolver.ResolutionResult contextResult = http.requireRequestContext(request);
        if (!contextResult.isSuccess()) {
            return Promise.of(http.errorResponse(contextResult.errorCode(), contextResult.errorMessage()));
        }
        String tenantId = contextResult.context().map(RequestContext::tenantId).orElse(null);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "Tenant ID required"));
        }
        log.info("Getting metrics for storage profile {} for tenant: {}", profileId, tenantId);

        try {
            DataCloudClient.Entity profile = client.findById(tenantId, DC_STORAGE_PROFILES, profileId).getResult()
                .orElseThrow(() -> new IllegalArgumentException("Profile not found: " + profileId));
            // Verify tenant ownership
            if (!tenantId.equals(profile.data().get("tenantId"))) {
                return Promise.of(http.forbiddenResponse("Access denied to profile", http.resolveCorrelationId(request)));
            }

            // Return mock metrics for now (real metrics would come from storage backend)
            Map<String, Object> metrics = Map.of(
                "profileId", profileId,
                "totalSizeBytes", 0L,
                "objectCount", 0,
                "lastAccessedAt", Instant.now().toString(),
                "healthStatus", "HEALTHY",
                "errorCount", 0
            );
            return Promise.of(http.jsonResponse(metrics));
        } catch (Exception e) {
            log.error("Failed to get storage profile metrics", e);
            return Promise.of(http.errorResponse(500, "Failed to get storage profile metrics"));
        }
    }

    public Promise<HttpResponse> getProfileMetrics(HttpRequest request) {
        return getMetrics(request);
    }

    /**
     * Redact sensitive fields from profile data
     */
    private void redactSensitiveFields(Map<String, Object> profile) {
        if (profile.containsKey("storageUri")) {
            profile.put("storageUri", "***REDACTED***");
        }
        if (profile.containsKey("accessKeys")) {
            profile.put("accessKeys", "***REDACTED***");
        }
        if (profile.containsKey(CREDENTIALS_KEY)) {
            profile.put(CREDENTIALS_KEY, "***REDACTED***");
        }
    }

    /**
     * Emit audit event for storage profile operations
     */
    private void emitAuditEvent(String tenantId, String userId, String action, Map<String, Object> data) {
        if (auditService != null) {
            try {
                AuditEvent event = AuditEvent.builder()
                    .tenantId(tenantId)
                    .principal(userId != null ? userId : "system")
                    .eventType(action)
                    .resourceType("storage_profile")
                    .success(true)
                    .timestamp(Instant.now())
                    .details(data)
                    .build();
                auditService.record(event).whenException(e ->
                    log.warn("Failed to record audit event for action: {}", action, e));
            } catch (Exception e) {
                log.warn("Failed to emit audit event for action: {}", action, e);
            }
        }
    }

    private String principalName(RequestContextResolver.ResolutionResult contextResult, String fallback) {
        return contextResult.context()
            .flatMap(RequestContext::principal)
            .map(principal -> principal.getName())
            .orElse(fallback);
    }
}
