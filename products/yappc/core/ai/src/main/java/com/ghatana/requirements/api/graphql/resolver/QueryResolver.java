package com.ghatana.requirements.api.graphql.resolver;

import com.ghatana.platform.security.model.User;
import com.ghatana.requirements.ai.RequirementEmbeddingService;
import com.ghatana.requirements.ai.suggestions.AISuggestion;
import com.ghatana.requirements.application.project.ProjectService;
import com.ghatana.requirements.application.requirement.RequirementRepository;
import com.ghatana.requirements.application.workspace.WorkspaceService;
import com.ghatana.requirements.domain.project.Project;
import com.ghatana.requirements.domain.requirement.Requirement;
import com.ghatana.requirements.domain.workspace.Workspace;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import io.activej.promise.Promise;

/**
 * GraphQL Query Resolver for read operations.
 *
 * <p><b>Purpose</b><br>
 * Handles all GraphQL queries for workspaces, projects, and requirements.
 * Integrates with service layer to fetch data with proper authorization.
 *
 * <p>Resolver methods return {@link Promise} so that graphql-kickstart
 * handles the async completion without blocking the event loop.
 * The underlying ActiveJ {@code Promise} is bridged via {@code .toCompletableFuture()}.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * query {
 *   workspace(id: "ws-123") {
 *     name
 *     projects {
 *       name
 *     }
 *   }
 * }
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe. All service dependencies are thread-safe.
 *
 * <p><b>Architecture Role</b><br>
 * GraphQL resolver layer that delegates to application services.
 * Handles authentication and authorization through UserPrincipal.
 *
 * @doc.type class
 * @doc.purpose GraphQL query resolver
 * @doc.layer product
 * @doc.pattern Resolver
 */
public class QueryResolver implements GraphQLQueryResolver {
    private static final Logger logger = LoggerFactory.getLogger(QueryResolver.class);

    private final WorkspaceService workspaceService;
    private final ProjectService projectService;
    @Nullable private final RequirementRepository requirementRepository;
    @Nullable private final RequirementEmbeddingService embeddingService;

    /**
     * Full constructor with all optional dependencies.
     *
     * @param workspaceService workspace service (required)
     * @param projectService project service (required)
     * @param requirementRepository requirement repository (nullable — stubs return empty if absent)
     * @param embeddingService embedding service for vector search (nullable — stubs return empty if absent)
     */
    public QueryResolver(
            WorkspaceService workspaceService,
            ProjectService projectService,
            @Nullable RequirementRepository requirementRepository,
            @Nullable RequirementEmbeddingService embeddingService) {
        this.workspaceService = Objects.requireNonNull(workspaceService);
        this.projectService = Objects.requireNonNull(projectService);
        this.requirementRepository = requirementRepository;
        this.embeddingService = embeddingService;
    }

    /**
     * Backward-compatible constructor (no requirement/embedding services).
     */
    public QueryResolver(WorkspaceService workspaceService, ProjectService projectService) {
        this(workspaceService, projectService, null, null);
    }

    /**
     * Query: workspace(id: String!): Workspace — async, non-blocking.
     */
    public Promise<Workspace> workspace(String id, User principal) {
        logger.debug("GraphQL query: workspace(id={})", id);
        return workspaceService.getWorkspace(id, principal)
                .toCompletableFuture()
                .exceptionally(e -> {
                    logger.error("Failed to fetch workspace: {}", e.getMessage());
                    return null;
                });
    }

    /**
     * Query: workspaces: [Workspace!]! — async, non-blocking.
     */
    public CompletableFuture<List<Workspace>> workspaces(User principal) {
        logger.debug("GraphQL query: workspaces for user={}", principal.getUserId());
        return workspaceService.listUserWorkspaces(principal.getUserId())
                .toCompletableFuture()
                .exceptionally(e -> {
                    logger.error("Failed to list workspaces: {}", e.getMessage());
                    return List.of();
                });
    }

    /**
     * Query: project(id: String!): Project — async, non-blocking.
     */
    public Promise<Project> project(String id, User principal) {
        logger.debug("GraphQL query: project(id={})", id);
        return projectService.getProject(principal, id)
                .toCompletableFuture()
                .exceptionally(e -> {
                    logger.error("Failed to fetch project: {}", e.getMessage());
                    return null;
                });
    }

    /**
     * Query: projects(workspaceId: String!): [Project!]! — async, non-blocking.
     */
    public CompletableFuture<List<Project>> projects(String workspaceId, User principal) {
        logger.debug("GraphQL query: projects(workspaceId={})", workspaceId);
        return projectService.listWorkspaceProjects(principal, workspaceId)
                .toCompletableFuture()
                .exceptionally(e -> {
                    logger.error("Failed to fetch projects: {}", e.getMessage());
                    return List.of();
                });
    }

    // ============ GraphQLConfiguration Data-Fetcher Delegates ============
    // These methods are called from GraphQLConfiguration's manual RuntimeWiring
    // without a UserPrincipal (resolved from GraphQL context separately).

    /**
     * Get project by ID (no auth context — used by GraphQL data fetchers).
     */
    public Promise<Project> getProject(String id) {
        logger.debug("GraphQL data-fetcher: getProject(id={})", id);
        return projectService.getProject(null, id)
                .toCompletableFuture()
                .exceptionally(e -> {
                    logger.error("Failed to fetch project via data-fetcher: {}", e.getMessage());
                    return null;
                });
    }

    /**
     * Get requirement by ID.
     * Delegates to RequirementRepository when available.
     */
    public Promise<Requirement> getRequirement(String id) {
        logger.debug("GraphQL data-fetcher: getRequirement(id={})", id);
        if (requirementRepository == null) {
            logger.warn("RequirementRepository not wired — returning null for id={}", id);
            return Promise.of(null);
        }
        return requirementRepository.findById(id)
                .map(opt -> opt.orElse(null))
                .toCompletableFuture()
                .exceptionally(e -> {
                    logger.error("Failed to fetch requirement: {}", e.getMessage());
                    return null;
                });
    }

    /**
     * Get paginated projects list.
     * NOTE: Cross-workspace listing not yet supported — returns empty list.
     * Once ProjectService gains a listAll(tenantId, limit, offset) method this can be wired.
     */
    public List<Project> getProjects(int limit, int offset) {
        logger.debug("GraphQL data-fetcher: getProjects(limit={}, offset={})", limit, offset);
        // NOTE: implement when ProjectService.listAll(tenantId, limit, offset) is available
        return Collections.emptyList();
    }

    /**
     * Get requirements for a project with pagination.
     * Delegates to RequirementRepository.findByProjectId, applying offset/limit in-memory.
     */
    public CompletableFuture<List<Requirement>> getProjectRequirements(String projectId, int limit, int offset) {
        logger.debug("GraphQL data-fetcher: getProjectRequirements(projectId={}, limit={}, offset={})",
                projectId, limit, offset);
        if (requirementRepository == null) {
            logger.warn("RequirementRepository not wired — returning empty for project={}", projectId);
            return Promise.of(Collections.emptyList());
        }
        return requirementRepository.findByProjectId(projectId)
                .map(reqs -> reqs.stream()
                        .skip(offset)
                        .limit(limit)
                        .toList())
                .toCompletableFuture()
                .exceptionally(e -> {
                    logger.error("Failed to fetch project requirements: {}", e.getMessage());
                    return Collections.emptyList();
                });
    }

    /**
     * Get suggestion by ID.
     * NOTE: SuggestionEngine does not expose a findById method.
     * Pending addition of a SuggestionRepository.
     */
    public Object getSuggestion(String id) {
        logger.debug("GraphQL data-fetcher: getSuggestion(id={})", id);
        // NOTE: wire SuggestionRepository.findById(id) when available
        return null;
    }

    /**
     * Search similar requirements using vector search.
     * Delegates to RequirementEmbeddingService when available.
     */
    public CompletableFuture<List<?>> searchSimilar(String projectId, String query, int limit, float minSimilarity) {
        logger.debug("GraphQL data-fetcher: searchSimilar(projectId={}, query={}, limit={}, minSimilarity={})",
                projectId, query, limit, minSimilarity);
        if (embeddingService == null) {
            logger.warn("EmbeddingService not wired — returning empty for searchSimilar");
            return Promise.of(Collections.emptyList());
        }
        return embeddingService.findSimilarRequirements(query, projectId, limit, minSimilarity)
                .toCompletableFuture()
                .handle((results, e) -> {
                    if (e != null) {
                        logger.error("Vector search failed: {}", e.getMessage());
                        return (List<?>) Collections.emptyList();
                    }
                    return (List<?>) results;
                });
    }

    /**
     * Get suggestion statistics for a project.
     * Returns requirement-level counts via RequirementRepository when available.
     */
    public CompletableFuture<Map<String, Object>> getSuggestionStats(String projectId) {
        logger.debug("GraphQL data-fetcher: getSuggestionStats(projectId={})", projectId);
        if (requirementRepository == null) {
            logger.warn("RequirementRepository not wired — returning empty stats for project={}", projectId);
            return Promise.of(Collections.emptyMap());
        }
        return requirementRepository.countByProjectId(projectId)
                .map(count -> {
                    Map<String, Object> stats = new HashMap<>();
                    stats.put("projectId", projectId);
                    stats.put("totalRequirements", count);
                    return stats;
                })
                .toCompletableFuture()
                .exceptionally(e -> {
                    logger.error("Failed to fetch suggestion stats: {}", e.getMessage());
                    return Collections.emptyMap();
                });
    }
}

