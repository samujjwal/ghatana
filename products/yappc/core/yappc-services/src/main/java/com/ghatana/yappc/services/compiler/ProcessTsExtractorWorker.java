package com.ghatana.yappc.services.compiler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.domain.artifact.ArtifactEdgeDto;
import com.ghatana.yappc.domain.artifact.ArtifactNodeDto;
import com.ghatana.yappc.domain.artifact.EdgeResolutionRecordDto;
import com.ghatana.yappc.domain.artifact.ResidualIslandDto;
import com.ghatana.yappc.domain.artifact.SemanticModelDto;
import com.ghatana.yappc.domain.artifact.UnresolvedGraphEdgeDto;
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
    public Promise<ArtifactCompileJobService.ExtractionResult> extract(
        RepositorySnapshot snapshot,
        List<RepositorySnapshot.SnapshotFile> tsFiles
    ) {
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
            List<UnresolvedGraphEdgeDto> unresolvedEdges = mapUnresolvedEdges(asListOfMaps(response.get("unresolvedEdges")));
            List<EdgeResolutionRecordDto> edgeResolutionRecords = mapEdgeResolutionRecords(asListOfMaps(response.get("edgeResolutionRecords")));
            List<ResidualIslandDto> residualIslands = mapResidualIslands(
                response.get("residualIslands"),
                response.get("residualIslandIds"),
                snapshot
            );
            List<SemanticModelDto> semanticModels = mapSemanticModels(asListOfMaps(response.get("semanticModels")), snapshot);

            return new ArtifactCompileJobService.ExtractionResult(
                nodes,
                edges,
                unresolvedEdges,
                edgeResolutionRecords,
                residualIslands,
                semanticModels
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
     * Validates the extraction response schema strictly against the canonical contract.
     * P0: Rejects any response that uses legacy/aliased field names, is missing mandatory fields,
     * carries edge targets that do not exist in the declared node set, or uses legacy residualIslandIds.
     */
    private void validateExtractionResponseSchema(JsonNode response) {
        if (!response.has("nodes") || !response.get("nodes").isArray()) {
            throw new IllegalArgumentException("Missing required array field 'nodes' in extraction response");
        }
        if (!response.has("edges") || !response.get("edges").isArray()) {
            throw new IllegalArgumentException("Missing required array field 'edges' in extraction response");
        }

        JsonNode nodes = response.get("nodes");
        JsonNode edges = response.get("edges");

        // P0: Reject legacy residualIslandIds - only residualIslands with full payload is accepted
        if (response.has("residualIslandIds") && response.get("residualIslandIds").size() > 0) {
            throw new IllegalArgumentException(
                "Legacy field 'residualIslandIds' is deprecated and not accepted. " +
                "Use 'residualIslands' with full payload (originalSource, sourceLocation, checksum, rawFragmentRef).");
        }

        // Build declared node-ID set for edge target validation
        java.util.Set<String> nodeIds = new java.util.HashSet<>();
        for (JsonNode node : nodes) {
            if (!node.has("id") || node.get("id").asText("").isBlank()) {
                throw new IllegalArgumentException("Node missing required field 'id'");
            }
            // P0: Require canonical field name 'type', reject legacy 'kind'
            if (!node.has("type") || node.get("type").asText("").isBlank()) {
                throw new IllegalArgumentException(
                    "Node missing required field 'type'. Legacy 'kind' alias is not accepted.");
            }
            nodeIds.add(node.get("id").asText());
        }

        for (JsonNode edge : edges) {
            // Strict: require canonical field names, not legacy aliases
            if (!edge.has("sourceNodeId") || edge.get("sourceNodeId").asText("").isBlank()) {
                throw new IllegalArgumentException(
                    "Edge missing required field 'sourceNodeId'. Legacy 'source' alias is not accepted.");
            }
            if (!edge.has("targetNodeId") || edge.get("targetNodeId").asText("").isBlank()) {
                throw new IllegalArgumentException(
                    "Edge missing required field 'targetNodeId'. Legacy 'target' alias is not accepted.");
            }
            // P0: Require relationshipType, reject legacy relationship/type/kind
            if (!edge.has("relationshipType") || edge.get("relationshipType").asText("").isBlank()) {
                throw new IllegalArgumentException(
                    "Edge missing required field 'relationshipType'. Legacy 'relationship'/'type'/'kind' aliases are not accepted.");
            }
            // Reject fake/external edge targets not declared in the node set
            String sourceNodeId = edge.get("sourceNodeId").asText();
            String targetNodeId = edge.get("targetNodeId").asText();
            if (!nodeIds.contains(sourceNodeId)) {
                throw new IllegalArgumentException(
                    "Edge sourceNodeId '" + sourceNodeId + "' not found in declared nodes. " +
                    "Unresolved references belong in 'unresolvedEdges', not 'edges'.");
            }
            if (!nodeIds.contains(targetNodeId)) {
                throw new IllegalArgumentException(
                    "Edge targetNodeId '" + targetNodeId + "' not found in declared nodes. " +
                    "Unresolved references belong in 'unresolvedEdges', not 'edges'.");
            }
        }

        // P0: Validate unresolved edges use relationshipType
        if (response.has("unresolvedEdges")) {
            JsonNode unresolvedEdges = response.get("unresolvedEdges");
            if (!unresolvedEdges.isArray()) {
                throw new IllegalArgumentException("'unresolvedEdges' must be an array");
            }
            for (JsonNode edge : unresolvedEdges) {
                // P0: Require relationshipType in unresolved edges too
                if (!edge.has("relationshipType") || edge.get("relationshipType").asText("").isBlank()) {
                    throw new IllegalArgumentException(
                        "UnresolvedEdge missing required field 'relationshipType'. Legacy 'relationship' alias is not accepted.");
                }
            }
        }

        // P0: Require residualIslands with full payload
        if (!response.has("residualIslands") || !response.get("residualIslands").isArray()) {
            throw new IllegalArgumentException("Missing required array field 'residualIslands' in extraction response");
        }

        JsonNode residuals = response.get("residualIslands");
        for (JsonNode island : residuals) {
            if (!island.has("id") || island.get("id").asText("").isBlank()) {
                throw new IllegalArgumentException("ResidualIsland missing required field 'id'");
            }
            if (!island.has("islandType") || island.get("islandType").asText("").isBlank()) {
                throw new IllegalArgumentException("ResidualIsland missing required field 'islandType'");
            }
            // P0: Require originalSource for round-trip fidelity
            if (!island.has("originalSource") || island.get("originalSource").asText("").isBlank()) {
                throw new IllegalArgumentException(
                    "ResidualIsland '" + island.get("id").asText() + "' missing required field 'originalSource'");
            }
            // P0: Require sourceLocation for precise positioning
            if (!island.has("sourceLocation")) {
                throw new IllegalArgumentException(
                    "ResidualIsland '" + island.get("id").asText() + "' missing required field 'sourceLocation'");
            }
            JsonNode sourceLocation = island.get("sourceLocation");
            if (!sourceLocation.has("filePath") || sourceLocation.get("filePath").asText("").isBlank()) {
                throw new IllegalArgumentException(
                    "ResidualIsland '" + island.get("id").asText() + "' sourceLocation missing required field 'filePath'");
            }
            if (!island.has("checksum") || island.get("checksum").asText("").isBlank()) {
                throw new IllegalArgumentException(
                    "ResidualIsland '" + island.get("id").asText() + "' missing required field 'checksum'");
            }
            if (!island.has("rawFragmentRef") || island.get("rawFragmentRef").asText("").isBlank()) {
                throw new IllegalArgumentException(
                    "ResidualIsland '" + island.get("id").asText() + "' missing required field 'rawFragmentRef'");
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

    /**
     * P0: Maps residual islands from the TS worker response.
     * Strictly requires full payload - no fallback from legacy residualIslandIds.
     * Extracts originalSource and sourceLocation for round-trip fidelity.
     */
    private static List<ResidualIslandDto> mapResidualIslands(Object residualIslands, Object residualIslandIds, RepositorySnapshot snapshot) {
        // P0: Reject legacy residualIslandIds - validation should have already rejected this
        if (residualIslandIds != null && residualIslandIds instanceof List<?> list && !list.isEmpty()) {
            throw new IllegalArgumentException(
                "Legacy field 'residualIslandIds' is not accepted. Use 'residualIslands' with full payload.");
        }

        List<Map<String, Object>> payloads = asListOfMaps(residualIslands);
        if (payloads.isEmpty()) {
            return List.of();
        }

        List<ResidualIslandDto> result = new ArrayList<>(payloads.size());
        for (Map<String, Object> island : payloads) {
            String islandId = asString(island.get("id"));
            if (islandId == null || islandId.isBlank()) {
                continue;
            }

            result.add(new ResidualIslandDto(
                islandId,
                firstNonBlank(asString(island.get("islandType")), "unknown_file"),
                firstNonBlank(asString(island.get("summary")), "Residual island from extractor"),
                asString(island.get("originalSource")), // P0: Original source for round-trip
                parseResidualSourceLocation(island.get("sourceLocation")),
                asString(island.get("sourceSpan")),
                asString(island.get("checksum")),
                asString(island.get("rawFragmentRef")),
                firstNonBlank(asString(island.get("reason")), "unsupported_file_type"),
                asDouble(island.get("confidence")),
                asBoolean(island.get("reviewRequired")),
                asDouble(island.get("riskScore")),
                mapOfStringToString(island.get("metadata")),
                asInteger(island.get("fileCount")),
                asString(island.get("tenantId")),
                asString(island.get("projectId")),
                asString(island.get("workspaceId")),
                firstNonBlank(asString(island.get("snapshotId")), snapshot.snapshotId())
            ));
        }
        return result;
    }

    private static ResidualIslandDto.SourceLocation parseResidualSourceLocation(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return null;
        }
        String filePath = asString(raw.get("filePath"));
        if (filePath == null || filePath.isBlank()) {
            return null;
        }
        return new ResidualIslandDto.SourceLocation(
            filePath,
            asInteger(raw.get("startLine")) == null ? 0 : asInteger(raw.get("startLine")),
            asInteger(raw.get("startColumn")) == null ? 0 : asInteger(raw.get("startColumn")),
            asInteger(raw.get("endLine")) == null ? 0 : asInteger(raw.get("endLine")),
            asInteger(raw.get("endColumn")) == null ? 0 : asInteger(raw.get("endColumn"))
        );
    }

    private static List<UnresolvedGraphEdgeDto> mapUnresolvedEdges(List<Map<String, Object>> rawEdges) {
        List<UnresolvedGraphEdgeDto> edges = new ArrayList<>(rawEdges.size());
        for (Map<String, Object> edge : rawEdges) {
            String sourceNodeId = asString(edge.get("sourceNodeId"));
            String targetRef = asString(edge.get("targetRef"));
            String relationshipType = asString(edge.get("relationshipType"));
            if (sourceNodeId == null || targetRef == null || relationshipType == null) {
                throw new IllegalArgumentException("Unresolved edge missing required fields sourceNodeId/targetRef/relationshipType");
            }
            edges.add(new UnresolvedGraphEdgeDto(
                asString(edge.get("id")),
                sourceNodeId,
                targetRef,
                relationshipType,
                asString(edge.get("targetKindHint")),
                null,
                asDouble(edge.get("confidence")),
                mapOrEmpty(edge.get("metadata")),
                asString(edge.get("tenantId")),
                asString(edge.get("projectId")),
                asString(edge.get("workspaceId"))
            ));
        }
        return edges;
    }

    private static List<EdgeResolutionRecordDto> mapEdgeResolutionRecords(List<Map<String, Object>> records) {
        List<EdgeResolutionRecordDto> mapped = new ArrayList<>(records.size());
        for (Map<String, Object> record : records) {
            String unresolvedEdgeId = asString(record.get("unresolvedEdgeId"));
            String status = asString(record.get("status"));
            if (unresolvedEdgeId == null || status == null) {
                throw new IllegalArgumentException("Edge resolution record missing unresolvedEdgeId/status");
            }
            mapped.add(new EdgeResolutionRecordDto(
                asString(record.get("id")),
                unresolvedEdgeId,
                status,
                asString(record.get("resolvedTargetId")),
                asListOfStrings(record.get("candidateIds")),
                Boolean.TRUE.equals(asBoolean(record.get("reviewRequired"))),
                asString(record.get("resolutionMethod")),
                mapOrEmpty(record.get("metadata")),
                asString(record.get("tenantId")),
                asString(record.get("projectId")),
                asString(record.get("workspaceId"))
            ));
        }
        return mapped;
    }

    private static List<SemanticModelDto> mapSemanticModels(List<Map<String, Object>> models, RepositorySnapshot snapshot) {
        if (models.isEmpty()) {
            return List.of();
        }
        List<SemanticModelDto> mapped = new ArrayList<>(models.size());
        for (Map<String, Object> model : models) {
            String elementId = asString(model.get("elementId"));
            String elementType = firstNonBlank(asString(model.get("elementType")), "unknown");
            String name = firstNonBlank(asString(model.get("name")), elementId);
            if (elementId == null || name == null) {
                continue;
            }
            mapped.add(SemanticModelDto.builder()
                .id(firstNonBlank(asString(model.get("id")), java.util.UUID.randomUUID().toString()))
                .elementId(elementId)
                .elementType(elementType)
                .name(name)
                .qualifiedName(asString(model.get("qualifiedName")))
                .filePath(asString(model.get("filePath")))
                .provenance(firstNonBlank(asString(model.get("provenance")), "ts-extractor"))
                .snapshotId(firstNonBlank(asString(model.get("snapshotId")), snapshot.snapshotId()))
                .tenantId(asString(model.get("tenantId")))
                .workspaceId(asString(model.get("workspaceId")))
                .projectId(asString(model.get("projectId")))
                .build());
        }
        return mapped;
    }

    private static Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Integer.parseInt(text);
        }
        return null;
    }

    private static Map<String, String> mapOfStringToString(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        Map<String, String> mapped = new java.util.LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            mapped.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
        }
        return mapped;
    }

    /**
     * P0: Maps artifact nodes from the TS worker response.
     * Strictly requires canonical field name 'type' - rejects legacy 'kind'.
     */
    private static List<ArtifactNodeDto> mapNodes(List<Map<String, Object>> rawNodes) {
        List<ArtifactNodeDto> nodes = new ArrayList<>();
        for (Map<String, Object> node : rawNodes) {
            @SuppressWarnings("unchecked")
            Map<String, Object> sourceLocation = node.get("sourceLocation") instanceof Map<?, ?> location
                ? (Map<String, Object>) location
                : Map.of();

            // P0: Require canonical 'type', reject legacy 'kind'
            String nodeType = asString(node.get("type"));
            if (nodeType == null || nodeType.isBlank()) {
                throw new IllegalArgumentException(
                    "Node missing required field 'type'. Legacy 'kind' alias is not accepted.");
            }

            nodes.add(new ArtifactNodeDto(
                asString(node.get("id")),
                nodeType,
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

    /**
     * P0: Maps artifact edges from the TS worker response.
     * Strictly requires canonical field names - rejects legacy aliases.
     */
    private static List<ArtifactEdgeDto> mapEdges(List<Map<String, Object>> rawEdges) {
        List<ArtifactEdgeDto> edges = new ArrayList<>();
        for (Map<String, Object> edge : rawEdges) {
            // P0: Strict mapping - require canonical field names validated by validateExtractionResponseSchema
            String relationshipType = asString(edge.get("relationshipType"));
            if (relationshipType == null || relationshipType.isBlank()) {
                throw new IllegalArgumentException(
                    "Edge missing required field 'relationshipType'. Legacy 'relationship'/'type'/'kind' aliases are not accepted.");
            }

            edges.add(new ArtifactEdgeDto(
                firstNonBlank(asString(edge.get("edgeId")), asString(edge.get("id"))),
                asString(edge.get("sourceNodeId")),
                asString(edge.get("targetNodeId")),
                relationshipType,
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
