/**
 * CanvasVersionHistory
 *
 * Sidebar panel that lists saved canvas versions and lets the user
 * restore any previous snapshot.
 *
 * @doc.type component
 * @doc.purpose Display and interact with canvas version history
 * @doc.layer product
 * @doc.pattern Presentational Component
 */

import React, { useState } from 'react';
import {
    Box,
    Typography,
    List,
    ListItem,
    ListItemText,
    ListItemSecondaryAction,
    Button,
    Chip,
    CircularProgress,
    Alert,
    Divider,
    Tooltip,
} from '@mui/material';
import { History as HistoryIcon, RotateCcw as RestoreIcon } from 'lucide-react';
import type { CanvasVersion } from '@yappc/core/types';

export interface CanvasVersionHistoryProps {
    versions: CanvasVersion[];
    isLoading?: boolean;
    isRestoring?: boolean;
    error?: Error | null;
    currentVersion?: number;
    onRestore?: (version: CanvasVersion) => Promise<void>;
    onLoadMore?: () => void;
    hasMore?: boolean;
    className?: string;
}

const CHANGE_TYPE_LABEL: Record<CanvasVersion['changeType'], string> = {
    MANUAL_SAVE: 'Manual',
    AUTO_SAVE: 'Auto',
    RESTORE: 'Restore',
    MERGE: 'Merge',
};

const CHANGE_TYPE_COLOR: Record<
    CanvasVersion['changeType'],
    'default' | 'primary' | 'success' | 'warning'
> = {
    MANUAL_SAVE: 'primary',
    AUTO_SAVE: 'default',
    RESTORE: 'warning',
    MERGE: 'success',
};

/**
 * Panel for browsing and restoring canvas version history.
 */
export const CanvasVersionHistory: React.FC<CanvasVersionHistoryProps> = ({
    versions,
    isLoading,
    isRestoring,
    error,
    currentVersion,
    onRestore,
    onLoadMore,
    hasMore,
    className,
}) => {
    const [confirmingId, setConfirmingId] = useState<string | null>(null);

    const handleRestoreClick = (version: CanvasVersion) => {
        setConfirmingId(version.id);
    };

    const handleRestoreConfirm = async (version: CanvasVersion) => {
        setConfirmingId(null);
        await onRestore?.(version);
    };

    return (
        <Box className={className} display="flex" flexDirection="column" gap={1} height="100%">
            <Box display="flex" alignItems="center" gap={0.75} px={1} pt={1}>
                <HistoryIcon size={16} />
                <Typography variant="subtitle2" fontWeight={600}>
                    Version history
                </Typography>
            </Box>

            <Divider />

            {error && (
                <Alert severity="error" sx={{ mx: 1 }}>
                    {error.message}
                </Alert>
            )}

            {isLoading ? (
                <Box display="flex" justifyContent="center" py={3}>
                    <CircularProgress size={24} />
                </Box>
            ) : versions.length === 0 ? (
                <Box display="flex" justifyContent="center" py={3}>
                    <Typography variant="body2" color="text.secondary">
                        No versions saved yet.
                    </Typography>
                </Box>
            ) : (
                <List dense sx={{ flexGrow: 1, overflowY: 'auto', px: 0.5 }}>
                    {versions.map((version) => {
                        const isCurrent = version.version === currentVersion;
                        const isConfirming = confirmingId === version.id;

                        return (
                            <ListItem
                                key={version.id}
                                sx={{
                                    borderRadius: 1,
                                    bgcolor: isCurrent ? 'action.selected' : undefined,
                                    mb: 0.5,
                                }}
                            >
                                <ListItemText
                                    primary={
                                        <Box display="flex" alignItems="center" gap={0.5}>
                                            <Typography variant="body2" fontWeight={isCurrent ? 700 : 400}>
                                                v{version.version}
                                            </Typography>
                                            <Chip
                                                label={CHANGE_TYPE_LABEL[version.changeType]}
                                                size="small"
                                                color={CHANGE_TYPE_COLOR[version.changeType]}
                                                variant="outlined"
                                                sx={{ height: 16, fontSize: 10 }}
                                            />
                                            {isCurrent && (
                                                <Chip
                                                    label="current"
                                                    size="small"
                                                    color="primary"
                                                    sx={{ height: 16, fontSize: 10 }}
                                                />
                                            )}
                                        </Box>
                                    }
                                    secondary={
                                        <>
                                            {version.changeSummary && (
                                                <Typography variant="caption" display="block" noWrap>
                                                    {version.changeSummary}
                                                </Typography>
                                            )}
                                            <Typography variant="caption" color="text.disabled">
                                                {new Date(version.createdAt).toLocaleString()}
                                            </Typography>
                                        </>
                                    }
                                />

                                {onRestore && !isCurrent && (
                                    <ListItemSecondaryAction>
                                        {isConfirming ? (
                                            <Box display="flex" gap={0.5}>
                                                <Button
                                                    size="small"
                                                    variant="contained"
                                                    color="warning"
                                                    onClick={() => handleRestoreConfirm(version)}
                                                    disabled={isRestoring}
                                                >
                                                    {isRestoring ? <CircularProgress size={12} /> : 'Confirm'}
                                                </Button>
                                                <Button
                                                    size="small"
                                                    variant="text"
                                                    onClick={() => setConfirmingId(null)}
                                                >
                                                    Cancel
                                                </Button>
                                            </Box>
                                        ) : (
                                            <Tooltip title="Restore this version">
                                                <Button
                                                    size="small"
                                                    variant="text"
                                                    startIcon={<RestoreIcon size={14} />}
                                                    onClick={() => handleRestoreClick(version)}
                                                >
                                                    Restore
                                                </Button>
                                            </Tooltip>
                                        )}
                                    </ListItemSecondaryAction>
                                )}
                            </ListItem>
                        );
                    })}

                    {hasMore && (
                        <Box display="flex" justifyContent="center" py={1}>
                            <Button size="small" variant="text" onClick={onLoadMore}>
                                Load more
                            </Button>
                        </Box>
                    )}
                </List>
            )}
        </Box>
    );
};
