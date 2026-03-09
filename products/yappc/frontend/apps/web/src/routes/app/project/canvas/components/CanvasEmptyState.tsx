/**
 * @doc.type component
 * @doc.purpose Empty canvas state display with call-to-action buttons
 * @doc.layer product
 * @doc.pattern Presentation Component
 */

import React from 'react';
import { Box } from '@ghatana/ui';
import { EmptyCanvasState as BaseEmptyCanvasState } from '@/components/canvas/ui/CanvasUI';

interface CanvasEmptyStateProps {
  visible: boolean;
  onUseTemplate: () => void;
  onStartBlank: () => void;
  onAskAI: () => void;
}

/**
 * Wrapper for empty canvas state with positioning
 */
export const CanvasEmptyState: React.FC<CanvasEmptyStateProps> = ({
  visible,
  onUseTemplate,
  onStartBlank,
  onAskAI,
}) => {
  if (!visible) return null;

  return (
    <Box
      className="absolute flex items-center justify-center pointer-events-none top-[0px] left-[0px] right-[0px] bottom-[0px] z-10"
    >
      <Box className="pointer-events-auto">
        <BaseEmptyCanvasState
          onUseTemplate={onUseTemplate}
          onStartBlank={onStartBlank}
          onAskAI={onAskAI}
        />
      </Box>
    </Box>
  );
};
