import React, { useState } from 'react';
import {
    Box,
    Card,
    Button,
    Stack,
    Typography,
    TextField,
    Select,
    MenuItem,
    Chip,
    IconButton,
    Badge,
    Grid,
    InputAdornment,
    FormControl,
    InputLabel,
} from '@ghatana/ui';
import { IncidentDetailPanel } from './IncidentDetailPanel';

/**
 * Incident priority levels
 */
export type IncidentPriority = 'critical' | 'high' | 'medium' | 'low';

/**
 * Incident status
 */
export type IncidentStatus = 'open' | 'investigating' | 'resolved' | 'closed';

/**
 * Incident category
 */
export type IncidentCategory =
    | 'security'
    | 'performance'
    | 'availability'
    | 'data-integrity'
    | 'compliance'
    | 'other';

/**
 * Incident data structure
 */
export interface Incident {
    id: string;
    title: string;
    description: string;
    priority: IncidentPriority;
    status: IncidentStatus;
    category: IncidentCategory;
    assignedTo?: string;
    reportedBy: string;
    reportedAt: string;
    updatedAt: string;
    resolvedAt?: string;
    affectedUsers?: number;
    affectedResources?: string[];
    tags: string[];
    timeline?: IncidentTimelineEvent[];
    comments?: IncidentComment[];
    metadata?: Record<string, any>;
}

/**
 * Timeline event
 */
export interface IncidentTimelineEvent {
    id: string;
    timestamp: string;
    user: string;
    action: string;
    details?: string;
}

/**
 * Incident comment
 */
export interface IncidentComment {
    id: string;
    user: string;
    timestamp: string;
    content: string;
}

/**
 * Props for IncidentDashboard
 */
interface IncidentDashboardProps {
    incidents?: Incident[];
    onCreateIncident?: () => void;
    onUpdateIncident?: (incident: Incident) => void;
    onAssignIncident?: (incidentId: string, userId: string) => void;
    onResolveIncident?: (incidentId: string, resolution: string) => void;
    onAddComment?: (incidentId: string, comment: string) => void;
    availableUsers?: Array<{ id: string; name: string }>;
}

/**
 * Incident Management Dashboard
 *
 * Provides a comprehensive view of all incidents with:
 * - Active incidents list with priority and status
 * - Filtering and search
 * - Quick actions (assign, update status, resolve)
 * - Detail panel for deep investigation
 * - Statistics overview
 */
export const IncidentDashboard: React.FC<IncidentDashboardProps> = ({
    incidents: initialIncidents,
    onCreateIncident,
    onUpdateIncident,
    onAssignIncident,
    onResolveIncident,
    onAddComment,
    availableUsers = [],
}) => {
    // Mock data if none provided
    const mockIncidents: Incident[] = [
        {
            id: 'INC-001',
            title: 'Unauthorized Access Attempt Detected',
            description: 'Multiple failed login attempts from suspicious IP address',
            priority: 'critical',
            status: 'investigating',
            category: 'security',
            assignedTo: 'admin-001',
            reportedBy: 'system',
            reportedAt: new Date(Date.now() - 2 * 3600000).toISOString(),
            updatedAt: new Date(Date.now() - 1800000).toISOString(),
            affectedUsers: 1,
            affectedResources: ['auth-service', 'user-db'],
            tags: ['security', 'auth', 'urgent'],
            timeline: [
                {
                    id: 't1',
                    timestamp: new Date(Date.now() - 2 * 3600000).toISOString(),
                    user: 'system',
                    action: 'Incident Created',
                    details: 'Automated detection triggered',
                },
                {
                    id: 't2',
                    timestamp: new Date(Date.now() - 1800000).toISOString(),
                    user: 'admin-001',
                    action: 'Status Changed',
                    details: 'Changed from Open to Investigating',
                },
            ],
            comments: [
                {
                    id: 'c1',
                    user: 'admin-001',
                    timestamp: new Date(Date.now() - 1800000).toISOString(),
                    content: 'Investigating the source IP. Appears to be from known malicious range.',
                },
            ],
        },
        {
            id: 'INC-002',
            title: 'Database Performance Degradation',
            description: 'Query response times increased by 300% over baseline',
            priority: 'high',
            status: 'open',
            category: 'performance',
            reportedBy: 'monitoring',
            reportedAt: new Date(Date.now() - 3600000).toISOString(),
            updatedAt: new Date(Date.now() - 3600000).toISOString(),
            affectedUsers: 150,
            affectedResources: ['postgres-primary', 'api-gateway'],
            tags: ['performance', 'database'],
            timeline: [
                {
                    id: 't3',
                    timestamp: new Date(Date.now() - 3600000).toISOString(),
                    user: 'monitoring',
                    action: 'Incident Created',
                    details: 'Alert threshold exceeded',
                },
            ],
        },
        {
            id: 'INC-003',
            title: 'SSO Service Outage',
            description: 'Users unable to authenticate via Okta',
            priority: 'high',
            status: 'resolved',
            category: 'availability',
            assignedTo: 'admin-002',
            reportedBy: 'user-support',
            reportedAt: new Date(Date.now() - 7200000).toISOString(),
            updatedAt: new Date(Date.now() - 1200000).toISOString(),
            resolvedAt: new Date(Date.now() - 1200000).toISOString(),
            affectedUsers: 500,
            affectedResources: ['okta-sso', 'auth-service'],
            tags: ['sso', 'auth', 'outage'],
            timeline: [
                {
                    id: 't4',
                    timestamp: new Date(Date.now() - 7200000).toISOString(),
                    user: 'user-support',
                    action: 'Incident Created',
                },
                {
                    id: 't5',
                    timestamp: new Date(Date.now() - 5400000).toISOString(),
                    user: 'admin-002',
                    action: 'Status Changed',
                    details: 'Investigating Okta service status',
                },
                {
                    id: 't6',
                    timestamp: new Date(Date.now() - 1200000).toISOString(),
                    user: 'admin-002',
                    action: 'Incident Resolved',
                    details: 'Okta service restored. Certificate renewal completed.',
                },
            ],
        },
        {
            id: 'INC-004',
            title: 'Data Sync Failure',
            description: 'Tenant data not replicating to backup region',
            priority: 'medium',
            status: 'open',
            category: 'data-integrity',
            reportedBy: 'data-team',
            reportedAt: new Date(Date.now() - 5400000).toISOString(),
            updatedAt: new Date(Date.now() - 5400000).toISOString(),
            affectedResources: ['replication-service', 'backup-db'],
            tags: ['data', 'replication'],
            timeline: [
                {
                    id: 't7',
                    timestamp: new Date(Date.now() - 5400000).toISOString(),
                    user: 'data-team',
                    action: 'Incident Created',
                },
            ],
        },
    ];

    const [incidents] = useState<Incident[]>(initialIncidents || mockIncidents);
    const [selectedIncident, setSelectedIncident] = useState<Incident | null>(null);
    const [searchTerm, setSearchTerm] = useState('');
    const [filterStatus, setFilterStatus] = useState<IncidentStatus | 'all'>('all');
    const [filterPriority, setFilterPriority] = useState<IncidentPriority | 'all'>('all');
    const [filterCategory, setFilterCategory] = useState<IncidentCategory | 'all'>('all');

    // Filter incidents
    const filteredIncidents = incidents.filter((incident) => {
        const matchesSearch =
            searchTerm === '' ||
            incident.title.toLowerCase().includes(searchTerm.toLowerCase()) ||
            incident.id.toLowerCase().includes(searchTerm.toLowerCase()) ||
            incident.tags.some((tag) => tag.toLowerCase().includes(searchTerm.toLowerCase()));

        const matchesStatus = filterStatus === 'all' || incident.status === filterStatus;
        const matchesPriority = filterPriority === 'all' || incident.priority === filterPriority;
        const matchesCategory = filterCategory === 'all' || incident.category === filterCategory;

        return matchesSearch && matchesStatus && matchesPriority && matchesCategory;
    });

    // Statistics
    const stats = {
        total: incidents.length,
        critical: incidents.filter((i) => i.priority === 'critical').length,
        open: incidents.filter((i) => i.status === 'open' || i.status === 'investigating').length,
        resolved: incidents.filter((i) => i.status === 'resolved').length,
        avgResolutionTime: '4.2 hours', // Mock
    };

    // Priority color
    const getPriorityColor = (priority: IncidentPriority): string => {
        switch (priority) {
            case 'critical':
                return 'error';
            case 'high':
                return 'warning';
            case 'medium':
                return 'info';
            case 'low':
                return 'success';
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

    return (
        <Box>
            <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 3 }}>
                <Typography variant="h4">Incident Management</Typography>
                <Button variant="contained" onClick={onCreateIncident}>
                    Create Incident
                </Button>
            </Stack>

            {/* Statistics Cards */}
            <Grid container spacing={2} sx={{ mb: 3 }}>
                <Grid item xs={12} sm={6} md={2.4}>
                    <Card sx={{ p: 2 }}>
                        <Typography variant="body2" color="text.secondary" gutterBottom>
                            Total Incidents
                        </Typography>
                        <Typography variant="h4">{stats.total}</Typography>
                    </Card>
                </Grid>
                <Grid item xs={12} sm={6} md={2.4}>
                    <Card sx={{ p: 2 }}>
                        <Typography variant="body2" color="text.secondary" gutterBottom>
                            Critical
                        </Typography>
                        <Typography variant="h4" color="error.main">
                            {stats.critical}
                        </Typography>
                    </Card>
                </Grid>
                <Grid item xs={12} sm={6} md={2.4}>
                    <Card sx={{ p: 2 }}>
                        <Typography variant="body2" color="text.secondary" gutterBottom>
                            Active
                        </Typography>
                        <Typography variant="h4" color="warning.main">
                            {stats.open}
                        </Typography>
                    </Card>
                </Grid>
                <Grid item xs={12} sm={6} md={2.4}>
                    <Card sx={{ p: 2 }}>
                        <Typography variant="body2" color="text.secondary" gutterBottom>
                            Resolved (24h)
                        </Typography>
                        <Typography variant="h4" color="success.main">
                            {stats.resolved}
                        </Typography>
                    </Card>
                </Grid>
                <Grid item xs={12} sm={6} md={2.4}>
                    <Card sx={{ p: 2 }}>
                        <Typography variant="body2" color="text.secondary" gutterBottom>
                            Avg Resolution
                        </Typography>
                        <Typography variant="h4">{stats.avgResolutionTime}</Typography>
                    </Card>
                </Grid>
            </Grid>

            {/* Filters */}
            <Card sx={{ p: 2, mb: 3 }}>
                <Grid container spacing={2} alignItems="center">
                    <Grid item xs={12} md={4}>
                        <TextField
                            fullWidth
                            size="small"
                            placeholder="Search incidents..."
                            value={searchTerm}
                            onChange={(e) => setSearchTerm(e.target.value)}
                            InputProps={{
                                startAdornment: <InputAdornment position="start">🔍</InputAdornment>,
                            }}
                        />
                    </Grid>
                    <Grid item xs={12} sm={4} md={2}>
                        <FormControl fullWidth size="small">
                            <InputLabel>Status</InputLabel>
                            <Select
                                value={filterStatus}
                                label="Status"
                                onChange={(e) => setFilterStatus(e.target.value as any)}
                            >
                                <MenuItem value="all">All</MenuItem>
                                <MenuItem value="open">Open</MenuItem>
                                <MenuItem value="investigating">Investigating</MenuItem>
                                <MenuItem value="resolved">Resolved</MenuItem>
                                <MenuItem value="closed">Closed</MenuItem>
                            </Select>
                        </FormControl>
                    </Grid>
                    <Grid item xs={12} sm={4} md={2}>
                        <FormControl fullWidth size="small">
                            <InputLabel>Priority</InputLabel>
                            <Select
                                value={filterPriority}
                                label="Priority"
                                onChange={(e) => setFilterPriority(e.target.value as any)}
                            >
                                <MenuItem value="all">All</MenuItem>
                                <MenuItem value="critical">Critical</MenuItem>
                                <MenuItem value="high">High</MenuItem>
                                <MenuItem value="medium">Medium</MenuItem>
                                <MenuItem value="low">Low</MenuItem>
                            </Select>
                        </FormControl>
                    </Grid>
                    <Grid item xs={12} sm={4} md={2}>
                        <FormControl fullWidth size="small">
                            <InputLabel>Category</InputLabel>
                            <Select
                                value={filterCategory}
                                label="Category"
                                onChange={(e) => setFilterCategory(e.target.value as any)}
                            >
                                <MenuItem value="all">All</MenuItem>
                                <MenuItem value="security">Security</MenuItem>
                                <MenuItem value="performance">Performance</MenuItem>
                                <MenuItem value="availability">Availability</MenuItem>
                                <MenuItem value="data-integrity">Data Integrity</MenuItem>
                                <MenuItem value="compliance">Compliance</MenuItem>
                                <MenuItem value="other">Other</MenuItem>
                            </Select>
                        </FormControl>
                    </Grid>
                    <Grid item xs={12} sm={4} md={2}>
                        <Typography variant="body2" color="text.secondary">
                            {filteredIncidents.length} incident(s)
                        </Typography>
                    </Grid>
                </Grid>
            </Card>

            {/* Incidents List */}
            <Grid container spacing={3}>
                <Grid item xs={12} md={selectedIncident ? 6 : 12}>
                    <Stack spacing={2}>
                        {filteredIncidents.length === 0 ? (
                            <Card sx={{ p: 4, textAlign: 'center' }}>
                                <Typography variant="body1" color="text.secondary">
                                    No incidents found matching your filters
                                </Typography>
                            </Card>
                        ) : (
                            filteredIncidents.map((incident) => (
                                <Card
                                    key={incident.id}
                                    sx={{
                                        p: 2,
                                        cursor: 'pointer',
                                        borderLeft: 4,
                                        borderColor:
                                            incident.priority === 'critical'
                                                ? 'error.main'
                                                : incident.priority === 'high'
                                                    ? 'warning.main'
                                                    : 'divider',
                                        bgcolor:
                                            selectedIncident?.id === incident.id ? 'action.selected' : 'transparent',
                                        '&:hover': {
                                            bgcolor: 'action.hover',
                                        },
                                    }}
                                    onClick={() => setSelectedIncident(incident)}
                                >
                                    <Stack spacing={1}>
                                        <Stack direction="row" justifyContent="space-between" alignItems="start">
                                            <Box sx={{ flex: 1 }}>
                                                <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 0.5 }}>
                                                    <Typography variant="subtitle2">{incident.id}</Typography>
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
                                                <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                                                    {incident.description}
                                                </Typography>
                                            </Box>
                                        </Stack>

                                        <Stack direction="row" spacing={1} flexWrap="wrap">
                                            <Chip label={incident.category} size="small" variant="outlined" />
                                            {incident.tags.map((tag) => (
                                                <Chip key={tag} label={tag} size="small" />
                                            ))}
                                        </Stack>

                                        <Stack
                                            direction="row"
                                            justifyContent="space-between"
                                            alignItems="center"
                                            sx={{ pt: 1, borderTop: 1, borderColor: 'divider' }}
                                        >
                                            <Stack direction="row" spacing={2}>
                                                <Typography variant="caption" color="text.secondary">
                                                    Reported {formatTimeAgo(incident.reportedAt)}
                                                </Typography>
                                                {incident.assignedTo && (
                                                    <Typography variant="caption" color="text.secondary">
                                                        Assigned to {incident.assignedTo}
                                                    </Typography>
                                                )}
                                                {incident.affectedUsers && (
                                                    <Typography variant="caption" color="text.secondary">
                                                        {incident.affectedUsers} users affected
                                                    </Typography>
                                                )}
                                            </Stack>
                                            {incident.comments && incident.comments.length > 0 && (
                                                <Badge badgeContent={incident.comments.length} color="primary">
                                                    <Typography variant="caption">💬</Typography>
                                                </Badge>
                                            )}
                                        </Stack>
                                    </Stack>
                                </Card>
                            ))
                        )}
                    </Stack>
                </Grid>

                {/* Detail Panel */}
                {selectedIncident && (
                    <Grid item xs={12} md={6}>
                        <IncidentDetailPanel
                            incident={selectedIncident}
                            onClose={() => setSelectedIncident(null)}
                            onUpdateStatus={(status) => {
                                const updated = { ...selectedIncident, status, updatedAt: new Date().toISOString() };
                                onUpdateIncident?.(updated);
                                setSelectedIncident(updated);
                            }}
                            onAssign={(userId) => {
                                onAssignIncident?.(selectedIncident.id, userId);
                                setSelectedIncident({ ...selectedIncident, assignedTo: userId });
                            }}
                            onResolve={(resolution) => {
                                onResolveIncident?.(selectedIncident.id, resolution);
                                setSelectedIncident({
                                    ...selectedIncident,
                                    status: 'resolved',
                                    resolvedAt: new Date().toISOString(),
                                });
                            }}
                            onAddComment={(comment) => {
                                onAddComment?.(selectedIncident.id, comment);
                                const newComment: IncidentComment = {
                                    id: `c${Date.now()}`,
                                    user: 'current-user',
                                    timestamp: new Date().toISOString(),
                                    content: comment,
                                };
                                setSelectedIncident({
                                    ...selectedIncident,
                                    comments: [...(selectedIncident.comments || []), newComment],
                                });
                            }}
                            availableUsers={availableUsers}
                        />
                    </Grid>
                )}
            </Grid>
        </Box>
    );
};
