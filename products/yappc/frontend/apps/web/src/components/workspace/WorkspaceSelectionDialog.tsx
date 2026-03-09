/**
 * Workspace Selection Dialog
 * 
 * Shown when a project is part of multiple workspaces.
 * Allows user to select which workspace context to use.
 * 
 * @doc.type component
 * @doc.purpose Workspace context selection for projects
 * @doc.layer product
 * @doc.pattern Dialog Component
 */

import { useState } from 'react';
import { Dialog, DialogTitle, DialogContent, DialogActions, Button, InteractiveList as List, ListItemButton, ListItemIcon, ListItemText, Radio, Typography, Box } from '@ghatana/ui';
import { Building as Business, CheckCircle } from 'lucide-react';

interface Workspace {
    id: string;
    name: string;
    description?: string;
    isOwner?: boolean;
}

interface WorkspaceSelectionDialogProps {
    open: boolean;
    projectName: string;
    workspaces: Workspace[];
    defaultWorkspaceId?: string;
    onSelect: (workspaceId: string) => void;
    onCancel: () => void;
}

/**
 * Dialog for selecting workspace when project is in multiple workspaces
 */
export function WorkspaceSelectionDialog({
    open,
    projectName,
    workspaces,
    defaultWorkspaceId,
    onSelect,
    onCancel,
}: WorkspaceSelectionDialogProps) {
    const [selectedWorkspaceId, setSelectedWorkspaceId] = useState<string>(
        defaultWorkspaceId || workspaces[0]?.id || ''
    );

    const handleConfirm = () => {
        if (selectedWorkspaceId) {
            onSelect(selectedWorkspaceId);
        }
    };

    return (
        <Dialog
            open={open}
            onClose={onCancel}
            size="sm"
            fullWidth
            aria-labelledby="workspace-selection-dialog-title"
        >
            <DialogTitle id="workspace-selection-dialog-title">
                <Box display="flex" alignItems="center" gap={1}>
                    <Business tone="primary" />
                    <span>Select Workspace</span>
                </Box>
            </DialogTitle>
            <DialogContent>
                <Typography as="p" className="mb-6 text-sm" color="text.secondary">
                    <strong>{projectName}</strong> is part of multiple workspaces.
                    Choose which workspace context to open it in.
                </Typography>

                <List>
                    {workspaces.map((workspace) => (
                        <ListItemButton
                            key={workspace.id}
                            selected={selectedWorkspaceId === workspace.id}
                            onClick={() => setSelectedWorkspaceId(workspace.id)}
                            className="rounded mb-2 border border-solid" style={{ borderColor: selectedWorkspaceId === workspace.id
                                    ? 'primary.main'
                                    : 'divider' }}
                        >
                            <ListItemIcon>
                                <Radio
                                    edge="start"
                                    checked={selectedWorkspaceId === workspace.id}
                                    tabIndex={-1}
                                    disableRipple
                                />
                            </ListItemIcon>
                            <ListItemText
                                primary={
                                    <Box display="flex" alignItems="center" gap={1}>
                                        <span>{workspace.name}</span>
                                        {workspace.isOwner && (
                                            <CheckCircle
                                                className="text-green-600 text-base"
                                                titleAccess="Owner"
                                            />
                                        )}
                                    </Box>
                                }
                                secondary={workspace.description || 'No description'}
                            />
                        </ListItemButton>
                    ))}
                </List>
            </DialogContent>
            <DialogActions className="px-6 pb-4">
                <Button onClick={onCancel} tone="neutral">
                    Cancel
                </Button>
                <Button
                    onClick={handleConfirm}
                    variant="solid"
                    disabled={!selectedWorkspaceId}
                >
                    Open in Workspace
                </Button>
            </DialogActions>
        </Dialog>
    );
}
