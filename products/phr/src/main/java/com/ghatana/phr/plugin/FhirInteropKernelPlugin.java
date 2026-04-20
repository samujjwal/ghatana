package com.ghatana.phr.plugin;

import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter;
import com.ghatana.kernel.adapter.datacloud.DataQueryRequest;
import com.ghatana.kernel.adapter.datacloud.DataReadRequest;
import com.ghatana.kernel.adapter.datacloud.DataResult;
import com.ghatana.kernel.adapter.datacloud.DataWriteRequest;
import com.ghatana.kernel.adapter.datacloud.QueryResult;
import com.ghatana.kernel.adapter.datacloud.SchemaCreateRequest;
import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.plugin.KernelPlugin;
import com.ghatana.kernel.plugin.PluginManifest;
import com.ghatana.platform.health.HealthStatus;
import com.ghatana.platform.kernel.dependency.DependencyAvailability;
import com.ghatana.platform.kernel.dependency.DependencyMode;
import com.ghatana.phr.fhir.FhirResourceService;
import com.ghatana.phr.fhir.FhirTransformer;
import com.ghatana.phr.fhir.FhirValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * FHIR R4 interoperability plugin for healthcare data exchange.
 *
 * <p>Provides FHIR R4 resource processing, validation, transformation,
 * and exchange capabilities for PHR integration with external healthcare systems.</p>
 *
 * @doc.type class
 * @doc.purpose FHIR R4 interoperability plugin for healthcare data exchange
 * @doc.layer product
 * @doc.pattern Plugin
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public class FhirInteropKernelPlugin implements KernelPlugin, FhirResourceService, FhirValidator, FhirTransformer {

    private static final Logger LOG = LoggerFactory.getLogger(FhirInteropKernelPlugin.class);
    private static final String PLUGIN_ID = "fhir-interop-r4";
    private static final String VERSION = "1.0.0";
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final String FHIR_RESOURCE_DATASET = "phr.fhir.resources";
    private static final String DATACLOUD_MODE_ENV = "PHR_DATACLOUD_MODE";
    private static final String DATACLOUD_MODE_DEFAULT = "STRICT";

    private volatile KernelContext context;
    private volatile DependencyAvailability dataCloudAvailability;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean installed = new AtomicBoolean(false);
    private final ConcurrentHashMap<String, FhirResource> resourceCache = new ConcurrentHashMap<>();

    @Override
    public PluginManifest getManifest() {
        return PluginManifest.builder()
            .pluginId(PLUGIN_ID)
            .version(VERSION)
            .description("FHIR R4 interoperability plugin for healthcare data exchange")
            .author("Ghatana PHR Team")
            .license("Apache-2.0")
            .capability(new KernelCapability(
                "fhir.r4.interop",
                "FHIR R4 Interoperability",
                "FHIR R4 resource processing and exchange",
                KernelCapability.CapabilityType.INTEGRATION,
                Map.of("fhir_version", "R4", "profile", "core")
            ))
            .dependency(new com.ghatana.kernel.descriptor.KernelDependency(
                "data-storage", "1.0.0",
                com.ghatana.kernel.descriptor.KernelDependency.DependencyType.CAPABILITY, false
            ))
            .requiredKernelVersion("1.0.0")
            .tag("healthcare")
            .tag("fhir")
            .tag("interop")
            .build();
    }

    @Override
    public Set<String> getExportedContracts() {
        return Set.of(
            "com.ghatana.phr.fhir.FhirResourceService",
            "com.ghatana.phr.fhir.FhirValidator",
            "com.ghatana.phr.fhir.FhirTransformer"
        );
    }

    @Override
    public Set<String> getRequiredContracts() {
        return Set.of(
            "com.ghatana.kernel.storage.DataStorageService"
        );
    }

    @Override
    public void initialize(KernelContext context) {
        if (!initialized.compareAndSet(false, true)) {
            return;
        }
        this.context = context;

        // Initialize DataCloud dependency availability tracker with configured mode
        DependencyMode datacloudMode = resolveDataCloudMode();
        boolean datacloudAvailable = context != null && context.hasDependency(DataCloudKernelAdapter.class);
        this.dataCloudAvailability = new DependencyAvailability("DataCloudKernelAdapter", datacloudMode, datacloudAvailable);

        if (datacloudMode.isStrict() && !datacloudAvailable) {
            LOG.warn("INITIALIZATION WARNING: DataCloud adapter not found but STRICT mode is enabled. " +
                    "Write operations will fail. Consider using DEGRADED mode for development.");
        }

        LOG.info("PHR FHIR Plugin initialized with DataCloud mode: {} (available: {})", datacloudMode.name(), datacloudAvailable);

        registerEventHandlers();
    }

    /**
     * Resolve DataCloud operation mode from environment or use default.
     * STRICT mode (default): Fail fast on missing dependencies, suitable for production.
     * DEGRADED mode: Allow graceful degradation with fallbacks, suitable for development.
     */
    private DependencyMode resolveDataCloudMode() {
        String modeEnv = System.getenv(DATACLOUD_MODE_ENV);
        if (modeEnv != null && !modeEnv.isEmpty()) {
            try {
                return DependencyMode.valueOf(modeEnv.toUpperCase());
            } catch (IllegalArgumentException e) {
                LOG.warn("Invalid {} value: {}; using default: {}", DATACLOUD_MODE_ENV, modeEnv, DATACLOUD_MODE_DEFAULT);
            }
        }
        return DependencyMode.valueOf(DATACLOUD_MODE_DEFAULT);
    }

    @Override
    public Promise<Void> install() {
        if (!installed.compareAndSet(false, true)) {
            return Promise.complete();
        }

        // Initialize FHIR resources, create indexes, etc.
        return initializeFhirResources();
    }

    @Override
    public Promise<Void> uninstall() {
        if (!installed.compareAndSet(true, false)) {
            return Promise.complete();
        }

        // Cleanup FHIR resources
        return cleanupFhirResources();
    }

    @Override
    public Promise<Void> start() {
        if (!started.compareAndSet(false, true)) {
            return Promise.complete();
        }

        // Start FHIR services
        return startFhirServices();
    }

    @Override
    public Promise<Void> stop() {
        if (!started.compareAndSet(true, false)) {
            return Promise.complete();
        }

        // Stop FHIR services
        return stopFhirServices();
    }

    @Override
    public HealthStatus getHealthStatus() {
        if (!started.get()) {
            return HealthStatus.unhealthy("Plugin not started");
        }

        return HealthStatus.healthy("FHIR R4 interop operational");
    }

    // ==================== FHIR Resource Processing ====================

    /** {@inheritDoc} */
    @Override
    public Promise<ValidationResult> validateResource(String resourceType, String resourceJson) {
        if (!started.get()) {
            return Promise.ofException(new IllegalStateException("Plugin not started"));
        }

        // Perform FHIR R4 validation
        boolean valid = performValidation(resourceType, resourceJson);
        String message = valid ? "Valid FHIR R4 resource" : "Validation failed";

        return Promise.of(new ValidationResult(valid, message, resourceType));
    }

    /** {@inheritDoc} */
    @Override
    public Promise<String> transformToFhir(Object internalData, String targetResourceType) {
        if (!started.get()) {
            return Promise.ofException(new IllegalStateException("Plugin not started"));
        }

        // Transform to FHIR R4
        String fhirJson = performTransformation(internalData, targetResourceType);

        return Promise.of(fhirJson);
    }

    /** {@inheritDoc} */
    @Override
    public Promise<Object> transformFromFhir(String fhirJson, String sourceResourceType) {
        if (!started.get()) {
            return Promise.ofException(new IllegalStateException("Plugin not started"));
        }

        // Transform from FHIR R4
        Object internalData = performReverseTransformation(fhirJson, sourceResourceType);

        return Promise.of(internalData);
    }

    /** {@inheritDoc} */
    @Override
    public Promise<Void> storeResource(FhirResource resource) {
        if (!started.get()) {
            return Promise.ofException(new IllegalStateException("Plugin not started"));
        }

        resourceCache.put(resource.getId(), resource);

        // Persist to storage
        return persistResource(resource);
    }

    /** {@inheritDoc} */
    @Override
    public Promise<FhirResource> getResource(String resourceId) {
        if (!started.get()) {
            return Promise.ofException(new IllegalStateException("Plugin not started"));
        }

        FhirResource cached = resourceCache.get(resourceId);
        if (cached != null) {
            return Promise.of(cached);
        }

        // Load from storage
        return loadResource(resourceId);
    }

    /** {@inheritDoc} */
    @Override
    public Promise<SearchResult> searchResources(String resourceType, Map<String, String> searchParams) {
        if (!started.get()) {
            return Promise.ofException(new IllegalStateException("Plugin not started"));
        }

        // Perform search
        return performSearch(resourceType, searchParams);
    }

    // ==================== Private Methods ====================

    private void registerEventHandlers() {
        // Register event handlers
    }

    private Promise<Void> initializeFhirResources() {
        // Verify DataCloud availability for schema creation (write operation)
        try {
            dataCloudAvailability.verifyAvailable("FHIR_RESOURCE_SCHEMA_CREATION", true);
        } catch (IllegalStateException e) {
            LOG.error("Cannot initialize FHIR resources: {}", e.getMessage());
            return Promise.ofException(e);
        }

        if (!dataCloudAvailability.isAvailable()) {
            LOG.warn("DataCloud not available for FHIR resource initialization in DEGRADED mode");
            return Promise.complete();
        }

        return context.getDependency(DataCloudKernelAdapter.class).createSchema(
            new SchemaCreateRequest(
                FHIR_RESOURCE_DATASET,
                Map.of(
                    "id", "string",
                    "resourceType", "string",
                    "patient", "string",
                    "subject", "string",
                    "identifier", "string",
                    "code", "string",
                    "lastUpdated", "timestamp"
                ),
                Map.of("retention", "25years")
            )
        );
    }

    private Promise<Void> cleanupFhirResources() {
        return Promise.complete();
    }

    private Promise<Void> startFhirServices() {
        return Promise.complete();
    }

    private Promise<Void> stopFhirServices() {
        return Promise.complete();
    }

    private boolean performValidation(String resourceType, String resourceJson) {
        if (resourceJson == null || resourceJson.isBlank()) {
            return false;
        }

        // Allowed FHIR R4 resource types
        Set<String> allowedTypes = Set.of(
                "Patient", "Observation", "Encounter", "Condition", "Procedure",
                "Medication", "AllergyIntolerance", "DiagnosticReport",
                "MedicationRequest", "Immunization", "CarePlan", "Coverage");
        if (!allowedTypes.contains(resourceType)) {
            return false;
        }

        // Parse JSON and validate required FHIR R4 structural fields
        try {
            JsonNode root = JSON_MAPPER.readTree(resourceJson);

            // FHIR R4 requires `resourceType` field matching the declared type
            JsonNode rtNode = root.get("resourceType");
            if (rtNode == null || !resourceType.equals(rtNode.asText())) {
                return false;
            }

            // Resource-type-specific required field checks (PHR-03 contract correctness)
            return switch (resourceType) {
                case "Patient" ->
                        root.has("id") || root.has("identifier") || root.has("name");
                case "Observation" ->
                        root.has("status") && root.has("code") && root.has("subject");
                case "Encounter" ->
                        root.has("status") && root.has("class") && root.has("subject");
                case "Condition" ->
                        root.has("code") && root.has("subject");
                case "Procedure" ->
                        root.has("status") && root.has("code") && root.has("subject");
                case "DiagnosticReport" ->
                        root.has("status") && root.has("code") && root.has("subject");
                default -> true; // Other supported types: structural check sufficient
            };
        } catch (Exception e) {
            return false;
        }
    }

    private String performTransformation(Object internalData, String targetResourceType) {
        try {
            return com.ghatana.phr.fhir.FhirR4TransformationEngine.toFhir(internalData, targetResourceType);
        } catch (IllegalArgumentException e) {
            // Fallback for generic/unsupported input: return a minimal valid FHIR response
            return "{\"resourceType\":\"" + targetResourceType + "\"}";
        }
    }

    private Object performReverseTransformation(String fhirJson, String sourceResourceType) {
        return com.ghatana.phr.fhir.FhirR4TransformationEngine.fromFhir(fhirJson, sourceResourceType);
    }

    private Promise<Void> persistResource(FhirResource resource) {
        // Verify DataCloud availability - will throw in STRICT mode if unavailable
        dataCloudAvailability.verifyAvailable("FHIR_RESOURCE_PERSISTENCE", true);

        if (!dataCloudAvailability.isAvailable()) {
            LOG.warn("DataCloud not available for resource persistence [id={}] in DEGRADED mode; resource cached only", resource.getId());
            return Promise.complete();
        }

        return context.getDependency(DataCloudKernelAdapter.class).writeData(
            new DataWriteRequest(
                FHIR_RESOURCE_DATASET,
                resource.getId(),
                resource.getJson().getBytes(StandardCharsets.UTF_8),
                extractIndexedMetadata(resource)
            )
        ).then(v -> {
            LOG.debug("FHIR resource persisted [id={} dataset={}]", resource.getId(), FHIR_RESOURCE_DATASET);
            return Promise.complete();
        });
    }

    private Promise<FhirResource> loadResource(String resourceId) {
        // Check DataCloud availability - but this is a read operation, so allowed in degraded mode
        if (!dataCloudAvailability.isAvailable()) {
            LOG.debug("DataCloud not available; returning cached/empty resource [id={}]", resourceId);
            return Promise.of(new FhirResource(resourceId, "Unknown", "{}", Instant.now()));
        }

        return context.getDependency(DataCloudKernelAdapter.class)
            .readData(new DataReadRequest(FHIR_RESOURCE_DATASET, resourceId, Map.of()))
            .map(result -> {
                if (result == null) {
                    return new FhirResource(resourceId, "Unknown", "{}", Instant.now());
                }

                FhirResource resource = toFhirResource(result);
                resourceCache.put(resource.getId(), resource);
                return resource;
            })
            .mapException(e -> {
                LOG.warn("Failed to load FHIR resource [id={}] from DataCloud; using empty fallback: {}", resourceId, e.getMessage());
                return e;
            });
    }

    private Promise<SearchResult> performSearch(String resourceType, Map<String, String> searchParams) {
        // Check DataCloud availability - read operation allowed in degraded mode
        if (!dataCloudAvailability.isAvailable()) {
            LOG.debug("DataCloud not available; returning cached search results for resourceType={}", resourceType);
            return Promise.of(searchCache(resourceType, searchParams));
        }

        return context.getDependency(DataCloudKernelAdapter.class)
            .queryData(new DataQueryRequest(
                FHIR_RESOURCE_DATASET,
                "resourceType = :resourceType",
                Map.of("resourceType", resourceType),
                parseSearchCount(searchParams),
                0
            ))
            .map(result -> toSearchResult(resourceType, searchParams, result))
            .mapException(e -> {
                LOG.warn("Search query failed for resourceType={}; using cache fallback: {}", resourceType, e.getMessage());
                return e;
            });
    }

    private FhirResource toFhirResource(DataResult result) {
        String json = new String(result.getData(), StandardCharsets.UTF_8);
        try {
            JsonNode root = JSON_MAPPER.readTree(json);
            return new FhirResource(
                result.getRecordId(),
                textValue(root.path("resourceType"), "Unknown"),
                json,
                Instant.ofEpochMilli(result.getTimestamp())
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to deserialize stored FHIR resource", exception);
        }
    }

    private SearchResult toSearchResult(String resourceType, Map<String, String> searchParams, QueryResult queryResult) {
        Set<FhirResource> matches = new LinkedHashSet<>();
        for (DataResult result : queryResult.getResults()) {
            FhirResource resource = toFhirResource(result);
            if (!resourceType.equals(resource.getResourceType())) {
                continue;
            }
            if (matchesSearchParams(resource.getJson(), searchParams)) {
                resourceCache.put(resource.getId(), resource);
                matches.add(resource);
            }
        }
        return new SearchResult(matches, matches.size());
    }

    private SearchResult searchCache(String resourceType, Map<String, String> searchParams) {
        Set<FhirResource> matches = new LinkedHashSet<>();
        for (FhirResource resource : resourceCache.values()) {
            if (!resourceType.equals(resource.getResourceType())) {
                continue;
            }
            if (matchesSearchParams(resource.getJson(), searchParams)) {
                matches.add(resource);
            }
        }
        return new SearchResult(matches, matches.size());
    }

    private Map<String, String> extractIndexedMetadata(FhirResource resource) {
        try {
            JsonNode root = JSON_MAPPER.readTree(resource.getJson());
            java.util.LinkedHashMap<String, String> metadata = new java.util.LinkedHashMap<>();
            metadata.put("resourceType", resource.getResourceType());
            metadata.put("lastUpdated", resource.getLastUpdated().toString());
            putIfPresent(metadata, "patient", extractPatientReference(root));
            putIfPresent(metadata, "subject", extractSubjectReference(root));
            putIfPresent(metadata, "identifier", extractIdentifier(root));
            putIfPresent(metadata, "code", extractCode(root));
            return metadata;
        } catch (Exception exception) {
            return Map.of(
                "resourceType", resource.getResourceType(),
                "lastUpdated", resource.getLastUpdated().toString()
            );
        }
    }

    private boolean matchesSearchParams(String resourceJson, Map<String, String> searchParams) {
        if (searchParams == null || searchParams.isEmpty()) {
            return true;
        }

        try {
            JsonNode root = JSON_MAPPER.readTree(resourceJson);
            for (Map.Entry<String, String> entry : searchParams.entrySet()) {
                String key = entry.getKey();
                String expected = entry.getValue();

                if (expected == null || expected.isBlank() || "_count".equals(key)) {
                    continue;
                }

                String actual = switch (key) {
                    case "id" -> textValue(root.path("id"), null);
                    case "patient" -> extractPatientReference(root);
                    case "subject" -> extractSubjectReference(root);
                    case "identifier" -> extractIdentifier(root);
                    case "code" -> extractCode(root);
                    default -> textValue(root.path(key), null);
                };

                if (actual == null || !expected.equals(actual)) {
                    return false;
                }
            }
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    private int parseSearchCount(Map<String, String> searchParams) {
        if (searchParams == null) {
            return 100;
        }
        try {
            return Integer.parseInt(searchParams.getOrDefault("_count", "100"));
        } catch (NumberFormatException exception) {
            return 100;
        }
    }

    private String extractPatientReference(JsonNode root) {
        if ("Patient".equals(root.path("resourceType").asText()) && root.has("id")) {
            return root.path("id").asText();
        }
        return extractReferenceId(textValue(root.path("patient").path("reference"), null));
    }

    private String extractSubjectReference(JsonNode root) {
        return extractReferenceId(textValue(root.path("subject").path("reference"), null));
    }

    private String extractIdentifier(JsonNode root) {
        JsonNode identifiers = root.path("identifier");
        if (identifiers.isArray() && !identifiers.isEmpty()) {
            return textValue(identifiers.get(0).path("value"), null);
        }
        return null;
    }

    private String extractCode(JsonNode root) {
        JsonNode coding = root.path("code").path("coding");
        if (coding.isArray() && !coding.isEmpty()) {
            return textValue(coding.get(0).path("code"), null);
        }
        return null;
    }

    private String extractReferenceId(String reference) {
        if (reference == null || reference.isBlank()) {
            return null;
        }
        int slashIndex = reference.lastIndexOf('/');
        return slashIndex >= 0 ? reference.substring(slashIndex + 1) : reference;
    }

    private void putIfPresent(Map<String, String> metadata, String key, String value) {
        if (value != null && !value.isBlank()) {
            metadata.put(key, value);
        }
    }

    private String textValue(JsonNode node, String defaultValue) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return defaultValue;
        }
        String value = node.asText();
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private String generateConsentId(String patientId, String purpose) {
        return patientId + ":" + purpose + ":" + Instant.now().toEpochMilli();
    }

    // ==================== Inner Types ====================

    public static class ValidationResult {
        private final boolean valid;
        private final String message;
        private final String resourceType;

        public ValidationResult(boolean valid, String message, String resourceType) {
            this.valid = valid;
            this.message = message;
            this.resourceType = resourceType;
        }

        public boolean isValid() { return valid; }
        public String getMessage() { return message; }
        public String getResourceType() { return resourceType; }
    }

    public static class FhirResource {
        private final String id;
        private final String resourceType;
        private final String json;
        private final Instant lastUpdated;

        public FhirResource(String id, String resourceType, String json, Instant lastUpdated) {
            this.id = id;
            this.resourceType = resourceType;
            this.json = json;
            this.lastUpdated = lastUpdated;
        }

        public String getId() { return id; }
        public String getResourceType() { return resourceType; }
        public String getJson() { return json; }
        public Instant getLastUpdated() { return lastUpdated; }
    }

    public static class SearchResult {
        private final Set<FhirResource> resources;
        private final int totalCount;

        public SearchResult(Set<FhirResource> resources, int totalCount) {
            this.resources = resources != null ? resources : Set.of();
            this.totalCount = totalCount;
        }

        public Set<FhirResource> getResources() { return resources; }
        public int getTotalCount() { return totalCount; }
    }
}
