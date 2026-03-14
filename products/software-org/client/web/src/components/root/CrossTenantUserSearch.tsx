import { useState, useMemo } from 'react';
import {
    Box,
    Card,
    Stack,
    Grid,
    Typography,
    TextField,
    InputAdornment,
    Chip,
    IconButton,
    Button,
    Dialog,
    DialogTitle,
    DialogContent,
    DialogActions,
    Tabs,
    Tab,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Alert,
    Avatar,
} from '@ghatana/design-system';
import {
    Search as SearchIcon,
    Close as CloseIcon,
    Block as BlockIcon,
    CheckCircle as CheckCircleIcon,
    Warning as WarningIcon,
    Person as PersonIcon,
    AdminPanelSettings as AdminIcon,
} from '@ghatana/design-system/icons';

/**
 * User information across tenants
 */
export interface CrossTenantUser {
    id: string;
    email: string;
    name: string;
    avatarUrl?: string;
    status: 'active' | 'suspended' | 'inactive';
    createdAt: string;
    lastLoginAt?: string;
    tenantMemberships: {
        tenantId: string;
        tenantName: string;
        tenantSlug: string;
        roles: string[];
        joinedAt: string;
        lastActivityAt?: string;
    }[];
    securityFlags: {
        multipleFailedLogins?: boolean;
        suspiciousActivity?: boolean;
        compromisedCredentials?: boolean;
    };
    globalStats: {
        totalLogins: number;
        totalApiCalls: number;
        dataAccessed: number; // GB
    };
}

/**
 * User activity log entry
 */
export interface UserActivity {
    id: string;
    userId: string;
    tenantId: string;
    tenantName: string;
    action: string;
    resource?: string;
    ipAddress: string;
    location?: string;
    userAgent: string;
    timestamp: string;
    status: 'success' | 'failed' | 'blocked';
}

/**
 * Security event information
 */
export interface SecurityEvent {
    id: string;
    userId: string;
    tenantId: string;
    eventType: 'failed_login' | 'suspicious_activity' | 'privilege_escalation' | 'data_export' | 'account_compromise';
    severity: 'low' | 'medium' | 'high' | 'critical';
    description: string;
    ipAddress: string;
    timestamp: string;
    resolved: boolean;
}

export interface CrossTenantUserSearchProps {
    users?: CrossTenantUser[];
    onUserSelect?: (userId: string) => void;
    onSuspendUser?: (userId: string, reason: string) => void;
    onUnsuspendUser?: (userId: string) => void;
    onFetchActivities?: (userId: string) => Promise<UserActivity[]>;
    onFetchSecurityEvents?: (userId: string) => Promise<SecurityEvent[]>;
}

/**
 * Cross-Tenant User Management Component
 *
 * Provides platform administrators with:
 * - Global user search across all tenants
 * - User activity review and comparison
 * - Security event monitoring
 * - Account suspension/reactivation
 * - Cross-tenant permission analysis
 */
export function CrossTenantUserSearch({
    users: propUsers,
    onUserSelect,
    onSuspendUser,
    onUnsuspendUser,
    onFetchActivities,
    onFetchSecurityEvents,
}: CrossTenantUserSearchProps) {
    // Mock data for development
    const mockUsers: CrossTenantUser[] = [
        {
            id: 'user-1',
            email: 'john.smith@acme.com',
            name: 'John Smith',
            status: 'active',
            createdAt: '2024-01-15T10:00:00Z',
            lastLoginAt: '2025-12-11T11:30:00Z',
            tenantMemberships: [
                {
                    tenantId: 'tenant-1',
                    tenantName: 'Acme Corporation',
                    tenantSlug: 'acme-corp',
                    roles: ['Owner', 'Admin'],
                    joinedAt: '2024-01-15T10:00:00Z',
                    lastActivityAt: '2025-12-11T11:30:00Z',
                },
            ],
            securityFlags: {},
            globalStats: {
                totalLogins: 450,
                totalApiCalls: 125000,
                dataAccessed: 85,
            },
        },
        {
            id: 'user-2',
            email: 'sarah.johnson@techstart.io',
            name: 'Sarah Johnson',
            status: 'active',
            createdAt: '2024-06-20T14:00:00Z',
            lastLoginAt: '2025-12-11T10:15:00Z',
            tenantMemberships: [
                {
                    tenantId: 'tenant-2',
                    tenantName: 'TechStart Inc',
                    tenantSlug: 'techstart',
                    roles: ['Owner'],
                    joinedAt: '2024-06-20T14:00:00Z',
                    lastActivityAt: '2025-12-11T10:15:00Z',
                },
            ],
            securityFlags: {},
            globalStats: {
                totalLogins: 220,
                totalApiCalls: 18000,
                dataAccessed: 12,
            },
        },
        {
            id: 'user-3',
            email: 'alex.suspicious@example.com',
            name: 'Alex Suspicious',
            status: 'active',
            createdAt: '2025-11-15T08:00:00Z',
            lastLoginAt: '2025-12-11T09:45:00Z',
            tenantMemberships: [
                {
                    tenantId: 'tenant-1',
                    tenantName: 'Acme Corporation',
                    tenantSlug: 'acme-corp',
                    roles: ['IC'],
                    joinedAt: '2025-11-15T08:00:00Z',
                    lastActivityAt: '2025-12-11T09:45:00Z',
                },
                {
                    tenantId: 'tenant-2',
                    tenantName: 'TechStart Inc',
                    tenantSlug: 'techstart',
                    roles: ['IC'],
                    joinedAt: '2025-11-20T10:00:00Z',
                    lastActivityAt: '2025-12-10T16:30:00Z',
                },
            ],
            securityFlags: {
                multipleFailedLogins: true,
                suspiciousActivity: true,
            },
            globalStats: {
                totalLogins: 85,
                totalApiCalls: 15000,
                dataAccessed: 45,
            },
        },
        {
            id: 'user-4',
            email: 'robert.wilson@legacysystems.com',
            name: 'Robert Wilson',
            status: 'suspended',
            createdAt: '2022-03-15T12:00:00Z',
            lastLoginAt: '2025-11-20T14:30:00Z',
            tenantMemberships: [
                {
                    tenantId: 'tenant-5',
                    tenantName: 'Legacy Systems Co',
                    tenantSlug: 'legacy-systems',
                    roles: ['Owner', 'Admin'],
                    joinedAt: '2022-03-15T12:00:00Z',
                    lastActivityAt: '2025-11-20T14:30:00Z',
                },
            ],
            securityFlags: {
                compromisedCredentials: true,
            },
            globalStats: {
                totalLogins: 890,
                totalApiCalls: 250000,
                dataAccessed: 380,
            },
        },
    ];

    const mockActivities: UserActivity[] = [
        {
            id: 'activity-1',
            userId: 'user-3',
            tenantId: 'tenant-1',
            tenantName: 'Acme Corporation',
            action: 'Login',
            ipAddress: '192.168.1.100',
            location: 'New York, US',
            userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)',
            timestamp: '2025-12-11T09:45:00Z',
            status: 'success',
        },
        {
            id: 'activity-2',
            userId: 'user-3',
            tenantId: 'tenant-1',
            tenantName: 'Acme Corporation',
            action: 'Export Data',
            resource: '/api/v1/employees',
            ipAddress: '192.168.1.100',
            location: 'New York, US',
            userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)',
            timestamp: '2025-12-11T09:50:00Z',
            status: 'success',
        },
        {
            id: 'activity-3',
            userId: 'user-3',
            tenantId: 'tenant-2',
            tenantName: 'TechStart Inc',
            action: 'Login',
            ipAddress: '203.0.113.45',
            location: 'London, UK',
            userAgent: 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)',
            timestamp: '2025-12-10T16:30:00Z',
            status: 'success',
        },
        {
            id: 'activity-4',
            userId: 'user-3',
            tenantId: 'tenant-1',
            tenantName: 'Acme Corporation',
            action: 'Login',
            ipAddress: '198.51.100.22',
            location: 'Singapore, SG',
            userAgent: 'curl/7.68.0',
            timestamp: '2025-12-09T03:15:00Z',
            status: 'failed',
        },
    ];

    const mockSecurityEvents: SecurityEvent[] = [
        {
            id: 'event-1',
            userId: 'user-3',
            tenantId: 'tenant-1',
            eventType: 'suspicious_activity',
            severity: 'high',
            description: 'Multiple logins from different geographic locations within short timeframe',
            ipAddress: '198.51.100.22',
            timestamp: '2025-12-09T03:15:00Z',
            resolved: false,
        },
        {
            id: 'event-2',
            userId: 'user-3',
            tenantId: 'tenant-1',
            eventType: 'failed_login',
            severity: 'medium',
            description: 'Multiple failed login attempts detected',
            ipAddress: '198.51.100.22',
            timestamp: '2025-12-09T03:10:00Z',
            resolved: false,
        },
    ];

    const users = propUsers || mockUsers;

    const [searchQuery, setSearchQuery] = useState('');
    const [filterStatus, setFilterStatus] = useState<string>('all');
    const [selectedUser, setSelectedUser] = useState<CrossTenantUser | null>(null);
    const [userTab, setUserTab] = useState(0);
    const [userActivities, setUserActivities] = useState<UserActivity[]>([]);
    const [securityEvents, setSecurityEvents] = useState<SecurityEvent[]>([]);
    const [suspendDialogOpen, setSuspendDialogOpen] = useState(false);
    const [suspendReason, setSuspendReason] = useState('');

    // Filter users
    const filteredUsers = useMemo(() => {
        return users.filter((user) => {
            const matchesSearch =
                user.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
                user.email.toLowerCase().includes(searchQuery.toLowerCase()) ||
                user.tenantMemberships.some((m) =>
                    m.tenantName.toLowerCase().includes(searchQuery.toLowerCase())
                );

            const matchesStatus = filterStatus === 'all' || user.status === filterStatus;

            return matchesSearch && matchesStatus;
        });
    }, [users, searchQuery, filterStatus]);

    const handleUserClick = async (user: CrossTenantUser) => {
        setSelectedUser(user);
        onUserSelect?.(user.id);

        // Fetch activities and security events
        if (onFetchActivities) {
            const activities = await onFetchActivities(user.id);
            setUserActivities(activities);
        } else {
            setUserActivities(mockActivities.filter((a) => a.userId === user.id));
        }

        if (onFetchSecurityEvents) {
            const events = await onFetchSecurityEvents(user.id);
            setSecurityEvents(events);
        } else {
            setSecurityEvents(mockSecurityEvents.filter((e) => e.userId === user.id));
        }
    };

    const handleCloseUserDetail = () => {
        setSelectedUser(null);
        setUserTab(0);
        setUserActivities([]);
        setSecurityEvents([]);
    };

    const handleOpenSuspendDialog = () => {
        setSuspendDialogOpen(true);
    };

    const handleSuspendUser = () => {
        if (selectedUser && onSuspendUser && suspendReason.trim()) {
            onSuspendUser(selectedUser.id, suspendReason);
            setSuspendDialogOpen(false);
            setSuspendReason('');
            handleCloseUserDetail();
        }
    };

    const handleUnsuspendUser = () => {
        if (selectedUser && onUnsuspendUser) {
            onUnsuspendUser(selectedUser.id);
            handleCloseUserDetail();
        }
    };

    const getStatusColor = (status: string) => {
        switch (status) {
            case 'active':
                return 'success';
            case 'suspended':
                return 'error';
            case 'inactive':
                return 'default';
            default:
                return 'default';
        }
    };

    const getSeverityColor = (severity: string) => {
        switch (severity) {
            case 'critical':
                return 'error';
            case 'high':
                return 'error';
            case 'medium':
                return 'warning';
            case 'low':
                return 'info';
            default:
                return 'default';
        }
    };

    const getActivityStatusColor = (status: string) => {
        switch (status) {
            case 'success':
                return 'success';
            case 'failed':
                return 'error';
            case 'blocked':
                return 'warning';
            default:
                return 'default';
        }
    };

    const formatDateTime = (dateString: string) => {
        const date = new Date(dateString);
        return date.toLocaleString('en-US', {
            month: 'short',
            day: 'numeric',
            year: 'numeric',
            hour: '2-digit',
            minute: '2-digit',
        });
    };

    const formatTimeAgo = (dateString: string) => {
        const date = new Date(dateString);
        const now = new Date();
        const diff = now.getTime() - date.getTime();
        const minutes = Math.floor(diff / 60000);
        const hours = Math.floor(minutes / 60);
        const days = Math.floor(hours / 24);

        if (minutes < 60) return `${minutes}m ago`;
        if (hours < 24) return `${hours}h ago`;
        return `${days}d ago`;
    };

    const hasSecurityFlags = (user: CrossTenantUser) => {
        return (
            user.securityFlags.multipleFailedLogins ||
            user.securityFlags.suspiciousActivity ||
            user.securityFlags.compromisedCredentials
        );
    };

    return (
        <Box sx={{ p: 3 }}>
            {/* Header */}
            <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 3 }}>
                <Box>
                    <Typography variant="h4" sx={{ fontWeight: 600 }}>
                        Cross-Tenant User Management
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                        Global user search and security monitoring
                    </Typography>
                </Box>
                <Stack direction="row" spacing={2}>
                    <Chip
                        icon={<WarningIcon />}
                        label={`${users.filter((u) => hasSecurityFlags(u)).length} Security Flags`}
                        color={users.filter((u) => hasSecurityFlags(u)).length > 0 ? 'error' : 'default'}
                    />
                    <Chip
                        icon={<BlockIcon />}
                        label={`${users.filter((u) => u.status === 'suspended').length} Suspended`}
                        color="error"
                        variant="outlined"
                    />
                </Stack>
            </Stack>

            {/* Search and Filters */}
            <Card sx={{ mb: 3 }}>
                <Box sx={{ p: 2 }}>
                    <Grid container spacing={2} alignItems="center">
                        <Grid item xs={12} md={8}>
                            <TextField
                                fullWidth
                                placeholder="Search by name, email, or tenant..."
                                value={searchQuery}
                                onChange={(e) => setSearchQuery(e.target.value)}
                                InputProps={{
                                    startAdornment: (
                                        <InputAdornment position="start">
                                            <SearchIcon />
                                        </InputAdornment>
                                    ),
                                }}
                            />
                        </Grid>
                        <Grid item xs={12} md={4}>
                            <TextField
                                select
                                fullWidth
                                label="Status"
                                value={filterStatus}
                                onChange={(e) => setFilterStatus(e.target.value)}
                                SelectProps={{
                                    native: true,
                                }}
                            >
                                <option value="all">All Statuses</option>
                                <option value="active">Active</option>
                                <option value="suspended">Suspended</option>
                                <option value="inactive">Inactive</option>
                            </TextField>
                        </Grid>
                    </Grid>
                </Box>
            </Card>

            {/* User List */}
            <Card>
                <Box sx={{ p: 2, borderBottom: 1, borderColor: 'divider' }}>
                    <Typography variant="h6">Users ({filteredUsers.length})</Typography>
                </Box>
                <Stack divider={<Box sx={{ borderBottom: 1, borderColor: 'divider' }} />}>
                    {filteredUsers.map((user) => (
                        <Box
                            key={user.id}
                            sx={{
                                p: 2,
                                cursor: 'pointer',
                                '&:hover': { bgcolor: 'action.hover' },
                            }}
                            onClick={() => handleUserClick(user)}
                        >
                            <Grid container spacing={2} alignItems="center">
                                <Grid item xs={12} md={4}>
                                    <Stack direction="row" alignItems="center" spacing={2}>
                                        <Avatar src={user.avatarUrl} sx={{ width: 48, height: 48 }}>
                                            {user.name.split(' ').map((n) => n[0]).join('')}
                                        </Avatar>
                                        <Box sx={{ flex: 1 }}>
                                            <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
                                                {user.name}
                                            </Typography>
                                            <Typography variant="body2" color="text.secondary">
                                                {user.email}
                                            </Typography>
                                            <Stack direction="row" spacing={1} sx={{ mt: 0.5 }}>
                                                <Chip
                                                    label={user.status.toUpperCase()}
                                                    size="small"
                                                    color={getStatusColor(user.status) as any}
                                                />
                                                {hasSecurityFlags(user) && (
                                                    <Chip
                                                        icon={<WarningIcon />}
                                                        label="SECURITY FLAG"
                                                        size="small"
                                                        color="error"
                                                    />
                                                )}
                                            </Stack>
                                        </Box>
                                    </Stack>
                                </Grid>

                                <Grid item xs={12} sm={6} md={3}>
                                    <Typography variant="caption" color="text.secondary">
                                        Tenant Memberships
                                    </Typography>
                                    <Typography variant="body1">
                                        {user.tenantMemberships.length} organization(s)
                                    </Typography>
                                    <Stack direction="row" spacing={0.5} sx={{ mt: 0.5, flexWrap: 'wrap' }}>
                                        {user.tenantMemberships.slice(0, 2).map((membership) => (
                                            <Chip
                                                key={membership.tenantId}
                                                label={membership.tenantSlug}
                                                size="small"
                                                variant="outlined"
                                            />
                                        ))}
                                        {user.tenantMemberships.length > 2 && (
                                            <Chip label={`+${user.tenantMemberships.length - 2}`} size="small" variant="outlined" />
                                        )}
                                    </Stack>
                                </Grid>

                                <Grid item xs={12} sm={6} md={3}>
                                    <Typography variant="caption" color="text.secondary">
                                        Last Login
                                    </Typography>
                                    <Typography variant="body1">
                                        {user.lastLoginAt ? formatTimeAgo(user.lastLoginAt) : 'Never'}
                                    </Typography>
                                    <Typography variant="caption" color="text.secondary">
                                        Total Logins: {user.globalStats.totalLogins.toLocaleString()}
                                    </Typography>
                                </Grid>

                                <Grid item xs={12} md={2}>
                                    <Stack direction="row" spacing={1} alignItems="center">
                                        {user.tenantMemberships.some((m) => m.roles.includes('Owner') || m.roles.includes('Admin')) && (
                                            <Chip icon={<AdminIcon />} label="Admin" size="small" color="primary" />
                                        )}
                                    </Stack>
                                </Grid>
                            </Grid>
                        </Box>
                    ))}
                </Stack>

                {filteredUsers.length === 0 && (
                    <Box sx={{ p: 4, textAlign: 'center' }}>
                        <Typography variant="body1" color="text.secondary">
                            No users match your search criteria
                        </Typography>
                    </Box>
                )}
            </Card>

            {/* User Detail Dialog */}
            <Dialog open={!!selectedUser} onClose={handleCloseUserDetail} maxWidth="lg" fullWidth>
                {selectedUser && (
                    <>
                        <DialogTitle>
                            <Stack direction="row" justifyContent="space-between" alignItems="center">
                                <Stack direction="row" alignItems="center" spacing={2}>
                                    <Avatar src={selectedUser.avatarUrl} sx={{ width: 56, height: 56 }}>
                                        {selectedUser.name.split(' ').map((n) => n[0]).join('')}
                                    </Avatar>
                                    <Box>
                                        <Typography variant="h6">{selectedUser.name}</Typography>
                                        <Typography variant="body2" color="text.secondary">
                                            {selectedUser.email}
                                        </Typography>
                                        <Stack direction="row" spacing={1} sx={{ mt: 0.5 }}>
                                            <Chip
                                                label={selectedUser.status.toUpperCase()}
                                                size="small"
                                                color={getStatusColor(selectedUser.status) as any}
                                            />
                                            {hasSecurityFlags(selectedUser) && (
                                                <Chip icon={<WarningIcon />} label="SECURITY FLAG" size="small" color="error" />
                                            )}
                                        </Stack>
                                    </Box>
                                </Stack>
                                <IconButton onClick={handleCloseUserDetail}>
                                    <CloseIcon />
                                </IconButton>
                            </Stack>
                        </DialogTitle>

                        <Tabs value={userTab} onChange={(_, v) => setUserTab(v)} sx={{ px: 3 }}>
                            <Tab label="Overview" />
                            <Tab label="Tenant Memberships" />
                            <Tab label="Activity Log" />
                            <Tab label="Security Events" />
                            <Tab label="Actions" />
                        </Tabs>

                        <DialogContent>
                            {/* Overview Tab */}
                            {userTab === 0 && (
                                <Stack spacing={3}>
                                    <Grid container spacing={2}>
                                        <Grid item xs={12} sm={6}>
                                            <Typography variant="caption" color="text.secondary">
                                                Account Created
                                            </Typography>
                                            <Typography variant="body1">{formatDateTime(selectedUser.createdAt)}</Typography>
                                        </Grid>
                                        <Grid item xs={12} sm={6}>
                                            <Typography variant="caption" color="text.secondary">
                                                Last Login
                                            </Typography>
                                            <Typography variant="body1">
                                                {selectedUser.lastLoginAt ? formatDateTime(selectedUser.lastLoginAt) : 'Never'}
                                            </Typography>
                                        </Grid>
                                    </Grid>

                                    <Box>
                                        <Typography variant="subtitle2" sx={{ mb: 2 }}>
                                            Global Statistics
                                        </Typography>
                                        <Grid container spacing={2}>
                                            <Grid item xs={12} sm={4}>
                                                <Card variant="outlined">
                                                    <Box sx={{ p: 2, textAlign: 'center' }}>
                                                        <Typography variant="h4" sx={{ fontWeight: 600 }}>
                                                            {selectedUser.globalStats.totalLogins.toLocaleString()}
                                                        </Typography>
                                                        <Typography variant="caption" color="text.secondary">
                                                            Total Logins
                                                        </Typography>
                                                    </Box>
                                                </Card>
                                            </Grid>
                                            <Grid item xs={12} sm={4}>
                                                <Card variant="outlined">
                                                    <Box sx={{ p: 2, textAlign: 'center' }}>
                                                        <Typography variant="h4" sx={{ fontWeight: 600 }}>
                                                            {(selectedUser.globalStats.totalApiCalls / 1000).toFixed(1)}K
                                                        </Typography>
                                                        <Typography variant="caption" color="text.secondary">
                                                            API Calls
                                                        </Typography>
                                                    </Box>
                                                </Card>
                                            </Grid>
                                            <Grid item xs={12} sm={4}>
                                                <Card variant="outlined">
                                                    <Box sx={{ p: 2, textAlign: 'center' }}>
                                                        <Typography variant="h4" sx={{ fontWeight: 600 }}>
                                                            {selectedUser.globalStats.dataAccessed} GB
                                                        </Typography>
                                                        <Typography variant="caption" color="text.secondary">
                                                            Data Accessed
                                                        </Typography>
                                                    </Box>
                                                </Card>
                                            </Grid>
                                        </Grid>
                                    </Box>

                                    {hasSecurityFlags(selectedUser) && (
                                        <Alert severity="error">
                                            <Typography variant="subtitle2">Security Flags Detected</Typography>
                                            <Stack spacing={0.5} sx={{ mt: 1 }}>
                                                {selectedUser.securityFlags.multipleFailedLogins && (
                                                    <Typography variant="body2">• Multiple failed login attempts</Typography>
                                                )}
                                                {selectedUser.securityFlags.suspiciousActivity && (
                                                    <Typography variant="body2">• Suspicious activity detected</Typography>
                                                )}
                                                {selectedUser.securityFlags.compromisedCredentials && (
                                                    <Typography variant="body2">• Compromised credentials reported</Typography>
                                                )}
                                            </Stack>
                                        </Alert>
                                    )}
                                </Stack>
                            )}

                            {/* Tenant Memberships Tab */}
                            {userTab === 1 && (
                                <Stack spacing={2}>
                                    {selectedUser.tenantMemberships.map((membership) => (
                                        <Card key={membership.tenantId} variant="outlined">
                                            <Box sx={{ p: 2 }}>
                                                <Typography variant="subtitle1" sx={{ fontWeight: 600, mb: 1 }}>
                                                    {membership.tenantName}
                                                </Typography>
                                                <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                                                    {membership.tenantSlug}
                                                </Typography>
                                                <Grid container spacing={2}>
                                                    <Grid item xs={12} sm={6}>
                                                        <Typography variant="caption" color="text.secondary">
                                                            Roles
                                                        </Typography>
                                                        <Stack direction="row" spacing={0.5} sx={{ mt: 0.5 }}>
                                                            {membership.roles.map((role) => (
                                                                <Chip key={role} label={role} size="small" color="primary" variant="outlined" />
                                                            ))}
                                                        </Stack>
                                                    </Grid>
                                                    <Grid item xs={12} sm={6}>
                                                        <Typography variant="caption" color="text.secondary">
                                                            Joined
                                                        </Typography>
                                                        <Typography variant="body2">{formatDateTime(membership.joinedAt)}</Typography>
                                                    </Grid>
                                                </Grid>
                                            </Box>
                                        </Card>
                                    ))}
                                </Stack>
                            )}

                            {/* Activity Log Tab */}
                            {userTab === 2 && (
                                <TableContainer>
                                    <Table size="small">
                                        <TableHead>
                                            <TableRow>
                                                <TableCell>Timestamp</TableCell>
                                                <TableCell>Tenant</TableCell>
                                                <TableCell>Action</TableCell>
                                                <TableCell>Location</TableCell>
                                                <TableCell>Status</TableCell>
                                            </TableRow>
                                        </TableHead>
                                        <TableBody>
                                            {userActivities.map((activity) => (
                                                <TableRow key={activity.id}>
                                                    <TableCell>{formatDateTime(activity.timestamp)}</TableCell>
                                                    <TableCell>{activity.tenantName}</TableCell>
                                                    <TableCell>
                                                        <Typography variant="body2">{activity.action}</Typography>
                                                        {activity.resource && (
                                                            <Typography variant="caption" color="text.secondary">
                                                                {activity.resource}
                                                            </Typography>
                                                        )}
                                                    </TableCell>
                                                    <TableCell>
                                                        <Typography variant="body2">{activity.location || activity.ipAddress}</Typography>
                                                    </TableCell>
                                                    <TableCell>
                                                        <Chip
                                                            label={activity.status.toUpperCase()}
                                                            size="small"
                                                            color={getActivityStatusColor(activity.status) as any}
                                                        />
                                                    </TableCell>
                                                </TableRow>
                                            ))}
                                        </TableBody>
                                    </Table>
                                </TableContainer>
                            )}

                            {/* Security Events Tab */}
                            {userTab === 3 && (
                                <Stack spacing={2}>
                                    {securityEvents.map((event) => (
                                        <Alert key={event.id} severity={getSeverityColor(event.severity) as any}>
                                            <Stack direction="row" justifyContent="space-between" alignItems="flex-start">
                                                <Box>
                                                    <Typography variant="subtitle2">{event.eventType.replace(/_/g, ' ').toUpperCase()}</Typography>
                                                    <Typography variant="body2" sx={{ mt: 0.5 }}>
                                                        {event.description}
                                                    </Typography>
                                                    <Typography variant="caption" color="text.secondary" sx={{ mt: 1, display: 'block' }}>
                                                        {formatDateTime(event.timestamp)} • {event.ipAddress}
                                                    </Typography>
                                                </Box>
                                                {event.resolved && <Chip label="RESOLVED" size="small" color="success" />}
                                            </Stack>
                                        </Alert>
                                    ))}
                                    {securityEvents.length === 0 && (
                                        <Box sx={{ p: 4, textAlign: 'center' }}>
                                            <CheckCircleIcon color="success" sx={{ fontSize: 48, mb: 2 }} />
                                            <Typography variant="body1" color="text.secondary">
                                                No security events detected
                                            </Typography>
                                        </Box>
                                    )}
                                </Stack>
                            )}

                            {/* Actions Tab */}
                            {userTab === 4 && (
                                <Stack spacing={3}>
                                    <Alert severity="warning">
                                        <Typography variant="subtitle2">Caution: Administrative Actions</Typography>
                                        <Typography variant="body2">
                                            These actions affect the user across all tenant organizations.
                                        </Typography>
                                    </Alert>

                                    {selectedUser.status === 'active' && (
                                        <Card variant="outlined">
                                            <Box sx={{ p: 2 }}>
                                                <Typography variant="subtitle1" sx={{ mb: 1, color: 'error.main' }}>
                                                    Suspend User Account
                                                </Typography>
                                                <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                                                    Suspending this user will revoke access across all tenant organizations.
                                                    This action is reversible.
                                                </Typography>
                                                <Button variant="outlined" color="error" onClick={handleOpenSuspendDialog}>
                                                    Suspend Account
                                                </Button>
                                            </Box>
                                        </Card>
                                    )}

                                    {selectedUser.status === 'suspended' && (
                                        <Card variant="outlined">
                                            <Box sx={{ p: 2 }}>
                                                <Typography variant="subtitle1" sx={{ mb: 1, color: 'success.main' }}>
                                                    Reactivate User Account
                                                </Typography>
                                                <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                                                    Reactivating this user will restore access across all tenant organizations.
                                                </Typography>
                                                <Button variant="outlined" color="success" onClick={handleUnsuspendUser}>
                                                    Reactivate Account
                                                </Button>
                                            </Box>
                                        </Card>
                                    )}

                                    <Card variant="outlined">
                                        <Box sx={{ p: 2 }}>
                                            <Typography variant="subtitle1" sx={{ mb: 1 }}>
                                                Additional Actions
                                            </Typography>
                                            <Stack spacing={1}>
                                                <Button variant="outlined" fullWidth disabled>
                                                    View Full Audit Log
                                                </Button>
                                                <Button variant="outlined" fullWidth disabled>
                                                    Reset Password
                                                </Button>
                                                <Button variant="outlined" fullWidth disabled>
                                                    Revoke All Sessions
                                                </Button>
                                                <Button variant="outlined" fullWidth disabled>
                                                    Export User Data
                                                </Button>
                                            </Stack>
                                        </Box>
                                    </Card>
                                </Stack>
                            )}
                        </DialogContent>

                        <DialogActions>
                            <Button onClick={handleCloseUserDetail}>Close</Button>
                        </DialogActions>
                    </>
                )}
            </Dialog>

            {/* Suspend User Dialog */}
            <Dialog open={suspendDialogOpen} onClose={() => setSuspendDialogOpen(false)} maxWidth="sm" fullWidth>
                <DialogTitle>Suspend User Account</DialogTitle>
                <DialogContent>
                    <Alert severity="error" sx={{ mb: 2 }}>
                        This will immediately revoke access for this user across all tenant organizations.
                    </Alert>
                    <TextField
                        label="Reason for Suspension"
                        fullWidth
                        multiline
                        rows={4}
                        value={suspendReason}
                        onChange={(e) => setSuspendReason(e.target.value)}
                        placeholder="Provide a detailed reason for suspending this account..."
                        required
                    />
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setSuspendDialogOpen(false)}>Cancel</Button>
                    <Button onClick={handleSuspendUser} variant="contained" color="error" disabled={!suspendReason.trim()}>
                        Suspend Account
                    </Button>
                </DialogActions>
            </Dialog>
        </Box>
    );
}
