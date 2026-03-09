/**
 * @doc.type component
 * @doc.purpose Canvas loading state with skeleton or spinner
 * @doc.layer product
 * @doc.pattern Presentation Component
 */

import React from 'react';
import { Box, Spinner as CircularProgress } from '@ghatana/ui';

interface CanvasLoadingStateProps {
  message?: string;
}

/**
 * Loading state display for canvas
 */
export const CanvasLoadingState: React.FC<CanvasLoadingStateProps> = ({
  message = 'Loading canvas...',
}) => {
  return (
    <Box
      className="flex items-center justify-center h-screen flex-col gap-4"
      role="status"
      aria-live="polite"
      aria-label={message}
    >
      <CircularProgress size={48} />
      <div>{message}</div>
    </Box>
  );
};
