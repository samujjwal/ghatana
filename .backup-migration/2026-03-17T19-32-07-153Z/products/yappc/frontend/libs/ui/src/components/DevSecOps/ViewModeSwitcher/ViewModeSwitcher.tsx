/**
 * ViewModeSwitcher Component
 *
 * A mode switcher for toggling between Canvas, Kanban, Timeline, and Table views.
 * Preserves filter and sort state when switching between view modes.
 *
 * @module DevSecOps/ViewModeSwitcher
 */

import { Grid3x3 as GridViewIcon } from 'lucide-react';
import { Table as TableChartIcon } from 'lucide-react';
import { Activity as TimelineIcon } from 'lucide-react';
import { Kanban as ViewKanbanIcon } from 'lucide-react';
import { Box, ToggleButton, ToggleButtonGroup, Tooltip } from '@ghatana/ui';


import type { ViewModeSwitcherProps, ViewModeMetadata } from './types';
import type { ViewMode } from '@ghatana/yappc-types/devsecops';

const VIEW_MODE_METADATA: Record<ViewMode, ViewModeMetadata> = {
  canvas: {
    id: 'canvas',
    label: 'Canvas',
    icon: GridViewIcon,
    description: 'Phase-based canvas view with drag-and-drop',
  },
  kanban: {
    id: 'kanban',
    label: 'Kanban',
    icon: ViewKanbanIcon,
    description: 'Status-based kanban board',
  },
  timeline: {
    id: 'timeline',
    label: 'Timeline',
    icon: TimelineIcon,
    description: 'Gantt-style timeline view',
  },
  table: {
    id: 'table',
    label: 'Table',
    icon: TableChartIcon,
    description: 'Detailed table view with sorting',
  },
};

const DEFAULT_MODES: ViewMode[] = ['canvas', 'kanban', 'timeline', 'table'];

/**
 * ViewModeSwitcher - Toggle between different view modes
 *
 * @param props - ViewModeSwitcher component props
 * @returns Rendered ViewModeSwitcher component
 *
 * @example
 * ```tsx
 * <ViewModeSwitcher
 *   value={viewMode}
 *   onChange={setViewMode}
 *   variant="full"
 * />
 * ```
 */
export function ViewModeSwitcher({
  value,
  onChange,
  modes = DEFAULT_MODES,
  variant = 'full',
  size = 'medium',
  disabled = false,
  orientation = 'horizontal',
  labels,
  className,
}: ViewModeSwitcherProps) {
  const handleChange = (_event: React.MouseEvent<HTMLElement>, newMode: ViewMode | null) => {
    if (newMode !== null) {
      onChange?.(newMode); // Use optional chaining to prevent crash
    }
  };

  return (
    <Box className={className}>
      <ToggleButtonGroup
        value={value}
        exclusive
        onChange={handleChange}
        size={size}
        disabled={disabled}
        orientation={orientation}
        aria-label="view mode selector"
        className="bg-white dark:bg-gray-900"
      >
        {modes.map((mode) => {
          const metadata = VIEW_MODE_METADATA[mode];
          const IconComponent = metadata.icon;
          const label = labels?.[mode] || metadata.label;

          return (
            <ToggleButton
              key={mode}
              value={mode}
              aria-label={metadata.description}
              className={variant === 'compact' ? 'px-2 gap-0' : 'px-4 gap-2'}
            >
              <Tooltip title={metadata.description} placement="top">
                <Box display="flex" alignItems="center" gap={1}>
                  <Box style={{ fontSize: size === 'small' ? '20px' : size === 'large' ? '32px' : '24px' }}>
                    <IconComponent />
                  </Box>
                  {variant === 'full' && (
                    <Box
                      component="span"
                      className="font-medium"
                      style={{ fontSize: size === 'small' ? 'var(--ds-text-sm)' : 'var(--ds-text-base)' }}
                    >
                      {label}
                    </Box>
                  )}
                </Box>
              </Tooltip>
            </ToggleButton>
          );
        })}
      </ToggleButtonGroup>
    </Box>
  );
}
