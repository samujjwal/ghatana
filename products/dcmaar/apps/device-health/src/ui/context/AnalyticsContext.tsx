/**
 * @fileoverview Analytics Context
 * 
 * Provides shared analytics state across the application including
 * time range selection and auto-refresh settings.
 */

import React, { createContext, useContext, useState, useCallback, ReactNode } from 'react';
import type { TimeRange } from '../components/filters/TimeRangeSelector';

interface AnalyticsContextValue {
  timeRange: TimeRange;
  setTimeRange: (range: TimeRange) => void;
  autoRefresh: boolean;
  setAutoRefresh: (enabled: boolean) => void;
  refreshInterval: number; // in milliseconds
  setRefreshInterval: (interval: number) => void;
  triggerRefresh: () => void;
  refreshTrigger: number; // timestamp to trigger manual refresh
}

const AnalyticsContext = createContext<AnalyticsContextValue | undefined>(undefined);

interface AnalyticsProviderProps {
  children: ReactNode;
}

export const AnalyticsProvider: React.FC<AnalyticsProviderProps> = ({ children }) => {
  const [timeRange, setTimeRange] = useState<TimeRange>({
    from: Date.now() - 24 * 60 * 60 * 1000, // Last 24 hours
    to: Date.now(),
    preset: '24h',
  });

  const [autoRefresh, setAutoRefresh] = useState(true);
  const [refreshInterval, setRefreshInterval] = useState(5000); // 5 seconds default
  const [refreshTrigger, setRefreshTrigger] = useState(Date.now());

  const triggerRefresh = useCallback(() => {
    setRefreshTrigger(Date.now());
  }, []);

  const value: AnalyticsContextValue = {
    timeRange,
    setTimeRange,
    autoRefresh,
    setAutoRefresh,
    refreshInterval,
    setRefreshInterval,
    triggerRefresh,
    refreshTrigger,
  };

  return (
    <AnalyticsContext.Provider value={value}>
      {children}
    </AnalyticsContext.Provider>
  );
};

export const useAnalyticsContext = (): AnalyticsContextValue => {
  const context = useContext(AnalyticsContext);
  if (!context) {
    throw new Error('useAnalyticsContext must be used within an AnalyticsProvider');
  }
  return context;
};
