/**
 * ProductShell — root layout wrapper for Ghatana product UIs.
 *
 * Composes `CapabilitySidebar`, `ProductHeader`, `ActiveOperationsBar`,
 * and an `<Outlet />` (or `children`) into the standard product layout:
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
 * │   <Outlet /> or children                             │
 * ├──────────────────────────────────────────────────────┤
 * │ ActiveOperationsBar (fixed bottom, when count > 0)   │
 * └──────────────────────────────────────────────────────┘
 * ```
 *
 * Both AEP and Data Cloud configure this shell via `ProductShellConfig`
 * instead of independently implementing layout behavior.
 *
 * @doc.type component
 * @doc.purpose Root layout wrapper for Ghatana product shells
 * @doc.layer platform
 * @doc.pattern Compound Component / Layout Shell
 */
import React, { useState } from 'react';
import { Outlet } from 'react-router';
import type { ProductShellConfig } from '../types';
import { CapabilitySidebar } from './CapabilitySidebar';
import { ProductHeader } from './ProductHeader';
import { ActiveOperationsBar } from './ActiveOperationsBar';

interface ProductShellProps {
  config: ProductShellConfig;
  /**
   * When provided, renders children instead of `<Outlet />`.
   * Use this when ProductShell is not the direct parent of react-router routes.
   */
  children?: React.ReactNode;
  /**
   * Additional CSS class applied to the main content area wrapper.
   */
  contentClassName?: string;
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
 *   const config = useProductShellConfig(); // product-specific hook
 *   return <ProductShell config={config} />;
 * }
 * ```
 */
export function ProductShell({ config, children, contentClassName }: ProductShellProps): React.ReactElement {
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

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
          className={contentClassName ?? 'pt-16 p-6'}
        >
          {children ?? <Outlet />}
        </main>
      </div>

      {/* Active operations bar */}
      <ActiveOperationsBar
        count={config.activeOperationsCount ?? 0}
        onClick={config.onActiveOperationsClick}
      />
    </div>
  );
}
