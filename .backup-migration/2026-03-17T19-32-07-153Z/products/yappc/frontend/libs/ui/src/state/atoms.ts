/**
 * Pre-built Atoms Library
 *
 * Collection of commonly used global state atoms.
 *
 * @module state/atoms
 */

import { StateManager } from './StateManager';
import type {
  User as StoreUser,
  Workspace as StoreWorkspace,
  Project as StoreProject,
  Task as StoreTask,
} from '@ghatana/yappc-types';

// ============================================================================
// Theme State
// ============================================================================

export const themeAtom = StateManager.createPersistentAtom(
  'theme',
  'base' as 'base' | 'brand' | 'workspace' | 'app',
  {
    description: 'Current theme layer',
    storage: 'local',
  }
);

export const darkModeAtom = StateManager.createPersistentAtom('darkMode', false, {
  description: 'Dark mode enabled',
  storage: 'local',
});

export const colorSchemeAtom = StateManager.createDerivedAtom(
  'colorScheme',
  (get) => {
    const darkMode = get(darkModeAtom);
    return darkMode ? 'dark' : 'light';
  },
  'Computed color scheme based on dark mode'
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

export const userAtom = StateManager.createPersistentAtom<User | null>(
  'user',
  null,
  {
    description: 'Current user information',
    storage: 'local',
  }
);

export const isAuthenticatedAtom = StateManager.createDerivedAtom(
  'isAuthenticated',
  (get) => {
    const user = get(userAtom);
    return user !== null;
  },
  'User authentication status'
);

export const userRoleAtom = StateManager.createDerivedAtom(
  'userRole',
  (get) => {
    const user = get(userAtom);
    return user?.role || 'guest';
  },
  'Current user role'
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

export const userPreferencesAtom = StateManager.createPersistentAtom<UserPreferences>(
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
    description: 'User preferences and settings',
    storage: 'local',
  }
);

// ============================================================================
// UI State
// ============================================================================

export const sidebarOpenAtom = StateManager.createPersistentAtom(
  'sidebarOpen',
  true,
  {
    description: 'Sidebar open/closed state',
    storage: 'local',
  }
);

export const sidebarCollapsedAtom = StateManager.createPersistentAtom(
  'sidebarCollapsed',
  false,
  {
    description: 'Sidebar collapsed state',
    storage: 'local',
  }
);

export const activeModalAtom = StateManager.createAtom<string | null>(
  'activeModal',
  null,
  'Currently active modal ID'
);

export const modalStackAtom = StateManager.createAtom<string[]>(
  'modalStack',
  [],
  'Stack of open modals'
);

export const loadingAtom = StateManager.createAtom<boolean>(
  'loading',
  false,
  'Global loading state'
);

export const loadingMessageAtom = StateManager.createAtom<string>(
  'loadingMessage',
  '',
  'Loading message text'
);

export const errorAtom = StateManager.createAtom<Error | null>(
  'error',
  null,
  'Global error state'
);

export const notificationsAtom = StateManager.createAtom<
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

export const canvasStateAtom = StateManager.createAtom<CanvasState>(
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

export const canvasHistoryAtom = StateManager.createAtom<
  Array<{ action: string; timestamp: Date }>
>('canvasHistory', [], 'Canvas action history');

export const canvasDirtyAtom = StateManager.createAtom<boolean>(
  'canvasDirty',
  false,
  'Canvas has unsaved changes'
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

export const formsAtom = StateManager.createAtom<FormState>(
  'forms',
  {},
  'Active forms state'
);

// ============================================================================
// Navigation State
// ============================================================================

export const currentRouteAtom = StateManager.createAtom<string>(
  'currentRoute',
  '/',
  'Current route path'
);

export const navigationHistoryAtom = StateManager.createAtom<string[]>(
  'navigationHistory',
  [],
  'Navigation history'
);

export const breadcrumbsAtom = StateManager.createAtom<
  Array<{ label: string; path: string }>
>('breadcrumbs', [], 'Navigation breadcrumbs');

// ============================================================================
// Search State
// ============================================================================

export const searchQueryAtom = StateManager.createAtom<string>(
  'searchQuery',
  '',
  'Global search query'
);

export const searchResultsAtom = StateManager.createAtom<unknown[]>(
  'searchResults',
  [],
  'Search results'
);

export const searchLoadingAtom = StateManager.createAtom<boolean>(
  'searchLoading',
  false,
  'Search in progress'
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

export const performanceMetricsAtom = StateManager.createAtom<PerformanceMetrics>(
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

export const featureFlagsAtom = StateManager.createPersistentAtom<Record<string, boolean>>(
  'featureFlags',
  {},
  {
    description: 'Feature flags configuration',
    storage: 'local',
  }
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

export const mobileSettingsAtom = StateManager.createPersistentAtom<MobileSettings>(
  'mobile:settings',
  createDefaultMobileSettings(),
  {
    description: 'Mobile application persistent settings',
    storage: 'local',
  }
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
export const storeAuthStateAtom = StateManager.createAtom<LegacyAuthState>(
  'store:authState',
  createDefaultLegacyAuthState(),
  'Legacy store authentication composite state'
);

export const storeAccessTokenAtom = StateManager.createPersistentAtom<string | null>(
  'store:accessToken',
  null,
  {
    description: 'Legacy store JWT access token',
    storage: 'local',
    storageKey: 'access-token',
  }
);

export const storeRefreshTokenAtom = StateManager.createPersistentAtom<string | null>(
  'store:refreshToken',
  null,
  {
    description: 'Legacy store refresh token',
    storage: 'local',
    storageKey: 'refresh-token',
  }
);

export const storeUserAtom = StateManager.createWritableDerivedAtom<StoreUser | null, [StoreUser | null], void>(
  'store:user',
  (get) => get(storeAuthStateAtom).user,
  (get, set, user) => {
    const current = get(storeAuthStateAtom);
    set(storeAuthStateAtom, {
      ...current,
      user,
      isAuthenticated: user !== null,
    });
  },
  'Legacy store current user accessor'
);

export const storeIsLoggedInAtom = StateManager.createDerivedAtom(
  'store:isLoggedIn',
  (get) => get(storeAuthStateAtom).isAuthenticated,
  'Legacy store authentication status'
);

export const storeAuthLoadingAtom = StateManager.createWritableDerivedAtom<boolean, [boolean], void>(
  'store:authLoading',
  (get) => get(storeAuthStateAtom).isLoading,
  (get, set, isLoading) => {
    const current = get(storeAuthStateAtom);
    set(storeAuthStateAtom, { ...current, isLoading });
  },
  'Legacy store auth loading state'
);

export const storeAuthErrorAtom = StateManager.createWritableDerivedAtom<string | null, [string | null], void>(
  'store:authError',
  (get) => get(storeAuthStateAtom).error,
  (get, set, error) => {
    const current = get(storeAuthStateAtom);
    set(storeAuthStateAtom, { ...current, error });
  },
  'Legacy store auth error state'
);

export const storeLoginAtom = StateManager.createWritableDerivedAtom<
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

      const data = await response.json();
      set(storeAccessTokenAtom, data.accessToken ?? null);
      set(storeRefreshTokenAtom, data.refreshToken ?? null);
      set(storeUserAtom, data.user ?? null);
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Login failed';
      set(storeAuthErrorAtom, message);
    } finally {
      set(storeAuthLoadingAtom, false);
    }
  },
  'Legacy store login action'
);

export const storeLogoutAtom = StateManager.createWritableDerivedAtom<null, [], void>(
  'store:auth:logout',
  () => null,
  (_get, set) => {
    set(storeAccessTokenAtom, null);
    set(storeRefreshTokenAtom, null);
    set(storeAuthStateAtom, createDefaultLegacyAuthState());
  },
  'Legacy store logout action'
);

export const storeRefreshTokensAtom = StateManager.createWritableDerivedAtom<null, [], Promise<void>>(
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

      const data = await response.json();
      set(storeAccessTokenAtom, data.accessToken ?? null);
      set(storeRefreshTokenAtom, data.refreshToken ?? null);
    } catch {
      set(storeLogoutAtom);
    }
  },
  'Legacy store refresh token action'
);

export const storeHasPermissionAtom = StateManager.createDerivedAtom(
  'store:auth:hasPermission',
  (get) => (permission: string) => {
    const user = get(storeUserAtom);
    const permissions = (user as unknown as { permissions?: string[] } | null)?.permissions;
    return permissions?.includes(permission) ?? false;
  },
  'Legacy store permission guard'
);

export const storeHasRoleAtom = StateManager.createDerivedAtom(
  'store:auth:hasRole',
  (get) => (role: string) => {
    const user = get(storeUserAtom);
    return user?.role === role;
  },
  'Legacy store role guard'
);

// General store state
export const storeThemeAtom = StateManager.createPersistentAtom<'light' | 'dark'>(
  'store:theme',
  'light',
  {
    description: 'Legacy store theme preference',
    storage: 'local',
  }
);

export const storeThemeModeAtom = StateManager.createPersistentAtom<LegacyThemeMode>(
  'store:themeMode',
  'system',
  {
    description: 'Legacy store theme mode',
    storage: 'local',
    storageKey: 'theme-mode',
  }
);

export const storeSidebarOpenAtom = StateManager.createPersistentAtom<boolean>(
  'store:sidebarOpen',
  true,
  {
    description: 'Legacy store sidebar state',
    storage: 'local',
    storageKey: 'sidebar-open',
  }
);

export const storePlatformAtom = StateManager.createAtom<StorePlatform>(
  'store:platform',
  'web',
  'Legacy store platform context'
);

export const storeWorkspacesAtom = StateManager.createAtom<StoreWorkspace[]>(
  'store:workspaces',
  [],
  'Legacy store workspace list'
);

export const storeCurrentWorkspaceIdAtom = StateManager.createPersistentAtom<string | null>(
  'store:currentWorkspaceId',
  null,
  {
    description: 'Legacy store current workspace ID',
    storage: 'local',
  }
);

export const storeCurrentWorkspaceAtom = StateManager.createWritableDerivedAtom<StoreWorkspace | null, [string | null], void>(
  'store:currentWorkspace',
  (get) => {
    const workspaces = get(storeWorkspacesAtom);
    const currentId = get(storeCurrentWorkspaceIdAtom);
    return currentId ? workspaces.find((workspace) => workspace.id === currentId) ?? null : null;
  },
  (_get, set, workspaceId) => {
    set(storeCurrentWorkspaceIdAtom, workspaceId);
  },
  'Legacy store current workspace accessor'
);

export const storeProjectsAtom = StateManager.createAtom<StoreProject[]>(
  'store:projects',
  [],
  'Legacy store project list'
);

export const storeCurrentProjectIdAtom = StateManager.createPersistentAtom<string | null>(
  'store:currentProjectId',
  null,
  {
    description: 'Legacy store current project ID',
    storage: 'local',
  }
);

export const storeCurrentProjectAtom = StateManager.createWritableDerivedAtom<StoreProject | null, [string | null], void>(
  'store:currentProject',
  (get) => {
    const projects = get(storeProjectsAtom);
    const currentId = get(storeCurrentProjectIdAtom);
    return currentId ? projects.find((project) => project.id === currentId) ?? null : null;
  },
  (_get, set, projectId) => {
    set(storeCurrentProjectIdAtom, projectId);
  },
  'Legacy store current project accessor'
);

export const storeTasksAtom = StateManager.createAtom<StoreTask[]>(
  'store:tasks',
  [],
  'Legacy store task list'
);

export const storeCurrentProjectTasksAtom = StateManager.createDerivedAtom(
  'store:currentProjectTasks',
  (get) => {
    const tasks = get(storeTasksAtom);
    const currentProject = get(storeCurrentProjectAtom);
    return currentProject ? tasks.filter((task) => task.projectId === currentProject.id) : [];
  },
  'Legacy store tasks scoped to current project'
);

export const storeLoadingStatesAtom = StateManager.createAtom<Record<string, boolean>>(
  'store:loadingStates',
  {},
  'Legacy store loading state map'
);

export const storeIsLoadingAtom = StateManager.createDerivedAtom(
  'store:isLoading',
  (get) => Object.values(get(storeLoadingStatesAtom)).some(Boolean),
  'Legacy store aggregate loading flag'
);

export const storeSetLoadingAtom = StateManager.createWritableDerivedAtom<
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
  },
  'Legacy store loading state mutator'
);

export const storeGlobalLoadingAtom = StateManager.createAtom<boolean>(
  'store:globalLoading',
  false,
  'Legacy store global loading indicator'
);

export const storeNotificationsAtom = StateManager.createAtom<LegacyNotification[]>(
  'store:notifications',
  [],
  'Legacy store notifications collection'
);

export const storeCommandPaletteOpenAtom = StateManager.createAtom<boolean>(
  'store:commandPaletteOpen',
  false,
  'Legacy store command palette visibility'
);

export const storeSearchQueryAtom = StateManager.createAtom<string>(
  'store:searchQuery',
  '',
  'Legacy store search query'
);

export const storeSearchResultsAtom = StateManager.createAtom<Array<Record<string, unknown>>>(
  'store:searchResults',
  [],
  'Legacy store search results'
);

export const storeBreadcrumbsAtom = StateManager.createAtom<Array<{ label: string; href?: string }>>(
  'store:breadcrumbs',
  [],
  'Legacy store breadcrumb trail'
);

export const storePageTitleAtom = StateManager.createAtom<string>(
  'store:pageTitle',
  '',
  'Legacy store page title'
);

export const storeFullscreenAtom = StateManager.createAtom<boolean>(
  'store:fullscreen',
  false,
  'Legacy store fullscreen state'
);

export const storeModalAtom = StateManager.createAtom<LegacyModalState>(
  'store:modal',
  createDefaultLegacyModalState(),
  'Legacy store modal state'
);

export const storeOpenModalAtom = StateManager.createWritableDerivedAtom<
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
  },
  'Legacy store open modal action'
);

export const storeCloseModalAtom = StateManager.createWritableDerivedAtom<null, [], void>(
  'store:closeModal',
  () => null,
  (_get, set) => {
    set(storeModalAtom, createDefaultLegacyModalState());
  },
  'Legacy store close modal action'
);

export const storeDrawerAtom = StateManager.createAtom<LegacyDrawerState>(
  'store:drawer',
  createDefaultLegacyDrawerState(),
  'Legacy store drawer state'
);

export const storeOpenDrawerAtom = StateManager.createWritableDerivedAtom<
  null,
  [
    {
      drawerId: string;
      position?: LegacyDrawerState['position'];
      data?: Record<string, unknown>;
    }
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
  },
  'Legacy store open drawer action'
);

export const storeCloseDrawerAtom = StateManager.createWritableDerivedAtom<null, [], void>(
  'store:closeDrawer',
  () => null,
  (_get, set) => {
    set(storeDrawerAtom, createDefaultLegacyDrawerState());
  },
  'Legacy store close drawer action'
);

export const storeAddNotificationAtom = StateManager.createWritableDerivedAtom<
  null,
  [Omit<LegacyNotification, 'id'>],
  void
>(
  'store:addNotification',
  () => null,
  (get, set, notification) => {
    const id = Math.random().toString(36).slice(2, 11);
    const newNotification: LegacyNotification = { ...notification, id };
    set(storeNotificationsAtom, [...get(storeNotificationsAtom), newNotification]);

    if (notification.duration !== 0) {
      setTimeout(() => {
        set(
          storeNotificationsAtom,
          get(storeNotificationsAtom).filter((existing) => existing.id !== id)
        );
      }, notification.duration ?? 5000);
    }
  },
  'Legacy store add notification action'
);

export const storeRemoveNotificationAtom = StateManager.createWritableDerivedAtom<null, [string], void>(
  'store:removeNotification',
  () => null,
  (get, set, id) => {
    set(
      storeNotificationsAtom,
      get(storeNotificationsAtom).filter((existing) => existing.id !== id)
    );
  },
  'Legacy store remove notification action'
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
