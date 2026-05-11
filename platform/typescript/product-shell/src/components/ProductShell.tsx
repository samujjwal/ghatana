/**
 * ProductShell — root layout wrapper for Ghatana product UIs.
 *
 * Composes `CapabilitySidebar`, `ProductHeader`, `ActiveOperationsBar`,
 * and product-owned `children` into the standard product layout:
 *
 * ```
 * ┌──────────────────────────────────────────────────────┐
 * │ Sidebar (fixed, w-64 / w-16 collapsed)               │
 * │   Logo + product name                                │
 * │   RouteCapabilityNav (grouped, role-filtered)        │
 * │   Sidebar footer slot                                │
 * ├──────────────────────────────────────────────────────┤
 * │ Header (fixed, h-16)                                 │
 * │   Left: mobile hamburger                             │
 * │   Right: search | notifications | mode selector      │
 * │         | product-specific actions                   │
 * ├──────────────────────────────────────────────────────┤
 * │ Main content area (pt-16 + pl-64 / pl-16)            │
 * │   Product-owned route content                        │
 * ├──────────────────────────────────────────────────────┤
 * │ ActiveOperationsBar (fixed bottom, when count > 0)   │
 * └──────────────────────────────────────────────────────┘
 * ```
 *
 * Products configure this shell via `ProductShellConfig` instead of
 * independently implementing layout behavior.
 *
 * @doc.type component
 * @doc.purpose Root layout wrapper for Ghatana product shells
 * @doc.layer platform
 * @doc.pattern Compound Component / Layout Shell
 */
import React, { useState } from 'react';
import type { ProductShellConfig } from '../types';
import { CapabilitySidebar } from './CapabilitySidebar';
import { ProductHeader } from './ProductHeader';
import { ActiveOperationsBar } from './ActiveOperationsBar';

interface ProductShellProps {
  config: ProductShellConfig;
  /**
   * Product-owned route/content slot. The shared shell intentionally does not
   * import router primitives.
   */
  children: React.ReactNode;
  /**
   * Additional CSS class applied to the main content area wrapper.
   */
  contentClassName?: string;
  /**
   * Optional id applied to the main content element for skip-link support.
   */
  mainContentId?: string;
  /**
   * Optional tab index applied to the main content element.
   */
  mainContentTabIndex?: number;
  /**
   * Optional ARIA role override for the main content element.
   */
  mainContentRole?: React.AriaRole;
}

/**
 * Root product shell layout.
 *
 * Manages sidebar collapse state internally. All product-specific behavior
 * (role change, search, notifications, operations) is provided via `config`.
 *
 * @example
 * ```tsx
 * // In a product's root layout component:
 * export function AppLayout() {
 *   const config = useProductShellConfig();
 *   return <ProductShell config={config}>{routeContent}</ProductShell>;
 * }
 * ```
 */
export function ProductShell({
  config,
  children,
  contentClassName,
  mainContentId,
  mainContentTabIndex,
  mainContentRole,
}: ProductShellProps): React.ReactElement {
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const mainContentProps: React.ComponentPropsWithoutRef<'main'> = {};

  if (mainContentId) {
    mainContentProps.id = mainContentId;
  }
  if (typeof mainContentTabIndex === 'number') {
    mainContentProps.tabIndex = mainContentTabIndex;
  }
  if (mainContentRole) {
    mainContentProps.role = mainContentRole;
  }

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-950">
      {/* Sidebar */}
      <CapabilitySidebar
        config={config}
        isCollapsed={sidebarCollapsed}
        onToggle={() => setSidebarCollapsed((prev) => !prev)}
        isMobileOpen={mobileMenuOpen}
        onMobileClose={() => setMobileMenuOpen(false)}
      />

      {/* Main area — offset by sidebar width */}
      <div
        className={
          sidebarCollapsed
            ? 'lg:ml-16 transition-all duration-300'
            : 'lg:ml-64 transition-all duration-300'
        }
      >
        {/* Header */}
        <ProductHeader
          config={config}
          sidebarCollapsed={sidebarCollapsed}
          onMenuClick={() => setMobileMenuOpen(true)}
        />

        {/* Content */}
        <main
          {...mainContentProps}
          className={contentClassName ?? 'pt-16 p-6'}
        >
          {children}
        </main>
      </div>

      {/* Active operations bar */}
      <ActiveOperationsBar
        count={config.activeOperationsCount ?? 0}
        {...(config.onActiveOperationsClick ? { onClick: config.onActiveOperationsClick } : {})}
      />
    </div>
  );
}
