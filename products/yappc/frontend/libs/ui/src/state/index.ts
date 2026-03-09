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
export type { MobileSettings, MobileThemePreference, MobilePlatform } from './mobile/atoms';

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

// Cross-tab synchronization
export {
  CrossTabSync,
  getCrossTabSync,
  createSyncedAtom,
  useSyncedAtom,
  destroyCrossTabSync,
  SyncMessageType,
} from './CrossTabSync';
export type { SyncMessage, CrossTabSyncOptions } from './CrossTabSync';

// Storybook utilities
export * from './storybook';
