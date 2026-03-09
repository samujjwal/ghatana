/**
 * Jotai Stores Export Index
 *
 * Central export point for all Jotai atoms managing app state.
 *
 * Per copilot-instructions.md:
 * - App-scoped state using Jotai atoms
 * - Feature-centric organization in feature/stores/
 * - Colocation principle - state close to where used
 *
 * @doc.type module
 * @doc.purpose Atom export index
 * @doc.layer product
 * @doc.pattern Index
 */

// Authentication store
export {
  authAtom,
  isAuthenticatedAtom,
  currentUserAtom,
  authStatusAtom,
  authErrorAtom,
  loginAtom,
  logoutAtom,
  refreshTokenAtom,
  clearAuthErrorAtom,
  type User,
  type LoginCredentials,
  type AuthState,
} from './auth.store';

// Device management store
export {
  deviceAtom,
  selectedDeviceAtom,
  deviceCountAtom,
  isDeviceSelectedAtom,
  onlineDevicesAtom,
  selectDeviceAtom,
  fetchDevicesAtom,
  removeDeviceAtom,
  updateDeviceStatusAtom,
  clearDeviceErrorAtom,
  type Device,
  type DeviceState,
} from './device.store';

// Policy management store
export {
  policyAtom,
  activePolicyAtom,
  policyCountAtom,
  isPolicyAppliedAtom,
  activePoliciesAtom,
  createPolicyAtom,
  updatePolicyAtom,
  deletePolicyAtom,
  applyPolicyAtom,
  clearPolicyErrorAtom,
  fetchPoliciesAtom,
  type Policy,
  type PolicyState,
  type PolicyConstraints,
} from './policy.store';

// Monitoring data store
export {
  monitoringAtom,
  recentEventsAtom,
  eventCountAtom,
  appUsageAtom,
  flaggedActivitiesAtom,
  recordEventAtom,
  fetchDailySummaryAtom,
  clearEventsAtom,
  startMonitoringAtom,
  pauseMonitoringAtom,
  clearMonitoringErrorAtom,
  type MonitoringEvent,
  type DailySummary,
  type MonitoringState,
} from './monitoring.store';

// WebSocket real-time store
export {
  websocketAtom,
  isConnectedAtom,
  lastMessageAtom,
  activeSubscriptionsAtom,
  isReconnectingAtom,
  connectAtom,
  disconnectAtom,
  sendMessageAtom,
  subscribeAtom,
  unsubscribeAtom,
  receiveMessageAtom,
  clearWebSocketErrorAtom,
  type WebSocketMessage,
  type ChannelSubscription,
  type WebSocketState,
} from './websocket.store';

// UI state store
export {
  uiAtom,
  currentScreenAtom,
  isModalOpenAtom,
  activeNotificationsAtom,
  themeAtom,
  navigateToAtom,
  toggleModalAtom,
  openModalAtom,
  closeModalAtom,
  toggleThemeAtom,
  showNotificationAtom,
  dismissNotificationAtom,
  setLoadingAtom,
  toggleSidebarAtom,
  closeSidebarAtom,
  type Notification,
  type UIState,
} from './ui.store';

// Settings store
export {
  settingsAtom,
  languageAtom,
  notificationPreferencesAtom,
  privacySettingsAtom,
  isAutoLockEnabledAtom,
  setLanguageAtom,
  setTimezoneAtom,
  updateNotificationPrefsAtom,
  updatePrivacySettingsAtom,
  toggleAutoLockAtom,
  setAutoLockTimeoutAtom,
  setCustomPrefAtom,
  resetSettingsAtom,
  setSettingsErrorAtom,
  type NotificationPreferences,
  type PrivacySettings,
  type SettingsState,
} from './settings.store';

// App list management store
export {
  appsAtom,
  filteredAppsAtom,
  appCountAtom,
  selectedAppsAtom,
  activeAppCountAtom,
  inactiveAppCountAtom,
  updateSearchAtom,
  updateFilterAtom,
  updateSortAtom,
  toggleAppSelectionAtom,
  toggleMultiSelectModeAtom,
  bulkUpdateStatusAtom,
  deleteAppsAtom,
  fetchAppsAtom,
  clearAppErrorAtom,
  clearAllSelectionsAtom,
  type App,
  type AppListState,
} from './apps.store';

// App usage metrics store
export {
  usageAtom,
  totalDailyUsageAtom,
  averageUsagePerAppAtom,
  topAppsAtom,
  usageTrendAtom,
  hourlyDataForChartAtom,
  weeklyTrendDataAtom,
  appMetricsAtom,
  updateDailyUsageAtom,
  updateHourlyUsageAtom,
  updateAppUsageAtom,
  updateWeeklyTrendAtom,
  fetchUsageAtom,
  resetDailyStatsAtom,
  clearUsageErrorAtom,
  type DailyUsageSummary,
  type HourlyUsage,
  type AppUsageMetrics,
  type UsageState,
} from './usage.store';

// App permissions store
export {
  permissionsAtom,
  appPermissionsAtom,
  grantedPermissionsAtom,
  deniedPermissionsAtom,
  dangerousPermissionsAtom,
  pendingRequestCountAtom,
  permissionStatusAtom,
  updateAppPermissionsAtom,
  updatePermissionStatusAtom,
  requestPermissionAtom,
  grantPermissionAtom,
  denyPermissionAtom,
  revokePermissionAtom,
  fetchPermissionsAtom,
  clearPermissionsErrorAtom,
  PERMISSION_TYPES,
  type Permission,
  type AppPermissionSet,
  type PermissionRequest,
  type PermissionsState,
  type PermissionStatus,
} from './permissions.store';

// Data sync store
export {
  syncAtom,
  isSyncingAtom,
  isOnlineAtom,
  pendingChangesCountAtom,
  syncProgressAtom,
  timeSinceLastSyncAtom,
  setOnlineStatusAtom,
  startSyncOperationAtom,
  updateSyncProgressAtom,
  completeSyncOperationAtom,
  failSyncOperationAtom,
  queueSyncOperationAtom,
  clearPendingChangesAtom,
  clearSyncErrorAtom,
  resetSyncAtom,
  SYNC_OPERATION_TYPES,
  type SyncState,
  type SyncOperation,
  type SyncOperationStatus,
} from './sync.store';
