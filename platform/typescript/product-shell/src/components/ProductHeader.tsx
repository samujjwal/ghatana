/**
 * ProductHeader — top header with search, notifications, and role/mode selector.
 *
 * Renders the fixed top header bar containing:
 * - Mobile hamburger menu button
 * - Product name (when sidebar is collapsed or mobile)
 * - Search trigger button
 * - Notification center
 * - View mode / role selector
 * - Additional product-specific header actions slot
 *
 * @doc.type component
 * @doc.purpose Top header bar for product shell
 * @doc.layer platform
 * @doc.pattern Organism
 */
import React from 'react';
import { Search, Menu } from 'lucide-react';
import type { ProductShellConfig } from '../types';
import { ProductViewModeSelector } from './ProductViewModeSelector';
import { NotificationCenter } from './NotificationCenter';

interface ProductHeaderProps {
  config: ProductShellConfig;
  sidebarCollapsed: boolean;
  onMenuClick: () => void;
}

/**
 * Fixed top header bar.
 *
 * The left slot shows the mobile menu button. The right slot shows search,
 * notifications, mode selector, and optional product actions.
 */
export function ProductHeader({ config, sidebarCollapsed, onMenuClick }: ProductHeaderProps): React.ReactElement {
  return (
    <header
      className="fixed top-0 z-30 flex h-16 items-center justify-between border-b border-gray-200 bg-white px-4 dark:border-gray-700 dark:bg-gray-900"
      style={{
        left: sidebarCollapsed ? '4rem' : '16rem',
        right: 0,
      }}
    >
      {/* Left slot — mobile menu */}
      <div className="flex items-center gap-3">
        <button
          type="button"
          onClick={onMenuClick}
          aria-label="Open navigation menu"
          className="flex h-8 w-8 items-center justify-center rounded-md text-gray-500 transition-colors hover:bg-gray-100 hover:text-gray-700 dark:hover:bg-gray-700 dark:hover:text-gray-200 lg:hidden"
        >
          <Menu className="h-5 w-5" aria-hidden="true" />
        </button>
      </div>

      {/* Right slot — actions */}
      <div className="flex items-center gap-2">
        {/* Search trigger */}
        {config.onSearch && (
          <button
            type="button"
            onClick={config.onSearch}
            aria-label="Open global search"
            className="flex h-8 w-8 items-center justify-center rounded-full text-gray-600 transition-colors hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-gray-700"
          >
            <Search className="h-4 w-4" aria-hidden="true" />
          </button>
        )}

        {/* Notification center */}
        <NotificationCenter
          notifications={config.notifications ?? []}
          onAction={config.onNotificationAction}
        />

        {/* Mode selector */}
        {(config.availableRoles ?? Object.keys(config.roleLabels ?? {})).length > 1 && (
          <ProductViewModeSelector config={config} />
        )}

        {/* Additional product-specific actions */}
        {config.headerActions}
      </div>
    </header>
  );
}
