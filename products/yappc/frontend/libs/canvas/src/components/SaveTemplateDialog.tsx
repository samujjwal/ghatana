/**
 * Save Template Dialog
 * 
 * Modal dialog for saving the current canvas as a custom template.
 * Allows users to provide a name and description for the template.
 * 
 * @module SaveTemplateDialog
 */

import React, { useState } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Box,
} from '@ghatana/ui';
import { TextField } from '@ghatana/ui';

/**
 *
 */
export interface SaveTemplateDialogProps {
    open: boolean;
    onClose: () => void;
    onSave: (name: string, description: string) => void;
}

/**
 * Dialog for saving current canvas as a template
 */
export const SaveTemplateDialog: React.FC<SaveTemplateDialogProps> = ({
    open,
    onClose,
    onSave,
}) => {
    const [name, setName] = useState('');
    const [description, setDescription] = useState('');

    const handleSave = () => {
        if (name.trim()) {
            onSave(name.trim(), description.trim());
            setName('');
            setDescription('');
            onClose();
        }
    };

    const handleClose = () => {
        setName('');
        setDescription('');
        onClose();
    };

    return (
        <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
            <DialogTitle>Save as Template</DialogTitle>
            <DialogContent>
                <Box className="mt-4 flex flex-col gap-4">
                    <TextField
                        label="Template Name"
                        value={name}
                        onChange={(e) => setName(e.target.value)}
                        fullWidth
                        required
                        autoFocus
                        placeholder="e.g., My Custom Journey"
                        data-testid="template-name-input"
                    />
                    <TextField
                        label="Description"
                        value={description}
                        onChange={(e) => setDescription(e.target.value)}
                        fullWidth
                        multiline
                        rows={3}
                        placeholder="Describe what this template is for..."
                        data-testid="template-description-input"
                    />
                </Box>
            </DialogContent>
            <DialogActions>
                <Button onClick={handleClose}>Cancel</Button>
                <Button
                    onClick={handleSave}
                    variant="contained"
                    disabled={!name.trim()}
                    data-testid="save-template-button"
                >
                    Save Template
                </Button>
            </DialogActions>
        </Dialog>
    );
};
