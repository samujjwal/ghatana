/**
 * Brainstorm Mode Renderer
 * 
 * @doc.type class
 * @doc.purpose Provides brainstorming canvas for ideation and mind mapping
 * @doc.layer product
 * @doc.pattern ModeRenderer
 * 
 * Brainstorm mode is focused on free-form ideation with:
 * - System level: High-level idea mapping and vision boards
 * - Component level: Feature brainstorming with sticky notes
 * - File level: Detailed requirement cards
 * - Code level: Technical notes and pseudocode
 */

import { Lightbulb as IdeaIcon, Sparkles as AIIcon, StickyNote as NoteIcon, Tag as CategoryIcon, Plus as AddIcon } from 'lucide-react';
import {
  Box,
  Button,
  Chip,
  Stack,
  Typography,
  Surface as Paper,
} from '@ghatana/ui';
import React, { useCallback, useState } from 'react';

import type { ModeRendererProps } from './types';

// Level-specific configurations
const LEVEL_CONFIG = {
  system: {
    title: 'Vision & Ideas',
    description: 'Capture high-level concepts and goals',
    tools: ['Mind Map', 'Vision Board', 'Goals'],
    emptyMessage: 'Start brainstorming your vision',
    aiAction: 'Generate idea cluster',
  },
  component: {
    title: 'Feature Ideas',
    description: 'Brainstorm features and user stories',
    tools: ['Sticky Notes', 'Feature Cards', 'Voting'],
    emptyMessage: 'Add feature ideas with sticky notes',
    aiAction: 'Suggest features from requirements',
  },
  file: {
    title: 'Requirement Details',
    description: 'Document detailed requirements',
    tools: ['Requirement Cards', 'Acceptance Criteria', 'Links'],
    emptyMessage: 'Document requirement specifics',
    aiAction: 'Generate user stories',
  },
  code: {
    title: 'Technical Notes',
    description: 'Technical ideation and pseudocode',
    tools: ['Code Snippets', 'Technical Notes', 'API Ideas'],
    emptyMessage: 'Sketch technical approaches',
    aiAction: 'Generate pseudocode',
  },
};

/**
 * Brainstorm Mode Empty State Component
 */
const BrainstormEmptyState: React.FC<{
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
        className="p-8 text-center max-w-[480px] rounded-xl" style={{ backgroundColor: 'rgba(255' }} >
        <Box
          className="mb-6 rounded-full flex items-center justify-center w-[80px] h-[80px] mx-auto bg-[#FFC107]"
        >
          <IdeaIcon className="text-[40px] text-[#fff]" />
        </Box>

        <Typography variant="h5" gutterBottom fontWeight={600}>
          {config.title}
        </Typography>
        
        <Typography variant="body1" color="text.secondary" className="mb-6">
          {config.emptyMessage}
        </Typography>

        <Stack direction="row" spacing={1} justifyContent="center" className="mb-6">
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
              className="bg-[#FFC107] hover:bg-[#FFA000]"
            >
              Add Idea
            </Button>
          )}
          {onAskAI && (
            <Button
              variant="outlined"
              startIcon={<AIIcon />}
              onClick={onAskAI}
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
 * Brainstorm Mode Content Renderer
 */
export const BrainstormModeRenderer: React.FC<ModeRendererProps> = ({
  level,
  hasContent,
  onAskAI,
  onGetStarted,
  onDrillDown,
  children,
  readOnly = false,
}) => {
  const config = LEVEL_CONFIG[level];
  const [selectedIdea, setSelectedIdea] = useState<string | null>(null);

  const handleIdeaClick = useCallback((ideaId: string) => {
    setSelectedIdea(ideaId);
    if (onDrillDown && level !== 'code') {
      onDrillDown(ideaId);
    }
  }, [level, onDrillDown]);

  // If no content, show empty state
  if (!hasContent) {
    return (
      <BrainstormEmptyState
        level={level}
        onAskAI={onAskAI}
        onGetStarted={onGetStarted}
      />
    );
  }

  // Render children (ReactFlow canvas or custom content)
  return (
    <Box
      className="relative h-full w-full"
    >
      {/* Mode-specific toolbar overlay */}
      {!readOnly && (
        <Paper
          elevation={1}
          className="absolute px-4 py-2 flex gap-2 rounded-lg top-[8px] left-[50%] z-20" style={{ transform: 'translateX(-50%)', backgroundColor: 'rgba(255' }} >
          <Chip
            icon={<NoteIcon />}
            label="Sticky Note"
            size="small"
            clickable
            className="bg-[#FFF9C4]"
          />
          <Chip
            icon={<CategoryIcon />}
            label="Category"
            size="small"
            clickable
            className="bg-[#E3F2FD]"
          />
          <Chip
            icon={<AIIcon />}
            label="AI Expand"
            size="small"
            clickable
            color="primary"
            onClick={onAskAI}
          />
        </Paper>
      )}

      {/* Render canvas children */}
      {children}
    </Box>
  );
};

export default BrainstormModeRenderer;
