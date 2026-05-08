/**
 * Pre-built Atoms Library
 *
 * Collection of commonly used global state atoms.
 *
 * @module state/atoms
 */

import type {
  User as StoreUser,
  Workspace as StoreWorkspace,
  Project as StoreProject,
  Task as StoreTask,
} from 'yappc-core/types/common';
import { atom } from 'jotai';

import {
  createAtom,
  createPersistentAtom,
  createDerivedAtom,
  createWritableDerivedAtom,
} from '@ghatana/state';

async function parseJsonResponse<T>(
  response: Response,
  context: string
): Promise<T> {
  const raw = await response.text();

  if (!raw) {
    throw new Error(`${context} returned an empty response`);
  }

  try {
    return JSON.parse(raw) as T;
  } catch (error) {
    const detail = error instanceof Error ? error.message : String(error);
    throw new Error(`${context} returned invalid JSON: ${detail}`);
  }
}

// ============================================================================
// Theme State
// ============================================================================

export const themeAtom = createPersistentAtom(
  'theme',
  'base' as 'base' | 'brand' | 'workspace' | 'app',
  {
    storage: 'sessionStorage',
    storageKey: 'theme',
  },
  'Current theme layer'
);

export const darkModeAtom = createPersistentAtom(
  'darkMode',
  false,
  {
    storage: 'sessionStorage',
    storageKey: 'darkMode',
  },
  'Dark mode enabled'
);

export const colorSchemeAtom = createDerivedAtom(
  'colorScheme',
  (get) => {
    const darkMode = get(darkModeAtom);
    return darkMode ? 'dark' : 'light';
  }
);

// ============================================================================
// User State
// ============================================================================

/**
 *
 */
export interface User {
  id: string;
  name: string;
  email: string;
  avatar?: string;
  role: 'admin' | 'user' | 'guest';
}

export const userAtom = createPersistentAtom<User | null>(
  'user',
  null,
  {
    storage: 'sessionStorage',
    storageKey: 'user',
  },
  'Current user information'
);

export const isAuthenticatedAtom = createDerivedAtom(
  'isAuthenticated',
  (get) => {
    const user = get(userAtom);
    return user !== null;
  }
);

export const userRoleAtom = createDerivedAtom(
  'userRole',
  (get) => {
    const user = get(userAtom);
    return user?.role || 'guest';
  }
);

// ============================================================================
// User Preferences
// ============================================================================

/**
 *
 */
export interface UserPreferences {
  language: string;
  timezone: string;
  dateFormat: 'MM/DD/YYYY' | 'DD/MM/YYYY' | 'YYYY-MM-DD';
  notifications: boolean;
  soundEnabled: boolean;
  compactMode: boolean;
}

export const userPreferencesAtom = createPersistentAtom<UserPreferences>(
  'userPreferences',
  {
    language: 'en',
    timezone: 'UTC',
    dateFormat: 'MM/DD/YYYY',
    notifications: true,
    soundEnabled: true,
    compactMode: false,
  },
  {
    storage: 'sessionStorage',
  },
  'User preferences and settings'
);

// ============================================================================
// UI State
// ============================================================================

export const sidebarOpenAtom = createPersistentAtom(
  'sidebarOpen',
  true,
  {
    storage: 'sessionStorage',
  },
  'Sidebar open/closed state'
);

export const sidebarCollapsedAtom = createPersistentAtom(
  'sidebarCollapsed',
  false,
  {
    storage: 'sessionStorage',
  },
  'Sidebar collapsed state'
);

export const activeModalAtom = createAtom<string | null>(
  'activeModal',
  null
);

export const modalStackAtom = createAtom<string[]>(
  'modalStack',
  []
);

export const loadingAtom = createAtom<boolean>(
  'loading',
  false
);

export const loadingMessageAtom = createAtom<string>(
  'loadingMessage',
  ''
);

export const errorAtom = createAtom<Error | null>(
  'error',
  null
);

export const notificationsAtom = createAtom<
  Array<{
    id: string;
    type: 'info' | 'success' | 'warning' | 'error';
    message: string;
    duration?: number;
  }>
>('notifications', [], 'Active notifications queue');

// ============================================================================
// Canvas State
// ============================================================================

/**
 *
 */
export interface CanvasState {
  selectedNodeIds: string[];
  hoveredNodeId: string | null;
  clipboard: unknown;
  zoom: number;
  pan: { x: number; y: number };
  mode: 'select' | 'pan' | 'draw' | 'edit';
}

export const canvasStateAtom = createAtom<CanvasState>(
  'canvasState',
  {
    selectedNodeIds: [],
    hoveredNodeId: null,
    clipboard: null,
    zoom: 1,
    pan: { x: 0, y: 0 },
    mode: 'select',
  },
  'Canvas editor state'
);

export const canvasHistoryAtom = createAtom<
  Array<{ action: string; timestamp: Date }>
>('canvasHistory', []);

export const canvasDirtyAtom = createAtom<boolean>(
  'canvasDirty',
  false
);

// ============================================================================
// Form State
// ============================================================================

/**
 *
 */
export interface FormState {
  [formId: string]: {
    values: Record<string, unknown>;
    errors: Record<string, string>;
    touched: Record<string, boolean>;
    isSubmitting: boolean;
    isDirty: boolean;
  };
}

export const formsAtom = createAtom<FormState>(
  'forms',
  {}
);

// ============================================================================
// Navigation State
// ============================================================================

export const currentRouteAtom = createAtom<string>(
  'currentRoute',
  '/'
);

export const navigationHistoryAtom = createAtom<string[]>(
  'navigationHistory',
  []
);

export const breadcrumbsAtom = createAtom<
  Array<{ label: string; path: string }>
>('breadcrumbs', []);

// ============================================================================
// Search State
// ============================================================================

export const searchQueryAtom = createAtom<string>(
  'searchQuery',
  ''
);

export const searchResultsAtom = createAtom<unknown[]>(
  'searchResults',
  []
);

export const searchLoadingAtom = createAtom<boolean>(
  'searchLoading',
  false
);

// ============================================================================
// Performance Metrics
// ============================================================================

/**
 *
 */
export interface PerformanceMetrics {
  fps: number;
  renderTime: number;
  memoryUsage: number;
  lastUpdate: Date;
}

export const performanceMetricsAtom = createAtom<PerformanceMetrics>(
  'performanceMetrics',
  {
    fps: 60,
    renderTime: 0,
    memoryUsage: 0,
    lastUpdate: new Date(),
  },
  'Performance monitoring metrics'
);

// ============================================================================
// Feature Flags
// ============================================================================

export const featureFlagsAtom = createPersistentAtom<
  Record<string, boolean>
>(
  'featureFlags',
  {},
  {
    storage: 'sessionStorage',
  },
  'Feature flags configuration'
);

// ============================================================================
// Mobile Settings
// ============================================================================

export interface MobileSettings {
  notifications: {
    enabled: boolean;
    sound: boolean;
    badge: boolean;
  };
  offline: {
    enabled: boolean;
    syncOnWifi: boolean;
  };
  theme: 'light' | 'dark' | 'auto';
  language: string;
}

const createDefaultMobileSettings = (): MobileSettings => ({
  notifications: {
    enabled: false,
    sound: true,
    badge: true,
  },
  offline: {
    enabled: false,
    syncOnWifi: true,
  },
  theme: 'auto',
  language: 'en',
});

export const mobileSettingsAtom = createPersistentAtom<MobileSettings>(
  'mobile:settings',
  createDefaultMobileSettings(),
  {
    storage: 'sessionStorage',
  },
  'Mobile application persistent settings'
);

// ============================================================================
// Legacy Store Compatibility
// ============================================================================

export type LegacyThemeMode = 'light' | 'dark' | 'system';
type StorePlatform = 'web' | 'desktop' | 'mobile';

export interface LegacyAuthState {
  user: StoreUser | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  error: string | null;
}

const createDefaultLegacyAuthState = (): LegacyAuthState => ({
  user: null,
  isAuthenticated: false,
  isLoading: false,
  error: null,
});

export interface LegacyModalState {
  isOpen: boolean;
  modalId: string | null;
  data?: Record<string, unknown>;
}

const createDefaultLegacyModalState = (): LegacyModalState => ({
  isOpen: false,
  modalId: null,
  data: undefined,
});

export interface LegacyDrawerState {
  isOpen: boolean;
  drawerId: string | null;
  position: 'left' | 'right' | 'top' | 'bottom';
  data?: Record<string, unknown>;
}

const createDefaultLegacyDrawerState = (): LegacyDrawerState => ({
  isOpen: false,
  drawerId: null,
  position: 'right',
  data: undefined,
});

export interface LegacyNotification {
  id: string;
  type: 'success' | 'error' | 'warning' | 'info';
  message: string;
  duration?: number;
}

// Auth compatibility
export const storeAuthStateAtom = createAtom<LegacyAuthState>(
  'store:authState',
  createDefaultLegacyAuthState()
);

export const storeAccessTokenAtom = createPersistentAtom<
  string | null
>('store:accessToken', null, {
  storage: 'localStorage',
  storageKey: 'access-token',
});

export const storeRefreshTokenAtom = createPersistentAtom<
  string | null
>('store:refreshToken', null, {
  storage: 'localStorage',
  storageKey: 'refresh-token',
});

export const storeUserAtom = createWritableDerivedAtom<
  StoreUser | null,
  [StoreUser | null],
  void
>(
  'store:user',
  (get) => get(storeAuthStateAtom).user,
  (get, set, user) => {
    const current = get(storeAuthStateAtom);
    set(storeAuthStateAtom, {
      ...current,
      user,
      isAuthenticated: user !== null,
    });
  }
);

export const storeIsLoggedInAtom = createDerivedAtom(
  'store:isLoggedIn',
  (get) => get(storeAuthStateAtom).isAuthenticated
);

export const storeAuthLoadingAtom = createWritableDerivedAtom<
  boolean,
  [boolean],
  void
>(
  'store:authLoading',
  (get) => get(storeAuthStateAtom).isLoading,
  (get, set, isLoading) => {
    const current = get(storeAuthStateAtom);
    set(storeAuthStateAtom, { ...current, isLoading });
  }
);

export const storeAuthErrorAtom = createWritableDerivedAtom<
  string | null,
  [string | null],
  void
>(
  'store:authError',
  (get) => get(storeAuthStateAtom).error,
  (get, set, error) => {
    const current = get(storeAuthStateAtom);
    set(storeAuthStateAtom, { ...current, error });
  }
);

export interface AuthState {
  user: StoreUser | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  error: string | null;
  token: string | null;
}

export const authStateAtom = atom(
  (get): AuthState => ({
    ...get(storeAuthStateAtom),
    token: get(storeAccessTokenAtom),
  }),
  (_get, set, nextState: AuthState) => {
    set(storeAuthStateAtom, {
      user: nextState.user,
      isAuthenticated: nextState.isAuthenticated,
      isLoading: nextState.isLoading,
      error: nextState.error,
    });
    set(storeAccessTokenAtom, nextState.token);
  }
);

export const authUserAtom = atom(
  (get) => get(storeAuthStateAtom).user,
  (get, set, user: StoreUser | null) => {
    const currentState = get(storeAuthStateAtom);
    const currentToken = get(storeAccessTokenAtom);
    set(storeAuthStateAtom, {
      ...currentState,
      user,
      isAuthenticated: Boolean(user && currentToken),
    });
  }
);

export const authTokenAtom = atom(
  (get) => get(storeAccessTokenAtom),
  (get, set, token: string | null) => {
    const currentState = get(storeAuthStateAtom);
    set(storeAccessTokenAtom, token);
    set(storeAuthStateAtom, {
      ...currentState,
      isAuthenticated: Boolean(currentState.user && token),
    });
  }
);

export const authLoadingAtom = atom(
  (get) => get(storeAuthStateAtom).isLoading,
  (get, set, isLoading: boolean) => {
    const currentState = get(storeAuthStateAtom);
    set(storeAuthStateAtom, { ...currentState, isLoading });
  }
);

export const authErrorAtom = atom(
  (get) => get(storeAuthStateAtom).error,
  (get, set, error: string | null) => {
    const currentState = get(storeAuthStateAtom);
    set(storeAuthStateAtom, { ...currentState, error });
  }
);

export const storeLoginAtom = createWritableDerivedAtom<
  null,
  [{ email: string; password: string }],
  Promise<void>
>(
  'store:auth:login',
  () => null,
  async (_get, set, credentials) => {
    set(storeAuthLoadingAtom, true);
    set(storeAuthErrorAtom, null);

    try {
      const response = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(credentials),
      });

      if (!response.ok) {
        throw new Error('Login failed');
      }

      const data = await parseJsonResponse<Record<string, unknown>>(
        response,
        'legacy store login'
      );
      set(storeAccessTokenAtom, (data.accessToken as string | null | undefined) ?? null);
      set(storeRefreshTokenAtom, (data.refreshToken as string | null | undefined) ?? null);
      set(storeUserAtom, (data.user as StoreUser | null | undefined) ?? null);
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Login failed';
      set(storeAuthErrorAtom, message);
    } finally {
      set(storeAuthLoadingAtom, false);
    }
  }
);

export const storeLogoutAtom = createWritableDerivedAtom<
  null,
  [],
  void
>(
  'store:auth:logout',
  () => null,
  (_get, set) => {
    set(storeAccessTokenAtom, null);
    set(storeRefreshTokenAtom, null);
    set(storeAuthStateAtom, createDefaultLegacyAuthState());
  }
);

export const storeRefreshTokensAtom = createWritableDerivedAtom<
  null,
  [],
  Promise<void>
>(
  'store:auth:refreshTokens',
  () => null,
  async (get, set) => {
    const refreshToken = get(storeRefreshTokenAtom);
    if (!refreshToken) {
      return;
    }

    try {
      const response = await fetch('/api/auth/refresh', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      });

      if (!response.ok) {
        throw new Error('Token refresh failed');
      }

      const data = await parseJsonResponse<Record<string, unknown>>(
        response,
        'legacy store token refresh'
      );
      set(storeAccessTokenAtom, (data.accessToken as string | null | undefined) ?? null);
      set(storeRefreshTokenAtom, (data.refreshToken as string | null | undefined) ?? null);
    } catch {
      set(storeLogoutAtom);
    }
  }
);

export const storeHasPermissionAtom = createDerivedAtom(
  'store:auth:hasPermission',
  (get) => (permission: string) => {
    const user = get(storeUserAtom);
    const permissions = (user as unknown as { permissions?: string[] } | null)
      ?.permissions;
    return permissions?.includes(permission) ?? false;
  }
);

export const storeHasRoleAtom = createDerivedAtom(
  'store:auth:hasRole',
  (get) => (role: string) => {
    const user = get(storeUserAtom);
    return user?.role === role;
  }
);

// General store state
export const storeThemeAtom = createPersistentAtom<
  'light' | 'dark'
>('store:theme', 'light', {
  storage: 'sessionStorage',
});

export const storeThemeModeAtom = createPersistentAtom<LegacyThemeMode>(
  'store:themeMode',
  'system',
  {
    storage: 'sessionStorage',
    storageKey: 'theme-mode',
  }
);

export const storeSidebarOpenAtom = createPersistentAtom<boolean>(
  'store:sidebarOpen',
  true,
  {
    storage: 'sessionStorage',
    storageKey: 'sidebar-open',
  }
);

export const storePlatformAtom = createAtom<StorePlatform>(
  'store:platform',
  'web'
);

export const storeWorkspacesAtom = createAtom<StoreWorkspace[]>(
  'store:workspaces',
  []
);

export const storeCurrentWorkspaceIdAtom = createPersistentAtom<
  string | null
>('store:currentWorkspaceId', null, {
  storage: 'localStorage',
});

export const storeCurrentWorkspaceAtom = createWritableDerivedAtom<
  StoreWorkspace | null,
  [string | null],
  void
>(
  'store:currentWorkspace',
  (get) => {
    const workspaces = get(storeWorkspacesAtom);
    const currentId = get(storeCurrentWorkspaceIdAtom);
    return currentId
      ? (workspaces.find((workspace) => workspace.id === currentId) ?? null)
      : null;
  },
  (_get, set, workspaceId) => {
    set(storeCurrentWorkspaceIdAtom, workspaceId);
  }
);

export const storeProjectsAtom = createAtom<StoreProject[]>(
  'store:projects',
  []
);

export const storeCurrentProjectIdAtom = createPersistentAtom<
  string | null
>('store:currentProjectId', null, {
  storage: 'localStorage',
});

export const storeCurrentProjectAtom = createWritableDerivedAtom<
  StoreProject | null,
  [string | null],
  void
>(
  'store:currentProject',
  (get) => {
    const projects = get(storeProjectsAtom);
    const currentId = get(storeCurrentProjectIdAtom);
    return currentId
      ? (projects.find((project) => project.id === currentId) ?? null)
      : null;
  },
  (_get, set, projectId) => {
    set(storeCurrentProjectIdAtom, projectId);
  }
);

export const storeTasksAtom = createAtom<StoreTask[]>(
  'store:tasks',
  []
);

export const storeCurrentProjectTasksAtom = createDerivedAtom(
  'store:currentProjectTasks',
  (get) => {
    const tasks = get(storeTasksAtom);
    const currentProject = get(storeCurrentProjectAtom);
    return currentProject
      ? tasks.filter((task) => task.projectId === currentProject.id)
      : [];
  }
);

export const storeLoadingStatesAtom = createAtom<
  Record<string, boolean>
>('store:loadingStates', {});

export const storeIsLoadingAtom = createDerivedAtom(
  'store:isLoading',
  (get) => Object.values(get(storeLoadingStatesAtom)).some(Boolean)
);

export const storeSetLoadingAtom = createWritableDerivedAtom<
  null,
  [{ key: string; isLoading: boolean }],
  void
>(
  'store:setLoading',
  () => null,
  (get, set, payload) => {
    const current = get(storeLoadingStatesAtom);
    set(storeLoadingStatesAtom, {
      ...current,
      [payload.key]: payload.isLoading,
    });
  }
);

export const storeGlobalLoadingAtom = createAtom<boolean>(
  'store:globalLoading',
  false
);

export const storeNotificationsAtom = createAtom<
  LegacyNotification[]
>('store:notifications', []);

export const storeCommandPaletteOpenAtom = createAtom<boolean>(
  'store:commandPaletteOpen',
  false
);

export const storeSearchQueryAtom = createAtom<string>(
  'store:searchQuery',
  ''
);

export const storeSearchResultsAtom = createAtom<
  Array<Record<string, unknown>>
>('store:searchResults', []);

export const storeBreadcrumbsAtom = createAtom<
  Array<{ label: string; href?: string }>
>('store:breadcrumbs', []);

export const storePageTitleAtom = createAtom<string>(
  'store:pageTitle',
  ''
);

export const storeFullscreenAtom = createAtom<boolean>(
  'store:fullscreen',
  false
);

export const storeModalAtom = createAtom<LegacyModalState>(
  'store:modal',
  createDefaultLegacyModalState()
);

export const storeOpenModalAtom = createWritableDerivedAtom<
  null,
  [{ modalId: string; data?: Record<string, unknown> }],
  void
>(
  'store:openModal',
  () => null,
  (_get, set, payload) => {
    set(storeModalAtom, {
      isOpen: true,
      modalId: payload.modalId,
      data: payload.data,
    });
  }
);

export const storeCloseModalAtom = createWritableDerivedAtom<
  null,
  [],
  void
>(
  'store:closeModal',
  () => null,
  (_get, set) => {
    set(storeModalAtom, createDefaultLegacyModalState());
  }
);

export const storeDrawerAtom = createAtom<LegacyDrawerState>(
  'store:drawer',
  createDefaultLegacyDrawerState()
);

export const storeOpenDrawerAtom = createWritableDerivedAtom<
  null,
  [
    {
      drawerId: string;
      position?: LegacyDrawerState['position'];
      data?: Record<string, unknown>;
    },
  ],
  void
>(
  'store:openDrawer',
  () => null,
  (_get, set, payload) => {
    set(storeDrawerAtom, {
      isOpen: true,
      drawerId: payload.drawerId,
      position: payload.position ?? 'right',
      data: payload.data,
    });
  }
);

export const storeCloseDrawerAtom = createWritableDerivedAtom<
  null,
  [],
  void
>(
  'store:closeDrawer',
  () => null,
  (_get, set) => {
    set(storeDrawerAtom, createDefaultLegacyDrawerState());
  }
);

export const storeAddNotificationAtom = createWritableDerivedAtom<
  null,
  [Omit<LegacyNotification, 'id'>],
  void
>(
  'store:addNotification',
  () => null,
  (get, set, notification) => {
    const id = Date.now().toString();
    set(storeNotificationsAtom, [
      ...get(storeNotificationsAtom),
      { ...notification, id },
    ]);
  }
);

export const storeRemoveNotificationAtom =
  createWritableDerivedAtom<null, [string], void>(
    'store:removeNotification',
    () => null,
    (get, set, id) => {
      set(
        storeNotificationsAtom,
        get(storeNotificationsAtom).filter((existing) => existing.id !== id)
      );
    }
  );

// ============================================================================
// Export all atoms
// ============================================================================

export const allAtoms = {
  // Theme
  theme: themeAtom,
  darkMode: darkModeAtom,
  colorScheme: colorSchemeAtom,

  // User
  user: userAtom,
  isAuthenticated: isAuthenticatedAtom,
  userRole: userRoleAtom,
  userPreferences: userPreferencesAtom,

  // UI
  sidebarOpen: sidebarOpenAtom,
  sidebarCollapsed: sidebarCollapsedAtom,
  activeModal: activeModalAtom,
  modalStack: modalStackAtom,
  loading: loadingAtom,
  loadingMessage: loadingMessageAtom,
  error: errorAtom,
  notifications: notificationsAtom,

  // Canvas
  canvasState: canvasStateAtom,
  canvasHistory: canvasHistoryAtom,
  canvasDirty: canvasDirtyAtom,

  // Forms
  forms: formsAtom,

  // Navigation
  currentRoute: currentRouteAtom,
  navigationHistory: navigationHistoryAtom,
  breadcrumbs: breadcrumbsAtom,

  // Search
  searchQuery: searchQueryAtom,
  searchResults: searchResultsAtom,
  searchLoading: searchLoadingAtom,

  // Performance
  performanceMetrics: performanceMetricsAtom,

  // Features
  featureFlags: featureFlagsAtom,
  mobileSettings: mobileSettingsAtom,

  // Legacy Store Compatibility
  storeAuthState: storeAuthStateAtom,
  storeAccessToken: storeAccessTokenAtom,
  storeRefreshToken: storeRefreshTokenAtom,
  storeAuthLoading: storeAuthLoadingAtom,
  storeAuthError: storeAuthErrorAtom,
  storeLogin: storeLoginAtom,
  storeLogout: storeLogoutAtom,
  storeRefreshTokens: storeRefreshTokensAtom,
  storeHasPermission: storeHasPermissionAtom,
  storeHasRole: storeHasRoleAtom,
  storeTheme: storeThemeAtom,
  storeThemeMode: storeThemeModeAtom,
  storeSidebarOpen: storeSidebarOpenAtom,
  storePlatform: storePlatformAtom,
  storeUser: storeUserAtom,
  storeIsLoggedIn: storeIsLoggedInAtom,
  storeWorkspaces: storeWorkspacesAtom,
  storeCurrentWorkspaceId: storeCurrentWorkspaceIdAtom,
  storeCurrentWorkspace: storeCurrentWorkspaceAtom,
  storeProjects: storeProjectsAtom,
  storeCurrentProjectId: storeCurrentProjectIdAtom,
  storeCurrentProject: storeCurrentProjectAtom,
  storeTasks: storeTasksAtom,
  storeCurrentProjectTasks: storeCurrentProjectTasksAtom,
  storeLoadingStates: storeLoadingStatesAtom,
  storeIsLoading: storeIsLoadingAtom,
  storeSetLoading: storeSetLoadingAtom,
  storeGlobalLoading: storeGlobalLoadingAtom,
  storeNotifications: storeNotificationsAtom,
  storeCommandPaletteOpen: storeCommandPaletteOpenAtom,
  storeSearchQuery: storeSearchQueryAtom,
  storeSearchResults: storeSearchResultsAtom,
  storeBreadcrumbs: storeBreadcrumbsAtom,
  storePageTitle: storePageTitleAtom,
  storeFullscreen: storeFullscreenAtom,
  storeModal: storeModalAtom,
  storeOpenModal: storeOpenModalAtom,
  storeCloseModal: storeCloseModalAtom,
  storeDrawer: storeDrawerAtom,
  storeOpenDrawer: storeOpenDrawerAtom,
  storeCloseDrawer: storeCloseDrawerAtom,
  storeAddNotification: storeAddNotificationAtom,
  storeRemoveNotification: storeRemoveNotificationAtom,
};
