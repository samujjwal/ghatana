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
import { useTranslation } from '@ghatana/i18n';

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
  const { t } = useTranslation('common');

  // Only show on mobile/tablet
  if (!isMobile && !isTablet) {
    return null;
  }

  const handleItemClick = useCallback((item: NavItem) => {
    item.onClick();
    setExpanded(false);
  }, []);

  return (
    <div className={`fixed bottom-0 left-0 right-0 z-50 bg-white dark:bg-surface border-t border-border dark:border-border safe-all ${className}`}>
      {/* Expanded menu */}
      {expanded && (
        <div className="absolute bottom-full left-0 right-0 bg-white dark:bg-surface border-t border-border dark:border-border p-4 shadow-lg">
          <div className="flex items-center justify-between mb-4">
            <Typography className="font-semibold">{t('mobileNav.allNavigation')}</Typography>
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
              <Button
                key={item.id}
                onClick={() => handleItemClick(item)}
                className={`flex flex-col items-center p-3 rounded-lg transition-colors ${
                  activeItemId === item.id
                    ? 'bg-info-bg dark:bg-info-bg/20 text-info-color dark:text-info-color'
                    : 'hover:bg-surface-muted dark:hover:bg-surface'
                }`}
                variant="text"
                size="sm"
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
              </Button>
            ))}
          </div>
        </div>
      )}

      {/* Bottom navigation bar */}
      <div className="flex items-center justify-around px-2 py-2">
        {items.slice(0, 4).map(item => (
          <Button
            key={item.id}
            onClick={() => handleItemClick(item)}
            className={`flex flex-col items-center p-2 min-w-[48px] min-h-[48px] rounded-lg transition-colors relative ${
              activeItemId === item.id
                ? 'text-info-color dark:text-info-color'
                : 'text-fg-muted dark:text-fg-muted'
            }`}
            variant="text"
            size="sm"
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
          </Button>
        ))}

        {/* More button for additional items */}
        {items.length > 4 && (
          <Button
            onClick={() => setExpanded(!expanded)}
            className={`flex flex-col items-center p-2 min-w-[48px] min-h-[48px] rounded-lg transition-colors ${
              expanded
                ? 'text-info-color dark:text-info-color'
                : 'text-fg-muted dark:text-fg-muted'
            }`}
            variant="text"
            size="sm"
          >
            {expanded ? <CloseIcon className="w-6 h-6" /> : <MenuIcon className="w-6 h-6" />}
            <Typography className="text-xs mt-1">{expanded ? 'Close' : 'More'}</Typography>
          </Button>
        )}
      </div>
    </div>
  );
}
