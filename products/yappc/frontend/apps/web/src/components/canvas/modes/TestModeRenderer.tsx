/**
 * Test Mode Renderer
 * 
 * @doc.type class
 * @doc.purpose Provides testing canvas for test coverage and execution
 * @doc.layer product
 * @doc.pattern ModeRenderer
 * 
 * Test mode is focused on quality assurance with:
 * - System level: E2E test coverage map (test suites, scenarios)
 * - Component level: Unit test coverage (badges, gaps)
 * - File level: Test file list (results, status)
 * - Code level: Test code editor (runner, assertions)
 * 
 * Uses DevSecOps canvas components for rich visualization.
 */

import { Bug as TestIcon, Sparkles as AIIcon, BarChart3 as CoverageIcon, ListChecks as SuitesIcon, FileText as FileIcon, Play as RunIcon, Plus as AddIcon } from 'lucide-react';
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

// Level-specific configurations for test mode
const LEVEL_CONFIG = {
  system: {
    title: 'E2E Coverage Map',
    description: 'Plan integration and E2E tests',
    tools: ['Test Suites', 'Scenarios', 'Flows'],
    emptyMessage: 'Plan integration tests',
    aiAction: 'Gen-Test: Write missing cases',
    icon: <SuitesIcon />,
    color: '#FF5722',
  },
  component: {
    title: 'Unit Coverage',
    description: 'View test coverage by component',
    tools: ['Coverage Badges', 'Gaps', 'Reports'],
    emptyMessage: 'View test coverage',
    aiAction: 'Generate unit tests',
    icon: <CoverageIcon />,
    color: '#FF5722',
  },
  file: {
    title: 'Test Files',
    description: 'Browse test files and results',
    tools: ['Test Results', 'Status', 'Filters'],
    emptyMessage: 'See test files',
    aiAction: 'Generate test file',
    icon: <FileIcon />,
    color: '#FF5722',
  },
  code: {
    title: 'Test Editor',
    description: 'Write and run test cases',
    tools: ['Test Runner', 'Assertions', 'Mocks'],
    emptyMessage: 'Write test cases',
    aiAction: 'Complete test assertions',
    icon: <RunIcon />,
    color: '#FF5722',
  },
};

/**
 * Test Mode Empty State Component
 */
const TestEmptyState: React.FC<{
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
          className="mb-6 rounded-full flex items-center justify-center w-[80px] h-[80px] mx-auto" style={{ backgroundColor: config.color, backgroundColor: 'rgba(255' }} >
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
              className="hover:bg-[#E64A19]" style={{ backgroundColor: config.color, transform: 'translateX(-50%)', backgroundColor: 'rgba(255' }}
            >
              Add Test
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
 * Test Mode Content Renderer
 * 
 * Integrates DevSecOps testing phase canvas for rich test visualization.
 */
export const TestModeRenderer: React.FC<ModeRendererProps> = ({
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
      <TestEmptyState
        level={level}
        onAskAI={onAskAI}
        onGetStarted={onGetStarted}
      />
    );
  }

  // Render children with test-specific toolbar
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
            icon={<RunIcon />}
            label="Run All"
            size="small"
            clickable
            className="bg-[rgba(76,_175,_80,_0.2)] hover:bg-[rgba(76,_175,_80,_0.3)]"
          />
          <Chip
            icon={<CoverageIcon />}
            label="Coverage"
            size="small"
            clickable
            className="bg-[rgba(255,_87,_34,_0.1)] hover:bg-[rgba(255,_87,_34,_0.2)]"
          />
          <Chip
            icon={<AIIcon />}
            label="Gen-Test"
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
        <TestIcon className="text-base" style={{ color: config.color }} />
        <Typography variant="caption" fontWeight={500}>
          {config.title}
        </Typography>
      </Box>

      {/* Render canvas children */}
      {children}
    </Box>
  );
};

export default TestModeRenderer;
