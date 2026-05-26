package com.ghatana.yappc.services.evolve;

import com.ghatana.yappc.domain.artifact.ArtifactNodeDto;
import com.ghatana.yappc.domain.evolve.EvolutionTask;
import com.ghatana.yappc.storage.ArtifactGraphRepository;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * @doc.type class
 * @doc.purpose Computes Evolve impact analysis from the canonical artifact graph
 * @doc.layer service
 * @doc.pattern Adapter
 */
public final class ArtifactGraphEvolutionImpactAnalysisService implements EvolutionImpactAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(ArtifactGraphEvolutionImpactAnalysisService.class);
    private static final int GRAPH_NODE_LIMIT = 10_000;

    private final ArtifactGraphRepository artifactGraphRepository;

    public ArtifactGraphEvolutionImpactAnalysisService(@NotNull ArtifactGraphRepository artifactGraphRepository) {
        this.artifactGraphRepository = Objects.requireNonNull(
                artifactGraphRepository,
                "artifactGraphRepository must not be null");
    }

    @Override
    public Promise<EvolutionImpactAnalysis> analyze(@NotNull ImpactAnalysisRequest request) {
        if (request.workspaceId() == null || request.workspaceId().isBlank()
                || "workspace-unavailable".equals(request.workspaceId())) {
            return Promise.of(inferWithoutGraph(
                    request,
                    "workspaceId is unavailable; canonical artifact graph traversal was skipped"));
        }

        return artifactGraphRepository.findNodesByProduct(
                        request.projectId(),
                        request.tenantId(),
                        request.workspaceId(),
                        GRAPH_NODE_LIMIT)
                .then((nodes, exception) -> {
                    if (exception != null) {
                        log.warn(
                                "Failed to compute Evolve impact analysis for tenantId={}, workspaceId={}, projectId={}",
                                request.tenantId(),
                                request.workspaceId(),
                                request.projectId(),
                                exception);
                        return Promise.of(inferWithoutGraph(
                                request,
                                "artifact graph query failed: " + exception.getClass().getSimpleName()));
                    }
                    return Promise.of(buildGraphImpact(request, nodes));
                });
    }

    private EvolutionImpactAnalysis buildGraphImpact(
            ImpactAnalysisRequest request,
            List<ArtifactNodeDto> nodes
    ) {
        Set<String> affectedSurfaces = new LinkedHashSet<>();
        Set<String> affectedModules = new LinkedHashSet<>();
        Set<String> affectedTests = new LinkedHashSet<>();
        Set<String> runtimeImpacts = new LinkedHashSet<>();
        Set<String> dependencyNodeIds = new LinkedHashSet<>();
        List<String> notes = new ArrayList<>();

        collectTaskImpacts(request.plan().tasks(), affectedSurfaces, affectedModules, affectedTests, runtimeImpacts);

        List<String> taskTokens = taskTokens(request.plan().tasks());
        for (ArtifactNodeDto node : nodes == null ? List.<ArtifactNodeDto>of() : nodes) {
            if (!matchesTask(node, taskTokens) && !matchesTaskImpact(node, affectedSurfaces, affectedModules)) {
                continue;
            }
            dependencyNodeIds.add(node.id());
            collectNodeImpact(node, affectedSurfaces, affectedModules, affectedTests, runtimeImpacts);
        }

        if (nodes == null || nodes.isEmpty()) {
            notes.add("artifact graph has no nodes for the project scope");
        }
        if (dependencyNodeIds.isEmpty()) {
            notes.add("no artifact graph nodes matched the proposed evolution tasks");
        }

        String status = dependencyNodeIds.isEmpty() ? "INFERRED" : "READY";
        return new EvolutionImpactAnalysis(
                status,
                "artifact-graph",
                List.copyOf(affectedSurfaces),
                List.copyOf(affectedModules),
                List.copyOf(affectedTests),
                List.copyOf(runtimeImpacts),
                List.copyOf(dependencyNodeIds),
                List.copyOf(notes));
    }

    private EvolutionImpactAnalysis inferWithoutGraph(ImpactAnalysisRequest request, String note) {
        Set<String> affectedSurfaces = new LinkedHashSet<>();
        Set<String> affectedModules = new LinkedHashSet<>();
        Set<String> affectedTests = new LinkedHashSet<>();
        Set<String> runtimeImpacts = new LinkedHashSet<>();
        collectTaskImpacts(request.plan().tasks(), affectedSurfaces, affectedModules, affectedTests, runtimeImpacts);
        return new EvolutionImpactAnalysis(
                "DEGRADED",
                "task-inference",
                List.copyOf(affectedSurfaces),
                List.copyOf(affectedModules),
                List.copyOf(affectedTests),
                List.copyOf(runtimeImpacts),
                List.of(),
                List.of(note));
    }

    private static void collectTaskImpacts(
            List<EvolutionTask> tasks,
            Set<String> affectedSurfaces,
            Set<String> affectedModules,
            Set<String> affectedTests,
            Set<String> runtimeImpacts
    ) {
        if (tasks == null) {
            return;
        }
        for (EvolutionTask task : tasks) {
            Map<String, Object> details = task.details() == null ? Map.of() : task.details();
            collectDelimited(details.get("surface"), affectedSurfaces);
            collectDelimited(details.get("surfaces"), affectedSurfaces);
            collectDelimited(details.get("module"), affectedModules);
            collectDelimited(details.get("modules"), affectedModules);
            collectDelimited(details.get("test"), affectedTests);
            collectDelimited(details.get("tests"), affectedTests);
            collectDelimited(details.get("runtime"), runtimeImpacts);
            collectDelimited(details.get("runtimeImpacts"), runtimeImpacts);
            collectTextImpact(task.description(), affectedSurfaces, affectedTests, runtimeImpacts);
        }
    }

    private static void collectNodeImpact(
            ArtifactNodeDto node,
            Set<String> affectedSurfaces,
            Set<String> affectedModules,
            Set<String> affectedTests,
            Set<String> runtimeImpacts
    ) {
        String type = normalize(node.type());
        if ("surface".equals(type)) {
            addValue(affectedSurfaces, node.name());
        } else if ("module".equals(type) || type.contains("module")) {
            addValue(affectedModules, node.name());
        } else if ("test".equals(type) || type.contains("test")) {
            addValue(affectedTests, node.name());
        } else if (type.contains("runtime") || type.contains("deployment") || type.contains("preview")) {
            addValue(runtimeImpacts, node.name());
        }
        Map<String, Object> properties = node.properties() == null ? Map.of() : node.properties();
        collectDelimited(properties.get("surface"), affectedSurfaces);
        collectDelimited(properties.get("module"), affectedModules);
        collectDelimited(properties.get("runtime"), runtimeImpacts);
    }

    private static boolean matchesTask(
            ArtifactNodeDto node,
            List<String> taskTokens
    ) {
        if (taskTokens.isEmpty()) {
            return false;
        }
        String haystack = normalize(String.join(" ",
                value(node.id()),
                value(node.type()),
                value(node.name()),
                value(node.filePath()),
                value(node.symbolRef())));
        return taskTokens.stream().anyMatch(token -> token.length() > 2 && haystack.contains(token));
    }

    private static boolean matchesTaskImpact(
            ArtifactNodeDto node,
            Set<String> affectedSurfaces,
            Set<String> affectedModules
    ) {
        String normalizedName = normalize(node.name());
        String normalizedType = normalize(node.type());
        return affectedSurfaces.stream().map(ArtifactGraphEvolutionImpactAnalysisService::normalize)
                .anyMatch(value -> normalizedName.contains(value) || normalizedType.contains(value))
                || affectedModules.stream().map(ArtifactGraphEvolutionImpactAnalysisService::normalize)
                .anyMatch(value -> normalizedName.contains(value) || normalizedType.contains(value));
    }

    private static List<String> taskTokens(List<EvolutionTask> tasks) {
        if (tasks == null) {
            return List.of();
        }
        Set<String> tokens = new LinkedHashSet<>();
        for (EvolutionTask task : tasks) {
            addTokens(tokens, task.type());
            addTokens(tokens, task.description());
            if (task.details() != null) {
                task.details().values().forEach(value -> addTokens(tokens, String.valueOf(value)));
            }
        }
        return List.copyOf(tokens);
    }

    private static void addTokens(Set<String> tokens, String value) {
        for (String token : normalize(value).split("[^a-z0-9._-]+")) {
            if (token.length() > 2) {
                tokens.add(token);
            }
        }
    }

    private static void collectTextImpact(
            String text,
            Set<String> affectedSurfaces,
            Set<String> affectedTests,
            Set<String> runtimeImpacts
    ) {
        String normalized = normalize(text);
        if (normalized.contains("web")) {
            affectedSurfaces.add("web");
        }
        if (normalized.contains("mobile")) {
            affectedSurfaces.add("mobile");
        }
        if (normalized.contains("api")) {
            affectedSurfaces.add("api");
        }
        if (normalized.contains("test") || normalized.contains("validation")) {
            affectedTests.add("validation");
        }
        if (normalized.contains("run") || normalized.contains("preview") || normalized.contains("deploy")) {
            runtimeImpacts.add("runtime");
        }
    }

    private static void collectDelimited(Object rawValue, Set<String> target) {
        if (rawValue instanceof Iterable<?> values) {
            for (Object value : values) {
                addValue(target, value);
            }
            return;
        }
        if (rawValue == null) {
            return;
        }
        for (String value : String.valueOf(rawValue).split(",")) {
            addValue(target, value);
        }
    }

    private static void addValue(Set<String> target, Object value) {
        String normalized = normalize(String.valueOf(value));
        if (!normalized.isBlank() && !"null".equals(normalized)) {
            target.add(normalized);
        }
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
