/**
 * Global State Management
 *
 * Complete state management system using Jotai.
 *
 * @module state
 */

// State Manager
export { StateManager } from './StateManager';
export type { AtomKey, AtomMetadata, StateManagerConfig } from './StateManager';

// Hooks
export {
  useGlobalState,
  useGlobalStateValue,
  useSetGlobalState,
  useResetGlobalState,
  useToggleGlobalState,
  useCounterGlobalState,
  useArrayGlobalState,
  useObjectGlobalState,
  useBatchGlobalStateUpdate,
  useGlobalStateKeys,
  useGlobalStateStatistics,
} from './useGlobalState';

// Provider
export { StateProvider } from './StateProvider';
export type { StateProviderProps } from './StateProvider';

// Pre-built atoms
export * from './atoms';
export * from './canvasMigrationAtoms';
export * from './configAtoms';
export {
  mobilePlatformAtom,
  mobileSettingsAtom,
  mobileThemePreferenceAtom,
  resolvedMobileThemeAtom,
  mobileNotificationSettingsAtom,
  mobileOfflineSettingsAtom,
  mobileSystemThemeAtom,
} from './mobile/atoms';
export type {
  MobileSettings,
  MobileThemePreference,
  MobilePlatform,
} from './mobile/atoms';

// Mobile-specific hooks
export {
  useMobilePlatform,
  useMobileSettings,
  useMobileSettingsValue,
  useSetMobileSettings,
  useMobileThemePreference,
  useResolvedMobileTheme,
  useMobileSystemTheme,
  useMobileNotificationSettings,
  useSetMobileNotificationSettings,
  useMobileOfflineSettings,
  useSetMobileOfflineSettings,
} from './mobile/hooks';

// Workspace atoms
export * from './workspaceAtoms';

// Project atoms
export * from './projectAtoms';

// Builder editor session atoms
export * from './builderAtoms';

// AI atoms
export * from './aiAtoms';

// Cross-tab synchronization
export {
  syncStateAcrossTabs,
  writeAtomToStorage,
  readAtomFromStorage,
  subscribeToSync,
  getSyncStatistics,
} from './cross-tab-sync';
export type {
  StorageEvent as SyncMessage,
  SyncConfig as CrossTabSyncOptions,
} from './cross-tab-sync';
export {
  useSyncedAtom,
  useSyncedAtomValue,
  useSyncedSetAtom,
  useAutoSyncAtom,
} from './hooks/useSyncedAtom';
