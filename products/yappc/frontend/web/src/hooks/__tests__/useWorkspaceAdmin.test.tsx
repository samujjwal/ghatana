/**
 * useWorkspaceAdmin Hook Tests
 */

import React from 'react';
import { createStore, Provider } from 'jotai';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';

// Mock yappc-auth/rbac
const mockRolePermissions = vi.hoisted(() => ({
  admin: ['read', 'write', 'delete', 'invite'],
  member: ['read', 'write'],
  viewer: ['read'],
}));
vi.mock('yappc-auth/rbac', () => ({
  WorkspaceRole: { ADMIN: 'admin', MEMBER: 'member', VIEWER: 'viewer' },
  ROLE_PERMISSIONS: mockRolePermissions,
}));

// Mock workspace service
const mockWorkspaceService = vi.hoisted(() => ({
  getWorkspace: vi.fn().mockReturnValue(null),
  getWorkspaceMembers: vi.fn().mockReturnValue([]),
  addMember: vi.fn().mockResolvedValue(undefined),
  removeMember: vi.fn().mockResolvedValue(undefined),
  updateMemberRole: vi.fn().mockResolvedValue(undefined),
  updateMemberPersonas: vi.fn().mockResolvedValue(undefined),
}));
vi.mock('../../state/atoms', () => {
  return {
    getWorkspaceService: () => mockWorkspaceService,
    createWorkspaceService: () => mockWorkspaceService,
  };
});

import { useWorkspaceAdmin } from '../useWorkspaceAdmin';
import {
  selectedWorkspaceAtom,
  currentMembershipAtom,
  userPermissionsAtom,
  currentUserAtom,
} from '@/stores/user.store';

function createTestEnv(): {
  wrapper: React.ComponentType<{ children: React.ReactNode }>;
  store: ReturnType<typeof createStore>;
} {
  const store = createStore();
  const wrapper = ({ children }: { children: React.ReactNode }) => (
    <Provider store={store}>{children}</Provider>
  );
  return { wrapper, store };
}

describe('useWorkspaceAdmin', () => {
  let wrapper: React.ComponentType<{ children: React.ReactNode }>;
  let store: ReturnType<typeof createStore>;

  beforeEach(() => {
    const env = createTestEnv();
    wrapper = env.wrapper;
    store = env.store;
    vi.clearAllMocks();
    mockWorkspaceService.getWorkspace.mockReturnValue(null);
    mockWorkspaceService.getWorkspaceMembers.mockReturnValue([]);
  });

  it('returns null workspace and empty members initially', () => {
    const { result } = renderHook(() => useWorkspaceAdmin(), { wrapper });

    expect(result.current.workspace).toBeNull();
    expect(result.current.members).toEqual([]);
    expect(result.current.currentMembership).toBeNull();
    expect(result.current.error).toBeNull();
  });

  it('exposes all required functions', () => {
    const { result } = renderHook(() => useWorkspaceAdmin(), { wrapper });

    expect(typeof result.current.inviteMember).toBe('function');
    expect(typeof result.current.removeMember).toBe('function');
    expect(typeof result.current.updateMemberRole).toBe('function');
    expect(typeof result.current.updateMemberPersonas).toBe('function');
    expect(typeof result.current.refresh).toBe('function');
    expect(typeof result.current.hasPermission).toBe('function');
  });

  it('hasPermission returns false when no membership', () => {
    const { result } = renderHook(() => useWorkspaceAdmin(), { wrapper });

    expect(result.current.hasPermission('read')).toBe(false);
  });

  it('isLoading is false when no workspace is selected', () => {
    const { result } = renderHook(() => useWorkspaceAdmin(), { wrapper });

    expect(result.current.isLoading).toBe(false);
  });

  it('loads workspace and members when workspaceId is set', async () => {
    const fakeWorkspace = { id: 'ws-1', name: 'My Workspace', slug: 'my-workspace' };
    const fakeMembers = [{ userId: 'user-1', email: 'a@b.com', role: 'admin', personas: [] }];
    mockWorkspaceService.getWorkspace.mockReturnValue(fakeWorkspace);
    mockWorkspaceService.getWorkspaceMembers.mockReturnValue(fakeMembers);

    const { result } = renderHook(() => useWorkspaceAdmin(), { wrapper });

    // Set atom after mount (same pattern used by the passing "sets error" test)
    await act(async () => {
      store.set(selectedWorkspaceAtom, 'ws-1');
    });

    // getWorkspace is sync inside loadWorkspaceData — verify service was invoked
    await waitFor(() => {
      expect(mockWorkspaceService.getWorkspace).toHaveBeenCalledWith('ws-1');
    });
    expect(mockWorkspaceService.getWorkspaceMembers).toHaveBeenCalledWith('ws-1');
    expect(result.current.error).toBeNull();
  });

  it('sets error when getWorkspace returns null for selected workspace', async () => {
    mockWorkspaceService.getWorkspace.mockReturnValue(null);

    const { result } = renderHook(() => useWorkspaceAdmin(), { wrapper });

    await act(async () => {
      store.set(selectedWorkspaceAtom, 'ws-missing');
    });

    await waitFor(() => expect(result.current.error).toBe('Workspace not found'), {
      timeout: 3000,
    });
    expect(result.current.workspace).toBeNull();
  });

  it('hasPermission returns true when membership role includes the permission', async () => {
    // Render with no workspaceId so the initial effect leaves membership alone (returns early)
    const { result } = renderHook(() => useWorkspaceAdmin(), { wrapper });

    // Set currentMembership AFTER initial effects have run to avoid it being reset
    act(() => {
      store.set(currentMembershipAtom, { userId: 'user-1', role: 'admin', personas: [] } as never);
    });

    expect(result.current.hasPermission('read')).toBe(true);
    expect(result.current.hasPermission('invite')).toBe(true);
    expect(result.current.hasPermission('delete')).toBe(true);
  });

  it('refresh re-loads workspace data on demand', async () => {
    const { result } = renderHook(() => useWorkspaceAdmin(), { wrapper });

    // Set workspaceId after mount so the hook picks it up
    await act(async () => {
      store.set(selectedWorkspaceAtom, 'ws-1');
    });

    // Wait for initial load
    await waitFor(() => {
      expect(mockWorkspaceService.getWorkspace).toHaveBeenCalledWith('ws-1');
    });

    const callCountAfterLoad = mockWorkspaceService.getWorkspace.mock.calls.length;

    // Explicitly call refresh to trigger a second load
    await act(async () => {
      await result.current.refresh();
    });

    // refresh() should invoke loadWorkspaceData again
    expect(mockWorkspaceService.getWorkspace.mock.calls.length).toBeGreaterThan(callCountAfterLoad);
  });
});
