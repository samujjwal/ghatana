import { useState, useCallback } from 'react';

export type TimeRange = '24h' | '7d' | '30d' | '90d' | 'custom';

export interface CustomTimeRange {
  start: Date;
  end: Date;
}

export const useTimeRange = (initialRange: TimeRange = '24h') => {
  const [selectedRange, setSelectedRange] = useState<TimeRange>(initialRange);
  const [customRange, setCustomRange] = useState<CustomTimeRange>({
    start: new Date(Date.now() - 24 * 60 * 60 * 1000), // 24 hours ago
    end: new Date(),
  });

  const getTimeRange = useCallback((): { start: Date; end: Date } => {
    const now = new Date();
    const start = new Date(now);

    switch (selectedRange) {
      case '24h':
        start.setDate(start.getDate() - 1);
        break;
      case '7d':
        start.setDate(start.getDate() - 7);
        break;
      case '30d':
        start.setDate(start.getDate() - 30);
        break;
      case '90d':
        start.setDate(start.getDate() - 90);
        break;
      case 'custom':
        return {
          start: customRange.start,
          end: customRange.end,
        };
      default:
        start.setDate(start.getDate() - 1);
    }

    return { start, end: now };
  }, [selectedRange, customRange]);

  const setRange = useCallback((range: TimeRange) => {
    setSelectedRange(range);
  }, []);

  const setCustomTimeRange = useCallback((start: Date, end: Date) => {
    setSelectedRange('custom');
    setCustomRange({ start, end });
  }, []);

  return {
    selectedRange,
    setRange,
    customRange,
    setCustomTimeRange,
    getTimeRange,
  };
};
