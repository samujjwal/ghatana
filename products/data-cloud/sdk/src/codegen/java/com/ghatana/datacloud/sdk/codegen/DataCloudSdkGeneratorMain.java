package com.ghatana.datacloud.sdk.codegen;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @doc.type class
 * @doc.purpose Generates lightweight Data-Cloud SDKs from the canonical OpenAPI spec
 * @doc.layer product
 * @doc.pattern Code Generation
 */
public final class DataCloudSdkGeneratorMain {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

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

    static void generate(Path specPath, Path outputRoot) throws IOException {
        Objects.requireNonNull(specPath, "specPath");
        Objects.requireNonNull(outputRoot, "outputRoot");

        JsonNode root = YAML_MAPPER.readTree(specPath.toFile());
        OpenApiSummary summary = OpenApiSummary.from(root);

        write(outputRoot.resolve("metadata.json"), renderMetadata(summary));
        write(outputRoot.resolve("java/src/main/java/com/ghatana/datacloud/sdk/generated/DataCloudJavaSdk.java"),
            renderJavaSdk(summary));
        write(outputRoot.resolve("typescript/src/index.ts"), renderTypeScriptSdk(summary));
        write(outputRoot.resolve("typescript/tsconfig.json"), renderTypeScriptConfig());
        write(outputRoot.resolve("typescript/package.json"), renderTypeScriptPackage(summary));
        write(outputRoot.resolve("python/datacloud_sdk/__init__.py"), renderPythonInit(summary));
        write(outputRoot.resolve("python/datacloud_sdk/client.py"), renderPythonSdk(summary));
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
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
            javaString(summary.queryEntitiesPath().replace("{collection}", ""))
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
            typescriptString(summary.queryEntitiesPath().replace("{collection}", ""))
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
            pythonString(summary.queryEntitiesPath().replace("{collection}", ""))
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
        String deleteEntityPath
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
                entityItemPath
            );
        }

        private static String requirePath(List<String> paths, String expectedPath) {
            if (!paths.contains(expectedPath)) {
                throw new IllegalStateException("Expected OpenAPI path not found: " + expectedPath);
            }
            return expectedPath;
        }

        private static String text(JsonNode node, String defaultValue) {
            return node != null && node.isTextual() ? node.asText() : defaultValue;
        }
    }
}