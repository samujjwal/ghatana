/**
 * Design Mode Renderer
 * 
 * @doc.type class
 * @doc.purpose Provides design canvas for UI/UX and visual design work
 * @doc.layer product
 * @doc.pattern ModeRenderer
 * 
 * Design mode is focused on visual design with:
 * - System level: Design system overview (themes, colors, typography)
 * - Component level: Page wireframes and layouts
 * - File level: Component specifications (props, variants, states)
 * - Code level: Style tokens editor (CSS/tokens)
 */

import { Palette as DesignIcon, Sparkles as AIIcon, Palette as ColorIcon, LayoutDashboard as LayoutIcon, SlidersHorizontal as PropsIcon, Code as TokensIcon, Plus as AddIcon } from 'lucide-react';
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

// Level-specific configurations for design mode
const LEVEL_CONFIG = {
  system: {
    title: 'Design System',
    description: 'Define your design language',
    tools: ['Theme', 'Colors', 'Typography'],
    emptyMessage: 'Define your design language',
    aiAction: 'Generate Dashboard layout',
    icon: <ColorIcon />,
    color: '#E91E63',
  },
  component: {
    title: 'Page Wireframes',
    description: 'Create page layouts and wireframes',
    tools: ['Page Templates', 'Layouts', 'Grids'],
    emptyMessage: 'Create your first page',
    aiAction: 'Generate page layout',
    icon: <LayoutIcon />,
    color: '#E91E63',
  },
  file: {
    title: 'Component Specs',
    description: 'Define component specifications',
    tools: ['Props', 'Variants', 'States'],
    emptyMessage: 'Design component details',
    aiAction: 'Generate component variants',
    icon: <PropsIcon />,
    color: '#E91E63',
  },
  code: {
    title: 'Style Tokens',
    description: 'Edit CSS and design tokens',
    tools: ['CSS/Tokens Editor'],
    emptyMessage: 'Edit component styles',
    aiAction: 'Generate CSS from design',
    icon: <TokensIcon />,
    color: '#E91E63',
  },
};

/**
 * Design Mode Empty State Component
 */
const DesignEmptyState: React.FC<{
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
          className="mb-6 rounded-full flex items-center justify-center w-[80px] h-[80px] mx-auto" style={{ backgroundColor: config.color, backgroundColor: 'rgba(233' }} >
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
              className="hover:bg-[#C2185B]" style={{ backgroundColor: config.color, transform: 'translateX(-50%)', backgroundColor: 'rgba(255' }}
            >
              Add Element
            </Button>
          )}
          {onAskAI && (
            <Button
              variant="outlined"
              startIcon={<AIIcon />}
              onClick={onAskAI}
              style={{ color: config.color, borderColor: config.color, backgroundColor: 'rgba(255' }}
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
 * Design Mode Content Renderer
 */
export const DesignModeRenderer: React.FC<ModeRendererProps> = ({
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
      <DesignEmptyState
        level={level}
        onAskAI={onAskAI}
        onGetStarted={onGetStarted}
      />
    );
  }

  // Render children with design-specific toolbar
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
          {config.tools.map((tool) => (
            <Chip
              key={tool}
              label={tool}
              size="small"
              clickable
              className="bg-[rgba(233,_30,_99,_0.1)] hover:bg-[rgba(233,_30,_99,_0.2)]"
            />
          ))}
          <Chip
            icon={<AIIcon />}
            label="Generative UI"
            size="small"
            clickable
            className="text-[#fff] [&_.MuiChip-icon]:text-white" style={{ backgroundColor: config.color }}
            onClick={onAskAI}
          />
        </Paper>
      )}

      {/* Level indicator */}
      <Box
        className="absolute flex items-center gap-2 px-4 py-1 rounded bottom-[48px] left-[16px] z-20 shadow-sm" >
        <DesignIcon className="text-base" style={{ color: config.color }} />
        <Typography variant="caption" fontWeight={500}>
          {config.title}
        </Typography>
      </Box>

      {/* Render canvas children */}
      {children}
    </Box>
  );
};

export default DesignModeRenderer;
