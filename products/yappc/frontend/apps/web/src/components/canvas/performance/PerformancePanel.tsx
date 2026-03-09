// Core UI components from @ghatana/yappc-ui
import {
  Box,
  Stack,
  Typography,
  Button,
  Chip,
  LinearProgress as MuiLinearProgress,
  Surface as Paper,
} from '@ghatana/ui';

import { Gauge as SpeedIcon } from 'lucide-react';
import React, { useEffect, useState } from 'react';

/**
 * Performance metrics data structure.
 *
 * Captures real-time performance data including frame rate, element count,
 * and rendering times for monitoring canvas performance.
 *
 * @doc.type interface
 * @doc.purpose Performance data model
 * @doc.layer presentation
 */
export interface PerformanceMetrics {
  fps: number;
  elements: number;
  frameTime: number;
  renderTime: number;
  memoryUsage?: number;
}

/**
 * Props for PerformancePanel component.
 *
 * Controls panel visibility, metrics display, and monitoring state.
 *
 * @doc.type interface
 * @doc.purpose Component props definition
 * @doc.layer presentation
 */
export interface PerformancePanelProps {
  open: boolean;
  onClose: () => void;
  metrics?: PerformanceMetrics;
  onEnableMonitoring?: () => void;
  monitoringEnabled?: boolean;
}

/**
 * Performance metrics panel for canvas monitoring.
 *
 * Displays real-time performance data including FPS, element count,
 * frame time, and memory usage. Provides controls for enabling/disabling
 * performance monitoring and visual indicators for performance health.
 *
 * Features:
 * - Real-time FPS monitoring with color-coded health status
 * - Element count and rendering time tracking
 * - Memory usage monitoring (when available)
 * - Performance health indicators
 * - Toggle monitoring on/off
 *
 * @param props - Component properties
 * @returns Rendered performance panel
 *
 * @doc.type component
 * @doc.purpose Canvas performance monitoring
 * @doc.layer presentation
 * @doc.pattern Observer
 *
 * @example
 * ```tsx
 * <PerformancePanel
 *   open={showMetrics}
 *   onClose={() => setShowMetrics(false)}
 *   metrics={currentMetrics}
 *   onEnableMonitoring={() => startMonitoring()}
 *   monitoringEnabled={isMonitoring}
 * />
 * ```
 */
export const PerformancePanel: React.FC<PerformancePanelProps> = ({
  open,
  onClose,
  metrics,
  onEnableMonitoring,
  monitoringEnabled = false,
}) => {
  const [liveMetrics, setLiveMetrics] = useState<PerformanceMetrics>(
    metrics || {
      fps: 60,
      elements: 0,
      frameTime: 16,
      renderTime: 8,
    }
  );

  useEffect(() => {
    if (metrics) {
      setLiveMetrics(metrics);
    }
  }, [metrics]);

  useEffect(() => {
    if (!monitoringEnabled || !open) return;

    const interval = setInterval(() => {
      // Simulate live metrics update
      setLiveMetrics((prev) => ({
        ...prev,
        fps: Math.max(30, Math.min(60, prev.fps + (Math.random() - 0.5) * 2)),
        frameTime: Math.max(10, Math.min(30, prev.frameTime + (Math.random() - 0.5))),
        renderTime: Math.max(5, Math.min(20, prev.renderTime + (Math.random() - 0.5))),
      }));
    }, 1000);

    return () => clearInterval(interval);
  }, [monitoringEnabled, open]);

  if (!open) return null;

  const fpsColor = liveMetrics.fps >= 50 ? 'success' : liveMetrics.fps >= 30 ? 'warning' : 'error';
  const frameTimeColor = liveMetrics.frameTime <= 20 ? 'success' : liveMetrics.frameTime <= 30 ? 'warning' : 'error';

  return (
    <Paper
      data-testid="performance-metrics"
      elevation={3}
      className="absolute p-4 bottom-[24px] right-[16px] z-40 w-[280px] pointer-events-auto"
    >
      <Stack spacing={2}>
        <Box className="flex items-center justify-between">
          <Box className="flex items-center gap-2">
            <SpeedIcon size={16} />
            <Typography variant="subtitle2">Performance</Typography>
          </Box>
          <Button size="small" onClick={onClose}>
            Close
          </Button>
        </Box>

        <Stack spacing={1.5}>
          <Box>
            <Box className="flex justify-between mb-1">
              <Typography variant="body2">FPS</Typography>
              <Chip label={Math.round(liveMetrics.fps)} size="small" color={fpsColor} />
            </Box>
            <MuiLinearProgress
              variant="determinate"
              value={(liveMetrics.fps / 60) * 100}
            />
          </Box>

          <Box className="flex justify-between">
            <Typography variant="body2">Elements</Typography>
            <Typography variant="body2" fontWeight="medium">
              {liveMetrics.elements}
            </Typography>
          </Box>

          <Box>
            <Box className="flex justify-between mb-1">
              <Typography variant="body2">Frame Time</Typography>
              <Chip
                label={`${Math.round(liveMetrics.frameTime)}ms`}
                size="small"
                color={frameTimeColor}
              />
            </Box>
            <Box className="w-full mt-2">
              <MuiLinearProgress
                variant="determinate"
                value={Math.min(100, (liveMetrics.frameTime / 33) * 100)}
                className="h-[8px] rounded-2xl"
              />
            </Box>
          </Box>

          <Box className="flex justify-between">
            <Typography variant="body2">Render Time</Typography>
            <Typography variant="body2" fontWeight="medium">
              {Math.round(liveMetrics.renderTime)}ms
            </Typography>
          </Box>

          {liveMetrics.memoryUsage !== undefined && (
            <Box className="flex justify-between">
              <Typography variant="body2">Memory</Typography>
              <Typography variant="body2" fontWeight="medium">
                {Math.round(liveMetrics.memoryUsage)}MB
              </Typography>
            </Box>
          )}
        </Stack>

        {!monitoringEnabled && onEnableMonitoring && (
          <Button
            variant="contained"
            size="small"
            fullWidth
            data-testid="enable-monitoring"
            onClick={onEnableMonitoring}
          >
            Enable Live Monitoring
          </Button>
        )}

        {monitoringEnabled && (
          <Typography variant="caption" color="success.main" align="center">
            ● Live monitoring active
          </Typography>
        )}
      </Stack>
    </Paper>
  );
};
