/**
 * Version Diff Viewer Component
 * 
 * Visualizes differences between two canvas versions.
 * Shows added, removed, and modified elements.
 * 
 * @doc.type component
 * @doc.purpose Version comparison visualization
 * @doc.layer product
 * @doc.pattern Presentation Component
 */

import React from 'react';
import {
  Box,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Typography,
  Grid,
  Chip,
  Stack,
  Divider,
  Surface as Paper,
} from '@ghatana/ui';
import { Plus as Add, Minus as Remove, Pencil as Edit, CheckCircle } from 'lucide-react';

import type { CanvasSnapshot, VersionDiff } from '../../../services/canvas';

/**
 * Props for VersionDiffViewer
 */
export interface VersionDiffViewerProps {
    snapshot1: CanvasSnapshot;
    snapshot2: CanvasSnapshot;
    diff: VersionDiff;
    open: boolean;
    onClose: () => void;
    onRestoreVersion?: (snapshotId: string) => void;
}

/**
 * Diff stat item component
 */
interface DiffStatProps {
    icon: React.ReactNode;
    label: string;
    value: number;
    color: string;
}

const DiffStat: React.FC<DiffStatProps> = ({ icon, label, value, color }) => (
    <Paper
        elevation={0}
        className="p-4 flex items-center gap-3 border border-solid border-gray-200 dark:border-gray-700"
    >
        <Box
            className="w-[40px] h-[40px] rounded-full flex items-center justify-center" style={{ backgroundColor: `${color }}
        >
            {icon}
        </Box>
        <Box>
            <Typography variant="h6" component="div">
                {value}
            </Typography>
            <Typography variant="caption" color="text.secondary">
                {label}
            </Typography>
        </Box>
    </Paper>
);

/**
 * Version Diff Viewer Component
 * 
 * Displays a detailed comparison between two canvas versions.
 */
export const VersionDiffViewer: React.FC<VersionDiffViewerProps> = ({
    snapshot1,
    snapshot2,
    diff,
    open,
    onClose,
    onRestoreVersion,
}) => {
    const totalChanges =
        diff.added.nodes +
        diff.added.edges +
        diff.removed.nodes +
        diff.removed.edges +
        diff.modified.nodes +
        diff.modified.edges;

    const formatDate = (timestamp: number) => {
        return new Date(timestamp).toLocaleString();
    };

    return (
        <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
            <DialogTitle>
                <Box display="flex" justifyContent="space-between" alignItems="center">
                    <Typography variant="h6">Version Comparison</Typography>
                    <Chip
                        label={`${totalChanges} changes`}
                        color={totalChanges > 0 ? 'primary' : 'default'}
                        size="small"
                    />
                </Box>
            </DialogTitle>

            <DialogContent>
                <Stack spacing={3}>
                    {/* Version Information */}
                    <Box>
                        <Grid container spacing={2}>
                            <Grid xs={6}>
                                <Paper
                                    elevation={0}
                                    className="p-4 border-[2px_solid] border-blue-600 bg-blue-50" >
                                    <Typography variant="subtitle2" color="primary.main" gutterBottom>
                                        Version {snapshot2.version}
                                        {snapshot2.label && ` - ${snapshot2.label}`}
                                    </Typography>
                                    <Typography variant="caption" display="block" color="text.secondary">
                                        {formatDate(snapshot2.timestamp)}
                                    </Typography>
                                    {snapshot2.author && (
                                        <Typography variant="caption" display="block" color="text.secondary">
                                            by {snapshot2.author}
                                        </Typography>
                                    )}
                                    <Divider className="my-2" />
                                    <Typography variant="caption" display="block">
                                        {snapshot2.data.elements.length} elements
                                    </Typography>
                                    <Typography variant="caption" display="block">
                                        {snapshot2.data.connections.length} connections
                                    </Typography>
                                </Paper>
                            </Grid>

                            <Grid xs={6}>
                                <Paper
                                    elevation={0}
                                    className="p-4 border border-solid border-gray-200 dark:border-gray-700"
                                >
                                    <Typography variant="subtitle2" gutterBottom>
                                        Version {snapshot1.version}
                                        {snapshot1.label && ` - ${snapshot1.label}`}
                                    </Typography>
                                    <Typography variant="caption" display="block" color="text.secondary">
                                        {formatDate(snapshot1.timestamp)}
                                    </Typography>
                                    {snapshot1.author && (
                                        <Typography variant="caption" display="block" color="text.secondary">
                                            by {snapshot1.author}
                                        </Typography>
                                    )}
                                    <Divider className="my-2" />
                                    <Typography variant="caption" display="block">
                                        {snapshot1.data.elements.length} elements
                                    </Typography>
                                    <Typography variant="caption" display="block">
                                        {snapshot1.data.connections.length} connections
                                    </Typography>
                                </Paper>
                            </Grid>
                        </Grid>
                    </Box>

                    {/* Changes Summary */}
                    <Box>
                        <Typography variant="subtitle2" gutterBottom>
                            Changes Summary
                        </Typography>
                        <Grid container spacing={2}>
                            {/* Added */}
                            <Grid xs={6}>
                                <DiffStat
                                    icon={<Add />}
                                    label="Nodes Added"
                                    value={diff.added.nodes}
                                    color="success"
                                />
                            </Grid>
                            <Grid xs={6}>
                                <DiffStat
                                    icon={<Add />}
                                    label="Edges Added"
                                    value={diff.added.edges}
                                    color="success"
                                />
                            </Grid>

                            {/* Modified */}
                            <Grid xs={6}>
                                <DiffStat
                                    icon={<Edit />}
                                    label="Nodes Modified"
                                    value={diff.modified.nodes}
                                    color="warning"
                                />
                            </Grid>
                            <Grid xs={6}>
                                <DiffStat
                                    icon={<Edit />}
                                    label="Edges Modified"
                                    value={diff.modified.edges}
                                    color="warning"
                                />
                            </Grid>

                            {/* Removed */}
                            <Grid xs={6}>
                                <DiffStat
                                    icon={<Remove />}
                                    label="Nodes Removed"
                                    value={diff.removed.nodes}
                                    color="error"
                                />
                            </Grid>
                            <Grid xs={6}>
                                <DiffStat
                                    icon={<Remove />}
                                    label="Edges Removed"
                                    value={diff.removed.edges}
                                    color="error"
                                />
                            </Grid>

                            {/* Unchanged */}
                            <Grid xs={6}>
                                <DiffStat
                                    icon={<CheckCircle />}
                                    label="Nodes Unchanged"
                                    value={diff.unchanged.nodes}
                                    color="info"
                                />
                            </Grid>
                            <Grid xs={6}>
                                <DiffStat
                                    icon={<CheckCircle />}
                                    label="Edges Unchanged"
                                    value={diff.unchanged.edges}
                                    color="info"
                                />
                            </Grid>
                        </Grid>
                    </Box>

                    {/* Summary Text */}
                    {totalChanges === 0 && (
                        <Paper elevation={0} className="p-4 bg-sky-50" >
                            <Typography variant="body2" color="info.main">
                                No differences found between these versions.
                            </Typography>
                        </Paper>
                    )}
                </Stack>
            </DialogContent>

            <DialogActions>
                {onRestoreVersion && (
                    <>
                        <Button
                            onClick={() => onRestoreVersion(snapshot1.id)}
                            variant="outlined"
                        >
                            Restore v{snapshot1.version}
                        </Button>
                        <Button
                            onClick={() => onRestoreVersion(snapshot2.id)}
                            variant="outlined"
                        >
                            Restore v{snapshot2.version}
                        </Button>
                    </>
                )}
                <Box className="flex-1" />
                <Button onClick={onClose}>Close</Button>
            </DialogActions>
        </Dialog>
    );
};

export default VersionDiffViewer;
