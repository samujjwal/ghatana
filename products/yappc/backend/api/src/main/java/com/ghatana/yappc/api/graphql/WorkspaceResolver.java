package com.ghatana.yappc.api.graphql;

import com.ghatana.yappc.api.common.TenantContextExtractor;
import com.ghatana.yappc.api.domain.Workspace;
import com.ghatana.yappc.api.repository.WorkspaceRepository;
import graphql.schema.DataFetcher;
import io.activej.inject.annotation.Inject;
import io.activej.promise.Promise;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * GraphQL resolver for Workspace queries and mutations.
 *
 * <p>All data fetchers return {@link Promise} instances that are obtained
 * non-blockingly from ActiveJ {@link Promise} via {@link Promise#toCompletableFuture()}.
 * The GraphQL engine handles the async completion — no {@code .get()} calls are used.
 *
 * @doc.type class
 * @doc.purpose Workspace GraphQL data fetchers
 * @doc.layer product
 * @doc.pattern Resolver
 */
public class WorkspaceResolver {

    private final WorkspaceRepository workspaceRepository;

    @Inject
    public WorkspaceResolver(WorkspaceRepository workspaceRepository) {
        this.workspaceRepository = workspaceRepository;
    }

    /** Query: workspaces(tenantId) — async, non-blocking. */
    public DataFetcher<CompletableFuture<?>> workspaces() {
        return env -> {
            String tenantId = env.getArgument("tenantId");
            return workspaceRepository.findByTenantId(tenantId).toCompletableFuture();
        };
    }

    /** Query: workspace(tenantId, id) — async, non-blocking. */
    public DataFetcher<CompletableFuture<?>> workspace() {
        return env -> {
            String tenantId = env.getArgument("tenantId");
            String id = env.getArgument("id");
            return workspaceRepository.findById(tenantId, UUID.fromString(id))
                    .map(opt -> opt.orElse(null))
                    .toCompletableFuture();
        };
    }

    /** Mutation: createWorkspace(tenantId, input) — async, non-blocking. */
    public DataFetcher<CompletableFuture<?>> createWorkspace() {
        return env -> {
            Map<String, Object> input = env.getArgument("input");
            TenantContextExtractor.RequestContext requestContext =
                    env.getGraphQlContext().get("requestContext");
            String ownerId = requestContext != null && requestContext.userId() != null
                    ? requestContext.userId()
                    : "admin";
            Workspace w = new Workspace();
            w.setTenantId(env.getArgument("tenantId"));
            w.setName((String) input.get("name"));
            w.setStatus(Workspace.WorkspaceStatus.ACTIVE);
            w.setOwnerId(ownerId);
            return workspaceRepository.save(w).toCompletableFuture();
        };
    }
}
