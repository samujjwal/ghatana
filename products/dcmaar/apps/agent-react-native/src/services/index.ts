/**
 * API Services Index
 *
 * Centralized exports for API client and configuration.
 */

export { guardianApi } from './guardianApi';
export type {
  ApiResponse,
  PaginationMeta,
  PaginatedResponse,
  AppData,
  AppFilters,
  PolicyData,
  RecommendationData,
  DeviceStatusData,
} from './guardianApi';

// Event sync service
export {
  EventSyncService,
  queueSyncOperation,
  startEventSync,
  stopEventSync,
  pauseEventSync,
  resumeEventSync,
  getPendingSyncCount,
  clearSyncQueue,
  getSyncStatus,
} from './eventSyncService';

// Policy notification service
export {
  startPolicyNotifications,
  stopPolicyNotifications,
  pausePolicyNotifications,
  resumePolicyNotifications,
  getPendingNotifications,
  clearNotifications,
  getNotificationServiceStatus,
  setPollingInterval,
  triggerPolicyPoll,
} from './policyNotificationService';

// Bridge service
export { BridgeService } from './bridgeService';

// Sprint 5: Command sync service (connector-based)
export {
  useCommandSyncStore,
  initCommandSync,
  startCommandSync,
  stopCommandSync,
  forceSync,
  onSyncSnapshot,
} from './commandSyncService';
export type {
  SyncSnapshot,
  PolicyItem,
  GuardianCommand,
  CommandSyncConfig,
} from './commandSyncService';

// Sprint 5: Command execution service (connector-based)
export {
  useCommandExecutionStore,
  initCommandExecution,
  executeCommand,
  executeCommands,
  getExecutionResult,
} from './commandExecutionService';
export type {
  CommandResult,
  CommandExecutionConfig,
  CommandHandlers,
} from './commandExecutionService';

// Sprint 5: Telemetry service (connector-based)
export {
  useTelemetryStore,
  initTelemetry,
  startTelemetry,
  stopTelemetry,
  sendEvent,
  sendCustomEvent,
  sendCommandEvent,
  sendErrorEvent,
  flushEvents,
  getBufferSize,
} from './telemetryService';
export type {
  GuardianEventPayload,
  TelemetryConfig,
} from './telemetryService';
