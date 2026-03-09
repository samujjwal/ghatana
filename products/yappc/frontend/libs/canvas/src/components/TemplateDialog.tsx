/**
 * Template Selection Dialog
 * 
 * Modal dialog for selecting and loading journey templates.
 * Displays available templates with descriptions and persona information.
 * 
 * @module TemplateDialog
 */

import React, { useState } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  ListItem,
  ListItemText,
  Box,
  Chip,
  Typography,
  InteractiveList as List,
} from '@ghatana/ui';
import { ListItemButton } from '@ghatana/ui';
import { journeyTemplates } from '../templates/journeyTemplates';

/**
 *
 */
export interface TemplateDialogProps {
    open: boolean;
    onClose: () => void;
    onSelect: (templateId: string) => void;
}

/**
 * Dialog for selecting journey templates
 */
export const TemplateDialog: React.FC<TemplateDialogProps> = ({ open, onClose, onSelect }) => {
    const [selectedTemplate, setSelectedTemplate] = useState<string | null>(null);

    const handleSelect = () => {
        if (selectedTemplate) {
            onSelect(selectedTemplate);
            onClose();
        }
    };

    return (
        <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
            <DialogTitle>Load Journey Template</DialogTitle>
            <DialogContent>
                <Box className="mt-4">
                    <List>
                        {journeyTemplates.map((template) => (
                            <ListItem key={template.id} disablePadding>
                                <ListItemButton
                                    selected={selectedTemplate === template.id}
                                    onClick={() => setSelectedTemplate(template.id)}
                                >
                                    <ListItemText
                                        primary={
                                            <Box className="flex items-center gap-2">
                                                <Typography variant="subtitle1">{template.name}</Typography>
                                                <Chip
                                                    label={template.persona}
                                                    size="small"
                                                    color="primary"
                                                    variant="outlined"
                                                />
                                                <Chip
                                                    label={`${template.nodes.length} nodes`}
                                                    size="small"
                                                    variant="outlined"
                                                />
                                            </Box>
                                        }
                                        secondary={template.description}
                                    />
                                </ListItemButton>
                            </ListItem>
                        ))}
                    </List>
                </Box>
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose}>Cancel</Button>
                <Button
                    onClick={handleSelect}
                    variant="contained"
                    disabled={!selectedTemplate}
                    data-testid="load-template-button"
                >
                    Load Template
                </Button>
            </DialogActions>
        </Dialog>
    );
};
