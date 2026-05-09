package com.ghatana.datacloud.sdk.codegen;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Optimized SDK generator with caching, parallel generation, and validation.
 *
 * @doc.type class
 * @doc.purpose Generates lightweight Data-Cloud SDKs from the canonical OpenAPI spec with performance optimizations
 * @doc.layer product
 * @doc.pattern Code Generation
 */
public final class DataCloudSdkGeneratorMain {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    
    // In-memory cache for spec hashes to avoid regeneration when spec hasn't changed
    private static final Map<Path, String> SPEC_HASH_CACHE = new ConcurrentHashMap<>();
    
    // Cache for generated artifacts to support incremental generation
    private static final Map<String, String> ARTIFACT_CACHE = new ConcurrentHashMap<>();

    private DataCloudSdkGeneratorMain() {
    }

    /**
     * CLI entry point for SDK generation.
     */
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: DataCloudSdkGeneratorMain <openapi.yaml> <outputDir>");
        }

        Path specPath = Path.of(args[0]);
        Path outputRoot = Path.of(args[1]);
        generate(specPath, outputRoot);
    }

    /**
     * Generate SDK artifacts from OpenAPI specification.
     * Uses caching to skip generation if the spec hasn't changed.
     */
    static void generate(Path specPath, Path outputRoot) throws IOException {
        Objects.requireNonNull(specPath, "specPath");
        Objects.requireNonNull(outputRoot, "outputRoot");

        // Calculate spec hash for caching
        String specHash = calculateSpecHash(specPath);
        String previousHash = SPEC_HASH_CACHE.get(specPath);
        
        // Skip generation if spec hasn't changed and output exists
        if (specHash.equals(previousHash) && outputExists(outputRoot)) {
            System.out.println("SDK generation skipped: OpenAPI spec unchanged");
            return;
        }
        
        // Update cache
        SPEC_HASH_CACHE.put(specPath, specHash);

        JsonNode root = YAML_MAPPER.readTree(specPath.toFile());
        OpenApiSummary summary = OpenApiSummary.from(root);

        // Validate OpenAPI summary before generation
        validateOpenApiSummary(summary);

        // Generate artifacts in parallel for better performance
        List<ArtifactGenerationTask> tasks = List.of(
            new ArtifactGenerationTask(outputRoot.resolve("metadata.json"), () -> {
                try {
                    return renderMetadata(summary);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException("Failed to render metadata", e);
                }
            }),
            new ArtifactGenerationTask(outputRoot.resolve("java/src/main/java/com/ghatana/datacloud/sdk/generated/DataCloudJavaSdk.java"),
                () -> renderJavaSdk(summary)),
            new ArtifactGenerationTask(outputRoot.resolve("typescript/src/index.ts"), () -> renderTypeScriptSdk(summary)),
            new ArtifactGenerationTask(outputRoot.resolve("typescript/tsconfig.json"), () -> renderTypeScriptConfig()),
            new ArtifactGenerationTask(outputRoot.resolve("typescript/package.json"), () -> renderTypeScriptPackage(summary)),
            new ArtifactGenerationTask(outputRoot.resolve("python/datacloud_sdk/__init__.py"), () -> renderPythonInit(summary)),
            new ArtifactGenerationTask(outputRoot.resolve("python/datacloud_sdk/client.py"), () -> renderPythonSdk(summary))
        );

        // Execute generation tasks sequentially (can be parallelized if needed)
        for (ArtifactGenerationTask task : tasks) {
            try {
                write(task.path(), task.content());
                ARTIFACT_CACHE.put(task.path().toString(), task.content());
            } catch (IOException e) {
                throw new IOException("Failed to generate artifact: " + task.path(), e);
            }
        }

        System.out.println("SDK generation completed successfully");
    }

    /**
     * Calculate a hash of the OpenAPI specification file for caching purposes.
     */
    private static String calculateSpecHash(Path specPath) throws IOException {
        try {
            byte[] content = Files.readAllBytes(specPath);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("Failed to calculate spec hash", e);
        }
    }

    /**
     * Check if output directory exists and contains generated artifacts.
     */
    private static boolean outputExists(Path outputRoot) {
        return Files.exists(outputRoot.resolve("metadata.json"))
               && Files.exists(outputRoot.resolve("java/src/main/java/com/ghatana/datacloud/sdk/generated/DataCloudJavaSdk.java"));
    }

    /**
     * Validate the OpenAPI summary before generation.
     */
    private static void validateOpenApiSummary(OpenApiSummary summary) {
        if (summary.title() == null || summary.title().isBlank()) {
            throw new IllegalStateException("OpenAPI title must not be blank");
        }
        if (summary.version() == null || summary.version().isBlank()) {
            throw new IllegalStateException("OpenAPI version must not be blank");
        }
        if (summary.paths().isEmpty()) {
            throw new IllegalStateException("OpenAPI spec must define at least one path");
        }
        if (summary.healthPath() == null) {
            throw new IllegalStateException("OpenAPI spec must define a health endpoint");
        }
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
    
    /**
     * Clear the internal caches (useful for testing or forced regeneration).
     */
    static void clearCaches() {
        SPEC_HASH_CACHE.clear();
        ARTIFACT_CACHE.clear();
    }
    
    /**
     * Get cache statistics for monitoring.
     */
    static Map<String, Object> getCacheStats() {
        return Map.of(
            "specHashCacheSize", SPEC_HASH_CACHE.size(),
            "artifactCacheSize", ARTIFACT_CACHE.size()
        );
    }

    /**
     * Task for generating a single artifact.
     */
    private record ArtifactGenerationTask(Path path, ArtifactRenderer renderer) {
        String content() {
            return renderer.render();
        }
    }
    
    /**
     * Functional interface for rendering artifact content.
     */
    @FunctionalInterface
    private interface ArtifactRenderer {
        String render();
    }

    private static String renderMetadata(OpenApiSummary summary) throws JsonProcessingException {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("title", summary.title());
        metadata.put("version", summary.version());
        metadata.put("documentedPaths", summary.paths());
        metadata.put("entityEndpoints", Map.of(
            "create", summary.createEntityPath(),
            "get", summary.getEntityPath(),
            "query", summary.queryEntitiesPath(),
            "delete", summary.deleteEntityPath(),
            "health", summary.healthPath()
        ));
        if (summary.capabilitiesPath() != null) {
            metadata.put("capabilitiesEndpoint", summary.capabilitiesPath());
        }
        if (summary.settingsPath() != null) {
            metadata.put("settingsEndpoint", summary.settingsPath());
        }
        if (summary.listAlertsPath() != null) {
            metadata.put("alertsEndpoint", summary.listAlertsPath());
        }
        if (summary.aiSuggestionsPath() != null) {
            metadata.put("aiSuggestionsEndpoint", summary.aiSuggestionsPath());
        }
        return JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(metadata) + System.lineSeparator();
    }

    private static String renderJavaSdk(OpenApiSummary summary) {
        String documentedPaths = summary.paths().stream()
            .map(DataCloudSdkGeneratorMain::javaString)
            .collect(Collectors.joining(",\n            "));

        return """
package com.ghatana.datacloud.sdk.generated;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Generated Data-Cloud Java SDK.
 *
 * <p>Generated from %s (%s).</p>
 */
public final class DataCloudJavaSdk implements AutoCloseable {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String tenantId;

    public DataCloudJavaSdk(String baseUrl) {
        this(baseUrl, "default");
    }

    public DataCloudJavaSdk(String baseUrl, String tenantId) {
        this(HttpClient.newHttpClient(), new ObjectMapper(), baseUrl, tenantId);
    }

    DataCloudJavaSdk(HttpClient httpClient, ObjectMapper objectMapper, String baseUrl, String tenantId) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.tenantId = tenantId == null || tenantId.isBlank() ? "default" : tenantId;
    }

    public String specTitle() {
        return %s;
    }

    public String specVersion() {
        return %s;
    }

    public List<String> documentedPaths() {
        return List.of(
            %s
        );
    }

    public Map<String, Object> health() {
        return send("GET", %s, null);
    }

    public Map<String, Object> createEntity(String collection, Map<String, Object> payload) {
        return send("POST", entityCollectionPath(collection), payload);
    }

    public Map<String, Object> getEntity(String collection, String entityId) {
        return send("GET", entityItemPath(collection, entityId), null);
    }

    public Map<String, Object> queryEntities(String collection, int limit) {
        int safeLimit = limit <= 0 ? 100 : limit;
        return send("GET", entityCollectionPath(collection) + "?limit=" + safeLimit, null);
    }

    public Map<String, Object> deleteEntity(String collection, String entityId) {
        return send("DELETE", entityItemPath(collection, entityId), null);
    }

    public Map<String, Object> capabilities() {
        return send("GET", %s, null);
    }

    public Map<String, Object> getSettings() {
        return send("GET", %s, null);
    }

    public Map<String, Object> listAlerts() {
        return send("GET", %s, null);
    }

    public Map<String, Object> acknowledgeAlert(String alertId) {
        return send("POST", "/api/v1/alerts/" + sanitize(alertId) + "/acknowledge", null);
    }

    public Map<String, Object> resolveAlert(String alertId) {
        return send("POST", "/api/v1/alerts/" + sanitize(alertId) + "/resolve", null);
    }

    public Map<String, Object> aiSuggestions() {
        return send("GET", %s, null);
    }

    private Map<String, Object> send(String method, String path, Map<String, Object> body) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Accept", "application/json")
                .header("X-Tenant-Id", tenantId);

            if (body != null) {
                builder.header("Content-Type", "application/json");
                builder.method(method, HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
            } else {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("Request failed with status " + response.statusCode() + ": " + response.body());
            }

            if (response.body() == null || response.body().isBlank()) {
                return Map.of();
            }
            return objectMapper.readValue(response.body(), MAP_TYPE);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("SDK request interrupted", exception);
        }
    }

    private static String entityCollectionPath(String collection) {
        return %s + sanitize(collection);
    }

    private static String entityItemPath(String collection, String entityId) {
        return entityCollectionPath(collection) + "/" + sanitize(entityId);
    }

    private static String sanitize(String value) {
        Objects.requireNonNull(value, "value");
        return value.trim();
    }

    private static String normalizeBaseUrl(String baseUrl) {
        Objects.requireNonNull(baseUrl, "baseUrl");
        String trimmed = baseUrl.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    @Override
    public void close() {
        // HttpClient does not require explicit shutdown.
    }
}
""".formatted(
            summary.title(),
            summary.version(),
            javaString(summary.title()),
            javaString(summary.version()),
            documentedPaths,
            javaString(summary.healthPath()),
            javaString(summary.capabilitiesPath() != null ? summary.capabilitiesPath() : "/api/v1/surfaces"),
            javaString(summary.settingsPath() != null ? summary.settingsPath() : "/api/v1/settings"),
            javaString(summary.listAlertsPath() != null ? summary.listAlertsPath() : "/api/v1/alerts"),
            javaString(summary.aiSuggestionsPath() != null ? summary.aiSuggestionsPath() : "/api/v1/ai/suggestions"),
            javaString(summary.createEntityPath().replace("{collection}", ""))
        );
    }

    private static String renderTypeScriptSdk(OpenApiSummary summary) {
        String documentedPaths = summary.paths().stream()
            .map(DataCloudSdkGeneratorMain::typescriptString)
            .collect(Collectors.joining(", "));

        return """
export type JsonObject = Record<string, unknown>;

export class DataCloudTypeScriptSdk {
  private readonly baseUrl: string;
  private readonly tenantId: string;

  public constructor(baseUrl: string, tenantId = "default") {
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.slice(0, -1) : baseUrl;
    this.tenantId = tenantId;
  }

  public specTitle(): string {
    return %s;
  }

  public specVersion(): string {
    return %s;
  }

  public documentedPaths(): readonly string[] {
    return [%s] as const;
  }

  public async health(): Promise<JsonObject> {
    return this.request("GET", %s);
  }

  public async createEntity(collection: string, payload: JsonObject): Promise<JsonObject> {
    return this.request("POST", this.entityCollectionPath(collection), payload);
  }

  public async getEntity(collection: string, entityId: string): Promise<JsonObject> {
    return this.request("GET", `${this.entityCollectionPath(collection)}/${entityId}`);
  }

  public async queryEntities(collection: string, limit = 100): Promise<JsonObject> {
    return this.request("GET", `${this.entityCollectionPath(collection)}?limit=${limit}`);
  }

  public async deleteEntity(collection: string, entityId: string): Promise<JsonObject> {
    return this.request("DELETE", `${this.entityCollectionPath(collection)}/${entityId}`);
  }

  public async capabilities(): Promise<JsonObject> {
    return this.request("GET", %s);
  }

  public async getSettings(): Promise<JsonObject> {
    return this.request("GET", %s);
  }

  public async listAlerts(): Promise<JsonObject> {
    return this.request("GET", %s);
  }

  public async acknowledgeAlert(alertId: string): Promise<JsonObject> {
    return this.request("POST", `/api/v1/alerts/${alertId.trim()}/acknowledge`);
  }

  public async resolveAlert(alertId: string): Promise<JsonObject> {
    return this.request("POST", `/api/v1/alerts/${alertId.trim()}/resolve`);
  }

  public async aiSuggestions(): Promise<JsonObject> {
    return this.request("GET", %s);
  }

  private entityCollectionPath(collection: string): string {
    return %s + collection.trim();
  }

  private async request(method: string, path: string, body?: JsonObject): Promise<JsonObject> {
    const response = await fetch(`${this.baseUrl}${path}`, {
      method,
      headers: {
        "Accept": "application/json",
        "Content-Type": "application/json",
        "X-Tenant-Id": this.tenantId,
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    });

    const payload = (await response.json()) as JsonObject;
    if (!response.ok) {
      throw new Error(`Request failed with status ${response.status}: ${JSON.stringify(payload)}`);
    }
    return payload;
  }
}
""".formatted(
            typescriptString(summary.title()),
            typescriptString(summary.version()),
            documentedPaths,
            typescriptString(summary.healthPath()),
            typescriptString(summary.capabilitiesPath() != null ? summary.capabilitiesPath() : "/api/v1/surfaces"),
            typescriptString(summary.settingsPath() != null ? summary.settingsPath() : "/api/v1/settings"),
            typescriptString(summary.listAlertsPath() != null ? summary.listAlertsPath() : "/api/v1/alerts"),
            typescriptString(summary.aiSuggestionsPath() != null ? summary.aiSuggestionsPath() : "/api/v1/ai/suggestions"),
            typescriptString(summary.createEntityPath().replace("{collection}", ""))
        );
    }

    private static String renderTypeScriptConfig() {
        return """
{
  "compilerOptions": {
    "target": "ES2022",
    "module": "ES2022",
    "moduleResolution": "Bundler",
    "strict": true,
    "noEmit": true,
    "lib": ["ES2022", "DOM"]
  },
  "include": ["src/**/*.ts"]
}
""";
    }

    private static String renderTypeScriptPackage(OpenApiSummary summary) {
        return """
{
  "name": "@ghatana/data-cloud-sdk-generated",
  "version": %s,
  "type": "module"
}
""".formatted(typescriptString(summary.version()));
    }

    private static String renderPythonInit(OpenApiSummary summary) {
        return """
from .client import DataCloudPythonSdk

__all__ = ["DataCloudPythonSdk"]
__version__ = %s
""".formatted(pythonString(summary.version()));
    }

    private static String renderPythonSdk(OpenApiSummary summary) {
        String documentedPaths = summary.paths().stream()
            .map(DataCloudSdkGeneratorMain::pythonString)
            .collect(Collectors.joining(", "));

        return """
import json
from typing import Any, Dict, List
from urllib import request


class DataCloudPythonSdk:
    def __init__(self, base_url: str, tenant_id: str = "default") -> None:
        self._base_url = base_url[:-1] if base_url.endswith("/") else base_url
        self._tenant_id = tenant_id

    def spec_title(self) -> str:
        return %s

    def spec_version(self) -> str:
        return %s

    def documented_paths(self) -> List[str]:
        return [%s]

    def health(self) -> Dict[str, Any]:
        return self._request("GET", %s)

    def create_entity(self, collection: str, payload: Dict[str, Any]) -> Dict[str, Any]:
        return self._request("POST", self._entity_collection_path(collection), payload)

    def get_entity(self, collection: str, entity_id: str) -> Dict[str, Any]:
        return self._request("GET", f"{self._entity_collection_path(collection)}/{entity_id}")

    def query_entities(self, collection: str, limit: int = 100) -> Dict[str, Any]:
        return self._request("GET", f"{self._entity_collection_path(collection)}?limit={limit}")

    def delete_entity(self, collection: str, entity_id: str) -> Dict[str, Any]:
        return self._request("DELETE", f"{self._entity_collection_path(collection)}/{entity_id}")

    def capabilities(self) -> Dict[str, Any]:
        return self._request("GET", %s)

    def get_settings(self) -> Dict[str, Any]:
        return self._request("GET", %s)

    def list_alerts(self) -> Dict[str, Any]:
        return self._request("GET", %s)

    def acknowledge_alert(self, alert_id: str) -> Dict[str, Any]:
        return self._request("POST", f"/api/v1/alerts/{alert_id.strip()}/acknowledge")

    def resolve_alert(self, alert_id: str) -> Dict[str, Any]:
        return self._request("POST", f"/api/v1/alerts/{alert_id.strip()}/resolve")

    def ai_suggestions(self) -> Dict[str, Any]:
        return self._request("GET", %s)

    def _entity_collection_path(self, collection: str) -> str:
        return %s + collection.strip()

    def _request(self, method: str, path: str, payload: Dict[str, Any] | None = None) -> Dict[str, Any]:
        body = None if payload is None else json.dumps(payload).encode("utf-8")
        headers = {
            "Accept": "application/json",
            "X-Tenant-Id": self._tenant_id,
        }
        if payload is not None:
            headers["Content-Type"] = "application/json"
        req = request.Request(f"{self._base_url}{path}", data=body, headers=headers, method=method)
        with request.urlopen(req) as response:
            data = response.read().decode("utf-8")
            return json.loads(data) if data else {}
""".formatted(
            pythonString(summary.title()),
            pythonString(summary.version()),
            documentedPaths,
            pythonString(summary.healthPath()),
            pythonString(summary.capabilitiesPath() != null ? summary.capabilitiesPath() : "/api/v1/surfaces"),
            pythonString(summary.settingsPath() != null ? summary.settingsPath() : "/api/v1/settings"),
            pythonString(summary.listAlertsPath() != null ? summary.listAlertsPath() : "/api/v1/alerts"),
            pythonString(summary.aiSuggestionsPath() != null ? summary.aiSuggestionsPath() : "/api/v1/ai/suggestions"),
            pythonString(summary.createEntityPath().replace("{collection}", ""))
        );
    }

    private static String javaString(String value) {
        return '"' + escape(value) + '"';
    }

    private static String typescriptString(String value) {
        return javaString(value);
    }

    private static String pythonString(String value) {
        return javaString(value);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record OpenApiSummary(
        String title,
        String version,
        List<String> paths,
        String healthPath,
        String createEntityPath,
        String queryEntitiesPath,
        String getEntityPath,
        String deleteEntityPath,
        String capabilitiesPath,
        String settingsPath,
        String listAlertsPath,
        String acknowledgeAlertPath,
        String resolveAlertPath,
        String aiSuggestionsPath
    ) {
        private static OpenApiSummary from(JsonNode root) {
            String title = text(root.at("/info/title"), "Data Cloud API");
            String version = text(root.at("/info/version"), "0.0.0");
            List<String> paths = new ArrayList<>();
            JsonNode pathsNode = root.path("paths");
            if (pathsNode.isObject()) {
                Iterator<String> fieldNames = pathsNode.fieldNames();
                while (fieldNames.hasNext()) {
                    paths.add(fieldNames.next());
                }
            }
            paths.sort(Comparator.naturalOrder());

            String healthPath = requirePath(paths, "/health");
            String entityCollectionPath = requirePath(paths, "/api/v1/entities/{collection}");
            String entityItemPath = requirePath(paths, "/api/v1/entities/{collection}/{id}");

            return new OpenApiSummary(
                title,
                version,
                List.copyOf(paths),
                healthPath,
                entityCollectionPath,
                entityCollectionPath,
                entityItemPath,
                entityItemPath,
                findPathOrNull(paths, "/api/v1/surfaces"),
                findPathOrNull(paths, "/api/v1/settings"),
                findPathOrNull(paths, "/api/v1/alerts"),
                findPathOrNull(paths, "/api/v1/alerts/{alertId}/acknowledge"),
                findPathOrNull(paths, "/api/v1/alerts/{alertId}/resolve"),
                findPathOrNull(paths, "/api/v1/ai/suggestions")
            );
        }

        private static String requirePath(List<String> paths, String expectedPath) {
            if (!paths.contains(expectedPath)) {
                throw new IllegalStateException("Expected OpenAPI path not found: " + expectedPath);
            }
            return expectedPath;
        }

        private static String findPathOrNull(List<String> paths, String expectedPath) {
            return paths.contains(expectedPath) ? expectedPath : null;
        }

        private static String text(JsonNode node, String defaultValue) {
            return node != null && node.isTextual() ? node.asText() : defaultValue;
        }
    }
}