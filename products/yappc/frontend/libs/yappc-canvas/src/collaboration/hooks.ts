import { useState, useEffect, useCallback, useRef } from 'react';

import {
  mockCollaborationServer,
} from './server';

import type {
  CanvasPermission,
  ExtendedShareToken,
  CreatePermissionRequest,
  CollaborationServer} from './server';
import type { UserRole, PermissionScope } from '../schemas/permission-schemas';

// Permission management hook
/**
 *
 */
export interface UsePermissionsConfig {
  canvasId: string;
  userId?: string;
  server?: CollaborationServer;
}

/**
 *
 */
export interface UsePermissionsReturn {
  // Current permissions
  userPermissions: CanvasPermission[];
  canvasPermissions: CanvasPermission[];
  loading: boolean;
  error: string | null;
  
  // Permission checks
  checkPermission: (action: string) => boolean;
  hasRole: (role: UserRole) => boolean;
  
  // Permission management
  createPermission: (request: Omit<CreatePermissionRequest, 'canvasId'>) => Promise<CanvasPermission>;
  updatePermission: (permissionId: string, updates: Partial<CanvasPermission>) => Promise<void>;
  deletePermission: (permissionId: string) => Promise<void>;
  
  // Utilities
  refresh: () => Promise<void>;
}

export const usePermissions = ({
  canvasId,
  userId,
  server = mockCollaborationServer,
}: UsePermissionsConfig): UsePermissionsReturn => {
  const [userPermissions, setUserPermissions] = useState<CanvasPermission[]>([]);
  const [canvasPermissions, setCanvasPermissions] = useState<CanvasPermission[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  
  const currentUserId = useRef(userId || 'anonymous');

  const checkPermission = useCallback(
    (action: string): boolean => {
      if (!currentUserId.current || !canvasId) return false;
      return server.checkPermission(currentUserId.current, canvasId, action)
        .then(allowed => allowed)
        .catch(() => false) as unknown; // Simplified for synchronous usage
    },
    [canvasId, server]
  );

  const hasRole = useCallback(
    (role: UserRole): boolean => {
      return userPermissions.some(p => p.canvasId === canvasId && p.role === role);
    },
    [userPermissions, canvasId]
  );

  // Async permission check
  const checkPermissionAsync = useCallback(
    async (action: string): Promise<boolean> => {
      if (!currentUserId.current || !canvasId) return false;
      try {
        return await server.checkPermission(currentUserId.current, canvasId, action);
      } catch (err) {
        console.error('Permission check failed:', err);
        return false;
      }
    },
    [canvasId, server]
  );

  const createPermission = useCallback(
    async (request: Omit<CreatePermissionRequest, 'canvasId'>): Promise<CanvasPermission> => {
      try {
        const permission = await server.createPermission({
          ...request,
          canvasId,
        });
        setCanvasPermissions(prev => [...prev, permission]);
        return permission;
      } catch (err) {
        const message = err instanceof Error ? err.message : 'Failed to create permission';
        setError(message);
        throw err;
      }
    },
    [canvasId, server]
  );

  const updatePermission = useCallback(
    async (permissionId: string, updates: Partial<CanvasPermission>): Promise<void> => {
      try {
        const updated = await server.updatePermission(permissionId, updates);
        setCanvasPermissions(prev =>
          prev.map(p => (p.id === permissionId ? updated : p))
        );
        if (updated.userId === currentUserId.current) {
          setUserPermissions(prev =>
            prev.map(p => (p.id === permissionId ? updated : p))
          );
        }
      } catch (err) {
        const message = err instanceof Error ? err.message : 'Failed to update permission';
        setError(message);
        throw err;
      }
    },
    [server]
  );

  const deletePermission = useCallback(
    async (permissionId: string): Promise<void> => {
      try {
        await server.deletePermission(permissionId);
        setCanvasPermissions(prev => prev.filter(p => p.id !== permissionId));
        setUserPermissions(prev => prev.filter(p => p.id !== permissionId));
      } catch (err) {
        const message = err instanceof Error ? err.message : 'Failed to delete permission';
        setError(message);
        throw err;
      }
    },
    [server]
  );

  const refresh = useCallback(async (): Promise<void> => {
    if (!canvasId) return;
    
    setLoading(true);
    setError(null);
    
    try {
      const [canvasPerms, userPerms] = await Promise.all([
        server.getCanvasPermissions(canvasId),
        currentUserId.current
          ? server.getUserPermissions(currentUserId.current)
          : Promise.resolve([]),
      ]);
      
      setCanvasPermissions(canvasPerms);
      setUserPermissions(userPerms.filter(p => p.canvasId === canvasId));
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to load permissions';
      setError(message);
    } finally {
      setLoading(false);
    }
  }, [canvasId, server]);

  // Load permissions on mount and when dependencies change
  useEffect(() => {
    refresh();
  }, [refresh]);

  // Update userId reference
  useEffect(() => {
    currentUserId.current = userId || 'anonymous';
  }, [userId]);

  return {
    userPermissions,
    canvasPermissions,
    loading,
    error,
    checkPermission,
    hasRole,
    createPermission,
    updatePermission,
    deletePermission,
    refresh,
  };
};

// Share token management hook
/**
 *
 */
export interface UseShareTokensConfig {
  canvasId: string;
  server?: CollaborationServer;
}

/**
 *
 */
export interface UseShareTokensReturn {
  tokens: ExtendedShareToken[];
  loading: boolean;
  error: string | null;
  
  createShareToken: (permissions: PermissionScope, expiresAt?: Date) => Promise<ExtendedShareToken>;
  validateToken: (token: string) => Promise<ExtendedShareToken | null>;
  deleteToken: (tokenId: string) => Promise<void>;
  refresh: () => Promise<void>;
}

export const useShareTokens = ({
  canvasId,
  server = mockCollaborationServer,
}: UseShareTokensConfig): UseShareTokensReturn => {
  const [tokens, setTokens] = useState<ExtendedShareToken[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const createShareToken = useCallback(
    async (permissions: PermissionScope, expiresAt?: Date): Promise<ExtendedShareToken> => {
      try {
        setError(null);
        const token = await server.createShareToken(canvasId, permissions, expiresAt);
        setTokens(prev => [...prev, token]);
        return token;
      } catch (err) {
        const message = err instanceof Error ? err.message : 'Failed to create share token';
        setError(message);
        throw err;
      }
    },
    [canvasId, server]
  );

  const validateToken = useCallback(
    async (token: string): Promise<ExtendedShareToken | null> => {
      try {
        setError(null);
        return await server.validateShareToken(token);
      } catch (err) {
        const message = err instanceof Error ? err.message : 'Failed to validate token';
        setError(message);
        return null;
      }
    },
    [server]
  );

  const deleteToken = useCallback(
    async (tokenId: string): Promise<void> => {
      try {
        setError(null);
        await server.deleteShareToken(tokenId);
        setTokens(prev => prev.filter(t => t.id !== tokenId));
      } catch (err) {
        const message = err instanceof Error ? err.message : 'Failed to delete token';
        setError(message);
        throw err;
      }
    },
    [server]
  );

  const refresh = useCallback(async (): Promise<void> => {
    // Note: This would require additional server method to list tokens by canvas
    // For now, tokens are managed locally after creation
    setLoading(false);
  }, []);

  return {
    tokens,
    loading,
    error,
    createShareToken,
    validateToken,
    deleteToken,
    refresh,
  };
};

// User presence hook  
/**
 *
 */
export interface UseUserPresenceConfig {
  canvasId: string;
  userId?: string;
  server?: CollaborationServer;
  updateInterval?: number;
}

/**
 *
 */
export interface UserPresence {
  userId: string;
  status: 'online' | 'offline' | 'away';
  lastSeen: Date;
}

/**
 *
 */
export interface UseUserPresenceReturn {
  activeUsers: string[];
  userPresence: Map<string, UserPresence>;
  loading: boolean;
  error: string | null;
  
  setUserStatus: (status: 'online' | 'offline' | 'away') => void;
  refresh: () => Promise<void>;
}

export const useUserPresence = ({
  canvasId,
  userId,
  server = mockCollaborationServer,
  updateInterval = 30000, // 30 seconds
}: UseUserPresenceConfig): UseUserPresenceReturn => {
  const [activeUsers, setActiveUsers] = useState<string[]>([]);
  const [userPresence, setUserPresence] = useState<Map<string, UserPresence>>(new Map());
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  
  const intervalRef = useRef<NodeJS.Timeout>();
  const currentUserId = useRef(userId || 'anonymous');

  const setUserStatus = useCallback(
    (status: 'online' | 'offline' | 'away') => {
      if (!currentUserId.current) return;
      
      // Update mock server if available
      if ('setUserStatus' in server) {
        (server as unknown).setUserStatus(currentUserId.current, status);
      }
      
      // Update local state
      setUserPresence(prev => {
        const updated = new Map(prev);
        updated.set(currentUserId.current, {
          userId: currentUserId.current,
          status,
          lastSeen: new Date(),
        });
        return updated;
      });
    },
    [server]
  );

  const refresh = useCallback(async (): Promise<void> => {
    if (!canvasId) return;
    
    try {
      setError(null);
      const users = await server.getActiveUsers(canvasId);
      setActiveUsers(users);
      
      // Get presence for each user
      const presencePromises = users.map(async (uid) => {
        const presence = await server.getUserPresence(uid);
        return { uid, presence: { ...presence, userId: uid } };
      });
      
      const presenceResults = await Promise.all(presencePromises);
      const presenceMap = new Map<string, UserPresence>();
      
      presenceResults.forEach(({ uid, presence }) => {
        presenceMap.set(uid, presence);
      });
      
      setUserPresence(presenceMap);
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to load user presence';
      setError(message);
    } finally {
      setLoading(false);
    }
  }, [canvasId, server]);

  // Set up periodic refresh
  useEffect(() => {
    refresh();
    
    intervalRef.current = setInterval(refresh, updateInterval);
    
    return () => {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
      }
    };
  }, [refresh, updateInterval]);

  // Update userId reference and mark as active
  useEffect(() => {
    currentUserId.current = userId || 'anonymous';
    
    if (userId && canvasId && 'addActiveUser' in server) {
      (server as unknown).addActiveUser(canvasId, userId);
      setUserStatus('online');
    }
    
    return () => {
      if (userId && canvasId && 'removeActiveUser' in server) {
        (server as unknown).removeActiveUser(canvasId, userId);
      }
    };
  }, [userId, canvasId, server, setUserStatus]);

  return {
    activeUsers,
    userPresence,
    loading,
    error,
    setUserStatus,
    refresh,
  };
};