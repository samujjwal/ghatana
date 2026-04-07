/**
 * useWorkspace Hook
 *
 * React hook that exposes workspace CRUD operations over the GraphQL API
 * while keeping local Jotai state in sync.
 *
 * @module hooks/useWorkspace
 * @doc.type module
 * @doc.purpose Workspace data-fetching and mutation hook
 * @doc.layer product
 * @doc.pattern Hook
 */

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useAtom, useAtomValue, useSetAtom } from 'jotai';
import { useCallback } from 'react';

import {
  workspacesAtom,
  currentWorkspaceIdAtom,
  currentWorkspaceAtom,
  workspaceLoadingAtom,
  workspaceErrorAtom,
  upsertWorkspaceAtom,
  removeWorkspaceAtom,
} from '../store/workspaceAtoms';

// ============================================================================
// GraphQL query strings
// ============================================================================

const WORKSPACES_QUERY = /* GraphQL */ `
  query GetWorkspaces {
    workspaces {
      id
      name
      description
      ownerId
      isDefault
      aiSummary
      aiTags
      createdAt
      updatedAt
    }
  }
`;

const CREATE_WORKSPACE_MUTATION = /* GraphQL */ `
  mutation CreateWorkspace($name: String!, $description: String) {
    createWorkspace(name: $name, description: $description) {
      id
      name
      description
      ownerId
      isDefault
      createdAt
      updatedAt
    }
  }
`;

const UPDATE_WORKSPACE_MUTATION = /* GraphQL */ `
  mutation UpdateWorkspace($id: ID!, $name: String, $description: String) {
    updateWorkspace(id: $id, name: $name, description: $description) {
      id
      name
      description
      updatedAt
    }
  }
`;

const DELETE_WORKSPACE_MUTATION = /* GraphQL */ `
  mutation DeleteWorkspace($id: ID!) {
    deleteWorkspace(id: $id)
  }
`;

// ============================================================================
// GraphQL client helper (re-uses existing pattern from the codebase)
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
 * Provides workspace CRUD operations and reactive access to workspace state.
 *
 * @doc.type function
 * @doc.purpose Workspace data access and mutation hook
 * @doc.layer product
 * @doc.pattern Hook
 *
 * @example
 * ```tsx
 * const { workspaces, currentWorkspace, selectWorkspace, createWorkspace } = useWorkspace();
 * ```
 */
export function useWorkspace(): {
  workspaces: Array<Record<string, unknown>>;
  currentWorkspace: Record<string, unknown> | undefined;
  currentWorkspaceId: string | null;
  isLoading: boolean;
  error: Error | null;
  selectWorkspace: (id: string | null) => void;
  createWorkspace: (input: { name: string; description?: string }) => Promise<unknown>;
  updateWorkspace: (input: { id: string; name?: string; description?: string }) => Promise<unknown>;
  deleteWorkspace: (id: string) => Promise<string>;
  isCreating: boolean;
  isUpdating: boolean;
  isDeleting: boolean;
  refetch: () => Promise<Array<Record<string, unknown>> | undefined>;
} {
  const queryClient = useQueryClient();

  const [currentWorkspaceId, setCurrentWorkspaceId] = useAtom(
    currentWorkspaceIdAtom
  );
  const currentWorkspace = useAtomValue(currentWorkspaceAtom);
  const [workspaces, setWorkspaces] = useAtom(workspacesAtom);
  const setLoading = useSetAtom(workspaceLoadingAtom);
  const setError = useSetAtom(workspaceErrorAtom);
  const upsertWorkspace = useSetAtom(upsertWorkspaceAtom);
  const removeWorkspace = useSetAtom(removeWorkspaceAtom);

  // ------------------------------------------------------------------
  // Fetch workspaces
  // ------------------------------------------------------------------

  const fetchQuery = useQuery({
    queryKey: ['workspaces'],
    queryFn: async () => {
      setLoading(true);
      try {
        const data = await gqlFetch<{ workspaces: unknown[] }>(
          WORKSPACES_QUERY
        );
        setWorkspaces(data.workspaces);
        return data.workspaces;
      } catch (err) {
        setError(err instanceof Error ? err : new Error(String(err)));
        throw err;
      } finally {
        setLoading(false);
      }
    },
  });

  // ------------------------------------------------------------------
  // Create workspace
  // ------------------------------------------------------------------

  const createMutation = useMutation({
    mutationFn: async (input: { name: string; description?: string }) => {
      const data = await gqlFetch<{ createWorkspace: unknown }>(
        CREATE_WORKSPACE_MUTATION,
        input
      );
      return data.createWorkspace;
    },
    onSuccess: (workspace) => {
      upsertWorkspace(workspace as Parameters<typeof upsertWorkspace>[0]);
      void queryClient.invalidateQueries({ queryKey: ['workspaces'] });
    },
  });

  // ------------------------------------------------------------------
  // Update workspace
  // ------------------------------------------------------------------

  const updateMutation = useMutation({
    mutationFn: async (input: {
      id: string;
      name?: string;
      description?: string;
    }) => {
      const data = await gqlFetch<{ updateWorkspace: unknown }>(
        UPDATE_WORKSPACE_MUTATION,
        input
      );
      return data.updateWorkspace;
    },
    onSuccess: (workspace) => {
      upsertWorkspace(workspace as Parameters<typeof upsertWorkspace>[0]);
    },
  });

  // ------------------------------------------------------------------
  // Delete workspace
  // ------------------------------------------------------------------

  const deleteMutation = useMutation({
    mutationFn: async (id: string) => {
      await gqlFetch<{ deleteWorkspace: boolean }>(DELETE_WORKSPACE_MUTATION, {
        id,
      });
      return id;
    },
    onSuccess: (id) => {
      removeWorkspace(id);
      void queryClient.invalidateQueries({ queryKey: ['workspaces'] });
    },
  });

  // ------------------------------------------------------------------
  // Select workspace
  // ------------------------------------------------------------------

  const selectWorkspace = useCallback(
    (id: string | null) => {
      setCurrentWorkspaceId(id);
    },
    [setCurrentWorkspaceId]
  );

  return {
    // State
    workspaces: workspaces ?? [],
    currentWorkspace,
    currentWorkspaceId,
    isLoading: fetchQuery.isLoading,
    error: fetchQuery.error,

    // Actions
    selectWorkspace,
    createWorkspace: createMutation.mutateAsync,
    updateWorkspace: updateMutation.mutateAsync,
    deleteWorkspace: deleteMutation.mutateAsync,

    // Mutation state
    isCreating: createMutation.isPending,
    isUpdating: updateMutation.isPending,
    isDeleting: deleteMutation.isPending,
    refetch: fetchQuery.refetch,
  };
}
