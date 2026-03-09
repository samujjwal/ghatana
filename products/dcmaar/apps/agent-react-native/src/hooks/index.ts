/**
 * Hooks Export Index
 *
 * Central export point for all custom hooks in the application.
 *
 * @doc.type module
 * @doc.purpose Hook export index
 * @doc.layer product
 * @doc.pattern Index
 */

// useStores hooks
export {
  useAuth,
  useAuthError,
  useIsAuthenticated,
  useCurrentUser,
  useDevice,
  useSelectedDevice,
  useOnlineDevices,
  usePolicy,
  useActivePolicies,
  useActivePolicy,
  useMonitoring,
  useRecentEvents,
  useFlaggedActivities,
  useAppUsage,
  useWebSocket,
  useIsConnected,
  useLastMessage,
  useIsReconnecting,
  useUI,
  useCurrentScreen,
  useTheme,
  useIsModalOpen,
  useActiveNotifications,
  useToggleTheme,
  useShowNotification,
  useSettings,
  useLanguage,
  useNotificationPreferences,
  usePrivacySettings,
  useIsAutoLockEnabled,
} from './useStores';

// Native module hooks
export {
  useCurrentApp,
  useDeviceStatus,
  usePolicies,
  useUsageStats,
} from './useNativeModule';

// Guardian API hooks
export {
  // Query hooks
  useApps,
  useApp,
  usePolicies as usePoliciesAPI,
  usePolicy as usePolicyAPI,
  useRecommendations,
  useRecommendation,
  useDeviceStatus as useDeviceStatusAPI,
  useDevices,
  useHealthCheck,
  // Mutation hooks
  useUpdateApp,
  useCreatePolicy,
  useUpdatePolicy,
  useDeletePolicy,
  useDismissRecommendation,
  useSyncDevice,
  // Query key factories
  appsQueryKeys,
  policiesQueryKeys,
  recommendationsQueryKeys,
  devicesQueryKeys,
} from './useGuardianApi';

// Event bridge hooks
export {
  useEventBridge,
  useEventBridgeManual,
} from './useEventBridge';
