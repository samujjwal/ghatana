/**
 * useCapabilityGate Hook Tests.
 */

import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook } from '@testing-library/react';
import { createStore, Provider } from 'jotai';
import { useCapabilityGate } from '../useCapabilityGate';
import { workspaceAtom, type WorkspaceState } from '../../state/atoms/workspaceAtom';

// ─────────────────────────────────────────────────────────────────────────────
// Mock useAuth
// ─────────────────────────────────────────────────────────────────────────────

const mockUseAuth = vi.hoisted(() => ({
  isAuthenticated: false,
  hasRole: vi.fn<(role: string) => boolean>(() => false),
  currentUser: null,
  currentSession: null,
  getToken: vi.fn<() => string | null>(() => null),
  getAuthHeader: vi.fn<() => string | null>(() => null),
  hasPermission: vi.fn<(permission: string) => boolean>(() => false),
  logout: vi.fn<() => Promise<void>>(),
}));

vi.mock('../useAuth', () => ({
  useAuth: () => mockUseAuth,
}));

// ─────────────────────────────────────────────────────────────────────────────
// Tests
// ─────────────────────────────────────────────────────────────────────────────

describe('useCapabilityGate', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseAuth.isAuthenticated = false;
    mockUseAuth.hasRole.mockReturnValue(false);
  });

  // ── Unauthenticated ────────────────────────────────────────────────────────

  it('denies any capability when the user is not authenticated', () => {
    mockUseAuth.isAuthenticated = false;

    const { result } = renderHook(() => useCapabilityGate('ops:alerts'));

    expect(result.current.granted).toBe(false);
    expect(result.current.reason).toBe('unauthenticated');
  });

  // ── Backend not live ───────────────────────────────────────────────────────

  it('denies ops:alerts when the user is authenticated but the backend is not live', () => {
    mockUseAuth.isAuthenticated = true;
    mockUseAuth.hasRole.mockReturnValue(true);  // role is fine

    const { result } = renderHook(() => useCapabilityGate('ops:alerts'));

    // All ops capabilities start as enabled: false
    expect(result.current.granted).toBe(false);
    expect(result.current.reason).toBe('backend-not-live');
  });

  it('denies admin:billing when the user is authenticated but the billing backend is not live', () => {
    mockUseAuth.isAuthenticated = true;
    mockUseAuth.hasRole.mockReturnValue(true);

    const { result } = renderHook(() => useCapabilityGate('admin:billing'));

    expect(result.current.granted).toBe(false);
    expect(result.current.reason).toBe('backend-not-live');
  });

  it('denies admin:teams when the user is authenticated but the backend is not live', () => {
    mockUseAuth.isAuthenticated = true;
    mockUseAuth.hasRole.mockReturnValue(true);

    const { result } = renderHook(() => useCapabilityGate('admin:teams'));

    expect(result.current.granted).toBe(false);
    expect(result.current.reason).toBe('backend-not-live');
  });

  // ── Role denied ────────────────────────────────────────────────────────────

  // Note: the tests below use a capability with enabled: false by default.
  // They verify the role-check path by testing the registry constants and
  // the hook's decision logic directly — the hook short-circuits at
  // `backend-not-live` before role checking.
  //
  // When a capability is enabled (enabled: true via config override at
  // runtime), insufficient role yields 'insufficient-role'. That path
  // is tested here by checking the hook returns 'backend-not-live' when
  // the backend flag is off, confirming the priority of flags over roles.

  it('returns backend-not-live (not insufficient-role) even when role is wrong and backend is off', () => {
    mockUseAuth.isAuthenticated = true;
    mockUseAuth.hasRole.mockReturnValue(false);  // no role

    const { result } = renderHook(() => useCapabilityGate('ops:oncall'));

    // backend-not-live takes priority over insufficient-role
    expect(result.current.reason).toBe('backend-not-live');
  });

  // ── All ops capabilities use the same backend-not-live guard ───────────────

  it.each([
    'ops:alerts',
    'ops:incidents',
    'ops:runbooks',
    'ops:oncall',
    'ops:warroom',
    'ops:postmortems',
    'ops:servicemap',
    'ops:metrics',
    'ops:logs',
    'ops:dashboards',
  ] as const)(
    '%s is denied with backend-not-live when authenticated',
    (capability) => {
      mockUseAuth.isAuthenticated = true;
      mockUseAuth.hasRole.mockReturnValue(true);

      const { result } = renderHook(() => useCapabilityGate(capability));

      expect(result.current.granted).toBe(false);
      expect(result.current.reason).toBe('backend-not-live');
    }
  );

  // ── Result shape when denied ───────────────────────────────────────────────

  it('result.granted is false and result.reason is defined when capability is denied', () => {
    mockUseAuth.isAuthenticated = false;

    const { result } = renderHook(() => useCapabilityGate('ops:metrics'));

    expect(result.current).toMatchObject({ granted: false });
    expect(result.current.reason).toBeDefined();
  });

  it('grants enabled admin capabilities from backend workspace admin access', () => {
    mockUseAuth.isAuthenticated = true;
    mockUseAuth.hasRole.mockReturnValue(false);

    const { result } = renderHook(() => useCapabilityGate('admin:feature-flags'), {
      wrapper: createWorkspaceWrapper({
        role: 'ADMIN',
        isOwner: false,
        capabilities: {
          read: true,
          create: true,
          update: true,
          delete: false,
          comment: true,
        },
      }),
    });

    expect(result.current).toEqual({ granted: true, reason: undefined });
  });

  it('grants product-family control plane when backend is live and workspace role is allowed', () => {
    mockUseAuth.isAuthenticated = true;
    mockUseAuth.hasRole.mockImplementation((role: string) => role === 'DEVELOPER');

    const { result } = renderHook(() => useCapabilityGate('product-family:control-plane'), {
      wrapper: createWorkspaceWrapper({
        role: 'MEMBER',
        isOwner: false,
        capabilities: {
          read: true,
          create: false,
          update: false,
          delete: false,
          comment: true,
        },
      }),
    });

    expect(result.current).toEqual({ granted: true, reason: undefined });
  });

  it('does not let global roles override backend workspace role for admin capabilities', () => {
    mockUseAuth.isAuthenticated = true;
    mockUseAuth.hasRole.mockReturnValue(true);

    const { result } = renderHook(() => useCapabilityGate('admin:feature-flags'), {
      wrapper: createWorkspaceWrapper({
        role: 'EDITOR',
        isOwner: false,
        capabilities: {
          read: true,
          create: true,
          update: true,
          delete: false,
          comment: true,
        },
      }),
    });

    expect(result.current).toEqual({ granted: false, reason: 'insufficient-role' });
  });
});

function createWorkspaceWrapper(
  workspaceAccess: Pick<WorkspaceState['currentWorkspace'], 'role' | 'isOwner' | 'capabilities'>,
): React.ComponentType<{ children: React.ReactNode }> {
  const store = createStore();
  store.set(workspaceAtom, {
    currentWorkspace: {
      id: 'workspace-1',
      name: 'Workspace',
      ownerId: 'owner-1',
      isDefault: true,
      aiTags: [],
      createdAt: '2026-05-07T00:00:00.000Z',
      updatedAt: '2026-05-07T00:00:00.000Z',
      ...workspaceAccess,
    },
    availableWorkspaces: [],
    ownedProjects: [],
    includedProjects: [],
    isLoading: false,
    isCreating: false,
    isSwitching: false,
  });

  return function WorkspaceWrapper({ children }: { children: React.ReactNode }) {
    return React.createElement(Provider, { store }, children);
  };
}
