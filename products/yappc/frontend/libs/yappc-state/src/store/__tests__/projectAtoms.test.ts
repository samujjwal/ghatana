/**
 * projectAtoms Tests
 *
 * Verifies core project state atoms using a standalone Jotai store so no React
 * component or DOM is required.
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { createStore } from 'jotai';
import type { Project } from '@yappc/core/types';

import {
  projectsAtom,
  currentProjectIdAtom,
  currentProjectAtom,
  activeProjectsAtom,
  projectLoadingAtom,
  projectErrorAtom,
  upsertProjectAtom,
  removeProjectAtom,
} from '../projectAtoms';

// ---------------------------------------------------------------------------
// Fixture helpers
// ---------------------------------------------------------------------------

function makeProject(overrides: Partial<Project> = {}): Project {
  return {
    id: 'proj-1',
    workspaceId: 'ws-1',
    name: 'Test Project',
    description: null,
    type: 'FULL_STACK',
    status: 'ACTIVE',
    lifecyclePhase: 'DEVELOPMENT',
    isDefault: false,
    aiSummary: null,
    aiNextActions: [],
    aiHealthScore: null,
    createdAt: '2025-01-01T00:00:00.000Z',
    updatedAt: '2025-01-01T00:00:00.000Z',
    ...overrides,
  };
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makeStore() {
  return createStore();
}

// ---------------------------------------------------------------------------
// Primitive atoms — initial values
// ---------------------------------------------------------------------------

describe('projectAtoms — default values', () => {
  let store: ReturnType<typeof makeStore>;

  beforeEach(() => {
    store = makeStore();
  });

  it('projectsAtom starts as empty array', () => {
    expect(store.get(projectsAtom)).toEqual([]);
  });

  it('currentProjectIdAtom starts as null', () => {
    expect(store.get(currentProjectIdAtom)).toBeNull();
  });

  it('projectLoadingAtom starts as false', () => {
    expect(store.get(projectLoadingAtom)).toBe(false);
  });

  it('projectErrorAtom starts as null', () => {
    expect(store.get(projectErrorAtom)).toBeNull();
  });

  it('currentProjectAtom starts as null', () => {
    expect(store.get(currentProjectAtom)).toBeNull();
  });

  it('activeProjectsAtom starts as empty array', () => {
    expect(store.get(activeProjectsAtom)).toEqual([]);
  });
});

// ---------------------------------------------------------------------------
// currentProjectAtom — derived
// ---------------------------------------------------------------------------

describe('currentProjectAtom — derived selection', () => {
  it('returns null when no projects are loaded', () => {
    const store = makeStore();
    store.set(currentProjectIdAtom, 'proj-1');
    expect(store.get(currentProjectAtom)).toBeNull();
  });

  it('returns the matching project when it exists in the list', () => {
    const store = makeStore();
    const proj = makeProject();
    store.set(projectsAtom, [proj]);
    store.set(currentProjectIdAtom, 'proj-1');
    expect(store.get(currentProjectAtom)).toEqual(proj);
  });

  it('returns null when currentProjectIdAtom is null even with projects loaded', () => {
    const store = makeStore();
    store.set(projectsAtom, [makeProject()]);
    store.set(currentProjectIdAtom, null);
    expect(store.get(currentProjectAtom)).toBeNull();
  });

  it('updates reactively when the selection changes', () => {
    const store = makeStore();
    const p1 = makeProject({ id: 'proj-1', name: 'Alpha' });
    const p2 = makeProject({ id: 'proj-2', name: 'Beta' });
    store.set(projectsAtom, [p1, p2]);

    store.set(currentProjectIdAtom, 'proj-1');
    expect(store.get(currentProjectAtom)?.name).toBe('Alpha');

    store.set(currentProjectIdAtom, 'proj-2');
    expect(store.get(currentProjectAtom)?.name).toBe('Beta');
  });
});

// ---------------------------------------------------------------------------
// activeProjectsAtom — derived
// ---------------------------------------------------------------------------

describe('activeProjectsAtom — derived filter', () => {
  it('returns only ACTIVE projects', () => {
    const store = makeStore();
    store.set(projectsAtom, [
      makeProject({ id: 'p-1', status: 'ACTIVE' }),
      makeProject({ id: 'p-2', status: 'ARCHIVED' }),
      makeProject({ id: 'p-3', status: 'ACTIVE' }),
    ]);

    const active = store.get(activeProjectsAtom);
    expect(active).toHaveLength(2);
    expect(active.map((p) => p.id)).toEqual(['p-1', 'p-3']);
  });

  it('returns empty array when all projects are archived', () => {
    const store = makeStore();
    store.set(projectsAtom, [
      makeProject({ id: 'p-1', status: 'ARCHIVED' }),
      makeProject({ id: 'p-2', status: 'ARCHIVED' }),
    ]);
    expect(store.get(activeProjectsAtom)).toEqual([]);
  });

  it('returns all projects when all are ACTIVE', () => {
    const store = makeStore();
    store.set(projectsAtom, [
      makeProject({ id: 'p-1', status: 'ACTIVE' }),
      makeProject({ id: 'p-2', status: 'ACTIVE' }),
    ]);
    expect(store.get(activeProjectsAtom)).toHaveLength(2);
  });

  it('updates reactively when project status changes', () => {
    const store = makeStore();
    const p1 = makeProject({ id: 'p-1', status: 'ACTIVE' });
    store.set(projectsAtom, [p1]);
    expect(store.get(activeProjectsAtom)).toHaveLength(1);

    store.set(projectsAtom, [{ ...p1, status: 'ARCHIVED' }]);
    expect(store.get(activeProjectsAtom)).toHaveLength(0);
  });
});

// ---------------------------------------------------------------------------
// upsertProjectAtom
// ---------------------------------------------------------------------------

describe('upsertProjectAtom', () => {
  it('prepends a new project to the list', () => {
    const store = makeStore();
    const existing = makeProject({ id: 'proj-1', name: 'Existing' });
    store.set(projectsAtom, [existing]);

    const newProj = makeProject({ id: 'proj-2', name: 'New' });
    store.set(upsertProjectAtom, newProj);

    const list = store.get(projectsAtom);
    expect(list).toHaveLength(2);
    expect(list[0].id).toBe('proj-2'); // new project prepended
  });

  it('updates an existing project in-place without changing list length', () => {
    const store = makeStore();
    const proj = makeProject({ id: 'proj-1', name: 'Original' });
    store.set(projectsAtom, [proj]);

    const updated = makeProject({ id: 'proj-1', name: 'Updated' });
    store.set(upsertProjectAtom, updated);

    const list = store.get(projectsAtom);
    expect(list).toHaveLength(1);
    expect(list[0].name).toBe('Updated');
  });

  it('preserves order of other projects when updating one', () => {
    const store = makeStore();
    const p1 = makeProject({ id: 'proj-1', name: 'Alpha' });
    const p2 = makeProject({ id: 'proj-2', name: 'Beta' });
    const p3 = makeProject({ id: 'proj-3', name: 'Gamma' });
    store.set(projectsAtom, [p1, p2, p3]);

    store.set(
      upsertProjectAtom,
      makeProject({ id: 'proj-2', name: 'Beta Updated' })
    );

    const list = store.get(projectsAtom);
    expect(list.map((p) => p.id)).toEqual(['proj-1', 'proj-2', 'proj-3']);
    expect(list[1].name).toBe('Beta Updated');
  });
});

// ---------------------------------------------------------------------------
// removeProjectAtom
// ---------------------------------------------------------------------------

describe('removeProjectAtom', () => {
  it('removes the project from the list by ID', () => {
    const store = makeStore();
    store.set(projectsAtom, [
      makeProject({ id: 'proj-1' }),
      makeProject({ id: 'proj-2' }),
    ]);

    store.set(removeProjectAtom, 'proj-1');

    const list = store.get(projectsAtom);
    expect(list).toHaveLength(1);
    expect(list[0].id).toBe('proj-2');
  });

  it('clears currentProjectIdAtom when the active project is deleted', () => {
    const store = makeStore();
    store.set(projectsAtom, [makeProject({ id: 'proj-1' })]);
    store.set(currentProjectIdAtom, 'proj-1');

    store.set(removeProjectAtom, 'proj-1');

    expect(store.get(currentProjectIdAtom)).toBeNull();
  });

  it('does NOT clear currentProjectIdAtom when a different project is deleted', () => {
    const store = makeStore();
    store.set(projectsAtom, [
      makeProject({ id: 'proj-1' }),
      makeProject({ id: 'proj-2' }),
    ]);
    store.set(currentProjectIdAtom, 'proj-1');

    store.set(removeProjectAtom, 'proj-2');

    expect(store.get(currentProjectIdAtom)).toBe('proj-1');
  });

  it('is a no-op when the project ID does not exist', () => {
    const store = makeStore();
    store.set(projectsAtom, [makeProject({ id: 'proj-1' })]);

    store.set(removeProjectAtom, 'nonexistent');

    expect(store.get(projectsAtom)).toHaveLength(1);
  });
});
