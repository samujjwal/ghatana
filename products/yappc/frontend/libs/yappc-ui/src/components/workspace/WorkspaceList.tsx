/**
 * WorkspaceList
 *
 * Renders a scrollable list of WorkspaceCards.
 *
 * @doc.type component
 * @doc.purpose Display a list of workspaces with selection and settings
 * @doc.layer product
 * @doc.pattern Presentational Component
 */

import React from 'react';
import { Box, Typography, CircularProgress, Alert } from '@mui/material';
import { WorkspaceCard } from './WorkspaceCard';
import type { Workspace } from '@yappc/core/types';

export interface WorkspaceListProps {
    workspaces: Workspace[];
    selectedId?: string | null;
    isLoading?: boolean;
    error?: Error | null;
    onSelect?: (workspace: Workspace) => void;
    onSettings?: (workspace: Workspace) => void;
    emptyMessage?: string;
    className?: string;
}

/**
 * Renders a list of workspaces.
 */
export const WorkspaceList: React.FC<WorkspaceListProps> = ({
    workspaces,
    selectedId,
    isLoading,
    error,
    onSelect,
    onSettings,
    emptyMessage = 'No workspaces found.',
    className,
}) => {
    if (isLoading) {
        return (
            <Box display="flex" justifyContent="center" py={4}>
                <CircularProgress size={32} />
            </Box>
        );
    }

    if (error) {
        return (
            <Alert severity="error" sx={{ m: 1 }}>
                {error.message}
            </Alert>
        );
    }

    if (workspaces.length === 0) {
        return (
            <Box display="flex" justifyContent="center" py={4}>
                <Typography variant="body2" color="text.secondary">
                    {emptyMessage}
                </Typography>
            </Box>
        );
    }

    return (
        <Box
            className={className}
            display="flex"
            flexDirection="column"
            gap={1}
            sx={{ overflowY: 'auto' }}
        >
            {workspaces.map((ws) => (
                <WorkspaceCard
                    key={ws.id}
                    workspace={ws}
                    isSelected={ws.id === selectedId}
                    onSelect={onSelect}
                    onSettings={onSettings}
                />
            ))}
        </Box>
    );
};
