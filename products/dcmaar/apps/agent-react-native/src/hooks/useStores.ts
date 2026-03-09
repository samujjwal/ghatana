/**
 * Custom Hooks for Jotai Atoms
 *
 * Provides convenient unified API for accessing all Jotai stores.
 * This layer abstracts direct atom usage and provides type-safe hooks.
 *
 * @doc.type module
 * @doc.purpose Unified store hook layer for all atoms
 * @doc.layer product
 * @doc.pattern Hook Layer
 */

import { useAtom, useAtomValue } from 'jotai';
import {
  // Auth atoms
  authAtom,
  isAuthenticatedAtom,
  currentUserAtom,
  loginAtom,
  logoutAtom,
  clearAuthErrorAtom,
  
  // Device atoms
  deviceAtom,
  selectedDeviceAtom,
  deviceCountAtom,
  isDeviceSelectedAtom,
  onlineDevicesAtom,
  selectDeviceAtom,
  fetchDevicesAtom,
  
  // Policy atoms
  policyAtom,
  activePolicyAtom,
  policyCountAtom,
  isPolicyAppliedAtom,
  activePoliciesAtom,
  fetchPoliciesAtom,
  
  // Monitoring atoms
  monitoringAtom,
  recentEventsAtom,
  eventCountAtom,
  appUsageAtom,
  flaggedActivitiesAtom,
  recordEventAtom,
  
  // WebSocket atoms
  websocketAtom,
  isConnectedAtom,
  lastMessageAtom,
  activeSubscriptionsAtom,
  isReconnectingAtom,
  connectAtom,
  disconnectAtom,
  sendMessageAtom,
  
  // UI atoms
  currentScreenAtom,
  isModalOpenAtom,
  activeNotificationsAtom,
  themeAtom,
  toggleModalAtom,
  toggleThemeAtom,
  showNotificationAtom,
  
  // Settings atoms
  languageAtom,
  notificationPreferencesAtom,
  privacySettingsAtom,
  isAutoLockEnabledAtom,
  setLanguageAtom,
  updateNotificationPrefsAtom,
} from '../stores';

// ============================================================================
// AUTH HOOKS
// ============================================================================

/**
 * Main auth hook - provides user state and authentication methods
 *
 * @returns Auth state and methods (user, loading, error, methods)
 */
export function useAuth() {
  const auth = useAtomValue(authAtom);
  const [, login] = useAtom(loginAtom);
  const [, logout] = useAtom(logoutAtom);
  const isAuthenticated = useAtomValue(isAuthenticatedAtom);

  return {
    user: auth.user,
    status: auth.status,
    error: auth.error,
    isAuthenticated,
    login,
    logout,
  };
}

/**
 * Authentication status hook (read-only)
 *
 * @returns Whether user is authenticated
 */
export function useIsAuthenticated() {
  return useAtomValue(isAuthenticatedAtom);
}

/**
 * Current user hook (read-only)
 *
 * @returns Current user or null
 */
export function useCurrentUser() {
  return useAtomValue(currentUserAtom);
}

/**
 * Auth error hook
 *
 * @returns Current auth error and setter
 */
export function useAuthError() {
  const auth = useAtomValue(authAtom);
  const [, clearError] = useAtom(clearAuthErrorAtom);
  return { error: auth.error, clearError };
}

// ============================================================================
// DEVICE HOOKS
// ============================================================================

/**
 * Main device hook - provides device state and methods
 *
 * @returns Device state (selected, list, count, etc.)
 */
export function useDevice() {
  const deviceState = useAtomValue(deviceAtom);
  const selectedDevice = useAtomValue(selectedDeviceAtom);
  const deviceCount = useAtomValue(deviceCountAtom);
  const isDeviceSelected = useAtomValue(isDeviceSelectedAtom);
  const [, selectDevice] = useAtom(selectDeviceAtom);
  const [, fetchDevices] = useAtom(fetchDevicesAtom);

  return {
    devices: deviceState.devices,
    selectedDevice,
    deviceCount,
    isDeviceSelected,
    selectDevice,
    fetchDevices,
  };
}

/**
 * Selected device hook (read-only)
 *
 * @returns Currently selected device or null
 */
export function useSelectedDevice() {
  return useAtomValue(selectedDeviceAtom);
}

/**
 * Online devices hook (read-only)
 *
 * @returns List of online devices
 */
export function useOnlineDevices() {
  return useAtomValue(onlineDevicesAtom);
}

// ============================================================================
// POLICY HOOKS
// ============================================================================

/**
 * Main policy hook - provides policy state and methods
 *
 * @returns Policy state (active, list, count, etc.)
 */
export function usePolicy() {
  const policyState = useAtomValue(policyAtom);
  const activePolicy = useAtomValue(activePolicyAtom);
  const activePolicies = useAtomValue(activePoliciesAtom);
  const policyCount = useAtomValue(policyCountAtom);
  const isPolicyApplied = useAtomValue(isPolicyAppliedAtom);
  const [, fetchPolicies] = useAtom(fetchPoliciesAtom);

  return {
    policies: policyState.policies,
    activePolicy,
    activePolicies,
    policyCount,
    isPolicyApplied,
    fetchPolicies,
  };
}

/**
 * Active policies hook (read-only)
 *
 * @returns List of active policies
 */
export function useActivePolicies() {
  return useAtomValue(activePoliciesAtom);
}

/**
 * Active policy hook (read-only)
 *
 * @returns Currently active policy or null
 */
export function useActivePolicy() {
  return useAtomValue(activePolicyAtom);
}

// ============================================================================
// MONITORING HOOKS
// ============================================================================

/**
 * Main monitoring hook - provides monitoring state
 *
 * @returns Monitoring state (events, usage, activities)
 */
export function useMonitoring() {
  const monitoringState = useAtomValue(monitoringAtom);
  const recentEvents = useAtomValue(recentEventsAtom);
  const eventCount = useAtomValue(eventCountAtom);
  const appUsage = useAtomValue(appUsageAtom);
  const flaggedActivities = useAtomValue(flaggedActivitiesAtom);
  const [, recordEvent] = useAtom(recordEventAtom);

  return {
    events: monitoringState.events,
    recentEvents,
    eventCount,
    appUsage,
    flaggedActivities,
    recordEvent,
  };
}

/**
 * Recent events hook (read-only)
 *
 * @returns Recent monitoring events
 */
export function useRecentEvents() {
  return useAtomValue(recentEventsAtom);
}

/**
 * Flagged activities hook (read-only)
 *
 * @returns Activities flagged as violations
 */
export function useFlaggedActivities() {
  return useAtomValue(flaggedActivitiesAtom);
}

/**
 * App usage hook (read-only)
 *
 * @returns App usage metrics
 */
export function useAppUsage() {
  return useAtomValue(appUsageAtom);
}

// ============================================================================
// WEBSOCKET HOOKS
// ============================================================================

/**
 * Main WebSocket hook - provides connection state and messages
 *
 * @returns WebSocket state (connection, messages, subscriptions)
 */
export function useWebSocket() {
  const wsState = useAtomValue(websocketAtom);
  const isConnected = useAtomValue(isConnectedAtom);
  const lastMessage = useAtomValue(lastMessageAtom);
  const activeSubscriptions = useAtomValue(activeSubscriptionsAtom);
  const [, connect] = useAtom(connectAtom);
  const [, disconnect] = useAtom(disconnectAtom);
  const [, sendMessage] = useAtom(sendMessageAtom);

  return {
    state: wsState,
    isConnected,
    lastMessage,
    activeSubscriptions,
    connect,
    disconnect,
    sendMessage,
  };
}

/**
 * WebSocket connection status hook (read-only)
 *
 * @returns Whether WebSocket is connected
 */
export function useIsConnected() {
  return useAtomValue(isConnectedAtom);
}

/**
 * WebSocket last message hook (read-only)
 *
 * @returns Last received WebSocket message
 */
export function useLastMessage() {
  return useAtomValue(lastMessageAtom);
}

/**
 * WebSocket reconnecting state hook (read-only)
 *
 * @returns Whether WebSocket is reconnecting
 */
export function useIsReconnecting() {
  return useAtomValue(isReconnectingAtom);
}

// ============================================================================
// UI HOOKS
// ============================================================================

/**
 * Main UI hook - provides UI state and methods
 *
 * @returns UI state (screen, modals, theme, notifications)
 */
export function useUI() {
  const currentScreen = useAtomValue(currentScreenAtom);
  const theme = useAtomValue(themeAtom);
  const activeNotifications = useAtomValue(activeNotificationsAtom);
  const isModalOpen = useAtomValue(isModalOpenAtom);
  const [, toggleTheme] = useAtom(toggleThemeAtom);
  const [, toggleModal] = useAtom(toggleModalAtom);
  const [, showNotification] = useAtom(showNotificationAtom);

  return {
    currentScreen,
    theme,
    activeNotifications,
    isModalOpen,
    toggleTheme,
    toggleModal,
    showNotification,
  };
}

/**
 * Current screen hook (read-only)
 *
 * @returns Current active screen
 */
export function useCurrentScreen() {
  return useAtomValue(currentScreenAtom);
}

/**
 * Theme hook (read-only)
 *
 * @returns Current theme ('light' or 'dark')
 */
export function useTheme() {
  return useAtomValue(themeAtom);
}

/**
 * Modal state hook (read-only)
 *
 * @returns Whether any modal is open
 */
export function useIsModalOpen() {
  return useAtomValue(isModalOpenAtom);
}

/**
 * Active notifications hook (read-only)
 *
 * @returns List of active notifications
 */
export function useActiveNotifications() {
  return useAtomValue(activeNotificationsAtom);
}

/**
 * Toggle theme hook
 *
 * @returns Function to toggle theme
 */
export function useToggleTheme() {
  const [, toggleTheme] = useAtom(toggleThemeAtom);
  return toggleTheme;
}

/**
 * Show notification hook
 *
 * @returns Function to show notification
 */
export function useShowNotification() {
  const [, showNotification] = useAtom(showNotificationAtom);
  return showNotification;
}

// ============================================================================
// SETTINGS HOOKS
// ============================================================================

/**
 * Main settings hook - provides user settings state
 *
 * @returns User settings state (preferences, notifications, privacy)
 */
export function useSettings() {
  const language = useAtomValue(languageAtom);
  const notificationPrefs = useAtomValue(notificationPreferencesAtom);
  const privacySettings = useAtomValue(privacySettingsAtom);
  const isAutoLockEnabled = useAtomValue(isAutoLockEnabledAtom);
  const [, setLanguage] = useAtom(setLanguageAtom);
  const [, updateNotificationPrefs] = useAtom(updateNotificationPrefsAtom);

  return {
    language,
    notificationPrefs,
    privacySettings,
    isAutoLockEnabled,
    setLanguage,
    updateNotificationPrefs,
  };
}

/**
 * Language hook (read-only)
 *
 * @returns Current language setting
 */
export function useLanguage() {
  return useAtomValue(languageAtom);
}

/**
 * Notification preferences hook (read-only)
 *
 * @returns User notification preferences
 */
export function useNotificationPreferences() {
  return useAtomValue(notificationPreferencesAtom);
}

/**
 * Privacy settings hook (read-only)
 *
 * @returns User privacy settings
 */
export function usePrivacySettings() {
  return useAtomValue(privacySettingsAtom);
}

/**
 * Auto-lock enabled hook (read-only)
 *
 * @returns Whether auto-lock is enabled
 */
export function useIsAutoLockEnabled() {
  return useAtomValue(isAutoLockEnabledAtom);
}

// ============================================================================
// EXPORTS
// ============================================================================

// Export all hooks as named exports
export default {
  // Auth
  useAuth,
  useAuthError,
  useIsAuthenticated,
  useCurrentUser,
  
  // Device
  useDevice,
  useSelectedDevice,
  useOnlineDevices,
  
  // Policy
  usePolicy,
  useActivePolicies,
  useActivePolicy,
  
  // Monitoring
  useMonitoring,
  useRecentEvents,
  useFlaggedActivities,
  useAppUsage,
  
  // WebSocket
  useWebSocket,
  useIsConnected,
  useLastMessage,
  useIsReconnecting,
  
  // UI
  useUI,
  useCurrentScreen,
  useTheme,
  useIsModalOpen,
  useActiveNotifications,
  useToggleTheme,
  useShowNotification,
  
  // Settings
  useSettings,
  useLanguage,
  useNotificationPreferences,
  usePrivacySettings,
  useIsAutoLockEnabled,
};
