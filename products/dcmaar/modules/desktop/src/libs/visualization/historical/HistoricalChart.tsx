/**
 * Historical Chart Component
 * 
 * Specialized chart for historical data analysis with:
 * - Time range selection
 * - Comparison mode
 * - Data aggregation controls
 * - Export functionality
 */

import React, { useState, useCallback, useEffect, useMemo } from 'react';
import {
  Box,
  Paper,
  ToggleButtonGroup,
  ToggleButton,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  Button,
  CircularProgress,
  useTheme,
} from '@mui/material';
import { DateTimePicker } from '@mui/x-date-pickers/DateTimePicker';
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider';
import { AdapterDateFns } from '@mui/x-date-pickers/AdapterDateFns';
import { LineChart } from '../charts/LineChart';
import { historicalDataService } from './HistoricalDataService';
import type { HistoricalQuery, HistoricalResult, TimeSeriesData } from '../types';

export interface HistoricalChartProps {
  metric: string;
  defaultTimeRange?: '1h' | '24h' | '7d' | '30d' | 'custom';
  defaultAggregation?: 'avg' | 'sum' | 'min' | 'max' | 'count';
  onExport?: (data: TimeSeriesData[]) => void;
}

/**
 * Historical chart with time range and aggregation controls
 */
export const HistoricalChart: React.FC<HistoricalChartProps> = ({
  metric,
  defaultTimeRange = '24h',
  defaultAggregation = 'avg',
  onExport,
}) => {
  const theme = useTheme();
  const [timeRange, setTimeRange] = useState(defaultTimeRange);
  const [aggregation, setAggregation] = useState(defaultAggregation);
  const [startTime, setStartTime] = useState<Date | null>(null);
  const [endTime, setEndTime] = useState<Date | null>(null);
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState<HistoricalResult | null>(null);
  const [error, setError] = useState<Error | null>(null);

  // Calculate time range
  const timeRangeMs = useMemo(() => {
    switch (timeRange) {
      case '1h':
        return 60 * 60 * 1000;
      case '24h':
        return 24 * 60 * 60 * 1000;
      case '7d':
        return 7 * 24 * 60 * 60 * 1000;
      case '30d':
        return 30 * 24 * 60 * 60 * 1000;
      default:
        return 24 * 60 * 60 * 1000;
    }
  }, [timeRange]);

  // Fetch data
  const fetchData = useCallback(async () => {
    setLoading(true);
    setError(null);

    try {
      const now = Date.now();
      const query: HistoricalQuery = {
        metric,
        startTime: timeRange === 'custom' && startTime ? startTime.getTime() : now - timeRangeMs,
        endTime: timeRange === 'custom' && endTime ? endTime.getTime() : now,
        aggregation,
        interval: timeRangeMs > 24 * 60 * 60 * 1000 ? 60 * 60 * 1000 : 60 * 1000, // 1h or 1m
      };

      const result = await historicalDataService.query(query);
      setData(result);
    } catch (err) {
      setError(err as Error);
    } finally {
      setLoading(false);
    }
  }, [metric, timeRange, timeRangeMs, aggregation, startTime, endTime]);

  // Fetch data on mount and when parameters change
  useEffect(() => {
    fetchData();
  }, [fetchData]);

  // Handle time range change
  const handleTimeRangeChange = useCallback(
    (_event: React.MouseEvent<HTMLElement>, newValue: string | null) => {
      if (newValue) {
        setTimeRange(newValue as typeof timeRange);
      }
    },
    []
  );

  // Handle aggregation change
  const handleAggregationChange = useCallback(
    (event: any) => {
      setAggregation(event?.target?.value as typeof aggregation);
    },
    [aggregation]
  );

  // Handle export
  const handleExport = useCallback(() => {
    if (data && onExport) {
      onExport(data.data);
    }
  }, [data, onExport]);

  return (
    <LocalizationProvider dateAdapter={AdapterDateFns}>
      <Paper sx={{ p: 2, height: '100%' }}>
        {/* Controls */}
        <Box
          sx={{
            display: 'flex',
            gap: 2,
            mb: 2,
            flexWrap: 'wrap',
            alignItems: 'center',
          }}
        >
          {/* Time range selector */}
          <ToggleButtonGroup
            value={timeRange}
            exclusive
            onChange={handleTimeRangeChange}
            size="small"
          >
            <ToggleButton value="1h">1H</ToggleButton>
            <ToggleButton value="24h">24H</ToggleButton>
            <ToggleButton value="7d">7D</ToggleButton>
            <ToggleButton value="30d">30D</ToggleButton>
            <ToggleButton value="custom">Custom</ToggleButton>
          </ToggleButtonGroup>

          {/* Custom time range */}
          {timeRange === 'custom' && (
            <>
              <DateTimePicker
                label="Start Time"
                value={startTime}
                onChange={setStartTime}
                slotProps={{ textField: { size: 'small' } }}
              />
              <DateTimePicker
                label="End Time"
                value={endTime}
                onChange={setEndTime}
                slotProps={{ textField: { size: 'small' } }}
              />
            </>
          )}

          {/* Aggregation selector */}
          <FormControl size="small" sx={{ minWidth: 120 }}>
            <InputLabel>Aggregation</InputLabel>
            <Select value={aggregation} onChange={handleAggregationChange} label="Aggregation">
              <MenuItem value="avg">Average</MenuItem>
              <MenuItem value="sum">Sum</MenuItem>
              <MenuItem value="min">Minimum</MenuItem>
              <MenuItem value="max">Maximum</MenuItem>
              <MenuItem value="count">Count</MenuItem>
            </Select>
          </FormControl>

          {/* Refresh button */}
          <Button
            variant="outlined"
            size="small"
            onClick={fetchData}
            disabled={loading}
          >
            Refresh
          </Button>

          {/* Export button */}
          {onExport && data && (
            <Button
              variant="outlined"
              size="small"
              onClick={handleExport}
            >
              Export
            </Button>
          )}

          {/* Cache stats */}
          {data?.metadata.cacheHit && (
            <Box
              sx={{
                ml: 'auto',
                px: 1,
                py: 0.5,
                borderRadius: 1,
                backgroundColor: theme.palette.success.light,
                color: theme.palette.success.contrastText,
                fontSize: '0.75rem',
              }}
            >
              Cached
            </Box>
          )}
        </Box>

        {/* Chart */}
        <Box sx={{ height: 'calc(100% - 80px)' }}>
          {loading ? (
            <Box
              sx={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                height: '100%',
              }}
            >
              <CircularProgress />
            </Box>
          ) : error ? (
            <Box
              sx={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                height: '100%',
                color: theme.palette.error.main,
              }}
            >
              Error: {error.message}
            </Box>
          ) : data ? (
            <LineChart
              data={data.data}
              config={{
                type: 'line',
                title: metric,
                xAxis: { label: 'Time' },
                yAxis: { label: 'Value' },
              }}
              smooth
              showGrid
            />
          ) : null}
        </Box>
      </Paper>
    </LocalizationProvider>
  );
};

HistoricalChart.displayName = 'HistoricalChart';

export default HistoricalChart;
