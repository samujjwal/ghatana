/**
 * @doc.type component
 * @doc.purpose Canvas dialog components (Layout, Template Save)
 * @doc.layer product
 * @doc.pattern Presentation Component
 */

import React from 'react';
import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Typography,
} from '@ghatana/design-system';
import { TextField } from '@ghatana/design-system';

import type { AutoLayoutPreview } from '../hooks/useCanvasLayout';

interface LayoutDialogProps {
  open: boolean;
  onClose: () => void;
  onPreview: () => void;
  onApply: () => void;
  preview: AutoLayoutPreview | null;
}

/**
 * Auto-layout confirmation dialog
 */
export const LayoutDialog: React.FC<LayoutDialogProps> = ({
  open,
  onClose,
  onPreview,
  onApply,
  preview,
}) => {
  return (
    <Dialog open={open} onClose={onClose}>
      <DialogTitle>Auto Layout</DialogTitle>
      <DialogContent>
        <Typography variant="body2">
          Preview the tidy grid layout before applying it. The applied layout is undoable.
        </Typography>
        {preview ? (
          <Typography variant="body2" className="mt-3" data-testid="layout-preview-summary">
            {preview.moves.length} elements will move. First change:{' '}
            {preview.moves[0]
              ? `${preview.moves[0].elementId} (${preview.moves[0].from.x}, ${preview.moves[0].from.y}) -> (${preview.moves[0].to.x}, ${preview.moves[0].to.y})`
              : 'no position changes required.'}
          </Typography>
        ) : (
          <Typography variant="body2" className="mt-3" data-testid="layout-preview-empty">
            Generate a preview to review the position diff before any canvas nodes move.
          </Typography>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button onClick={onPreview} data-testid="preview-layout">
          Preview Layout
        </Button>
        <Button onClick={onApply} data-testid="apply-layout" disabled={!preview}>
          Apply Layout
        </Button>
      </DialogActions>
    </Dialog>
  );
};

interface TemplateSaveDialogProps {
  open: boolean;
  templateName: string;
  onTemplateNameChange: (name: string) => void;
  onClose: () => void;
  onSave: () => void;
}

/**
 * Save template dialog
 */
export const TemplateSaveDialog: React.FC<TemplateSaveDialogProps> = ({
  open,
  templateName,
  onTemplateNameChange,
  onClose,
  onSave,
}) => {
  return (
    <Dialog open={open} onClose={onClose}>
      <DialogTitle>Save Template</DialogTitle>
      <DialogContent>
        <TextField
          autoFocus
          margin="dense"
          label="Template Name"
          type="text"
          fullWidth
          value={templateName}
          onChange={(event) => onTemplateNameChange(event.target.value)}
          inputProps={{ 'data-testid': 'template-name' }}
        />
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button onClick={onSave} data-testid="save-template-confirm">
          Save Template
        </Button>
      </DialogActions>
    </Dialog>
  );
};
