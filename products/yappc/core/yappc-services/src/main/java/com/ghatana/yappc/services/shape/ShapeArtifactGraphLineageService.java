package com.ghatana.yappc.services.shape;

import com.ghatana.yappc.domain.artifact.ArtifactEdgeDto;
import com.ghatana.yappc.domain.artifact.ArtifactGraphIngestRequest;
import com.ghatana.yappc.domain.artifact.ArtifactGraphResponse;
import com.ghatana.yappc.domain.artifact.ArtifactNodeDto;
import com.ghatana.yappc.domain.shape.ArchitecturePattern;
import com.ghatana.yappc.domain.shape.BoundedContextSpec;
import com.ghatana.yappc.domain.shape.EntitySpec;
import com.ghatana.yappc.domain.shape.ShapeSpec;
import com.ghatana.yappc.services.artifact.ArtifactGraphService;
import com.ghatana.yappc.services.artifact.ArtifactRequestScope;
import io.activej.promise.Promise;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * @doc.type class
 * @doc.purpose Publishes Shape phase lineage into the canonical artifact graph
 * @doc.layer service
 * @doc.pattern Adapter
 */
public class ShapeArtifactGraphLineageService {

    private final ArtifactGraphService artifactGraphService;

    public ShapeArtifactGraphLineageService(ArtifactGraphService artifactGraphService) {
        this.artifactGraphService = Objects.requireNonNull(artifactGraphService, "artifactGraphService must not be null");
    }

    public Promise<ArtifactGraphResponse> recordShapeLineage(ShapeSpec shape) {
        Objects.requireNonNull(shape, "shape must not be null");
        String workspaceId = requiredMetadata(shape, "workspaceId");
        String projectId = requiredMetadata(shape, "projectId");
        ArtifactRequestScope scope = new ArtifactRequestScope(projectId, shape.tenantId(), workspaceId);
        ShapeLineageGraph graph = buildGraph(shape, projectId);
        ArtifactGraphIngestRequest request = new ArtifactGraphIngestRequest(
                projectId,
                shape.tenantId(),
                graph.nodes(),
                graph.edges(),
                "shape:" + shape.id(),
                "shape-lineage:" + shape.id(),
                "shape-lineage:" + shape.id() + ":" + shape.createdAt().toEpochMilli(),
                shape.id() + ":" + shape.createdAt().toEpochMilli(),
                List.of(),
                List.of(),
                List.of());
        return artifactGraphService.ingestGraph(scope, request);
    }

    private ShapeLineageGraph buildGraph(ShapeSpec shape, String projectId) {
        List<ArtifactNodeDto> nodes = new ArrayList<>();
        List<ArtifactEdgeDto> edges = new ArrayList<>();

        String shapeNodeId = nodeId(projectId, "shape", shape.id());
        addNode(nodes, shape, shapeNodeId, "shape", shape.id(), "shape", Map.of(
                "phase", "SHAPE",
                "intentRef", valueOrUnknown(shape.intentRef())));

        String previousNodeId = shapeNodeId;
        if (shape.intentRef() != null && !shape.intentRef().isBlank()) {
            String intentNodeId = nodeId(projectId, "intent", shape.intentRef());
            addNode(nodes, shape, intentNodeId, "intent", shape.intentRef(), "intent", Map.of(
                    "phase", "INTENT",
                    "sourceIntentId", shape.intentRef()));
            addEdge(edges, intentNodeId, shapeNodeId, "INFORMS");
        }

        ArchitecturePattern architecture = shape.architecture();
        if (architecture != null && architecture.name() != null && !architecture.name().isBlank()) {
            String architectureNodeId = nodeId(projectId, "architecture", shape.id(), architecture.name());
            addNode(nodes, shape, architectureNodeId, "architecture", architecture.name(), "architecture", Map.of(
                    "phase", "SHAPE",
                    "description", valueOrUnknown(architecture.description())));
            addEdge(edges, shapeNodeId, architectureNodeId, "DESCRIBES_ARCHITECTURE");
            previousNodeId = architectureNodeId;
        }

        List<String> surfaces = selectedSurfaces(shape.metadata());
        List<String> moduleNodeIds = moduleNodeIds(shape, projectId, nodes);
        List<String> surfaceNodeIds = new ArrayList<>();
        for (String surface : surfaces) {
            String surfaceNodeId = nodeId(projectId, "surface", surface);
            addNode(nodes, shape, surfaceNodeId, "surface", surface, "surface", Map.of(
                    "phase", "SHAPE",
                    "surface", surface));
            addEdge(edges, previousNodeId, surfaceNodeId, "TARGETS_SURFACE");
            surfaceNodeIds.add(surfaceNodeId);
        }

        List<String> moduleParents = surfaceNodeIds.isEmpty() ? List.of(previousNodeId) : surfaceNodeIds;
        for (String moduleNodeId : moduleNodeIds) {
            for (String parentId : moduleParents) {
                addEdge(edges, parentId, moduleNodeId, "DEFINES_MODULE");
            }
            List<String> artifactParents = surfaceNodeIds.isEmpty() ? List.of("unspecified") : surfaces;
            for (String surface : artifactParents) {
                String artifactNodeId = moduleNodeId + ":artifact:" + sanitize(surface);
                addNode(nodes, shape, artifactNodeId, "generated_artifact", artifactNodeId.substring(artifactNodeId.lastIndexOf(':') + 1), "generated-artifact", Map.of(
                        "phase", "GENERATE",
                        "artifactStatus", "planned",
                        "surface", surface,
                        "moduleNodeId", moduleNodeId));
                addEdge(edges, moduleNodeId, artifactNodeId, "GENERATES_ARTIFACT");
            }
        }

        return new ShapeLineageGraph(nodes, edges);
    }

    private List<String> moduleNodeIds(ShapeSpec shape, String projectId, List<ArtifactNodeDto> nodes) {
        List<String> moduleIds = new ArrayList<>();
        if (shape.domainModel() != null && shape.domainModel().boundedContexts() != null
                && !shape.domainModel().boundedContexts().isEmpty()) {
            for (BoundedContextSpec context : shape.domainModel().boundedContexts()) {
                String moduleId = nodeId(projectId, "module", context.name());
                addNode(nodes, shape, moduleId, "module", context.name(), "bounded-context", Map.of(
                        "phase", "SHAPE",
                        "description", valueOrUnknown(context.description())));
                moduleIds.add(moduleId);
            }
            return moduleIds;
        }

        if (shape.domainModel() != null && shape.domainModel().entities() != null) {
            for (EntitySpec entity : shape.domainModel().entities()) {
                String moduleId = nodeId(projectId, "module", entity.name());
                addNode(nodes, shape, moduleId, "module", entity.name(), "entity-module", Map.of(
                        "phase", "SHAPE",
                        "entity", entity.name()));
                moduleIds.add(moduleId);
            }
        }
        return moduleIds;
    }

    private static void addNode(
            List<ArtifactNodeDto> nodes,
            ShapeSpec shape,
            String id,
            String type,
            String name,
            String tag,
            Map<String, Object> properties) {
        nodes.add(new ArtifactNodeDto(
                id,
                type,
                name,
                null,
                null,
                properties,
                List.of(tag, "shape-lineage"),
                shape.tenantId(),
                shape.metadata().get("projectId"),
                null,
                "shape-lineage",
                "1.0.0",
                1.0,
                "derived",
                List.of(),
                List.of(),
                "shape:" + shape.id(),
                id));
    }

    private static void addEdge(List<ArtifactEdgeDto> edges, String sourceNodeId, String targetNodeId, String relationshipType) {
        edges.add(new ArtifactEdgeDto(
                sourceNodeId + "->" + targetNodeId + ":" + relationshipType,
                sourceNodeId,
                targetNodeId,
                relationshipType,
                Map.of("lineagePhase", "SHAPE"),
                1.0,
                false,
                Map.of("createdBy", "ShapeArtifactGraphLineageService"),
                null,
                null));
    }

    private static List<String> selectedSurfaces(Map<String, String> metadata) {
        Set<String> surfaces = new LinkedHashSet<>();
        addDelimited(surfaces, metadata.get("surface"));
        addDelimited(surfaces, metadata.get("surfaces"));
        addDelimited(surfaces, metadata.get("surfaceType"));
        return List.copyOf(surfaces);
    }

    private static void addDelimited(Set<String> values, String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return;
        }
        for (String value : rawValue.split(",")) {
            if (!value.isBlank()) {
                values.add(value.trim().toLowerCase(Locale.ROOT));
            }
        }
    }

    private static String requiredMetadata(ShapeSpec shape, String key) {
        String value = shape.metadata().get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("shape metadata is missing required lineage key: " + key);
        }
        return value;
    }

    private static String nodeId(String projectId, String... parts) {
        List<String> idParts = new ArrayList<>();
        idParts.add("yappc");
        idParts.add(sanitize(projectId));
        for (String part : parts) {
            idParts.add(sanitize(part));
        }
        return String.join(":", idParts);
    }

    private static String sanitize(String value) {
        String normalized = value == null || value.isBlank() ? "unknown" : value.trim().toLowerCase(Locale.ROOT);
        return normalized.replaceAll("[^a-z0-9._-]+", "-");
    }

    private static String valueOrUnknown(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private record ShapeLineageGraph(List<ArtifactNodeDto> nodes, List<ArtifactEdgeDto> edges) {
    }
}
