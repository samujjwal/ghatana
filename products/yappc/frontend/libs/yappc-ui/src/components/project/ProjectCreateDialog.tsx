/**
 * ProjectCreateDialog
 *
 * Modal dialog for creating a new project within a workspace.
 *
 * @doc.type component
 * @doc.purpose Create-project form in a modal dialog
 * @doc.layer product
 * @doc.pattern Form Component
 */

import React, { useState } from 'react';
import {
    Dialog,
    DialogTitle,
    DialogContent,
    DialogActions,
    TextField,
    Button,
    Box,
    Typography,
    CircularProgress,
    Select,
    MenuItem,
    InputLabel,
    FormControl,
} from '@mui/material';

import type { ProjectType } from '@yappc/core/types';

const PROJECT_TYPES: ProjectType[] = [
    'FULL_STACK',
    'FRONTEND',
    'BACKEND',
    'MOBILE',
    'DATA',
    'INFRASTRUCTURE',
    'OTHER',
];

export interface ProjectCreateDialogProps {
    open: boolean;
    onClose: () => void;
    onCreate: (data: {
        name: string;
        description?: string;
        type: ProjectType;
    }) => Promise<void>;
    isLoading?: boolean;
}

/**
 * Dialog for creating a new project.
 */
export const ProjectCreateDialog: React.FC<ProjectCreateDialogProps> = ({
    open,
    onClose,
    onCreate,
    isLoading = false,
}) => {
    const [name, setName] = useState('');
    const [description, setDescription] = useState('');
    const [type, setType] = useState<ProjectType>('FULL_STACK');
    const [nameError, setNameError] = useState('');

    const submitForm = async (): Promise<void> => {
        const trimmedName = name.trim();
        if (!trimmedName) {
            setNameError('Project name is required.');
            return;
        }
        if (trimmedName.length < 2) {
            setNameError('Name must be at least 2 characters.');
            return;
        }
        try {
            await onCreate({
                name: trimmedName,
                description: description.trim() || undefined,
                type,
            });
            handleClose();
        } catch (err) {
            setNameError(err instanceof Error ? err.message : 'Failed to create project.');
        }
    };

    const handleSubmit = (e: React.FormEvent) => {
        e.preventDefault();
        void submitForm();
    };

    const handleClose = () => {
        setName('');
        setDescription('');
        setType('FULL_STACK');
        setNameError('');
        onClose();
    };

    return (
        <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
            <form onSubmit={handleSubmit}>
                <DialogTitle>Create project</DialogTitle>

                <DialogContent>
                    <Box display="flex" flexDirection="column" gap={2} pt={1}>
                        <Typography variant="body2" color="text.secondary">
                            Projects contain your canvas documents, pages, and AI-assisted
                            automation flows.
                        </Typography>

                        <TextField
                            label="Project name"
                            value={name}
                            onChange={(e) => {
                                setName(e.target.value);
                                if (nameError) setNameError('');
                            }}
                            error={Boolean(nameError)}
                            helperText={nameError}
                            required
                            autoFocus
                            fullWidth
                            inputProps={{ maxLength: 80 }}
                        />

                        <FormControl fullWidth>
                            <InputLabel id="project-type-label">Project type</InputLabel>
                            <Select
                                labelId="project-type-label"
                                value={type}
                                label="Project type"
                                onChange={(e) => setType(e.target.value as ProjectType)}
                            >
                                {PROJECT_TYPES.map((t) => (
                                    <MenuItem key={t} value={t}>
                                        {t.replace('_', ' ')}
                                    </MenuItem>
                                ))}
                            </Select>
                        </FormControl>

                        <TextField
                            label="Description (optional)"
                            value={description}
                            onChange={(e) => setDescription(e.target.value)}
                            multiline
                            rows={3}
                            fullWidth
                            inputProps={{ maxLength: 500 }}
                        />
                    </Box>
                </DialogContent>

                <DialogActions>
                    <Button onClick={handleClose} disabled={isLoading}>
                        Cancel
                    </Button>
                    <Button
                        type="submit"
                        variant="contained"
                        disabled={isLoading || !name.trim()}
                        startIcon={isLoading ? <CircularProgress size={16} /> : undefined}
                    >
                        Create
                    </Button>
                </DialogActions>
            </form>
        </Dialog>
    );
};
