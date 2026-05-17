package com.ghatana.yappc.services.compiler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.domain.artifact.ArtifactEdgeDto;
import com.ghatana.yappc.domain.artifact.ArtifactNodeDto;
import com.ghatana.yappc.domain.source.RepositorySnapshot;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @doc.type class
 * @doc.purpose Executes the TypeScript extractor worker process for snapshot extraction with timeout and schema validation
 * @doc.layer service
 * @doc.pattern Adapter
 * 
 * P1-17: Added process timeout to prevent hanging worker processes.
 * P1-17: Added schema validation for input/output to ensure data integrity.
 */
public final class ProcessTsExtractorWorker implements ArtifactCompileJobService.TsExtractorWorker {

    private static final Logger log = LoggerFactory.getLogger(ProcessTsExtractorWorker.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private static final long DEFAULT_TIMEOUT_SECONDS = 300; // 5 minutes

    private final ObjectMapper objectMapper;
    private final Executor blockingExecutor;
    private final String workerCommand;
    private final long timeoutSeconds;

    public ProcessTsExtractorWorker(ObjectMapper objectMapper, Executor blockingExecutor, String workerCommand) {
        this(objectMapper, blockingExecutor, workerCommand, DEFAULT_TIMEOUT_SECONDS);
    }

    public ProcessTsExtractorWorker(ObjectMapper objectMapper, Executor blockingExecutor, String workerCommand, long timeoutSeconds) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.blockingExecutor = Objects.requireNonNull(blockingExecutor, "blockingExecutor must not be null");
        this.workerCommand = workerCommand == null ? "" : workerCommand.trim();
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : DEFAULT_TIMEOUT_SECONDS;
    }

    @Override
    public Promise<ArtifactCompileJobService.ExtractionResult> extract(RepositorySnapshot snapshot) {
        return Promise.ofBlocking(blockingExecutor, () -> runExtractor(snapshot));
    }

    private ArtifactCompileJobService.ExtractionResult runExtractor(RepositorySnapshot snapshot) {
        if (workerCommand.isBlank()) {
            throw new IllegalStateException("TypeScript extractor worker command is not configured. Set YAPPC_TS_EXTRACTOR_WORKER_CMD.");
        }

        try {
            Process process = new ProcessBuilder("sh", "-lc", workerCommand)
                .redirectErrorStream(true)
                .start();

            Map<String, Object> payload = Map.of(
                "snapshot", Map.of(
                    "snapshotRef", Map.of(
                        "provider", snapshot.provider(),
                        "repoId", snapshot.repoId(),
                        "commitSha", snapshot.commitSha().orElse(null),
                        "ref", null,
                        "resolvedPath", snapshot.materializedRoot(),
                        "snapshotId", snapshot.snapshotId(),
                        "checksum", snapshot.checksum(),
                        "capturedAt", snapshot.createdAt().toString()
                    ),
                    "localRootPath", snapshot.materializedRoot(),
                    "files", snapshot.files().stream().map(file -> Map.of(
                        "relativePath", file.relativePath(),
                        "absolutePath", file.absolutePath(),
                        "sizeBytes", file.sizeBytes(),
                        "lastModified", file.lastModified().toString()
                    )).toList(),
                    "diagnostics", snapshot.diagnostics()
                )
            );

            String jsonPayload = objectMapper.writeValueAsString(payload);
            process.getOutputStream().write(jsonPayload.getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().flush();
            process.getOutputStream().close();

            // P1-17: Add timeout to prevent hanging worker processes
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                log.warn("TypeScript extractor worker timed out after {} seconds, destroying process", timeoutSeconds);
                process.destroyForcibly();
                throw new TimeoutException("TypeScript extractor worker timed out after " + timeoutSeconds + " seconds");
            }

            String output = readAll(process.getInputStream());
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                log.error("TypeScript extractor worker failed with exit code {}: {}", exitCode, output);
                throw new IllegalStateException("TypeScript extractor worker failed with exit code " + exitCode + ": " + output);
            }

            // P1-17: Validate output schema before parsing
            JsonNode responseNode = objectMapper.readTree(output);
            validateExtractionResponseSchema(responseNode);

            Map<String, Object> response = objectMapper.convertValue(responseNode, MAP_TYPE);
            List<ArtifactNodeDto> nodes = mapNodes(asListOfMaps(response.get("nodes")));
            List<ArtifactEdgeDto> edges = mapEdges(asListOfMaps(response.get("edges")));

            return new ArtifactCompileJobService.ExtractionResult(
                nodes,
                edges,
                asListOfMaps(response.get("unresolvedEdges")),
                asListOfMaps(response.get("edgeResolutionRecords")),
                asListOfStrings(response.get("residualIslandIds"))
            );
        } catch (IllegalArgumentException e) {
            log.error("TypeScript extractor worker output schema validation failed", e);
            throw new IllegalStateException("TypeScript extractor worker output schema validation failed: " + e.getMessage(), e);
        } catch (TimeoutException e) {
            throw new IllegalStateException("TypeScript extractor worker timed out", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to execute TypeScript extractor worker", e);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to execute TypeScript extractor worker", e);
        }
    }

    /**
     * P1-17: Validate the extraction response schema to ensure data integrity.
     */
    private void validateExtractionResponseSchema(JsonNode response) {
        if (!response.has("nodes")) {
            throw new IllegalArgumentException("Missing required field 'nodes' in extraction response");
        }
        if (!response.has("edges")) {
            throw new IllegalArgumentException("Missing required field 'edges' in extraction response");
        }
        
        JsonNode nodes = response.get("nodes");
        if (!nodes.isArray()) {
            throw new IllegalArgumentException("'nodes' must be an array in extraction response");
        }
        
        JsonNode edges = response.get("edges");
        if (!edges.isArray()) {
            throw new IllegalArgumentException("'edges' must be an array in extraction response");
        }
        
        // Validate node structure
        for (JsonNode node : nodes) {
            if (!node.has("id")) {
                throw new IllegalArgumentException("Node missing required field 'id'");
            }
            if (!node.has("type") && !node.has("kind")) {
                throw new IllegalArgumentException("Node missing required field 'type' or 'kind'");
            }
        }
        
        // Validate edge structure
        for (JsonNode edge : edges) {
            if (!edge.has("sourceNodeId") && !edge.has("source")) {
                throw new IllegalArgumentException("Edge missing required field 'sourceNodeId' or 'source'");
            }
            if (!edge.has("targetNodeId") && !edge.has("target")) {
                throw new IllegalArgumentException("Edge missing required field 'targetNodeId' or 'target'");
            }
        }
    }

    private static List<Map<String, Object>> asListOfMaps(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> raw) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typed = (Map<String, Object>) raw;
                result.add(typed);
            }
        }
        return result;
    }

    private static List<String> asListOfStrings(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            if (item != null) {
                result.add(String.valueOf(item));
            }
        }
        return result;
    }

    private static List<ArtifactNodeDto> mapNodes(List<Map<String, Object>> rawNodes) {
        List<ArtifactNodeDto> nodes = new ArrayList<>();
        for (Map<String, Object> node : rawNodes) {
            @SuppressWarnings("unchecked")
            Map<String, Object> sourceLocation = node.get("sourceLocation") instanceof Map<?, ?> location
                ? (Map<String, Object>) location
                : Map.of();

            nodes.add(new ArtifactNodeDto(
                asString(node.get("id")),
                firstNonBlank(asString(node.get("type")), asString(node.get("kind"))),
                asString(node.get("name")),
                firstNonBlank(asString(node.get("filePath")), asString(sourceLocation.get("filePath"))),
                asString(node.get("content")),
                mapOrEmpty(node.get("properties")),
                asListOfStrings(node.get("tags")),
                asString(node.get("tenantId")),
                asString(node.get("projectId")),
                sourceLocation,
                asString(node.get("extractorId")),
                asString(node.get("extractorVersion")),
                asDouble(node.get("confidence")),
                asString(node.get("provenance")),
                asListOfStrings(node.get("privacySecurityFlags")),
                asListOfStrings(node.get("residualFragmentIds")),
                asString(node.get("sourceRef")),
                asString(node.get("symbolRef"))
            ));
        }
        return nodes;
    }

    private static List<ArtifactEdgeDto> mapEdges(List<Map<String, Object>> rawEdges) {
        List<ArtifactEdgeDto> edges = new ArrayList<>();
        for (Map<String, Object> edge : rawEdges) {
            edges.add(new ArtifactEdgeDto(
                firstNonBlank(asString(edge.get("edgeId")), asString(edge.get("id"))),
                firstNonBlank(asString(edge.get("sourceNodeId")), asString(edge.get("source"))),
                firstNonBlank(asString(edge.get("targetNodeId")), asString(edge.get("target"))),
                firstNonBlank(asString(edge.get("relationshipType")), asString(edge.get("type"))),
                mapOrEmpty(edge.get("properties")),
                asDouble(edge.get("confidence")),
                asBoolean(edge.get("bidirectional")),
                mapOrEmpty(edge.get("metadata")),
                asString(edge.get("snapshotId")),
                asString(edge.get("versionId"))
            ));
        }
        return edges;
    }

    private static Map<String, Object> mapOrEmpty(Object value) {
        if (value instanceof Map<?, ?> raw) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) raw;
            return typed;
        }
        return Map.of();
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Double.parseDouble(text);
        }
        return null;
    }

    private static Boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text && !text.isBlank()) {
            return Boolean.parseBoolean(text);
        }
        return null;
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    private static String readAll(InputStream inputStream) throws IOException {
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }
}
