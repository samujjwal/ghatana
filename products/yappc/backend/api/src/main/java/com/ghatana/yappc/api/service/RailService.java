/*
 * @doc.purpose Service for UI Left Rail data
 * @doc.layer api
 * @doc.pattern Service
 */
package com.ghatana.yappc.api.service;

import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.LLMGateway;
import com.ghatana.yappc.api.domain.Workspace;
import com.ghatana.yappc.api.repository.WorkspaceRepository;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Service for fetching data for the Unified Left Rail.
 *
 * @doc.type class
 * @doc.purpose Service for UI Left Rail data
 * @doc.layer api
 * @doc.pattern Service
 */
public class RailService {

    private final WorkspaceRepository workspaceRepository;
    private final LLMGateway llmGateway;

    public RailService(WorkspaceRepository workspaceRepository, LLMGateway llmGateway) {
        this.workspaceRepository = Objects.requireNonNull(workspaceRepository, "workspaceRepository");
        this.llmGateway = Objects.requireNonNull(llmGateway, "llmGateway");
    }

    public record ComponentItem(String id, String name, String category, String description, List<String> tags, int usage) {}
    public record InfraResource(String id, String name, String type, String provider, String status, double cost) {}
    public record FileItem(String id, String name, String type, long size, Instant modified, String path) {}
    public record DataSource(String id, String name, String type, String provider, String status, int tables, int endpoints) {}
    public record HistoryEntry(String id, String action, Instant timestamp, String details, boolean canUndo, boolean canRedo) {}
    public record FavoriteItem(String id, String name, String type, Instant dateAdded, int usageCount) {}
    public record AISuggestion(String id, String title, String description, String type, double confidence, String action) {}

    public Promise<List<ComponentItem>> getComponents(String tenantId, String category, String query, String mode) {
        return workspaceRepository.findByTenantId(tenantId)
            .map(workspaces -> {
                String normalizedMode = mode == null ? "developer" : mode.toLowerCase();
                String normalizedCategory = category == null ? null : category.toLowerCase();
                String normalizedQuery = query == null ? null : query.toLowerCase();

                Stream<ComponentItem> stream = workspaces.stream().map(workspace -> {
                    List<String> tags = new ArrayList<>();
                    tags.add("workspace");
                    tags.add(workspace.getStatus().name().toLowerCase());
                    if ("architect".equals(normalizedMode)) {
                        tags.add("architecture");
                    } else if ("designer".equals(normalizedMode)) {
                        tags.add("ux");
                    } else {
                        tags.add("engineering");
                    }

                    int usage = workspace.getMembers() != null ? workspace.getMembers().size() : 0;
                    return new ComponentItem(
                        workspace.getId().toString(),
                        workspace.getName(),
                        "Workspace",
                        workspace.getDescription() != null ? workspace.getDescription() : "Workspace component",
                        tags,
                        usage);
                });

                if (normalizedCategory != null && !normalizedCategory.isBlank()) {
                    stream = stream.filter(item -> item.category().toLowerCase().contains(normalizedCategory));
                }
                if (normalizedQuery != null && !normalizedQuery.isBlank()) {
                    stream = stream.filter(item ->
                        item.name().toLowerCase().contains(normalizedQuery)
                            || item.description().toLowerCase().contains(normalizedQuery)
                            || item.tags().stream().anyMatch(tag -> tag.toLowerCase().contains(normalizedQuery)));
                }

                return stream
                    .sorted(Comparator.comparingInt(ComponentItem::usage).reversed())
                    .limit(100)
                    .toList();
            });
    }

    public Promise<List<InfraResource>> getInfrastructure(String tenantId, String mode) {
        return workspaceRepository.findByTenantId(tenantId)
            .map(workspaces -> {
                if ("designer".equalsIgnoreCase(mode)) {
                    return List.of();
                }
                return workspaces.stream()
                    .map(workspace -> {
                        int memberCount = workspace.getMembers() != null ? workspace.getMembers().size() : 0;
                        return new InfraResource(
                            "infra-" + workspace.getId(),
                            workspace.getName() + " runtime",
                            "workspace",
                            "YAPPC",
                            workspace.getStatus().name().toLowerCase(),
                            memberCount * 2.5);
                    })
                    .sorted(Comparator.comparingDouble(InfraResource::cost).reversed())
                    .toList();
            });
    }

    public Promise<List<FileItem>> getFiles(String path) {
        return Promise.of(List.of(
            new FileItem("f1", "src", "folder", 0, Instant.now(), "/src"),
            new FileItem("f2", "main.java", "file", 1024, Instant.now(), "/src/main.java")
        ));
    }

    public Promise<List<DataSource>> getDataSources() {
        return Promise.of(List.of(
            new DataSource("ds1", "Production DB", "database", "PostgreSQL", "connected", 45, 0),
            new DataSource("ds2", "Stripe API", "api", "REST", "connected", 0, 12)
        ));
    }

    public Promise<List<HistoryEntry>> getHistory(String tenantId) {
        return workspaceRepository.findByTenantId(tenantId)
            .map(workspaces -> workspaces.stream()
                .sorted(Comparator.comparing(Workspace::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(20)
                .map(workspace -> new HistoryEntry(
                    "history-" + workspace.getId(),
                    "Workspace Updated",
                    workspace.getUpdatedAt() != null ? workspace.getUpdatedAt() : Instant.now(),
                    workspace.getName() + " status: " + workspace.getStatus(),
                    false,
                    false))
                .toList());
    }

    public Promise<List<FavoriteItem>> getFavorites(String tenantId) {
        return workspaceRepository.findByTenantId(tenantId)
            .map(workspaces -> workspaces.stream()
                .filter(workspace -> workspace.getOwnerId() != null)
                .limit(20)
                .map(workspace -> new FavoriteItem(
                    "favorite-" + workspace.getId(),
                    workspace.getName(),
                    "workspace",
                    workspace.getCreatedAt() != null ? workspace.getCreatedAt() : Instant.now(),
                    workspace.getMembers() != null ? workspace.getMembers().size() : 0))
                .toList());
    }

    public Promise<List<AISuggestion>> getSuggestions(Map<String, Object> context) {
        String tenantId = String.valueOf(context.getOrDefault("tenantId", "default"));
        String mode = String.valueOf(context.getOrDefault("mode", "developer"));
        String userPrompt = String.valueOf(context.getOrDefault("prompt", "Suggest meaningful improvements."));

        CompletionRequest request = CompletionRequest.builder()
            .prompt(
                "You are a YAPPC left-rail assistant for mode: " + mode + ".\n"
                    + "Tenant: " + tenantId + "\n"
                    + "User context: " + context + "\n"
                    + "Task: " + userPrompt + "\n"
                    + "Return a concise suggestion title and detail.")
            .temperature(0.3)
            .maxTokens(300)
            .build();

        return llmGateway.complete(request)
            .map(result -> {
                String text = result != null && result.getText() != null
                    ? result.getText().trim()
                    : "Review current workspace structure and prioritize reliability improvements.";
                String[] lines = text.split("\\R", 2);
                String title = lines.length > 0 && !lines[0].isBlank()
                    ? lines[0]
                    : "AI Recommendation";
                String detail = lines.length > 1 ? lines[1] : text;
                return List.of(new AISuggestion(
                    "s-" + Instant.now().toEpochMilli(),
                    title,
                    detail,
                    "improvement",
                    0.8,
                    "review"));
            })
            .then(
                Promise::of,
                e -> Promise.of(List.of(new AISuggestion(
                    "s-fallback",
                    "AI Suggestion Unavailable",
                    "Unable to generate AI suggestion right now. " + e.getMessage(),
                    "fallback",
                    0.2,
                    "retry"))));
    }
}
