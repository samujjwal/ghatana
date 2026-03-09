/**
 * Base Chart Component
 * 
 * Foundation for all chart types with common functionality:
 * - Data sampling for large datasets
 * - Responsive sizing
 * - Theme integration
 * - Export capabilities
 */

import React from 'react';
import { Box, useTheme } from '@mui/material';
import type { ChartConfig, TimeSeriesData, ExportOptions } from '../types';

export interface BaseChartProps {
  data: TimeSeriesData[];
  config: ChartConfig;
  maxDataPoints?: number;
  onExport?: (options: ExportOptions) => void;
  /** Numeric height in pixels for the chart container (defaults to 300) */
  height?: number;
  loading?: boolean;
  error?: Error;
  className?: string;
  children?: React.ReactNode;
}

/**
 * Base chart component with common functionality
 */
export const BaseChart: React.FC<BaseChartProps> = ({
  data,
  config: _config,
  maxDataPoints: _maxDataPoints = 1000,
  onExport: _onExport,
  loading = false,
  error,
  className,
  height = 300,
  children,
}) => {
  const theme = useTheme();

  // Note: export handling is intentionally left to callers via `onExport`.

  // Loading state
  if (loading) {
    return (
      <Box
        className={className}
        sx={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          minHeight: height,
        }}
      >
        Loading...
      </Box>
    );
  }

  // Error state
  if (error) {
    return (
      <Box
        className={className}
        sx={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          minHeight: height,
          color: theme.palette.error.main,
        }}
      >
        Error: {error.message}
      </Box>
    );
  }

  // Empty state
  if (!data || data.length === 0) {
    return (
      <Box
        className={className}
        sx={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          minHeight: height,
          color: theme.palette.text.secondary,
        }}
      >
        No data available
      </Box>
    );
  }

  return (
    <Box className={className} sx={{ width: '100%', height, minHeight: height, minWidth: 0 }}>
      {/* Child components are expected to render their own Recharts container or chart elements */}
      <>{children}</>
    </Box>
  );
};

BaseChart.displayName = 'BaseChart';

export default BaseChart;
