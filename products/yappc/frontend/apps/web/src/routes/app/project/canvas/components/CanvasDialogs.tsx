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
} from '@ghatana/ui';
import { TextField } from '@ghatana/ui';

interface LayoutDialogProps {
  open: boolean;
  onClose: () => void;
  onApply: () => void;
}

/**
 * Auto-layout confirmation dialog
 */
export const LayoutDialog: React.FC<LayoutDialogProps> = ({
  open,
  onClose,
  onApply,
}) => {
  return (
    <Dialog open={open} onClose={onClose}>
      <DialogTitle>Auto Layout</DialogTitle>
      <DialogContent>
        <Typography variant="body2">
          Arrange canvas nodes into a tidy grid layout. Existing positions will be replaced.
        </Typography>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button onClick={onApply} data-testid="apply-layout">
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
