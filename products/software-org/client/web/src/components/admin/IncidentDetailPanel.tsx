import React, { useState } from 'react';
import {
    Box,
    Card,
    Button,
    Stack,
    Typography,
    TextField,
    Select,
    Chip,
    IconButton,
    Divider,
    FormControl,
    Grid,
    InputLabel,
    Tab,
    Tabs,
} from '@ghatana/design-system';
import type { Incident, IncidentStatus, IncidentComment, IncidentTimelineEvent } from './IncidentDashboard';

/**
 * Props for IncidentDetailPanel
 */
interface IncidentDetailPanelProps {
    incident: Incident;
    onClose?: () => void;
    onUpdateStatus?: (status: IncidentStatus) => void;
    onAssign?: (userId: string) => void;
    onResolve?: (resolution: string) => void;
    onAddComment?: (comment: string) => void;
    availableUsers?: Array<{ id: string; name: string }>;
}

/**
 * Incident Detail Panel
 *
 * Provides detailed view and management of a single incident:
 * - Timeline of all actions
 * - Comment thread
 * - Status updates
 * - Assignment workflow
 * - Resolution actions
 */
export const IncidentDetailPanel: React.FC<IncidentDetailPanelProps> = ({
    incident,
    onClose,
    onUpdateStatus,
    onAssign,
    onResolve,
    onAddComment,
    availableUsers = [
        { id: 'admin-001', name: 'Alice Admin' },
        { id: 'admin-002', name: 'Bob Manager' },
        { id: 'admin-003', name: 'Carol Lead' },
    ],
}) => {
    const [activeTab, setActiveTab] = useState<'timeline' | 'comments'>('timeline');
    const [newComment, setNewComment] = useState('');
    const [resolutionNote, setResolutionNote] = useState('');

    // Format timestamp
    const formatTimestamp = (timestamp: string): string => {
        const date = new Date(timestamp);
        return date.toLocaleString('en-US', {
            month: 'short',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit',
        });
    };

    // Format time ago
    const formatTimeAgo = (timestamp: string): string => {
        const seconds = Math.floor((Date.now() - new Date(timestamp).getTime()) / 1000);
        if (seconds < 60) return `${seconds}s ago`;
        const minutes = Math.floor(seconds / 60);
        if (minutes < 60) return `${minutes}m ago`;
        const hours = Math.floor(minutes / 60);
        if (hours < 24) return `${hours}h ago`;
        const days = Math.floor(hours / 24);
        return `${days}d ago`;
    };

    // Priority color
    const getPriorityColor = (priority: string): string => {
        switch (priority) {
            case 'critical':
                return 'error';
            case 'high':
                return 'warning';
            case 'medium':
                return 'info';
            case 'low':
                return 'success';
            default:
                return 'default';
        }
    };

    // Status color
    const getStatusColor = (status: IncidentStatus): string => {
        switch (status) {
            case 'open':
                return 'error';
            case 'investigating':
                return 'warning';
            case 'resolved':
                return 'success';
            case 'closed':
                return 'default';
        }
    };

    // Handle add comment
    const handleAddComment = () => {
        if (newComment.trim()) {
            onAddComment?.(newComment);
            setNewComment('');
        }
    };

    // Handle resolve
    const handleResolve = () => {
        if (resolutionNote.trim()) {
            onResolve?.(resolutionNote);
            setResolutionNote('');
        }
    };

    return (
        <Card sx={{ position: 'sticky', top: 16, maxHeight: 'calc(100vh - 32px)', overflow: 'auto' }}>
            {/* Header */}
            <Box sx={{ p: 2, borderBottom: 1, borderColor: 'divider' }}>
                <Stack direction="row" justifyContent="space-between" alignItems="start">
                    <Box>
                        <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 1 }}>
                            <Typography variant="h6">{incident.id}</Typography>
                            <Chip
                                label={incident.priority}
                                size="small"
                                color={getPriorityColor(incident.priority) as any}
                            />
                            <Chip
                                label={incident.status}
                                size="small"
                                color={getStatusColor(incident.status) as any}
                                variant="outlined"
                            />
                        </Stack>
                        <Typography variant="body1" fontWeight="medium">
                            {incident.title}
                        </Typography>
                    </Box>
                    <IconButton size="small" onClick={onClose} aria-label="Close incident">
                        ✕
                    </IconButton>
                </Stack>
            </Box>

            {/* Description */}
            <Box sx={{ p: 2 }}>
                <Typography variant="body2" color="text.secondary" gutterBottom>
                    Description
                </Typography>
                <Typography variant="body1">{incident.description}</Typography>
            </Box>

            <Divider />

            {/* Metadata */}
            <Box sx={{ p: 2 }}>
                <Grid container spacing={2}>
                    <Grid item xs={6}>
                        <Typography variant="caption" color="text.secondary">
                            Category
                        </Typography>
                        <Typography variant="body2">{incident.category}</Typography>
                    </Grid>
                    <Grid item xs={6}>
                        <Typography variant="caption" color="text.secondary">
                            Reported By
                        </Typography>
                        <Typography variant="body2">{incident.reportedBy}</Typography>
                    </Grid>
                    <Grid item xs={6}>
                        <Typography variant="caption" color="text.secondary">
                            Reported At
                        </Typography>
                        <Typography variant="body2">{formatTimestamp(incident.reportedAt)}</Typography>
                    </Grid>
                    <Grid item xs={6}>
                        <Typography variant="caption" color="text.secondary">
                            Last Updated
                        </Typography>
                        <Typography variant="body2">{formatTimeAgo(incident.updatedAt)}</Typography>
                    </Grid>
                    {incident.affectedUsers && (
                        <Grid item xs={6}>
                            <Typography variant="caption" color="text.secondary">
                                Affected Users
                            </Typography>
                            <Typography variant="body2">{incident.affectedUsers}</Typography>
                        </Grid>
                    )}
                    {incident.resolvedAt && (
                        <Grid item xs={6}>
                            <Typography variant="caption" color="text.secondary">
                                Resolved At
                            </Typography>
                            <Typography variant="body2">{formatTimestamp(incident.resolvedAt)}</Typography>
                        </Grid>
                    )}
                </Grid>
            </Box>

            {/* Affected Resources */}
            {incident.affectedResources && incident.affectedResources.length > 0 && (
                <>
                    <Divider />
                    <Box sx={{ p: 2 }}>
                        <Typography variant="caption" color="text.secondary" gutterBottom>
                            Affected Resources
                        </Typography>
                        <Stack direction="row" spacing={1} flexWrap="wrap" sx={{ mt: 1 }}>
                            {incident.affectedResources.map((resource) => (
                                <Chip key={resource} label={resource} size="small" variant="outlined" />
                            ))}
                        </Stack>
                    </Box>
                </>
            )}

            {/* Tags */}
            {incident.tags && incident.tags.length > 0 && (
                <>
                    <Divider />
                    <Box sx={{ p: 2 }}>
                        <Typography variant="caption" color="text.secondary" gutterBottom>
                            Tags
                        </Typography>
                        <Stack direction="row" spacing={1} flexWrap="wrap" sx={{ mt: 1 }}>
                            {incident.tags.map((tag) => (
                                <Chip key={tag} label={tag} size="small" />
                            ))}
                        </Stack>
                    </Box>
                </>
            )}

            <Divider />

            {/* Actions */}
            <Box sx={{ p: 2 }}>
                <Typography variant="caption" color="text.secondary" gutterBottom>
                    Actions
                </Typography>
                <Stack spacing={2} sx={{ mt: 1 }}>
                    {/* Status Update */}
                    <FormControl fullWidth size="small">
                        <InputLabel>Status</InputLabel>
                        <Select
                            value={incident.status}
                            label="Status"
                            onChange={(e) => onUpdateStatus?.(e.target.value as IncidentStatus)}
                        >
                            <option value="open">Open</option>
                            <option value="investigating">Investigating</option>
                            <option value="resolved">Resolved</option>
                            <option value="closed">Closed</option>
                        </Select>
                    </FormControl>

                    {/* Assignment */}
                    <FormControl fullWidth size="small">
                        <InputLabel>Assign To</InputLabel>
                        <Select
                            value={incident.assignedTo || ''}
                            label="Assign To"
                            onChange={(e) => onAssign?.(e.target.value)}
                        >
                            <option value="">Unassigned</option>
                            {availableUsers.map((user) => (
                                <option key={user.id} value={user.id}>
                                    {user.name}
                                </option>
                            ))}
                        </Select>
                    </FormControl>

                    {/* Resolve (if not already resolved) */}
                    {incident.status !== 'resolved' && incident.status !== 'closed' && (
                        <Box>
                            <TextField
                                fullWidth
                                size="small"
                                multiline
                                rows={2}
                                placeholder="Resolution notes..."
                                value={resolutionNote}
                                onChange={(e) => setResolutionNote(e.target.value)}
                            />
                            <Button
                                fullWidth
                                variant="contained"
                                color="success"
                                sx={{ mt: 1 }}
                                onClick={handleResolve}
                                disabled={!resolutionNote.trim()}
                            >
                                Mark as Resolved
                            </Button>
                        </Box>
                    )}
                </Stack>
            </Box>

            <Divider />

            {/* Timeline / Comments Tabs */}
            <Box>
                <Tabs value={activeTab} onChange={(_, val) => setActiveTab(val)}>
                    <Tab label="Timeline" value="timeline" />
                    <Tab
                        label={`Comments${incident.comments && incident.comments.length > 0
                                ? ` (${incident.comments.length})`
                                : ''
                            }`}
                        value="comments"
                    />
                </Tabs>

                {/* Timeline Tab */}
                {activeTab === 'timeline' && (
                    <Box sx={{ p: 2, maxHeight: 400, overflow: 'auto' }}>
                        {incident.timeline && incident.timeline.length > 0 ? (
                            <Stack spacing={2}>
                                {incident.timeline.map((event, index) => (
                                    <Box key={event.id}>
                                        <Stack direction="row" spacing={2}>
                                            <Box
                                                sx={{
                                                    width: 8,
                                                    height: 8,
                                                    borderRadius: '50%',
                                                    bgcolor: 'primary.main',
                                                    mt: 0.5,
                                                    flexShrink: 0,
                                                }}
                                            />
                                            <Box sx={{ flex: 1 }}>
                                                <Stack direction="row" justifyContent="space-between" alignItems="start">
                                                    <Typography variant="body2" fontWeight="medium">
                                                        {event.action}
                                                    </Typography>
                                                    <Typography variant="caption" color="text.secondary">
                                                        {formatTimestamp(event.timestamp)}
                                                    </Typography>
                                                </Stack>
                                                <Typography variant="caption" color="text.secondary">
                                                    {event.user}
                                                </Typography>
                                                {event.details && (
                                                    <Typography variant="body2" sx={{ mt: 0.5 }}>
                                                        {event.details}
                                                    </Typography>
                                                )}
                                            </Box>
                                        </Stack>
                                        {index < incident.timeline!.length - 1 && (
                                            <Box
                                                sx={{
                                                    ml: '3px',
                                                    width: 2,
                                                    height: 16,
                                                    bgcolor: 'divider',
                                                    my: 0.5,
                                                }}
                                            />
                                        )}
                                    </Box>
                                ))}
                            </Stack>
                        ) : (
                            <Typography variant="body2" color="text.secondary">
                                No timeline events
                            </Typography>
                        )}
                    </Box>
                )}

                {/* Comments Tab */}
                {activeTab === 'comments' && (
                    <Box sx={{ p: 2 }}>
                        {/* Comment Input */}
                        <Stack spacing={1} sx={{ mb: 2 }}>
                            <TextField
                                fullWidth
                                size="small"
                                multiline
                                rows={3}
                                placeholder="Add a comment..."
                                value={newComment}
                                onChange={(e) => setNewComment(e.target.value)}
                            />
                            <Button
                                variant="contained"
                                size="small"
                                onClick={handleAddComment}
                                disabled={!newComment.trim()}
                            >
                                Add Comment
                            </Button>
                        </Stack>

                        <Divider sx={{ my: 2 }} />

                        {/* Comments List */}
                        <Box sx={{ maxHeight: 300, overflow: 'auto' }}>
                            {incident.comments && incident.comments.length > 0 ? (
                                <Stack spacing={2}>
                                    {incident.comments.map((comment) => (
                                        <Card key={comment.id} variant="outlined" sx={{ p: 1.5 }}>
                                            <Stack direction="row" justifyContent="space-between" sx={{ mb: 0.5 }}>
                                                <Typography variant="caption" fontWeight="medium">
                                                    {comment.user}
                                                </Typography>
                                                <Typography variant="caption" color="text.secondary">
                                                    {formatTimestamp(comment.timestamp)}
                                                </Typography>
                                            </Stack>
                                            <Typography variant="body2">{comment.content}</Typography>
                                        </Card>
                                    ))}
                                </Stack>
                            ) : (
                                <Typography variant="body2" color="text.secondary">
                                    No comments yet
                                </Typography>
                            )}
                        </Box>
                    </Box>
                )}
            </Box>
        </Card>
    );
};
