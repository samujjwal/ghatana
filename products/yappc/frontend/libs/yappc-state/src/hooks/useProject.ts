/**
 * useProject Hook
 *
 * React hook that exposes project CRUD operations over the GraphQL API
 * while keeping local Jotai state in sync.
 *
 * @module hooks/useProject
 * @doc.type module
 * @doc.purpose Project data-fetching and mutation hook
 * @doc.layer product
 * @doc.pattern Hook
 */

import { useCallback } from 'react';
import { useAtom, useAtomValue, useSetAtom } from 'jotai';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  projectsAtom,
  currentProjectIdAtom,
  currentProjectAtom,
  projectLoadingAtom,
  projectErrorAtom,
  upsertProjectAtom,
  removeProjectAtom,
} from '../store/projectAtoms';

// ============================================================================
// GraphQL query strings
// ============================================================================

const PROJECTS_QUERY = /* GraphQL */ `
  query GetProjects($workspaceId: ID!) {
    projects(workspaceId: $workspaceId) {
      id
      workspaceId
      name
      description
      type
      status
      lifecyclePhase
      isDefault
      aiSummary
      aiNextActions
      aiHealthScore
      createdAt
      updatedAt
    }
  }
`;

const CREATE_PROJECT_MUTATION = /* GraphQL */ `
  mutation CreateProject(
    $workspaceId: ID!
    $name: String!
    $description: String
    $type: ProjectType!
  ) {
    createProject(
      workspaceId: $workspaceId
      name: $name
      description: $description
      type: $type
    ) {
      id
      workspaceId
      name
      description
      type
      status
      lifecyclePhase
      isDefault
      createdAt
      updatedAt
    }
  }
`;

const UPDATE_PROJECT_MUTATION = /* GraphQL */ `
  mutation UpdateProject(
    $id: ID!
    $name: String
    $description: String
    $status: ProjectStatus
  ) {
    updateProject(
      id: $id
      name: $name
      description: $description
      status: $status
    ) {
      id
      name
      description
      status
      updatedAt
    }
  }
`;

const DELETE_PROJECT_MUTATION = /* GraphQL */ `
  mutation DeleteProject($id: ID!) {
    deleteProject(id: $id)
  }
`;

// ============================================================================
// GraphQL client helper
// ============================================================================

async function gqlFetch<T>(
  query: string,
  variables?: Record<string, unknown>
): Promise<T> {
  const apiUrl =
    (typeof window !== 'undefined' &&
      (window as Window & { __GRAPHQL_URL__?: string }).__GRAPHQL_URL__) ||
    '/graphql';

  const response = await fetch(apiUrl, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ query, variables }),
    credentials: 'include',
  });

  if (!response.ok) {
    throw new Error(`GraphQL request failed: ${response.statusText}`);
  }

  const json = (await response.json()) as {
    data?: T;
    errors?: { message: string }[];
  };

  if (json.errors?.length) {
    throw new Error(json.errors.map((e) => e.message).join('; '));
  }

  return json.data as T;
}

// ============================================================================
// Hook
// ============================================================================

/**
 * Provides project CRUD operations and reactive access to project state.
 *
 * @doc.type function
 * @doc.purpose Project data access and mutation hook
 * @doc.layer product
 * @doc.pattern Hook
 *
 * @example
 * ```tsx
 * const { projects, currentProject, selectProject, createProject } = useProject('workspace-id');
 * ```
 */
export function useProject(workspaceId: string | null) {
  const queryClient = useQueryClient();

  const [currentProjectId, setCurrentProjectId] = useAtom(currentProjectIdAtom);
  const currentProject = useAtomValue(currentProjectAtom);
  const setProjects = useSetAtom(projectsAtom);
  const setLoading = useSetAtom(projectLoadingAtom);
  const setError = useSetAtom(projectErrorAtom);
  const upsertProject = useSetAtom(upsertProjectAtom);
  const removeProject = useSetAtom(removeProjectAtom);
  const projects = useAtomValue(projectsAtom);

  // ------------------------------------------------------------------
  // Fetch projects for current workspace
  // ------------------------------------------------------------------

  const fetchQuery = useQuery({
    queryKey: ['projects', workspaceId],
    enabled: Boolean(workspaceId),
    queryFn: async () => {
      setLoading(true);
      try {
        const data = await gqlFetch<{ projects: unknown[] }>(PROJECTS_QUERY, {
          workspaceId,
        });
        setProjects(data.projects as unknown[]);
        return data.projects;
      } catch (err) {
        setError(err instanceof Error ? err : new Error(String(err)));
        throw err;
      } finally {
        setLoading(false);
      }
    },
  });

  // ------------------------------------------------------------------
  // Create project
  // ------------------------------------------------------------------

  const createMutation = useMutation({
    mutationFn: async (input: {
      name: string;
      description?: string;
      type?: string;
    }) => {
      const data = await gqlFetch<{ createProject: unknown }>(
        CREATE_PROJECT_MUTATION,
        { workspaceId, type: 'FULL_STACK', ...input }
      );
      return data.createProject;
    },
    onSuccess: (project) => {
      upsertProject(project as Parameters<typeof upsertProject>[0]);
      queryClient.invalidateQueries({ queryKey: ['projects', workspaceId] });
    },
  });

  // ------------------------------------------------------------------
  // Update project
  // ------------------------------------------------------------------

  const updateMutation = useMutation({
    mutationFn: async (input: {
      id: string;
      name?: string;
      description?: string;
      status?: string;
    }) => {
      const data = await gqlFetch<{ updateProject: unknown }>(
        UPDATE_PROJECT_MUTATION,
        input
      );
      return data.updateProject;
    },
    onSuccess: (project) => {
      upsertProject(project as Parameters<typeof upsertProject>[0]);
    },
  });

  // ------------------------------------------------------------------
  // Delete project
  // ------------------------------------------------------------------

  const deleteMutation = useMutation({
    mutationFn: async (id: string) => {
      await gqlFetch<{ deleteProject: boolean }>(DELETE_PROJECT_MUTATION, {
        id,
      });
      return id;
    },
    onSuccess: (id) => {
      removeProject(id);
      queryClient.invalidateQueries({ queryKey: ['projects', workspaceId] });
    },
  });

  // ------------------------------------------------------------------
  // Select project
  // ------------------------------------------------------------------

  const selectProject = useCallback(
    (id: string | null) => {
      setCurrentProjectId(id);
    },
    [setCurrentProjectId]
  );

  return {
    // State
    projects: projects ?? [],
    currentProject,
    currentProjectId,
    isLoading: fetchQuery.isLoading,
    error: fetchQuery.error,

    // Actions
    selectProject,
    createProject: createMutation.mutateAsync,
    updateProject: updateMutation.mutateAsync,
    deleteProject: deleteMutation.mutateAsync,

    // Mutation state
    isCreating: createMutation.isPending,
    isUpdating: updateMutation.isPending,
    isDeleting: deleteMutation.isPending,
    refetch: fetchQuery.refetch,
  };
}
