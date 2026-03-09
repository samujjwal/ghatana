/**
 * EmptyCanvasState Component
 * 
 * Initial empty canvas state with template cards
 * Appears when canvas has no content
 * Fades out after first artifact created
 * 
 * Features:
 * - Subtle center dot with pulse animation
 * - Hint text: "Click anywhere to create a frame or press F to start"
 * - 3 template cards (120×80px each)
 * - Re-summon via ⌘⇧A
 * 
 * @doc.type component
 * @doc.purpose Empty canvas onboarding
 * @doc.layer components
 */

import { Box } from '@ghatana/ui';
import { useAtom } from 'jotai';
import React from 'react';

import { chromeShowEmptyStateAtom } from '../state/chrome-atoms';
import { CANVAS_TOKENS } from '../tokens/canvas-tokens';

const { SPACING, COLORS, TYPOGRAPHY, FONT_WEIGHT, RADIUS, SHADOWS } = CANVAS_TOKENS;

export interface EmptyCanvasStateProps {
  /** Callback when template selected */
  onTemplateSelect?: (templateId: string) => void;
  
  /** Callback when canvas clicked */
  onCanvasClick?: (event: React.MouseEvent) => void;
}

interface Template {
  id: string;
  emoji: string;
  title: string;
  description: string;
}

const TEMPLATES: Template[] = [
  {
    id: 'user-story',
    emoji: '📋',
    title: 'User Story',
    description: 'Create user story cards',
  },
  {
    id: 'wireframe',
    emoji: '🎨',
    title: 'Wireframe',
    description: 'Design wireframe layout',
  },
  {
    id: 'api-flow',
    emoji: '⚡',
    title: 'API Flow',
    description: 'Map API flow diagram',
  },
];

export function EmptyCanvasState({
  onTemplateSelect,
  onCanvasClick,
}: EmptyCanvasStateProps) {
  const [showEmptyState] = useAtom(chromeShowEmptyStateAtom);

  if (!showEmptyState) {
    return null;
  }

  const handleTemplateClick = (templateId: string) => {
    onTemplateSelect?.(templateId);
  };

  return (
    <Box
      onClick={onCanvasClick}
      className="absolute flex flex-col items-center justify-center top-[0px] left-[0px] right-[0px] bottom-[0px] pointer-events-auto cursor-crosshair" >
      {/* Center dot with pulse animation */}
      <Box
        className="w-[8px] h-[8px] rounded-full" style={{ backgroundColor: COLORS.BORDER_LIGHT, marginBottom: SPACING.XL, color: COLORS.TEXT_PRIMARY }}
      />

      {/* Hint text */}
      <Box
        className="text-center gap-6" style={{ fontSize: TYPOGRAPHY.BASE, color: COLORS.TEXT_SECONDARY, marginBottom: SPACING.XXL }}
      >
        Click anywhere to create a frame
        <br />
        or press F to start
      </Box>

      {/* Template cards */}
      <Box className="flex flex-wrap justify-center" style={{ gap: SPACING.LG }}
      >
        {TEMPLATES.map((template) => (
          <Box
            key={template.id}
            onClick={(e) => {
              e.stopPropagation();
              handleTemplateClick(template.id);
            }}
            className="w-[120px] h-[80px] flex flex-col items-center justify-center" style={{ gap: SPACING.XS, backgroundColor: COLORS.NEUTRAL_50, border: `1px solid ${COLORS.BORDER_LIGHT}` }}
          >
            {/* Emoji icon */}
            <Box
              className="text-2xl text-sm font-semibold"
            >
              {template.emoji}
            </Box>

            {/* Title */}
            <Box style={{ color: COLORS.TEXT_PRIMARY }}
            >
              {template.title}
            </Box>
          </Box>
        ))}
      </Box>

      {/* Keyboard shortcut hint */}
      <Box
        className="text-center mt-12 text-xs" >
        Press ⌘⇧A to show templates anytime
      </Box>
    </Box>
  );
}
