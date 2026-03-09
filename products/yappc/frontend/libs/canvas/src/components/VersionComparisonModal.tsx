/**
 * Version Comparison Modal Component
 * Feature 1.4: Document Management - UI Component
 * 
 * Provides UI for comparing document versions with structural change highlighting.
 * Integrates with historyManager utilities for version diff operations.
 */

import { X as CloseIcon, GitCompare as CompareIcon, GitCompareArrows as CompareArrows, PlusCircle as AddIcon, MinusCircle as RemoveIcon, Pencil as ModifyIcon, AlertTriangle as WarningIcon } from 'lucide-react';
import { Dialog, DialogTitle, DialogContent, DialogActions, Button, Typography, Stack, IconButton, Box, Divider, Chip, InteractiveList as List, ListItem, ListItemText, FormControl, InputLabel, Select, MenuItem, Alert, Surface as Paper } from '@ghatana/ui';
import React, { useState, useMemo } from 'react';

import type { DocumentVersion, VersionDiff } from '../history/historyManager';

/**
 *
 */
export interface VersionComparisonModalProps<T = unknown> {
    open: boolean;
    onClose: () => void;
    versions: DocumentVersion<T>[];
    onCompareVersions: (versionId1: string, versionId2: string) => VersionDiff[];
    onRestoreVersion?: (versionId: string) => void;
    currentVersionId?: string;
}

/**
 * Version Comparison Modal
 * 
 * UI component for comparing two document versions side-by-side.
 * Features:
 * - Select two versions to compare
 * - Display structural vs styling changes
 * - Highlight additions, removals, and modifications
 * - Restore previous version
 */
export function VersionComparisonModal<T = unknown>({
    open,
    onClose,
    versions,
    onCompareVersions,
    onRestoreVersion,
    currentVersionId,
}: VersionComparisonModalProps<T>) {
    const [version1Id, setVersion1Id] = useState<string>('');
    const [version2Id, setVersion2Id] = useState<string>('');
    const [showStructuralOnly, setShowStructuralOnly] = useState(false);

    // Sort versions by timestamp (newest first)
    const sortedVersions = useMemo(() => {
        return [...versions].sort((a, b) => b.timestamp - a.timestamp);
    }, [versions]);

    // Auto-select last two versions when dialog opens
    React.useEffect(() => {
        if (open && sortedVersions.length >= 2 && !version1Id && !version2Id) {
            setVersion1Id(sortedVersions[1].id);
            setVersion2Id(sortedVersions[0].id);
        }
    }, [open, sortedVersions, version1Id, version2Id]);

    // Get version diffs when both versions are selected
    const diffs = useMemo(() => {
        if (!version1Id || !version2Id) return [];
        const allDiffs = onCompareVersions(version1Id, version2Id);
        return showStructuralOnly
            ? allDiffs.filter((diff) => diff.isStructural)
            : allDiffs;
    }, [version1Id, version2Id, onCompareVersions, showStructuralOnly]);

    // Categorize diffs by type
    const diffsByType = useMemo(() => {
        const added = diffs.filter((d) => d.type === 'added');
        const removed = diffs.filter((d) => d.type === 'removed');
        const modified = diffs.filter((d) => d.type === 'modified');
        return { added, removed, modified };
    }, [diffs]);

    const structuralChangesCount = diffs.filter((d) => d.isStructural).length;
    const stylingChangesCount = diffs.length - structuralChangesCount;

    const getVersion = (versionId: string) =>
        versions.find((v) => v.id === versionId);

    const formatTimestamp = (timestamp: number) => {
        return new Date(timestamp).toLocaleString();
    };

    const formatPath = (path: string[]) => {
        return path.join(' > ') || 'Root';
    };

    const getDiffIcon = (type: VersionDiff['type']) => {
        switch (type) {
            case 'added':
                return <AddIcon tone="success" size={16} />;
            case 'removed':
                return <RemoveIcon tone="danger" size={16} />;
            case 'modified':
                return <ModifyIcon tone="warning" size={16} />;
        }
    };

    const getDiffColor = (type: VersionDiff['type']) => {
        switch (type) {
            case 'added':
                return 'success.light';
            case 'removed':
                return 'error.light';
            case 'modified':
                return 'warning.light';
        }
    };

    const handleRestore = (versionId: string) => {
        if (onRestoreVersion) {
            onRestoreVersion(versionId);
            onClose();
        }
    };

    return (
        <Dialog
            open={open}
            onClose={(_, __) => onClose()}
            size="md"
            fullWidth
            PaperProps={{
                sx: { height: '80vh' },
            }}
        >
            <DialogTitle>
                <Stack direction="row" alignItems="center" justifyContent="space-between">
                    <Stack direction="row" alignItems="center" spacing={1}>
                        <CompareIcon />
                        <Typography as="h6">Compare Versions</Typography>
                    </Stack>
                    <IconButton onClick={onClose} size="sm">
                        <CloseIcon />
                    </IconButton>
                </Stack>
            </DialogTitle>

            <Divider />

            <DialogContent>
                <Stack spacing={3}>
                    {/* Version Selection */}
                    <Stack direction="row" spacing={2} alignItems="center">
                        <FormControl fullWidth>
                            <InputLabel>Version 1 (Base)</InputLabel>
                            <Select
                                value={version1Id}
                                label="Version 1 (Base)"
                                onChange={(e) => setVersion1Id(e.target.value)}
                                data-testid="version1-select"
                            >
                                {sortedVersions.map((version) => (
                                    <MenuItem key={version.id} value={version.id}>
                                        v{version.version} - {formatTimestamp(version.timestamp)}
                                        {version.id === currentVersionId && ' (Current)'}
                                    </MenuItem>
                                ))}
                            </Select>
                        </FormControl>

                        <CompareArrows className="shrink-0 text-gray-500 dark:text-gray-400" />

                        <FormControl fullWidth>
                            <InputLabel>Version 2 (Compare)</InputLabel>
                            <Select
                                value={version2Id}
                                label="Version 2 (Compare)"
                                onChange={(e) => setVersion2Id(e.target.value)}
                                data-testid="version2-select"
                            >
                                {sortedVersions.map((version) => (
                                    <MenuItem key={version.id} value={version.id}>
                                        v{version.version} - {formatTimestamp(version.timestamp)}
                                        {version.id === currentVersionId && ' (Current)'}
                                    </MenuItem>
                                ))}
                            </Select>
                        </FormControl>
                    </Stack>

                    {/* Version Metadata */}
                    {version1Id && version2Id && (
                        <Paper variant="outlined" className="p-4">
                            <Stack direction="row" spacing={4} justifyContent="space-around">
                                <Box>
                                    <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                        Base Version
                                    </Typography>
                                    <Typography as="p" className="text-sm">
                                        v{getVersion(version1Id)?.version}
                                    </Typography>
                                    {getVersion(version1Id)?.author && (
                                        <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                            by {getVersion(version1Id)?.author}
                                        </Typography>
                                    )}
                                </Box>
                                <Box>
                                    <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                        Compare Version
                                    </Typography>
                                    <Typography as="p" className="text-sm">
                                        v{getVersion(version2Id)?.version}
                                    </Typography>
                                    {getVersion(version2Id)?.author && (
                                        <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                            by {getVersion(version2Id)?.author}
                                        </Typography>
                                    )}
                                </Box>
                            </Stack>
                        </Paper>
                    )}

                    {/* Change Summary */}
                    {version1Id && version2Id && (
                        <>
                            <Stack direction="row" spacing={2} alignItems="center" flexWrap="wrap">
                                <Chip
                                    label={`${diffs.length} total change${diffs.length !== 1 ? 's' : ''}`}
                                    tone="primary"
                                    variant="outlined"
                                />
                                <Chip
                                    label={`${structuralChangesCount} structural`}
                                    tone="danger"
                                    icon={<WarningIcon />}
                                    data-testid="structural-changes-chip"
                                />
                                <Chip
                                    label={`${stylingChangesCount} styling`}
                                    tone="info"
                                />
                                <Chip
                                    label={`${diffsByType.added.length} added`}
                                    tone="success"
                                    icon={<AddIcon />}
                                />
                                <Chip
                                    label={`${diffsByType.removed.length} removed`}
                                    tone="danger"
                                    icon={<RemoveIcon />}
                                />
                                <Chip
                                    label={`${diffsByType.modified.length} modified`}
                                    tone="warning"
                                    icon={<ModifyIcon />}
                                />
                            </Stack>

                            <FormControl>
                                <Stack direction="row" alignItems="center" spacing={1}>
                                    <Typography as="p" className="text-sm">Filter:</Typography>
                                    <Button
                                        size="sm"
                                        variant={showStructuralOnly ? 'contained' : 'outlined'}
                                        onClick={() => setShowStructuralOnly(!showStructuralOnly)}
                                        data-testid="structural-only-toggle"
                                    >
                                        Structural Only
                                    </Button>
                                </Stack>
                            </FormControl>

                            {/* Changes List */}
                            {diffs.length === 0 ? (
                                <Alert severity="info">
                                    {showStructuralOnly
                                        ? 'No structural changes between these versions.'
                                        : 'No changes between these versions.'}
                                </Alert>
                            ) : (
                                <List
                                    className="rounded overflow-auto bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-700 max-h-[400px]"
                                    data-testid="version-diff-list"
                                >
                                    {diffs.map((diff, index) => (
                                        <ListItem
                                            key={index}
                                            className="hover:bg-gray-100 hover:dark:bg-gray-800" style={{ borderColor: getDiffColor(diff.type), borderLeft: '4px solid' }} data-testid={`diff-item-${diff.type}`}
                                        >
                                            <Stack direction="row" spacing={1} alignItems="flex-start" width="100%">
                                                {getDiffIcon(diff.type)}
                                                <Box flexGrow={1}>
                                                    <ListItemText
                                                        primary={
                                                            <Stack direction="row" alignItems="center" spacing={1}>
                                                                <Typography as="p" className="text-sm" fontWeight="medium">
                                                                    {formatPath(diff.path)}
                                                                </Typography>
                                                                {diff.isStructural && (
                                                                    <Chip
                                                                        label="Structural"
                                                                        size="sm"
                                                                        tone="danger"
                                                                        className="h-[20px]"
                                                                    />
                                                                )}
                                                            </Stack>
                                                        }
                                                        secondary={
                                                            <Stack spacing={0.5} className="mt-1">
                                                                <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                                                    Type: {diff.type}
                                                                </Typography>
                                                                {diff.type === 'modified' && (
                                                                    <>
                                                                        <Typography
                                                                            as="span" className="text-xs text-gray-500"
                                                                            className="text-red-600 font-mono"
                                                                        >
                                                                            - {JSON.stringify(diff.before)}
                                                                        </Typography>
                                                                        <Typography
                                                                            as="span" className="text-xs text-gray-500"
                                                                            className="text-green-600 font-mono"
                                                                        >
                                                                            + {JSON.stringify(diff.after)}
                                                                        </Typography>
                                                                    </>
                                                                )}
                                                                {diff.type === 'added' && (
                                                                    <Typography
                                                                        as="span" className="text-xs text-gray-500"
                                                                        className="text-green-600 font-mono"
                                                                    >
                                                                        + {JSON.stringify(diff.after)}
                                                                    </Typography>
                                                                )}
                                                                {diff.type === 'removed' && (
                                                                    <Typography
                                                                        as="span" className="text-xs text-gray-500"
                                                                        className="text-red-600 font-mono"
                                                                    >
                                                                        - {JSON.stringify(diff.before)}
                                                                    </Typography>
                                                                )}
                                                            </Stack>
                                                        }
                                                    />
                                                </Box>
                                            </Stack>
                                        </ListItem>
                                    ))}
                                </List>
                            )}
                        </>
                    )}
                </Stack>
            </DialogContent>

            <DialogActions>
                <Button onClick={onClose}>Close</Button>
                {onRestoreVersion && version1Id && version1Id !== currentVersionId && (
                    <Button
                        variant="outlined"
                        onClick={() => handleRestore(version1Id)}
                        data-testid="restore-version1-btn"
                    >
                        Restore v{getVersion(version1Id)?.version}
                    </Button>
                )}
                {onRestoreVersion && version2Id && version2Id !== currentVersionId && (
                    <Button
                        variant="solid"
                        onClick={() => handleRestore(version2Id)}
                        data-testid="restore-version2-btn"
                    >
                        Restore v{getVersion(version2Id)?.version}
                    </Button>
                )}
            </DialogActions>
        </Dialog>
    );
}
