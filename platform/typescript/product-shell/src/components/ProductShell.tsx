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

const shellLayoutClasses = {
  sidebarExpanded: 'lg:ml-64',
  sidebarCollapsed: 'lg:ml-16',
  transition: 'transition-all duration-300',
  contentPadding: 'pt-16 p-6',
  fullHeight: 'min-h-screen',
  bgLight: 'bg-gray-50',
  bgDark: 'dark:bg-gray-950',
} as const;

export interface ProductShellProps {
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

export interface ProductShellState {
  readonly sidebarCollapsed: boolean;
  readonly mobileMenuOpen: boolean;
  readonly toggleSidebar: () => void;
  readonly openMobileMenu: () => void;
  readonly closeMobileMenu: () => void;
}

export function useProductShellState(): ProductShellState {
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  return {
    sidebarCollapsed,
    mobileMenuOpen,
    toggleSidebar: () => setSidebarCollapsed((prev) => !prev),
    openMobileMenu: () => setMobileMenuOpen(true),
    closeMobileMenu: () => setMobileMenuOpen(false),
  };
}

export interface ProductShellLayoutProps extends ProductShellProps {
  readonly state: ProductShellState;
}

export function ProductShellLayout({
  config,
  children,
  contentClassName,
  mainContentId,
  mainContentTabIndex,
  mainContentRole,
  state,
}: ProductShellLayoutProps): React.ReactElement {
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
    <div className={`${shellLayoutClasses.fullHeight} ${shellLayoutClasses.bgLight} ${shellLayoutClasses.bgDark}`}>
      <CapabilitySidebar
        config={config}
        isCollapsed={state.sidebarCollapsed}
        onToggle={state.toggleSidebar}
        isMobileOpen={state.mobileMenuOpen}
        onMobileClose={state.closeMobileMenu}
      />

      <div
        className={
          state.sidebarCollapsed
            ? `${shellLayoutClasses.sidebarCollapsed} ${shellLayoutClasses.transition}`
            : `${shellLayoutClasses.sidebarExpanded} ${shellLayoutClasses.transition}`
        }
      >
        <ProductHeader
          config={config}
          sidebarCollapsed={state.sidebarCollapsed}
          onMenuClick={state.openMobileMenu}
        />

        <main
          {...mainContentProps}
          className={contentClassName ?? shellLayoutClasses.contentPadding}
        >
          {children}
        </main>
      </div>

      <ActiveOperationsBar
        count={config.activeOperationsCount ?? 0}
        {...(config.onActiveOperationsClick ? { onClick: config.onActiveOperationsClick } : {})}
      />
    </div>
  );
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
  const state = useProductShellState();
  const layoutProps: Omit<ProductShellLayoutProps, 'children' | 'config' | 'state'> = {};

  if (contentClassName !== undefined) {
    layoutProps.contentClassName = contentClassName;
  }
  if (mainContentId !== undefined) {
    layoutProps.mainContentId = mainContentId;
  }
  if (mainContentTabIndex !== undefined) {
    layoutProps.mainContentTabIndex = mainContentTabIndex;
  }
  if (mainContentRole !== undefined) {
    layoutProps.mainContentRole = mainContentRole;
  }

  return (
    <ProductShellLayout
      config={config}
      state={state}
      {...layoutProps}
    >
      {children}
    </ProductShellLayout>
  );
}
