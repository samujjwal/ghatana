/**
 * Timeline Component
 *
 * Gantt-style timeline for visualizing items and milestones over time.
 *
 * @module DevSecOps/Timeline
 */

import { Flag as FlagIcon } from 'lucide-react';
import { Box, LinearProgress, Surface as Paper, Tooltip, Typography } from '@ghatana/ui';
import { useMemo, useRef } from 'react';

import { TimelineUtils } from './utils';

import type { TimelineProps } from './types';

const ROW_HEIGHT = 48;
const HEADER_HEIGHT = 60;

/**
 * Timeline - Gantt-style visualization component
 *
 * Displays items and milestones on a timeline with configurable zoom levels.
 *
 * @param props - Timeline component props
 * @returns Rendered Timeline component
 *
 * @example
 * ```tsx
 * <Timeline
 *   items={filteredItems}
 *   milestones={milestones}
 *   viewMode="week"
 *   onItemClick={handleItemClick}
 *   showToday
 * />
 * ```
 */
// eslint-disable-next-line max-lines-per-function
export function Timeline({
  items,
  milestones = [],
  phases = [],
  startDate: providedStartDate,
  endDate: providedEndDate,
  onItemClick,
  onMilestoneClick,
  viewMode = 'week',
  loading = false,
  height = 600,
  showToday = true,
  className,
}: TimelineProps) {
  const containerRef = useRef<HTMLDivElement>(null);

  // Calculate date range
  const { startDate, endDate } = useMemo(() => {
    if (providedStartDate && providedEndDate) {
      return { startDate: providedStartDate, endDate: providedEndDate };
    }
    return TimelineUtils.getAutoDateRange(items);
  }, [items, providedStartDate, providedEndDate]);

  // Calculate timeline scale
  const scale = useMemo(() => {
    const width = containerRef.current?.offsetWidth || 1000;
    return TimelineUtils.calculateScale(startDate, endDate, viewMode, width);
  }, [startDate, endDate, viewMode]);

  // Calculate grid
  const grid = useMemo(() => TimelineUtils.calculateGrid(scale), [scale]);

  // Calculate item positions
  const itemPositions = useMemo(
    () => TimelineUtils.calculateItemPositions(items, scale, ROW_HEIGHT),
    [items, scale]
  );

  // Calculate milestone positions
  const milestonePositions = useMemo(
    () => TimelineUtils.calculateMilestonePositions(milestones, scale),
    [milestones, scale]
  );

  // Calculate today's position
  const todayX = useMemo(() => {
    if (!showToday) return null;
    const today = new Date();
    if (today < startDate || today > endDate) return null;
    return TimelineUtils.dateToX(today, scale);
  }, [showToday, startDate, endDate, scale]);

  // Calculate timeline height
  const timelineHeight = useMemo(() => {
    const maxRow = Math.max(...itemPositions.map((p) => p.y + p.height), 0);
    return Math.max(maxRow + ROW_HEIGHT, height - HEADER_HEIGHT);
  }, [itemPositions, height]);

  if (loading) {
    return (
      <Box className="w-full p-4">
        <LinearProgress />
        <Typography as="p" className="text-sm" color="text.secondary" className="mt-4 text-center">
          Loading timeline...
        </Typography>
      </Box>
    );
  }

  return (
    <Paper
      ref={containerRef}
      className={className}
      className="overflow-auto relative bg-white dark:bg-gray-900"
    >
      {/* Header with date labels */}
      <Box
        className="sticky top-[0px] z-[2] bg-white dark:bg-gray-900 border-gray-200 dark:border-gray-700" style={{ borderBottom: '2', height: HEADER_HEIGHT }} >
        <svg width="100%" height={HEADER_HEIGHT}>
          {/* Major ticks */}
          {grid.majorTicks.map((tick, i) => (
            <g key={`major-${i}`}>
              <line
                x1={tick.x}
                y1={0}
                x2={tick.x}
                y2={HEADER_HEIGHT}
                stroke="var(--ds-border-color, #e0e0e0)"
                strokeWidth={1}
              />
              <text
                x={tick.x + 8}
                y={20}
                fontSize={12}
                fill="var(--ds-text-secondary, #666)"
                fontWeight={500}
              >
                {tick.label}
              </text>
            </g>
          ))}

          {/* Minor ticks */}
          {grid.minorTicks.map((tick, i) => (
            <line
              key={`minor-${i}`}
              x1={tick.x}
              y1={HEADER_HEIGHT - 15}
              x2={tick.x}
              y2={HEADER_HEIGHT}
              stroke="var(--ds-border-color, #e0e0e0)"
              strokeWidth={0.5}
              opacity={0.5}
            />
          ))}
        </svg>
      </Box>

      {/* Timeline content */}
      <Box className="relative" style={{ minHeight: 'timelineHeight' }} >
        <svg width="100%" height={timelineHeight}>
          {/* Grid lines */}
          {grid.majorTicks.map((tick, i) => (
            <line
              key={`grid-${i}`}
              x1={tick.x}
              y1={0}
              x2={tick.x}
              y2={timelineHeight}
              stroke="var(--ds-border-color, #e0e0e0)"
              strokeWidth={1}
              opacity={0.3}
            />
          ))}

          {/* Today indicator */}
          {todayX !== null && (
            <line
              x1={todayX}
              y1={0}
              x2={todayX}
              y2={timelineHeight}
              stroke="var(--ds-error-500, #ef4444)"
              strokeWidth={2}
              strokeDasharray="4 4"
            />
          )}

          {/* Phase backgrounds - Note: Phase type doesn't include start/end dates */}
          {/* Phase rendering removed until Phase type includes date properties */}

          {/* Milestone markers */}
          {milestonePositions.map((pos, i) => (
            <Tooltip key={`milestone-${i}`} title={pos.label} placement="top">
              <g
                onClick={() => onMilestoneClick?.(pos.milestone)}
                style={{ cursor: onMilestoneClick ? 'pointer' : 'default', left: 'pos.x - 12' }}
              >
                <line
                  x1={pos.x}
                  y1={0}
                  x2={pos.x}
                  y2={timelineHeight}
                  stroke="var(--ds-warning-500, #f59e0b)"
                  strokeWidth={2}
                />
                <FlagIcon
                  className="absolute top-[8px] text-amber-600 text-2xl" />
              </g>
            </Tooltip>
          ))}
        </svg>

        {/* Item bars */}
        {itemPositions.map((pos, i) => (
          <Tooltip key={`item-${i}`} title={pos.label} placement="top">
            <Box
              onClick={() => onItemClick?.(pos.item)}
              className={`absolute flex items-center px-2 overflow-hidden rounded transition-all duration-200 ease-in-out hover:opacity-80 hover:-translate-y-0.5 hover:shadow-md ${onItemClick ? 'cursor-pointer' : 'cursor-default'}`}
              style={{
                left: pos.x,
                top: pos.y + 4,
                width: pos.width,
                height: pos.height,
                backgroundColor: `var(--ds-priority-${pos.item.priority}, #3b82f6)`,
              }}
            >
              <Typography
                as="span" className="text-xs text-gray-500"
                className="font-medium whitespace-nowrap overflow-hidden text-ellipsis text-white"
              >
                {pos.label}
              </Typography>
            </Box>
          </Tooltip>
        ))}
      </Box>
    </Paper>
  );
}
