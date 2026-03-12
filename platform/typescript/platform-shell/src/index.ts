/**
 * Public API for `@ghatana/platform-shell`.
 *
 * Atoms
 * ─────
 *   tenantAtom, availableTenantsAtom, hasRealTenantAtom
 *   authTokenAtom, isAuthenticatedAtom, isTokenExpiredAtom, currentUserEmailAtom
 *   notificationsAtom, unreadCountAtom, notificationPanelOpenAtom
 *   pushNotificationAtom, markReadAtom, markAllReadAtom
 *
 * Components
 * ──────────
 *   PlatformShell  — root shell (Jotai Provider + NavBar + content slot)
 *   NavBar         — top navigation bar
 *   TenantSelector — tenant context dropdown
 *   NotificationCenter — bell icon + notification panel
 *   ProductPicker  — landing product grid
 */

/* ── Atoms ──────────────────────────────────────────────────────────────────── */
export {
  tenantAtom,
  availableTenantsAtom,
  hasRealTenantAtom,
  type Tenant,
} from './atoms/tenantAtom';

export {
  authTokenAtom,
  isAuthenticatedAtom,
  isTokenExpiredAtom,
  currentUserEmailAtom,
  type AuthToken,
} from './atoms/authAtom';

export {
  notificationsAtom,
  unreadCountAtom,
  notificationPanelOpenAtom,
  pushNotificationAtom,
  markReadAtom,
  markAllReadAtom,
  type Notification,
  type NotificationSeverity,
} from './atoms/notificationAtom';

/* ── Components ─────────────────────────────────────────────────────────────── */
export { PlatformShell, type PlatformShellProps } from './components/PlatformShell';
export { NavBar, type NavBarProps } from './components/NavBar';
export { TenantSelector, type TenantSelectorProps } from './components/TenantSelector';
export { NotificationCenter, type NotificationCenterProps } from './components/NotificationCenter';
export { ProductPicker, type ProductPickerProps } from './components/ProductPicker';
