/**
 * TopNav Component Types
 *
 * Type definitions for the TopNav component - a navigation bar for the DevSecOps Canvas.
 *
 * @module DevSecOps/TopNav/types
 */

/**
 * User role types
 */
export type UserRole = 'Executive' | 'PM' | 'Developer' | 'Security' | 'DevOps' | 'QA';

/**
 * Navigation page identifiers
 */
export type NavigationPage = 'dashboard' | 'phases' | 'persona' | 'reports' | 'settings' | 'canvas' | 'workflows' | 'task-board';

/**
 * User information for display in the navigation bar
 *
 * @property name - User's full name
 * @property avatar - Optional URL to user's avatar image
 * @property role - User's role in the system
 *
 * @example
 * ```typescript
 * const user: TopNavUser = {
 *   name: 'John Doe',
 *   avatar: '/avatars/john.png',
 *   role: 'Developer'
 * };
 * ```
 */
export interface TopNavUser {
  /** User's full name */
  name: string;

  /** URL to user's avatar image */
  avatar?: string;

  /** User's role */
  role: UserRole;
}

/**
 * Props for the TopNav component
 *
 * @property currentPage - Currently active page
 * @property onNavigate - Callback when navigation item is clicked
 * @property notificationCount - Number of unread notifications
 * @property user - Current user information
 * @property onProfileClick - Callback when profile is clicked
 * @property onNotificationsClick - Callback when notifications icon is clicked
 *
 * @example
 * ```typescript
 * <TopNav
 *   currentPage="dashboard"
 *   onNavigate={(page) => router.push(`/${page}`)}
 *   notificationCount={5}
 *   user={{ name: 'John Doe', role: 'Developer' }}
 * />
 * ```
 */
export interface TopNavProps {
  /** Currently active page */
  currentPage?: NavigationPage;

  /** Navigation callback */
  onNavigate?: (page: NavigationPage) => void;

  /** Number of unread notifications */
  notificationCount?: number;

  /** Current user information */
  user?: TopNavUser;

  /** Profile click callback */
  onProfileClick?: () => void;

  /** Notifications click callback */
  onNotificationsClick?: () => void;
}
