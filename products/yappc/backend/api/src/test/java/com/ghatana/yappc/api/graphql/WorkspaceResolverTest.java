/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.graphql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.api.common.TenantContextExtractor;
import com.ghatana.yappc.api.domain.Workspace;
import com.ghatana.yappc.api.domain.Workspace.WorkspaceStatus;
import com.ghatana.yappc.api.repository.WorkspaceRepository;
import graphql.GraphQLContext;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.activej.promise.Promise;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link WorkspaceResolver}.
 *
 * <p>Covers the workspaces, workspace, and createWorkspace data fetchers. Each fetcher is invoked
 * directly via {@link DataFetcher#get(DataFetchingEnvironment)} with a mocked environment.
 *
 * @doc.type class
 * @doc.purpose Unit tests for WorkspaceResolver GraphQL data fetchers
 * @doc.layer product
 * @doc.pattern Test
 */
class WorkspaceResolverTest extends EventloopTestBase {

  private WorkspaceRepository workspaceRepository;
  private WorkspaceResolver resolver;

  private static final String TENANT = "tenant-test";
  private static final UUID WORKSPACE_ID = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    workspaceRepository = mock(WorkspaceRepository.class);
    resolver = new WorkspaceResolver(workspaceRepository);
  }

  // =========================================================================
  // workspaces(tenantId) — Query
  // =========================================================================

  @Nested
  class WorkspacesQuery {

    @Test
    void shouldReturnListForTenant() throws Exception {
      Workspace w = makeWorkspace("My Workspace");
      when(workspaceRepository.findByTenantId(TENANT)).thenReturn(Promise.of(List.of(w)));

      DataFetchingEnvironment env = mockEnv(TENANT, null, null, null);
      DataFetcher<CompletableFuture<?>> fetcher = resolver.workspaces();

      @SuppressWarnings("unchecked")
      List<Workspace> result = (List<Workspace>) fetcher.get(env).get();

      assertThat(result).hasSize(1);
      assertThat(result.get(0).getName()).isEqualTo("My Workspace");
      verify(workspaceRepository).findByTenantId(TENANT);
    }

    @Test
    void shouldReturnEmptyListWhenNoWorkspaces() throws Exception {
      when(workspaceRepository.findByTenantId(TENANT)).thenReturn(Promise.of(List.of()));

      DataFetchingEnvironment env = mockEnv(TENANT, null, null, null);
      DataFetcher<CompletableFuture<?>> fetcher = resolver.workspaces();

      @SuppressWarnings("unchecked")
      List<Workspace> result = (List<Workspace>) fetcher.get(env).get();

      assertThat(result).isEmpty();
    }
  }

  // =========================================================================
  // workspace(tenantId, id) — Query
  // =========================================================================

  @Nested
  class WorkspaceQuery {

    @Test
    void shouldReturnWorkspaceWhenFound() throws Exception {
      Workspace w = makeWorkspace("Found Workspace");
      w.setId(WORKSPACE_ID);
      when(workspaceRepository.findById(TENANT, WORKSPACE_ID))
          .thenReturn(Promise.of(Optional.of(w)));

      DataFetchingEnvironment env = mockEnv(TENANT, WORKSPACE_ID.toString(), null, null);
      DataFetcher<CompletableFuture<?>> fetcher = resolver.workspace();

      Workspace result = (Workspace) fetcher.get(env).get();

      assertThat(result).isNotNull();
      assertThat(result.getName()).isEqualTo("Found Workspace");
    }

    @Test
    void shouldReturnNullWhenNotFound() throws Exception {
      when(workspaceRepository.findById(TENANT, WORKSPACE_ID))
          .thenReturn(Promise.of(Optional.empty()));

      DataFetchingEnvironment env = mockEnv(TENANT, WORKSPACE_ID.toString(), null, null);
      DataFetcher<CompletableFuture<?>> fetcher = resolver.workspace();

      Object result = fetcher.get(env).get();

      assertThat(result).isNull();
    }
  }

  // =========================================================================
  // createWorkspace(tenantId, input) — Mutation
  // =========================================================================

  @Nested
  class CreateWorkspaceMutation {

    @Test
    void shouldCreateWorkspaceWithOwnerFromRequestContext() throws Exception {
      Map<String, Object> input = Map.of("name", "New Workspace");
      TenantContextExtractor.RequestContext ctx =
          TenantContextExtractor.RequestContext.of(TENANT, "user-123", "developer");

      when(workspaceRepository.save(any(Workspace.class)))
          .thenAnswer(inv -> Promise.of(inv.getArgument(0)));

      DataFetchingEnvironment env = mockEnv(TENANT, null, input, ctx);
      DataFetcher<CompletableFuture<?>> fetcher = resolver.createWorkspace();

      Workspace result = (Workspace) fetcher.get(env).get();

      assertThat(result.getTenantId()).isEqualTo(TENANT);
      assertThat(result.getName()).isEqualTo("New Workspace");
      assertThat(result.getOwnerId()).isEqualTo("user-123");
      assertThat(result.getStatus()).isEqualTo(WorkspaceStatus.ACTIVE);
      verify(workspaceRepository).save(any(Workspace.class));
    }

    @Test
    void shouldFallbackToAdminOwnerWhenNoRequestContext() throws Exception {
      Map<String, Object> input = Map.of("name", "Admin Workspace");

      when(workspaceRepository.save(any(Workspace.class)))
          .thenAnswer(inv -> Promise.of(inv.getArgument(0)));

      // No requestContext provided
      DataFetchingEnvironment env = mockEnv(TENANT, null, input, null);
      DataFetcher<CompletableFuture<?>> fetcher = resolver.createWorkspace();

      Workspace result = (Workspace) fetcher.get(env).get();

      assertThat(result.getOwnerId()).isEqualTo("admin");
    }
  }

  // =========================================================================
  // Helpers
  // =========================================================================

  private Workspace makeWorkspace(String name) {
    Workspace w = new Workspace();
    w.setTenantId(TENANT);
    w.setName(name);
    w.setStatus(WorkspaceStatus.ACTIVE);
    w.setOwnerId("owner-1");
    return w;
  }

  /**
   * Construct a mock DataFetchingEnvironment responding to the arguments used by WorkspaceResolver.
   * Supply {@code null} for unused arguments.
   */
  private DataFetchingEnvironment mockEnv(
      String tenantId,
      String id,
      Map<String, Object> input,
      TenantContextExtractor.RequestContext requestContext) {

    DataFetchingEnvironment env = mock(DataFetchingEnvironment.class);
    GraphQLContext gqlCtx = mock(GraphQLContext.class);

    when(env.getArgument("tenantId")).thenReturn(tenantId);
    when(env.getArgument("id")).thenReturn(id);
    when(env.getArgument("input")).thenReturn(input);
    when(env.getGraphQlContext()).thenReturn(gqlCtx);
    when(gqlCtx.get("requestContext")).thenReturn(requestContext);

    return env;
  }
}
