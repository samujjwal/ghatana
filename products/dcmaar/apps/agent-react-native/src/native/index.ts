/**
 * Native Bridge Modules Index
 *
 * Central export point for all native bridge modules.
 * Each module coordinates with native Android/iOS code.
 *
 * Modules:
 * - AccessibilityServiceModule: App monitoring and focus tracking
 * - PermissionsModule: Runtime permission management
 * - DeviceAdminModule: Device administration and policies
 * - BackgroundSyncModule: Background synchronization coordination
 *
 * @see ../services/bridgeService.ts for main service coordinator
 */

// Export native modules
export { default as AccessibilityServiceModule } from './AccessibilityServiceModule';
export {
  PermissionsModule,
  type PermissionRequest,
  type PermissionGrant,
  type PermissionRevoke,
  type PermissionStatusResult,
} from './PermissionsModule';
export {
  DeviceAdminModule,
  type DevicePolicy,
  type PolicyStatus,
  type AdminRequestResult,
  type DeviceRestriction,
} from './DeviceAdminModule';
export {
  BackgroundSyncModule,
  type SyncOperationType,
  type SyncStatus,
  type SyncOperationConfig,
  type SyncProgress,
  type SyncStatistics,
} from './BackgroundSyncModule';

// Export bridge service
export {
  BridgeService,
  getBridgeService,
  useBridgeService,
  type IBridgeService,
  type PermissionStatus,
  type AppFocusEvent,
  type SyncOperation,
} from '../services/bridgeService';
