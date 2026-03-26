/**
 * Workspace State Atoms
 *
 * Jotai atoms for workspace selection, listing, loading state, and errors.
 * All atoms use StateManager factory methods for consistent registration.
 *
 * @module state/workspaceAtoms
 * @doc.type module
 * @doc.purpose Workspace state atoms
 * @doc.layer product
 * @doc.pattern State Management
 */

import { atom } from 'jotai';
import type { WritableAtom } from 'jotai';
import { StateManager } from './StateManager';
import type { Workspace, Project } from '@yappc/core/types';

// ============================================================================
// Primitive atoms
// ============================================================================

/**
 * ID of the currently-selected workspace.
 * Persisted to localStorage so the selection survives page refresh.
 */
export const currentWorkspaceIdAtom = StateManager.createPersistentAtom<
  string | null
>('workspace:currentId', null, {
  description: 'Currently selected workspace ID',
  storage: 'local',
});

/**
 * Full list of workspaces the current user belongs to.
 * Populated by the useWorkspace hook after API fetch.
 */
export const workspacesAtom = StateManager.createAtom<Workspace[]>(
  'workspace:list',
  [],
  'List of user workspaces'
);

/**
 * List of projects belonging to the currently-selected workspace.
 */
export const workspaceProjectsAtom = StateManager.createAtom<Project[]>(
  'workspace:projects',
  [],
  'Projects in the current workspace'
);

/**
 * Whether workspace data is currently being loaded.
 */
export const workspaceLoadingAtom = StateManager.createAtom<boolean>(
  'workspace:loading',
  false,
  'Workspace data loading flag'
);

/**
 * Last workspace-related error, or null if there is none.
 */
export const workspaceErrorAtom = StateManager.createAtom<Error | null>(
  'workspace:error',
  null,
  'Workspace operation error'
);

// ============================================================================
// Derived atoms
// ============================================================================

/**
 * The current Workspace object, derived from currentWorkspaceIdAtom + workspacesAtom.
 */
export const currentWorkspaceAtom =
  StateManager.createDerivedAtom<Workspace | null>(
    'workspace:current',
    (get) => {
      const id = get(currentWorkspaceIdAtom);
      const workspaces = get(workspacesAtom);
      return id ? (workspaces.find((w) => w.id === id) ?? null) : null;
    },
    'Currently selected workspace object'
  );

// ============================================================================
// Action atoms
// ============================================================================

/**
 * Write-only atom that updates a single workspace in the workspaces list.
 * Use this after an optimistic update or a server-confirmed mutation.
 */
export const upsertWorkspaceAtom = atom(
  null,
  (get, set, workspace: Workspace) => {
    const wAtom = workspacesAtom as WritableAtom<
      Workspace[],
      [Workspace[]],
      void
    >;
    const current = get(wAtom);
    const idx = current.findIndex((w) => w.id === workspace.id);
    if (idx >= 0) {
      const updated = [...current];
      updated[idx] = workspace;
      set(wAtom, updated);
    } else {
      set(wAtom, [workspace, ...current]);
    }
  }
);

/**
 * Write-only atom that removes a workspace from the list by ID.
 */
export const removeWorkspaceAtom = atom(
  null,
  (get, set, workspaceId: string) => {
    const wAtom = workspacesAtom as WritableAtom<
      Workspace[],
      [Workspace[]],
      void
    >;
    const current = get(wAtom);
    set(
      wAtom,
      current.filter((w) => w.id !== workspaceId)
    );
    // Clear selection if the deleted workspace was selected
    if (get(currentWorkspaceIdAtom) === workspaceId) {
      set(currentWorkspaceIdAtom, null);
    }
  }
);
