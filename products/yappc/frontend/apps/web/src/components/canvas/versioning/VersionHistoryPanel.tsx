/**
 * Version History Panel Component
 * 
 * Displays version history with ability to view diffs and restore snapshots.
 * 
 * @doc.type component
 * @doc.purpose Version history visualization and management
 * @doc.layer product
 * @doc.pattern Presentation Component
 */

import React, { useState } from 'react';
import {
  Box,
  Button,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  ListItem,
  ListItemText,
  Typography,
  Chip,
  IconButton,
  Stack,
  InteractiveList as List,
  Surface as Paper,
} from '@ghatana/ui';
import { TextField } from '@ghatana/ui';
import { RotateCcw as Restore, Eye as Visibility, Trash2 as Delete, Save } from 'lucide-react';

import type { CanvasSnapshot } from '../../../services/canvas/CanvasPersistence';

/**
 * Props for VersionHistoryPanel
 */
export interface VersionHistoryPanelProps {
    snapshots: CanvasSnapshot[];
    currentVersion?: number;
    onRestore: (snapshotId: string) => void;
    onDelete?: (snapshotId: string) => void;
    onCreateSnapshot?: (label: string, description?: string) => void;
    onViewDiff?: (snapshot1: CanvasSnapshot, snapshot2: CanvasSnapshot) => void;
    open: boolean;
    onClose: () => void;
}

/**
 * Format timestamp to readable date
 */
function formatDate(timestamp: number): string {
    const date = new Date(timestamp);
    const now = new Date();
    const diff = now.getTime() - date.getTime();

    // Less than 1 minute
    if (diff < 60000) {
        return 'Just now';
    }

    // Less than 1 hour
    if (diff < 3600000) {
        const minutes = Math.floor(diff / 60000);
        return `${minutes}m ago`;
    }

    // Less than 1 day
    if (diff < 86400000) {
        const hours = Math.floor(diff / 3600000);
        return `${hours}h ago`;
    }

    // Less than 7 days
    if (diff < 604800000) {
        const days = Math.floor(diff / 86400000);
        return `${days}d ago`;
    }

    // Formatted date
    return date.toLocaleDateString() + ' ' + date.toLocaleTimeString([], {
        hour: '2-digit',
        minute: '2-digit',
    });
}

/**
 * Version History List Component
 */
export const VersionHistoryList: React.FC<{
    snapshots: CanvasSnapshot[];
    currentVersion?: number;
    onRestore: (snapshotId: string) => void;
    onDelete?: (snapshotId: string) => void;
    onViewDiff?: (snapshot: CanvasSnapshot) => void;
}> = ({ snapshots, currentVersion, onRestore, onDelete, onViewDiff }) => {
    const sortedSnapshots = [...snapshots].sort((a, b) => b.timestamp - a.timestamp);

    if (sortedSnapshots.length === 0) {
        return (
            <Box py={4} textAlign="center">
                <Typography variant="body2" color="text.secondary">
                    No version history available
                </Typography>
            </Box>
        );
    }

    return (
        <List className="p-0">
            {sortedSnapshots.map((snapshot, index) => {
                const isCurrent = snapshot.version === currentVersion;

                return (
                    <Paper
                        key={snapshot.id}
                        elevation={0}
                        className="mb-2 p-3 rounded-lg border border-solid"
                        style={{
                            borderColor: isCurrent ? '#1976d2' : '#d1d5db',
                            backgroundColor: isCurrent ? 'rgba(25, 118, 210, 0.08)' : 'transparent',
                        }}
                    >
                        <Box className="flex justify-between items-start">
                            <Box className="flex-1">
                                <Box display="flex" alignItems="center" gap={1} mb={0.5}>
                                    <Typography variant="subtitle2" fontWeight="700">
                                        v{snapshot.version}
                                    </Typography>
                                    {snapshot.label && (
                                        <Typography variant="body2" noWrap className="max-w-[120px]">
                                            {snapshot.label}
                                        </Typography>
                                    )}
                                    {isCurrent && <StatusChip label="Current" color="primary" />}
                                </Box>
                                <Typography variant="caption" color="text.disabled" display="block">
                                    {formatDate(snapshot.timestamp)} {snapshot.author && `• ${snapshot.author}`}
                                </Typography>
                            </Box>

                            <Stack direction="row">
                                {!isCurrent && (
                                    <IconButton
                                        size="small"
                                        color="primary"
                                        onClick={() => onRestore(snapshot.id)}
                                        className="p-1"
                                    >
                                        <Restore className="text-lg" />
                                    </IconButton>
                                )}
                                {onViewDiff && index < sortedSnapshots.length - 1 && (
                                    <IconButton
                                        size="small"
                                        onClick={() => onViewDiff(snapshot)}
                                        className="p-1"
                                    >
                                        <Visibility className="text-lg" />
                                    </IconButton>
                                )}
                            </Stack>
                        </Box>
                    </Paper>
                );
            })}
        </List>
    );
};

const StatusChip = ({ label, color }: { label: string, color: 'default' | 'primary' | 'secondary' | 'error' | 'info' | 'success' | 'warning' }) => (
    <Chip label={label} color={color} size="small" className="text-[0.625rem] font-extrabold uppercase" />
);

/**
 * Version History Panel Component
 */
export const VersionHistoryPanel: React.FC<VersionHistoryPanelProps> = ({
    snapshots,
    currentVersion,
    onRestore,
    onDelete,
    onCreateSnapshot,
    onViewDiff,
    open,
    onClose,
}) => {
    const [showCreateDialog, setShowCreateDialog] = useState(false);
    const [newSnapshotLabel, setNewSnapshotLabel] = useState('');
    const [newSnapshotDescription, setNewSnapshotDescription] = useState('');

    const handleCreateSnapshot = () => {
        if (newSnapshotLabel.trim() && onCreateSnapshot) {
            onCreateSnapshot(newSnapshotLabel.trim(), newSnapshotDescription.trim() || undefined);
            setNewSnapshotLabel('');
            setNewSnapshotDescription('');
            setShowCreateDialog(false);
        }
    };

    return (
        <>
            <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth className="backdrop-blur-[4px]">
                <DialogTitle className="pb-4 border-b border-solid border-gray-200 dark:border-gray-700">
                    <Box display="flex" justifyContent="space-between" alignItems="center">
                        <Typography variant="h6" fontWeight="800">Version History</Typography>
                        {onCreateSnapshot && (
                            <Button
                                variant="contained"
                                size="small"
                                startIcon={<Save />}
                                onClick={() => setShowCreateDialog(true)}
                                className="rounded-lg"
                            >
                                Create Snapshot
                            </Button>
                        )}
                    </Box>
                </DialogTitle>

                <DialogContent className="pt-6">
                    <VersionHistoryList
                        snapshots={snapshots}
                        currentVersion={currentVersion}
                        onRestore={onRestore}
                        onDelete={onDelete}
                        onViewDiff={onViewDiff ? (s) => {
                            const sorted = [...snapshots].sort((a, b) => b.timestamp - a.timestamp);
                            const idx = sorted.findIndex(ss => ss.id === s.id);
                            if (idx < sorted.length - 1) onViewDiff(s, sorted[idx + 1]);
                        } : undefined}
                    />
                </DialogContent>

                <DialogActions className="p-4 border-t border-solid border-gray-200 dark:border-gray-700">
                    <Button onClick={onClose} variant="outlined" className="rounded-lg">Close</Button>
                </DialogActions>
            </Dialog>

            {/* Create Snapshot Dialog */}
            <Dialog open={showCreateDialog} onClose={() => setShowCreateDialog(false)}>
                <DialogTitle className="font-extrabold">Create Manual Snapshot</DialogTitle>
                <DialogContent>
                    <Stack spacing={2} className="mt-2 min-w-[400px]">
                        <TextField
                            label="Snapshot Name"
                            value={newSnapshotLabel}
                            onChange={(e: React.ChangeEvent<HTMLInputElement>) => setNewSnapshotLabel(e.target.value)}
                            fullWidth
                            required
                            autoFocus
                            placeholder="e.g. Pre-auth implementation"
                        />
                        <TextField
                            label="Description (optional)"
                            value={newSnapshotDescription}
                            onChange={(e: React.ChangeEvent<HTMLInputElement>) => setNewSnapshotDescription(e.target.value)}
                            fullWidth
                            multiline
                            rows={3}
                            placeholder="What changed in this version?"
                        />
                    </Stack>
                </DialogContent>
                <DialogActions className="p-4">
                    <Button onClick={() => setShowCreateDialog(false)} className="rounded-lg">Cancel</Button>
                    <Button
                        onClick={handleCreateSnapshot}
                        variant="contained"
                        disabled={!newSnapshotLabel.trim()}
                        className="rounded-lg"
                    >
                        Create Version
                    </Button>
                </DialogActions>
            </Dialog>
        </>
    );
};

export default VersionHistoryPanel;
