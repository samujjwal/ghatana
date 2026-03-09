/**
 * Observe Mode Renderer
 * 
 * @doc.type class
 * @doc.purpose Provides observability canvas for monitoring and debugging
 * @doc.layer product
 * @doc.pattern ModeRenderer
 * 
 * Observe mode is focused on monitoring with:
 * - System level: Health dashboard (status, uptime, alerts)
 * - Component level: Component metrics (latency, throughput)
 * - File level: Log streams (log viewer, filters)
 * - Code level: Trace details (span viewer, timeline)
 * 
 * Uses DevSecOps monitoring phase canvas for KPI visualization.
 */

import { Eye as ObserveIcon, Sparkles as AIIcon, LayoutDashboard as DashboardIcon, Gauge as MetricsIcon, Newspaper as LogsIcon, Activity as TraceIcon, Plus as AddIcon, AlertTriangle as AlertIcon } from 'lucide-react';
import {
  Box,
  Button,
  Chip,
  Stack,
  Typography,
  Surface as Paper,
} from '@ghatana/ui';
import React from 'react';

import type { ModeRendererProps } from './types';

// Level-specific configurations for observe mode
const LEVEL_CONFIG = {
  system: {
    title: 'Health Dashboard',
    description: 'Monitor system health and alerts',
    tools: ['Status', 'Uptime', 'Alerts'],
    emptyMessage: 'Connect monitoring',
    aiAction: 'Root Cause: "Why 500 error?"',
    icon: <DashboardIcon />,
    color: '#00BCD4',
  },
  component: {
    title: 'Component Metrics',
    description: 'View performance by component',
    tools: ['Latency', 'Throughput', 'Errors'],
    emptyMessage: 'View component health',
    aiAction: 'Analyze performance',
    icon: <MetricsIcon />,
    color: '#00BCD4',
  },
  file: {
    title: 'Log Streams',
    description: 'View and search logs',
    tools: ['Log Viewer', 'Filters', 'Search'],
    emptyMessage: 'View log files',
    aiAction: 'Search logs for errors',
    icon: <LogsIcon />,
    color: '#00BCD4',
  },
  code: {
    title: 'Trace Details',
    description: 'View execution traces',
    tools: ['Span Viewer', 'Timeline', 'Waterfall'],
    emptyMessage: 'View execution traces',
    aiAction: 'Find bottlenecks',
    icon: <TraceIcon />,
    color: '#00BCD4',
  },
};

/**
 * Observe Mode Empty State Component
 */
const ObserveEmptyState: React.FC<{
  level: ModeRendererProps['level'];
  onAskAI?: () => void;
  onGetStarted?: () => void;
}> = ({ level, onAskAI, onGetStarted }) => {
  const config = LEVEL_CONFIG[level];

  return (
    <Box
      className="flex flex-col items-center justify-center h-full p-8 min-h-[400px]"
    >
      <Paper
        elevation={0}
        className="p-8 text-center max-w-[480px] rounded-xl" >
        <Box
          className="mb-6 rounded-full flex items-center justify-center w-[80px] h-[80px] mx-auto" style={{ backgroundColor: config.color, backgroundColor: 'rgba(0' }} >
          {React.cloneElement(config.icon as React.ReactElement, { 
            sx: { fontSize: 40, color: '#fff' } 
          })}
        </Box>

        <Typography variant="h5" gutterBottom fontWeight={600}>
          {config.title}
        </Typography>
        
        <Typography variant="body1" color="text.secondary" className="mb-6">
          {config.emptyMessage}
        </Typography>

        <Stack direction="row" spacing={1} justifyContent="center" flexWrap="wrap" className="mb-6">
          {config.tools.map((tool) => (
            <Chip key={tool} label={tool} size="small" variant="outlined" />
          ))}
        </Stack>

        <Stack direction="row" spacing={2} justifyContent="center">
          {onGetStarted && (
            <Button
              variant="contained"
              startIcon={<AddIcon />}
              onClick={onGetStarted}
              className="hover:bg-[#0097A7]" style={{ backgroundColor: config.color, transform: 'translateX(-50%)', backgroundColor: 'rgba(255' }}
            >
              Connect Source
            </Button>
          )}
          {onAskAI && (
            <Button
              variant="outlined"
              startIcon={<AIIcon />}
              onClick={onAskAI}
              style={{ color: config.color, borderColor: config.color }}
            >
              {config.aiAction}
            </Button>
          )}
        </Stack>
      </Paper>
    </Box>
  );
};

/**
 * Observe Mode Content Renderer
 * 
 * Integrates DevSecOps monitoring phase canvas for KPI visualization.
 */
export const ObserveModeRenderer: React.FC<ModeRendererProps> = ({
  level,
  hasContent,
  onAskAI,
  onGetStarted,
  children,
  readOnly = false,
}) => {
  const config = LEVEL_CONFIG[level];

  // If no content, show empty state
  if (!hasContent) {
    return (
      <ObserveEmptyState
        level={level}
        onAskAI={onAskAI}
        onGetStarted={onGetStarted}
      />
    );
  }

  // Render children with observe-specific toolbar
  return (
    <Box
      className="relative h-full w-full"
    >
      {/* Mode-specific toolbar overlay */}
      {!readOnly && (
        <Paper
          elevation={1}
          className="absolute px-4 py-2 flex gap-2 rounded-lg top-[8px] left-[50%] z-20" >
          <Chip
            icon={<DashboardIcon />}
            label="Dashboard"
            size="small"
            clickable
            className="bg-[rgba(0,_188,_212,_0.2)] hover:bg-[rgba(0,_188,_212,_0.3)]"
          />
          <Chip
            icon={<AlertIcon />}
            label="Alerts"
            size="small"
            clickable
            className="bg-[rgba(255,_152,_0,_0.2)] hover:bg-[rgba(255,_152,_0,_0.3)]"
          />
          <Chip
            icon={<AIIcon />}
            label="Root Cause"
            size="small"
            clickable
            className="text-[#fff] [&_.MuiChip-icon]:text-white" style={{ backgroundColor: config.color, backgroundColor: 'rgba(255' }}
            onClick={onAskAI}
          />
        </Paper>
      )}

      {/* Level indicator */}
      <Box
        className="absolute flex items-center gap-2 px-4 py-1 rounded bottom-[48px] left-[16px] z-20 shadow-sm" >
        <ObserveIcon className="text-base" style={{ color: config.color }} />
        <Typography variant="caption" fontWeight={500}>
          {config.title}
        </Typography>
      </Box>

      {/* Render canvas children */}
      {children}
    </Box>
  );
};

export default ObserveModeRenderer;
