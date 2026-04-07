/**
 * Project State Atoms
 *
 * Jotai atoms for project selection, listing, loading state, and errors.
 * All atoms use StateManager factory methods for consistent registration.
 *
 * @module state/projectAtoms
 * @doc.type module
 * @doc.purpose Project state atoms
 * @doc.layer product
 * @doc.pattern State Management
 */

import { atom } from 'jotai';
import type { WritableAtom } from 'jotai';

import type { Project } from '@yappc/core/types';

import { StateManager } from './StateManager';

// ============================================================================
// Primitive atoms
// ============================================================================

/**
 * ID of the currently-selected project.
 * Persisted to sessionStorage so it survives soft navigations but resets on new sessions.
 */
export const currentProjectIdAtom = StateManager.createPersistentAtom<
  string | null
>('project:currentId', null, {
  description: 'Currently selected project ID',
  storage: 'session',
});

/**
 * Full list of projects for the currently-active workspace.
 */
export const projectsAtom = StateManager.createAtom<Project[]>(
  'project:list',
  [],
  'Projects in the active workspace'
);

/**
 * Whether project data is currently being loaded.
 */
export const projectLoadingAtom = StateManager.createAtom<boolean>(
  'project:loading',
  false,
  'Project data loading flag'
);

/**
 * Last project-related error, or null if there is none.
 */
export const projectErrorAtom = StateManager.createAtom<Error | null>(
  'project:error',
  null,
  'Project operation error'
);

// ============================================================================
// Derived atoms
// ============================================================================

/**
 * The current Project object, derived from currentProjectIdAtom + projectsAtom.
 */
export const currentProjectAtom =
  StateManager.createDerivedAtom<Project | null>(
    'project:current',
    (get) => {
      const id = get(currentProjectIdAtom);
      const projects = get(
        projectsAtom
      );
      return id ? (projects.find((p) => p.id === id) ?? null) : null;
    },
    'Currently selected project object'
  );

/**
 * Derived atom: projects filtered to ACTIVE status.
 */
export const activeProjectsAtom = StateManager.createDerivedAtom<Project[]>(
  'project:activeList',
  (get) => {
    const projects = get(
      projectsAtom
    );
    return projects.filter((p) => p.status === 'ACTIVE');
  },
  'Active projects only'
);

// ============================================================================
// Action atoms
// ============================================================================

/**
 * Write-only atom that upserts a project in the projects list.
 */
export const upsertProjectAtom = atom(null, (get, set, project: Project) => {
  const pAtom = projectsAtom;
  const current = get(pAtom);
  const idx = current.findIndex((p) => p.id === project.id);
  if (idx >= 0) {
    const updated = [...current];
    updated[idx] = project;
    set(pAtom, updated);
  } else {
    set(pAtom, [project, ...current]);
  }
});

/**
 * Write-only atom that removes a project from the list by ID.
 */
export const removeProjectAtom = atom(null, (get, set, projectId: string) => {
  const pAtom = projectsAtom;
  const current = get(pAtom);
  set(
    pAtom,
    current.filter((p) => p.id !== projectId)
  );
  if (get(currentProjectIdAtom) === projectId) {
    set(currentProjectIdAtom, null);
  }
});
