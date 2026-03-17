/**
 * Timeline Component Types
 *
 * @module DevSecOps/Timeline/types
 */

import type { Item, Milestone, Phase } from '@ghatana/yappc-types/devsecops';

/**
 * Props for the Timeline component
 */
export interface TimelineProps {
  /** Items to display on timeline */
  items: Item[];

  /** Optional milestones to display */
  milestones?: Milestone[];

  /** Optional phases to display as background sections */
  phases?: Phase[];

  /** Start date for timeline (defaults to earliest item) */
  startDate?: Date;

  /** End date for timeline (defaults to latest item) */
  endDate?: Date;

  /** Callback when an item is clicked */
  onItemClick?: (item: Item) => void;

  /** Callback when a milestone is clicked */
  onMilestoneClick?: (milestone: Milestone) => void;

  /** View mode for timeline */
  viewMode?: TimelineViewMode;

  /** Whether the timeline is in loading state */
  loading?: boolean;

  /** Height of timeline in pixels */
  height?: number;

  /** Show today indicator line */
  showToday?: boolean;

  /** Additional CSS class name */
  className?: string;
}

/**
 * Timeline view modes for different zoom levels
 */
export type TimelineViewMode = 'day' | 'week' | 'month' | 'quarter';

/**
 * Timeline item with calculated position
 */
export interface TimelineItemPosition {
  item: Item;
  x: number;
  y: number;
  width: number;
  height: number;
  label: string;
}

/**
 * Timeline milestone with calculated position
 */
export interface TimelineMilestonePosition {
  milestone: Milestone;
  x: number;
  y: number;
  label: string;
}

/**
 * Timeline scale configuration
 */
export interface TimelineScale {
  startDate: Date;
  endDate: Date;
  viewMode: TimelineViewMode;
  pixelsPerDay: number;
  totalDays: number;
  totalWidth: number;
}

/**
 * Timeline grid configuration
 */
export interface TimelineGrid {
  majorTicks: TimelineTick[];
  minorTicks: TimelineTick[];
}

/**
 * Timeline tick mark
 */
export interface TimelineTick {
  date: Date;
  x: number;
  label: string;
  isMajor: boolean;
}
