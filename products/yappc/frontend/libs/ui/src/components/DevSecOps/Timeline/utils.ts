/**
 * Timeline Utility Functions
 *
 * Pure calculation functions for timeline positioning and layout.
 *
 * @module DevSecOps/Timeline/utils
 */

import type {
  TimelineScale,
  TimelineViewMode,
  TimelineGrid,
  TimelineTick,
  TimelineItemPosition,
  TimelineMilestonePosition,
} from './types';
import type { Item, Milestone } from '@ghatana/yappc-types/devsecops';

/**
 * Timeline utility functions
 */
export class TimelineUtils {
  /**
   * Normalize various date inputs into valid Date objects
   *
   * @param value - Date-compatible input
   * @returns Valid Date instance or null if input is invalid
   */
  static parseDate(value: Date | string | number | null | undefined): Date | null {
    if (value == null) {
      return null;
    }

    if (value instanceof Date) {
      return Number.isNaN(value.getTime()) ? null : value;
    }

    const parsed = new Date(value);
    return Number.isNaN(parsed.getTime()) ? null : parsed;
  }

  /**
   * Calculate timeline scale based on date range and view mode
   *
   * @param startDate - Start date of timeline
   * @param endDate - End date of timeline
   * @param viewMode - View mode (day/week/month/quarter)
   * @param width - Available width in pixels
   * @returns Timeline scale configuration
   */
  static calculateScale(
    startDate: Date,
    endDate: Date,
    viewMode: TimelineViewMode,
    width: number
  ): TimelineScale {
    const totalDays = Math.ceil(
      (endDate.getTime() - startDate.getTime()) / (1000 * 60 * 60 * 24)
    );

    // Minimum days to display based on view mode
    const minDays = viewMode === 'day' ? 7 : viewMode === 'week' ? 30 : viewMode === 'month' ? 90 : 365;
    const displayDays = Math.max(totalDays, minDays);

    const pixelsPerDay = width / displayDays;

    return {
      startDate,
      endDate,
      viewMode,
      pixelsPerDay,
      totalDays: displayDays,
      totalWidth: width,
    };
  }

  /**
   * Convert date to X position on timeline
   *
   * @param date - Date to convert
   * @param scale - Timeline scale
   * @returns X position in pixels
   */
  static dateToX(date: Date | string | number, scale: TimelineScale): number {
    const normalizedDate = this.parseDate(date);
    if (!normalizedDate) {
      return 0;
    }

    const daysSinceStart =
      (normalizedDate.getTime() - scale.startDate.getTime()) / (1000 * 60 * 60 * 24);
    return daysSinceStart * scale.pixelsPerDay;
  }

  /**
   * Calculate grid ticks for timeline
   *
   * @param scale - Timeline scale
   * @returns Grid configuration with major and minor ticks
   */
  static calculateGrid(scale: TimelineScale): TimelineGrid {
    const { startDate, endDate, viewMode, pixelsPerDay } = scale;
    const majorTicks: TimelineTick[] = [];
    const minorTicks: TimelineTick[] = [];

    let currentDate = new Date(startDate);

    // Set interval based on view mode
    const majorInterval =
      viewMode === 'day' ? 1 : viewMode === 'week' ? 7 : viewMode === 'month' ? 30 : 90;
    const minorInterval =
      viewMode === 'day' ? 0.5 : viewMode === 'week' ? 1 : viewMode === 'month' ? 7 : 30;

    // Generate major ticks
    while (currentDate <= endDate) {
      const x = this.dateToX(currentDate, scale);
      majorTicks.push({
        date: new Date(currentDate),
        x,
        label: this.formatDateForViewMode(currentDate, viewMode),
        isMajor: true,
      });

      // Add days based on interval
      currentDate = new Date(currentDate.getTime() + majorInterval * 24 * 60 * 60 * 1000);
    }

    // Generate minor ticks (if space allows)
    if (pixelsPerDay > 2) {
      currentDate = new Date(startDate);
      while (currentDate <= endDate) {
        const x = this.dateToX(currentDate, scale);
        minorTicks.push({
          date: new Date(currentDate),
          x,
          label: '',
          isMajor: false,
        });

        currentDate = new Date(currentDate.getTime() + minorInterval * 24 * 60 * 60 * 1000);
      }
    }

    return { majorTicks, minorTicks };
  }

  /**
   * Format date based on view mode
   *
   * @param date - Date to format
   * @param viewMode - View mode
   * @returns Formatted date string
   */
  static formatDateForViewMode(date: Date, viewMode: TimelineViewMode): string {
    const options: Intl.DateTimeFormatOptions =
      viewMode === 'day'
        ? { month: 'short', day: 'numeric' }
        : viewMode === 'week'
          ? { month: 'short', day: 'numeric' }
          : viewMode === 'month'
            ? { month: 'short', year: 'numeric' }
            : { month: 'short', year: 'numeric' };

    return new Intl.DateTimeFormat('en-US', options).format(date);
  }

  /**
   * Calculate item positions on timeline
   *
   * @param items - Items to position
   * @param scale - Timeline scale
   * @param rowHeight - Height of each row
   * @returns Array of positioned items
   */
  static calculateItemPositions(
    items: Item[],
    scale: TimelineScale,
    rowHeight: number
  ): TimelineItemPosition[] {
    const positions: TimelineItemPosition[] = [];
    const rows: Array<{ endX: number }> = [];

    // Sort items by start date
    const sortedItems = [...items].sort((a, b) => {
      const aDate = this.parseDate(a.startDate ?? a.createdAt)?.getTime() ?? 0;
      const bDate = this.parseDate(b.startDate ?? b.createdAt)?.getTime() ?? 0;
      return aDate - bDate;
    });

    for (const item of sortedItems) {
      const startDate = this.parseDate(item.startDate ?? item.createdAt);
      if (!startDate) continue;

      const endDate =
        this.parseDate(item.dueDate ?? item.updatedAt) ?? startDate;

      const x = this.dateToX(startDate, scale);
      const width = Math.max(
        this.dateToX(endDate, scale) - x,
        scale.pixelsPerDay * 2 // Minimum 2 days width
      );

      // Find available row
      let rowIndex = 0;
      for (let i = 0; i < rows.length; i++) {
        // eslint-disable-next-line security/detect-object-injection
        if (rows[i].endX < x) {
          rowIndex = i;
          break;
        }
        rowIndex = i + 1;
      }

      // Create or update row
      // eslint-disable-next-line security/detect-object-injection
      if (!rows[rowIndex]) {
        // eslint-disable-next-line security/detect-object-injection
        rows[rowIndex] = { endX: x + width };
      } else {
        // eslint-disable-next-line security/detect-object-injection
        rows[rowIndex].endX = x + width;
      }

      positions.push({
        item,
        x,
        y: rowIndex * rowHeight,
        width,
        height: rowHeight - 8, // 8px gap
        label: item.title,
      });
    }

    return positions;
  }

  /**
   * Calculate milestone positions on timeline
   *
   * @param milestones - Milestones to position
   * @param scale - Timeline scale
   * @returns Array of positioned milestones
   */
  static calculateMilestonePositions(
    milestones: Milestone[],
    scale: TimelineScale
  ): TimelineMilestonePosition[] {
    return milestones
      .map((milestone) => {
        const dueDate = this.parseDate(milestone.dueDate);
        if (!dueDate) {
          return null;
        }

        return {
          milestone,
          x: this.dateToX(dueDate, scale),
          y: 0,
          label: milestone.title,
        } satisfies TimelineMilestonePosition;
      })
      .filter((pos): pos is TimelineMilestonePosition => pos !== null);
  }

  /**
   * Get auto date range from items
   *
   * @param items - Items to analyze
   * @returns Start and end dates
   */
  static getAutoDateRange(items: Item[]): { startDate: Date; endDate: Date } {
    const dates = items
      .flatMap((item) => [
        this.parseDate(item.startDate ?? item.createdAt),
        this.parseDate(item.dueDate ?? item.updatedAt),
      ])
      .filter((d): d is Date => d != null);

    if (dates.length === 0) {
      const today = new Date();
      const monthAgo = new Date(today);
      monthAgo.setMonth(monthAgo.getMonth() - 1);
      return { startDate: monthAgo, endDate: today };
    }

    const minDate = new Date(Math.min(...dates.map((d) => d.getTime())));
    const maxDate = new Date(Math.max(...dates.map((d) => d.getTime())));

    // Add 10% padding
    const range = maxDate.getTime() - minDate.getTime();
    const padding = range * 0.1;

    return {
      startDate: new Date(minDate.getTime() - padding),
      endDate: new Date(maxDate.getTime() + padding),
    };
  }
}
