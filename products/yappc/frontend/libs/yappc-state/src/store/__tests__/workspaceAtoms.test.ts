/**
 * workspaceAtoms Tests
 *
 * Verifies core state atoms using a standalone Jotai store so no React
 * component or DOM is required.
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { createStore } from 'jotai';
import type { Workspace } from '@yappc/core/types';

import {
  workspacesAtom,
  currentWorkspaceIdAtom,
  currentWorkspaceAtom,
  workspaceLoadingAtom,
  workspaceErrorAtom,
  upsertWorkspaceAtom,
  removeWorkspaceAtom,
} from '../workspaceAtoms';

// ---------------------------------------------------------------------------
// Fixture helpers
// ---------------------------------------------------------------------------

function makeWorkspace(overrides: Partial<Workspace> = {}): Workspace {
  return {
    id: 'ws-1',
    name: 'Test Workspace',
    description: null,
    ownerId: 'user-1',
    isDefault: false,
    aiSummary: null,
    aiTags: [],
    createdAt: '2025-01-01T00:00:00.000Z',
    updatedAt: '2025-01-01T00:00:00.000Z',
    ...overrides,
  };
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/**
 * Create a fresh Jotai store for each test so atoms start at their defaults.
 */
function makeStore() {
  return createStore();
}

// ---------------------------------------------------------------------------
// Primitive atoms — initial values
// ---------------------------------------------------------------------------

describe('workspaceAtoms — default values', () => {
  let store: ReturnType<typeof makeStore>;

  beforeEach(() => {
    store = makeStore();
  });

  it('workspacesAtom starts as empty array', () => {
    expect(store.get(workspacesAtom)).toEqual([]);
  });

  it('currentWorkspaceIdAtom starts as null', () => {
    expect(store.get(currentWorkspaceIdAtom)).toBeNull();
  });

  it('workspaceLoadingAtom starts as false', () => {
    expect(store.get(workspaceLoadingAtom)).toBe(false);
  });

  it('workspaceErrorAtom starts as null', () => {
    expect(store.get(workspaceErrorAtom)).toBeNull();
  });

  it('currentWorkspaceAtom starts as null', () => {
    expect(store.get(currentWorkspaceAtom)).toBeNull();
  });
});

// ---------------------------------------------------------------------------
// currentWorkspaceAtom — derived
// ---------------------------------------------------------------------------

describe('currentWorkspaceAtom — derived selection', () => {
  it('returns null when no workspaces are loaded', () => {
    const store = makeStore();
    store.set(currentWorkspaceIdAtom, 'ws-1');
    expect(store.get(currentWorkspaceAtom)).toBeNull();
  });

  it('returns the matching workspace when it exists in the list', () => {
    const store = makeStore();
    const ws = makeWorkspace();
    store.set(workspacesAtom, [ws]);
    store.set(currentWorkspaceIdAtom, 'ws-1');

    expect(store.get(currentWorkspaceAtom)).toEqual(ws);
  });

  it('returns null when currentWorkspaceIdAtom is null even with workspaces loaded', () => {
    const store = makeStore();
    store.set(workspacesAtom, [makeWorkspace()]);
    store.set(currentWorkspaceIdAtom, null);

    expect(store.get(currentWorkspaceAtom)).toBeNull();
  });

  it('updates reactively when the selection changes', () => {
    const store = makeStore();
    const ws1 = makeWorkspace({ id: 'ws-1', name: 'Alpha' });
    const ws2 = makeWorkspace({ id: 'ws-2', name: 'Beta' });
    store.set(workspacesAtom, [ws1, ws2]);

    store.set(currentWorkspaceIdAtom, 'ws-1');
    expect(store.get(currentWorkspaceAtom)?.name).toBe('Alpha');

    store.set(currentWorkspaceIdAtom, 'ws-2');
    expect(store.get(currentWorkspaceAtom)?.name).toBe('Beta');
  });
});

// ---------------------------------------------------------------------------
// upsertWorkspaceAtom
// ---------------------------------------------------------------------------

describe('upsertWorkspaceAtom', () => {
  it('prepends a new workspace to the list', () => {
    const store = makeStore();
    const existing = makeWorkspace({ id: 'ws-1', name: 'Existing' });
    store.set(workspacesAtom, [existing]);

    const newWs = makeWorkspace({ id: 'ws-2', name: 'New' });
    store.set(upsertWorkspaceAtom, newWs);

    const list = store.get(workspacesAtom);
    expect(list).toHaveLength(2);
    expect(list[0].id).toBe('ws-2'); // new workspace is prepended
  });

  it('updates an existing workspace in-place without changing list length', () => {
    const store = makeStore();
    const ws = makeWorkspace({ id: 'ws-1', name: 'Original' });
    store.set(workspacesAtom, [ws]);

    const updated = makeWorkspace({ id: 'ws-1', name: 'Updated' });
    store.set(upsertWorkspaceAtom, updated);

    const list = store.get(workspacesAtom);
    expect(list).toHaveLength(1);
    expect(list[0].name).toBe('Updated');
  });

  it('preserves order of other workspaces when updating one', () => {
    const store = makeStore();
    const ws1 = makeWorkspace({ id: 'ws-1', name: 'Alpha' });
    const ws2 = makeWorkspace({ id: 'ws-2', name: 'Beta' });
    const ws3 = makeWorkspace({ id: 'ws-3', name: 'Gamma' });
    store.set(workspacesAtom, [ws1, ws2, ws3]);

    store.set(
      upsertWorkspaceAtom,
      makeWorkspace({ id: 'ws-2', name: 'Beta Updated' })
    );

    const list = store.get(workspacesAtom);
    expect(list.map((w) => w.id)).toEqual(['ws-1', 'ws-2', 'ws-3']);
    expect(list[1].name).toBe('Beta Updated');
  });
});

// ---------------------------------------------------------------------------
// removeWorkspaceAtom
// ---------------------------------------------------------------------------

describe('removeWorkspaceAtom', () => {
  it('removes the workspace from the list by ID', () => {
    const store = makeStore();
    store.set(workspacesAtom, [
      makeWorkspace({ id: 'ws-1' }),
      makeWorkspace({ id: 'ws-2' }),
    ]);

    store.set(removeWorkspaceAtom, 'ws-1');

    const list = store.get(workspacesAtom);
    expect(list).toHaveLength(1);
    expect(list[0].id).toBe('ws-2');
  });

  it('clears currentWorkspaceIdAtom when the active workspace is deleted', () => {
    const store = makeStore();
    store.set(workspacesAtom, [makeWorkspace({ id: 'ws-1' })]);
    store.set(currentWorkspaceIdAtom, 'ws-1');

    store.set(removeWorkspaceAtom, 'ws-1');

    expect(store.get(currentWorkspaceIdAtom)).toBeNull();
  });

  it('does NOT clear currentWorkspaceIdAtom when a different workspace is deleted', () => {
    const store = makeStore();
    store.set(workspacesAtom, [
      makeWorkspace({ id: 'ws-1' }),
      makeWorkspace({ id: 'ws-2' }),
    ]);
    store.set(currentWorkspaceIdAtom, 'ws-1');

    store.set(removeWorkspaceAtom, 'ws-2');

    expect(store.get(currentWorkspaceIdAtom)).toBe('ws-1');
  });

  it('is a no-op when the workspace ID does not exist', () => {
    const store = makeStore();
    store.set(workspacesAtom, [makeWorkspace({ id: 'ws-1' })]);

    store.set(removeWorkspaceAtom, 'nonexistent');

    expect(store.get(workspacesAtom)).toHaveLength(1);
  });
});
