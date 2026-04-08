/**
 * Mobile Navigation Component
 *
 * Mobile-specific bottom navigation bar for touch-friendly navigation.
 * Provides quick access to primary app features on mobile devices.
 *
 * @doc.type component
 * @doc.purpose Mobile bottom navigation bar
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { ReactNode, useState, useCallback } from 'react';
import { 
  Home as HomeIcon, 
  Folder as ProjectsIcon, 
  Settings as SettingsIcon,
  Menu as MenuIcon,
  X as CloseIcon,
} from 'lucide-react';
import { Typography, Button, Box, Chip } from '@ghatana/design-system';
import { useResponsive } from '../../hooks/useResponsive';

// ============================================================================
// Types
// ============================================================================

export interface NavItem {
  id: string;
  label: string;
  icon: ReactNode;
  badge?: number;
  onClick: () => void;
}

export interface MobileNavigationProps {
  items: NavItem[];
  activeItemId?: string;
  className?: string;
}

// ============================================================================
// Mobile Navigation Component
// ============================================================================

/**
 * Mobile Navigation Component
 */
export function MobileNavigation({ items, activeItemId, className = '' }: MobileNavigationProps): ReactNode {
  const { isMobile, isTablet } = useResponsive();
  const [expanded, setExpanded] = useState(false);

  // Only show on mobile/tablet
  if (!isMobile && !isTablet) {
    return null;
  }

  const handleItemClick = useCallback((item: NavItem) => {
    item.onClick();
    setExpanded(false);
  }, []);

  return (
    <div className={`fixed bottom-0 left-0 right-0 z-50 bg-white dark:bg-gray-900 border-t border-gray-200 dark:border-gray-800 safe-all ${className}`}>
      {/* Expanded menu */}
      {expanded && (
        <div className="absolute bottom-full left-0 right-0 bg-white dark:bg-gray-900 border-t border-gray-200 dark:border-gray-800 p-4 shadow-lg">
          <div className="flex items-center justify-between mb-4">
            <Typography className="font-semibold">All Navigation</Typography>
            <Button
              size="sm"
              variant="text"
              onClick={() => setExpanded(false)}
            >
              <CloseIcon className="w-5 h-5" />
            </Button>
          </div>
          <div className="grid grid-cols-3 gap-2">
            {items.map(item => (
              <button
                key={item.id}
                onClick={() => handleItemClick(item)}
                className={`flex flex-col items-center p-3 rounded-lg transition-colors ${
                  activeItemId === item.id
                    ? 'bg-blue-50 dark:bg-blue-900/20 text-blue-600 dark:text-blue-400'
                    : 'hover:bg-gray-100 dark:hover:bg-gray-800'
                }`}
              >
                <div className="relative mb-1">
                  {item.icon}
                  {item.badge && item.badge > 0 && (
                    <Chip
                      size="sm"
                      label={item.badge > 99 ? '99+' : item.badge}
                      className="absolute -top-2 -right-2 min-w-[20px] h-5 flex items-center justify-center text-xs"
                    />
                  )}
                </div>
                <Typography className="text-xs">{item.label}</Typography>
              </button>
            ))}
          </div>
        </div>
      )}

      {/* Bottom navigation bar */}
      <div className="flex items-center justify-around px-2 py-2">
        {items.slice(0, 4).map(item => (
          <button
            key={item.id}
            onClick={() => handleItemClick(item)}
            className={`flex flex-col items-center p-2 min-w-[48px] min-h-[48px] rounded-lg transition-colors relative ${
              activeItemId === item.id
                ? 'text-blue-600 dark:text-blue-400'
                : 'text-gray-600 dark:text-gray-400'
            }`}
          >
            <div className="relative">
              {item.icon}
              {item.badge && item.badge > 0 && (
                <Chip
                  size="sm"
                  label={item.badge > 99 ? '99+' : item.badge}
                  className="absolute -top-1 -right-1 min-w-[18px] h-4 flex items-center justify-center text-xs"
                />
              )}
            </div>
            <Typography className="text-xs mt-1">{item.label}</Typography>
          </button>
        ))}

        {/* More button for additional items */}
        {items.length > 4 && (
          <button
            onClick={() => setExpanded(!expanded)}
            className={`flex flex-col items-center p-2 min-w-[48px] min-h-[48px] rounded-lg transition-colors ${
              expanded
                ? 'text-blue-600 dark:text-blue-400'
                : 'text-gray-600 dark:text-gray-400'
            }`}
          >
            {expanded ? <CloseIcon className="w-6 h-6" /> : <MenuIcon className="w-6 h-6" />}
            <Typography className="text-xs mt-1">{expanded ? 'Close' : 'More'}</Typography>
          </button>
        )}
      </div>
    </div>
  );
}
