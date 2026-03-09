/**
 * Auth Store Atoms - Jotai migration from Zustand
 * Consolidated authentication with RBAC and session management
 * Combines functionality from both auth.tsx and auth.ts
 */

import { atom } from 'jotai';
import { atomWithStorage } from 'jotai/utils';

// Types from auth.tsx
export type Role = 'admin' | 'operator' | 'viewer';
export type Permission =
  | 'config:read'
  | 'config:write'
  | 'metrics:read'
  | 'status:read'
  | 'agent:restart'
  | 'agent:stop';

// Types from auth.ts (for backward compatibility)
export type PermissionEntry = {
  read: boolean;
  write: boolean;
};

export type PermissionKey =
  | 'metrics'
  | 'status'
  | 'config'
  | 'events'
  | 'audit'
  | 'control'
  | (string & {});

export interface User {
  id: string;
  username: string;
  roles: Role[];
  permissions: Permission[];
  lastLogin: number;
  sessionExpiry: number;
}

// Legacy user type (from auth.ts)
export interface AuthUser {
  id: string;
  username: string;
  role?: string;
}

// Role-Permission Matrix
export const ROLE_PERMISSIONS: Record<Role, Permission[]> = {
  admin: [
    'config:read',
    'config:write',
    'metrics:read',
    'status:read',
    'agent:restart',
    'agent:stop',
  ],
  operator: ['config:read', 'config:write', 'metrics:read', 'status:read', 'agent:restart'],
  viewer: ['config:read', 'metrics:read', 'status:read'],
};

// Default permissions (from auth.ts)
const defaultPermissions: Record<string, PermissionEntry> = {
  metrics: { read: true, write: false },
  status: { read: true, write: false },
  config: { read: true, write: false },
  events: { read: true, write: false },
  audit: { read: true, write: false },
  control: { read: true, write: false },
};

// Core persisted atoms (localStorage backed)
export const authUserAtom = atomWithStorage<User | null>('auth-user', null);
export const authSessionTokenAtom = atomWithStorage<string | null>('auth-session-token', null);
export const authIsAuthenticatedAtom = atomWithStorage<boolean>('auth-authenticated', false);

// Legacy permissions system (from auth.ts - for backward compatibility)
export const authLegacyPermissionsAtom = atom<Record<string, PermissionEntry>>(defaultPermissions);

// Derived authentication status
export const authStatusAtom = atom(get => {
  const user = get(authUserAtom);
  const isAuthenticated = get(authIsAuthenticatedAtom);
  const sessionToken = get(authSessionTokenAtom);

  return {
    user,
    isAuthenticated,
    sessionToken,
    hasSession: !!sessionToken,
  };
});

// Session validity check
export const authIsSessionValidAtom = atom(get => {
  const user = get(authUserAtom);
  const isAuthenticated = get(authIsAuthenticatedAtom);

  if (!isAuthenticated || !user) {
    return false;
  }

  return user.sessionExpiry > Date.now();
});

// Helper functions
function getUserPermissions(roles: Role[]): Permission[] {
  const permissions = new Set<Permission>();
  roles.forEach(role => {
    ROLE_PERMISSIONS[role]?.forEach(permission => {
      permissions.add(permission);
    });
  });
  return Array.from(permissions);
}

function generateSessionToken(): string {
  return `session_${Date.now()}_${Math.random().toString(36).substring(2, 15)}`;
}

// Mock Authentication (replace with actual implementation)
async function mockAuthenticate(
  username: string,
  password: string
): Promise<Omit<User, 'permissions' | 'lastLogin' | 'sessionExpiry'> | null> {
  // Simulate API delay
  await new Promise(resolve => setTimeout(resolve, 500));

  // Mock users database
  const users = {
    admin: { password: 'admin123', roles: ['admin'] as Role[] },
    operator: { password: 'op123', roles: ['operator'] as Role[] },
    viewer: { password: 'view123', roles: ['viewer'] as Role[] },
  };

  const user = users[username as keyof typeof users];

  if (user && user.password === password) {
    return {
      id: `user_${username}`,
      username,
      roles: user.roles,
    };
  }

  return null;
}

// Write-only action atoms
export const authLoginAtom = atom(
  null,
  async (get, set, { username, password }: { username: string; password: string }) => {
    try {
      const mockUser = await mockAuthenticate(username, password);

      if (mockUser) {
        const sessionToken = generateSessionToken();
        const user: User = {
          ...mockUser,
          permissions: getUserPermissions(mockUser.roles),
          lastLogin: Date.now(),
          sessionExpiry: Date.now() + 8 * 60 * 60 * 1000, // 8 hours
        };

        set(authUserAtom, user);
        set(authIsAuthenticatedAtom, true);
        set(authSessionTokenAtom, sessionToken);

        // Store token for API calls
        localStorage.setItem('agent_admin_token', sessionToken);

        return true;
      }

      return false;
    } catch (error) {
      console.error('Login failed:', error);
      return false;
    }
  }
);

export const authLogoutAtom = atom(null, (get, set) => {
  localStorage.removeItem('agent_admin_token');
  set(authUserAtom, null);
  set(authIsAuthenticatedAtom, false);
  set(authSessionTokenAtom, null);
});

export const authRefreshSessionAtom = atom(null, async (get, set) => {
  const user = get(authUserAtom);
  const sessionToken = get(authSessionTokenAtom);

  if (!user || !sessionToken) {
    return false;
  }

  // Check if session needs refresh (within 1 hour of expiry)
  const oneHour = 60 * 60 * 1000;
  if (user.sessionExpiry - Date.now() < oneHour) {
    try {
      // Mock session refresh - replace with actual service
      const newExpiry = Date.now() + 8 * 60 * 60 * 1000;
      const updatedUser = { ...user, sessionExpiry: newExpiry };

      set(authUserAtom, updatedUser);
      return true;
    } catch {
      // Logout on refresh failure
      set(authUserAtom, null);
      set(authIsAuthenticatedAtom, false);
      set(authSessionTokenAtom, null);
      return false;
    }
  }

  return true;
});

// Legacy permissions setters (from auth.ts - for backward compatibility)
export const authSetLegacyPermissionsAtom = atom(
  null,
  (get, set, incoming: Partial<Record<string, PermissionEntry>>) => {
    set(authLegacyPermissionsAtom, prev => ({
      ...Object.fromEntries(
        Object.entries(defaultPermissions).map(([key, value]) => [key, { ...value }])
      ),
      ...Object.fromEntries(Object.entries(prev).map(([key, value]) => [key, { ...value }])),
      ...Object.fromEntries(
        Object.entries(incoming ?? {}).map(([key, value]) => [
          key,
          { ...defaultPermissions[key], ...value },
        ])
      ),
    }));
  }
);

// Derived permission checks
export const authHasPermissionAtom = atom(get => (permission: Permission): boolean => {
  const user = get(authUserAtom);
  const isAuthenticated = get(authIsAuthenticatedAtom);
  return isAuthenticated && !!user?.permissions.includes(permission);
});

export const authHasRoleAtom = atom(get => (role: Role): boolean => {
  const user = get(authUserAtom);
  const isAuthenticated = get(authIsAuthenticatedAtom);
  return isAuthenticated && !!user?.roles.includes(role);
});

// Legacy permission checks (from auth.ts)
export const authCanReadAtom = atom(get => (domain: PermissionKey): boolean => {
  const permissions = get(authLegacyPermissionsAtom);
  const entry = permissions[domain];
  return entry?.read ?? defaultPermissions[domain]?.read ?? true;
});

export const authCanWriteAtom = atom(get => (domain: PermissionKey): boolean => {
  const permissions = get(authLegacyPermissionsAtom);
  const entry = permissions[domain];
  return entry?.write ?? defaultPermissions[domain]?.write ?? false;
});

// Derived permissions helper
export const authPermissionsAtom = atom(get => {
  const hasPermission = get(authHasPermissionAtom);
  const hasRole = get(authHasRoleAtom);
  const canRead = get(authCanReadAtom);
  const canWrite = get(authCanWriteAtom);

  return {
    hasPermission,
    hasRole,
    canRead,
    canWrite,
    canReadResource: (resource: 'config' | 'metrics' | 'status') =>
      hasPermission(`${resource}:read` as Permission),
    canWriteResource: (resource: 'config') => hasPermission(`${resource}:write` as Permission),
    canControl: (action: 'restart' | 'stop') => hasPermission(`agent:${action}` as Permission),
  };
});
