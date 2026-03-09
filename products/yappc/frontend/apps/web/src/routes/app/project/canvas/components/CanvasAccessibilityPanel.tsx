/**
 * @doc.type component
 * @doc.purpose Accessibility issues panel overlay
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

interface CanvasAccessibilityPanelProps {
  open: boolean;
  issues: string[];
  onClose: () => void;
}

/**
 * Accessibility issues display panel
 */
export const CanvasAccessibilityPanel: React.FC<CanvasAccessibilityPanelProps> = ({
  open,
  issues,
  onClose,
}) => {
  if (!open) return null;

  return (
    <Paper
      data-testid="accessibility-panel"
      className="absolute p-4 bottom-[24px] left-[16px] z-40 w-[300px]"
      elevation={3}
    >
      <Stack spacing={1}>
        <Typography variant="subtitle2">Accessibility Issues</Typography>
        {issues.map((issue) => (
          <Typography key={issue} variant="body2">
            {issue}
          </Typography>
        ))}
        <Button size="small" onClick={onClose}>
          Close
        </Button>
      </Stack>
    </Paper>
  );
};
