/**
 * CapabilitySidebar — registry-driven collapsible sidebar.
 *
 * Renders a collapsible left sidebar with:
 * - Product logo and name header
 * - Registry-driven navigation (via RouteCapabilityNav)
 * - Optional sidebar footer slot (tenant selector, status indicator, etc.)
 * - Mobile overlay support
 *
 * @doc.type component
 * @doc.purpose Registry-driven collapsible sidebar for product shell
 * @doc.layer platform
 * @doc.pattern Organism
 */
import React from 'react';
import { ChevronLeft, ChevronRight, X } from 'lucide-react';
import type { ProductShellConfig } from '../types';
import { RouteCapabilityNav } from './RouteCapabilityNav';

interface CapabilitySidebarProps {
  config: ProductShellConfig;
  isCollapsed: boolean;
  onToggle: () => void;
  isMobileOpen: boolean;
  onMobileClose: () => void;
}

function cn(...classes: (string | false | null | undefined)[]): string {
  return classes.filter(Boolean).join(' ');
}

/**
 * Collapsible sidebar with capability-driven navigation.
 *
 * - Desktop: permanently visible, collapsible to icon-only mode
 * - Mobile: hidden by default, shown as an overlay when `isMobileOpen=true`
 */
export function CapabilitySidebar({
  config,
  isCollapsed,
  onToggle,
  isMobileOpen,
  onMobileClose,
}: CapabilitySidebarProps): React.ReactElement {
  const SidebarContent = (
    <div className="flex h-full flex-col">
      {/* Header */}
      <div className="flex h-16 items-center justify-between px-4 border-b border-gray-200 dark:border-gray-700">
        {!isCollapsed && (
          <div className="flex items-center gap-2 min-w-0">
            {config.logo && (
              <span className="shrink-0" aria-hidden="true">
                {config.logo}
              </span>
            )}
            <span className="truncate text-base font-semibold text-gray-900 dark:text-gray-100">
              {config.productName}
            </span>
          </div>
        )}
        {isCollapsed && config.logo && (
          <span aria-label={config.productName}>
            {config.logo}
          </span>
        )}

        {/* Desktop collapse toggle */}
        <button
          type="button"
          onClick={onToggle}
          aria-label={isCollapsed ? 'Expand sidebar' : 'Collapse sidebar'}
          className="hidden lg:flex h-7 w-7 items-center justify-center rounded-md text-gray-500 transition-colors hover:bg-gray-100 hover:text-gray-700 dark:hover:bg-gray-700 dark:hover:text-gray-200"
        >
          {isCollapsed ? (
            <ChevronRight className="h-4 w-4" aria-hidden="true" />
          ) : (
            <ChevronLeft className="h-4 w-4" aria-hidden="true" />
          )}
        </button>

        {/* Mobile close button */}
        <button
          type="button"
          onClick={onMobileClose}
          aria-label="Close navigation menu"
          className="lg:hidden flex h-7 w-7 items-center justify-center rounded-md text-gray-500 transition-colors hover:bg-gray-100 hover:text-gray-700"
        >
          <X className="h-4 w-4" aria-hidden="true" />
        </button>
      </div>

      {/* Navigation */}
      <div className="flex-1 overflow-y-auto p-3">
        <RouteCapabilityNav
          routes={config.routes}
          config={{
            currentRole: config.currentRole,
            roleOrder: config.roleOrder,
          }}
          collapsed={isCollapsed}
        />
      </div>

      {/* Footer slot */}
      {config.sidebarFooter && !isCollapsed && (
        <div className="border-t border-gray-200 p-3 dark:border-gray-700">
          {config.sidebarFooter}
        </div>
      )}
    </div>
  );

  return (
    <>
      {/* Desktop sidebar */}
      <aside
        aria-label="Primary navigation"
        className={cn(
          'hidden lg:flex flex-col fixed top-0 left-0 h-full border-r border-gray-200 bg-white dark:border-gray-700 dark:bg-gray-900 transition-all duration-300 z-20',
          isCollapsed ? 'w-16' : 'w-64'
        )}
      >
        {SidebarContent}
      </aside>

      {/* Mobile overlay backdrop */}
      {isMobileOpen && (
        <div
          className="fixed inset-0 z-30 bg-black/50 lg:hidden"
          aria-hidden="true"
          onClick={onMobileClose}
        />
      )}

      {/* Mobile sidebar */}
      {isMobileOpen && (
        <aside
          aria-label="Primary navigation"
          className="fixed top-0 left-0 z-40 h-full w-64 flex-col border-r border-gray-200 bg-white dark:border-gray-700 dark:bg-gray-900 lg:hidden flex"
        >
          {SidebarContent}
        </aside>
      )}
    </>
  );
}
