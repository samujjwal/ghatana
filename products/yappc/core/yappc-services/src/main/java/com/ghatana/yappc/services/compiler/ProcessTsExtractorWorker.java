package com.ghatana.yappc.services.compiler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.domain.artifact.ArtifactEdgeDto;
import com.ghatana.yappc.domain.artifact.ArtifactNodeDto;
import com.ghatana.yappc.domain.source.RepositorySnapshot;
import io.activej.promise.Promise;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * @doc.type class
 * @doc.purpose Executes the TypeScript extractor worker process for snapshot extraction
 * @doc.layer service
 * @doc.pattern Adapter
 */
public final class ProcessTsExtractorWorker implements ArtifactCompileJobService.TsExtractorWorker {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private final ObjectMapper objectMapper;
    private final Executor blockingExecutor;
    private final String workerCommand;

    public ProcessTsExtractorWorker(ObjectMapper objectMapper, Executor blockingExecutor, String workerCommand) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.blockingExecutor = Objects.requireNonNull(blockingExecutor, "blockingExecutor must not be null");
        this.workerCommand = workerCommand == null ? "" : workerCommand.trim();
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

            String output = readAll(process.getInputStream());
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException("TypeScript extractor worker failed: " + output);
            }

            Map<String, Object> response = objectMapper.readValue(output, MAP_TYPE);
            List<ArtifactNodeDto> nodes = mapNodes(asListOfMaps(response.get("nodes")));
            List<ArtifactEdgeDto> edges = mapEdges(asListOfMaps(response.get("edges")));

            return new ArtifactCompileJobService.ExtractionResult(
                nodes,
                edges,
                asListOfMaps(response.get("unresolvedEdges")),
                asListOfMaps(response.get("edgeResolutionRecords")),
                asListOfStrings(response.get("residualIslandIds"))
            );
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to execute TypeScript extractor worker", e);
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
