/**
 * @doc.type component
 * @doc.purpose Performance metrics panel overlay
 * @doc.layer product
 * @doc.pattern Presentation Component
 */

import React from 'react';
import {
  Button,
  Stack,
  Typography,
  Surface as Paper,
} from '@ghatana/ui';

interface PerformanceMetrics {
  fps: number;
  elements: number;
  frameTime: number;
  renderTime: number;
}

interface CanvasPerformancePanelProps {
  open: boolean;
  metrics: PerformanceMetrics;
  enabled: boolean;
  onEnableMonitoring: () => void;
}

/**
 * Performance metrics display panel
 */
export const CanvasPerformancePanel: React.FC<CanvasPerformancePanelProps> = ({
  open,
  metrics,
  enabled,
  onEnableMonitoring,
}) => {
  if (!open) return null;

  return (
    <Paper
      data-testid="performance-metrics"
      className="absolute p-4 bottom-[24px] right-[16px] z-40 w-[240px]"
      elevation={3}
    >
      <Stack spacing={1}>
        <Typography variant="subtitle2">Performance Metrics</Typography>
        <Typography variant="body2">FPS: {metrics.fps}</Typography>
        <Typography variant="body2">Elements: {metrics.elements}</Typography>
        <Typography variant="body2">Frame Time: {metrics.frameTime}ms</Typography>
        <Typography variant="body2">Render Time: {metrics.renderTime}ms</Typography>
        {!enabled && (
          <Button
            size="small"
            variant="contained"
            data-testid="enable-monitoring"
            onClick={onEnableMonitoring}
          >
            Enable Monitoring
          </Button>
        )}
      </Stack>
    </Paper>
  );
};
