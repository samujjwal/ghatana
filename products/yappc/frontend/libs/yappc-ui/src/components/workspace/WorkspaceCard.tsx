/**
 * WorkspaceCard
 *
 * Displays a workspace as a clickable summary card.
 *
 * @doc.type component
 * @doc.purpose Display a workspace summary with name, description, and owner
 * @doc.layer product
 * @doc.pattern Presentational Component
 */

import React from 'react';
import {
    Card,
    CardContent,
    Typography,
    Box,
    Avatar,
    Chip,
    IconButton,
    Tooltip,
} from '@mui/material';
import { Settings as SettingsIcon, Star as StarIcon } from 'lucide-react';

import type { Workspace } from '@yappc/core/types';

export interface WorkspaceCardProps {
    workspace: Workspace;
    isSelected?: boolean;
    onSelect?: (workspace: Workspace) => void;
    onSettings?: (workspace: Workspace) => void;
    className?: string;
}

/**
 * Card component for a single workspace.
 */
export const WorkspaceCard: React.FC<WorkspaceCardProps> = ({
    workspace,
    isSelected = false,
    onSelect,
    onSettings,
    className,
}) => {
    const initials = workspace.name
        .split(' ')
        .map((w) => w[0])
        .slice(0, 2)
        .join('')
        .toUpperCase();

    return (
        <Card
            className={className}
            variant="outlined"
            sx={{
                cursor: onSelect ? 'pointer' : 'default',
                border: isSelected ? 2 : 1,
                borderColor: isSelected ? 'primary.main' : 'divider',
                transition: 'all 0.15s ease',
                '&:hover': onSelect ? { borderColor: 'primary.light', boxShadow: 1 } : {},
            }}
            onClick={onSelect ? () => onSelect(workspace) : undefined}
        >
            <CardContent>
                <Box display="flex" alignItems="flex-start" gap={1.5}>
                    <Avatar
                        sx={{
                            bgcolor: isSelected ? 'primary.main' : 'secondary.main',
                            width: 40,
                            height: 40,
                            fontSize: 14,
                            fontWeight: 700,
                        }}
                    >
                        {initials}
                    </Avatar>

                    <Box flexGrow={1} minWidth={0}>
                        <Box display="flex" alignItems="center" gap={0.5}>
                            <Typography
                                variant="subtitle2"
                                fontWeight={600}
                                noWrap
                                sx={{ flexGrow: 1 }}
                            >
                                {workspace.name}
                            </Typography>
                            {workspace.isDefault && (
                                <Tooltip title="Default workspace">
                                    <StarIcon size={14} color="orange" />
                                </Tooltip>
                            )}
                        </Box>

                        {workspace.description && (
                            <Typography
                                variant="caption"
                                color="text.secondary"
                                sx={{
                                    display: '-webkit-box',
                                    WebkitLineClamp: 2,
                                    WebkitBoxOrient: 'vertical',
                                    overflow: 'hidden',
                                }}
                            >
                                {workspace.description}
                            </Typography>
                        )}

                        {workspace.aiTags && workspace.aiTags.length > 0 && (
                            <Box display="flex" flexWrap="wrap" gap={0.5} mt={0.5}>
                                {workspace.aiTags.slice(0, 3).map((tag) => (
                                    <Chip key={tag} label={tag} size="small" variant="outlined" />
                                ))}
                            </Box>
                        )}
                    </Box>

                    {onSettings && (
                        <Tooltip title="Workspace settings">
                            <IconButton
                                size="small"
                                onClick={(e) => {
                                    e.stopPropagation();
                                    onSettings(workspace);
                                }}
                            >
                                <SettingsIcon size={16} />
                            </IconButton>
                        </Tooltip>
                    )}
                </Box>
            </CardContent>
        </Card>
    );
};
