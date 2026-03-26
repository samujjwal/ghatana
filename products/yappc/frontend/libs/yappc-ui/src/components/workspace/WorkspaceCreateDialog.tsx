/**
 * WorkspaceCreateDialog
 *
 * Modal dialog for creating a new workspace.  Validates the name client-side
 * before calling `onCreate`.
 *
 * @doc.type component
 * @doc.purpose Create-workspace form in a modal dialog
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
} from '@mui/material';

export interface WorkspaceCreateDialogProps {
    open: boolean;
    onClose: () => void;
    onCreate: (data: { name: string; description?: string }) => Promise<void>;
    isLoading?: boolean;
}

/**
 * Dialog for creating a new workspace.
 */
export const WorkspaceCreateDialog: React.FC<WorkspaceCreateDialogProps> = ({
    open,
    onClose,
    onCreate,
    isLoading = false,
}) => {
    const [name, setName] = useState('');
    const [description, setDescription] = useState('');
    const [nameError, setNameError] = useState('');

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!name.trim()) {
            setNameError('Workspace name is required.');
            return;
        }
        if (name.trim().length < 2) {
            setNameError('Name must be at least 2 characters.');
            return;
        }
        try {
            await onCreate({ name: name.trim(), description: description.trim() || undefined });
            handleClose();
        } catch (err) {
            setNameError(err instanceof Error ? err.message : 'Failed to create workspace.');
        }
    };

    const handleClose = () => {
        setName('');
        setDescription('');
        setNameError('');
        onClose();
    };

    return (
        <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
            <form onSubmit={handleSubmit}>
                <DialogTitle>Create workspace</DialogTitle>

                <DialogContent>
                    <Box display="flex" flexDirection="column" gap={2} pt={1}>
                        <Typography variant="body2" color="text.secondary">
                            A workspace groups projects and allows you to collaborate with team
                            members.
                        </Typography>

                        <TextField
                            label="Workspace name"
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
