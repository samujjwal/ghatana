/**
 * Diagram Mode Renderer
 * 
 * @doc.type class
 * @doc.purpose Provides diagramming canvas for system design and architecture
 * @doc.layer product
 * @doc.pattern ModeRenderer
 * 
 * Diagram mode is focused on visual architecture with:
 * - System level: C4 context diagram with external actors
 * - Component level: C4 container/component diagrams
 * - File level: Class/module diagrams
 * - Code level: Sequence diagrams
 */

import { GitBranch as DiagramIcon, Sparkles as AIIcon, Network as SystemIcon, LayoutGrid as ContainerIcon, FileCode as ClassIcon, Activity as SequenceIcon, Plus as AddIcon } from 'lucide-react';
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

// Level-specific configurations for diagram mode
const LEVEL_CONFIG = {
  system: {
    title: 'System Context',
    description: 'C4 context diagram showing external actors',
    tools: ['Actors', 'Systems', 'Connections'],
    emptyMessage: 'Design your system context',
    aiAction: 'Generate C4 diagram from description',
    icon: <SystemIcon />,
    color: '#4CAF50',
  },
  component: {
    title: 'Container Diagram',
    description: 'Services, databases, and their connections',
    tools: ['Services', 'Databases', 'Queues', 'APIs'],
    emptyMessage: 'Map your system containers',
    aiAction: 'Suggest microservices split',
    icon: <ContainerIcon />,
    color: '#4CAF50',
  },
  file: {
    title: 'Class Diagram',
    description: 'Classes, interfaces, and inheritance',
    tools: ['Classes', 'Interfaces', 'Inheritance'],
    emptyMessage: 'Select components to see structure',
    aiAction: 'Generate class diagram',
    icon: <ClassIcon />,
    color: '#4CAF50',
  },
  code: {
    title: 'Sequence Diagram',
    description: 'Actors, messages, and lifelines',
    tools: ['Actors', 'Messages', 'Lifelines'],
    emptyMessage: 'Drill into a function to see flow',
    aiAction: 'Generate sequence from code',
    icon: <SequenceIcon />,
    color: '#4CAF50',
  },
};

/**
 * Diagram Mode Empty State Component
 */
const DiagramEmptyState: React.FC<{
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
          className="mb-6 rounded-full flex items-center justify-center w-[80px] h-[80px] mx-auto" style={{ backgroundColor: config.color, backgroundColor: 'rgba(76' }} >
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
              className="hover:bg-[#388E3C]" style={{ backgroundColor: config.color, backgroundColor: 'rgba(255', transform: 'translateX(-50%)', backgroundColor: 'rgba(255' }}
            >
              Add Node
            </Button>
          )}
          {onAskAI && (
            <Button
              variant="outlined"
              startIcon={<AIIcon />}
              onClick={onAskAI}
              color="success"
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
 * Diagram Mode Content Renderer
 */
export const DiagramModeRenderer: React.FC<ModeRendererProps> = ({
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
      <DiagramEmptyState
        level={level}
        onAskAI={onAskAI}
        onGetStarted={onGetStarted}
      />
    );
  }

  // Render children with diagram-specific toolbar
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
          {config.tools.slice(0, 3).map((tool) => (
            <Chip
              key={tool}
              label={tool}
              size="small"
              clickable
              className="bg-[rgba(76,_175,_80,_0.1)] hover:bg-[rgba(76,_175,_80,_0.2)]"
            />
          ))}
          <Chip
            icon={<AIIcon />}
            label="AI Generate"
            size="small"
            clickable
            color="success"
            onClick={onAskAI}
          />
        </Paper>
      )}

      {/* Level indicator */}
      <Box
        className="absolute flex items-center gap-2 px-4 py-1 rounded bottom-[48px] left-[16px] z-20 shadow-sm" >
        <DiagramIcon className="text-base" style={{ color: config.color }} />
        <Typography variant="caption" fontWeight={500}>
          {config.title}
        </Typography>
      </Box>

      {/* Render canvas children */}
      {children}
    </Box>
  );
};

export default DiagramModeRenderer;
