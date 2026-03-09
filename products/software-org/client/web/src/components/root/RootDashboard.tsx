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
    LinearProgress,
    Tooltip,
    Alert,
} from '@ghatana/ui';
import {
    Search as SearchIcon,
    Close as CloseIcon,
    Visibility as ViewIcon,
    Warning as WarningIcon,
    CheckCircle as CheckCircleIcon,
    TrendingUp as TrendingUpIcon,
    Storage as StorageIcon,
    People as PeopleIcon,
    Speed as SpeedIcon,
    Security as SecurityIcon,
} from '@ghatana/ui/icons';

/**
 * Tenant information with resource usage and health metrics
 */
export interface TenantInfo {
    id: string;
    name: string;
    slug: string;
    createdAt: string;
    status: 'active' | 'suspended' | 'trial' | 'inactive';
    plan: 'free' | 'starter' | 'professional' | 'enterprise';
    userCount: number;
    activeUsers: number;
    storageUsed: number; // GB
    storageLimit: number; // GB
    apiCallsToday: number;
    apiCallLimit: number;
    healthScore: number; // 0-100
    lastActivity: string;
    owner?: {
        name: string;
        email: string;
    };
}

/**
 * Resource usage metrics for a tenant
 */
export interface ResourceMetrics {
    cpu: {
        current: number; // percentage
        average: number;
        peak: number;
    };
    memory: {
        used: number; // MB
        limit: number; // MB
        percentage: number;
    };
    database: {
        connections: number;
        maxConnections: number;
        queryLatency: number; // ms
    };
    api: {
        requestsPerMinute: number;
        errorRate: number; // percentage
        avgResponseTime: number; // ms
    };
}

/**
 * Platform-wide statistics
 */
export interface PlatformStats {
    totalTenants: number;
    activeTenants: number;
    suspendedTenants: number;
    trialTenants: number;
    totalUsers: number;
    activeUsers: number;
    totalStorage: number; // GB
    apiCallsToday: number;
    averageHealthScore: number;
}

export interface RootDashboardProps {
    tenants?: TenantInfo[];
    platformStats?: PlatformStats;
    onTenantInspect?: (tenantId: string) => void;
    onTenantSuspend?: (tenantId: string) => void;
    onTenantReactivate?: (tenantId: string) => void;
    onAdjustLimits?: (tenantId: string, limits: { storage?: number; apiCalls?: number }) => void;
}

/**
 * Root Dashboard Component
 *
 * Provides platform administrators with:
 * - Global tenant search and filtering
 * - Platform-wide statistics
 * - Tenant inspection overlay
 * - Resource usage monitoring
 * - Limit adjustment capabilities
 */
export function RootDashboard({
    tenants: propTenants,
    platformStats: propStats,
    onTenantInspect,
    onTenantSuspend,
    onTenantReactivate,
    onAdjustLimits,
}: RootDashboardProps) {
    // Mock data for development
    const mockTenants: TenantInfo[] = [
        {
            id: 'tenant-1',
            name: 'Acme Corporation',
            slug: 'acme-corp',
            createdAt: '2024-01-15',
            status: 'active',
            plan: 'enterprise',
            userCount: 450,
            activeUsers: 380,
            storageUsed: 850,
            storageLimit: 1000,
            apiCallsToday: 125000,
            apiCallLimit: 500000,
            healthScore: 95,
            lastActivity: '2025-12-11T10:30:00Z',
            owner: {
                name: 'John Smith',
                email: 'john@acme.com',
            },
        },
        {
            id: 'tenant-2',
            name: 'TechStart Inc',
            slug: 'techstart',
            createdAt: '2024-06-20',
            status: 'active',
            plan: 'professional',
            userCount: 85,
            activeUsers: 72,
            storageUsed: 120,
            storageLimit: 250,
            apiCallsToday: 18000,
            apiCallLimit: 100000,
            healthScore: 88,
            lastActivity: '2025-12-11T11:15:00Z',
            owner: {
                name: 'Sarah Johnson',
                email: 'sarah@techstart.io',
            },
        },
        {
            id: 'tenant-3',
            name: 'Global Ventures',
            slug: 'global-ventures',
            createdAt: '2023-09-10',
            status: 'active',
            plan: 'enterprise',
            userCount: 1200,
            activeUsers: 980,
            storageUsed: 2400,
            storageLimit: 3000,
            apiCallsToday: 485000,
            apiCallLimit: 1000000,
            healthScore: 72,
            lastActivity: '2025-12-11T11:45:00Z',
            owner: {
                name: 'Michael Chen',
                email: 'mchen@globalventures.com',
            },
        },
        {
            id: 'tenant-4',
            name: 'Startup Labs',
            slug: 'startup-labs',
            createdAt: '2025-11-01',
            status: 'trial',
            plan: 'starter',
            userCount: 12,
            activeUsers: 8,
            storageUsed: 5,
            storageLimit: 50,
            apiCallsToday: 450,
            apiCallLimit: 10000,
            healthScore: 100,
            lastActivity: '2025-12-11T09:00:00Z',
            owner: {
                name: 'Emily Davis',
                email: 'emily@startuplabs.co',
            },
        },
        {
            id: 'tenant-5',
            name: 'Legacy Systems Co',
            slug: 'legacy-systems',
            createdAt: '2022-03-15',
            status: 'suspended',
            plan: 'professional',
            userCount: 150,
            activeUsers: 0,
            storageUsed: 380,
            storageLimit: 500,
            apiCallsToday: 0,
            apiCallLimit: 100000,
            healthScore: 0,
            lastActivity: '2025-11-20T14:30:00Z',
            owner: {
                name: 'Robert Wilson',
                email: 'rwilson@legacysystems.com',
            },
        },
    ];

    const mockStats: PlatformStats = {
        totalTenants: 5,
        activeTenants: 4,
        suspendedTenants: 1,
        trialTenants: 1,
        totalUsers: 1897,
        activeUsers: 1440,
        totalStorage: 3755, // GB
        apiCallsToday: 628450,
        averageHealthScore: 71,
    };

    const tenants = propTenants || mockTenants;
    const platformStats = propStats || mockStats;

    const [searchQuery, setSearchQuery] = useState('');
    const [filterStatus, setFilterStatus] = useState<string>('all');
    const [filterPlan, setFilterPlan] = useState<string>('all');
    const [selectedTenant, setSelectedTenant] = useState<TenantInfo | null>(null);
    const [inspectTab, setInspectTab] = useState(0);
    const [limitsDialogOpen, setLimitsDialogOpen] = useState(false);
    const [newStorageLimit, setNewStorageLimit] = useState<number>(0);
    const [newApiLimit, setNewApiLimit] = useState<number>(0);

    // Mock resource metrics for selected tenant
    const mockResourceMetrics: ResourceMetrics = {
        cpu: {
            current: 45,
            average: 38,
            peak: 72,
        },
        memory: {
            used: 3200,
            limit: 8192,
            percentage: 39,
        },
        database: {
            connections: 45,
            maxConnections: 100,
            queryLatency: 12,
        },
        api: {
            requestsPerMinute: 850,
            errorRate: 0.8,
            avgResponseTime: 145,
        },
    };

    // Filter tenants
    const filteredTenants = useMemo(() => {
        return tenants.filter((tenant) => {
            const matchesSearch =
                tenant.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
                tenant.slug.toLowerCase().includes(searchQuery.toLowerCase()) ||
                tenant.owner?.email.toLowerCase().includes(searchQuery.toLowerCase());

            const matchesStatus = filterStatus === 'all' || tenant.status === filterStatus;
            const matchesPlan = filterPlan === 'all' || tenant.plan === filterPlan;

            return matchesSearch && matchesStatus && matchesPlan;
        });
    }, [tenants, searchQuery, filterStatus, filterPlan]);

    const handleTenantClick = (tenant: TenantInfo) => {
        setSelectedTenant(tenant);
        setNewStorageLimit(tenant.storageLimit);
        setNewApiLimit(tenant.apiCallLimit);
        onTenantInspect?.(tenant.id);
    };

    const handleCloseTenantInspect = () => {
        setSelectedTenant(null);
        setInspectTab(0);
    };

    const handleOpenLimitsDialog = () => {
        setLimitsDialogOpen(true);
    };

    const handleSaveLimits = () => {
        if (selectedTenant && onAdjustLimits) {
            onAdjustLimits(selectedTenant.id, {
                storage: newStorageLimit,
                apiCalls: newApiLimit,
            });
        }
        setLimitsDialogOpen(false);
    };

    const handleSuspendTenant = () => {
        if (selectedTenant && onTenantSuspend) {
            onTenantSuspend(selectedTenant.id);
            handleCloseTenantInspect();
        }
    };

    const handleReactivateTenant = () => {
        if (selectedTenant && onTenantReactivate) {
            onTenantReactivate(selectedTenant.id);
            handleCloseTenantInspect();
        }
    };

    const getStatusColor = (status: string) => {
        switch (status) {
            case 'active':
                return 'success';
            case 'trial':
                return 'info';
            case 'suspended':
                return 'error';
            case 'inactive':
                return 'default';
            default:
                return 'default';
        }
    };

    const getPlanColor = (plan: string) => {
        switch (plan) {
            case 'enterprise':
                return 'primary';
            case 'professional':
                return 'secondary';
            case 'starter':
                return 'info';
            case 'free':
                return 'default';
            default:
                return 'default';
        }
    };

    const getHealthScoreColor = (score: number) => {
        if (score >= 90) return 'success';
        if (score >= 70) return 'info';
        if (score >= 50) return 'warning';
        return 'error';
    };

    const formatDate = (dateString: string) => {
        const date = new Date(dateString);
        return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
    };

    const formatDateTime = (dateString: string) => {
        const date = new Date(dateString);
        const now = new Date();
        const diff = now.getTime() - date.getTime();
        const minutes = Math.floor(diff / 60000);
        const hours = Math.floor(minutes / 60);
        const days = Math.floor(hours / 24);

        if (minutes < 60) return `${minutes} minutes ago`;
        if (hours < 24) return `${hours} hours ago`;
        return `${days} days ago`;
    };

    return (
        <Box sx={{ p: 3 }}>
            {/* Header */}
            <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 3 }}>
                <Box>
                    <Typography variant="h4" sx={{ fontWeight: 600 }}>
                        Platform Administration
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                        Global tenant management and monitoring
                    </Typography>
                </Box>
            </Stack>

            {/* Platform Statistics */}
            <Grid container spacing={2} sx={{ mb: 3 }}>
                <Grid item xs={12} sm={6} md={3}>
                    <Card sx={{ p: 2 }}>
                        <Stack direction="row" alignItems="center" spacing={2}>
                            <Box
                                sx={{
                                    width: 48,
                                    height: 48,
                                    borderRadius: 2,
                                    bgcolor: 'primary.main',
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'center',
                                    color: 'white',
                                }}
                            >
                                <TrendingUpIcon />
                            </Box>
                            <Box sx={{ flex: 1 }}>
                                <Typography variant="body2" color="text.secondary">
                                    Total Tenants
                                </Typography>
                                <Typography variant="h5" sx={{ fontWeight: 600 }}>
                                    {platformStats.totalTenants}
                                </Typography>
                                <Typography variant="caption" color="success.main">
                                    {platformStats.activeTenants} active
                                </Typography>
                            </Box>
                        </Stack>
                    </Card>
                </Grid>

                <Grid item xs={12} sm={6} md={3}>
                    <Card sx={{ p: 2 }}>
                        <Stack direction="row" alignItems="center" spacing={2}>
                            <Box
                                sx={{
                                    width: 48,
                                    height: 48,
                                    borderRadius: 2,
                                    bgcolor: 'secondary.main',
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'center',
                                    color: 'white',
                                }}
                            >
                                <PeopleIcon />
                            </Box>
                            <Box sx={{ flex: 1 }}>
                                <Typography variant="body2" color="text.secondary">
                                    Total Users
                                </Typography>
                                <Typography variant="h5" sx={{ fontWeight: 600 }}>
                                    {platformStats.totalUsers.toLocaleString()}
                                </Typography>
                                <Typography variant="caption" color="success.main">
                                    {platformStats.activeUsers.toLocaleString()} active
                                </Typography>
                            </Box>
                        </Stack>
                    </Card>
                </Grid>

                <Grid item xs={12} sm={6} md={3}>
                    <Card sx={{ p: 2 }}>
                        <Stack direction="row" alignItems="center" spacing={2}>
                            <Box
                                sx={{
                                    width: 48,
                                    height: 48,
                                    borderRadius: 2,
                                    bgcolor: 'info.main',
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'center',
                                    color: 'white',
                                }}
                            >
                                <StorageIcon />
                            </Box>
                            <Box sx={{ flex: 1 }}>
                                <Typography variant="body2" color="text.secondary">
                                    Total Storage
                                </Typography>
                                <Typography variant="h5" sx={{ fontWeight: 600 }}>
                                    {(platformStats.totalStorage / 1000).toFixed(1)} TB
                                </Typography>
                                <Typography variant="caption" color="text.secondary">
                                    Across all tenants
                                </Typography>
                            </Box>
                        </Stack>
                    </Card>
                </Grid>

                <Grid item xs={12} sm={6} md={3}>
                    <Card sx={{ p: 2 }}>
                        <Stack direction="row" alignItems="center" spacing={2}>
                            <Box
                                sx={{
                                    width: 48,
                                    height: 48,
                                    borderRadius: 2,
                                    bgcolor: 'warning.main',
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'center',
                                    color: 'white',
                                }}
                            >
                                <SpeedIcon />
                            </Box>
                            <Box sx={{ flex: 1 }}>
                                <Typography variant="body2" color="text.secondary">
                                    API Calls Today
                                </Typography>
                                <Typography variant="h5" sx={{ fontWeight: 600 }}>
                                    {(platformStats.apiCallsToday / 1000).toFixed(0)}K
                                </Typography>
                                <Typography variant="caption" color="text.secondary">
                                    Platform-wide
                                </Typography>
                            </Box>
                        </Stack>
                    </Card>
                </Grid>
            </Grid>

            {/* Search and Filters */}
            <Card sx={{ mb: 3 }}>
                <Box sx={{ p: 2 }}>
                    <Grid container spacing={2} alignItems="center">
                        <Grid item xs={12} md={6}>
                            <TextField
                                fullWidth
                                placeholder="Search by tenant name, slug, or owner email..."
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
                        <Grid item xs={12} sm={6} md={3}>
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
                                <option value="trial">Trial</option>
                                <option value="suspended">Suspended</option>
                                <option value="inactive">Inactive</option>
                            </TextField>
                        </Grid>
                        <Grid item xs={12} sm={6} md={3}>
                            <TextField
                                select
                                fullWidth
                                label="Plan"
                                value={filterPlan}
                                onChange={(e) => setFilterPlan(e.target.value)}
                                SelectProps={{
                                    native: true,
                                }}
                            >
                                <option value="all">All Plans</option>
                                <option value="enterprise">Enterprise</option>
                                <option value="professional">Professional</option>
                                <option value="starter">Starter</option>
                                <option value="free">Free</option>
                            </TextField>
                        </Grid>
                    </Grid>
                </Box>
            </Card>

            {/* Tenant List */}
            <Card>
                <Box sx={{ p: 2, borderBottom: 1, borderColor: 'divider' }}>
                    <Typography variant="h6">
                        Tenants ({filteredTenants.length})
                    </Typography>
                </Box>
                <Stack divider={<Box sx={{ borderBottom: 1, borderColor: 'divider' }} />}>
                    {filteredTenants.map((tenant) => (
                        <Box
                            key={tenant.id}
                            sx={{
                                p: 2,
                                cursor: 'pointer',
                                '&:hover': { bgcolor: 'action.hover' },
                            }}
                            onClick={() => handleTenantClick(tenant)}
                        >
                            <Grid container spacing={2} alignItems="center">
                                <Grid item xs={12} md={3}>
                                    <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
                                        {tenant.name}
                                    </Typography>
                                    <Typography variant="body2" color="text.secondary">
                                        {tenant.slug}
                                    </Typography>
                                    <Stack direction="row" spacing={1} sx={{ mt: 0.5 }}>
                                        <Chip
                                            label={tenant.status.toUpperCase()}
                                            size="small"
                                            color={getStatusColor(tenant.status) as any}
                                        />
                                        <Chip
                                            label={tenant.plan.toUpperCase()}
                                            size="small"
                                            color={getPlanColor(tenant.plan) as any}
                                            variant="outlined"
                                        />
                                    </Stack>
                                </Grid>

                                <Grid item xs={12} sm={6} md={2}>
                                    <Typography variant="caption" color="text.secondary">
                                        Users
                                    </Typography>
                                    <Typography variant="body1">
                                        {tenant.activeUsers} / {tenant.userCount}
                                    </Typography>
                                </Grid>

                                <Grid item xs={12} sm={6} md={2}>
                                    <Typography variant="caption" color="text.secondary">
                                        Storage
                                    </Typography>
                                    <Typography variant="body1">
                                        {tenant.storageUsed} / {tenant.storageLimit} GB
                                    </Typography>
                                    <LinearProgress
                                        variant="determinate"
                                        value={(tenant.storageUsed / tenant.storageLimit) * 100}
                                        sx={{ mt: 0.5 }}
                                    />
                                </Grid>

                                <Grid item xs={12} sm={6} md={2}>
                                    <Typography variant="caption" color="text.secondary">
                                        API Calls Today
                                    </Typography>
                                    <Typography variant="body1">
                                        {(tenant.apiCallsToday / 1000).toFixed(1)}K / {(tenant.apiCallLimit / 1000).toFixed(0)}K
                                    </Typography>
                                    <LinearProgress
                                        variant="determinate"
                                        value={(tenant.apiCallsToday / tenant.apiCallLimit) * 100}
                                        sx={{ mt: 0.5 }}
                                    />
                                </Grid>

                                <Grid item xs={12} sm={6} md={2}>
                                    <Typography variant="caption" color="text.secondary">
                                        Health Score
                                    </Typography>
                                    <Stack direction="row" alignItems="center" spacing={1}>
                                        <Typography variant="body1" sx={{ fontWeight: 600 }}>
                                            {tenant.healthScore}
                                        </Typography>
                                        <Chip
                                            size="small"
                                            icon={
                                                tenant.healthScore >= 90 ? (
                                                    <CheckCircleIcon />
                                                ) : tenant.healthScore >= 50 ? (
                                                    <WarningIcon />
                                                ) : (
                                                    <WarningIcon />
                                                )
                                            }
                                            label={
                                                tenant.healthScore >= 90
                                                    ? 'Excellent'
                                                    : tenant.healthScore >= 70
                                                        ? 'Good'
                                                        : tenant.healthScore >= 50
                                                            ? 'Fair'
                                                            : 'Poor'
                                            }
                                            color={getHealthScoreColor(tenant.healthScore) as any}
                                        />
                                    </Stack>
                                </Grid>

                                <Grid item xs={12} md={1}>
                                    <IconButton size="small">
                                        <ViewIcon />
                                    </IconButton>
                                </Grid>
                            </Grid>
                        </Box>
                    ))}
                </Stack>

                {filteredTenants.length === 0 && (
                    <Box sx={{ p: 4, textAlign: 'center' }}>
                        <Typography variant="body1" color="text.secondary">
                            No tenants match your search criteria
                        </Typography>
                    </Box>
                )}
            </Card>

            {/* Tenant Inspection Dialog */}
            <Dialog
                open={!!selectedTenant}
                onClose={handleCloseTenantInspect}
                maxWidth="lg"
                fullWidth
            >
                {selectedTenant && (
                    <>
                        <DialogTitle>
                            <Stack direction="row" justifyContent="space-between" alignItems="center">
                                <Box>
                                    <Typography variant="h6">{selectedTenant.name}</Typography>
                                    <Typography variant="body2" color="text.secondary">
                                        {selectedTenant.slug}
                                    </Typography>
                                </Box>
                                <Stack direction="row" spacing={1}>
                                    <Chip
                                        label={selectedTenant.status.toUpperCase()}
                                        size="small"
                                        color={getStatusColor(selectedTenant.status) as any}
                                    />
                                    <Chip
                                        label={selectedTenant.plan.toUpperCase()}
                                        size="small"
                                        color={getPlanColor(selectedTenant.plan) as any}
                                    />
                                    <IconButton onClick={handleCloseTenantInspect}>
                                        <CloseIcon />
                                    </IconButton>
                                </Stack>
                            </Stack>
                        </DialogTitle>

                        <Tabs value={inspectTab} onChange={(_, v) => setInspectTab(v)} sx={{ px: 3 }}>
                            <Tab label="Overview" />
                            <Tab label="Resource Usage" />
                            <Tab label="Limits" />
                            <Tab label="Actions" />
                        </Tabs>

                        <DialogContent>
                            {/* Overview Tab */}
                            {inspectTab === 0 && (
                                <Stack spacing={3}>
                                    <Grid container spacing={2}>
                                        <Grid item xs={12} sm={6}>
                                            <Typography variant="caption" color="text.secondary">
                                                Created
                                            </Typography>
                                            <Typography variant="body1">
                                                {formatDate(selectedTenant.createdAt)}
                                            </Typography>
                                        </Grid>
                                        <Grid item xs={12} sm={6}>
                                            <Typography variant="caption" color="text.secondary">
                                                Last Activity
                                            </Typography>
                                            <Typography variant="body1">
                                                {formatDateTime(selectedTenant.lastActivity)}
                                            </Typography>
                                        </Grid>
                                        <Grid item xs={12} sm={6}>
                                            <Typography variant="caption" color="text.secondary">
                                                Owner
                                            </Typography>
                                            <Typography variant="body1">{selectedTenant.owner?.name}</Typography>
                                            <Typography variant="body2" color="text.secondary">
                                                {selectedTenant.owner?.email}
                                            </Typography>
                                        </Grid>
                                        <Grid item xs={12} sm={6}>
                                            <Typography variant="caption" color="text.secondary">
                                                Health Score
                                            </Typography>
                                            <Stack direction="row" alignItems="center" spacing={1}>
                                                <Typography variant="h4" sx={{ fontWeight: 600 }}>
                                                    {selectedTenant.healthScore}
                                                </Typography>
                                                <Chip
                                                    label={
                                                        selectedTenant.healthScore >= 90
                                                            ? 'Excellent'
                                                            : selectedTenant.healthScore >= 70
                                                                ? 'Good'
                                                                : 'Needs Attention'
                                                    }
                                                    color={getHealthScoreColor(selectedTenant.healthScore) as any}
                                                />
                                            </Stack>
                                        </Grid>
                                    </Grid>
                                </Stack>
                            )}

                            {/* Resource Usage Tab */}
                            {inspectTab === 1 && (
                                <Stack spacing={3}>
                                    <Card variant="outlined">
                                        <Box sx={{ p: 2 }}>
                                            <Typography variant="subtitle1" sx={{ mb: 2 }}>
                                                CPU Usage
                                            </Typography>
                                            <Grid container spacing={2}>
                                                <Grid item xs={4}>
                                                    <Typography variant="caption" color="text.secondary">
                                                        Current
                                                    </Typography>
                                                    <Typography variant="h6">{mockResourceMetrics.cpu.current}%</Typography>
                                                </Grid>
                                                <Grid item xs={4}>
                                                    <Typography variant="caption" color="text.secondary">
                                                        Average
                                                    </Typography>
                                                    <Typography variant="h6">{mockResourceMetrics.cpu.average}%</Typography>
                                                </Grid>
                                                <Grid item xs={4}>
                                                    <Typography variant="caption" color="text.secondary">
                                                        Peak
                                                    </Typography>
                                                    <Typography variant="h6">{mockResourceMetrics.cpu.peak}%</Typography>
                                                </Grid>
                                            </Grid>
                                        </Box>
                                    </Card>

                                    <Card variant="outlined">
                                        <Box sx={{ p: 2 }}>
                                            <Typography variant="subtitle1" sx={{ mb: 2 }}>
                                                Memory Usage
                                            </Typography>
                                            <Typography variant="h6">
                                                {mockResourceMetrics.memory.used} MB / {mockResourceMetrics.memory.limit} MB
                                            </Typography>
                                            <LinearProgress
                                                variant="determinate"
                                                value={mockResourceMetrics.memory.percentage}
                                                sx={{ mt: 1 }}
                                            />
                                            <Typography variant="caption" color="text.secondary" sx={{ mt: 1 }}>
                                                {mockResourceMetrics.memory.percentage}% utilized
                                            </Typography>
                                        </Box>
                                    </Card>

                                    <Card variant="outlined">
                                        <Box sx={{ p: 2 }}>
                                            <Typography variant="subtitle1" sx={{ mb: 2 }}>
                                                Database
                                            </Typography>
                                            <Grid container spacing={2}>
                                                <Grid item xs={6}>
                                                    <Typography variant="caption" color="text.secondary">
                                                        Connections
                                                    </Typography>
                                                    <Typography variant="h6">
                                                        {mockResourceMetrics.database.connections} /{' '}
                                                        {mockResourceMetrics.database.maxConnections}
                                                    </Typography>
                                                </Grid>
                                                <Grid item xs={6}>
                                                    <Typography variant="caption" color="text.secondary">
                                                        Avg Query Latency
                                                    </Typography>
                                                    <Typography variant="h6">
                                                        {mockResourceMetrics.database.queryLatency} ms
                                                    </Typography>
                                                </Grid>
                                            </Grid>
                                        </Box>
                                    </Card>

                                    <Card variant="outlined">
                                        <Box sx={{ p: 2 }}>
                                            <Typography variant="subtitle1" sx={{ mb: 2 }}>
                                                API Performance
                                            </Typography>
                                            <Grid container spacing={2}>
                                                <Grid item xs={4}>
                                                    <Typography variant="caption" color="text.secondary">
                                                        Requests/Min
                                                    </Typography>
                                                    <Typography variant="h6">
                                                        {mockResourceMetrics.api.requestsPerMinute}
                                                    </Typography>
                                                </Grid>
                                                <Grid item xs={4}>
                                                    <Typography variant="caption" color="text.secondary">
                                                        Error Rate
                                                    </Typography>
                                                    <Typography variant="h6" color={mockResourceMetrics.api.errorRate > 5 ? 'error' : 'inherit'}>
                                                        {mockResourceMetrics.api.errorRate}%
                                                    </Typography>
                                                </Grid>
                                                <Grid item xs={4}>
                                                    <Typography variant="caption" color="text.secondary">
                                                        Avg Response
                                                    </Typography>
                                                    <Typography variant="h6">
                                                        {mockResourceMetrics.api.avgResponseTime} ms
                                                    </Typography>
                                                </Grid>
                                            </Grid>
                                        </Box>
                                    </Card>
                                </Stack>
                            )}

                            {/* Limits Tab */}
                            {inspectTab === 2 && (
                                <Stack spacing={3}>
                                    <Alert severity="info">
                                        Adjust resource limits for this tenant. Changes take effect immediately.
                                    </Alert>

                                    <Card variant="outlined">
                                        <Box sx={{ p: 2 }}>
                                            <Typography variant="subtitle1" sx={{ mb: 2 }}>
                                                Current Limits
                                            </Typography>
                                            <Grid container spacing={2}>
                                                <Grid item xs={12} sm={6}>
                                                    <Typography variant="caption" color="text.secondary">
                                                        Storage Limit
                                                    </Typography>
                                                    <Typography variant="h6">{selectedTenant.storageLimit} GB</Typography>
                                                    <Typography variant="caption" color="text.secondary">
                                                        Currently using: {selectedTenant.storageUsed} GB (
                                                        {((selectedTenant.storageUsed / selectedTenant.storageLimit) * 100).toFixed(1)}
                                                        %)
                                                    </Typography>
                                                </Grid>
                                                <Grid item xs={12} sm={6}>
                                                    <Typography variant="caption" color="text.secondary">
                                                        API Call Limit (Daily)
                                                    </Typography>
                                                    <Typography variant="h6">
                                                        {selectedTenant.apiCallLimit.toLocaleString()}
                                                    </Typography>
                                                    <Typography variant="caption" color="text.secondary">
                                                        Today: {selectedTenant.apiCallsToday.toLocaleString()} (
                                                        {((selectedTenant.apiCallsToday / selectedTenant.apiCallLimit) * 100).toFixed(1)}
                                                        %)
                                                    </Typography>
                                                </Grid>
                                            </Grid>
                                        </Box>
                                    </Card>

                                    <Button variant="contained" onClick={handleOpenLimitsDialog}>
                                        Adjust Limits
                                    </Button>
                                </Stack>
                            )}

                            {/* Actions Tab */}
                            {inspectTab === 3 && (
                                <Stack spacing={3}>
                                    <Alert severity="warning">
                                        <Typography variant="subtitle2">Caution: Administrative Actions</Typography>
                                        <Typography variant="body2">
                                            These actions affect the entire tenant organization and all its users.
                                        </Typography>
                                    </Alert>

                                    {selectedTenant.status === 'active' && (
                                        <Card variant="outlined">
                                            <Box sx={{ p: 2 }}>
                                                <Typography variant="subtitle1" sx={{ mb: 1, color: 'error.main' }}>
                                                    Suspend Tenant
                                                </Typography>
                                                <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                                                    Suspending this tenant will immediately revoke access for all users. This
                                                    action is reversible.
                                                </Typography>
                                                <Button variant="outlined" color="error" onClick={handleSuspendTenant}>
                                                    Suspend Tenant
                                                </Button>
                                            </Box>
                                        </Card>
                                    )}

                                    {selectedTenant.status === 'suspended' && (
                                        <Card variant="outlined">
                                            <Box sx={{ p: 2 }}>
                                                <Typography variant="subtitle1" sx={{ mb: 1, color: 'success.main' }}>
                                                    Reactivate Tenant
                                                </Typography>
                                                <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                                                    Reactivating this tenant will restore access for all users.
                                                </Typography>
                                                <Button variant="outlined" color="success" onClick={handleReactivateTenant}>
                                                    Reactivate Tenant
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
                                                    View Audit Log
                                                </Button>
                                                <Button variant="outlined" fullWidth disabled>
                                                    Export Tenant Data
                                                </Button>
                                                <Button variant="outlined" fullWidth disabled>
                                                    Reset API Keys
                                                </Button>
                                            </Stack>
                                        </Box>
                                    </Card>
                                </Stack>
                            )}
                        </DialogContent>

                        <DialogActions>
                            <Button onClick={handleCloseTenantInspect}>Close</Button>
                        </DialogActions>
                    </>
                )}
            </Dialog>

            {/* Adjust Limits Dialog */}
            <Dialog open={limitsDialogOpen} onClose={() => setLimitsDialogOpen(false)} maxWidth="sm" fullWidth>
                <DialogTitle>Adjust Resource Limits</DialogTitle>
                <DialogContent>
                    <Stack spacing={3} sx={{ mt: 1 }}>
                        <TextField
                            label="Storage Limit (GB)"
                            type="number"
                            fullWidth
                            value={newStorageLimit}
                            onChange={(e) => setNewStorageLimit(Number(e.target.value))}
                            helperText={`Current: ${selectedTenant?.storageLimit} GB`}
                        />
                        <TextField
                            label="Daily API Call Limit"
                            type="number"
                            fullWidth
                            value={newApiLimit}
                            onChange={(e) => setNewApiLimit(Number(e.target.value))}
                            helperText={`Current: ${selectedTenant?.apiCallLimit.toLocaleString()}`}
                        />
                    </Stack>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setLimitsDialogOpen(false)}>Cancel</Button>
                    <Button onClick={handleSaveLimits} variant="contained">
                        Save Changes
                    </Button>
                </DialogActions>
            </Dialog>
        </Box>
    );
}
