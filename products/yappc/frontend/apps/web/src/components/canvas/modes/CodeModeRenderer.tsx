/**
 * Code Mode Renderer
 * 
 * @doc.type class
 * @doc.purpose Provides coding canvas for development and code editing
 * @doc.layer product
 * @doc.pattern ModeRenderer
 * 
 * Code mode is focused on development with:
 * - System level: Service topology (API endpoints, connections)
 * - Component level: Module dependency graph
 * - File level: File explorer + preview
 * - Code level: Monaco code editor
 */

import { Code as CodeIcon, Sparkles as AIIcon, Plug as ApiIcon, GitBranch as DepsIcon, FolderOpen as FileIcon, Terminal as EditorIcon, Plus as AddIcon } from 'lucide-react';
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

// Level-specific configurations for code mode
const LEVEL_CONFIG = {
  system: {
    title: 'Service Topology',
    description: 'Map your API endpoints and connections',
    tools: ['API Endpoints', 'Connections', 'Gateways'],
    emptyMessage: 'Map your services',
    aiAction: 'Copilot: Complete functions',
    icon: <ApiIcon />,
    color: '#2196F3',
  },
  component: {
    title: 'Module Graph',
    description: 'Visualize imports and exports',
    tools: ['Imports', 'Exports Visual', 'Dependencies'],
    emptyMessage: 'View module structure',
    aiAction: 'Suggest module organization',
    icon: <DepsIcon />,
    color: '#2196F3',
  },
  file: {
    title: 'File Explorer',
    description: 'Browse and preview project files',
    tools: ['File Tree', 'Quick Preview', 'Search'],
    emptyMessage: 'Browse project files',
    aiAction: 'Generate file structure',
    icon: <FileIcon />,
    color: '#2196F3',
  },
  code: {
    title: 'Code Editor',
    description: 'Full IDE features with Monaco',
    tools: ['Full IDE Features', 'IntelliSense', 'Debugging'],
    emptyMessage: 'Select a file to edit',
    aiAction: 'Complete code with AI',
    icon: <EditorIcon />,
    color: '#2196F3',
  },
};

/**
 * Code Mode Empty State Component
 */
const CodeEmptyState: React.FC<{
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
          className="mb-6 rounded-full flex items-center justify-center w-[80px] h-[80px] mx-auto" style={{ backgroundColor: config.color, backgroundColor: 'rgba(33' }} >
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
              className="hover:bg-[#1976D2]" style={{ backgroundColor: config.color, backgroundColor: 'rgba(255', transform: 'translateX(-50%)', backgroundColor: 'rgba(255' }}
            >
              {level === 'code' ? 'New File' : 'Add Node'}
            </Button>
          )}
          {onAskAI && (
            <Button
              variant="outlined"
              startIcon={<AIIcon />}
              onClick={onAskAI}
              color="primary"
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
 * Code Mode Content Renderer
 */
export const CodeModeRenderer: React.FC<ModeRendererProps> = ({
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
      <CodeEmptyState
        level={level}
        onAskAI={onAskAI}
        onGetStarted={onGetStarted}
      />
    );
  }

  // Render children with code-specific toolbar
  return (
    <Box
      className="relative h-full w-full"
    >
      {/* Mode-specific toolbar overlay */}
      {!readOnly && (
        <Paper
          elevation={1}
          className="absolute px-4 py-2 flex gap-2 rounded-lbackgroundColor: 'rgba(255 */
        >
          {config.tools.slice(0, 2).map((tool) => (
            <Chip
              key={tool}
              label={tool}
              size="small"
              clickable
              className="bg-[rgba(33,_150,_243,_0.1)] hover:bg-[rgba(33,_150,_243,_0.2)]"
            />
          ))}
          <Chip
            icon={<AIIcon />}
            label="Copilot"
            size="small"
            clickable
            color="primary"
            onClick={onAskAI}
          />
        </Paper>
      )}

      {/* Level indicator */}
      <Box
        className="absolute flex items-center gap-2 px-4 py-1 rounded bottom-[48px] left-[16px] z-20 shadow-sm" >
        <CodeIcon className="text-base" style={{ color: config.color }} />
        <Typography variant="caption" fontWeight={500}>
          {config.title}
        </Typography>
      </Box>

      {/* Render canvas children */}
      {children}
    </Box>
  );
};

export default CodeModeRenderer;
