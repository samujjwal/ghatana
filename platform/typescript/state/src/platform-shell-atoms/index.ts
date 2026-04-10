/**
 * Platform-shell atoms re-exported from @ghatana/state
 *
 * Authentication, notification, and tenant Jotai atoms
 * that are shared across all platform shells.
 */

export {
  authTokenAtom,
  isAuthenticatedAtom,
  isTokenExpiredAtom,
  currentUserEmailAtom,
  type AuthToken,
} from './authAtom';

export {
  notificationsAtom,
  unreadCountAtom,
  notificationPanelOpenAtom,
  pushNotificationAtom,
  markReadAtom,
  markAllReadAtom,
  type Notification,
  type NotificationSeverity,
} from './notificationAtom';

export {
  tenantAtom,
  availableTenantsAtom,
  hasRealTenantAtom,
  type Tenant,
} from './tenantAtom';
