/**
 * @ghatana/product-shell — Shared type contracts
 *
 * These types form the generic interface between product-specific route
 * registries (AEP uses UserRole, DC uses ShellRole) and the shared shell
 * components. Products map their registry types to these generic types.
 *
 * @doc.type module
 * @doc.purpose Generic type contracts for product shell configuration
 * @doc.layer platform
 */

import type React from 'react';

// ---------------------------------------------------------------------------
// Route capability
// ---------------------------------------------------------------------------

/**
 * Route lifecycle state — mirrors the canonical registry enum.
 * stable  → fully implemented, discoverable
 * preview → partially implemented, visible with caveats
 * boundary → not ready, navigation-hidden or replaced by UnsupportedSurfaceBoundary
 */
export type RouteLifecycle = 'stable' | 'preview' | 'boundary';

/**
 * Generic route capability that both AEP and Data Cloud registries can map to.
 *
 * AEP maps from `RouteCapability` (with `minimumRole: UserRole`).
 * DC maps from its registry (with `minimumShellRole: ShellRole`).
 *
 * Both map to `minimumRole: string` in this generic form. The shell uses
 * `config.roleOrder` to compare roles numerically.
 */
export interface ProductRouteCapability {
  /** Absolute path, e.g. "/pipelines" */
  readonly path: string;
  /** Navigation display label */
  readonly label: string;
  /** Short description for tooltips / aria-label */
  readonly description?: string;
  /**
   * Lucide icon name string (e.g. "database", "workflow").
   * Shell resolves this via the icon registry or falls back to a circle.
   */
  readonly iconName?: string;
  /**
   * Navigation group heading, e.g. "Core", "Observability", "Manage".
   * Routes with the same group are rendered together.
   */
  readonly group?: string;
  /** Minimum role required to access this route. Compared via `config.roleOrder`. */
  readonly minimumRole?: string;
  /** Route lifecycle state. boundary routes are excluded from navigation. */
  readonly lifecycle?: RouteLifecycle;
  /** Whether the route appears in navigation for the current role. */
  readonly discoverable?: boolean;
}

// ---------------------------------------------------------------------------
// Product shell configuration
// ---------------------------------------------------------------------------

/**
 * Full configuration object passed to ProductShell.
 *
 * Products provide this to configure all shell behavior — navigation,
 * role/mode selection, search, and notifications — without the shell
 * having any product-specific knowledge.
 */
export interface ProductShellConfig {
  /** Product display name shown in the sidebar header, e.g. "AEP", "Data Cloud" */
  readonly productName: string;

  /**
   * Logo element rendered in the sidebar header beside `productName`.
   * Use a small icon or text mark — the sidebar is ~256px wide.
   */
  readonly logo?: React.ReactNode;

  /**
   * All route capabilities for this product.
   * The shell derives navigation from this list based on `currentRole`.
   */
  readonly routes: readonly ProductRouteCapability[];

  /**
   * The current user/shell role as a string.
   * Must be a key in `roleOrder`.
   */
  readonly currentRole: string;

  /**
   * Numeric hierarchy for roles. Higher numbers = more access.
   *
   * AEP example: `{ viewer: 0, operator: 1, admin: 2 }` (auditor handled separately)
   * DC example:  `{ 'primary-user': 0, operator: 1, admin: 2 }`
   */
  readonly roleOrder: Readonly<Record<string, number>>;

  /**
   * Display labels for each role, shown in the mode selector.
   *
   * DC example: `{ 'primary-user': 'Standard view', operator: 'Operator view', admin: 'Admin view' }`
   */
  readonly roleLabels?: Readonly<Record<string, string>>;

  /**
   * Per-role description text shown below the label in the selector dropdown.
   */
  readonly roleDescriptions?: Readonly<Record<string, string>>;

  /**
   * All role keys the selector should list, in display order.
   */
  readonly availableRoles?: readonly string[];

  /**
   * Title string for the role/mode selector dropdown heading.
   * Defaults to "View mode".
   */
  readonly roleSelectorTitle?: string;

  /**
   * aria-label for the role/mode selector button.
   * Defaults to "View mode menu".
   */
  readonly roleSelectorLabel?: string;

  /**
   * Explanatory note displayed at the bottom of the role selector explaining
   * that this is a UI disclosure control, not an authorization control.
   */
  readonly roleSelectorDisclosureNote?: string;

  /**
   * Called when the user selects a different role/mode.
   * The product is responsible for persisting and applying the change.
   */
  readonly onRoleChange?: (role: string) => void;

  /**
   * Called when the user clicks the search button/shortcut.
   * The product provides its own search overlay.
   */
  readonly onSearch?: () => void;

  /**
   * Notification items to display in the notification center.
   * The product owns notification state; the shell renders it.
   */
  readonly notifications?: readonly ProductNotification[];

  /**
   * Called when the user dismisses or clicks a notification.
   */
  readonly onNotificationAction?: (id: string, action: 'dismiss' | 'open') => void;

  /**
   * Active operations count. When > 0, the `ActiveOperationsBar` is shown.
   */
  readonly activeOperationsCount?: number;

  /**
   * Called when the user clicks the active operations bar.
   */
  readonly onActiveOperationsClick?: () => void;

  /**
   * Additional content rendered at the bottom of the sidebar
   * (e.g., tenant selector, SSE status indicator).
   */
  readonly sidebarFooter?: React.ReactNode;

  /**
   * Additional content rendered in the header right slot
   * (e.g., product-specific action buttons, environment badges).
   */
  readonly headerActions?: React.ReactNode;
}

// ---------------------------------------------------------------------------
// Notification
// ---------------------------------------------------------------------------

export interface ProductNotification {
  readonly id: string;
  readonly title: string;
  readonly message?: string;
  readonly level: 'info' | 'warning' | 'error' | 'success';
  readonly timestamp: string;
  readonly read?: boolean;
}

// ---------------------------------------------------------------------------
// Unsupported surface
// ---------------------------------------------------------------------------

export interface UnsupportedSurfaceConfig {
  readonly title: string;
  readonly reason: string;
  readonly guidance?: string;
  readonly estimatedAvailability?: string;
  readonly docsUrl?: string;
}
