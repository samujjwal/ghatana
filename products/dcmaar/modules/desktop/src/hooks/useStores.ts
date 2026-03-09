/**
 * Backward compatible hooks for Zustand → Jotai migration
 * Provides same API as old stores for smooth transition
 */

import { useAtomValue, useSetAtom, useAtom } from 'jotai';
import {
  // Extension atoms
  extensionConnectedAtom,
  extensionLatencyAtom,
  extensionEventsAtom,
  extensionRecentEventsAtom,
  extensionConfigAtom,
  addExtensionEventsAtom,
  clearExtensionEventsAtom,
  updateExtensionConfigAtom,
  type ExtensionEvent,
  type ExtensionConfig,

  // Agent atoms
  agentStatusAtom,
  agentMessagesAtom,
  agentContextAtom,
  agentCapabilitiesAtom,
  agentErrorAtom,
  addAgentMessageAtom,
  clearAgentMessagesAtom,
  setAgentStatusAtom,
  updateAgentContextAtom,
  setAgentErrorAtom,
  type AgentStatus,
  type Message,
  type AgentContext,
  type AgentCapability,

  // Auth atoms
  authUserAtom,
  authSessionTokenAtom,
  authIsAuthenticatedAtom,
  authLoginAtom,
  authLogoutAtom,
  authRefreshSessionAtom,
  authHasPermissionAtom,
  authHasRoleAtom,
  authCanReadAtom,
  authCanWriteAtom,
  type User,
  type Role,
  type Permission,
  type PermissionKey,
} from '../atoms';

/**
 * useExtensionStore - Backward compatible API
 * Maps Zustand store to Jotai atoms
 */
export function useExtensionStore<T>(
  selector?: (state: ExtensionStoreState) => T
): T extends undefined ? ExtensionStoreState : T {
  const connected = useAtomValue(extensionConnectedAtom);
  const latency = useAtomValue(extensionLatencyAtom);
  const events = useAtomValue(extensionEventsAtom);
  const recentEvents = useAtomValue(extensionRecentEventsAtom);
  const config = useAtomValue(extensionConfigAtom);

  const addEvents = useSetAtom(addExtensionEventsAtom);
  const clearEvents = useSetAtom(clearExtensionEventsAtom);
  const updateConfig = useSetAtom(updateExtensionConfigAtom);

  const state: ExtensionStoreState = {
    connected,
    latency,
    events,
    recentEvents,
    config,
    addEvents,
    clearEvents,
    updateConfig,
  };

  if (selector) {
    return selector(state) as any;
  }

  return state as any;
}

interface ExtensionStoreState {
  connected: boolean;
  latency: number | null;
  events: ExtensionEvent[];
  recentEvents: ExtensionEvent[];
  config: ExtensionConfig;
  addEvents: (events: ExtensionEvent[]) => void;
  clearEvents: () => void;
  updateConfig: (config: Partial<ExtensionConfig>) => void;
}

/**
 * useAgentStore - Backward compatible API
 * Maps Zustand store to Jotai atoms
 */
export function useAgentStore<T>(
  selector?: (state: AgentStoreState) => T
): T extends undefined ? AgentStoreState : T {
  const status = useAtomValue(agentStatusAtom);
  const messages = useAtomValue(agentMessagesAtom);
  const context = useAtomValue(agentContextAtom);
  const capabilities = useAtomValue(agentCapabilitiesAtom);
  const error = useAtomValue(agentErrorAtom);

  const addMessage = useSetAtom(addAgentMessageAtom);
  const clearMessages = useSetAtom(clearAgentMessagesAtom);
  const setStatus = useSetAtom(setAgentStatusAtom);
  const updateContext = useSetAtom(updateAgentContextAtom);
  const setError = useSetAtom(setAgentErrorAtom);

  const state: AgentStoreState = {
    status,
    messages,
    context,
    capabilities,
    error,
    addMessage,
    clearMessages,
    setStatus,
    updateContext,
    setError,
  };

  if (selector) {
    return selector(state) as any;
  }

  return state as any;
}

interface AgentStoreState {
  status: AgentStatus;
  messages: Message[];
  context: AgentContext;
  capabilities: AgentCapability[];
  error: string | null;
  addMessage: (message: Message) => void;
  clearMessages: () => void;
  setStatus: (status: AgentStatus) => void;
  updateContext: (context: Partial<AgentContext>) => void;
  setError: (error: string | null) => void;
}

/**
 * useAuthStore - Backward compatible API (from auth.tsx)
 * Maps Zustand store to Jotai atoms
 */
export function useAuthStore<T>(
  selector?: (state: AuthStoreState) => T
): T extends undefined ? AuthStoreState : T {
  const user = useAtomValue(authUserAtom);
  const isAuthenticated = useAtomValue(authIsAuthenticatedAtom);
  const sessionToken = useAtomValue(authSessionTokenAtom);

  const login = useSetAtom(authLoginAtom);
  const logout = useSetAtom(authLogoutAtom);
  const refreshSession = useSetAtom(authRefreshSessionAtom);
  const hasPermission = useAtomValue(authHasPermissionAtom);
  const hasRole = useAtomValue(authHasRoleAtom);

  const state: AuthStoreState = {
    user,
    isAuthenticated,
    sessionToken,
    login: async (username: string, password: string) => {
      return await login({ username, password });
    },
    logout: () => logout(),
    refreshSession: async () => {
      return await refreshSession();
    },
    hasPermission,
    hasRole,
    isSessionValid: () => {
      return isAuthenticated && !!user && user.sessionExpiry > Date.now();
    },
  };

  if (selector) {
    return selector(state) as any;
  }

  return state as any;
}

interface AuthStoreState {
  user: User | null;
  isAuthenticated: boolean;
  sessionToken: string | null;
  login: (username: string, password: string) => Promise<boolean>;
  logout: () => void;
  refreshSession: () => Promise<boolean>;
  hasPermission: (permission: Permission) => boolean;
  hasRole: (role: Role) => boolean;
  isSessionValid: () => boolean;
}

/**
 * usePermissions - Backward compatible hook (from auth.tsx)
 */
export function usePermissions() {
  const hasPermission = useAtomValue(authHasPermissionAtom);
  const hasRole = useAtomValue(authHasRoleAtom);
  const isAuthenticated = useAtomValue(authIsAuthenticatedAtom);
  const user = useAtomValue(authUserAtom);

  return {
    hasPermission,
    hasRole,
    isAuthenticated,
    user,
    canRead: (resource: 'config' | 'metrics' | 'status') =>
      hasPermission(`${resource}:read` as Permission),
    canWrite: (resource: 'config') => hasPermission(`${resource}:write` as Permission),
    canControl: (action: 'restart' | 'stop') => hasPermission(`agent:${action}` as Permission),
  };
}

/**
 * useSession - Backward compatible hook (from auth.tsx)
 */
export function useSession() {
  const [user] = useAtom(authUserAtom);
  const [isAuthenticated] = useAtom(authIsAuthenticatedAtom);
  const login = useSetAtom(authLoginAtom);
  const logout = useSetAtom(authLogoutAtom);
  const refreshSession = useSetAtom(authRefreshSessionAtom);

  const isSessionValid = () => {
    return isAuthenticated && !!user && user.sessionExpiry > Date.now();
  };

  return {
    login: async (username: string, password: string) => {
      return await login({ username, password });
    },
    logout: () => logout(),
    refreshSession: async () => {
      return await refreshSession();
    },
    isSessionValid,
    user,
  };
}

/**
 * Legacy permissions hook (from auth.ts) - for backward compatibility
 */
export function useLegacyPermissions() {
  const canRead = useAtomValue(authCanReadAtom);
  const canWrite = useAtomValue(authCanWriteAtom);

  return {
    canRead,
    canWrite,
  };
}

// Re-export types for convenience
export type {
  ExtensionEvent,
  ExtensionConfig,
  AgentStatus,
  Message,
  AgentContext,
  AgentCapability,
  User,
  Role,
  Permission,
  PermissionKey,
};
